package com.github.ethanhosier.analysis.metrics.ck

import kotlinx.serialization.Serializable

/**
 * CK (class-level OO metrics) output for one checkpoint.
 *
 * `perClass` holds one [CkClassMetrics] entry per successfully parsed class,
 * inner class, or anonymous class. `parseErrors` lists files CK could not
 * parse — this is *data*, not a run-abort signal, because a mid-refactor
 * checkpoint may legitimately contain unparseable Java. Any exception from
 * `CK.calculate()` itself (OOM, classpath issue, …) still propagates and
 * aborts the run.
 */
@Serializable
data class CkResult(
    val perClass: List<CkClassMetrics>,
    val parseErrors: List<CkParseError> = emptyList(),
)

/**
 * Per-class CK metrics. Field set is a curated subset of [com.github.mauricioaniche.ck.CKClassResult]
 * focused on OO structure + complexity proxies; the long tail (per-modifier
 * method/field counts, lambda counts, log-statement counts, …) is omitted for
 * now and can be added later if downstream analysis needs it.
 *
 * `file` is stored relative to the checkpoint root so metrics files stay
 * comparable across checkpoints and machines.
 */
@Serializable
data class CkClassMetrics(
    val className: String,
    val file: String,
    // "class" | "interface" | "innerclass" | "anonymous" | "enum"; CK's own categorisation.
    val type: String,
    // Coupling Between Objects. [0..∞); count of distinct non-JDK types this class depends on (outgoing only).
    val cbo: Int,
    // CBO variant counting both outgoing AND incoming refs. [0..∞); typically ≥ cbo.
    val cboModified: Int,
    // Fan-in: classes that reference this one. [0..∞).
    val fanin: Int,
    // Fan-out: classes referenced by this one. [0..∞).
    val fanout: Int,
    // Weighted Method Count (McCabe summed across methods). [1..∞); base 1 per method.
    val wmc: Int,
    // Response For a Class — number of unique method invocations. [0..∞).
    val rfc: Int,
    // Lack of Cohesion of Methods (v1 definition). [0..∞); 0 = cohesive. Noisy — prefer lcomNormalized.
    val lcom: Int,
    // LCOM* (Henderson-Sellers). [0.0..1.0]; 0 = cohesive, 1 = no cohesion.
    val lcomNormalized: Float,
    // Tight Class Cohesion. [0.0..1.0]; 1 = fully cohesive (direct connections between visible methods).
    val tcc: Float,
    // Loose Class Cohesion. [0.0..1.0]; 1 = fully cohesive. Invariant: lcc ≥ tcc.
    val lcc: Float,
    // Depth of Inheritance Tree. [1..∞); 1 = direct java.lang.Object subclass.
    val dit: Int,
    // Number of Children — immediate subclasses within the project. [0..∞).
    val noc: Int,
    // Source Lines Of Code (excludes blanks and comments). [0..∞).
    val loc: Int,
    // Total methods including the constructor. [0..∞).
    val numberOfMethods: Int,
    // Total declared fields. [0..∞).
    val numberOfFields: Int,
    // Count of `return` statements in the class. [0..∞).
    val returnQty: Int,
    // Count of loop constructs (for / while / do-while / enhanced-for). [0..∞).
    val loopQty: Int,
    // Count of `==` and `!=` comparisons. [0..∞).
    val comparisonsQty: Int,
    // Count of try/catch blocks. [0..∞).
    val tryCatchQty: Int,
    // Count of declared local variables across all methods. [0..∞).
    val variablesQty: Int,
    // Max nesting depth of blocks in any single method. [0..∞); typical ≤ 10.
    val maxNestedBlocks: Int,
    // Unique tokens after stripping Java keywords and splitting camelCase/underscore. [0..∞).
    val uniqueWordsQty: Int,
)

@Serializable
data class CkParseError(
    val file: String,
    val message: String,
)
