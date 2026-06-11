package com.github.ethanhosier.analysis.metrics.gradlebuild

import kotlinx.serialization.Serializable

@Serializable
data class BuildResult(
    val success: Boolean,
    val exitCode: Int,
    val durationMs: Long,
    val timedOut: Boolean,
    val stderrTail: String,
)
