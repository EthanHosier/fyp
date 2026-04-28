package com.github.ethanhosier.analysis.metrics.gradlebuild

import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Runs `./gradlew build -x test` on a checkpoint's worktree and reports the
 * outcome.
 *
 * ### Isolation caveat
 *
 * This is *process-level* isolation, not a security sandbox. The subprocess
 * runs with our user's permissions and can read/write the filesystem, open
 * network connections, etc. We rely on:
 *
 * - worktree-per-checkpoint → fresh source tree, no shared `build/` dir
 * - `--no-daemon` → fresh JVM per invocation, no daemon memory carryover
 * - a timeout → bounded wallclock even if the build hangs
 * - an optional session-shared `gradle-user-home` → dep cache reuse without
 *   per-run re-downloads
 *
 * That's sufficient for self-authored sessions (the typical case here) because
 * the threat model is cross-checkpoint pollution, not malicious code. If a
 * session ever contains third-party code we haven't vetted, swap this runner
 * for a container-based one (docker with `--network=none` + CPU/mem caps).
 */
class GradleBuildRunner(
    private val timeout: Duration = Duration.ofMinutes(5),
    private val gradleUserHome: Path? = null,
    private val stderrTailBytes: Int = 8 * 1024,
) {

    /**
     * @param projectDir root of the Gradle project (must contain `gradlew`)
     */
    fun run(projectDir: Path): BuildResult {
        require(Files.isDirectory(projectDir)) { "not a directory: $projectDir" }
        val gradlew = projectDir.resolve("gradlew")
        require(Files.isRegularFile(gradlew)) { "gradlew not found at $gradlew" }

        val cmd = buildList {
            add(gradlew.toAbsolutePath().toString())
            add("--console=plain")
            add("--build-cache")
            // Deliberately no --stacktrace: it dumps ~40KB of Gradle internals
            // after the actionable "What went wrong" block, which then gets
            // kept by our tail buffer instead of the diagnostic. The "What
            // went wrong" section alone is enough for compile/test failures.
            gradleUserHome?.let { add("--gradle-user-home=${it.toAbsolutePath()}") }
            add("build")
            add("-x")
            add("test")
        }

        val process = ProcessBuilder(cmd)
            .directory(projectDir.toFile())
            .redirectErrorStream(false)
            .start()

        val stderrTail = RingTail(stderrTailBytes)
        val stderrDrain = Thread({
            BufferedReader(InputStreamReader(process.errorStream)).use { r ->
                r.lineSequence().forEach { stderrTail.appendLine(it) }
            }
        }, "gradle-build-stderr").apply { isDaemon = true; start() }
        val stdoutDrain = Thread({
            // Drain and discard stdout so the pipe doesn't block the process.
            process.inputStream.use { it.readAllBytes() }
        }, "gradle-build-stdout").apply { isDaemon = true; start() }

        val start = System.currentTimeMillis()
        val finishedInTime = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)
        val durationMs = System.currentTimeMillis() - start

        if (!finishedInTime) {
            process.destroyForcibly().waitFor()
            stderrDrain.join(2_000)
            stdoutDrain.join(2_000)
            return BuildResult(
                success = false,
                exitCode = -1,
                durationMs = durationMs,
                timedOut = true,
                stderrTail = stderrTail.snapshot(),
            )
        }

        stderrDrain.join(2_000)
        stdoutDrain.join(2_000)
        val exit = process.exitValue()
        return BuildResult(
            success = exit == 0,
            exitCode = exit,
            durationMs = durationMs,
            timedOut = false,
            stderrTail = stderrTail.snapshot(),
        )
    }
}

/**
 * Bounded tail buffer: append lines freely; `snapshot()` returns at most the
 * last [limitBytes] of text. Trimming is cheap because we only compact when
 * the buffer would otherwise balloon.
 */
private class RingTail(private val limitBytes: Int) {
    private val buf = StringBuilder()

    @Synchronized
    fun appendLine(line: String) {
        buf.append(line).append('\n')
        if (buf.length > limitBytes * 2) {
            buf.delete(0, buf.length - limitBytes)
        }
    }

    @Synchronized
    fun snapshot(): String =
        if (buf.length <= limitBytes) buf.toString()
        else buf.substring(buf.length - limitBytes)
}
