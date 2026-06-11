package com.github.ethanhosier.analysis.metrics.readability

import net.sourceforge.pmd.lang.java.JavaLanguageModule
import net.sourceforge.pmd.lang.java.ast.ASTClassDeclaration
import net.sourceforge.pmd.lang.java.ast.ASTExecutableDeclaration
import net.sourceforge.pmd.lang.java.ast.ASTFieldDeclaration
import net.sourceforge.pmd.lang.java.ast.ASTTypeDeclaration
import net.sourceforge.pmd.lang.java.ast.ASTVariableId
import net.sourceforge.pmd.lang.java.ast.JavaNode
import net.sourceforge.pmd.lang.java.ast.internal.PrettyPrintingUtil
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule
import net.sourceforge.pmd.lang.rule.Rule
import java.util.Collections

internal class ReadabilityCollectorRule : AbstractJavaRulechainRule(
    ASTTypeDeclaration::class.java,
    ASTExecutableDeclaration::class.java,
) {

    data class CollectedClass(
        val className: String,
        val file: String,
        val beginLine: Int,
        val endLine: Int,
        val identifiers: List<String>,
    )

    data class CollectedMethod(
        val className: String,
        val signature: String,
        val file: String,
        val beginLine: Int,
        val endLine: Int,
        val identifiers: List<String>,
    )

    class Sink {
        val classes: MutableList<CollectedClass> =
            Collections.synchronizedList(mutableListOf())
        val methods: MutableList<CollectedMethod> =
            Collections.synchronizedList(mutableListOf())
    }

    private var sink: Sink = Sink()

    val classes: List<CollectedClass>
        get() = synchronized(sink.classes) { sink.classes.toList() }
    val methods: List<CollectedMethod>
        get() = synchronized(sink.methods) { sink.methods.toList() }

    init {
        name = "AnalysisReadabilityCollector"
        message = "internal readability collector — never reports"
        language = JavaLanguageModule.getInstance()
    }

    override fun deepCopy(): Rule {
        val copy = super.deepCopy() as ReadabilityCollectorRule
        copy.sink = this.sink
        return copy
    }

    override fun visitJavaNode(node: JavaNode, param: Any?): Any? {
        when (node) {
            is ASTTypeDeclaration -> collectType(node)
            is ASTExecutableDeclaration -> collectMethod(node)
        }
        return param
    }

    private fun collectType(node: ASTTypeDeclaration) {
        val identifiers = mutableListOf<String>()
        (node as? ASTClassDeclaration)?.simpleName?.let { identifiers += it }
        node.children(ASTFieldDeclaration::class.java).forEach { field ->
            field.descendants(ASTVariableId::class.java).forEach { identifiers += it.name }
        }
        node.children(ASTExecutableDeclaration::class.java).forEach { identifiers += it.name }
        val span = node.textDocument.toLocation(node.textRegion)
        sink.classes += CollectedClass(
            className = node.binaryName,
            file = fileOf(node),
            beginLine = span.startLine,
            endLine = span.endLine,
            identifiers = identifiers,
        )
    }

    private fun collectMethod(node: ASTExecutableDeclaration) {
        val identifiers = mutableListOf<String>()
        identifiers += node.name
        // Params + locals: every ASTVariableId declared anywhere under the
        // method body/formal parameters.
        node.descendants(ASTVariableId::class.java).forEach { identifiers += it.name }
        val span = node.textDocument.toLocation(node.textRegion)
        sink.methods += CollectedMethod(
            className = node.enclosingType.binaryName,
            signature = PrettyPrintingUtil.displaySignature(node),
            file = fileOf(node),
            beginLine = span.startLine,
            endLine = span.endLine,
            identifiers = identifiers,
        )
    }

    private fun fileOf(node: JavaNode): String =
        node.reportLocation.fileId.absolutePath
}
