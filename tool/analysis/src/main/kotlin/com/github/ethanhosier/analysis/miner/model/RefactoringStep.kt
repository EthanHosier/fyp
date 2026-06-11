package com.github.ethanhosier.analysis.miner.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class RefactoringStep(
    val stepIndex: Int,
    val fromSha: String,
    val toSha: String,
    val toCheckpointIndex: Int,
    val timestamp: Long,
    val refactoring: DetectedRefactoring,
    val wasPerformedByIde: Boolean = false,
    val settledSha: String = toSha,
    @Transient val spec: RefactoringSpec? = null,
)
