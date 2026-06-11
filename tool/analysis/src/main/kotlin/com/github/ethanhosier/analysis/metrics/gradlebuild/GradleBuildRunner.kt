package com.github.ethanhosier.analysis.metrics.gradlebuild

import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit

class GradleBuildRunner(
    private val timeout: Duration = Duration.ofMinutes(5),
    private val gradleUserHome: Path? = null,
    private val stderrTailBytes: Int = 8 * 1024,
) {

    fun run(projectDir: Path): BuildResult {
        require(Files.isDirectory(projectDir)) { "not a directory: $projectDir" }
        val gradlew = projectDir.resolve("gradlew")
        require(Files.isRegularFile(gradlew)) { "gradlew not found at $gradlew" }

        val cmd = buildList {
            add(gradlew.toAbsolutePath().toString())
            add("--console=plain")
            add("--build-cache")
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
