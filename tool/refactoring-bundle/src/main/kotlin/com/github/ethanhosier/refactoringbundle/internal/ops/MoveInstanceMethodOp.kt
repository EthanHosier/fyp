package com.github.ethanhosier.refactoringbundle.internal.ops

import com.github.ethanhosier.refactoringbundle.internal.RefactoringRunner
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.IMethod
import org.eclipse.jdt.core.IType
import org.eclipse.jdt.internal.corext.refactoring.structure.MoveInstanceMethodProcessor
import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings
import org.eclipse.ltk.core.refactoring.participants.ProcessorBasedRefactoring

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
