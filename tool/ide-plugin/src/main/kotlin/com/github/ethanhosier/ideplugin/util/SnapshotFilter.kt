package com.github.ethanhosier.ideplugin.util

import java.nio.file.Path

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
