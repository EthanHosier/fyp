package com.github.ethanhosier.analysis.metrics

import kotlinx.serialization.Serializable
import java.nio.file.Path
import java.time.Duration

/**
 * Per-invocation knobs for the Gradle build/test runners. Bundled into a
 * single value so [CheckpointMetricsComputer] (and, later, the wire format
 * for the remote executor) can pass them as one object rather than four
 * loose params.
 *
 * Stored in `Long`/`String` rather than `Duration`/`Path` so the type
 * round-trips cleanly through `kotlinx.serialization` — needed once the
 * remote executor ships these to a Lambda. Accessor properties convert
 * back to the JVM types the runners want.
 */
@Serializable
data class GradleConfig(
    val buildTimeoutMs: Long = DEFAULT_BUILD_TIMEOUT_MS,
    val testTimeoutMs: Long = DEFAULT_TEST_TIMEOUT_MS,
    val gradleUserHome: String? = null,
) {
    val buildTimeout: Duration get() = Duration.ofMillis(buildTimeoutMs)
    val testTimeout: Duration get() = Duration.ofMillis(testTimeoutMs)
    val gradleUserHomePath: Path? get() = gradleUserHome?.let { Path.of(it) }

    companion object {
        const val DEFAULT_BUILD_TIMEOUT_MS = 5L * 60 * 1000
        const val DEFAULT_TEST_TIMEOUT_MS = 10L * 60 * 1000
        val DEFAULT = GradleConfig()
    }
}
