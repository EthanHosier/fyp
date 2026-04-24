package com.github.ethanhosier.refactoringbundle.internal.ops

import com.github.ethanhosier.refactoringbundle.internal.RefactoringRunner
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.IMethod
import org.eclipse.jdt.core.IType
import org.eclipse.jdt.internal.corext.refactoring.structure.MoveInstanceMethodProcessor
import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings
import org.eclipse.ltk.core.refactoring.participants.ProcessorBasedRefactoring

/**
 * Move an instance method [methodName] on [sourceTypeFqn] to one of
 * its parameters or instance fields named [targetName]. Target's
 * declared type becomes the new home of the method. Method name must
 * be unambiguous on [sourceTypeFqn].
 */
internal object MoveInstanceMethodOp {

    fun run(
        javaProject: IJavaProject,
        sourceTypeFqn: String,
        methodName: String,
        targetName: String,
    ): RefactoringRunner.Outcome {
        val type: IType = javaProject.findType(sourceTypeFqn)
            ?: return RefactoringRunner.Outcome.Failure("source type $sourceTypeFqn not found on classpath")

        val candidates = type.methods.filter { it.elementName == methodName }
        val method: IMethod = candidates.singleOrNull()
            ?: return RefactoringRunner.Outcome.Failure(
                "method $methodName on $sourceTypeFqn is ambiguous or missing (found ${candidates.size})",
            )

        val settings = JavaPreferencesSettings.getCodeGenerationSettings(javaProject)
        val processor = MoveInstanceMethodProcessor(method, settings)
        val refactoring = ProcessorBasedRefactoring(processor)

        // checkInitialConditions is what populates possibleTargets — call
        // it now so we can pick the caller-supplied target before
        // [RefactoringRunner.run] drives the rest of the LTK pipeline.
        val initial = refactoring.checkInitialConditions(NullProgressMonitor())
        if (initial.hasFatalError()) {
            return RefactoringRunner.Outcome.Failure(
                "initial conditions failed: ${initial.entries.joinToString("; ") { it.message }}",
            )
        }

        val possibleTargets = processor.possibleTargets
        val target = possibleTargets.firstOrNull { it.name == targetName }
            ?: return RefactoringRunner.Outcome.Failure(
                "no parameter or field named $targetName on $methodName; " +
                    "available: ${possibleTargets.joinToString(",") { it.name }}",
            )
        processor.setTarget(target)
        processor.setInlineDelegator(true)
        processor.setRemoveDelegator(true)

        return RefactoringRunner.run(refactoring)
    }
}
