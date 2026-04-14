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

/**
 * A snapshot of a single file attached inline to a file-changing event.
 * The analysis stage reconstructs project state by replaying these in event order.
 */
@Serializable
data class FileSnapshot(
    val path: String,
    // Null for DELETED files — the file no longer exists at event time.
    val contents: String?,
    val changeType: FileChangeType,
    // Populated for RENAMED / MOVED — the path the file had before the operation.
    val previousPath: String? = null,
)
