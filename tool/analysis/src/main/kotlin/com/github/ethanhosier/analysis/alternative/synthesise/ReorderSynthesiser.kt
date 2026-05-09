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
 * shadow repo, with an ordered-prefix cache so orderings sharing
 * a prefix re-use the materialised commits.
 *
 * Replaces the previous [com.github.ethanhosier.analysis.alternative.reorder.ReorderWindowLogger]
 * (deleted) — absorbs that class's window-splitting logic and
 * summary counts, adds the synthesis loop on top.
 *
 * Failure semantics: any per-step apply failure short-circuits
 * the *current ordering* (not the whole window); the successful
 * prefix is kept (committed, ref'd, cached for later orderings).
 *
 * No metrics. Slice 2b runs `MetricsRunner` on the synthesised
 * SHAs.
 */
class ReorderSynthesiser(
    private val applySpec: (RefactoringSpec, Path) -> SpecDispatcher.Result,
    private val runInSession: (() -> Unit) -> Unit,
    private val shadowGit: GitRunner,
    private val pool: WorktreePool,
    private val budget: EnumerationBudget = EnumerationBudget(),
) {

    /** Wires the production seams: real [SpecDispatcher] + the
     *  bundle's batch session. Tests use the primary constructor
     *  with fakes. */
    constructor(
        client: RefactoringClient,
        shadowGit: GitRunner,
        pool: WorktreePool,
        dispatcher: SpecDispatcher = SpecDispatcher(client),
        budget: EnumerationBudget = EnumerationBudget(),
    ) : this(
        applySpec = dispatcher::apply,
        runInSession = { body -> client.withBatchSession(body) },
        shadowGit = shadowGit,
        pool = pool,
        budget = budget,
    )

    data class Summary(
        val trajectories: List<ReorderTrajectory>,
        // Window-splitting counts (migrated from ReorderWindowLogger.Summary).
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
        val prefixCacheHits: Int,
    )

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
        var prefixCacheHits = 0
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
            log(
                "reorder synth: window #$wIdx $from→$to " +
                    "(${typedSpecs.size} typed specs, step indices=$indices):",
            )
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

            // Drop the user's own ordering (identity permutation): its
            // commit chain already exists in the user's actual trace.
            val identity = (0 until typedSpecs.size).toList()
            val altOrderings = enumeration.orderings.filter { it != identity }
            log("  reorder synth: window #$wIdx — synthesising ${altOrderings.size} alt ordering(s)")

            // Pre-compute once (used both per-ordering and on the
            // trajectory itself).
            val windowSpecLabels = window.map { it.refactoring.description }
            val cache = PrefixCache(fromSha = window.first().fromSha)
            val results = mutableListOf<ReorderOrdering>()
            for ((ordIdx, ordering) in altOrderings.withIndex()) {
                val (prefixHit, hitSha) = cache.deepestHit(ordering)
                if (prefixHit.isNotEmpty()) prefixCacheHits++
                val cachePrelude = if (prefixHit.isNotEmpty()) " (cache hit on prefix $prefixHit)" else " (cache miss)"
                log("  ord ${ordIdx + 1}/${altOrderings.size} $ordering: starting$cachePrelude")
                val orderStart = System.currentTimeMillis()
                val res = synthesiseOrdering(
                    window = window,
                    typedSpecs = typedSpecs,
                    windowSpecLabels = windowSpecLabels,
                    ordering = ordering,
                    cachedPrefix = prefixHit,
                    cachedPrefixSha = hitSha,
                    cache = cache,
                    windowIdx = wIdx,
                    orderIdx = ordIdx,
                    log = log,
                )
                val orderMs = System.currentTimeMillis() - orderStart
                val failNote = if (!res.terminalSuccess) " — failed at ${res.failedAt}" else ""
                log("  ord ${ordIdx + 1}/${altOrderings.size} $ordering: ${res.stepShas.size} commit(s) in ${orderMs}ms$failNote")
                orderingsSynthesised++
                commitsCreated += res.stepShas.size
                results.add(res)
            }

            trajectories.add(
                ReorderTrajectory(
                    windowIndex = wIdx,
                    windowFromSha = window.first().fromSha,
                    windowToSha = window.last().toSha,
                    windowStepIndexes = indices,
                    windowSpecLabels = windowSpecLabels,
                    orderings = results,
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
            prefixCacheHits = prefixCacheHits,
        )
    }

    private fun synthesiseOrdering(
        window: List<RefactoringStep>,
        typedSpecs: List<RefactoringSpec>,
        windowSpecLabels: List<String>,
        ordering: List<Int>,
        cachedPrefix: List<Int>,
        cachedPrefixSha: String,
        cache: PrefixCache,
        windowIdx: Int,
        orderIdx: Int,
        log: (String) -> Unit = {},
    ): ReorderOrdering {
        // Suffix to apply: drop the cached prefix; everything after is fresh work.
        val suffix = ordering.drop(cachedPrefix.size)
        val stepShas = mutableListOf<String>()
        val branchRefs = mutableListOf<String>()
        var failedAt: Int? = null
        // Track the running (cumulative) prefix as we apply each suffix step,
        // so cache writes are addressed correctly.
        val currentPrefix = cachedPrefix.toMutableList()

        // Pre-fill stepShas + branchRefs with the cached prefix's SHAs so
        // the result is a complete `permutation.size`-long trajectory
        // regardless of cache hits. Each ordering gets its own ref
        // namespace; the same SHA may now be reachable from multiple refs
        // (the original ordering's and this ordering's cache-hit prefix).
        for (k in 1..cachedPrefix.size) {
            val prefixSlice = cachedPrefix.subList(0, k)
            val prefixSha = cache.shaFor(prefixSlice)
                ?: error("prefix cache reported deepestHit length ${cachedPrefix.size} but $prefixSlice is missing")
            val ref = "reorder/win$windowIdx/ord$orderIdx/step-${k - 1}"
            shadowGit.branchForce(ref, prefixSha)
            stepShas.add(prefixSha)
            branchRefs.add(ref)
        }

        val worktree = pool.borrow(cachedPrefixSha)
        try {
            val worktreeGit = GitRunner(worktree)
            worktreeGit.setLocalIdentity("alt@analysis.local", "Alternative Trajectory")

            runInSession {
                var aborted = false
                for ((suffixIdx, specIdx) in suffix.withIndex()) {
                    if (aborted) break
                    val spec = typedSpecs[specIdx]
                    val stepStart = System.currentTimeMillis()
                    val outcome = applySpec(spec, worktree)
                    if (outcome is SpecDispatcher.Result.Failed) {
                        failedAt = cachedPrefix.size + suffixIdx
                        aborted = true
                        log(
                            "    step ${cachedPrefix.size + suffixIdx + 1}/${ordering.size} " +
                                "(specIdx=$specIdx ${specLabel(spec)}): FAILED — ${outcome.reason}",
                        )
                        continue
                    }
                    worktreeGit.addAll()
                    if (!worktreeGit.hasStagedChanges()) {
                        // Empty refactoring (no textual change). Treat as a
                        // partial-failure: we can't address what we didn't
                        // commit, and the caller's expectation is one
                        // commit per applied step.
                        failedAt = cachedPrefix.size + suffixIdx
                        aborted = true
                        log(
                            "    step ${cachedPrefix.size + suffixIdx + 1}/${ordering.size} " +
                                "(specIdx=$specIdx ${specLabel(spec)}): no textual change",
                        )
                        continue
                    }
                    val sha = worktreeGit.commit(
                        "reorder win=$windowIdx ord=$orderIdx step=$specIdx",
                    )
                    val ref = "reorder/win$windowIdx/ord$orderIdx/step-${currentPrefix.size}"
                    shadowGit.branchForce(ref, sha)
                    currentPrefix.add(specIdx)
                    cache.put(currentPrefix.toList(), sha)
                    stepShas.add(sha)
                    branchRefs.add(ref)
                    val stepMs = System.currentTimeMillis() - stepStart
                    log(
                        "    step ${cachedPrefix.size + suffixIdx + 1}/${ordering.size} " +
                            "(specIdx=$specIdx ${specLabel(spec)}): ok in ${stepMs}ms → ${shortSha(sha)}",
                    )
                }
            }
        } finally {
            pool.release(worktree)
        }

        return ReorderOrdering(
            orderIndex = orderIdx,
            permutation = ordering,
            permutationLabels = ordering.map { windowSpecLabels[it] },
            stepShas = stepShas,
            branchRefs = branchRefs,
            terminalSuccess = failedAt == null,
            failedAt = failedAt,
        )
    }

    /**
     * Split [steps] on every step whose validation status is not
     * [RefactoringStepValidator.Status.VALID]. Steps missing from
     * [validations] are conservatively treated as splitters with an
     * `UNVALIDATED` reason. Verbatim copy of the same private fn on
     * the deleted `ReorderWindowLogger`.
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

    private fun shortSha(sha: String): String = if (sha.length <= 8) sha else sha.take(8)

    /** One-word label for [spec], used in per-step progress lines. */
    private fun specLabel(spec: RefactoringSpec): String = spec::class.simpleName ?: "Spec"
}
