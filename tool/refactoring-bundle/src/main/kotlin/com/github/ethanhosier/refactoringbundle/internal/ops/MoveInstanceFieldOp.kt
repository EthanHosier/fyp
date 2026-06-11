package com.github.ethanhosier.refactoringbundle.internal.ops

import com.github.ethanhosier.refactoringbundle.internal.RefactoringRunner
import org.eclipse.core.resources.IResource
import org.eclipse.jdt.core.IField
import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.IType
import org.eclipse.jdt.internal.corext.refactoring.reorg.JavaMoveProcessor
import org.eclipse.jdt.internal.corext.refactoring.reorg.NullReorgQueries
import org.eclipse.jdt.internal.corext.refactoring.reorg.ReorgDestinationFactory
import org.eclipse.jdt.internal.corext.refactoring.reorg.ReorgPolicyFactory
import org.eclipse.ltk.core.refactoring.participants.ProcessorBasedRefactoring

internal object MoveInstanceFieldOp {

    fun run(
        javaProject: IJavaProject,
        sourceTypeFqn: String,
        fieldName: String,
        destinationTypeFqn: String,
    ): RefactoringRunner.Outcome {
        val sourceType: IType = javaProject.findType(sourceTypeFqn)
            ?: return RefactoringRunner.Outcome.Failure("source type $sourceTypeFqn not found")
        val destType: IType = javaProject.findType(destinationTypeFqn)
            ?: return RefactoringRunner.Outcome.Failure("destination type $destinationTypeFqn not found")
        val field: IField = sourceType.getField(fieldName).takeIf { it.exists() }
            ?: return RefactoringRunner.Outcome.Failure("field $fieldName not found on $sourceTypeFqn")

        val policy = ReorgPolicyFactory.createMovePolicy(
            emptyArray<IResource>(),
            arrayOf<IJavaElement>(field),
        )
        if (!policy.canEnable()) {
            return RefactoringRunner.Outcome.Failure("move policy cannot enable for $sourceTypeFqn.$fieldName")
        }

        val processor = JavaMoveProcessor(policy)
        val destination = ReorgDestinationFactory.createDestination(destType)
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
