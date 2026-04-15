package com.github.ethanhosier.analysis.metrics.ck

import kotlinx.serialization.Serializable

/**
 * CK (class-level OO metrics) output for one checkpoint. Populated by
 * `CkRunner` in a later step — for now the type just exists so the outer
 * `CheckpointMetrics` record compiles.
 */
@Serializable
class CkResult
