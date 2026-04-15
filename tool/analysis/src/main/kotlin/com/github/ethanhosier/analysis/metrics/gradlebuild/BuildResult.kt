package com.github.ethanhosier.analysis.metrics.gradlebuild

import kotlinx.serialization.Serializable

/**
 * Result of running `./gradlew build` on a checkpoint's worktree. Populated by
 * `GradleBuildRunner` in a later step.
 *
 * Note: `success = false` (compile failure, test failure) is a *valid* outcome
 * worth recording — not an error. Errors mean the build runner itself could
 * not produce a verdict, and those abort the run.
 */
@Serializable
class BuildResult
