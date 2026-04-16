package com.github.ethanhosier.analysis.miner.model

import kotlinx.serialization.Serializable

/**
 * One RefactoringMiner detection tied to a specific `(fromSha → toSha)` pair.
 * Used for inner sliding-window findings — the segment-level view is
 * embedded in [ManualRefactoringSegment] directly and doesn't need its own
 * wrapper (the range is the segment's range).
 */
@Serializable
data class RefactoringFinding(
    val fromSha: String,
    val toSha: String,
    val refactorings: List<DetectedRefactoring>,
)

/**
 * All manual-refactoring results for one segment (run of manual-edit
 * checkpoints between two anchors).
 *
 * [segmentRefactorings] — refactorings detected over the full `(fromSha →
 * toSha)` span. Captures the *net* refactorings the user made across the
 * whole segment. Empty if RM found nothing at segment scope.
 *
 * [innerFindings] — tight `(L*, R*)` windows the sliding-window algorithm
 * locked onto, each localising a specific refactoring to the smallest
 * checkpoint range where it appears.
 */
@Serializable
data class ManualRefactoringSegment(
    val segmentIndex: Int,
    val fromSha: String,
    val toSha: String,
    val segmentRefactorings: List<DetectedRefactoring> = emptyList(),
    val innerFindings: List<RefactoringFinding> = emptyList(),
)
