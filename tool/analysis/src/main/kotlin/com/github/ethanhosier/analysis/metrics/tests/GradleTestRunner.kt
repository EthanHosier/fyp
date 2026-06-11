package com.github.ethanhosier.analysis.metrics.tests

import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit

class GradleTestRunner(
    private val timeout: Duration = Duration.ofMinutes(10),
    private val gradleUserHome: Path? = null,
    private val stderrTailBytes: Int = 8 * 1024,
) {

    fun run(projectDir: Path): TestResult {
        require(Files.isDirectory(projectDir)) { "not a directory: $projectDir" }
        val gradlew = projectDir.resolve("gradlew")
        require(Files.isRegularFile(gradlew)) { "gradlew not found at $gradlew" }

        val cmd = buildList {
            add(gradlew.toAbsolutePath().toString())
            add("--console=plain")
            add("--build-cache")
            gradleUserHome?.let { add("--gradle-user-home=${it.toAbsolutePath()}") }
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
        }, "gradle-test-stderr").apply { isDaemon = true; start() }
        val stdoutDrain = Thread({
            process.inputStream.use { it.readAllBytes() }
        }, "gradle-test-stdout").apply { isDaemon = true; start() }

        val start = System.currentTimeMillis()
        val finishedInTime = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)
        val durationMs = System.currentTimeMillis() - start

        val timedOut = !finishedInTime
        if (timedOut) process.destroyForcibly().waitFor()
        stderrDrain.join(2_000)
        stdoutDrain.join(2_000)

        val exit = if (timedOut) -1 else process.exitValue()
        val aggregate = JUnitXmlParser.parse(projectDir.resolve("build/test-results/test"))

        return TestResult(
            success = !timedOut && exit == 0 && aggregate.failed == 0,
            exitCode = exit,
            durationMs = durationMs,
            timedOut = timedOut,
            total = aggregate.total,
            passed = aggregate.passed,
            failed = aggregate.failed,
            skipped = aggregate.skipped,
            failures = aggregate.failures,
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
