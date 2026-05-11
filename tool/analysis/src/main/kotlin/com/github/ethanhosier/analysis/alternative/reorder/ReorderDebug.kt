package com.github.ethanhosier.analysis.alternative.reorder

import com.github.ethanhosier.analysis.miner.model.RefactoringSpec

/**
 * Human-readable summary of a window's specs, derived edges, and
 * enumerated orderings. Test-only; not surfaced in production reports.
 *
 * Used during the manual-inspection workflow described in
 * `tool/plans/PLAN-reorder-enumerator.md` — paste the output, eyeball
 * whether the entity touches and edge reasons match intuition before
 * wiring synthesis.
 */
object ReorderDebug {

    fun describe(specs: List<RefactoringSpec>, budget: EnumerationBudget = EnumerationBudget()): String {
        val dag = SpecDependencyAnalyzer.analyze(specs)
        val enumeration = TopologicalEnumerator.enumerate(dag, budget)
        return buildString {
            appendSpecs(specs)
            appendLine()
            appendEdges(dag)
            appendLine()
            appendOrderings(enumeration, userOrder = specs.indices.toList())
        }
    }

    private fun StringBuilder.appendSpecs(specs: List<RefactoringSpec>) {
        appendLine("Specs (n=${specs.size}):")
        specs.forEachIndexed { i, s -> appendLine("  [$i] ${formatSpec(s)}") }
    }

    private fun StringBuilder.appendEdges(dag: SpecDag) {
        val flat = dag.edges.entries
            .flatMap { (src, succs) -> succs.map { src to it } }
            .sortedWith(compareBy({ it.first }, { it.second }))
        if (flat.isEmpty()) {
            appendLine("Edges: (none — all specs commute)")
            return
        }
        appendLine("Edges:")
        for ((src, dst) in flat) {
            val why = dag.edgeReasons[src to dst].orEmpty()
            val first = why.firstOrNull()
            val tail = if (why.size > 1) "  (+${why.size - 1} more)" else ""
            appendLine("  $src → $dst   ${first?.let(::formatReason) ?: ""}$tail")
        }
    }

    private fun StringBuilder.appendOrderings(result: EnumerationResult, userOrder: List<Int>) {
        if (result.skipReason != null) {
            appendLine("Orderings: skipped (${result.skipReason})")
            return
        }
        val tag = if (result.truncated) " (truncated)" else ""
        appendLine("Orderings (${result.orderings.size} valid)$tag:")
        for (o in result.orderings) {
            val mark = if (o == userOrder) "   ← user's" else ""
            appendLine("  $o$mark")
        }
    }

    private fun formatReason(r: EdgeReason): String =
        "(${r.kind.name.lowercase().replace('_', ' ')} on ${formatEntity(r.entity)})"

    private fun formatEntity(e: Entity): String = when (e) {
        is Entity.Type -> "Type(${e.fqn}${ver(e.version)})"
        is Entity.Method -> "Method(${e.declaringTypeFqn}#${e.name}${formatParams(e.paramTypeSignatures)}${ver(e.version)})"
        is Entity.Field -> "Field(${e.declaringTypeFqn}.${e.name}${ver(e.version)})"
        is Entity.Package -> "Package(${e.name}${ver(e.version)})"
        is Entity.HostMethodBody -> "Body(${e.declaringTypeFqn}#${e.methodName})"
        is Entity.Declaration -> "Decl(${e.host.declaringTypeFqn}#${e.host.methodName}, ${shortHash(e.declarationSubtreeHash)})"
        is Entity.Region -> "Region(${e.host.declaringTypeFqn}#${e.host.methodName}, ${shortHash(e.selectionSubtreeHash)})"
    }

    /** Render `@v<n>` only when [v] is non-zero. v=0 = pre-window or unversioned (visual noise). */
    private fun ver(v: Int): String = if (v == 0) "" else "@v$v"

    private fun formatParams(p: ParamTypes): String = when (p) {
        is ParamTypes.Known -> "(${p.list.joinToString(",")})"
        is ParamTypes.Opaque -> "(?)"
    }

    private fun shortHash(h: String): String = if (h.length <= 8) h else "${h.take(8)}.."

    @Suppress("CyclomaticComplexMethod")
    private fun formatSpec(s: RefactoringSpec): String = when (s) {
        RefactoringSpec.Other -> "Other"
        is RefactoringSpec.RenameClass -> "RenameClass(${s.typeFqn} → ${s.newName})"
        is RefactoringSpec.RenameMethod -> "RenameMethod(${s.declaringTypeFqn}#${s.oldName} → ${s.newName})"
        is RefactoringSpec.RenameField -> "RenameField(${s.declaringTypeFqn}.${s.oldName} → ${s.newName})"
        is RefactoringSpec.RenameLocalVariable -> "RenameLocalVariable(${s.declaringTypeFqn}#${s.hostMethodName} → ${s.newName})"
        is RefactoringSpec.RenameParameter -> "RenameParameter(${s.declaringTypeFqn}#${s.hostMethodName} → ${s.newName})"
        is RefactoringSpec.RenamePackage -> "RenamePackage(${s.oldPackage} → ${s.newPackage})"
        is RefactoringSpec.MoveClass -> "MoveClass(${s.typeFqn} → ${s.destinationPackage})"
        is RefactoringSpec.MoveAndRenameClass -> "MoveAndRenameClass(${s.typeFqn} → ${s.destinationPackage}.${s.newName})"
        is RefactoringSpec.MoveInstanceField -> "MoveInstanceField(${s.sourceTypeFqn}.${s.fieldName} → ${s.destinationTypeFqn})"
        is RefactoringSpec.MoveInstanceMethod -> "MoveInstanceMethod(${s.sourceTypeFqn}#${s.methodName} → ${s.targetName})"
        is RefactoringSpec.MoveAndRenameAttribute -> "MoveAndRenameAttribute(${s.sourceTypeFqn}.${s.fieldName} → ${s.destinationTypeFqn}.${s.newFieldName})"
        is RefactoringSpec.MoveAndRenameMethod -> "MoveAndRenameMethod(${s.sourceTypeFqn}#${s.methodName} → ${s.targetTypeFqn}#${s.newMethodName})"
        is RefactoringSpec.MovePackage -> "MovePackage(${s.oldPackage} → ${s.newParentPackage})"
        is RefactoringSpec.MoveStaticMembers -> "MoveStaticMembers(${s.sourceTypeFqn} → ${s.destinationTypeFqn})"
        is RefactoringSpec.PullUp -> "PullUp(${s.declaringTypeFqn}, methods=${s.methodNames}, fields=${s.fieldNames})"
        is RefactoringSpec.PushDown -> "PushDown(${s.declaringTypeFqn}, methods=${s.methodNames}, fields=${s.fieldNames})"
        is RefactoringSpec.ExtractMethod -> "ExtractMethod(${s.declaringTypeFqn}#${s.hostMethodName}, sel=${shortHash(s.selectionSubtreeHash)} → ${s.newMethodName})"
        is RefactoringSpec.InlineMethod -> "InlineMethod(${s.declaringTypeFqn}#${s.methodName})"
        is RefactoringSpec.ExtractVariable -> "ExtractVariable(${s.declaringTypeFqn}#${s.hostMethodName}, sel=${shortHash(s.selectionSubtreeHash)} → ${s.newName})"
        is RefactoringSpec.InlineVariable -> "InlineVariable(${s.declaringTypeFqn}#${s.hostMethodName}, decl=${shortHash(s.declarationSubtreeHash)})"
        is RefactoringSpec.ExtractAttribute -> "ExtractAttribute(${s.declaringTypeFqn}#${s.hostMethodName}, sel=${shortHash(s.selectionSubtreeHash)} → ${s.newName})"
        is RefactoringSpec.ExtractClass -> "ExtractClass(${s.sourceTypeFqn} → ${s.newClassName}, fields=${s.fieldNames})"
        is RefactoringSpec.ExtractSubclass -> "ExtractSubclass(${s.sourceTypeFqn} → ${s.newSubclassName})"
        is RefactoringSpec.ExtractSuperclass -> "ExtractSuperclass(${s.sourceTypeFqn} → ${s.newSupertypeName})"
        is RefactoringSpec.ExtractInterface -> "ExtractInterface(${s.sourceTypeFqn} → ${s.newInterfaceName})"
        is RefactoringSpec.ExtractAndMoveMethod -> "ExtractAndMoveMethod(${s.declaringTypeFqn} → ${s.newMethodName}@${s.moveTargetName})"
        is RefactoringSpec.ChangeMethodSignature -> "ChangeMethodSignature(${s.declaringTypeFqn}#${s.oldMethodName})"
        is RefactoringSpec.ChangeVariableType -> "ChangeVariableType(${s.declaringTypeFqn}#${s.hostMethodName}, decl=${shortHash(s.declarationSubtreeHash)} → ${s.newTypeFqn})"
        is RefactoringSpec.ChangeAttributeType -> "ChangeAttributeType(${s.declaringTypeFqn}.${s.fieldName} → ${s.newTypeFqn})"
        is RefactoringSpec.ParameterizeVariable -> "ParameterizeVariable(${s.declaringTypeFqn}#${s.hostMethodName} → ${s.newParameterName})"
        is RefactoringSpec.ParameterizeAttribute -> "ParameterizeAttribute(${s.declaringTypeFqn}#${s.hostMethodName} → ${s.newParameterName})"
        is RefactoringSpec.ReplaceVariableWithAttribute -> "ReplaceVariableWithAttribute(${s.declaringTypeFqn}#${s.hostMethodName} → ${s.newFieldName})"
    }
}
