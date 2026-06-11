package com.github.ethanhosier.refactoringbundle.internal.ops

import com.github.ethanhosier.refactoringbundle.internal.RefactoringRunner
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.IType
import org.eclipse.jdt.core.refactoring.descriptors.ExtractClassDescriptor
import org.eclipse.jdt.internal.corext.refactoring.structure.ExtractClassRefactoring

internal object ExtractClassOp {

    fun run(
        javaProject: IJavaProject,
        sourceTypeFqn: String,
        newClassName: String,
        delegateFieldName: String,
        fieldNames: Array<String>,
        createGetterSetter: Boolean,
    ): RefactoringRunner.Outcome {
        val type: IType = javaProject.findType(sourceTypeFqn)
            ?: return RefactoringRunner.Outcome.Failure("source type $sourceTypeFqn not found on classpath")

        if (fieldNames.isEmpty()) return RefactoringRunner.Outcome.Failure("no fields to extract")

        val allFields = ExtractClassDescriptor.getFields(type)
        val requested = fieldNames.toSet()
        val unknown = requested - allFields.map { it.fieldName }.toSet()
        if (unknown.isNotEmpty()) {
            return RefactoringRunner.Outcome.Failure(
                "fields not found on $sourceTypeFqn: ${unknown.joinToString(", ")}",
            )
        }
        for (f in allFields) f.isCreateField = f.fieldName in requested

        val descriptor = ExtractClassDescriptor()
        descriptor.setType(type)
        descriptor.setClassName(newClassName)
        descriptor.setFieldName(delegateFieldName)
        descriptor.setCreateTopLevel(true)
        descriptor.setCreateGetterSetter(createGetterSetter)
        descriptor.setFields(allFields)

        val refactoring = ExtractClassRefactoring(descriptor)
        return RefactoringRunner.run(refactoring)
    }
}
