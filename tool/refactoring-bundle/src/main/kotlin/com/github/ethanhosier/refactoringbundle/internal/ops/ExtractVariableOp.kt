package com.github.ethanhosier.refactoringbundle.internal.ops

import com.github.ethanhosier.refactoringbundle.internal.RefactoringRunner
import com.github.ethanhosier.refactoringbundle.internal.anchor.AnchorOps
import com.github.ethanhosier.refactoringbundle.internal.anchor.AnchorResolver
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.internal.corext.refactoring.code.ExtractTempRefactoring

internal object ExtractVariableOp {

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
        newName: String,
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

        val refactoring = ExtractTempRefactoring(icu, selection.startPosition, selection.length).apply {
            setTempName(newName)
            setReplaceAllOccurrences(true)
            setDeclareFinal(false)
        }
        return RefactoringRunner.run(refactoring)
    }
}
