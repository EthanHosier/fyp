package com.github.ethanhosier.analysis.metrics

import com.github.ethanhosier.analysis.reconstruct.GitRunner
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap

/**
 * Bounded pool of `git worktree` directories for parallel checkpoint analysis.
 *
 * Each [borrow] creates a fresh worktree from the shadow repo at the requested
 * SHA; each [release] removes it. Up to `size` worktrees exist concurrently —
 * `borrow` blocks once that cap is reached. Fresh-per-borrow (rather than
 * reusing worktrees across SHAs via `git checkout`) is deliberate: it
 * guarantees no leftover `build/`, stale Gradle cache, or other cross-SHA
 * pollution in the worktree, at a cost of ~100ms per borrow.
 *
 * Thread-safe: [GitRunner] invocations carry no mutable state, and git itself
 * serialises concurrent worktree ops via its own repo lock.
 */
class WorktreePool(
    shadowRepo: Path,
    private val baseDir: Path,
    size: Int,
) : AutoCloseable {

    private val git = GitRunner(shadowRepo)
    private val slots: BlockingQueue<Int>
    private val activePaths = ConcurrentHashMap<Path, Int>()

    init {
        require(size > 0) { "pool size must be positive, was $size" }
        // Wipe any leftover worktree dirs from a crashed prior run, then
        // tell git to forget their admin entries. Together these make the
        // pool idempotent on restart.
        if (Files.exists(baseDir)) baseDir.toFile().deleteRecursively()
        Files.createDirectories(baseDir)
        git.worktreePrune()

        slots = ArrayBlockingQueue<Int>(size).apply {
            repeat(size) { put(it) }
        }
    }

    /**
     * Blocks until a slot is free, then creates and returns a worktree at
     * [sha]. Every returned path must be handed back via [release] (or the
     * slot leaks and eventually [borrow] will block forever).
     */
    fun borrow(sha: String): Path {
        val slot = slots.take()
        val path = baseDir.resolve("w$slot")
        try {
            git.worktreeAdd(path, sha)
        } catch (e: Exception) {
            slots.put(slot)
            throw e
        }
        activePaths[path] = slot
        return path
    }

    fun release(path: Path) {
        val slot = activePaths.remove(path)
            ?: error("worktree was not borrowed from this pool: $path")
        try {
            git.worktreeRemove(path)
        } finally {
            slots.put(slot)
        }
    }

    /**
     * Removes all still-borrowed worktrees and prunes admin entries. Safe to
     * call multiple times; safe to call after a crash mid-analysis.
     */
    override fun close() {
        for ((path, _) in activePaths) {
            runCatching { git.worktreeRemove(path) }
        }
        activePaths.clear()
        runCatching { git.worktreePrune() }
    }
}
