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

/**
 * Walks class / method declarations and collects the identifier names
 * declared inside each scope. Follows the same rule-as-visitor pattern as
 * [com.github.ethanhosier.analysis.metrics.pmd.PmdMetricsCollectorRule].
 *
 * Collected names:
 *  - class scope: the class's simple name, all field names, all method
 *    names declared at this level.
 *  - method scope: the method's own name, parameter names, and local
 *    variable names.
 *
 * Reference sites (calls, field reads) are not counted — the goal is to
 * measure the *vocabulary a reader has to learn* per scope, which is the
 * set of declarations, not how often each name is used.
 */
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
        // Direct-child fields (including multi-declarator forms) and method
        // names declared at this class level. Nested classes' members are
        // collected when their own ASTTypeDeclaration visit fires.
        node.children(ASTFieldDeclaration::class.java).forEach { field ->
            field.descendants(ASTVariableId::class.java).forEach { identifiers += it.name }
        }
        node.children(ASTExecutableDeclaration::class.java).forEach { identifiers += it.name }
        val span = node.textDocument.toLocation(node.textRegion)
        sink.classes += CollectedClass(
            className = node.binaryName,
            file = fileOf(node),
            // reportLocation / beginLine narrow to the class name token; map
            // the node's full textRegion back through the document to get
            // start/end lines covering the whole declaration including body.
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
