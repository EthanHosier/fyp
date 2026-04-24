package com.github.ethanhosier.refactoringbundle.internal.ops

import com.github.ethanhosier.refactoringbundle.internal.RefactoringRunner
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.IType
import org.eclipse.jdt.core.refactoring.IJavaRefactorings
import org.eclipse.jdt.core.refactoring.descriptors.RenameJavaElementDescriptor
import org.eclipse.ltk.core.refactoring.RefactoringCore
import org.eclipse.ltk.core.refactoring.RefactoringStatus

/**
 * Rename the top-level type [typeFqn] to [newName] project-wide. The
 * underlying source file is also renamed on disk
 * (`Foo.java` → `<newName>.java`).
 */
internal object RenameClassOp {

    fun run(
        javaProject: IJavaProject,
        typeFqn: String,
        newName: String,
    ): RefactoringRunner.Outcome {
        val type: IType = javaProject.findType(typeFqn)
            ?: return RefactoringRunner.Outcome.Failure("type $typeFqn not found on classpath")

        val contribution = RefactoringCore.getRefactoringContribution(IJavaRefactorings.RENAME_TYPE)
            ?: return RefactoringRunner.Outcome.Failure("Rename Type refactoring contribution not available")
        val descriptor = contribution.createDescriptor() as RenameJavaElementDescriptor
        descriptor.setProject(javaProject.project.name)
        descriptor.setJavaElement(type)
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
