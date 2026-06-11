package com.github.ethanhosier.analysis.metrics.model

import kotlinx.serialization.Serializable

@Serializable
data class ReorderTrajectory(
    val windowIndex: Int,
    val windowFromSha: String,
    val windowToSha: String,
    val windowStepIndexes: List<Int>,
    val windowSpecLabels: List<String>,
    val orderings: List<ReorderOrdering>,
)

@Serializable
data class ReorderOrdering(
    val orderIndex: Int,
    val permutation: List<Int>,
    val permutationLabels: List<String>,
    val stepShas: List<String>,
    val branchRefs: List<String>,
    val terminalSuccess: Boolean,
    val failedAt: Int? = null,
    val terminalDivergedFiles: List<String>? = null,
)
