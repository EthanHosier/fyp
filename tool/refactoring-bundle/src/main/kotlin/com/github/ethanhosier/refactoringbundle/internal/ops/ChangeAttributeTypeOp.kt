package com.github.ethanhosier.refactoringbundle.internal.ops

import com.github.ethanhosier.refactoringbundle.internal.RefactoringRunner
import org.eclipse.jdt.core.IField
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.IType
import org.eclipse.jdt.internal.corext.refactoring.structure.ChangeTypeRefactoring

internal object ChangeAttributeTypeOp {

    fun run(
        javaProject: IJavaProject,
        declaringTypeFqn: String,
        fieldName: String,
        newTypeFqn: String,
    ): RefactoringRunner.Outcome {
        val type: IType = javaProject.findType(declaringTypeFqn)
            ?: return RefactoringRunner.Outcome.Failure("type $declaringTypeFqn not found")
        val field: IField = type.getField(fieldName).takeIf { it.exists() }
            ?: return RefactoringRunner.Outcome.Failure("field $fieldName not found on $declaringTypeFqn")

        val icu = type.compilationUnit
            ?: return RefactoringRunner.Outcome.Failure("$declaringTypeFqn has no source")
        val range = field.nameRange
            ?: return RefactoringRunner.Outcome.Failure("no source range for field $fieldName")

        val refactoring = ChangeTypeRefactoring(icu, range.offset, range.length, newTypeFqn)
        return RefactoringRunner.run(refactoring)
    }
}
