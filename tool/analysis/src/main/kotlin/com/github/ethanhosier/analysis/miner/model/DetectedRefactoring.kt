package com.github.ethanhosier.analysis.miner.model

import kotlinx.serialization.Serializable

/**
 * A single refactoring detected by RefactoringMiner between two checkpoint
 * commits, normalised so RM's own types don't leak into serialized output.
 *
 * [type] is the `RefactoringType.displayName` (e.g. `"Extract Method"`).
 * [description] is RM's human-readable one-liner — convenient for readers,
 * but not stable enough to key on. [leftSideLocations] and
 * [rightSideLocations] are the canonical identifiers: each location is a
 * compact string `file:startLine-endLine codeElement` that survives
 * serialisation and is order-independent when compared as a set.
 */
@Serializable
data class DetectedRefactoring(
    val type: String,
    val description: String,
    val leftSideLocations: List<String>,
    val rightSideLocations: List<String>,
    val ideRelevant: Boolean,
)
