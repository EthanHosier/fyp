package com.github.ethanhosier.analysis.model

import com.github.ethanhosier.ideplugin.model.SessionMetadata
import com.github.ethanhosier.ideplugin.model.TraceEvent
import kotlinx.serialization.Serializable

@Serializable
data class Trace(
    val metadata: SessionMetadata,
    val events: List<TraceEvent>,
)
