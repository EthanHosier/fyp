package com.github.ethanhosier.refactoringbundle.internal.ops

import com.github.ethanhosier.refactoringbundle.internal.RefactoringRunner
import org.eclipse.core.resources.IFile
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.core.dom.AST
import org.eclipse.jdt.core.dom.ASTParser
import org.eclipse.jdt.core.dom.CompilationUnit
import org.eclipse.jdt.internal.corext.refactoring.code.InlineTempRefactoring

/**
 * Inline the local variable at ([line], [column]) in [relativeFilePath],
 * replacing every use of the variable with its initialiser expression.
 */
internal object InlineVariableOp {

    fun run(
        javaProject: IJavaProject,
        relativeFilePath: String,
        line: Int,
        column: Int,
    ): RefactoringRunner.Outcome {
        val icu = findCompilationUnit(javaProject, relativeFilePath)
            ?: return RefactoringRunner.Outcome.Failure("no compilation unit at $relativeFilePath")

        val source = String(icu.buffer.characters)
        val offset = lineColumnOffset(source, line, column)

        val parser = ASTParser.newParser(AST.JLS_Latest).apply {
            setKind(ASTParser.K_COMPILATION_UNIT)
            setSource(icu)
            setResolveBindings(true)
        }
        @Suppress("UNUSED_VARIABLE")
        val astRoot = parser.createAST(null) as CompilationUnit

        val refactoring = InlineTempRefactoring(icu, astRoot, offset, 0)
        return RefactoringRunner.run(refactoring)
    }

    private fun findCompilationUnit(javaProject: IJavaProject, relativeFilePath: String): ICompilationUnit? {
        val file: IFile = javaProject.project.getFile(relativeFilePath).takeIf { it.exists() }
            ?: return null
        return JavaCore.createCompilationUnitFrom(file)
    }

    private fun lineColumnOffset(source: String, line: Int, column: Int): Int {
        var idx = 0
        var seen = 1
        while (seen < line && idx < source.length) {
            if (source[idx] == '\n') seen++
            idx++
        }
        return idx + (column - 1).coerceAtLeast(0)
    }
}
