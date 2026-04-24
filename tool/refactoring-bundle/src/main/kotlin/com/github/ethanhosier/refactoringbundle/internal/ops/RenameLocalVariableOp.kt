package com.github.ethanhosier.refactoringbundle.internal.ops

import com.github.ethanhosier.refactoringbundle.internal.RefactoringRunner
import org.eclipse.core.resources.IFile
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.ILocalVariable
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.core.refactoring.IJavaRefactorings
import org.eclipse.jdt.core.refactoring.descriptors.RenameJavaElementDescriptor
import org.eclipse.ltk.core.refactoring.RefactoringCore
import org.eclipse.ltk.core.refactoring.RefactoringStatus

/**
 * Rename the local variable (or method parameter) at
 * ([line], [column]) in [relativeFilePath] to [newName]. Both lines and
 * columns are 1-indexed.
 */
internal object RenameLocalVariableOp {

    fun run(
        javaProject: IJavaProject,
        relativeFilePath: String,
        line: Int,
        column: Int,
        newName: String,
    ): RefactoringRunner.Outcome {
        val icu = findCompilationUnit(javaProject, relativeFilePath)
            ?: return RefactoringRunner.Outcome.Failure("no compilation unit at $relativeFilePath")
        val source = String(icu.buffer.characters)
        val offset = lineColumnOffset(source, line, column)
        val selected = icu.codeSelect(offset, 0)
        val local = selected.firstOrNull() as? ILocalVariable
            ?: return RefactoringRunner.Outcome.Failure(
                "no local variable at $relativeFilePath:$line:$column (got ${selected.firstOrNull()?.javaClass?.simpleName})",
            )

        val contribution = RefactoringCore.getRefactoringContribution(IJavaRefactorings.RENAME_LOCAL_VARIABLE)
            ?: return RefactoringRunner.Outcome.Failure("Rename Local Variable refactoring contribution not available")
        val descriptor = contribution.createDescriptor() as RenameJavaElementDescriptor
        descriptor.setProject(javaProject.project.name)
        descriptor.setJavaElement(local)
        descriptor.setNewName(newName)
        descriptor.setUpdateReferences(true)

        val status = RefactoringStatus()
        val refactoring = descriptor.createRefactoring(status)
            ?: return RefactoringRunner.Outcome.Failure(
                "failed to construct rename refactoring: ${status.entries.joinToString("; ") { it.message }}",
            )
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
