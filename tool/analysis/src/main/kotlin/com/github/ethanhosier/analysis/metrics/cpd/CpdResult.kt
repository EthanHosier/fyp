package com.github.ethanhosier.analysis.metrics.cpd

import kotlinx.serialization.Serializable

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

@Serializable
data class CpdDuplication(
    val tokens: Int,
    val lines: Int,
    val occurrences: List<CpdOccurrence>,
    val identity: String = "",
)

@Serializable
data class CpdOccurrence(
    val file: String,
    val beginLine: Int,
    val endLine: Int,
    val snippet: CpdSnippet? = null,
)

@Serializable
data class CpdSnippet(val patch: String)

@Serializable
data class CpdProcessingError(
    val file: String,
    val message: String,
)
