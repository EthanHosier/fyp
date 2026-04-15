package com.github.ethanhosier.analysis.metrics.pmd

import kotlinx.serialization.Serializable

/**
 * PMD violation output for one checkpoint. Populated by `PmdRunner` in a later
 * step.
 */
@Serializable
class PmdResult
