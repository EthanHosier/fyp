package com.github.ethanhosier.analysis.model

import com.github.ethanhosier.analysis.pipeline.serialization.PathAsStringSerializer
import com.github.ethanhosier.analysis.reconstruct.EventCommitMap
import kotlinx.serialization.Serializable
import java.nio.file.Path

@Serializable
data class ReconstructionResult(
    @Serializable(with = PathAsStringSerializer::class)
    val repoDir: Path,
    val eventCommits: EventCommitMap,
)
