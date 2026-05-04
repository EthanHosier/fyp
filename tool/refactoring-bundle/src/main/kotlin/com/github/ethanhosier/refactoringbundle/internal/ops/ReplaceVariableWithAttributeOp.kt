package com.github.ethanhosier.refactoringbundle.internal.ops

import com.github.ethanhosier.refactoringbundle.internal.RefactoringRunner
import com.github.ethanhosier.refactoringbundle.internal.anchor.AnchorOps
import com.github.ethanhosier.refactoringbundle.internal.anchor.AnchorResolver
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.dom.Modifier
import org.eclipse.jdt.internal.corext.refactoring.code.PromoteTempToFieldRefactoring

internal object ReplaceVariableWithAttributeOp {

    fun run(
        javaProject: IJavaProject,
        relativeFilePath: String,
        declaringTypeFqn: String,
        hostMethodName: String,
        hostMethodParamTypes: Array<String>,
        declarationSubtreeHash: String,
        originalLineHint: Int,
        originalColumnHint: Int,
        newFieldName: String,
        visibility: String,
    ): RefactoringRunner.Outcome {
        val icu = AnchorOps.findCompilationUnit(javaProject, relativeFilePath)
            ?: return RefactoringRunner.Outcome.Failure("no compilation unit at $relativeFilePath")
        val cu = AnchorResolver.parse(icu)
        val host = AnchorResolver.findHostMethod(cu, declaringTypeFqn, hostMethodName, hostMethodParamTypes)
            ?: return RefactoringRunner.Outcome.Failure(
                "host method not found: $declaringTypeFqn#$hostMethodName(${hostMethodParamTypes.joinToString(",")})",
            )
        val decl = AnchorResolver.findDeclaration(host, declarationSubtreeHash, AnchorOps.hintOrNull(originalLineHint))
            ?: return RefactoringRunner.Outcome.Failure("no declaration match for hash=$declarationSubtreeHash")
        val (offset, length) = AnchorOps.nameOffsetAndLength(decl)
            ?: return RefactoringRunner.Outcome.Failure("declaration node has no name: ${decl.javaClass.simpleName}")

        val refactoring = PromoteTempToFieldRefactoring(icu, offset, length).apply {
            setFieldName(newFieldName)
            setVisibility(modifierFlag(visibility))
            setInitializeIn(PromoteTempToFieldRefactoring.INITIALIZE_IN_METHOD)
        }
        return RefactoringRunner.run(refactoring)
    }

    private fun modifierFlag(visibility: String): Int = when (visibility) {
        "public" -> Modifier.PUBLIC
        "protected" -> Modifier.PROTECTED
        "private" -> Modifier.PRIVATE
        else -> Modifier.NONE
    }
}
