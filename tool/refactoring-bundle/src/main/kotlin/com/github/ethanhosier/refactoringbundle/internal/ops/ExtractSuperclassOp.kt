package com.github.ethanhosier.refactoringbundle.internal.ops

import com.github.ethanhosier.refactoringbundle.internal.RefactoringRunner
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.IMember
import org.eclipse.jdt.core.IMethod
import org.eclipse.jdt.core.IType
import org.eclipse.jdt.internal.corext.refactoring.structure.ExtractSupertypeProcessor
import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings
import org.eclipse.ltk.core.refactoring.participants.ProcessorBasedRefactoring

/**
 * Create a new superclass [newSupertypeName] above [sourceTypeFqn]
 * carrying the listed members. Existing direct superclass (usually
 * `Object`) becomes the grandparent. Method names must be unambiguous
 * on the source type.
 */
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
        // Note on method deletion: unlike PullUp, ExtractSupertype
        // copies methods up but leaves the source implementation in
        // place as an override. JDT does this because the layered
        // supertype isn't a real resource until after the refactoring
        // commits, so `setDeletedMethods` against primary IMethods
        // doesn't match the layered CU's AST nodes. The IDE wizard
        // papers over this with its own working-copy handling; we
        // don't, and trying to re-resolve against the layered CU
        // doesn't take either. Fields, which go through a different
        // matching path, DO get removed from the source.
        processor.setAbstractMethods(emptyArray())
        processor.setDeletedMethods(pickedMethods.toTypedArray())

        return RefactoringRunner.run(ProcessorBasedRefactoring(processor))
    }
}
