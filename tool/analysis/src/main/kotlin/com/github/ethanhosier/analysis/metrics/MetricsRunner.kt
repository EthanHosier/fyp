package com.github.ethanhosier.analysis.metrics

import com.github.ethanhosier.analysis.metrics.ck.CkRunner
import com.github.ethanhosier.analysis.metrics.gradlebuild.GradleBuildRunner
import com.github.ethanhosier.analysis.metrics.model.CheckpointMetrics
import com.github.ethanhosier.analysis.metrics.pmd.PmdRunner
import com.github.ethanhosier.analysis.metrics.tests.GradleTestRunner
import com.github.ethanhosier.analysis.model.ReconstructionResult
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
    )

    fun run(reconstruction: ReconstructionResult, sessionFolder: Path): Summary {
        val outputDir = sessionFolder.resolve("checkpoint-metrics")
        Files.createDirectories(outputDir)
        val worktreeBase = sessionFolder.resolve("shadow-worktrees")

        // LinkedHashSet: keep chronological iteration order from the event
        // log so progress output lands in the order a human would expect.
        val uniqueShas = reconstruction.eventCommits.mapping.values.toCollection(LinkedHashSet())
        val reused = ConcurrentHashMap.newKeySet<String>()

        val pool = WorktreePool(reconstruction.repoDir, worktreeBase, parallelism)
        val executor = Executors.newFixedThreadPool(parallelism)

        val results = try {
            val futures = uniqueShas.map { sha ->
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

        return Summary(
            totalShas = uniqueShas.size,
            computed = uniqueShas.size - reused.size,
            reused = reused.size,
            buildOk = results.count { it.build.success },
            testsOk = results.count { it.tests.success },
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
            val build = GradleBuildRunner(
                timeout = buildTimeout,
                gradleUserHome = gradleUserHome,
            ).run(worktree)
            val tests = GradleTestRunner(
                timeout = testTimeout,
                gradleUserHome = gradleUserHome,
            ).run(worktree)
            CheckpointMetrics(sha, ck, pmd, build, tests)
        } finally {
            pool.release(worktree)
        }

        Files.writeString(outputFile, json.encodeToString(CheckpointMetrics.serializer(), metrics))
        return metrics
    }
}
