package com.github.ethanhosier.refactoringbundle.internal

import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jdt.core.IJavaProject
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Caches `IJavaProject` handles keyed by the on-disk project root, so
 * repeated refactorings against the same worktree amortise the cost of
 * project creation + classpath setup + index wait (seconds the first
 * time, ~nothing subsequent).
 *
 * The cache is invalidated when the worktree content is replaced — the
 * `WorktreePool` recycles a slot across SHAs, so its caller must tell
 * us to forget the project via [invalidate] on release.
 */
internal object ProjectRegistry {

    private val projects = ConcurrentHashMap<Path, IJavaProject>()

    fun getOrInit(
        root: Path,
        sourceFolders: List<String>,
        classpathJars: List<Path>,
    ): IJavaProject {
        val key = root.toAbsolutePath().normalize()
        projects[key]?.let { return it }
        return synchronized(this) {
            projects[key] ?: run {
                val name = projectNameFor(key)
                val jp = ProjectInitializer.initProject(name, key, sourceFolders, classpathJars)
                IndexingGate.waitForIndex(jp)
                projects[key] = jp
                jp
            }
        }
    }

    fun invalidate(root: Path) {
        val key = root.toAbsolutePath().normalize()
        val jp = projects.remove(key) ?: return
        // `deleteContent=false` — preserves the caller's worktree files;
        // only removes Eclipse's in-memory project + its `.project` file.
        runCatching {
            jp.project.delete(false, true, NullProgressMonitor())
        }
    }

    fun shutdown() {
        for (jp in projects.values) {
            runCatching { jp.project.delete(false, true, NullProgressMonitor()) }
        }
        projects.clear()
    }

    // Eclipse project names must be filesystem-safe and unique within
    // the workspace. Hash the absolute path so we get both.
    private fun projectNameFor(root: Path): String {
        val hex = Integer.toHexString(root.toString().hashCode())
        val leaf = root.fileName?.toString()?.replace(Regex("[^A-Za-z0-9_-]"), "_") ?: "project"
        return "$leaf-$hex"
    }

    init {
        // Best-effort cleanup of projects left over from a prior JVM
        // crash — the physical locations point at worktrees that may
        // already be gone, and Eclipse will refuse to reopen them.
        runCatching {
            val root = ResourcesPlugin.getWorkspace().root
            for (p in root.projects) {
                runCatching { p.delete(false, true, NullProgressMonitor()) }
            }
        }
    }
}
