package com.github.ethanhosier.refactoringbundle.internal.ops

import com.github.ethanhosier.refactoringbundle.internal.RefactoringRunner
import com.github.ethanhosier.refactoringbundle.internal.anchor.AnchorOps
import com.github.ethanhosier.refactoringbundle.internal.anchor.AnchorResolver
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.dom.AST
import org.eclipse.jdt.core.dom.ASTParser
import org.eclipse.jdt.core.dom.CompilationUnit
import org.eclipse.jdt.internal.corext.refactoring.code.InlineTempRefactoring

internal object InlineVariableOp {

    fun run(
        javaProject: IJavaProject,
        relativeFilePath: String,
        declaringTypeFqn: String,
        hostMethodName: String,
        hostMethodParamTypes: Array<String>,
        declarationSubtreeHash: String,
        originalLineHint: Int,
        originalColumnHint: Int,
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
        val (offset, _) = AnchorOps.nameOffsetAndLength(decl)
            ?: return RefactoringRunner.Outcome.Failure("declaration node has no name: ${decl.javaClass.simpleName}")

        val resolvedParser = ASTParser.newParser(AST.JLS_Latest).apply {
            setKind(ASTParser.K_COMPILATION_UNIT)
            setSource(icu)
            setResolveBindings(true)
        }
        val resolved = resolvedParser.createAST(null) as CompilationUnit
        val refactoring = InlineTempRefactoring(icu, resolved, offset, 0)
        return RefactoringRunner.run(refactoring)
    }
}
