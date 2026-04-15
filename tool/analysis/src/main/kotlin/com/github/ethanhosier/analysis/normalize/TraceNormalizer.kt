package com.github.ethanhosier.analysis.normalize

import com.github.ethanhosier.analysis.model.Trace

object TraceNormalizer {
    fun normalize(trace: Trace): Trace =
        trace.copy(events = trace.events.sortedBy { it.timestamp })
}
