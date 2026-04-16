package com.github.ethanhosier.analysis.metrics

import com.github.ethanhosier.analysis.reconstruct.GitRunner
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorktreePoolTest {

    @Test
    fun `borrow returns fresh worktree at requested sha, release removes it`(@TempDir temp: Path) {
        val repo = Files.createDirectories(temp.resolve("repo"))
        val git = GitRunner(repo).apply {
            init()
            setLocalIdentity("test@example.com", "test")
        }
        val file = repo.resolve("file.txt")

        file.writeText("v1")
        git.addAll(); val sha1 = git.commit("v1")
        file.writeText("v2")
        git.addAll(); val sha2 = git.commit("v2")

        val pool = WorktreePool(
            shadowRepo = repo,
            baseDir = temp.resolve("worktrees"),
            size = 2,
        )

        pool.use {
            val wt1 = pool.borrow(sha1)
            val wt2 = pool.borrow(sha2)
            assertEquals("v1", wt1.resolve("file.txt").readText())
            assertEquals("v2", wt2.resolve("file.txt").readText())
            assertTrue(Files.isDirectory(wt1))
            assertTrue(Files.isDirectory(wt2))

            pool.release(wt1)
            assertFalse(Files.exists(wt1), "worktree dir should be gone after release")

            // Slot is reusable — borrowing a third time should succeed.
            val wt3 = pool.borrow(sha1)
            assertEquals("v1", wt3.resolve("file.txt").readText())
            pool.release(wt3)
            pool.release(wt2)
        }
    }

    @Test
    fun `close cleans up still-borrowed worktrees`(@TempDir temp: Path) {
        val repo = Files.createDirectories(temp.resolve("repo"))
        val git = GitRunner(repo).apply {
            init()
            setLocalIdentity("test@example.com", "test")
        }
        repo.resolve("file.txt").writeText("hi")
        git.addAll(); val sha = git.commit("hi")

        val baseDir = temp.resolve("worktrees")
        val pool = WorktreePool(shadowRepo = repo, baseDir = baseDir, size = 2)
        val wt1 = pool.borrow(sha)
        val wt2 = pool.borrow(sha)
        assertTrue(Files.isDirectory(wt1))
        assertTrue(Files.isDirectory(wt2))

        pool.close()
        assertFalse(Files.exists(wt1))
        assertFalse(Files.exists(wt2))
    }

    @Test
    fun `pool is idempotent after crashed prior run`(@TempDir temp: Path) {
        val repo = Files.createDirectories(temp.resolve("repo"))
        val git = GitRunner(repo).apply {
            init()
            setLocalIdentity("test@example.com", "test")
        }
        repo.resolve("file.txt").writeText("a")
        git.addAll(); val sha = git.commit("a")

        val baseDir = temp.resolve("worktrees")
        // Simulate leftover state: a pre-existing dir at the slot-0 path. Pool
        // should wipe it on init rather than trip over "path already exists".
        Files.createDirectories(baseDir.resolve("w0"))
        Files.writeString(baseDir.resolve("w0/stale.txt"), "leftover")

        WorktreePool(shadowRepo = repo, baseDir = baseDir, size = 1).use { pool ->
            val wt = pool.borrow(sha)
            assertFalse(Files.exists(wt.resolve("stale.txt")))
            assertEquals("a", wt.resolve("file.txt").readText())
            pool.release(wt)
        }
    }
}
