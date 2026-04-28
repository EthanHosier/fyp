package com.github.ethanhosier.analysis.metrics.exec

import com.github.ethanhosier.analysis.metrics.model.CheckpointMetrics
import java.util.concurrent.Future

/**
 * Submit a SHA, get a future for its [CheckpointMetrics]. Implementations
 * decide where the work happens: an in-process thread pool against a
 * worktree pool ([LocalCheckpointExecutor]), or fan-out to remote workers.
 *
 * Submission order is preserved by the caller; this interface makes no
 * concurrency or ordering guarantees of its own beyond "futures eventually
 * complete with a result or an exception".
 *
 * Implementations are [AutoCloseable] so callers can `executor.use { … }`
 * to guarantee thread pool / worktree / network resources are released.
 */
interface CheckpointExecutor : AutoCloseable {
    fun submit(sha: String): Future<CheckpointMetrics>
}
