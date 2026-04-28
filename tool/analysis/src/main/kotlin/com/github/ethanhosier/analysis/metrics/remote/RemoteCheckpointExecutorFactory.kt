package com.github.ethanhosier.analysis.metrics.remote

import com.github.ethanhosier.analysis.metrics.GradleConfig
import com.github.ethanhosier.analysis.metrics.exec.CheckpointExecutor
import com.github.ethanhosier.analysis.metrics.exec.CheckpointExecutorFactory
import software.amazon.awssdk.services.lambda.LambdaClient
import software.amazon.awssdk.services.s3.S3Client
import java.nio.file.Path

/**
 * [CheckpointExecutorFactory] that wires up the AWS path: bundles the
 * shadow repo, uploads it once to S3, and constructs a
 * [RemoteCheckpointExecutor] that fans per-SHA invocations out to Lambda.
 *
 * AWS clients ([LambdaClient], [S3Client]) are owned by the factory — one
 * per pipeline lifetime — and closed alongside the executor. The
 * `parallelism` arg passed to [create] (the local cores/2 default from
 * [com.github.ethanhosier.analysis.pipeline.AnalysisPipeline]) is *ignored*
 * for the remote path: fan-out is bounded by [RemoteCheckpointExecutor]'s
 * own setting because the constraint is the AWS account's concurrent-
 * Lambda-invocation budget, not local CPU.
 */
class RemoteCheckpointExecutorFactory(
    private val lambdaClient: LambdaClient,
    private val s3Client: S3Client,
    private val functionArn: String,
    private val bundleBucket: String,
    private val gradleConfig: GradleConfig = GradleConfig.DEFAULT,
    private val remoteParallelism: Int = RemoteCheckpointExecutor.DEFAULT_PARALLELISM,
) : CheckpointExecutorFactory {

    override fun create(shadowRepoDir: Path, sessionDir: Path, parallelism: Int): CheckpointExecutor {
        val bundle = ShadowRepoBundleUploader(s3Client, bundleBucket).uploadOnce(shadowRepoDir)
        val invoker = AwsLambdaInvoker(lambdaClient, functionArn)
        return WrappedRemoteExecutor(
            inner = RemoteCheckpointExecutor(
                invoker = invoker,
                bundle = bundle,
                gradleConfig = gradleConfig,
                parallelism = remoteParallelism,
            ),
            onClose = {
                // AWS clients live for the whole pipeline run, not per
                // session; don't close them here. Left as a no-op for
                // clarity but kept as a hook in case we later want to
                // surface per-session telemetry on close.
            },
        )
    }

    companion object {
        /**
         * Construct from process env vars.
         *
         * Required:
         *  - `ANALYSIS_LAMBDA_ARN` — the function (or its alias) ARN
         *  - `ANALYSIS_BUNDLE_BUCKET` — S3 bucket holding shadow-repo bundles
         *
         * Optional:
         *  - `ANALYSIS_AWS_REGION` (default: SDK's default chain)
         *  - `ANALYSIS_REMOTE_PARALLELISM` (default: 50)
         */
        fun fromEnv(): RemoteCheckpointExecutorFactory {
            val arn = System.getenv("ANALYSIS_LAMBDA_ARN")
                ?: error("ANALYSIS_EXECUTOR=remote requires ANALYSIS_LAMBDA_ARN")
            val bucket = System.getenv("ANALYSIS_BUNDLE_BUCKET")
                ?: error("ANALYSIS_EXECUTOR=remote requires ANALYSIS_BUNDLE_BUCKET")
            val region = System.getenv("ANALYSIS_AWS_REGION")
            val parallelism = System.getenv("ANALYSIS_REMOTE_PARALLELISM")?.toIntOrNull()
                ?: RemoteCheckpointExecutor.DEFAULT_PARALLELISM

            val lambdaBuilder = LambdaClient.builder()
            val s3Builder = S3Client.builder()
            if (region != null) {
                lambdaBuilder.region(software.amazon.awssdk.regions.Region.of(region))
                s3Builder.region(software.amazon.awssdk.regions.Region.of(region))
            }

            return RemoteCheckpointExecutorFactory(
                lambdaClient = lambdaBuilder.build(),
                s3Client = s3Builder.build(),
                functionArn = arn,
                bundleBucket = bucket,
                remoteParallelism = parallelism,
            )
        }
    }
}

/**
 * Forward-only wrapper that runs an [onClose] hook *after* the inner
 * executor's resources are released. Keeps the factory's
 * lifecycle-coupling logic out of [RemoteCheckpointExecutor]'s own code.
 */
private class WrappedRemoteExecutor(
    private val inner: CheckpointExecutor,
    private val onClose: () -> Unit,
) : CheckpointExecutor by inner {
    override fun close() {
        try {
            inner.close()
        } finally {
            onClose()
        }
    }
}
