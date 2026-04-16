package com.github.ethanhosier.ideplugin.util

import java.nio.file.Path

/**
 * Decides which files in an initial-src snapshot are captured vs skipped.
 *
 * Currently a hardcoded skip list — directories that are generated output,
 * dependency caches, or IDE/VCS metadata; a few individual file names; and a
 * few file extensions. Plus a per-file size cap.
 *
 * A future iteration may swap this for a `.gitignore`-driven filter (shelling
 * out to `git ls-files --cached --others --exclude-standard`), but that would
 * require git on PATH and the project to be a git repo. The hardcoded
 * approach works unconditionally; the tradeoff is that it won't pick up
 * project-specific ignore rules.
 */
object SnapshotFilter {

    val skipDirs: Set<String> = setOf(
        ".git",
        ".idea",
        ".gradle",
        ".kotlin",
        ".intellijPlatform",
        ".qodana",
        ".settings",   // Eclipse / Buildship metadata
        ".refactoring-traces",  // this plugin's own output — snapshotting it would recurse
        "build",       // Gradle output
        "out",         // IDEA compile output
        "bin",         // Eclipse compile output
        "target",      // Maven output
        "node_modules",
    )

    val skipFiles: Set<String> = setOf(
        ".DS_Store",
        "Thumbs.db",
        ".classpath",
        ".project",
    )

    val skipExtensions: Set<String> = setOf(
        "class", // JVM bytecode
        "iml",   // IDEA module file
    )

    /** Per-file cap; anything bigger is skipped so snapshots stay bounded. */
    const val MAX_FILE_SIZE_BYTES: Long = 5L * 1024 * 1024

    fun shouldSkipDir(dirName: String): Boolean = dirName in skipDirs

    fun shouldCaptureFile(relativePath: Path, fileSize: Long): Boolean {
        val fileName = relativePath.fileName.toString()
        if (fileName in skipFiles) return false
        val ext = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        if (ext in skipExtensions) return false
        if (fileSize > MAX_FILE_SIZE_BYTES) return false
        return true
    }
}
