package com.github.ethanhosier.ideplugin.model

import kotlinx.serialization.Serializable

@Serializable
data class SessionMetadata(
    val sessionId: String,
    val projectName: String,
    val projectPath: String,
    val branch: String? = null,
    val commitHash: String? = null,
    val startTime: Long,
    val endTime: Long? = null,
    val ideVersion: String,
    val pluginVersion: String,
)
