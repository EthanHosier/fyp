package com.github.ethanhosier.analysis.metrics.tests

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GradleTestRunnerTest {

    @Test
    fun `runs gradle test and aggregates junit XML totals and failures`() {
        val fixture = Path.of("src/test/resources/gradle-fixture").toAbsolutePath()
        // Always copy to tmp so `build/` doesn't pollute the checked-in fixture.
        val workdir = copyFixture(fixture)

        val result = GradleTestRunner(timeout = Duration.ofMinutes(10)).run(workdir)

        println(result.copy(stderrTail = "<omitted>"))

        // Fixture has: 1 passing, 1 failing, 1 skipped.
        assertEquals(3, result.total, "unexpected total; stderr:\n${result.stderrTail}")
        assertEquals(1, result.passed)
        assertEquals(1, result.failed)
        assertEquals(1, result.skipped)
        assertFalse(result.success, "failing test should mean success=false")
        assertFalse(result.timedOut)
        assertTrue(result.exitCode != 0, "gradle should exit non-zero when tests fail, was ${result.exitCode}")
        assertTrue(result.durationMs > 0)

        assertEquals(1, result.failures.size)
        val fail = result.failures.single()
        assertEquals("sample.HelloTest", fail.className)
        assertEquals("fails()", fail.methodName)
        assertEquals("failure", fail.type)
        assertTrue(
            fail.message.contains("deliberate failure") || fail.message.contains("99"),
            "failure message should mention the assertion; was: ${fail.message}",
        )
        assertTrue(fail.stackTraceTail.contains("HelloTest"), "stack trace should reference the class")
    }

    private fun copyFixture(src: Path): Path {
        val dest = Files.createTempDirectory("gradle-fixture-test-")
        Files.walk(src).use { stream ->
            stream.forEach { path ->
                val target = dest.resolve(src.relativize(path).toString())
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target)
                } else {
                    Files.createDirectories(target.parent)
                    Files.copy(path, target)
                    if (Files.isExecutable(path)) target.toFile().setExecutable(true, false)
                }
            }
        }
        return dest
    }
}
