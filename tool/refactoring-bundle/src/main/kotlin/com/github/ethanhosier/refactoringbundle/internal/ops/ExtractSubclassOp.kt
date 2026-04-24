package com.github.ethanhosier.refactoringbundle.internal.ops

import com.github.ethanhosier.refactoringbundle.internal.RefactoringRunner
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.IPackageFragment
import org.eclipse.jdt.core.IType
import org.eclipse.jdt.core.IMember
import org.eclipse.jdt.core.IMethod
import org.eclipse.jdt.internal.corext.refactoring.structure.PushDownRefactoringProcessor
import org.eclipse.ltk.core.refactoring.participants.ProcessorBasedRefactoring

/**
 * Create a new subclass [newSubclassName] of [sourceTypeFqn] in the
 * same package, then push the listed members down into it. JDT has no
 * single processor for this; the two-step composition is what the
 * IntelliJ action produces for "Extract Subclass".
 *
 * The source type must not be final (push-down needs it subclassable).
 * Members named in [methodNames] / [fieldNames] must be unambiguous
 * on the source type.
 */
internal object ExtractSubclassOp {

    fun run(
        javaProject: IJavaProject,
        sourceTypeFqn: String,
        newSubclassName: String,
        methodNames: Array<String>,
        fieldNames: Array<String>,
    ): RefactoringRunner.Outcome {
        val sourceType: IType = javaProject.findType(sourceTypeFqn)
            ?: return RefactoringRunner.Outcome.Failure("source type $sourceTypeFqn not found")
        val pkg: IPackageFragment = sourceType.packageFragment
        val sourceSimpleName = sourceType.elementName

        // Step 1: create the subclass CU.
        val newCuName = "$newSubclassName.java"
        if (pkg.getCompilationUnit(newCuName).exists()) {
            return RefactoringRunner.Outcome.Failure("$newSubclassName already exists in ${pkg.elementName}")
        }
        val body = buildString {
            if (pkg.elementName.isNotEmpty()) {
                append("package ").append(pkg.elementName).append(";\n\n")
            }
            append("public class ").append(newSubclassName)
                .append(" extends ").append(sourceSimpleName).append(" {\n}\n")
        }
        pkg.createCompilationUnit(newCuName, body, false, NullProgressMonitor())

        // Step 2: push down selected members. PushDownRefactoringProcessor
        // distributes each member to every direct subclass of the
        // declaring type — with our freshly-created subclass being the
        // only one.
        val members = mutableListOf<IMember>()
        val pickedMethods = mutableListOf<IMethod>()
        for (name in methodNames) {
            val candidates = sourceType.methods.filter { it.elementName == name }
            val picked = candidates.singleOrNull()
                ?: return RefactoringRunner.Outcome.Failure(
                    "method $name on $sourceTypeFqn is ambiguous or missing (found ${candidates.size})",
                )
            members += picked
            pickedMethods += picked
        }
        for (name in fieldNames) {
            val field = sourceType.getField(name).takeIf { it.exists() }
                ?: return RefactoringRunner.Outcome.Failure("field $name not found on $sourceTypeFqn")
            members += field
        }
        if (members.isEmpty()) return RefactoringRunner.Outcome.Failure("no members to push down")

        val processor = PushDownRefactoringProcessor(members.toTypedArray())
        return RefactoringRunner.run(ProcessorBasedRefactoring(processor))
    }
}
