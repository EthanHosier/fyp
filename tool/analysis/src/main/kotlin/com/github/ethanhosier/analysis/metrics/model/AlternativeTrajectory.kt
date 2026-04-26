package com.github.ethanhosier.analysis.metrics.model

import com.github.ethanhosier.analysis.miner.model.RefactoringSpec
import kotlinx.serialization.Serializable

/**
 * Top-level entry on [AnalysisReport] describing one IDE-driven
 * alternative path the user could have taken in place of a manual
 * multi-checkpoint refactoring.
 *
 * The alt-side checkpoint (its metrics + transition diff) lives inside
 * [altCheckpoint] rather than being folded into the report's main
 * [AnalysisReport.checkpoints] array — that array stays the user's
 * actual trajectory, uncluttered by synthesised SHAs.
 *
 * Join keys for the frontend:
 *  - [stepIndex] joins back into [AnalysisReport.refactoringSteps]
 *    and into [AnalysisReport.alternativePatches] for the unified diff.
 *  - [fromSha] / [userToSha] are SHAs in [AnalysisReport.checkpoints]
 *    (the user's actual pre / post states).
 *  - [altCheckpoint].sha is the synthesised alt SHA and is reachable in
 *    the shadow repo via [branchRef].
 *  - [spec] is the typed refactoring info that was fed to the
 *    `RefactoringClient` to synthesise the alt commit — same parameters
 *    a human would pick in the IDE's refactoring dialog.
 */
@Serializable
data class AlternativeTrajectory(
    val stepIndex: Int,
    val fromSha: String,
    val userToSha: String,
    val branchRef: String,
    val spec: RefactoringSpec,
    // Sha + metrics + (fromSha → altSha) diff for the synthesised
    // commit. `events` / `touchedMembers` are always empty — alt SHAs
    // aren't landed on by user events.
    val altCheckpoint: CheckpointReport,
)
