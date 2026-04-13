package com.github.ethanhosier.ideplugin.model

import kotlinx.serialization.Serializable

@Serializable
data class TraceEvent(
    val id: String,
    val type: EventType,
    val timestamp: Long,
    val sessionId: String,
    // Full file snapshots for events that directly change files (EDIT_BURST, FILE_CREATED,
    // FILE_DELETED, FILE_RENAMED, FILE_MOVED, REFACTORING_FINISHED). Empty for all others.
    val changedFiles: List<FileSnapshot> = emptyList(),
    // Simple file path references for non-state-changing events (FILE_OPENED, FILE_FOCUSED, etc.).
    val relatedFiles: List<String> = emptyList(),
    // Flat string map for type-specific metadata (char counts, build flags, test results, etc.).
    val payload: Map<String, String> = emptyMap(),
)
