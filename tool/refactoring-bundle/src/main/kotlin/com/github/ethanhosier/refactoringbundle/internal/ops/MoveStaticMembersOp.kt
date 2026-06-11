package com.github.ethanhosier.refactoringbundle.internal.ops

import com.github.ethanhosier.refactoringbundle.internal.RefactoringRunner
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.IMember
import org.eclipse.jdt.core.IType
import org.eclipse.jdt.internal.corext.refactoring.structure.MoveStaticMembersProcessor
import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings
import org.eclipse.ltk.core.refactoring.participants.ProcessorBasedRefactoring

internal object MoveStaticMembersOp {

    fun run(
        javaProject: IJavaProject,
        sourceTypeFqn: String,
        destinationTypeFqn: String,
        methodNames: Array<String>,
        fieldNames: Array<String>,
    ): RefactoringRunner.Outcome {
        val source: IType = javaProject.findType(sourceTypeFqn)
            ?: return RefactoringRunner.Outcome.Failure("source type $sourceTypeFqn not found on classpath")

        val members = mutableListOf<IMember>()
        for (name in methodNames) {
            val candidates = source.methods.filter { it.elementName == name }
            val picked = candidates.singleOrNull()
                ?: return RefactoringRunner.Outcome.Failure(
                    "method $name on $sourceTypeFqn is ambiguous or missing (found ${candidates.size})",
                )
            members += picked
        }
        for (name in fieldNames) {
            val field = source.getField(name).takeIf { it.exists() }
                ?: return RefactoringRunner.Outcome.Failure("field $name not found on $sourceTypeFqn")
            members += field
        }
        if (members.isEmpty()) return RefactoringRunner.Outcome.Failure("no members to move")

        val settings = JavaPreferencesSettings.getCodeGenerationSettings(javaProject)
        val processor = MoveStaticMembersProcessor(members.toTypedArray(), settings)
        processor.setDestinationTypeFullyQualifiedName(destinationTypeFqn)
        processor.setDelegateUpdating(false)

        return RefactoringRunner.run(ProcessorBasedRefactoring(processor))
    }
}
