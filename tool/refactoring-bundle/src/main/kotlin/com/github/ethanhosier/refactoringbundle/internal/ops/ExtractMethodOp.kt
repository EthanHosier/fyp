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

        if (isStatic) {
            val rewriteOutcome = forceStaticModifier(icu, declaringTypeFqn, newMethodName)
            if (rewriteOutcome is RefactoringRunner.Outcome.Failure) return rewriteOutcome
        }

        val promoteOutcome = promoteVoidToReturn(icu, declaringTypeFqn, newMethodName)
        if (promoteOutcome is RefactoringRunner.Outcome.Failure) return promoteOutcome

        val splitOutcome = splitInlinedReturnCallSites(icu, declaringTypeFqn, newMethodName)
        if (splitOutcome is RefactoringRunner.Outcome.Failure) return splitOutcome
        return outcome
    }

    private fun promoteVoidToReturn(
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
