package com.github.ethanhosier.analysis.metrics.remote

import com.github.ethanhosier.analysis.metrics.GradleConfig
import com.github.ethanhosier.analysis.metrics.dto.BundleRef
import com.github.ethanhosier.analysis.metrics.dto.ComputeRequest
import com.github.ethanhosier.analysis.metrics.exec.CheckpointExecutor
import com.github.ethanhosier.analysis.metrics.model.CheckpointMetrics
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Fans per-SHA work out to remote workers via [LambdaInvoker]. Constructed
 * with a [BundleRef] that's already been uploaded to S3 (see
 * [ShadowRepoBundleUploader.uploadOnce]) — this class doesn't manage the
 * upload lifecycle itself, so it's trivial to substitute fakes in tests.
 *
 * [parallelism] caps the in-flight invocations: each Lambda call is a
 * blocking SDK API call from a worker thread, so the thread pool size is
 * the concurrency knob. Defaults to 50, well below Lambda's per-account
 * default concurrency limit (1000).
 *
 * Failures bubble out of `Future.get()` as exceptions: a handler-side
 * `ComputeResponse.error` (set when the lambda function itself failed)
 * becomes a [RuntimeException]; SDK-level network or auth errors propagate
 * directly.
 */
class RemoteCheckpointExecutor(
    private val invoker: LambdaInvoker,
    private val bundle: BundleRef,
    private val gradleConfig: GradleConfig = GradleConfig.DEFAULT,
    parallelism: Int = DEFAULT_PARALLELISM,
) : CheckpointExecutor {

    private val executor: ExecutorService = Executors.newFixedThreadPool(
        parallelism.coerceIn(1, MAX_PARALLELISM),
    )

    override fun submit(sha: String): Future<CheckpointMetrics> =
        executor.submit<CheckpointMetrics> {
            val response = invoker.invoke(
                ComputeRequest(sha = sha, bundle = bundle, gradleConfig = gradleConfig),
            )
            response.metrics
                ?: error("remote compute failed for $sha: ${response.error ?: "<no error message>"}")
        }

    override fun close() {
        executor.shutdownNow()
        executor.awaitTermination(1, TimeUnit.MINUTES)
    }

    companion object {
        const val DEFAULT_PARALLELISM = 50
        // Sanity cap: even on huge sessions we never want unbounded
        // concurrent in-flight Lambda invocations from one client.
        const val MAX_PARALLELISM = 200
    }
}
