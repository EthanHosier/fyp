package com.github.ethanhosier.refactoringbundle.internal.ops

import com.github.ethanhosier.refactoringbundle.internal.RefactoringRunner
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.IMember
import org.eclipse.jdt.core.IMethod
import org.eclipse.jdt.core.IType
import org.eclipse.jdt.internal.corext.refactoring.structure.ExtractSupertypeProcessor
import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings
import org.eclipse.ltk.core.refactoring.participants.ProcessorBasedRefactoring

internal object ExtractSuperclassOp {

    fun run(
        javaProject: IJavaProject,
        sourceTypeFqn: String,
        newSupertypeName: String,
        methodNames: Array<String>,
        fieldNames: Array<String>,
    ): RefactoringRunner.Outcome {
        val type: IType = javaProject.findType(sourceTypeFqn)
            ?: return RefactoringRunner.Outcome.Failure("source type $sourceTypeFqn not found on classpath")

        val members = mutableListOf<IMember>()
        val pickedMethods = mutableListOf<IMethod>()
        for (name in methodNames) {
            val candidates = type.methods.filter { it.elementName == name }
            val picked = candidates.singleOrNull()
                ?: return RefactoringRunner.Outcome.Failure(
                    "method $name on $sourceTypeFqn is ambiguous or missing (found ${candidates.size})",
                )
            members += picked
            pickedMethods += picked
        }
        for (name in fieldNames) {
            val field = type.getField(name).takeIf { it.exists() }
                ?: return RefactoringRunner.Outcome.Failure("field $name not found on $sourceTypeFqn")
            members += field
        }
        if (members.isEmpty()) return RefactoringRunner.Outcome.Failure("no members to extract")

        val settings = JavaPreferencesSettings.getCodeGenerationSettings(javaProject)
        val processor = ExtractSupertypeProcessor(members.toTypedArray(), settings)
        processor.setTypeName(newSupertypeName)
        processor.setTypesToExtract(arrayOf(type))
        processor.setAbstractMethods(emptyArray())
        processor.setDeletedMethods(pickedMethods.toTypedArray())

        return RefactoringRunner.run(ProcessorBasedRefactoring(processor))
    }
}
