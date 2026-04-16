package com.github.ethanhosier.analysis.metrics

import com.github.ethanhosier.analysis.metrics.model.CheckpointMetrics
import com.github.ethanhosier.analysis.model.ReconstructionResult
import com.github.ethanhosier.analysis.reconstruct.EventCommitMap
import com.github.ethanhosier.analysis.reconstruct.GitRunner
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * End-to-end smoke test for the full metrics pipeline. Builds a shadow repo
 * from the gradle-fixture, runs every section (CK + PMD + Gradle build +
 * Gradle test) against the resulting SHA, and inspects the written JSON.
 */
class MetricsRunnerTest {

    @Test
    fun `produces one checkpoint file per unique sha and is idempotent on rerun`(@TempDir temp: Path) {
        val sessionFolder = Files.createDirectories(temp.resolve("session"))
        val fixture = Path.of("src/test/resources/gradle-fixture").toAbsolutePath()
        val shadowRepo = sessionFolder.resolve("shadow-repo")

        copyTree(fixture, shadowRepo)
        val git = GitRunner(shadowRepo).apply {
            init()
            setLocalIdentity("test@example.com", "test")
            addAll()
        }
        val sha = git.commit("fixture baseline")

        val reconstruction = ReconstructionResult(
            repoDir = shadowRepo,
            eventCommits = EventCommitMap(mapping = mapOf("e1" to sha, "e2" to sha)),
        )

        val runner = MetricsRunner(parallelism = 1)
        val summary = runner.run(reconstruction, sessionFolder)

        println("summary: $summary")
        assertEquals(1, summary.totalShas, "two events should collapse onto one unique SHA")
        assertEquals(1, summary.computed)
        assertEquals(0, summary.reused)

        val outputFile = sessionFolder.resolve("checkpoint-metrics/$sha.json")
        assertTrue(Files.isRegularFile(outputFile), "expected $outputFile to exist")

        val metrics = Json.decodeFromString(CheckpointMetrics.serializer(), Files.readString(outputFile))
        assertEquals(sha, metrics.sha)
        // CK / PMD did their thing on the fixture's Java sources.
        assertTrue(metrics.ck.perClass.isNotEmpty(), "CK should find classes in the fixture")
        assertTrue(metrics.pmd.classMetrics.isNotEmpty(), "PMD should report per-class metrics")
        // Fixture has 1 passing + 1 failing + 1 skipped test.
        assertEquals(3, metrics.tests.total)
        assertEquals(1, metrics.tests.failed)
        assertFalse(metrics.tests.success)
        // Build task compiles main + test, which succeeds; a separate `test`
        // task runs the failures. Build alone should be ok here.
        assertTrue(metrics.build.success, "build should succeed; stderr:\n${metrics.build.stderrTail}")

        // Worktree cleanup: the shadow-worktrees/ dir should exist but be empty.
        val wtBase = sessionFolder.resolve("shadow-worktrees")
        assertTrue(Files.isDirectory(wtBase))
        assertTrue(
            Files.list(wtBase).use { it.toList() }.isEmpty(),
            "shadow-worktrees should be empty after close()",
        )

        // Idempotency: rerun with the same inputs → everything reused, no
        // Gradle subprocess cost.
        val rerun = runner.run(reconstruction, sessionFolder)
        assertEquals(1, rerun.reused)
        assertEquals(0, rerun.computed)
    }

    private fun copyTree(src: Path, dest: Path) {
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
    }
}
