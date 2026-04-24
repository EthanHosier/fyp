package com.github.ethanhosier.refactoringbundle.internal.ops

import com.github.ethanhosier.refactoringbundle.internal.RefactoringRunner
import org.eclipse.core.resources.IFile
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.core.dom.Modifier
import org.eclipse.jdt.internal.corext.refactoring.code.PromoteTempToFieldRefactoring

/**
 * Promote a local variable at ([line], [column]) to a field on the
 * enclosing class, keeping [newFieldName] as its name.
 * [visibility] is one of "public", "protected", "private", or "" for
 * package-private.
 */
internal object ReplaceVariableWithAttributeOp {

    fun run(
        javaProject: IJavaProject,
        relativeFilePath: String,
        line: Int,
        column: Int,
        newFieldName: String,
        visibility: String,
    ): RefactoringRunner.Outcome {
        val icu = findCompilationUnit(javaProject, relativeFilePath)
            ?: return RefactoringRunner.Outcome.Failure("no compilation unit at $relativeFilePath")

        val source = String(icu.buffer.characters)
        val offset = lineColumnOffset(source, line, column)
        var end = offset
        while (end < source.length && source[end].let { it.isLetterOrDigit() || it == '_' || it == '$' }) end++
        val length = (end - offset).coerceAtLeast(1)

        val refactoring = PromoteTempToFieldRefactoring(icu, offset, length).apply {
            setFieldName(newFieldName)
            setVisibility(modifierFlag(visibility))
            setInitializeIn(PromoteTempToFieldRefactoring.INITIALIZE_IN_METHOD)
        }
        return RefactoringRunner.run(refactoring)
    }

    private fun modifierFlag(visibility: String): Int = when (visibility) {
        "public" -> Modifier.PUBLIC
        "protected" -> Modifier.PROTECTED
        "private" -> Modifier.PRIVATE
        else -> Modifier.NONE
    }

    private fun findCompilationUnit(javaProject: IJavaProject, relativeFilePath: String): ICompilationUnit? {
        val file: IFile = javaProject.project.getFile(relativeFilePath).takeIf { it.exists() }
            ?: return null
        return JavaCore.createCompilationUnitFrom(file)
    }

    private fun lineColumnOffset(source: String, line: Int, column: Int): Int {
        var idx = 0
        var seen = 1
        while (seen < line && idx < source.length) {
            if (source[idx] == '\n') seen++
            idx++
        }
        return idx + (column - 1).coerceAtLeast(0)
    }
}
