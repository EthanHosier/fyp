package com.github.ethanhosier.analysis.miner.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

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
    /** True iff the `toSha` checkpoint carries a `REFACTORING_FINISHED`
     *  event — i.e. the IDE performed this refactoring, not the user by
     *  hand. */
    val wasPerformedByIde: Boolean = false,
    /** Typed structured payload used by `AlternativeTrajectoryRunner` to
     *  drive `RefactoringClient`. `@Transient` — excluded from JSON and
     *  the generated TS types; defaults to `null` so deserialised
     *  reports and hand-built test fixtures still compile. */
    @Transient val spec: RefactoringSpec? = null,
)
