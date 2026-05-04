package com.github.ethanhosier.analysis.refactoring.anchor

import org.eclipse.jdt.core.dom.ASTNode
import org.eclipse.jdt.core.dom.ChildListPropertyDescriptor
import org.eclipse.jdt.core.dom.ChildPropertyDescriptor
import org.eclipse.jdt.core.dom.Comment
import org.eclipse.jdt.core.dom.SimplePropertyDescriptor
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
 * See the bundle-side file for canonicalisation rationale.
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
                    for (child in value as List<*>) {
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
