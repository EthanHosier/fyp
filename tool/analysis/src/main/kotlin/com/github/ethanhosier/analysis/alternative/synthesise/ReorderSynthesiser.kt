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

class ReorderSynthesiser(
    private val applySpec: (RefactoringSpec, Path) -> SpecDispatcher.Result,
    private val refreshProject: () -> Unit,
    private val runInSession: (() -> Unit) -> Unit,
    private val shadowGit: GitRunner,
    private val pool: WorktreePool,
    private val budget: EnumerationBudget = EnumerationBudget(),
    private val hashWorktreeFile: (Path, String) -> String? = JavaFileAstHasher::hashFile,
    private val hashShaFile: (String, String) -> String? =
        { sha, path -> JavaFileAstHasher.hashFileAtSha(shadowGit, sha, path) },
) {

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
        val appliesIssued: Int,
        val backtracksIssued: Int,
        val terminalsChecked: Int,
        val terminalsAstMatched: Int,
        val terminalsAstDiverged: Int,
    )

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
            worktreeGit.addAllExcept(".project", ".classpath", ".settings")
            if (!worktreeGit.hasStagedChanges()) {
                prefixOutcomes[prefix] = NodeResult.Failed("apply: no textual change")
                log("    [depth ${prefix.size} prefix=$prefix specIdx=$specIdx ${specLabel(spec)}]: no textual change after ${applyMs}ms")
                worktreeGit.checkoutDetach(parentSha)
                runCatching { refreshProject() }
                return
            }
            val sha = worktreeGit.commit("reorder win=$windowIdx prefix=${encodePrefix(prefix)}")
            val ref = "reorder/win$windowIdx/path/${encodePrefix(prefix)}"
            shadowGit.branchForce(ref, sha)

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

        if (prefix.isNotEmpty() && mySha != parentSha) {
            counters.backtracks++
            val backStart = System.currentTimeMillis()
            worktreeGit.checkoutDetach(parentSha)
            runCatching { refreshProject() }
            val backMs = System.currentTimeMillis() - backStart
            log("    [depth ${prefix.size} prefix=$prefix]: backtrack to ${shortSha(parentSha)} in ${backMs}ms")
        }
    }

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

    private fun encodePrefix(prefix: List<Int>): String =
        if (prefix.isEmpty()) "root" else prefix.joinToString("-")

    private fun specLabel(spec: RefactoringSpec): String = spec::class.simpleName ?: "Spec"
}
