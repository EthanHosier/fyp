package com.github.ethanhosier.ideplugin.model

import kotlinx.serialization.Serializable

@Serializable
data class Checkpoint(
    val id: String,
    val sequenceNumber: Int,
    val triggerType: String,
    val timestamp: Long,
    val sessionId: String,
    val branch: String? = null,
    val commitHash: String? = null,
    val recentEventIds: List<String> = emptyList(),
    val changedFiles: List<CheckpointFile> = emptyList(),
    val validationSummary: ValidationSummary = ValidationSummary(),
    val activeFilePath: String? = null,
)
