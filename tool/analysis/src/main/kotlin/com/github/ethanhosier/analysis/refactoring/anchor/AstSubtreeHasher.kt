package com.github.ethanhosier.analysis.refactoring.anchor

import org.eclipse.jdt.core.dom.ASTNode
import org.eclipse.jdt.core.dom.ChildListPropertyDescriptor
import org.eclipse.jdt.core.dom.ChildPropertyDescriptor
import org.eclipse.jdt.core.dom.Comment
import org.eclipse.jdt.core.dom.MethodDeclaration
import org.eclipse.jdt.core.dom.SimplePropertyDescriptor
import org.eclipse.jdt.core.dom.SingleVariableDeclaration
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor
import java.security.MessageDigest

/**
 * Content-addressable hash of a JDT AST subtree.
 *
 * **Must stay byte-identical** to the bundle-side copy at
 * `refactoring-bundle/.../internal/anchor/AstSubtreeHasher.kt`. The
 * bundle is OSGi-isolated and reached via reflection over primitive-only
 * signatures, so the source can't be shared directly. A pinned test
 * fixture (Java snippet + expected SHA) is asserted on both sides; any
 * drift between the two copies fails CI.
 *
 * See the bundle-side file for canonicalisation rationale, including
 * the type-body method-list canonicalisation in
 * [canonicallyOrderBodyDecls].
 */
object AstSubtreeHasher {

    fun hashNode(node: ASTNode): String = sha256Hex(canonical(node))

    fun hashNodes(nodes: List<ASTNode>): String {
        val sb = StringBuilder()
        sb.append("[seq:").append(nodes.size).append(']')
        for (n in nodes) {
            sb.append('(')
            emit(n, sb)
            sb.append(')')
        }
        return sha256Hex(sb.toString())
    }

    private fun canonical(node: ASTNode): String = StringBuilder().also { emit(node, it) }.toString()

    private fun emit(node: ASTNode, sb: StringBuilder) {
        if (node is Comment) return
        sb.append(node.nodeType)
        for (prop in node.structuralPropertiesForType().filterIsInstance<StructuralPropertyDescriptor>()) {
            val value = node.getStructuralProperty(prop) ?: continue
            when (prop) {
                is SimplePropertyDescriptor -> {
                    sb.append('|').append(prop.id).append('=').append(value.toString())
                }
                is ChildPropertyDescriptor -> {
                    if (value is Comment) continue
                    sb.append('(')
                    emit(value as ASTNode, sb)
                    sb.append(')')
                }
                is ChildListPropertyDescriptor -> {
                    sb.append('[')
                    val list = value as List<*>
                    val ordered = if (prop.id == "bodyDeclarations") canonicallyOrderBodyDecls(list) else list
                    for (child in ordered) {
                        if (child is ASTNode && child !is Comment) {
                            sb.append('(')
                            emit(child, sb)
                            sb.append(')')
                        }
                    }
                    sb.append(']')
                }
            }
        }
    }

    /**
     * Canonicalise a `bodyDeclarations` list (on any
     * [org.eclipse.jdt.core.dom.AbstractTypeDeclaration] or
     * [org.eclipse.jdt.core.dom.AnonymousClassDeclaration]) by
     * sorting [MethodDeclaration] entries by `name(paramTypes)` while
     * keeping every non-method entry in source order.
     *
     * **Why methods get sorted.** Two Extract Method refactorings
     * applied in opposite orders produce the same set of methods in
     * different declaration positions. Java doesn't observe method
     * declaration order, so the validator (and any hash-keyed cache)
     * shouldn't either — otherwise commuting alt orderings get
     * spuriously classified `AST_DIVERGED`. See the bundle-side file
     * header for the full rationale.
     *
     * **Why nothing else gets sorted.** Most other "sibling lists"
     * in Java are order-sensitive in ways that *would* be observable
     * if collapsed:
     *   - Field declarations: initializers can read earlier fields.
     *   - `static {}` initializers: run in source order.
     *   - Inner type declarations: rare to refactor; left positional
     *     to be safe.
     *   - Statements inside a method `Block`: sequential by definition.
     *   - Switch cases: fall-through depends on case order.
     *   - Enum constants: ordinal value is the declaration index.
     * Sorting any of these would let two structurally different
     * programs hash equal. We instead allowlist the single case
     * (methods) that we know is safe AND that we know comes up in
     * practice from the refactorings we replay.
     */
    private fun canonicallyOrderBodyDecls(list: List<*>): List<*> {
        val methods = ArrayList<MethodDeclaration>()
        val rest = ArrayList<Any?>(list.size)
        for (child in list) {
            if (child is MethodDeclaration) methods.add(child) else rest.add(child)
        }
        if (methods.isEmpty()) return list
        methods.sortBy(::methodCanonicalKey)
        return rest + methods
    }

    private fun methodCanonicalKey(m: MethodDeclaration): String {
        val name = m.name.identifier
        val params = m.parameters()
            .filterIsInstance<SingleVariableDeclaration>()
            .joinToString(",") { it.type.toString() }
        return "$name($params)"
    }

    private fun sha256Hex(s: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
        val hex = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xff
            if (v < 16) hex.append('0')
            hex.append(v.toString(16))
        }
        return hex.toString()
    }
}
