package com.github.ethanhosier.refactoringbundle.internal.ops

import com.github.ethanhosier.refactoringbundle.internal.RefactoringRunner
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.IPackageFragment
import org.eclipse.jdt.core.IPackageFragmentRoot
import org.eclipse.jdt.core.refactoring.IJavaRefactorings
import org.eclipse.jdt.core.refactoring.descriptors.RenameJavaElementDescriptor
import org.eclipse.ltk.core.refactoring.RefactoringCore
import org.eclipse.ltk.core.refactoring.RefactoringStatus

/**
 * Rename package [oldPackage] → [newPackage] project-wide. Updates all
 * `package` / `import` statements, and moves source files to the new
 * directory on disk.
 */
internal object RenamePackageOp {

    fun run(
        javaProject: IJavaProject,
        oldPackage: String,
        newPackage: String,
    ): RefactoringRunner.Outcome {
        val fragment: IPackageFragment = findPackage(javaProject, oldPackage)
            ?: return RefactoringRunner.Outcome.Failure("package $oldPackage not found in any source root")

        val contribution = RefactoringCore.getRefactoringContribution(IJavaRefactorings.RENAME_PACKAGE)
            ?: return RefactoringRunner.Outcome.Failure("Rename Package refactoring contribution not available")
        val descriptor = contribution.createDescriptor() as RenameJavaElementDescriptor
        descriptor.setProject(javaProject.project.name)
        descriptor.setJavaElement(fragment)
        descriptor.setNewName(newPackage)
        descriptor.setUpdateReferences(true)

        val status = RefactoringStatus()
        val refactoring = descriptor.createRefactoring(status)
            ?: return RefactoringRunner.Outcome.Failure(
                "failed to construct rename refactoring: ${status.entries.joinToString("; ") { it.message }}",
            )
        return RefactoringRunner.run(refactoring)
    }

    private fun findPackage(javaProject: IJavaProject, name: String): IPackageFragment? {
        for (root in javaProject.packageFragmentRoots) {
            if (root.kind != IPackageFragmentRoot.K_SOURCE) continue
            val frag = root.getPackageFragment(name)
            if (frag.exists() && frag.hasChildren()) return frag
        }
        return null
    }
}
