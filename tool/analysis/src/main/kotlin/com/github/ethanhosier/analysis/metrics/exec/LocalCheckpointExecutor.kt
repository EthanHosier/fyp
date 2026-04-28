package com.github.ethanhosier.analysis.metrics.exec

import com.github.ethanhosier.analysis.metrics.CheckpointMetricsComputer
import com.github.ethanhosier.analysis.metrics.GradleConfig
import com.github.ethanhosier.analysis.metrics.WorktreePool
import com.github.ethanhosier.analysis.metrics.model.CheckpointMetrics
import java.nio.file.Path
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Runs [CheckpointMetricsComputer] in-process across [parallelism] worktrees
 * borrowed from a [WorktreePool] backed by [shadowRepoDir]. This is today's
 * `MetricsRunner.computeOne` behaviour, repackaged behind the
 * [CheckpointExecutor] interface so a future remote-fan-out implementation
 * can drop in without touching pipeline orchestration.
 *
 * Owns its own thread pool and worktree pool — call [close] (or use
 * `executor.use { … }`) to release both.
 */
class LocalCheckpointExecutor(
    shadowRepoDir: Path,
    worktreeBase: Path,
    private val parallelism: Int,
    gradleConfig: GradleConfig = GradleConfig.DEFAULT,
    private val computer: CheckpointMetricsComputer = CheckpointMetricsComputer(gradleConfig),
) : CheckpointExecutor {

    private val pool: WorktreePool = WorktreePool(shadowRepoDir, worktreeBase, parallelism)
    private val executor: ExecutorService = Executors.newFixedThreadPool(parallelism)

    override fun submit(sha: String): Future<CheckpointMetrics> =
        executor.submit<CheckpointMetrics> {
            val worktree = pool.borrow(sha)
            try {
                computer.compute(worktree, sha)
            } finally {
                pool.release(worktree)
            }
        }

    override fun close() {
        executor.shutdownNow()
        executor.awaitTermination(1, TimeUnit.MINUTES)
        pool.close()
    }
}
