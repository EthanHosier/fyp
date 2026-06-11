package com.github.ethanhosier.analysis.alternative.rework

import org.eclipse.jdt.core.dom.AST
import org.eclipse.jdt.core.dom.ASTNode
import org.eclipse.jdt.core.dom.ASTParser
import org.eclipse.jdt.core.dom.ASTVisitor
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration
import org.eclipse.jdt.core.dom.CompilationUnit
import org.eclipse.jdt.core.dom.FieldDeclaration
import org.eclipse.jdt.core.dom.Initializer
import org.eclipse.jdt.core.dom.MethodDeclaration
import org.eclipse.jdt.core.dom.Modifier
import org.eclipse.jdt.core.dom.SingleVariableDeclaration
import org.eclipse.jdt.core.dom.VariableDeclarationFragment

object EnclosingScopeResolver {

    fun resolve(fileContent: String, relativePath: String, lineNumber: Int): String {
        val cu = parse(fileContent) ?: return fileFallback(relativePath)
        if (cu.types().isEmpty()) return fileFallback(relativePath)

        val member = findSmallestEnclosingMember(cu, lineNumber) ?: return fileFallback(relativePath)
        return scopeIdFor(member)
    }

    private fun parse(source: String): CompilationUnit? {
        val parser = ASTParser.newParser(AST.JLS_Latest).apply {
            setKind(ASTParser.K_COMPILATION_UNIT)
            setSource(source.toCharArray())
            setResolveBindings(false)
        }
        return parser.createAST(null) as? CompilationUnit
    }

    private fun findSmallestEnclosingMember(cu: CompilationUnit, lineNumber: Int): ASTNode? {
        var best: ASTNode? = null
        cu.accept(object : ASTVisitor() {
            override fun visit(node: MethodDeclaration): Boolean { consider(node); return true }
            override fun visit(node: FieldDeclaration): Boolean { consider(node); return true }
            override fun visit(node: Initializer): Boolean { consider(node); return true }
            private fun consider(node: ASTNode) {
                val startLine = cu.getLineNumber(node.startPosition)
                val endLine = cu.getLineNumber(node.startPosition + node.length - 1)
                if (lineNumber in startLine..endLine) {
                    val cur = best
                    if (cur == null || node.length < cur.length) best = node
                }
            }
        })
        return best
    }

    private fun scopeIdFor(member: ASTNode): String = when (member) {
        is MethodDeclaration -> {
            val params = member.parameters()
                .filterIsInstance<SingleVariableDeclaration>()
                .joinToString(",") { it.type.toString() }
            "${qualifiedTypeName(member)}#${member.name.identifier}($params)"
        }
        is FieldDeclaration -> {
            val firstFrag = member.fragments()
                .filterIsInstance<VariableDeclarationFragment>()
                .firstOrNull()
            val name = firstFrag?.name?.identifier ?: "<unknown>"
            "${qualifiedTypeName(member)}::$name"
        }
        is Initializer -> {
            val isStatic = (member.modifiers and Modifier.STATIC) != 0
            "${qualifiedTypeName(member)}#${if (isStatic) "<clinit>" else "<init>"}"
        }
        else -> error("Unexpected member: ${member.javaClass.simpleName}")
    }

    private fun qualifiedTypeName(member: ASTNode): String {
        val cu = member.root as CompilationUnit
        val pkg = cu.`package`?.name?.fullyQualifiedName ?: ""
        val typeNames = mutableListOf<String>()
        var node: ASTNode? = member.parent
        while (node != null) {
            if (node is AbstractTypeDeclaration) typeNames.add(0, node.name.identifier)
            node = node.parent
        }
        val typed = typeNames.joinToString(".")
        return if (pkg.isEmpty()) typed else "$pkg.$typed"
    }

    private fun fileFallback(relativePath: String): String = "$relativePath#<file>"
}
