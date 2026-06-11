package com.github.ethanhosier.refactoringbundle.internal.ops

import com.github.ethanhosier.refactoringbundle.internal.RefactoringRunner
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.IMember
import org.eclipse.jdt.core.IType
import org.eclipse.jdt.internal.corext.refactoring.structure.PullUpRefactoringProcessor
import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings
import org.eclipse.ltk.core.refactoring.participants.ProcessorBasedRefactoring

internal object PullUpOp {

    fun run(
        javaProject: IJavaProject,
        declaringTypeFqn: String,
        methodNames: Array<String>,
        fieldNames: Array<String>,
    ): RefactoringRunner.Outcome {
        val type: IType = javaProject.findType(declaringTypeFqn)
            ?: return RefactoringRunner.Outcome.Failure("type $declaringTypeFqn not found on classpath")

        val members = mutableListOf<IMember>()
        val pickedMethods = mutableListOf<org.eclipse.jdt.core.IMethod>()
        for (name in methodNames) {
            val candidates = type.methods.filter { it.elementName == name }
            val picked = candidates.singleOrNull()
                ?: return RefactoringRunner.Outcome.Failure(
                    "method $name on $declaringTypeFqn is ambiguous or missing " +
                        "(found ${candidates.size})",
                )
            members += picked
            pickedMethods += picked
        }
        for (name in fieldNames) {
            val field = type.getField(name).takeIf { it.exists() }
                ?: return RefactoringRunner.Outcome.Failure("field $name not found on $declaringTypeFqn")
            members += field
        }
        if (members.isEmpty()) return RefactoringRunner.Outcome.Failure("no members to pull up")

        val hierarchy = type.newSupertypeHierarchy(null)
        val supers = hierarchy.getAllSuperclasses(type)
        val destination: IType = supers.firstOrNull { it.exists() && !it.isBinary && it.isClass }
            ?: return RefactoringRunner.Outcome.Failure(
                "no source-visible superclass available for $declaringTypeFqn",
            )

        val settings = JavaPreferencesSettings.getCodeGenerationSettings(javaProject)
        val processor = PullUpRefactoringProcessor(members.toTypedArray(), settings)
        processor.destinationType = destination
        // Delete the pulled-up methods from the subclass; without this
        // the method is copied to the superclass but also left behind.
        processor.setDeletedMethods(pickedMethods.toTypedArray())
        processor.setAbstractMethods(emptyArray())

        return RefactoringRunner.run(ProcessorBasedRefactoring(processor))
    }
}
