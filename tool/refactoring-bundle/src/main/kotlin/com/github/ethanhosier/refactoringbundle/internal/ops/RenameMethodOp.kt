package com.github.ethanhosier.refactoringbundle.internal.ops

import com.github.ethanhosier.refactoringbundle.internal.RefactoringRunner
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.IMethod
import org.eclipse.jdt.core.IType
import org.eclipse.jdt.core.refactoring.IJavaRefactorings
import org.eclipse.jdt.core.refactoring.descriptors.RenameJavaElementDescriptor
import org.eclipse.ltk.core.refactoring.RefactoringCore
import org.eclipse.ltk.core.refactoring.RefactoringStatus

/**
 * Rename a method declared on [declaringTypeFqn] from [oldName] to
 * [newName] across the entire project. Optional [paramTypeSignatures]
 * disambiguates overloads (JDT-encoded: `Ljava/lang/String;`, `I`, `V`).
 */
internal object RenameMethodOp {

    fun run(
        javaProject: IJavaProject,
        declaringTypeFqn: String,
        oldName: String,
        newName: String,
        paramTypeSignatures: Array<String>?,
    ): RefactoringRunner.Outcome {
        val type: IType = javaProject.findType(declaringTypeFqn)
            ?: return RefactoringRunner.Outcome.Failure("type $declaringTypeFqn not found on classpath")
        val method: IMethod = pickMethod(type, oldName, paramTypeSignatures)
            ?: return RefactoringRunner.Outcome.Failure(
                "method $oldName not found on $declaringTypeFqn" +
                    (paramTypeSignatures?.joinToString(",")?.let { " ($it)" } ?: ""),
            )

        val contribution = RefactoringCore.getRefactoringContribution(IJavaRefactorings.RENAME_METHOD)
            ?: return RefactoringRunner.Outcome.Failure("Rename Method refactoring contribution not available")
        val descriptor = contribution.createDescriptor() as RenameJavaElementDescriptor
        descriptor.setProject(javaProject.project.name)
        descriptor.setJavaElement(method)
        descriptor.setNewName(newName)
        descriptor.setUpdateReferences(true)

        val status = RefactoringStatus()
        val refactoring = descriptor.createRefactoring(status)
            ?: return RefactoringRunner.Outcome.Failure(
                "failed to construct rename refactoring: ${status.entries.joinToString("; ") { it.message }}",
            )
        return RefactoringRunner.run(refactoring)
    }

    private fun pickMethod(type: IType, name: String, paramTypeSignatures: Array<String>?): IMethod? {
        if (paramTypeSignatures != null) {
            return type.getMethod(name, paramTypeSignatures).takeIf { it.exists() }
        }
        // Unambiguous by name? Return it. Otherwise refuse so the
        // caller disambiguates explicitly rather than us picking a
        // surprising overload for them.
        val candidates = type.methods.filter { it.elementName == name }
        return candidates.singleOrNull()
    }
}
