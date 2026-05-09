package com.github.ethanhosier.analysis.alternative.synthesise

import com.github.ethanhosier.analysis.alternative.reorder.EnumerationBudget
import com.github.ethanhosier.analysis.alternative.reorder.ReorderDebug
import com.github.ethanhosier.analysis.alternative.reorder.SpecDependencyAnalyzer
import com.github.ethanhosier.analysis.alternative.reorder.TopologicalEnumerator
import com.github.ethanhosier.analysis.alternative.validate.JavaFileAstHasher
import com.github.ethanhosier.analysis.alternative.validate.RefactoringStepValidator
import com.github.ethanhosier.analysis.alternative.validate.SpecDispatcher
import com.github.ethanhosier.analysis.metrics.WorktreePool
import com.github.ethanhosier.analysis.metrics.model.ReorderOrdering
import com.github.ethanhosier.analysis.metrics.model.ReorderTrajectory
import com.github.ethanhosier.analysis.miner.model.RefactoringSpec
import com.github.ethanhosier.analysis.miner.model.RefactoringStep
import com.github.ethanhosier.analysis.reconstruct.GitRunner
import com.github.ethanhosier.analysis.refactoring.RefactoringClient
import java.nio.file.Path

/**
 * Pipeline stage: for each VALID reorder window in the trace,
 * synthesise every alt ordering as a chain of git commits in the
 * shadow repo.
 *
 * Strategy: build the prefix trie of all alt orderings (excluding
 * the user's identity ordering), then DFS over the trie with
 * **one** worktree borrowed at the window's `fromSha` and **one**
 * `withBatchSession` open for the window. Forward edges are
 * applied via [SpecDispatcher.apply]; backtracking happens via
 * `git checkout --detach <parentSha>` followed by a project
 * `refreshLocal` so Eclipse's resource model picks up the on-disk
 * change.
 *
 * Each unique prefix gets one commit and one branch ref, shared
 * across every ordering passing through it. Branch refs use the
 * pattern `reorder/win<W>/path/<dash-joined-prefix>`. Result
 * schema unchanged ([ReorderOrdering] still carries `permutation`,
 * `stepShas`, `branchRefs` parallel to it); orderings with shared
 * prefixes simply reference the same SHAs and refs.
 *
 * Failure: when an apply fails (or produces no textual change) at
 * depth k of the trie, the failing prefix is recorded and its
 * subtree is skipped (sibling subtrees still explored). Every
 * ordering passing through that failing prefix has
 * `terminalSuccess = false`, `failedAt = k - 1`, and
 * stepShas/branchRefs truncated to length `k - 1`.
 *
 * This trades the per-ordering re-init+re-index cost (the previous
 * prefix-cache implementation paid one per cache-miss ordering)
 * for a per-backtrack `git checkout` + `refreshLocal` cost. On
 * sessions where index time dominates, the window's wall-clock
 * drops materially.
 *
 * Why git checkout instead of JDT undo: an earlier attempt used
 * `Change.perform`'s undo Change to revert between siblings, but
 * JDT's per-file caches (working-copy buffers + JavaModel element
 * info) didn't reliably invalidate, producing corrupted commits on
 * the next forward apply. Git checkout + refreshLocal goes through
 * Eclipse's standard out-of-band-edit pathway, which IDE users
 * exercise constantly and which JDT handles cleanly.
 */
class ReorderSynthesiser(
    private val applySpec: (RefactoringSpec, Path) -> SpecDispatcher.Result,
    private val refreshProject: () -> Unit,
    private val runInSession: (() -> Unit) -> Unit,
    private val shadowGit: GitRunner,
    private val pool: WorktreePool,
    private val budget: EnumerationBudget = EnumerationBudget(),
    /** Hash a file in a live worktree. Default: [JavaFileAstHasher.hashFile].
     *  Tests inject spies / stubs. */
    private val hashWorktreeFile: (Path, String) -> String? = JavaFileAstHasher::hashFile,
    /** Hash a file at a given SHA in [shadowGit]. Default uses
     *  [JavaFileAstHasher.hashFileAtSha]. Tests inject spies / stubs. */
    private val hashShaFile: (String, String) -> String? =
        { sha, path -> JavaFileAstHasher.hashFileAtSha(shadowGit, sha, path) },
) {

    /** Wires the production seams: real [SpecDispatcher] + the
     *  bundle's `withBatchSession`. Tests use the primary
     *  constructor with fakes. */
    constructor(
        client: RefactoringClient,
        shadowGit: GitRunner,
        pool: WorktreePool,
        dispatcher: SpecDispatcher = SpecDispatcher(client),
        budget: EnumerationBudget = EnumerationBudget(),
    ) : this(
        applySpec = dispatcher::apply,
        refreshProject = { client.refreshProject() },
        runInSession = { body -> client.withBatchSession(body) },
        shadowGit = shadowGit,
        pool = pool,
        budget = budget,
    )

    data class Summary(
        val trajectories: List<ReorderTrajectory>,
        // Window-splitting counts (migrated from the deleted ReorderWindowLogger).
        val totalWindows: Int,
        val eligibleWindows: Int,
        val singletonWindows: Int,
        val typedCount: Int,
        val untypedCount: Int,
        val divergentCount: Int,
        val refactorFailedCount: Int,
        // Synthesis counts.
        val orderingsSynthesised: Int,
        val commitsCreated: Int,
        // Apply / backtrack call counts at the trie level. For a
        // clean DFS, applies == backtracks == distinct prefixes
        // visited.
        val appliesIssued: Int,
        val backtracksIssued: Int,
        // Terminal AST-equivalence audit. Every materialised terminal
        // prefix (= leaf of the trie = complete ordering) is
        // hash-compared against the user's `windowToSha` over the
        // user-changed file set. Divergent terminals are filtered
        // out of [trajectories] before return.
        val terminalsChecked: Int,
        val terminalsAstMatched: Int,
        val terminalsAstDiverged: Int,
    )

    /** Per-prefix DFS outcome. `Materialised` means the apply
     *  succeeded and the resulting commit was forced under the
     *  per-prefix ref. `Failed` means the apply or the
     *  staged-changes check failed and no commit exists.
     *
     *  [Materialised.terminalDivergence] is non-null iff the
     *  prefix is a terminal (= a complete ordering) AND its tree
     *  did not AST-match the user's `windowToSha`. Non-terminals
     *  always carry null (no check ran). */
    private sealed interface NodeResult {
        data class Materialised(
            val sha: String,
            val ref: String,
            val terminalDivergence: TerminalDivergence? = null,
        ) : NodeResult
        data class Failed(val reason: String) : NodeResult
    }

    private data class TerminalDivergence(val divergedFiles: List<String>)

    fun run(
        steps: List<RefactoringStep>,
        validations: Map<Int, RefactoringStepValidator.StepValidation>,
        log: (String) -> Unit,
    ): Summary {
        val windows = splitOnInvalid(steps, validations, log)
        val typedCount = windows.sumOf { it.size }
        var untyped = 0
        var diverged = 0
        var failed = 0
        for (s in steps) {
            when (validations[s.stepIndex]?.status) {
                RefactoringStepValidator.Status.UNTYPED -> untyped++
                RefactoringStepValidator.Status.AST_DIVERGED -> diverged++
                RefactoringStepValidator.Status.REFACTOR_FAILED -> failed++
                else -> Unit
            }
        }

        var eligible = 0
        var singletons = 0
        var orderingsSynthesised = 0
        var commitsCreated = 0
        var appliesIssued = 0
        var backtracksIssued = 0
        var terminalsChecked = 0
        var terminalsAstMatched = 0
        var terminalsAstDiverged = 0
        val trajectories = mutableListOf<ReorderTrajectory>()

        for ((wIdx, window) in windows.withIndex()) {
            val typedSpecs = window.mapNotNull { it.spec }
            if (typedSpecs.size < 2) {
                singletons++
                continue
            }
            eligible++
            val from = shortSha(window.first().fromSha)
            val to = shortSha(window.last().toSha)
            val indices = window.map { it.stepIndex }
            log("reorder synth: window #$wIdx $from→$to (${typedSpecs.size} typed specs, step indices=$indices):")
            ReorderDebug.describe(typedSpecs).lineSequence().forEach { line ->
                log("  $line")
            }

            val dag = SpecDependencyAnalyzer.analyze(typedSpecs)
            val enumeration = TopologicalEnumerator.enumerate(dag, budget)
            if (enumeration.skipReason != null) {
                log("  reorder synth: skipping window #$wIdx — ${enumeration.skipReason}")
                continue
            }
            if (enumeration.truncated) {
                log("  reorder synth: window #$wIdx enumerator truncated at ${enumeration.orderings.size} orderings")
            }

            val identity = (0 until typedSpecs.size).toList()
            val altOrderings = enumeration.orderings.filter { it != identity }
            log("  reorder synth: window #$wIdx — synthesising ${altOrderings.size} alt ordering(s)")
            if (altOrderings.isEmpty()) continue

            val trie = PrefixTrie.of(altOrderings)
            val prefixOutcomes = mutableMapOf<List<Int>, NodeResult>()
            val fromSha = window.first().fromSha
            val windowToSha = window.last().toSha
            val windowSpecLabels = window.map { it.refactoring.description }
            log("  reorder synth: window #$wIdx — trie has ${trie.size()} distinct prefix(es)")

            // Pre-compute the user-changed `.java` set for this window
            // and each path's canonical AST hash at `windowToSha`. We
            // pull from the shadow repo via `git show` so we don't
            // need a second worktree borrow. These hashes are the
            // ground truth every terminal ordering is checked against.
            val userChanged = shadowGit.changedJavaFilesBetween(fromSha, windowToSha)
            val userHashes: Map<String, String?> =
                userChanged.associateWith { hashShaFile(windowToSha, it) }

            val worktree = pool.borrow(fromSha)
            try {
                val worktreeGit = GitRunner(worktree)
                worktreeGit.setLocalIdentity("alt@analysis.local", "Alternative Trajectory")
                val counters = DfsCounters()
                runInSession {
                    walkTrie(
                        node = trie.root,
                        prefix = emptyList(),
                        parentSha = fromSha,
                        windowIdx = wIdx,
                        typedSpecs = typedSpecs,
                        worktree = worktree,
                        worktreeGit = worktreeGit,
                        prefixOutcomes = prefixOutcomes,
                        counters = counters,
                        userChanged = userChanged,
                        userHashes = userHashes,
                        log = log,
                    )
                }
                appliesIssued += counters.applies
                backtracksIssued += counters.backtracks
                terminalsChecked += counters.terminalsChecked
                terminalsAstMatched += counters.terminalsAstMatched
                terminalsAstDiverged += counters.terminalsAstDiverged
            } finally {
                pool.release(worktree)
            }

            val orderingResults = altOrderings.mapIndexed { ordIdx, ordering ->
                buildOrdering(
                    ordIdx = ordIdx,
                    ordering = ordering,
                    windowSpecLabels = windowSpecLabels,
                    prefixOutcomes = prefixOutcomes,
                )
            }
            // Filter terminally-divergent orderings out of the report.
            // Their commits + refs persist in the shadow repo for
            // offline forensics; the report only carries
            // end-state-equivalent ones so Slice 2b scoring compares
            // like-for-like.
            val (kept, divergent) = orderingResults.partition { it.terminalDivergedFiles == null }
            if (divergent.isNotEmpty()) {
                log(
                    "  reorder synth: window #$wIdx — filtering ${divergent.size} divergent " +
                        "ordering(s): ${divergent.map { it.permutation }}",
                )
            }
            orderingsSynthesised += kept.size
            commitsCreated += kept.sumOf { it.stepShas.size }

            trajectories.add(
                ReorderTrajectory(
                    windowIndex = wIdx,
                    windowFromSha = fromSha,
                    windowToSha = windowToSha,
                    windowStepIndexes = indices,
                    windowSpecLabels = windowSpecLabels,
                    orderings = kept,
                ),
            )
        }

        return Summary(
            trajectories = trajectories,
            totalWindows = windows.size,
            eligibleWindows = eligible,
            singletonWindows = singletons,
            typedCount = typedCount,
            untypedCount = untyped,
            divergentCount = diverged,
            refactorFailedCount = failed,
            orderingsSynthesised = orderingsSynthesised,
            commitsCreated = commitsCreated,
            appliesIssued = appliesIssued,
            backtracksIssued = backtracksIssued,
            terminalsChecked = terminalsChecked,
            terminalsAstMatched = terminalsAstMatched,
            terminalsAstDiverged = terminalsAstDiverged,
        )
    }

    private class DfsCounters {
        var applies: Int = 0
        var backtracks: Int = 0
        var terminalsChecked: Int = 0
        var terminalsAstMatched: Int = 0
        var terminalsAstDiverged: Int = 0
    }

    /**
     * DFS over the prefix trie. Forward edge → apply spec, commit,
     * branchForce. Back edge → `git checkout --detach parentSha` +
     * `refreshProject` so Eclipse re-stats files and JDT picks up
     * the new on-disk state.
     *
     * On forward failure (apply Failed, or no staged changes): record
     * the node as Failed, skip its subtree, return without
     * backtracking (no commit was made; HEAD unchanged from parent).
     */
private fun walkTrie(
        node: PrefixTrie.Node,
        prefix: List<Int>,
        parentSha: String,
        windowIdx: Int,
        typedSpecs: List<RefactoringSpec>,
        worktree: Path,
        worktreeGit: GitRunner,
        prefixOutcomes: MutableMap<List<Int>, NodeResult>,
        counters: DfsCounters,
        userChanged: Set<String>,
        userHashes: Map<String, String?>,
        log: (String) -> Unit,
    ) {
        var mySha: String = parentSha

        if (prefix.isNotEmpty()) {
            counters.applies++
            val specIdx = prefix.last()
            val spec = typedSpecs[specIdx]
            val stepStart = System.currentTimeMillis()
            val outcome = applySpec(spec, worktree)
            val applyMs = System.currentTimeMillis() - stepStart
            when (outcome) {
                is SpecDispatcher.Result.Failed -> {
                    val reason = "apply: ${outcome.reason}"
                    prefixOutcomes[prefix] = NodeResult.Failed(reason)
                    log("    [depth ${prefix.size} prefix=$prefix specIdx=$specIdx ${specLabel(spec)}]: FAILED in ${applyMs}ms — ${outcome.reason}")
                    return
                }
                is SpecDispatcher.Result.Ok -> Unit
            }
            worktreeGit.addAll()
            if (!worktreeGit.hasStagedChanges()) {
                prefixOutcomes[prefix] = NodeResult.Failed("apply: no textual change")
                log("    [depth ${prefix.size} prefix=$prefix specIdx=$specIdx ${specLabel(spec)}]: no textual change after ${applyMs}ms")
                // Apply mutated some bundle-internal state but produced
                // no diff. Force the worktree back to parent so the
                // sibling apply starts from a known clean base.
                worktreeGit.checkoutDetach(parentSha)
                runCatching { refreshProject() }
                return
            }
            val sha = worktreeGit.commit("reorder win=$windowIdx prefix=${encodePrefix(prefix)}")
            val ref = "reorder/win$windowIdx/path/${encodePrefix(prefix)}"
            shadowGit.branchForce(ref, sha)

            // Terminal-only AST equivalence check. The worktree is
            // currently AT this terminal SHA — no extra checkout
            // needed. Hash only the user-changed file set; compare
            // path-by-path to the user's `windowToSha` hashes.
            val divergence: TerminalDivergence? =
                if (prefix.size == typedSpecs.size) {
                    val res = checkTerminalDivergence(worktree, userChanged, userHashes)
                    counters.terminalsChecked++
                    if (res == null) {
                        counters.terminalsAstMatched++
                        log("    [depth ${prefix.size} prefix=$prefix specIdx=$specIdx ${specLabel(spec)}]: ok in ${applyMs}ms → ${shortSha(sha)} (terminal ast-match ✓)")
                    } else {
                        counters.terminalsAstDiverged++
                        log("    [depth ${prefix.size} prefix=$prefix specIdx=$specIdx ${specLabel(spec)}]: ok in ${applyMs}ms → ${shortSha(sha)} (terminal ast-divergence — ${res.divergedFiles.size} file(s) differ: ${res.divergedFiles})")
                    }
                    res
                } else {
                    log("    [depth ${prefix.size} prefix=$prefix specIdx=$specIdx ${specLabel(spec)}]: ok in ${applyMs}ms → ${shortSha(sha)}")
                    null
                }
            prefixOutcomes[prefix] = NodeResult.Materialised(
                sha = sha,
                ref = ref,
                terminalDivergence = divergence,
            )
            mySha = sha
        }

        for ((_, childNode) in node.children) {
            val childPrefix = prefix + (childNode.specIdx ?: error("non-root child must have specIdx"))
            walkTrie(
                node = childNode,
                prefix = childPrefix,
                parentSha = mySha,
                windowIdx = windowIdx,
                typedSpecs = typedSpecs,
                worktree = worktree,
                worktreeGit = worktreeGit,
                prefixOutcomes = prefixOutcomes,
                counters = counters,
                userChanged = userChanged,
                userHashes = userHashes,
                log = log,
            )
        }

        // Backtrack — only if this node materialised a commit (= mySha
        // moved away from parentSha). Failed / no-change nodes already
        // restored to parent inline above.
        if (prefix.isNotEmpty() && mySha != parentSha) {
            counters.backtracks++
            val backStart = System.currentTimeMillis()
            // checkoutDetach rewrites working-tree files AND moves
            // HEAD; refreshProject then tells Eclipse to re-stat
            // everything. Standard "files changed under us" pathway —
            // JDT exercises this constantly for IDE users.
            worktreeGit.checkoutDetach(parentSha)
            runCatching { refreshProject() }
            val backMs = System.currentTimeMillis() - backStart
            log("    [depth ${prefix.size} prefix=$prefix]: backtrack to ${shortSha(parentSha)} in ${backMs}ms")
        }
    }

    /**
     * Split [steps] on every step whose validation status is not
     * [RefactoringStepValidator.Status.VALID]. Steps missing from
     * [validations] are conservatively treated as splitters.
     * Verbatim from the deleted `ReorderWindowLogger.splitOnInvalid`.
     */
    private fun splitOnInvalid(
        steps: List<RefactoringStep>,
        validations: Map<Int, RefactoringStepValidator.StepValidation>,
        log: (String) -> Unit,
    ): List<List<RefactoringStep>> {
        val windows = mutableListOf<List<RefactoringStep>>()
        var current = mutableListOf<RefactoringStep>()
        for (step in steps) {
            val v = validations[step.stepIndex]
            val isValid = v?.status == RefactoringStepValidator.Status.VALID
            if (isValid) {
                current.add(step)
            } else {
                if (current.isNotEmpty()) {
                    windows.add(current)
                    current = mutableListOf()
                }
                val status = v?.status?.name ?: "UNVALIDATED"
                val reason = v?.reason?.let { " — $it" } ?: ""
                log("reorder synth: split at step #${step.stepIndex} ($status$reason)")
            }
        }
        if (current.isNotEmpty()) windows.add(current)
        return windows
    }

    private fun buildOrdering(
        ordIdx: Int,
        ordering: List<Int>,
        windowSpecLabels: List<String>,
        prefixOutcomes: Map<List<Int>, NodeResult>,
    ): ReorderOrdering {
        val stepShas = mutableListOf<String>()
        val branchRefs = mutableListOf<String>()
        var failedAt: Int? = null
        var terminalDivergedFiles: List<String>? = null
        for (depth in 1..ordering.size) {
            val prefix = ordering.subList(0, depth)
            when (val r = prefixOutcomes[prefix]) {
                is NodeResult.Materialised -> {
                    stepShas.add(r.sha)
                    branchRefs.add(r.ref)
                    if (depth == ordering.size && r.terminalDivergence != null) {
                        terminalDivergedFiles = r.terminalDivergence.divergedFiles
                    }
                }
                is NodeResult.Failed -> {
                    failedAt = depth - 1
                    break
                }
                null -> {
                    // Subtree skipped because an ancestor failed.
                    failedAt = depth - 1
                    break
                }
            }
        }
        return ReorderOrdering(
            orderIndex = ordIdx,
            permutation = ordering,
            permutationLabels = ordering.map { windowSpecLabels[it] },
            stepShas = stepShas,
            branchRefs = branchRefs,
            terminalSuccess = failedAt == null,
            failedAt = failedAt,
            terminalDivergedFiles = terminalDivergedFiles,
        )
    }

    /**
     * Per-file AST hash comparison between the worktree's current
     * state (a terminal SHA the DFS just committed at) and the
     * user's pre-computed [userHashes] for `windowToSha`. Returns
     * null if every user-changed path matches; otherwise the list
     * of divergent paths.
     *
     * Mirrors `RefactoringStepValidator.kt:181, :191-205` — same
     * predicate (`ours == null || theirs == null || ours != theirs`)
     * so an unparseable / missing file on either side counts as a
     * divergence rather than a silent match.
     */
    private fun checkTerminalDivergence(
        worktree: Path,
        userChanged: Set<String>,
        userHashes: Map<String, String?>,
    ): TerminalDivergence? {
        val mismatching = userChanged.filter { path ->
            val ours = hashWorktreeFile(worktree, path)
            val theirs = userHashes[path]
            ours == null || theirs == null || ours != theirs
        }
        return if (mismatching.isEmpty()) null else TerminalDivergence(mismatching)
    }

    private fun shortSha(sha: String): String = if (sha.length <= 8) sha else sha.take(8)

    /** Encode a prefix as a dash-joined string of spec indices for
     *  ref naming. Empty prefix → "root" (only used for log lines;
     *  the root itself never gets a ref). */
    private fun encodePrefix(prefix: List<Int>): String =
        if (prefix.isEmpty()) "root" else prefix.joinToString("-")

    /** One-word label for [spec], used in per-step progress lines. */
    private fun specLabel(spec: RefactoringSpec): String = spec::class.simpleName ?: "Spec"
}
