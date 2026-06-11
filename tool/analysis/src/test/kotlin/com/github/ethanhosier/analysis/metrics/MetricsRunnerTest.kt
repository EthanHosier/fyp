package com.github.ethanhosier.analysis.metrics

import com.github.ethanhosier.analysis.model.ReconstructionResult
import com.github.ethanhosier.analysis.reconstruct.EventCommitMap
import com.github.ethanhosier.analysis.reconstruct.GitRunner
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MetricsRunnerTest {

    @Test
    fun `computes a metrics record per unique sha`(@TempDir temp: Path) {
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

        val summary = MetricsRunner(parallelism = 1).run(reconstruction, sessionFolder)

        println("summary: $summary")
        assertEquals(1, summary.totalShas, "two events should collapse onto one unique SHA")
        assertEquals(1, summary.computed)
        assertEquals(1, summary.checkpoints.size)

        val metrics = summary.checkpoints.single()
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
