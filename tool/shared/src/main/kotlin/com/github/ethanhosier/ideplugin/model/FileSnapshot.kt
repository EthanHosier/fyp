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
    // Class/method members the event actually touched inside this file. Empty
    // for non-Java files, DELETED snapshots, or when PSI resolution failed.
    val touchedMembers: List<TouchedMember> = emptyList(),
)

/**
 * A class (and optionally a method within it) that an event edited. `className`
 * is the fully-qualified name when resolvable, falling back to the simple name.
 * `methodSignature` is `name(paramType1, paramType2, ...)` or null when the
 * edit is at class level (e.g. a field declaration, extracted superclass).
 */
@Serializable
data class TouchedMember(
    val className: String?,
    val methodSignature: String?,
)
