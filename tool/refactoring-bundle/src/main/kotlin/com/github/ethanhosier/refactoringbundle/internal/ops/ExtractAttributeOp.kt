package com.github.ethanhosier.refactoringbundle.internal.ops

import com.github.ethanhosier.refactoringbundle.internal.RefactoringRunner
import org.eclipse.core.resources.IFile
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.internal.corext.refactoring.code.ExtractConstantRefactoring

/**
 * Extract the expression spanning ([startLine],[startColumn]) to
 * ([endLine],[endColumn]) (inclusive) in [relativeFilePath] into a new
 * `static final` field named [newName]. Replaces all duplicate
 * occurrences in the enclosing type. Mirrors `ExtractVariable` but
 * targets a class-level declaration instead of a local.
 *
 * [visibility] is one of `public`, `protected`, `private`, or the
 * empty string for package-private.
 */
internal object ExtractAttributeOp {

    fun run(
        javaProject: IJavaProject,
        relativeFilePath: String,
        startLine: Int,
        startColumn: Int,
        endLine: Int,
        endColumn: Int,
        newName: String,
        visibility: String,
    ): RefactoringRunner.Outcome {
        val icu = findCompilationUnit(javaProject, relativeFilePath)
            ?: return RefactoringRunner.Outcome.Failure("no compilation unit at $relativeFilePath")

        val source = String(icu.buffer.characters)
        val offsetStart = lineColumnOffset(source, startLine, startColumn)
        val offsetEnd = lineColumnOffset(source, endLine, endColumn + 1)
        val length = offsetEnd - offsetStart

        val refactoring = ExtractConstantRefactoring(icu, offsetStart, length).apply {
            setConstantName(newName)
            setReplaceAllOccurrences(true)
            setVisibility(visibility)
        }
        return RefactoringRunner.run(refactoring)
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
