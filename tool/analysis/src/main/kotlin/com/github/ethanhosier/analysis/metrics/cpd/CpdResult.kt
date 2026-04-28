package com.github.ethanhosier.analysis.metrics.cpd

import kotlinx.serialization.Serializable

/**
 * PMD CPD (Copy/Paste Detector) output for one checkpoint.
 *
 * Aggregates are the cheap-to-query fields that [TrajectoryStats] reads each
 * step; [duplications] is the flat list of each detected clone group with all
 * its occurrences, kept so a report reader can see *where* duplication lives.
 *
 * `totalLines` / `totalTokens` cover the checkpoint's full scanned Java
 * source, so `duplicatedLinesShare` is a proper concentration ratio even as
 * the project grows.
 */
@Serializable
data class CpdResult(
    val duplicationBlocks: Int,
    val duplicatedLines: Int,
    val duplicatedTokens: Int,
    val totalLines: Int,
    val totalTokens: Int,
    val duplicatedLinesShare: Double,
    val largestBlockLines: Int,
    val largestBlockTokens: Int,
    val filesInvolvedInDuplication: Int,
    val duplications: List<CpdDuplication>,
    val processingErrors: List<CpdProcessingError> = emptyList(),
    val durationMs: Long = 0,
) {
    companion object {
        val EMPTY = CpdResult(
            duplicationBlocks = 0,
            duplicatedLines = 0,
            duplicatedTokens = 0,
            totalLines = 0,
            totalTokens = 0,
            duplicatedLinesShare = 0.0,
            largestBlockLines = 0,
            largestBlockTokens = 0,
            filesInvolvedInDuplication = 0,
            duplications = emptyList(),
        )
    }
}

/**
 * One clone group: the same tokenised region appearing in >= 2 places.
 * `tokens` / `lines` describe one occurrence — every occurrence in
 * [occurrences] has the same token/line shape (that's what makes them a
 * group).
 */
@Serializable
data class CpdDuplication(
    val tokens: Int,
    val lines: Int,
    val occurrences: List<CpdOccurrence>,
)

@Serializable
data class CpdOccurrence(
    val file: String,
    val beginLine: Int,
    val endLine: Int,
)

@Serializable
data class CpdProcessingError(
    val file: String,
    val message: String,
)
