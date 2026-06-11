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
data class FileSnapshot(
    val path: String,
    // Null for DELETED files — the file no longer exists at event time.
    val contents: String?,
    val changeType: FileChangeType,
    // Populated for RENAMED / MOVED — the path the file had before the operation.
    val previousPath: String? = null,
    // Class/method members the event actually touched inside this file. Empty
    // for non-Java files, DELETED snapshots, or when PSI resolution failed.
    val touchedMembers: List<TouchedMember> = emptyList(),
)

@Serializable
data class TouchedMember(
    val className: String?,
    val methodSignature: String?,
)
