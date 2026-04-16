package com.github.ethanhosier.analysis.miner.model

import kotlinx.serialization.Serializable

/**
 * A single refactoring detected by RefactoringMiner between two checkpoint
 * commits, normalised so RM's own types don't leak into serialized output.
 *
 * [type] is the `RefactoringType.displayName` (e.g. `"Extract Method"`).
 * [description] is RM's human-readable one-liner — convenient for readers,
 * but not stable enough to key on. [leftSideLocations] and
 * [rightSideLocations] carry both a human-readable [RefactoringLocation.key]
 * (for debugging / eyeballing) and the structured fields
 * (`filePath`, lines/columns, element type + text) so downstream consumers
 * don't have to parse the string form.
 */
@Serializable
data class DetectedRefactoring(
    val type: String,
    val description: String,
    val leftSideLocations: List<RefactoringLocation>,
    val rightSideLocations: List<RefactoringLocation>,
    val ideRelevant: Boolean,
)

/**
 * One location touched by a detected refactoring, in either the "before"
 * (left) or "after" (right) state of the diff.
 *
 * [key] is the same compact `file:startLine-endLine [codeElementType]
 * codeElement` string the canonical-equality check uses for sliding-window
 * matching — kept in the output so the serialized view is readable at a
 * glance without reconstructing it from the structured fields.
 */
@Serializable
data class RefactoringLocation(
    val key: String,
    val filePath: String,
    val startLine: Int,
    val endLine: Int,
    val startColumn: Int,
    val endColumn: Int,
    val codeElementType: String,
    val codeElement: String?,
)
