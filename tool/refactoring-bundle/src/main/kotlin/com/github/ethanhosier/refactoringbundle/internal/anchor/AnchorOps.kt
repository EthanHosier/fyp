package com.github.ethanhosier.refactoringbundle.internal.anchor

import org.eclipse.core.resources.IFile
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.core.dom.ASTNode
import org.eclipse.jdt.core.dom.SingleVariableDeclaration
import org.eclipse.jdt.core.dom.VariableDeclarationFragment

internal object AnchorOps {

    fun findCompilationUnit(javaProject: IJavaProject, relativeFilePath: String): ICompilationUnit? {
        val file: IFile = javaProject.project.getFile(relativeFilePath).takeIf { it.exists() }
            ?: return null
        return JavaCore.createCompilationUnitFrom(file)
    }

    fun hintOrNull(value: Int): Int? = if (value > 0) value else null

    fun nameOffsetAndLength(node: ASTNode): Pair<Int, Int>? {
        val name = when (node) {
            is VariableDeclarationFragment -> node.name
            is SingleVariableDeclaration -> node.name
            else -> return null
        }
        return name.startPosition to name.length
    }
}
