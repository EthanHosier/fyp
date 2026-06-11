package com.github.ethanhosier.analysis.reconstruct

import kotlinx.serialization.Serializable

@Serializable
data class EventCommitMap(val mapping: Map<String, String>)
