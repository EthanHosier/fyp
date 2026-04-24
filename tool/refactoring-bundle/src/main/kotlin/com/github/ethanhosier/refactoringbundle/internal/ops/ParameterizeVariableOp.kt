package com.github.ethanhosier.refactoringbundle.internal.ops

import com.github.ethanhosier.refactoringbundle.internal.RefactoringRunner
import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IResource
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.internal.corext.refactoring.code.IntroduceParameterRefactoring
import org.eclipse.ltk.core.refactoring.Change
import org.eclipse.ltk.core.refactoring.CompositeChange
import java.nio.file.Path

/**
 * Promote an expression — a local or a literal occurrence inside a
 * method — into a new parameter of the enclosing method. All existing
 * call sites pass the original expression as the new argument. JDT's
 * IntroduceParameterRefactoring. The selection should span the
 * expression exactly (start..end inclusive).
 */
internal object ParameterizeVariableOp {

    fun run(
        javaProject: IJavaProject,
        relativeFilePath: String,
        startLine: Int,
        startColumn: Int,
        endLine: Int,
        endColumn: Int,
        newParameterName: String,
    ): RefactoringRunner.Outcome {
        val icu = findCompilationUnit(javaProject, relativeFilePath)
            ?: return RefactoringRunner.Outcome.Failure("no compilation unit at $relativeFilePath")

        val source = String(icu.buffer.characters)
        val offsetStart = lineColumnOffset(source, startLine, startColumn)
        val offsetEnd = lineColumnOffset(source, endLine, endColumn + 1)
        val length = offsetEnd - offsetStart

        // IntroduceParameterRefactoring's fParameter is populated by
        // checkInitialConditions, so setParameterName must be called
        // after that. RefactoringRunner.run would re-invoke
        // checkInitialConditions and (depending on JDT internals) may
        // clobber the custom name — so we inline the LTK pipeline
        // here and interleave the setName call ourselves.
        val refactoring = IntroduceParameterRefactoring(icu, offsetStart, length)
        val pm = NullProgressMonitor()
        val initial = refactoring.checkInitialConditions(pm)
        if (initial.hasFatalError()) {
            return RefactoringRunner.Outcome.Failure(
                "initial conditions failed: ${initial.entries.joinToString("; ") { it.message }}",
            )
        }
        refactoring.setParameterName(newParameterName)
        val final = refactoring.checkFinalConditions(pm)
        if (final.hasFatalError()) {
            return RefactoringRunner.Outcome.Failure(
                "final conditions failed: ${final.entries.joinToString("; ") { it.message }}",
            )
        }
        val change = refactoring.createChange(pm)
            ?: return RefactoringRunner.Outcome.Failure("refactoring produced no change")
        val affected = mutableListOf<Path>()
        collectAffected(change, affected)
        change.perform(pm)
        return RefactoringRunner.Outcome.Success(affected.distinct())
    }

    private fun collectAffected(change: Change, out: MutableList<Path>) {
        if (change is CompositeChange) {
            for (child in change.children) collectAffected(child, out)
            return
        }
        when (val modified = change.modifiedElement) {
            is IFile -> modified.location?.toOSString()?.let { out.add(Path.of(it)) }
            is ICompilationUnit -> {
                val res = modified.resource
                if (res is IResource) res.location?.toOSString()?.let { out.add(Path.of(it)) }
            }
        }
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
