package com.github.ethanhosier.analysis.metrics.dto

import com.github.ethanhosier.analysis.metrics.GradleConfig
import com.github.ethanhosier.analysis.metrics.model.CheckpointMetrics
import kotlinx.serialization.Serializable

/**
 * S3-side reference to a `git bundle` of a session's shadow repo. Bundles
 * are content-addressed (`key` derived from the sha256 of bundle bytes) so
 * uploading the same shadow-repo state twice no-ops, and so a stale request
 * can never silently land on a different repo.
 */
@Serializable
data class BundleRef(
    val bucket: String,
    val key: String,
)

/**
 * Wire-format request to a remote checkpoint compute worker (e.g. an AWS
 * Lambda handler). Carries the SHA to measure plus everything the worker
 * needs to materialise the matching project tree on its own filesystem
 * before invoking [com.github.ethanhosier.analysis.metrics.CheckpointMetricsComputer].
 *
 * Lives in `:metrics-core` so both sides of the wire share one definition;
 * the lambda module imports this type, the analysis module's
 * `RemoteCheckpointExecutor` constructs it.
 */
@Serializable
data class ComputeRequest(
    val sha: String,
    val bundle: BundleRef,
    val gradleConfig: GradleConfig = GradleConfig.DEFAULT,
)

/**
 * Wire-format response. Exactly one of [metrics] (success) or [error]
 * (handler failure) is populated. A `metrics` value with `build.success =
 * false` is *not* an error — it's a valid measured outcome that the user's
 * code didn't compile at that SHA. `error` is reserved for handler-side
 * problems (bundle download failed, git checkout exploded, JVM crashed).
 */
@Serializable
data class ComputeResponse(
    val sha: String,
    val metrics: CheckpointMetrics? = null,
    val error: String? = null,
)
