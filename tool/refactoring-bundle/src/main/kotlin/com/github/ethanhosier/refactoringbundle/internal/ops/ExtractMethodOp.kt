package com.github.ethanhosier.refactoringbundle.internal.ops

import com.github.ethanhosier.refactoringbundle.internal.RefactoringRunner
import org.eclipse.core.resources.IFile
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.core.dom.Modifier
import org.eclipse.jdt.internal.corext.refactoring.code.ExtractMethodRefactoring

/**
 * Extract the source range
 * `[startLine:startColumn .. endLine:endColumn]` (1-indexed, inclusive
 * on both ends) of the file at [relativeFilePath] into a new private
 * method [newMethodName].
 *
 * Column-precise selection lets callers pull a sub-expression out of a
 * larger statement (matching JDT's IDE behaviour) instead of being
 * forced to extract whole lines. Pass `startColumn = 1` and a column
 * past the line's last character to extract the whole line range.
 */
internal object ExtractMethodOp {

    fun run(
        javaProject: IJavaProject,
        relativeFilePath: String,
        startLine: Int,
        startColumn: Int,
        endLine: Int,
        endColumn: Int,
        newMethodName: String,
    ): RefactoringRunner.Outcome {
        val icu = findCompilationUnit(javaProject, relativeFilePath)
            ?: return RefactoringRunner.Outcome.Failure("no compilation unit at $relativeFilePath")

        val source = String(icu.buffer.characters)
        val selStart = lineColumnOffset(source, startLine, startColumn)
        // endColumn is inclusive of the last selected character — bump
        // by one so the resulting offset points one past the last
        // character, matching JDT's half-open `[start, start+length)`
        // convention.
        val selEnd = lineColumnOffset(source, endLine, endColumn + 1)
        val selLength = (selEnd - selStart).coerceAtLeast(0)

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

    private fun lineColumnOffset(source: String, line: Int, column: Int): Int {
        var idx = 0
        var seen = 1
        while (seen < line && idx < source.length) {
            if (source[idx] == '\n') seen++
            idx++
        }
        // Walk forward up to `column - 1` characters or until the next
        // newline / EOF — clamps "endColumn past end of line" cleanly.
        var col = 1
        while (col < column && idx < source.length && source[idx] != '\n') {
            idx++
            col++
        }
        return idx
    }
}
