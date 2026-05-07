package com.github.ethanhosier.refactoringbundle.internal.ops

import com.github.ethanhosier.refactoringbundle.internal.RefactoringRunner
import com.github.ethanhosier.refactoringbundle.internal.anchor.AnchorOps
import com.github.ethanhosier.refactoringbundle.internal.anchor.AnchorResolver
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.dom.AST
import org.eclipse.jdt.core.dom.ASTParser
import org.eclipse.jdt.core.dom.CompilationUnit
import org.eclipse.jdt.internal.corext.refactoring.code.InlineTempRefactoring

/**
 * ## Known limitation — split-declared locals (JDT capability gap)
 *
 * Eclipse JDT's [InlineTempRefactoring] requires the inlined local
 * to be initialised at its declaration site (`T x = expr; … use(x);`).
 * Locals declared without an initialiser and assigned in branches —
 *
 * ```java
 * String grade;
 * if (price > 1000) { grade = "A"; }
 * else if (price > 500) { grade = "B"; }
 * else { grade = "C"; }
 * ```
 *
 * — fail JDT's `checkInitialConditions()` with
 * *"Local variable 'grade' is not initialized at declaration."*
 * IntelliJ's Inline Variable handles this case (it walks every
 * assignment and substitutes the assigned value into the
 * corresponding usage path), so RM legitimately surfaces the user's
 * Inline Variable, but our JDT replay then refuses.
 *
 * Surfaces as `REFACTOR_FAILED` from
 * [com.github.ethanhosier.analysis.alternative.validate.RefactoringStepValidator]
 * with that exact message. The validator correctly windows the step
 * out — there's nothing to "fix" on this side, the precondition is
 * inherent to JDT.
 *
 * Lifting it would require either:
 *   - implementing our own split-decl-aware Inline Variable (substitute
 *     each branch's assigned RHS into the corresponding read path), or
 *   - migrating to a different refactoring engine.
 *
 * Both are out of scope; documented here so the failure mode isn't a
 * surprise the next time a session shows it.
 */
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

        // InlineTempRefactoring needs a binding-resolved AST so it can
        // resolve the variable's IVariableBinding. The shared
        // [AnchorResolver.parse] uses `setResolveBindings(false)` for
        // speed (sufficient for hashing) — re-parse here just for this op.
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
