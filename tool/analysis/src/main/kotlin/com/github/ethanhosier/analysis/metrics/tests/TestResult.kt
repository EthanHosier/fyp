package com.github.ethanhosier.analysis.metrics.tests

import kotlinx.serialization.Serializable

/**
 * Result of running `./gradlew test` on a checkpoint's worktree, aggregated
 * across all JUnit XML reports.
 *
 * `success = false` covers every "tests did not all pass" case: a failing
 * assertion, an uncaught exception, a compilation failure before tests even
 * ran, a timeout. Errors mean the runner itself couldn't produce a verdict
 * and abort the run.
 */
@Serializable
data class TestResult(
    // true iff the test task exited 0 AND every testcase we parsed reported a pass.
    val success: Boolean,
    val exitCode: Int,
    val durationMs: Long,
    val timedOut: Boolean,
    // Aggregated across every `build/test-results/test/*.xml` file.
    val total: Int,
    val passed: Int,
    val failed: Int,
    val skipped: Int,
    val failures: List<TestFailure>,
    // Last N bytes of stderr — captures Gradle-level problems (compile errors,
    // dep resolution) that wouldn't show up in the JUnit XML at all.
    val stderrTail: String,
)

/**
 * One failed testcase. `type` distinguishes the JUnit XML element the failure
 * came from: `"failure"` = assertion failed (e.g. `AssertionError`),
 * `"error"` = uncaught exception.
 */
@Serializable
data class TestFailure(
    val className: String,
    val methodName: String,
    val type: String,
    val message: String,
    // Tail of the stack trace text from the XML; bounded so a single crashing
    // test with a 10k-line stack doesn't dominate the checkpoint file.
    val stackTraceTail: String,
)
