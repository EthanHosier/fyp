package com.github.ethanhosier.analysis.metrics.tests

import kotlinx.serialization.Serializable

/**
 * Result of running `./gradlew test` on a checkpoint's worktree, aggregated
 * across all JUnit XML reports. Populated by `GradleTestRunner` in a later
 * step.
 */
@Serializable
class TestResult
