package com.github.ethanhosier.refactoringbundle.internal.ops

import com.github.ethanhosier.refactoringbundle.internal.RefactoringRunner
import org.eclipse.core.resources.IFile
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.internal.corext.refactoring.structure.ChangeTypeRefactoring

/**
 * Change the declared type of the local variable, parameter, or field
 * whose name occurs at ([line], [column]) in [relativeFilePath] to
 * [newTypeFqn]. Works via JDT's "Generalise Declared Type" / "Use
 * Supertype Where Possible" refactoring machinery, which rewrites the
 * declaration and checks that all usages remain well-typed.
 */
internal object ChangeVariableTypeOp {

    fun run(
        javaProject: IJavaProject,
        relativeFilePath: String,
        line: Int,
        column: Int,
        newTypeFqn: String,
    ): RefactoringRunner.Outcome {
        val icu = findCompilationUnit(javaProject, relativeFilePath)
            ?: return RefactoringRunner.Outcome.Failure("no compilation unit at $relativeFilePath")

        val source = String(icu.buffer.characters)
        val offset = lineColumnOffset(source, line, column)
        // Walk forward until we're off the identifier so JDT's
        // NodeFinder resolves the SimpleName at the selection.
        var end = offset
        while (end < source.length && source[end].let { it.isLetterOrDigit() || it == '_' || it == '$' }) end++
        val length = (end - offset).coerceAtLeast(1)

        val refactoring = ChangeTypeRefactoring(icu, offset, length, newTypeFqn)
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
