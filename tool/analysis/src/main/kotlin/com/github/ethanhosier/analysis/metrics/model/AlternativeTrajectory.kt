package com.github.ethanhosier.analysis.metrics.model

import com.github.ethanhosier.analysis.miner.model.RefactoringSpec
import kotlinx.serialization.Serializable

/**
 * Top-level entry on [AnalysisReport] describing one IDE-driven
 * alternative path the user could have taken in place of a manual
 * multi-checkpoint refactoring.
 *
 * Every field is a join key the frontend can use:
 *  - [stepIndex] joins back into [AnalysisReport.refactoringSteps]
 *    and into [AnalysisReport.alternativePatches] for the unified diff.
 *  - [fromSha] / [userToSha] / [altSha] all live in
 *    [AnalysisReport.checkpoints] (the alt SHA's [CheckpointMetrics] is
 *    computed alongside the user's actual checkpoints), so the frontend
 *    can pull metrics for any of the three by SHA lookup.
 *  - [spec] is the typed refactoring info that was fed to the
 *    `RefactoringClient` to synthesise the alt commit — same parameters
 *    a human would pick in the IDE's refactoring dialog.
 */
@Serializable
data class AlternativeTrajectory(
    val stepIndex: Int,
    val fromSha: String,
    val userToSha: String,
    val altSha: String,
    val branchRef: String,
    val spec: RefactoringSpec,
)
