package com.github.ethanhosier.refactoringbundle.internal.ops

import com.github.ethanhosier.refactoringbundle.internal.RefactoringRunner
import org.eclipse.core.resources.IResource
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.IPackageFragment
import org.eclipse.jdt.core.IPackageFragmentRoot
import org.eclipse.jdt.core.IType
import org.eclipse.jdt.internal.corext.refactoring.reorg.JavaMoveProcessor
import org.eclipse.jdt.internal.corext.refactoring.reorg.NullReorgQueries
import org.eclipse.jdt.internal.corext.refactoring.reorg.ReorgDestinationFactory
import org.eclipse.jdt.internal.corext.refactoring.reorg.ReorgPolicyFactory
import org.eclipse.ltk.core.refactoring.participants.ProcessorBasedRefactoring

internal object MoveClassOp {

    fun run(
        javaProject: IJavaProject,
        typeFqn: String,
        destinationPackage: String,
    ): RefactoringRunner.Outcome {
        val type: IType = javaProject.findType(typeFqn)
            ?: return RefactoringRunner.Outcome.Failure("type $typeFqn not found on classpath")
        val icu: ICompilationUnit = type.compilationUnit
            ?: return RefactoringRunner.Outcome.Failure("$typeFqn has no source (binary?)")

        val sourceRoot: IPackageFragmentRoot = javaProject.packageFragmentRoots
            .firstOrNull { it.kind == IPackageFragmentRoot.K_SOURCE }
            ?: return RefactoringRunner.Outcome.Failure("no source root on $typeFqn's project")

        val destPackage: IPackageFragment = sourceRoot.getPackageFragment(destinationPackage).let {
            if (it.exists()) it
            else sourceRoot.createPackageFragment(destinationPackage, false, null)
        }

        val policy = ReorgPolicyFactory.createMovePolicy(
            emptyArray<IResource>(),
            arrayOf<IJavaElement>(icu),
        )
        if (!policy.canEnable()) {
            return RefactoringRunner.Outcome.Failure("move policy cannot enable for $typeFqn")
        }

        val processor = JavaMoveProcessor(policy)
        val destination = ReorgDestinationFactory.createDestination(destPackage)
        val destStatus = processor.setDestination(destination)
        if (destStatus.hasFatalError()) {
            return RefactoringRunner.Outcome.Failure(
                "destination rejected: ${destStatus.entries.joinToString("; ") { it.message }}",
            )
        }
        processor.setUpdateReferences(true)
        processor.setReorgQueries(NullReorgQueries())

        return RefactoringRunner.run(ProcessorBasedRefactoring(processor))
    }
}
