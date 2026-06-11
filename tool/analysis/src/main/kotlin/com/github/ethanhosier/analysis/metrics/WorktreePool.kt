package com.github.ethanhosier.analysis.metrics

import com.github.ethanhosier.analysis.reconstruct.GitRunner
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap

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
        if (Files.exists(baseDir)) baseDir.toFile().deleteRecursively()
        Files.createDirectories(baseDir)
        git.worktreePrune()

        slots = ArrayBlockingQueue<Int>(size).apply {
            repeat(size) { put(it) }
        }
    }

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

    override fun close() {
        for ((path, _) in activePaths) {
            runCatching { git.worktreeRemove(path) }
        }
        activePaths.clear()
        runCatching { git.worktreePrune() }
    }
}
