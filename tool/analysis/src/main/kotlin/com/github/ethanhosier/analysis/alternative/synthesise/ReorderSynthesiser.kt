package com.github.ethanhosier.analysis.alternative.synthesise

import com.github.ethanhosier.analysis.alternative.reorder.EnumerationBudget
import com.github.ethanhosier.analysis.alternative.reorder.ReorderDebug
import com.github.ethanhosier.analysis.alternative.reorder.SpecDependencyAnalyzer
import com.github.ethanhosier.analysis.alternative.reorder.TopologicalEnumerator
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
    )

    /** Per-prefix DFS outcome. `Materialised` means the apply
     *  succeeded and the resulting commit was forced under the
     *  per-prefix ref. `Failed` means the apply or the
     *  staged-changes check failed and no commit exists. */
    private sealed interface NodeResult {
        data class Materialised(val sha: String, val ref: String) : NodeResult
        data class Failed(val reason: String) : NodeResult
    }

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
            val windowSpecLabels = window.map { it.refactoring.description }
            log("  reorder synth: window #$wIdx — trie has ${trie.size()} distinct prefix(es)")

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
                        log = log,
                    )
                }
                appliesIssued += counters.applies
                backtracksIssued += counters.backtracks
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
            orderingsSynthesised += orderingResults.size
            commitsCreated += orderingResults.sumOf { it.stepShas.size }

            trajectories.add(
                ReorderTrajectory(
                    windowIndex = wIdx,
                    windowFromSha = fromSha,
                    windowToSha = window.last().toSha,
                    windowStepIndexes = indices,
                    windowSpecLabels = windowSpecLabels,
                    orderings = orderingResults,
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
        )
    }

    private class DfsCounters {
        var applies: Int = 0
        var backtracks: Int = 0
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
            prefixOutcomes[prefix] = NodeResult.Materialised(sha = sha, ref = ref)
            log("    [depth ${prefix.size} prefix=$prefix specIdx=$specIdx ${specLabel(spec)}]: ok in ${applyMs}ms → ${shortSha(sha)}")
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
        for (depth in 1..ordering.size) {
            val prefix = ordering.subList(0, depth)
            when (val r = prefixOutcomes[prefix]) {
                is NodeResult.Materialised -> {
                    stepShas.add(r.sha)
                    branchRefs.add(r.ref)
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
        )
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
