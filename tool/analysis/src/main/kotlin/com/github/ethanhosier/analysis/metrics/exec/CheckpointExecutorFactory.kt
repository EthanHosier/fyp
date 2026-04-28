package com.github.ethanhosier.analysis.metrics.exec

import java.nio.file.Path

/**
 * Constructs a [CheckpointExecutor] for a single pipeline run, given the
 * shadow-repo location and a session-scoped scratch dir. The pipeline owns
 * the executor's lifecycle (`use { … }`); the factory only knows how to
 * make one.
 *
 * Two implementations:
 *  - [LocalCheckpointExecutorFactory]: in-process worktrees + thread pool.
 *  - [RemoteCheckpointExecutorFactory]: uploads a `git bundle` to S3 once,
 *    fans per-SHA invocations out to AWS Lambda.
 *
 * Selection happens at boot. The CLI / server reads env config (see
 * [fromEnv]) and constructs the matching factory; AnalysisPipeline holds
 * a reference to it and calls [create] inside its metrics stage.
 */
fun interface CheckpointExecutorFactory {
    fun create(shadowRepoDir: Path, sessionDir: Path, parallelism: Int): CheckpointExecutor

    companion object {
        /**
         * Choose a factory from process env vars:
         *  - `ANALYSIS_EXECUTOR=local` (default) → [LocalCheckpointExecutorFactory]
         *  - `ANALYSIS_EXECUTOR=remote` → [RemoteCheckpointExecutorFactory]
         *    requires `ANALYSIS_LAMBDA_ARN` and `ANALYSIS_BUNDLE_BUCKET`
         *
         * Failing fast on missing remote config (rather than silently
         * falling back to local) catches misconfigured deployments before
         * the metrics stage actually starts running.
         */
        fun fromEnv(): CheckpointExecutorFactory = when (val mode = System.getenv("ANALYSIS_EXECUTOR") ?: "local") {
            "local" -> LocalCheckpointExecutorFactory()
            "remote" -> com.github.ethanhosier.analysis.metrics.remote
                .RemoteCheckpointExecutorFactory.fromEnv()
            else -> error("unknown ANALYSIS_EXECUTOR='$mode'; expected 'local' or 'remote'")
        }
    }
}

/**
 * Default factory: today's behaviour, just packaged behind the seam.
 * Constructs [LocalCheckpointExecutor] with a `shadow-worktrees/` scratch
 * dir under [sessionDir].
 */
class LocalCheckpointExecutorFactory(
    private val gradleConfig: com.github.ethanhosier.analysis.metrics.GradleConfig =
        com.github.ethanhosier.analysis.metrics.GradleConfig.DEFAULT,
) : CheckpointExecutorFactory {
    override fun create(shadowRepoDir: Path, sessionDir: Path, parallelism: Int): CheckpointExecutor =
        LocalCheckpointExecutor(
            shadowRepoDir = shadowRepoDir,
            worktreeBase = sessionDir.resolve("shadow-worktrees"),
            parallelism = parallelism,
            gradleConfig = gradleConfig,
        )
}
