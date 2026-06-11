package com.github.ethanhosier.analysis.metrics.gradlebuild

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GradleBuildRunnerTest {

    @Test
    fun `builds the happy-path gradle fixture`() {
        val fixture = Path.of("src/test/resources/gradle-fixture")
        val result = GradleBuildRunner(timeout = Duration.ofMinutes(10)).run(fixture)

        println(result.copy(stderrTail = "<omitted>"))
        assertTrue(result.success, "expected success; exit=${result.exitCode}\nstderr tail:\n${result.stderrTail}")
        assertFalse(result.timedOut)
        assertEquals(0, result.exitCode)
        assertTrue(result.durationMs > 0)
    }

    @Test
    fun `reports failure when the source does not compile`() {
        val fixture = Path.of("src/test/resources/gradle-fixture").toAbsolutePath()
        val broken = copyFixture(fixture)
        // Overwrite App.java with a syntax error.
        broken.resolve("src/main/java/sample/App.java").writeText(
            """
            package sample;
            public class App {
                public static void main(String[] args) {
                    this is not valid java
                }
            }
            """.trimIndent()
        )

        val result = GradleBuildRunner(timeout = Duration.ofMinutes(10)).run(broken)

        println(result.copy(stderrTail = "<omitted>"))
        assertFalse(result.success, "expected failure for broken source")
        assertFalse(result.timedOut)
        assertTrue(result.exitCode != 0, "exit code should be non-zero, was ${result.exitCode}")
    }

    private fun copyFixture(src: Path): Path {
        val dest = Files.createTempDirectory("gradle-fixture-")
        Files.walk(src).use { stream ->
            stream.forEach { path ->
                val rel = src.relativize(path)
                val target = dest.resolve(rel.toString())
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target)
                } else {
                    Files.createDirectories(target.parent)
                    Files.copy(path, target)
                    // Preserve the executable bit on gradlew.
                    if (Files.isExecutable(path)) target.toFile().setExecutable(true, false)
                }
            }
        }
        return dest
    }
}
