package com.github.ethanhosier.analysis.metrics.readability

import kotlinx.serialization.Serializable

/**
 * Readability signals for one checkpoint. Three parallel views:
 *  - [perFile] — whole-file text metrics (line length, blank/comment ratios,
 *    indentation) plus aggregated identifier stats.
 *  - [perClass] — identifier + size stats scoped to each class body.
 *  - [perMethod] — identifier + size stats scoped to each method body.
 *
 * Identifier stats cover declarations (variables, parameters, fields, plus the
 * member's own name where applicable). Reference sites are not counted — same
 * identifier at every call site would inflate the numbers without adding
 * signal.
 */
@Serializable
data class ReadabilityResult(
    val perFile: List<FileReadability>,
    val perClass: List<ClassReadability>,
    val perMethod: List<MethodReadability>,
    val summary: ReadabilitySummary = ReadabilitySummary.EMPTY,
    val durationMs: Long = 0,
) {
    companion object {
        val EMPTY = ReadabilityResult(emptyList(), emptyList(), emptyList(), ReadabilitySummary.EMPTY)
    }
}

/**
 * Checkpoint-level roll-up across every file / class / method. Raw aggregates
 * only — no composite "readability score". A proper model-based score
 * (Scalabrino / Buse-Weimer via readabilitySHARK) is a follow-up; exposing
 * the raw numbers lets downstream code plug in whatever weighting it wants.
 */
@Serializable
data class ReadabilitySummary(
    val fileCount: Int,
    val classCount: Int,
    val methodCount: Int,
    // File-level averages (mean across files).
    val avgLineLength: Double,
    val maxLineLength: Int,                   // max across all files
    val avgCommentRatio: Double,
    val avgBlankRatio: Double,
    val avgIndentation: Double,
    val maxIndentation: Int,                  // max across all files
    // Identifier stats aggregated across all declared identifiers in the checkpoint.
    val avgIdentifierLength: Double,
    val singleLetterRatio: Double,
    val avgWordCount: Double,
    val dictionaryWordRatio: Double,
    // "Worst" seeds (useful proxies until a real score lands).
    val worstClassLoc: Int,                   // largest class LOC
    val worstMethodLoc: Int,                  // largest method LOC
) {
    companion object {
        val EMPTY = ReadabilitySummary(
            fileCount = 0, classCount = 0, methodCount = 0,
            avgLineLength = 0.0, maxLineLength = 0,
            avgCommentRatio = 0.0, avgBlankRatio = 0.0,
            avgIndentation = 0.0, maxIndentation = 0,
            avgIdentifierLength = 0.0, singleLetterRatio = 0.0,
            avgWordCount = 0.0, dictionaryWordRatio = 0.0,
            worstClassLoc = 0, worstMethodLoc = 0,
        )
    }
}

/**
 * Identifier-level aggregates for one scope. `count` is the number of
 * declared identifiers considered. All ratios return 0.0 when `count == 0`.
 */
@Serializable
data class IdentifierStats(
    val count: Int,
    val avgLength: Double,
    val singleLetterRatio: Double,            // fraction of identifiers with length == 1
    val avgWordCount: Double,                 // avg parts after camelCase / snake_case split
    val dictionaryWordRatio: Double,          // fraction of split parts (len >= 2) in the bundled English wordlist
) {
    companion object {
        val EMPTY = IdentifierStats(0, 0.0, 0.0, 0.0, 0.0)
    }
}

@Serializable
data class FileReadability(
    val file: String,
    val totalLines: Int,
    val codeLines: Int,
    val commentLines: Int,
    val blankLines: Int,
    val commentLineRatio: Double,
    val blankLineRatio: Double,
    val avgLineLength: Double,                // among non-blank lines
    val maxLineLength: Int,
    val avgIndentation: Double,               // spaces; tab counted as 4
    val maxIndentation: Int,
    val identifiers: IdentifierStats,
)

@Serializable
data class ClassReadability(
    val className: String,
    val file: String,
    val loc: Int,                             // physical lines of the class declaration
    val identifiers: IdentifierStats,
)

@Serializable
data class MethodReadability(
    val className: String,
    val signature: String,
    val file: String,
    val loc: Int,                             // physical lines of the method declaration
    val identifiers: IdentifierStats,
)
