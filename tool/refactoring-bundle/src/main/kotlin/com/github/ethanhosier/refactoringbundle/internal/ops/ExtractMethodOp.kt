package com.github.ethanhosier.refactoringbundle.internal.ops

import com.github.ethanhosier.refactoringbundle.internal.RefactoringRunner
import org.eclipse.core.resources.IFile
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.core.dom.Modifier
import org.eclipse.jdt.internal.corext.refactoring.code.ExtractMethodRefactoring

/**
 * Extract lines `[startLine, endLine]` of the file at
 * [relativeFilePath] into a new private method [newMethodName].
 * Lines are 1-indexed and inclusive.
 */
internal object ExtractMethodOp {

    fun run(
        javaProject: IJavaProject,
        relativeFilePath: String,
        startLine: Int,
        endLine: Int,
        newMethodName: String,
    ): RefactoringRunner.Outcome {
        val icu = findCompilationUnit(javaProject, relativeFilePath)
            ?: return RefactoringRunner.Outcome.Failure("no compilation unit at $relativeFilePath")

        val source = String(icu.buffer.characters)
        val selStart = lineOffset(source, startLine)
        val selEnd = lineOffset(source, endLine + 1)
        val selLength = selEnd - selStart

        val refactoring = ExtractMethodRefactoring(icu, selStart, selLength).apply {
            methodName = newMethodName
            visibility = Modifier.PRIVATE
        }
        return RefactoringRunner.run(refactoring)
    }

    private fun findCompilationUnit(javaProject: IJavaProject, relativeFilePath: String): ICompilationUnit? {
        val file: IFile = javaProject.project.getFile(relativeFilePath).takeIf { it.exists() }
            ?: return null
        return JavaCore.createCompilationUnitFrom(file)
    }

    private fun lineOffset(source: String, line: Int): Int {
        if (line <= 1) return 0
        var idx = 0
        var seen = 1
        while (seen < line && idx < source.length) {
            if (source[idx] == '\n') seen++
            idx++
        }
        return idx
    }
}
