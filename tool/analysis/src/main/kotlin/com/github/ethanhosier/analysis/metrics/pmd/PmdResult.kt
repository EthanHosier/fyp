package com.github.ethanhosier.analysis.metrics.pmd

import kotlinx.serialization.Serializable

/**
 * PMD output for one checkpoint.
 *
 * `violations` lists every reported rule violation. `processingErrors` lists
 * files PMD could not analyse — same rationale as `CkResult.parseErrors`:
 * treated as data rather than a run-abort signal, since a mid-refactor
 * checkpoint may contain unparseable Java. Any exception thrown out of
 * `PmdAnalysis.performAnalysisAndCollectReport()` itself still propagates.
 */
@Serializable
data class PmdResult(
    val violations: List<PmdViolation>,
    val processingErrors: List<PmdProcessingError> = emptyList(),
)

/**
 * A single rule violation. `file` is relative to the checkpoint root so
 * results stay comparable across checkpoints. `priority` is PMD's 1..5 scale
 * where 1 is highest severity.
 */
@Serializable
data class PmdViolation(
    val file: String,
    val rule: String,
    val ruleSet: String,
    val priority: Int,
    val beginLine: Int,
    val endLine: Int,
    val message: String,
)

@Serializable
data class PmdProcessingError(
    val file: String,
    val message: String,
)
