package com.github.ethanhosier.ideplugin.model

import kotlinx.serialization.Serializable

@Serializable
data class Session(
    val metadata: SessionMetadata,
    val events: List<TraceEvent> = emptyList(),
    val checkpoints: List<Checkpoint> = emptyList(),
)
