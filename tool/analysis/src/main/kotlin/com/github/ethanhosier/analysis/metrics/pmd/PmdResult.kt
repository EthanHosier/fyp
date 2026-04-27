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
    val classMetrics: List<PmdClassMetrics> = emptyList(),
    val methodMetrics: List<PmdMethodMetrics> = emptyList(),
    val processingErrors: List<PmdProcessingError> = emptyList(),
)

/**
 * Class-level quantitative metrics from PMD's metrics framework. Picked to
 * complement CK rather than duplicate it — CK already gives WMC/TCC/LCOM/fan-in
 * etc.
 */
@Serializable
data class PmdClassMetrics(
    val className: String,
    val file: String,
    // non-commenting source statements (statement-based size signal). [0..∞); one per executable statement.
    val ncss: Int,
    // access to foreign data — feature-envy signal. [0..∞); counts refs to other classes' fields/accessors.
    val atfd: Int,
    // number of accessor methods (getters/setters). [0..numberOfMethods].
    val noam: Int,
    // number of public attributes. [0..numberOfFields].
    val nopa: Int,
    // weight of class — ratio of non-trivial methods to total. [0.0..1.0] or null when undefined (class has no methods). 1.0 = all behavioural, 0.0 = pure data class.
    val woc: Double?,
)

/**
 * Method-level quantitative metrics. CK reports WMC at class level only, so
 * these give per-method resolution for complexity signals.
 */
@Serializable
data class PmdMethodMetrics(
    val className: String,
    val signature: String,
    val file: String,
    // cyclomatic complexity (McCabe). [1..∞); base 1, +1 per branch (if/for/while/case/catch/&&/||/?:). PMD's "high" threshold: 10.
    val cyclo: Int,
    // cognitive complexity (Sonar). [0..∞); 0 = linear, weights deep nesting heavily. PMD's "high" threshold: 15.
    val cognitive: Int,
    // number of acyclic execution paths. [1..BigInteger]; grows multiplicatively through nested branching. Stored as string because the value can exceed Long on heavily nested code.
    val npath: String,
    // non-commenting source statements in the method body. [0..∞).
    val ncss: Int,
    // access to foreign data at method level. [0..∞); >3 per method is often flagged as feature envy.
    val atfd: Int,
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
    // PMD severity. [1..5]; 1 = highest (likely bug), 5 = lowest (style nit).
    val priority: Int,
    val beginLine: Int,
    val endLine: Int,
    val message: String,
    val snippet: PmdViolationSnippet? = null,
)

/**
 * Source excerpt around a violation: the violation's line range plus a few
 * lines of surrounding context, mirroring `git diff -U3` so the UI can render
 * snippets the same way it renders diff hunks. Null when the file can't be
 * read (processing errors, vanished file, etc.).
 */
@Serializable
data class PmdViolationSnippet(
    // 1-based line of the first line in `code`.
    val contextStartLine: Int,
    // Raw lines joined with "\n", no trailing newline.
    val code: String,
)

@Serializable
data class PmdProcessingError(
    val file: String,
    val message: String,
)
