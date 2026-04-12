package com.github.ethanhosier.ideplugin.model

import kotlinx.serialization.Serializable

@Serializable
enum class FileChangeType {
    CREATED,
    MODIFIED,
    RENAMED,
    MOVED,
    DELETED,
}

@Serializable
data class CheckpointFile(
    val path: String,
    // Null for DELETED files (no contents to capture).
    val fullContents: String?,
    val changeType: FileChangeType,
    // Populated for RENAMED / MOVED.
    val previousPath: String? = null,
)
