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
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Computes [CheckpointMetrics] for every unique commit SHA produced by the
 * reconstruct stage and writes one `<sha>.json` under
 * `<sessionFolder>/checkpoint-metrics/`.
 *
 * Unique-SHA dedup matters because a long session produces hundreds of events
 * but far fewer unique commits — every no-op event collapses onto its
 * preceding SHA.
 *
 * Parallelism is the pool size: each concurrent task borrows a dedicated
 * `git worktree` from [WorktreePool] (fresh-per-SHA, no cross-checkpoint
 * pollution) and runs CK + PMD + Gradle build + Gradle test sequentially
 * inside it. Any failure in any section aborts the whole run — a written
 * JSON file is always complete.
 *
 * Re-running is idempotent: existing `<sha>.json` files are kept as-is so
 * incremental runs (after adding a new session or tweaking config) skip the
 * expensive Gradle subprocesses for SHAs already computed.
 */
class MetricsRunner(
    private val parallelism: Int = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1),
    private val gradleUserHome: Path? = null,
    private val buildTimeout: Duration = Duration.ofMinutes(5),
    private val testTimeout: Duration = Duration.ofMinutes(10),
    private val json: Json = Json { prettyPrint = true; encodeDefaults = true },
) {

    data class Summary(
        val totalShas: Int,
        val computed: Int,
        val reused: Int,
        val buildOk: Int,
        val testsOk: Int,
        // Ordered by first-appearance of the SHA in the normalized event stream,
        // so callers can build a chronological report without re-deriving order.
        val checkpoints: List<CheckpointMetrics>,
        // Keyed by SHA. Diff is vs. the previous checkpoint (or the seed commit
        // for the first). Never cached on disk — recomputed each run because it
        // depends on the ordering of the current event stream, not just the SHA.
        val diffBySha: Map<String, DiffStats> = emptyMap(),
        // Metrics for any synthesised alternative-trajectory SHAs the caller
        // passed via [run]'s `alternativeShas` parameter. Sibling of
        // [checkpoints]; same `<sha>.json` cache used.
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

        val outputDir = sessionFolder.resolve("checkpoint-metrics")
        Files.createDirectories(outputDir)
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
        val reused = ConcurrentHashMap.newKeySet<String>()

        val pool = WorktreePool(reconstruction.repoDir, worktreeBase, parallelism)
        val executor = Executors.newFixedThreadPool(parallelism)

        val results = try {
            val futures = allShas.map { sha ->
                executor.submit<CheckpointMetrics> {
                    computeOne(sha, pool, outputDir, reused)
                }
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
            computed = allShas.size - reused.size,
            reused = reused.size,
            buildOk = traceCheckpoints.count { it.build.success },
            testsOk = traceCheckpoints.count { it.tests.success },
            checkpoints = traceCheckpoints,
            diffBySha = diffBySha,
            alternativeCheckpoints = altCheckpoints,
        )
    }

    private fun computeOne(
        sha: String,
        pool: WorktreePool,
        outputDir: Path,
        reused: MutableSet<String>,
    ): CheckpointMetrics {
        val outputFile = outputDir.resolve("$sha.json")
        if (Files.isRegularFile(outputFile)) {
            reused += sha
            return json.decodeFromString(CheckpointMetrics.serializer(), Files.readString(outputFile))
        }

        val worktree = pool.borrow(sha)
        val metrics = try {
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

        Files.writeString(outputFile, json.encodeToString(CheckpointMetrics.serializer(), metrics))
        return metrics
    }
}
