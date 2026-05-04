package com.github.ethanhosier.analysis.refactoring.anchor

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
import java.nio.file.Files
import java.nio.file.Path

/**
 * Resolves the anchor payload for an anchor-addressed
 * [com.github.ethanhosier.analysis.miner.model.RefactoringSpec].
 *
 * Two flavours:
 *  - [rangeAnchor] for selections (extract method/var/attr, parameterize):
 *    finds either a contiguous sibling-statement window inside any
 *    [Block] under the host method, or a single AST node whose range
 *    matches the selection. Hash + node count form a content-addressed
 *    pointer the apply path can re-find regardless of line shifts in
 *    other parts of the same file (or even the same method, as long as
 *    the selection's own subtree wasn't rewritten).
 *  - [pointAnchor] for declaration-targeted ops (rename local/param,
 *    inline var, change type, replace with attribute): finds the
 *    declaration node ([VariableDeclarationFragment] or
 *    [SingleVariableDeclaration]) covering the (line, col) point and
 *    hashes it.
 *
 * Returns `null` when no enclosing method exists or when no AST node
 * matches — the RM mapper falls back to
 * [com.github.ethanhosier.analysis.miner.model.RefactoringSpec.Other].
 */
class SpecAnchorBuilder(private val worktree: Path) {

    private val cuCache = mutableMapOf<String, Pair<CompilationUnit, Int>?>()

    data class RangeAnchor(
        val declaringTypeFqn: String,
        val hostMethodName: String,
        val hostMethodParamTypes: List<String>,
        val selectionSubtreeHash: String,
        val selectionNodeCount: Int,
    )

    data class PointAnchor(
        val declaringTypeFqn: String,
        val hostMethodName: String,
        val hostMethodParamTypes: List<String>,
        val declarationSubtreeHash: String,
    )

    fun rangeAnchor(
        relativeFilePath: String,
        startLine: Int, startColumn: Int,
        endLine: Int, endColumn: Int,
    ): RangeAnchor? {
        val parsed = parseFile(relativeFilePath) ?: return null
        val (cu, sourceLength) = parsed
        val startOffset = positionOf(cu, startLine, startColumn)
        val endOffset = positionOfEnd(cu, sourceLength, endLine, endColumn)
        val host = findEnclosingMethod(cu, startOffset) ?: return null

        // Prefer a sibling-statement window if the selection covers
        // whole statements inside any Block in the host. Falls back to
        // the smallest single AST node containing the selection.
        val window = findSiblingWindow(host, startOffset, endOffset)
        val nodes: List<ASTNode> = window
            ?: listOfNotNull(findSmallestContainingNode(host, startOffset, endOffset))
        if (nodes.isEmpty()) return null
        return RangeAnchor(
            declaringTypeFqn = qualifiedName(host),
            hostMethodName = host.name.identifier,
            hostMethodParamTypes = paramTypes(host),
            selectionSubtreeHash = AstSubtreeHasher.hashNodes(nodes),
            selectionNodeCount = nodes.size,
        )
    }

    fun pointAnchor(
        relativeFilePath: String,
        line: Int, column: Int,
    ): PointAnchor? {
        val parsed = parseFile(relativeFilePath) ?: return null
        val (cu, _) = parsed
        val offset = positionOf(cu, line, column)
        val host = findEnclosingMethod(cu, offset) ?: return null
        val decl = findEnclosingDeclaration(host, offset) ?: return null
        return PointAnchor(
            declaringTypeFqn = qualifiedName(host),
            hostMethodName = host.name.identifier,
            hostMethodParamTypes = paramTypes(host),
            declarationSubtreeHash = AstSubtreeHasher.hashNode(decl),
        )
    }

    private fun parseFile(relativeFilePath: String): Pair<CompilationUnit, Int>? = cuCache.getOrPut(relativeFilePath) {
        val path = worktree.resolve(relativeFilePath)
        if (!Files.exists(path)) return@getOrPut null
        val source = Files.readString(path)
        val parser = ASTParser.newParser(AST.JLS_Latest).apply {
            setKind(ASTParser.K_COMPILATION_UNIT)
            setSource(source.toCharArray())
            setResolveBindings(false)
        }
        val cu = parser.createAST(null) as? CompilationUnit ?: return@getOrPut null
        cu to source.length
    }

    private fun positionOf(cu: CompilationUnit, line: Int, column: Int): Int =
        cu.getPosition(line, (column - 1).coerceAtLeast(0)).coerceAtLeast(0)

    /**
     * Inclusive endColumn → exclusive end offset. Handles the
     * `Int.MAX_VALUE` "to end of line" idiom by clamping to the start
     * of the next line (or to source length).
     */
    private fun positionOfEnd(cu: CompilationUnit, sourceLength: Int, line: Int, column: Int): Int {
        val nextLineStart = cu.getPosition(line + 1, 0).let { if (it >= 0) it else sourceLength }
        if (column >= 1_000_000) return nextLineStart
        val abs = cu.getPosition(line, column).coerceAtLeast(0)
        return if (abs in 0..nextLineStart) abs else nextLineStart
    }

    private fun findEnclosingMethod(cu: CompilationUnit, offset: Int): MethodDeclaration? {
        var best: MethodDeclaration? = null
        cu.accept(object : ASTVisitor() {
            override fun visit(node: MethodDeclaration): Boolean {
                val start = node.startPosition
                val end = start + node.length
                if (offset in start..end) {
                    if (best == null || node.length < best!!.length) best = node
                }
                return true
            }
        })
        return best
    }

    /**
     * Look in every [Block] under [host] for a contiguous sibling
     * statement window whose first statement's start ≥ [startOffset]
     * and whose last statement's end ≤ [endOffset]. Picks the deepest
     * (most-nested) such block — that's the JDT semantics for
     * extract-method on a selection inside an `if`/`for`/etc. body.
     */
    private fun findSiblingWindow(host: MethodDeclaration, startOffset: Int, endOffset: Int): List<Statement>? {
        var best: List<Statement>? = null
        var bestDepth = -1
        host.accept(object : ASTVisitor() {
            override fun visit(node: Block): Boolean {
                @Suppress("UNCHECKED_CAST")
                val stmts = node.statements() as List<Statement>
                val first = stmts.indexOfFirst { it.startPosition >= startOffset }
                val last = stmts.indexOfLast { (it.startPosition + it.length) <= endOffset }
                if (first in 0..last) {
                    val window = stmts.subList(first, last + 1)
                    val depth = depthOf(node)
                    if (depth > bestDepth) {
                        best = window
                        bestDepth = depth
                    }
                }
                return true
            }
        })
        return best
    }

    /**
     * Walk all nodes under [host] and find the smallest one whose
     * [start, end) range fully contains the selection. This catches
     * sub-expression selections (extract-variable on an InfixExpression)
     * where no sibling-window matches.
     */
    private fun findSmallestContainingNode(host: MethodDeclaration, startOffset: Int, endOffset: Int): ASTNode? {
        var best: ASTNode? = null
        host.accept(object : ASTVisitor() {
            override fun preVisit2(node: ASTNode): Boolean {
                if (node is Comment) return false
                val s = node.startPosition
                val e = s + node.length
                if (s <= startOffset && e >= endOffset) {
                    if (best == null || node.length < best!!.length) best = node
                    return true
                }
                return false
            }
        })
        return best
    }

    private fun findEnclosingDeclaration(host: MethodDeclaration, offset: Int): ASTNode? {
        var best: ASTNode? = null
        host.accept(object : ASTVisitor() {
            override fun visit(node: VariableDeclarationFragment): Boolean = consider(node)
            override fun visit(node: SingleVariableDeclaration): Boolean = consider(node)
            private fun consider(node: ASTNode): Boolean {
                val s = node.startPosition
                val e = s + node.length
                if (offset in s..e) {
                    if (best == null || node.length < best!!.length) best = node
                }
                return true
            }
        })
        return best
    }

    private fun depthOf(node: ASTNode): Int {
        var d = 0
        var n: ASTNode? = node.parent
        while (n != null) { d++; n = n.parent }
        return d
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

    private fun paramTypes(method: MethodDeclaration): List<String> =
        method.parameters().filterIsInstance<SingleVariableDeclaration>().map { it.type.toString() }
}
