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
    val type: String,
    val cbo: Int,
    val cboModified: Int,
    val fanin: Int,
    val fanout: Int,
    val wmc: Int,
    val rfc: Int,
    val lcom: Int,
    val lcomNormalized: Float,
    val tcc: Float,
    val lcc: Float,
    val dit: Int,
    val noc: Int,
    val loc: Int,
    val numberOfMethods: Int,
    val numberOfFields: Int,
    val returnQty: Int,
    val loopQty: Int,
    val comparisonsQty: Int,
    val tryCatchQty: Int,
    val variablesQty: Int,
    val maxNestedBlocks: Int,
    val uniqueWordsQty: Int,
)

@Serializable
data class CkParseError(
    val file: String,
    val message: String,
)
