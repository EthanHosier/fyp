package com.github.ethanhosier.analysis.metrics

import com.github.ethanhosier.analysis.metrics.ck.CkRunner
import com.github.ethanhosier.analysis.metrics.cpd.CpdRunner
import com.github.ethanhosier.analysis.metrics.gitdiff.DiffRunner
import com.github.ethanhosier.analysis.metrics.readability.ReadabilityRunner
import com.github.ethanhosier.analysis.metrics.gitdiff.DiffStats
import com.github.ethanhosier.analysis.metrics.gradlebuild.GradleBuildRunner
import com.github.ethanhosier.analysis.metrics.model.CheckpointMetrics
import com.github.ethanhosier.analysis.metrics.pmd.PmdRunner
import com.github.ethanhosier.analysis.metrics.tests.GradleTestRunner
import com.github.ethanhosier.analysis.metrics.tests.TestResult
import com.github.ethanhosier.analysis.model.ReconstructionResult
import com.github.ethanhosier.analysis.reconstruct.GitRunner
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Computes [CheckpointMetrics] for every unique commit SHA produced by the
 * reconstruct stage.
 *
 * Unique-SHA dedup matters because a long session produces hundreds of events
 * but far fewer unique commits — every no-op event collapses onto its
 * preceding SHA.
 *
 * Parallelism is the pool size: each concurrent task borrows a dedicated
 * `git worktree` from [WorktreePool] (fresh-per-SHA, no cross-checkpoint
 * pollution) and runs CK + PMD + Gradle build + Gradle test sequentially
 * inside it. Any failure in any section aborts the whole run.
 *
 * No on-disk caching: each pipeline run recomputes every SHA from scratch.
 * Sessions are processed once and the surrounding orchestration assumes
 * that, so caching just adds invalidation surface for no real win.
 */
class MetricsRunner(
    private val parallelism: Int = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1),
    private val gradleUserHome: Path? = null,
    private val buildTimeout: Duration = Duration.ofMinutes(5),
    private val testTimeout: Duration = Duration.ofMinutes(10),
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
        sessionFolder: Path,
        alternativeShas: List<String> = emptyList(),
        alternativeFromShas: List<String> = emptyList(),
    ): Summary {
        require(alternativeShas.size == alternativeFromShas.size) {
            "alternativeShas and alternativeFromShas must be parallel; got " +
                "${alternativeShas.size} vs ${alternativeFromShas.size}"
        }

        val worktreeBase = sessionFolder.resolve("shadow-worktrees")

        // LinkedHashSet: keep chronological iteration order from the event
        // log so progress output lands in the order a human would expect.
        val uniqueShas = reconstruction.eventCommits.mapping.values.toCollection(LinkedHashSet())
        val altShasSet = alternativeShas.toCollection(LinkedHashSet())
        // Union for the executor; preserve trace-order, then alt-order.
        val allShas = LinkedHashSet<String>().apply {
            addAll(uniqueShas)
            addAll(altShasSet)
        }

        val pool = WorktreePool(reconstruction.repoDir, worktreeBase, parallelism)
        val executor = Executors.newFixedThreadPool(parallelism)

        val results = try {
            val futures = allShas.map { sha ->
                executor.submit<CheckpointMetrics> { computeOne(sha, pool) }
            }
            // Propagate the first failure out — .get() wraps thrown exceptions
            // in ExecutionException; unwrap so the stack trace points at the
            // real cause (e.g. a Gradle timeout, a git failure).
            futures.map { future ->
                try {
                    future.get()
                } catch (e: ExecutionException) {
                    throw e.cause ?: e
                }
            }
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(1, TimeUnit.MINUTES)
            pool.close()
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

    private fun computeOne(sha: String, pool: WorktreePool): CheckpointMetrics {
        val worktree = pool.borrow(sha)
        return try {
            val ck = CkRunner().run(worktree)
            val pmd = PmdRunner().run(worktree)
            val cpd = CpdRunner().run(worktree)
            val readability = ReadabilityRunner().run(worktree)
            val build = GradleBuildRunner(
                timeout = buildTimeout,
                gradleUserHome = gradleUserHome,
            ).run(worktree)
            // Skip tests when build failed — `./gradlew test` re-runs compileJava
            // and hits the same error, wasting seconds per broken checkpoint
            // without producing any test outcome we didn't already know.
            val tests = if (build.success) {
                GradleTestRunner(
                    timeout = testTimeout,
                    gradleUserHome = gradleUserHome,
                ).run(worktree)
            } else {
                TestResult.skipped("build failed (exit ${build.exitCode})")
            }
            CheckpointMetrics(sha, ck, pmd, cpd, readability, build, tests)
        } finally {
            pool.release(worktree)
        }
    }
}
