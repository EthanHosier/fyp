package com.github.ethanhosier.refactoringbundle.internal.ops

import com.github.ethanhosier.refactoringbundle.internal.RefactoringRunner
import com.github.ethanhosier.refactoringbundle.internal.anchor.AnchorOps
import com.github.ethanhosier.refactoringbundle.internal.anchor.AnchorResolver
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.dom.AST
import org.eclipse.jdt.core.dom.ASTNode
import org.eclipse.jdt.core.dom.ASTParser
import org.eclipse.jdt.core.dom.ASTVisitor
import org.eclipse.jdt.core.dom.Assignment
import org.eclipse.jdt.core.dom.Block
import org.eclipse.jdt.core.dom.Expression
import org.eclipse.jdt.core.dom.ExpressionStatement
import org.eclipse.jdt.core.dom.IVariableBinding
import org.eclipse.jdt.core.dom.MethodDeclaration
import org.eclipse.jdt.core.dom.MethodInvocation
import org.eclipse.jdt.core.dom.Modifier
import org.eclipse.jdt.core.dom.PrimitiveType
import org.eclipse.jdt.core.dom.SimpleName
import org.eclipse.jdt.core.dom.SingleVariableDeclaration
import org.eclipse.jdt.core.dom.Statement
import org.eclipse.jdt.core.dom.Type
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

        // JDT's `ExtractMethodAnalyzer.computeOutput()` fails to promote a
        // mutated outer-scoped variable to a return when the selection sits
        // inside a conditional branch — its post-selection liveness check
        // doesn't walk past the enclosing block's join point. IntelliJ's
        // analyzer does, so the user's "real" extraction looks like
        // `t = handleGold(t)` against our `void handleGold(double t)`.
        // Detect the shape and rewrite return-type + last stmt + call sites.
        val promoteOutcome = promoteVoidToReturn(icu, declaringTypeFqn, newMethodName)
        if (promoteOutcome is RefactoringRunner.Outcome.Failure) return promoteOutcome

        // When the original selection ended with `return <localVar>;` and
        // <localVar> was assigned earlier in the selection, JDT generates
        // a returning method (good) but collapses the call site into
        // `return f(args);` — discarding the outer-scope variable's identity.
        // IntelliJ keeps `<localVar> = f(args); return <localVar>;`. Split
        // those return-inlined call sites back into the assignment + return
        // pair so the AST matches the user's extraction.
        val splitOutcome = splitInlinedReturnCallSites(icu, declaringTypeFqn, newMethodName)
        if (splitOutcome is RefactoringRunner.Outcome.Failure) return splitOutcome
        return outcome
    }

    /**
     * Detect the void-with-trailing-assignment-to-a-parameter shape and
     * rewrite the method to return the new value, plus every call site to
     * capture it. Returns Success (no-op) when the shape doesn't match or
     * any call site can't be rewritten safely. Mirrors [forceStaticModifier]'s
     * working-copy + ASTRewrite pattern.
     */
    private fun promoteVoidToReturn(
        icu: ICompilationUnit,
        declaringTypeFqn: String,
        methodName: String,
    ): RefactoringRunner.Outcome {
        icu.becomeWorkingCopy(NullProgressMonitor())
        try {
            // We need resolved bindings here (LHS / arg SimpleName →
            // IVariableBinding); AnchorResolver.parse() parses without
            // bindings. Run a binding-aware parse on the same ICU.
            val parser = ASTParser.newParser(AST.JLS_Latest).apply {
                setKind(ASTParser.K_COMPILATION_UNIT)
                setSource(icu)
                setResolveBindings(true)
            }
            val cu = parser.createAST(NullProgressMonitor()) as org.eclipse.jdt.core.dom.CompilationUnit
            val method = AnchorResolver.findMethodByName(cu, declaringTypeFqn, methodName)
                ?: return RefactoringRunner.Outcome.Success(emptyList())

            // Must be a void-returning method (skip constructors / non-void).
            val returnType = method.returnType2 as? PrimitiveType ?: return RefactoringRunner.Outcome.Success(emptyList())
            if (returnType.primitiveTypeCode != PrimitiveType.VOID) {
                return RefactoringRunner.Outcome.Success(emptyList())
            }

            val body = method.body ?: return RefactoringRunner.Outcome.Success(emptyList())
            @Suppress("UNCHECKED_CAST")
            val statements = body.statements() as List<Statement>
            val lastStmt = statements.lastOrNull() as? ExpressionStatement
                ?: return RefactoringRunner.Outcome.Success(emptyList())
            val assign = lastStmt.expression as? Assignment
                ?: return RefactoringRunner.Outcome.Success(emptyList())
            if (assign.operator != Assignment.Operator.ASSIGN) {
                return RefactoringRunner.Outcome.Success(emptyList())
            }
            val lhs = assign.leftHandSide as? SimpleName
                ?: return RefactoringRunner.Outcome.Success(emptyList())
            val lhsBinding = lhs.resolveBinding() as? IVariableBinding
                ?: return RefactoringRunner.Outcome.Success(emptyList())

            @Suppress("UNCHECKED_CAST")
            val params = method.parameters() as List<SingleVariableDeclaration>
            val paramIdx = params.indexOfFirst { p ->
                val b = p.resolveBinding()
                b != null && b.isEqualTo(lhsBinding)
            }
            if (paramIdx < 0) return RefactoringRunner.Outcome.Success(emptyList())
            val paramType: Type = params[paramIdx].type

            // Find every call site of (declaringTypeFqn, methodName). Require
            // each to be a top-level ExpressionStatement whose paramIdx-th
            // argument is a SimpleName, so we can rewrite `f(x);` → `x = f(x);`.
            // If ANY caller breaks the shape, skip the whole promotion — we
            // can't change the method's return type without breaking the
            // value-discarding callers.
            data class CallSite(val stmt: ExpressionStatement, val invocation: MethodInvocation, val arg: SimpleName)
            val callSites = mutableListOf<CallSite>()
            var sawIncompatibleCaller = false
            cu.accept(object : ASTVisitor() {
                override fun visit(node: MethodInvocation): Boolean {
                    val mb = node.resolveMethodBinding() ?: return true
                    if (mb.name != methodName) return true
                    val declaring = mb.declaringClass?.qualifiedName
                    if (declaring != declaringTypeFqn) return true

                    val parent = node.parent
                    if (parent !is ExpressionStatement) {
                        sawIncompatibleCaller = true
                        return true
                    }
                    @Suppress("UNCHECKED_CAST")
                    val args = node.arguments() as List<Expression>
                    if (paramIdx >= args.size) {
                        sawIncompatibleCaller = true
                        return true
                    }
                    val argName = args[paramIdx] as? SimpleName
                    if (argName == null) {
                        sawIncompatibleCaller = true
                        return true
                    }
                    callSites.add(CallSite(parent, node, argName))
                    return true
                }
            })
            if (sawIncompatibleCaller || callSites.isEmpty()) {
                return RefactoringRunner.Outcome.Success(emptyList())
            }

            val ast = cu.ast
            val rewrite = ASTRewrite.create(ast)

            // 1. void → paramType
            val newReturnType = ASTNode.copySubtree(ast, paramType) as Type
            rewrite.set(method, MethodDeclaration.RETURN_TYPE2_PROPERTY, newReturnType, null)

            // 2. Keep the trailing `<param> = <rhs>;` and append
            // `return <param>;`. Matches IntelliJ's shape: the body's
            // mutation stays intact (in case the variable is read again
            // elsewhere inside the method) and we add a terminal return
            // that exposes the new value to the caller.
            val returnStmt = ast.newReturnStatement().apply {
                expression = ASTNode.copySubtree(ast, lhs) as Expression
            }
            rewrite.getListRewrite(body, Block.STATEMENTS_PROPERTY)
                .insertAfter(returnStmt, lastStmt, null)

            // 3. each `f(args);` → `<argName> = f(args);`
            for (cs in callSites) {
                val newAssign = ast.newAssignment().apply {
                    operator = Assignment.Operator.ASSIGN
                    leftHandSide = ASTNode.copySubtree(ast, cs.arg) as Expression
                    rightHandSide = ASTNode.copySubtree(ast, cs.invocation) as Expression
                }
                val newStmt = ast.newExpressionStatement(newAssign)
                rewrite.replace(cs.stmt, newStmt, null)
            }

            val edit = rewrite.rewriteAST()
            icu.applyTextEdit(edit, NullProgressMonitor())
            icu.commitWorkingCopy(true, NullProgressMonitor())
            return RefactoringRunner.Outcome.Success(emptyList())
        } finally {
            icu.discardWorkingCopy()
        }
    }

    /**
     * Detect call sites of the form `return f(args);` where the new
     * method ends with `return <param>;` and the matching call-site
     * argument is a SimpleName. Split each into
     * `<arg> = f(args);` + `return <arg>;` so the caller's variable
     * identity survives — matching IntelliJ's extract-method behaviour
     * when the original selection ended with `return <localVar>;`.
     */
    private fun splitInlinedReturnCallSites(
        icu: ICompilationUnit,
        declaringTypeFqn: String,
        methodName: String,
    ): RefactoringRunner.Outcome {
        icu.becomeWorkingCopy(NullProgressMonitor())
        try {
            val parser = ASTParser.newParser(AST.JLS_Latest).apply {
                setKind(ASTParser.K_COMPILATION_UNIT)
                setSource(icu)
                setResolveBindings(true)
            }
            val cu = parser.createAST(NullProgressMonitor()) as org.eclipse.jdt.core.dom.CompilationUnit
            val method = AnchorResolver.findMethodByName(cu, declaringTypeFqn, methodName)
                ?: return RefactoringRunner.Outcome.Success(emptyList())

            // Method must already be returning (non-void) and its last
            // statement must be `return <SimpleName>;` where the name
            // resolves to one of the method's parameters.
            val rType = method.returnType2 ?: return RefactoringRunner.Outcome.Success(emptyList())
            if (rType is PrimitiveType && rType.primitiveTypeCode == PrimitiveType.VOID) {
                return RefactoringRunner.Outcome.Success(emptyList())
            }
            val body = method.body ?: return RefactoringRunner.Outcome.Success(emptyList())
            @Suppress("UNCHECKED_CAST")
            val statements = body.statements() as List<Statement>
            val lastReturn = statements.lastOrNull() as? org.eclipse.jdt.core.dom.ReturnStatement
                ?: return RefactoringRunner.Outcome.Success(emptyList())
            val lastReturnName = lastReturn.expression as? SimpleName
                ?: return RefactoringRunner.Outcome.Success(emptyList())
            val lastReturnBinding = lastReturnName.resolveBinding() as? IVariableBinding
                ?: return RefactoringRunner.Outcome.Success(emptyList())

            @Suppress("UNCHECKED_CAST")
            val params = method.parameters() as List<SingleVariableDeclaration>
            val paramIdx = params.indexOfFirst { p ->
                val b = p.resolveBinding()
                b != null && b.isEqualTo(lastReturnBinding)
            }
            if (paramIdx < 0) return RefactoringRunner.Outcome.Success(emptyList())

            data class ReturnCallSite(val stmt: org.eclipse.jdt.core.dom.ReturnStatement, val invocation: MethodInvocation, val arg: SimpleName)
            val sites = mutableListOf<ReturnCallSite>()
            cu.accept(object : ASTVisitor() {
                override fun visit(node: MethodInvocation): Boolean {
                    val mb = node.resolveMethodBinding() ?: return true
                    if (mb.name != methodName) return true
                    val declaring = mb.declaringClass?.qualifiedName
                    if (declaring != declaringTypeFqn) return true
                    val parent = node.parent as? org.eclipse.jdt.core.dom.ReturnStatement ?: return true
                    if (parent.expression !== node) return true
                    @Suppress("UNCHECKED_CAST")
                    val args = node.arguments() as List<Expression>
                    if (paramIdx >= args.size) return true
                    val argName = args[paramIdx] as? SimpleName ?: return true
                    sites.add(ReturnCallSite(parent, node, argName))
                    return true
                }
            })
            if (sites.isEmpty()) return RefactoringRunner.Outcome.Success(emptyList())

            val ast = cu.ast
            val rewrite = ASTRewrite.create(ast)

            for (site in sites) {
                // Build `<arg> = call;` as an ExpressionStatement.
                val newAssign = ast.newAssignment().apply {
                    operator = Assignment.Operator.ASSIGN
                    leftHandSide = ASTNode.copySubtree(ast, site.arg) as Expression
                    rightHandSide = ASTNode.copySubtree(ast, site.invocation) as Expression
                }
                val assignStmt = ast.newExpressionStatement(newAssign)
                // Build `return <arg>;`.
                val returnStmt = ast.newReturnStatement().apply {
                    expression = ASTNode.copySubtree(ast, site.arg) as Expression
                }
                // Replace the single ReturnStatement with the two new
                // statements in its parent block.
                val parentBlock = site.stmt.parent as? Block
                    ?: return RefactoringRunner.Outcome.Success(emptyList())
                val listRewrite = rewrite.getListRewrite(parentBlock, Block.STATEMENTS_PROPERTY)
                listRewrite.replace(site.stmt, assignStmt, null)
                listRewrite.insertAfter(returnStmt, assignStmt, null)
            }

            val edit = rewrite.rewriteAST()
            icu.applyTextEdit(edit, NullProgressMonitor())
            icu.commitWorkingCopy(true, NullProgressMonitor())
            return RefactoringRunner.Outcome.Success(emptyList())
        } finally {
            icu.discardWorkingCopy()
        }
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
