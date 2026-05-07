package com.github.ethanhosier.refactoringbundle.internal.ops

import com.github.ethanhosier.refactoringbundle.internal.RefactoringRunner
import com.github.ethanhosier.refactoringbundle.internal.anchor.AnchorOps
import com.github.ethanhosier.refactoringbundle.internal.anchor.AnchorResolver
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.dom.MethodDeclaration
import org.eclipse.jdt.core.dom.Modifier
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite
import org.eclipse.jdt.internal.corext.refactoring.code.ExtractMethodRefactoring

internal object ExtractMethodOp {

    fun run(
        javaProject: IJavaProject,
        relativeFilePath: String,
        declaringTypeFqn: String,
        hostMethodName: String,
        hostMethodParamTypes: Array<String>,
        selectionSubtreeHash: String,
        selectionNodeCount: Int,
        originalLineHint: Int,
        originalColumnHint: Int,
        newMethodName: String,
        isStatic: Boolean,
    ): RefactoringRunner.Outcome {
        val icu = AnchorOps.findCompilationUnit(javaProject, relativeFilePath)
            ?: return RefactoringRunner.Outcome.Failure("no compilation unit at $relativeFilePath")
        val cu = AnchorResolver.parse(icu)
        val host = AnchorResolver.findHostMethod(cu, declaringTypeFqn, hostMethodName, hostMethodParamTypes)
            ?: return RefactoringRunner.Outcome.Failure(
                "host method not found: $declaringTypeFqn#$hostMethodName(${hostMethodParamTypes.joinToString(",")})",
            )
        val selection = AnchorResolver.findSelection(
            host, selectionSubtreeHash, selectionNodeCount, AnchorOps.hintOrNull(originalLineHint),
        ) ?: return RefactoringRunner.Outcome.Failure("no AST subtree match for hash=$selectionSubtreeHash")

        val refactoring = ExtractMethodRefactoring(icu, selection.startPosition, selection.length).apply {
            methodName = newMethodName
            visibility = Modifier.PRIVATE
        }
        val outcome = RefactoringRunner.run(refactoring)
        if (outcome !is RefactoringRunner.Outcome.Success) return outcome

        // JDT only emits `static` when the new method *must* be
        // static (every call site is in a static context). The
        // user's IDE — IntelliJ — adds it whenever the body permits.
        // RM gives us the user's actual modifier; force a match here
        // via an ASTRewrite when the result we got from JDT differs.
        if (isStatic) {
            val rewriteOutcome = forceStaticModifier(icu, declaringTypeFqn, newMethodName)
            if (rewriteOutcome is RefactoringRunner.Outcome.Failure) return rewriteOutcome
        }
        return outcome
    }

    /**
     * Re-parse [icu], locate the freshly-extracted method by
     * `(declaringTypeFqn, methodName)`, and add `static` to its
     * modifier list via an [ASTRewrite] iff it's not already there.
     * Returns [RefactoringRunner.Outcome.Success] on no-op too.
     */
    private fun forceStaticModifier(
        icu: ICompilationUnit,
        declaringTypeFqn: String,
        methodName: String,
    ): RefactoringRunner.Outcome {
        icu.becomeWorkingCopy(NullProgressMonitor())
        try {
            val cu = AnchorResolver.parse(icu)
            val method = AnchorResolver.findMethodByName(cu, declaringTypeFqn, methodName)
                ?: return RefactoringRunner.Outcome.Failure(
                    "post-extract static rewrite: $declaringTypeFqn#$methodName not found",
                )
            if (Modifier.isStatic(method.modifiers)) {
                return RefactoringRunner.Outcome.Success(emptyList())
            }
            val ast = cu.ast
            val rewrite = ASTRewrite.create(ast)
            val staticMod = ast.newModifier(Modifier.ModifierKeyword.STATIC_KEYWORD)
            val listRewrite = rewrite.getListRewrite(method, MethodDeclaration.MODIFIERS2_PROPERTY)
            listRewrite.insertLast(staticMod, null)
            val edit = rewrite.rewriteAST()
            icu.applyTextEdit(edit, NullProgressMonitor())
            icu.commitWorkingCopy(true, NullProgressMonitor())
            return RefactoringRunner.Outcome.Success(emptyList())
        } finally {
            icu.discardWorkingCopy()
        }
    }
}
