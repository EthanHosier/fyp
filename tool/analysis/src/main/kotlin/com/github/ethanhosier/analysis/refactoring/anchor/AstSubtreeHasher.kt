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
