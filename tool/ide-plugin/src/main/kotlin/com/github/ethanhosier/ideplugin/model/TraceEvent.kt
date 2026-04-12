package com.github.ethanhosier.ideplugin.model

import kotlinx.serialization.Serializable

@Serializable
data class TraceEvent(
    val id: String,
    val type: EventType,
    val timestamp: Long,
    val sessionId: String,
    val relatedFiles: List<String> = emptyList(),
    // Flat string map keeps serialization simple; listeners populate with type-specific fields.
    val payload: Map<String, String> = emptyMap(),
)
