package com.github.ethanhosier.analysis.metrics

import com.github.ethanhosier.analysis.metrics.exec.CheckpointExecutor
import com.github.ethanhosier.analysis.metrics.gitdiff.DiffRunner
import com.github.ethanhosier.analysis.metrics.gitdiff.DiffStats
import com.github.ethanhosier.analysis.metrics.model.CheckpointMetrics
import com.github.ethanhosier.analysis.model.ReconstructionResult
import com.github.ethanhosier.analysis.reconstruct.GitRunner
import java.util.concurrent.ExecutionException

/**
 * Computes [CheckpointMetrics] for every unique commit SHA produced by the
 * reconstruct stage, plus any synthesised alternative-trajectory SHAs the
 * caller passes alongside.
 *
 * Per-SHA work is delegated to a [CheckpointExecutor]: the local
 * implementation runs in-process across a worktree pool; a future remote
 * implementation will fan out to AWS Lambda. This class itself is purely
 * orchestration — ordering submissions, propagating failures, and stitching
 * results into a [Summary] alongside the (still-local) git-diff stats.
 *
 * Unique-SHA dedup matters because a long session produces hundreds of events
 * but far fewer unique commits — every no-op event collapses onto its
 * preceding SHA.
 *
 * No on-disk caching: each pipeline run recomputes every SHA from scratch.
 * Sessions are processed once and the surrounding orchestration assumes
 * that, so caching just adds invalidation surface for no real win.
 */
class MetricsRunner(
    private val executor: CheckpointExecutor,
) {

    data class Summary(
        val totalShas: Int,
        val computed: Int,
        val buildOk: Int,
        val testsOk: Int,
        // Ordered by first-appearance of the SHA in the normalized event stream,
        // so callers can build a chronological report without re-deriving order.
        val checkpoints: List<CheckpointMetrics>,
        // Keyed by SHA. Diff is vs. the previous checkpoint (or the seed commit
        // for the first).
        val diffBySha: Map<String, DiffStats> = emptyMap(),
        // Metrics for any synthesised alternative-trajectory SHAs the caller
        // passed via [run]'s `alternativeShas` parameter. Sibling of
        // [checkpoints].
        val alternativeCheckpoints: List<CheckpointMetrics> = emptyList(),
    )

    /**
     * @param reconstruction the user's actual trace; each unique SHA in
     *   `eventCommits` becomes a checkpoint.
     * @param alternativeShas synthesised SHAs (e.g. from
     *   `AlternativeTrajectoryRunner`) that should be measured alongside
     *   the trace SHAs. Each must already be reachable in
     *   `reconstruction.repoDir` (typically via a ref attached by the
     *   caller). Diff is computed pairwise against
     *   `alternativeFromShas[i]` rather than against the previous SHA in
     *   the list, so non-adjacent ancestor chains work too.
     * @param alternativeFromShas the parent SHA each entry in
     *   [alternativeShas] should be diffed against. Must have the same
     *   length as [alternativeShas]. Empty list when [alternativeShas]
     *   is empty.
     */
    fun run(
        reconstruction: ReconstructionResult,
        alternativeShas: List<String> = emptyList(),
        alternativeFromShas: List<String> = emptyList(),
    ): Summary {
        require(alternativeShas.size == alternativeFromShas.size) {
            "alternativeShas and alternativeFromShas must be parallel; got " +
                "${alternativeShas.size} vs ${alternativeFromShas.size}"
        }

        // LinkedHashSet: keep chronological iteration order from the event
        // log so progress output lands in the order a human would expect.
        val uniqueShas = reconstruction.eventCommits.mapping.values.toCollection(LinkedHashSet())
        val altShasSet = alternativeShas.toCollection(LinkedHashSet())
        // Union for the executor; preserve trace-order, then alt-order.
        val allShas = LinkedHashSet<String>().apply {
            addAll(uniqueShas)
            addAll(altShasSet)
        }

        val futures = allShas.map { sha -> executor.submit(sha) }
        // Propagate the first failure out — .get() wraps thrown exceptions
        // in ExecutionException; unwrap so the stack trace points at the
        // real cause (e.g. a Gradle timeout, a git failure).
        val results = futures.map { future ->
            try {
                future.get()
            } catch (e: ExecutionException) {
                throw e.cause ?: e
            }
        }

        val byShaResult = results.associateBy { it.sha }
        val traceCheckpoints = uniqueShas.mapNotNull { byShaResult[it] }
        val altCheckpoints = altShasSet.mapNotNull { byShaResult[it] }

        val diffRunner = DiffRunner(GitRunner(reconstruction.repoDir))
        val traceDiffs = diffRunner.runAll(uniqueShas.toList())
        val altDiffs = diffRunner.runPairs(alternativeFromShas.zip(alternativeShas))
        // Trace diffs first so an alt SHA never silently shadows a real one
        // (no overlap is expected since alt SHAs are distinct synthetic commits).
        val diffBySha = LinkedHashMap<String, DiffStats>().apply {
            putAll(traceDiffs)
            putAll(altDiffs)
        }

        return Summary(
            totalShas = uniqueShas.size,
            computed = allShas.size,
            buildOk = traceCheckpoints.count { it.build.success },
            testsOk = traceCheckpoints.count { it.tests.success },
            checkpoints = traceCheckpoints,
            diffBySha = diffBySha,
            alternativeCheckpoints = altCheckpoints,
        )
    }
}
