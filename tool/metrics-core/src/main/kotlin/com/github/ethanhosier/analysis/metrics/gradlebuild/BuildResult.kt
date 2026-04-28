package com.github.ethanhosier.analysis.metrics.gradlebuild

import kotlinx.serialization.Serializable

/**
 * Result of running `./gradlew build` on a checkpoint's worktree.
 *
 * `success = false` (compile failure, wrapper mis-config, etc.) is a *valid*
 * outcome worth recording — not an error. Errors mean the build runner itself
 * could not produce a verdict, and those abort the run.
 */
@Serializable
data class BuildResult(
    val success: Boolean,
    val exitCode: Int,
    val durationMs: Long,
    val timedOut: Boolean,
    // Last N bytes of the subprocess's merged stderr. Bounded to stay
    // reasonable on heavily-failing builds; the tail almost always contains
    // the actionable error. Empty string if nothing was emitted.
    val stderrTail: String,
)
