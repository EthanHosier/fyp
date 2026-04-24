package com.github.ethanhosier.refactoringbundle.internal.ops

import com.github.ethanhosier.refactoringbundle.internal.RefactoringRunner
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.IMethod
import org.eclipse.jdt.core.IType
import org.eclipse.jdt.core.dom.AST
import org.eclipse.jdt.core.dom.ASTParser
import org.eclipse.jdt.core.dom.CompilationUnit
import org.eclipse.jdt.internal.corext.refactoring.code.InlineMethodRefactoring

/**
 * Inline every call to [methodName] on [declaringTypeFqn] by replacing
 * each call site with the method body, then deleting the declaration.
 * Disambiguate overloads via JDT-encoded [paramTypeSignatures].
 */
internal object InlineMethodOp {

    fun run(
        javaProject: IJavaProject,
        declaringTypeFqn: String,
        methodName: String,
        paramTypeSignatures: Array<String>?,
    ): RefactoringRunner.Outcome {
        val type: IType = javaProject.findType(declaringTypeFqn)
            ?: return RefactoringRunner.Outcome.Failure("type $declaringTypeFqn not found on classpath")
        val method: IMethod = pickMethod(type, methodName, paramTypeSignatures)
            ?: return RefactoringRunner.Outcome.Failure(
                "method $methodName not found on $declaringTypeFqn" +
                    (paramTypeSignatures?.joinToString(",")?.let { " ($it)" } ?: ""),
            )

        val icu = type.compilationUnit
            ?: return RefactoringRunner.Outcome.Failure("declaring type has no source (binary?)")
        val range = method.nameRange
            ?: return RefactoringRunner.Outcome.Failure("method $methodName has no name range")

        val parser = ASTParser.newParser(AST.JLS_Latest).apply {
            setKind(ASTParser.K_COMPILATION_UNIT)
            setSource(icu)
            setResolveBindings(true)
        }
        val astRoot = parser.createAST(null) as CompilationUnit

        val refactoring = InlineMethodRefactoring.create(icu, astRoot, range.offset, range.length)
            ?: return RefactoringRunner.Outcome.Failure("could not construct InlineMethodRefactoring at declaration")
        return RefactoringRunner.run(refactoring)
    }

    private fun pickMethod(type: IType, name: String, paramTypeSignatures: Array<String>?): IMethod? {
        if (paramTypeSignatures != null) {
            return type.getMethod(name, paramTypeSignatures).takeIf { it.exists() }
        }
        val candidates = type.methods.filter { it.elementName == name }
        return candidates.singleOrNull()
    }
}
