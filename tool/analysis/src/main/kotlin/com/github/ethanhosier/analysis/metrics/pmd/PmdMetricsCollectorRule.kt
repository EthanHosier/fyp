package com.github.ethanhosier.analysis.metrics.pmd

import net.sourceforge.pmd.lang.java.JavaLanguageModule
import net.sourceforge.pmd.lang.java.ast.ASTExecutableDeclaration
import net.sourceforge.pmd.lang.java.ast.ASTTypeDeclaration
import net.sourceforge.pmd.lang.java.ast.JavaNode
import net.sourceforge.pmd.lang.java.ast.internal.PrettyPrintingUtil
import net.sourceforge.pmd.lang.java.metrics.JavaMetrics
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule
import net.sourceforge.pmd.lang.metrics.MetricsUtil
import net.sourceforge.pmd.lang.rule.Rule
import java.util.Collections

/**
 * Harvests PMD's quantitative metric values at each class and method node.
 *
 * A rule is the idiomatic AST-visit entry point in PMD 7.x — the built-in
 * `CyclomaticComplexityRule` follows exactly this shape. We do not call
 * `addViolation`, so the rule never contributes to the violation report; its
 * sole purpose is to populate the [Sink] during the analysis pass, which
 * [PmdRunner] reads back after the run.
 *
 * PMD clones each rule per analysis thread via [deepCopy] (reflective no-arg
 * construction), so a per-instance sink would be lost. We override `deepCopy`
 * to propagate the original sink into the clone; lists are synchronised so
 * concurrent clones can append safely.
 */
internal class PmdMetricsCollectorRule : AbstractJavaRulechainRule(
    ASTTypeDeclaration::class.java,
    ASTExecutableDeclaration::class.java,
) {

    data class CollectedClass(
        val className: String,
        val file: String,
        val ncss: Int,
        val atfd: Int,
        val noam: Int,
        val nopa: Int,
        val woc: Double?,
    )

    data class CollectedMethod(
        val className: String,
        val signature: String,
        val file: String,
        val cyclo: Int,
        val cognitive: Int,
        val npath: String,
        val ncss: Int,
        val atfd: Int,
    )

    class Sink {
        val classMetrics: MutableList<CollectedClass> =
            Collections.synchronizedList(mutableListOf())
        val methodMetrics: MutableList<CollectedMethod> =
            Collections.synchronizedList(mutableListOf())
    }

    private var sink: Sink = Sink()

    val classMetrics: List<CollectedClass>
        get() = synchronized(sink.classMetrics) { sink.classMetrics.toList() }
    val methodMetrics: List<CollectedMethod>
        get() = synchronized(sink.methodMetrics) { sink.methodMetrics.toList() }

    init {
        name = "AnalysisMetricsCollector"
        message = "internal metrics collector — never reports"
        language = JavaLanguageModule.getInstance()
    }

    override fun deepCopy(): Rule {
        val copy = super.deepCopy() as PmdMetricsCollectorRule
        copy.sink = this.sink
        return copy
    }

    override fun visitJavaNode(node: JavaNode, param: Any?): Any? {
        when (node) {
            is ASTTypeDeclaration -> collectClass(node)
            is ASTExecutableDeclaration -> collectMethod(node)
        }
        return param
    }

    private fun collectClass(node: ASTTypeDeclaration) {
        sink.classMetrics += CollectedClass(
            className = node.binaryName,
            file = fileOf(node),
            ncss = MetricsUtil.computeMetric(JavaMetrics.NCSS, node),
            atfd = MetricsUtil.computeMetric(JavaMetrics.ACCESS_TO_FOREIGN_DATA, node),
            noam = MetricsUtil.computeMetric(JavaMetrics.NUMBER_OF_ACCESSORS, node),
            nopa = MetricsUtil.computeMetric(JavaMetrics.NUMBER_OF_PUBLIC_FIELDS, node),
            // PMD returns NaN for WOC when the class has no methods. Map
            // NaN → null so we emit standard JSON and preserve the
            // "undefined" distinction from a real 0.0.
            woc = MetricsUtil.computeMetric(JavaMetrics.WEIGHT_OF_CLASS, node).takeIf { it.isFinite() },
        )
    }

    private fun collectMethod(node: ASTExecutableDeclaration) {
        sink.methodMetrics += CollectedMethod(
            className = node.enclosingType.binaryName,
            signature = PrettyPrintingUtil.displaySignature(node),
            file = fileOf(node),
            cyclo = MetricsUtil.computeMetric(JavaMetrics.CYCLO, node),
            cognitive = MetricsUtil.computeMetric(JavaMetrics.COGNITIVE_COMPLEXITY, node),
            npath = MetricsUtil.computeMetric(JavaMetrics.NPATH, node).toString(),
            ncss = MetricsUtil.computeMetric(JavaMetrics.NCSS, node),
            atfd = MetricsUtil.computeMetric(JavaMetrics.ACCESS_TO_FOREIGN_DATA, node),
        )
    }

    private fun fileOf(node: JavaNode): String =
        node.reportLocation.fileId.absolutePath
}
