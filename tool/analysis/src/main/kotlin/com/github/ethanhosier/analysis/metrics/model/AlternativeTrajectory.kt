package com.github.ethanhosier.analysis.metrics.model

import com.github.ethanhosier.analysis.miner.model.RefactoringSpec
import com.github.ethanhosier.analysis.pipeline.CheckpointReport
import com.github.ethanhosier.analysis.pipeline.DivergenceKind
import kotlinx.serialization.Serializable

@Serializable
data class AlternativeTrajectory(
    val kind: DivergenceKind = DivergenceKind.ORDERING,
    val stepIndexes: List<Int>,
    val fromSha: String,
    val userToSha: String,
    val branchRefs: List<String>,
    val specs: List<RefactoringSpec>,
    val altCheckpoints: List<CheckpointReport>,
    val residual: ResidualSummary? = null,
    val continuationCheckpoints: List<CheckpointReport> = emptyList(),
    val altCheckpointUserIndexes: List<Int> = emptyList(),
)

@Serializable
data class ResidualSummary(
    val applied: Boolean,
    val addedLines: Int,
    val deletedLines: Int,
    val rejectedFiles: List<String>,
)
