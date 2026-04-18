package com.github.ethanhosier.analysis.metrics.gitdiff

import kotlinx.serialization.Serializable

/**
 * Churn and file-change counts for the transition *into* a checkpoint, i.e.
 * `git diff -M <prev> <sha>` where `<prev>` is the previous checkpoint or
 * the seed commit for the first one.
 */
@Serializable
data class DiffStats(
    val filesTouched: Int,
    val linesAdded: Int,
    val linesDeleted: Int,
    val totalChurn: Int,
    val filesAdded: Int,
    val filesModified: Int,
    val filesDeleted: Int,
    val filesRenamed: Int,
    val perFileChurn: List<PerFileChurn>,
) {
    companion object {
        val ZERO = DiffStats(0, 0, 0, 0, 0, 0, 0, 0, emptyList())
    }
}

@Serializable
data class PerFileChurn(
    val path: String,
    val linesAdded: Int,
    val linesDeleted: Int,
)
