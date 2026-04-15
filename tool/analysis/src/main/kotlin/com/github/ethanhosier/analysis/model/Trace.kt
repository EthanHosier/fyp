package com.github.ethanhosier.analysis.model

import com.github.ethanhosier.ideplugin.model.SessionMetadata
import com.github.ethanhosier.ideplugin.model.TraceEvent

/**
 * A session's metadata paired with its full event stream.
 *
 * Ingest produces a `Trace` whose events follow JSONL line order. The normalize
 * stage later returns another `Trace` with events in canonical order. Downstream
 * stages consume the normalized form.
 */
data class Trace(
    val metadata: SessionMetadata,
    val events: List<TraceEvent>,
)
