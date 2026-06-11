package com.github.ethanhosier.analysis.metrics.tests

import kotlinx.serialization.Serializable

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
    val wasSkipped: Boolean = false,
    val skipReason: String? = null,
) {
    companion object {
        fun skipped(reason: String): TestResult = TestResult(
            success = false,
            exitCode = -1,
            durationMs = 0,
            timedOut = false,
            total = 0,
            passed = 0,
            failed = 0,
            skipped = 0,
            failures = emptyList(),
            stderrTail = "",
            wasSkipped = true,
            skipReason = reason,
        )
    }
}

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
