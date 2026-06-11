package com.github.ethanhosier.refactoringbundle.internal.ops

import com.github.ethanhosier.refactoringbundle.internal.RefactoringRunner
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.IMember
import org.eclipse.jdt.core.IType
import org.eclipse.jdt.internal.corext.refactoring.structure.ExtractInterfaceProcessor
import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings
import org.eclipse.ltk.core.refactoring.participants.ProcessorBasedRefactoring

internal object ExtractInterfaceOp {

    fun run(
        javaProject: IJavaProject,
        sourceTypeFqn: String,
        newInterfaceName: String,
        methodNames: Array<String>,
    ): RefactoringRunner.Outcome {
        val type: IType = javaProject.findType(sourceTypeFqn)
            ?: return RefactoringRunner.Outcome.Failure("source type $sourceTypeFqn not found on classpath")

        val members = mutableListOf<IMember>()
        for (name in methodNames) {
            val candidates = type.methods.filter { it.elementName == name }
            val picked = candidates.singleOrNull()
                ?: return RefactoringRunner.Outcome.Failure(
                    "method $name on $sourceTypeFqn is ambiguous or missing (found ${candidates.size})",
                )
            members += picked
        }
        if (members.isEmpty()) return RefactoringRunner.Outcome.Failure("no methods to extract")

        val settings = JavaPreferencesSettings.getCodeGenerationSettings(javaProject)
        val processor = ExtractInterfaceProcessor(type, settings)
        processor.setTypeName(newInterfaceName)
        processor.setExtractedMembers(members.toTypedArray())
        processor.setPackageFragment(type.packageFragment)

        return RefactoringRunner.run(ProcessorBasedRefactoring(processor))
    }
}
