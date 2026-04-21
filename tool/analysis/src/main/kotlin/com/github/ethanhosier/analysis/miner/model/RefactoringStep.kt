package com.github.ethanhosier.analysis.miner.model

import kotlinx.serialization.Serializable

/**
 * One refactoring detected by the end-to-end sliding window, localised to
 * the tightest `(fromSha → toSha)` checkpoint range where it appears.
 *
 * A single sliding-window tight window may yield multiple detections (e.g.
 * an Extract Method plus its Move); each becomes its own [RefactoringStep]
 * with the same `fromSha` / `toSha` / `toCheckpointIndex`. In practice edit
 * bursts are fine-grained enough that `refactoring-per-window = 1` is the
 * overwhelming norm.
 */
@Serializable
data class RefactoringStep(
    val stepIndex: Int,
    val fromSha: String,
    val toSha: String,
    val toCheckpointIndex: Int,
    val timestamp: Long,
    val refactoring: DetectedRefactoring,
)
