package com.github.ethanhosier.analysis.miner.model

import kotlinx.serialization.Serializable

/**
 * One RefactoringMiner detection result tied to a specific checkpoint range.
 *
 * Two scopes coexist in the output:
 *
 * - [FindingScope.SEGMENT] — one finding per segment between anchors,
 *   covering the full `(A_L → A_R)` range. Captures the net set of
 *   refactorings the user made between two automated refactorings.
 * - [FindingScope.INNER] — the tightest `(L*, R*)` window the sliding-window
 *   algorithm locked onto. One or more per segment, each localising a
 *   specific refactoring to the smallest checkpoint range where it appears.
 */
@Serializable
data class RefactoringFinding(
    val segmentIndex: Int,
    val scope: FindingScope,
    val fromSha: String,
    val toSha: String,
    val refactorings: List<DetectedRefactoring>,
)

@Serializable
enum class FindingScope { SEGMENT, INNER }
