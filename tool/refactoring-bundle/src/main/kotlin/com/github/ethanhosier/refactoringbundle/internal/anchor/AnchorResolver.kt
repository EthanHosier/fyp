package com.github.ethanhosier.refactoringbundle.internal.anchor

import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.core.dom.AST
import org.eclipse.jdt.core.dom.ASTNode
import org.eclipse.jdt.core.dom.ASTParser
import org.eclipse.jdt.core.dom.ASTVisitor
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration
import org.eclipse.jdt.core.dom.Block
import org.eclipse.jdt.core.dom.Comment
import org.eclipse.jdt.core.dom.CompilationUnit
import org.eclipse.jdt.core.dom.MethodDeclaration
import org.eclipse.jdt.core.dom.SingleVariableDeclaration
import org.eclipse.jdt.core.dom.Statement
import org.eclipse.jdt.core.dom.VariableDeclarationFragment
import kotlin.math.abs

/**
 * Mirror of analysis-side `SpecAnchorBuilder`: re-finds the host
 * method, then resolves an anchor-addressed selection or declaration
 * by hash. Walks recursively into every nested [Block] (for
 * sibling-statement windows) and every AST node under the host (for
 * single-node selections / declaration nodes), so selections inside
 * `if` / `for` bodies and sub-expression selections both round-trip.
 */
internal object AnchorResolver {

    data class Selection(val firstNode: ASTNode, val lastNode: ASTNode) {
        val startPosition: Int get() = firstNode.startPosition
        val length: Int get() = lastNode.startPosition + lastNode.length - firstNode.startPosition
    }

    fun parse(icu: ICompilationUnit): CompilationUnit {
        val parser = ASTParser.newParser(AST.JLS_Latest).apply {
            setKind(ASTParser.K_COMPILATION_UNIT)
            setSource(icu)
            setResolveBindings(false)
        }
        return parser.createAST(null) as CompilationUnit
    }

    fun findHostMethod(
        cu: CompilationUnit,
        declaringTypeFqn: String,
        methodName: String,
        paramTypes: Array<String>,
    ): MethodDeclaration? {
        var found: MethodDeclaration? = null
        cu.accept(object : ASTVisitor() {
            override fun visit(node: MethodDeclaration): Boolean {
                if (found != null) return false
                if (node.name.identifier != methodName) return true
                if (qualifiedName(node) != declaringTypeFqn) return true
                if (paramSignatures(node) != paramTypes.toList()) return true
                found = node
                return false
            }
        })
        return found
    }

    /**
     * Find a sibling-statement window of size [nodeCount] whose hash
     * equals [expectedHash], OR (when [nodeCount] == 1) a single AST
     * node whose hash equals [expectedHash]. Searches every [Block]
     * under [host] for windows; falls back to a full subtree walk for
     * single-node matches (covers sub-expression selections).
     */
    fun findSelection(
        host: MethodDeclaration,
        expectedHash: String,
        nodeCount: Int,
        tieBreakLineHint: Int?,
    ): Selection? {
        if (nodeCount <= 0) return null
        val cu = host.root as CompilationUnit

        val matches = mutableListOf<Selection>()

        // Multi- (or single-) statement windows inside any nested Block.
        host.accept(object : ASTVisitor() {
            override fun visit(node: Block): Boolean {
                @Suppress("UNCHECKED_CAST")
                val stmts = node.statements() as List<Statement>
                if (nodeCount > stmts.size) return true
                for (i in 0..(stmts.size - nodeCount)) {
                    val window = stmts.subList(i, i + nodeCount)
                    @Suppress("UNCHECKED_CAST")
                    val hash = AstSubtreeHasher.hashNodes(window as List<ASTNode>)
                    if (hash == expectedHash) {
                        matches.add(Selection(window.first(), window.last()))
                    }
                }
                return true
            }
        })

        // Single-node fallback: matches any AST node whose own hash
        // equals expectedHash. Catches sub-expression selections (e.g.
        // extract-variable on an InfixExpression inside one statement).
        if (nodeCount == 1) {
            host.accept(object : ASTVisitor() {
                override fun preVisit2(node: ASTNode): Boolean {
                    if (node is Comment) return false
                    if (node is Block) return true  // already covered above
                    if (AstSubtreeHasher.hashNodes(listOf(node)) == expectedHash) {
                        matches.add(Selection(node, node))
                    }
                    return true
                }
            })
        }

        if (matches.isEmpty()) return null
        if (matches.size == 1 || tieBreakLineHint == null) return matches.first()
        return matches.minByOrNull { abs(cu.getLineNumber(it.startPosition) - tieBreakLineHint) }
    }

    /** Find the [VariableDeclarationFragment] / [SingleVariableDeclaration]
     *  under [host] whose hash equals [expectedHash]. */
    fun findDeclaration(
        host: MethodDeclaration,
        expectedHash: String,
        tieBreakLineHint: Int?,
    ): ASTNode? {
        val cu = host.root as CompilationUnit
        val matches = mutableListOf<ASTNode>()
        host.accept(object : ASTVisitor() {
            override fun visit(node: VariableDeclarationFragment): Boolean = collect(node)
            override fun visit(node: SingleVariableDeclaration): Boolean = collect(node)
            private fun collect(node: ASTNode): Boolean {
                if (AstSubtreeHasher.hashNode(node) == expectedHash) matches.add(node)
                return true
            }
        })
        if (matches.isEmpty()) return null
        if (matches.size == 1 || tieBreakLineHint == null) return matches.first()
        return matches.minByOrNull { abs(cu.getLineNumber(it.startPosition) - tieBreakLineHint) }
    }

    private fun qualifiedName(method: MethodDeclaration): String {
        val cu = method.root as CompilationUnit
        val pkg = cu.`package`?.name?.fullyQualifiedName ?: ""
        val typeNames = mutableListOf<String>()
        var node: ASTNode? = method.parent
        while (node != null) {
            if (node is AbstractTypeDeclaration) typeNames.add(0, node.name.identifier)
            node = node.parent
        }
        val typed = typeNames.joinToString(".")
        return if (pkg.isEmpty()) typed else "$pkg.$typed"
    }

    private fun paramSignatures(method: MethodDeclaration): List<String> =
        method.parameters().filterIsInstance<SingleVariableDeclaration>().map { it.type.toString() }
}
