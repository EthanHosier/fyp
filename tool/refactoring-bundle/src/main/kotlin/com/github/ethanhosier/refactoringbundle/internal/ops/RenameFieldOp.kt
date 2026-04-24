package com.github.ethanhosier.refactoringbundle.internal.ops

import com.github.ethanhosier.refactoringbundle.internal.RefactoringRunner
import org.eclipse.jdt.core.IField
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.IType
import org.eclipse.jdt.core.refactoring.IJavaRefactorings
import org.eclipse.jdt.core.refactoring.descriptors.RenameJavaElementDescriptor
import org.eclipse.ltk.core.refactoring.RefactoringCore
import org.eclipse.ltk.core.refactoring.RefactoringStatus

/**
 * Rename field [oldName] on [declaringTypeFqn] to [newName]
 * project-wide, updating all readers and writers.
 */
internal object RenameFieldOp {

    fun run(
        javaProject: IJavaProject,
        declaringTypeFqn: String,
        oldName: String,
        newName: String,
    ): RefactoringRunner.Outcome {
        val type: IType = javaProject.findType(declaringTypeFqn)
            ?: return RefactoringRunner.Outcome.Failure("type $declaringTypeFqn not found on classpath")
        val field: IField = type.getField(oldName).takeIf { it.exists() }
            ?: return RefactoringRunner.Outcome.Failure("field $oldName not found on $declaringTypeFqn")

        val contribution = RefactoringCore.getRefactoringContribution(IJavaRefactorings.RENAME_FIELD)
            ?: return RefactoringRunner.Outcome.Failure("Rename Field refactoring contribution not available")
        val descriptor = contribution.createDescriptor() as RenameJavaElementDescriptor
        descriptor.setProject(javaProject.project.name)
        descriptor.setJavaElement(field)
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
