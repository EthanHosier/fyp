package com.github.ethanhosier.refactoringbundle.internal.anchor

import org.eclipse.core.resources.IFile
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.core.dom.ASTNode
import org.eclipse.jdt.core.dom.SingleVariableDeclaration
import org.eclipse.jdt.core.dom.VariableDeclarationFragment

/**
 * Shared helpers for anchor-resolved Op implementations. The wire
 * convention encodes "absent" optional ints as `-1` because the OSGi
 * reflection boundary doesn't carry nullable boxed Integers cleanly.
 */
internal object AnchorOps {

    fun findCompilationUnit(javaProject: IJavaProject, relativeFilePath: String): ICompilationUnit? {
        val file: IFile = javaProject.project.getFile(relativeFilePath).takeIf { it.exists() }
            ?: return null
        return JavaCore.createCompilationUnitFrom(file)
    }

    fun hintOrNull(value: Int): Int? = if (value > 0) value else null

    /** Position of the declared identifier inside a JDT declaration
     *  node. Declaration-targeted refactorings (rename, inline, change
     *  type, promote-to-field) want the offset of the symbol's name,
     *  not the start of the whole declaration. */
    fun nameOffsetAndLength(node: ASTNode): Pair<Int, Int>? {
        val name = when (node) {
            is VariableDeclarationFragment -> node.name
            is SingleVariableDeclaration -> node.name
            else -> return null
        }
        return name.startPosition to name.length
    }
}
