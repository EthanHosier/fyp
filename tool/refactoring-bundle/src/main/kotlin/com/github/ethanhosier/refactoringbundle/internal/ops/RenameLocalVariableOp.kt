package com.github.ethanhosier.refactoringbundle.internal.ops

import com.github.ethanhosier.refactoringbundle.internal.RefactoringRunner
import com.github.ethanhosier.refactoringbundle.internal.anchor.AnchorOps
import com.github.ethanhosier.refactoringbundle.internal.anchor.AnchorResolver
import org.eclipse.jdt.core.ILocalVariable
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.refactoring.IJavaRefactorings
import org.eclipse.jdt.core.refactoring.descriptors.RenameJavaElementDescriptor
import org.eclipse.ltk.core.refactoring.RefactoringCore
import org.eclipse.ltk.core.refactoring.RefactoringStatus

internal object RenameLocalVariableOp {

    fun run(
        javaProject: IJavaProject,
        relativeFilePath: String,
        declaringTypeFqn: String,
        hostMethodName: String,
        hostMethodParamTypes: Array<String>,
        declarationSubtreeHash: String,
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
        val decl = AnchorResolver.findDeclaration(host, declarationSubtreeHash, AnchorOps.hintOrNull(originalLineHint))
            ?: return RefactoringRunner.Outcome.Failure("no declaration match for hash=$declarationSubtreeHash")
        val (offset, _) = AnchorOps.nameOffsetAndLength(decl)
            ?: return RefactoringRunner.Outcome.Failure("declaration node has no name: ${decl.javaClass.simpleName}")

        val selected = icu.codeSelect(offset, 0)
        val local = selected.firstOrNull() as? ILocalVariable
            ?: return RefactoringRunner.Outcome.Failure(
                "no local variable at resolved offset (got ${selected.firstOrNull()?.javaClass?.simpleName})",
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
}
