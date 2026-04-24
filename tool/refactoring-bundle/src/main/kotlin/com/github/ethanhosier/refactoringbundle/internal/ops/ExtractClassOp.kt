package com.github.ethanhosier.refactoringbundle.internal.ops

import com.github.ethanhosier.refactoringbundle.internal.RefactoringRunner
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.IType
import org.eclipse.jdt.core.refactoring.descriptors.ExtractClassDescriptor
import org.eclipse.jdt.internal.corext.refactoring.structure.ExtractClassRefactoring

/**
 * Bundle a set of instance fields on [sourceTypeFqn] into a new class
 * [newClassName] (top-level, in the same package as the source unless
 * otherwise specified). The source type gains a delegating field named
 * [delegateFieldName] of the new type; all reads/writes of the moved
 * fields are rewritten to go through it.
 *
 * Only names that correspond to non-static, non-enum-constant fields
 * on the source type are accepted. The order in [fieldNames] determines
 * the order fields appear in the extracted class (JDT is sensitive to
 * this because of initialisation order).
 */
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

        // JDT's setFields expects one Field per *instance* field on the
        // source type, in declaration order. Fields the caller didn't
        // ask to move get createField=false; the ones they did get
        // createField=true. Deviating from declaration order is
        // supported but changes init order — we preserve it.
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
