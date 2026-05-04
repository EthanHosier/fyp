package com.github.ethanhosier.refactoringbundle.internal.anchor

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
 * Walks structural properties in JDT's declared order, encoding node
 * type, simple-property values, and recursing into child / child-list
 * properties. Comments / Javadoc are skipped — they are AST nodes but
 * carry no semantic information for refactoring identity. Source
 * positions are *not* structural properties in JDT, so they don't
 * affect the hash; whitespace and line shifts above the region are
 * therefore invisible.
 *
 * **Must stay byte-identical** to the analysis-side copy at
 * `analysis/.../refactoring/anchor/AstSubtreeHasher.kt`. A shared test
 * fixture (a Java snippet + its expected SHA) is run on both sides;
 * any drift fails CI.
 */
internal object AstSubtreeHasher {

    fun hashNode(node: ASTNode): String = sha256Hex(canonical(node))

    /** Hash an ordered list of sibling AST nodes (e.g. a contiguous
     *  statement window inside a method body). */
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
