package com.github.ethanhosier.analysis.model

import com.github.ethanhosier.analysis.reconstruct.EventCommitMap
import java.nio.file.Path

data class ReconstructionResult(
    val repoDir: Path,
    val eventCommits: EventCommitMap,
)
