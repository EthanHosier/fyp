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
    /**
     * Stable per-checkpoint key for this clone group, used by
     * cross-checkpoint trackers to detect when a clone is added or
     * resolved. Hash of the snippet body (whitespace-trimmed lines from
     * one occurrence's snippet patch); two groups with identical body
     * text — even if their occurrence locations differ — share an
     * identity. Empty default keeps old reports deserialisable.
     */
    val identity: String = "",
)

@Serializable
data class CpdOccurrence(
    val file: String,
    val beginLine: Int,
    val endLine: Int,
    /**
     * Source text at this occurrence, framed as a self-contained mini
     * unified-diff (every line a context line). Null when the file was
     * unreadable at analysis time. Default-null preserves backward
     * compatibility with reports generated before snippets shipped.
     */
    val snippet: CpdSnippet? = null,
)

/**
 * Wraps the unified-diff-shaped source text for one [CpdOccurrence].
 * Mirrors `PmdViolationSnippet` so frontend renderers can treat both the
 * same way.
 */
@Serializable
data class CpdSnippet(val patch: String)

@Serializable
data class CpdProcessingError(
    val file: String,
    val message: String,
)
