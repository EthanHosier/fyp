package com.github.ethanhosier.refactoringbundle.internal.ops

import com.github.ethanhosier.refactoringbundle.internal.RefactoringRunner
import com.github.ethanhosier.refactoringbundle.internal.anchor.AnchorOps
import com.github.ethanhosier.refactoringbundle.internal.anchor.AnchorResolver
import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IResource
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.internal.corext.refactoring.code.IntroduceParameterRefactoring
import org.eclipse.ltk.core.refactoring.Change
import org.eclipse.ltk.core.refactoring.CompositeChange
import java.nio.file.Path

internal object ParameterizeVariableOp {

    fun run(
        javaProject: IJavaProject,
        relativeFilePath: String,
        declaringTypeFqn: String,
        hostMethodName: String,
        hostMethodParamTypes: Array<String>,
        selectionSubtreeHash: String,
        selectionNodeCount: Int,
        originalLineHint: Int,
        originalColumnHint: Int,
        newParameterName: String,
    ): RefactoringRunner.Outcome {
        val icu = AnchorOps.findCompilationUnit(javaProject, relativeFilePath)
            ?: return RefactoringRunner.Outcome.Failure("no compilation unit at $relativeFilePath")
        val cu = AnchorResolver.parse(icu)
        val host = AnchorResolver.findHostMethod(cu, declaringTypeFqn, hostMethodName, hostMethodParamTypes)
            ?: return RefactoringRunner.Outcome.Failure(
                "host method not found: $declaringTypeFqn#$hostMethodName(${hostMethodParamTypes.joinToString(",")})",
            )
        val selection = AnchorResolver.findSelection(
            host, selectionSubtreeHash, selectionNodeCount, AnchorOps.hintOrNull(originalLineHint),
        ) ?: return RefactoringRunner.Outcome.Failure("no AST subtree match for hash=$selectionSubtreeHash")

        val refactoring = IntroduceParameterRefactoring(icu, selection.startPosition, selection.length)
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
}
