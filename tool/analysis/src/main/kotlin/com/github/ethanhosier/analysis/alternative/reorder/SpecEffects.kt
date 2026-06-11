package com.github.ethanhosier.analysis.alternative.reorder

import com.github.ethanhosier.analysis.miner.model.RefactoringSpec

data class Effects(
    val reads: Set<Entity> = emptySet(),
    val writes: Set<Entity> = emptySet(),
    val produces: Set<Entity> = emptySet(),
    val consumes: Set<Entity> = emptySet(),
)

@Suppress("LongMethod", "CyclomaticComplexMethod")
fun effectsOf(spec: RefactoringSpec): Effects = when (spec) {

    RefactoringSpec.Other ->
        error("effectsOf: caller must filter RefactoringSpec.Other before reordering")

    // ── Rename ─────────────────────────────────────────────────────

    is RefactoringSpec.RenameClass -> {
        val oldType = Entity.Type(spec.typeFqn)
        val newType = Entity.Type(renameSimpleName(spec.typeFqn, spec.newName))
        Effects(reads = setOf(oldType), consumes = setOf(oldType), produces = setOf(newType))
    }

    is RefactoringSpec.RenameMethod -> {
        val params = paramTypesOf(spec.paramTypeSignatures)
        val oldM = Entity.Method(spec.declaringTypeFqn, spec.oldName, params)
        val newM = Entity.Method(spec.declaringTypeFqn, spec.newName, params)
        Effects(
            reads = setOf(oldM),
            consumes = setOf(oldM),
            produces = setOf(newM),
            writes = setOf(Entity.Type(spec.declaringTypeFqn)), // coarse: call sites
        )
    }

    is RefactoringSpec.RenameField -> {
        val oldF = Entity.Field(spec.declaringTypeFqn, spec.oldName)
        val newF = Entity.Field(spec.declaringTypeFqn, spec.newName)
        Effects(reads = setOf(oldF), consumes = setOf(oldF), produces = setOf(newF))
    }

    is RefactoringSpec.RenameLocalVariable -> {
        val host = hostOf(spec.declaringTypeFqn, spec.hostMethodName, spec.hostMethodParamTypes)
        val decl = Entity.Declaration(host, spec.declarationSubtreeHash)
        Effects(
            reads = setOf(decl),
            consumes = setOf(decl),
            writes = setOf(host),
        )
    }

    is RefactoringSpec.RenameParameter -> {
        val host = hostOf(spec.declaringTypeFqn, spec.hostMethodName, spec.hostMethodParamTypes)
        val decl = Entity.Declaration(host, spec.declarationSubtreeHash)
        Effects(
            reads = setOf(decl),
            consumes = setOf(decl),
            writes = setOf(host),
        )
    }

    is RefactoringSpec.RenamePackage -> {
        val oldP = Entity.Package(spec.oldPackage)
        val newP = Entity.Package(spec.newPackage)
        Effects(reads = setOf(oldP), consumes = setOf(oldP), produces = setOf(newP))
    }

    // ── Move ───────────────────────────────────────────────────────

    is RefactoringSpec.MoveClass -> {
        val oldT = Entity.Type(spec.typeFqn)
        val newT = Entity.Type("${spec.destinationPackage}.${simpleNameOf(spec.typeFqn)}")
        Effects(reads = setOf(oldT), consumes = setOf(oldT), produces = setOf(newT))
    }

    is RefactoringSpec.MoveAndRenameClass -> {
        val oldT = Entity.Type(spec.typeFqn)
        val newT = Entity.Type("${spec.destinationPackage}.${spec.newName}")
        Effects(reads = setOf(oldT), consumes = setOf(oldT), produces = setOf(newT))
    }

    is RefactoringSpec.MoveInstanceField -> {
        val oldF = Entity.Field(spec.sourceTypeFqn, spec.fieldName)
        val newF = Entity.Field(spec.destinationTypeFqn, spec.fieldName)
        Effects(
            reads = setOf(oldF, Entity.Type(spec.sourceTypeFqn), Entity.Type(spec.destinationTypeFqn)),
            consumes = setOf(oldF),
            produces = setOf(newF),
        )
    }

    is RefactoringSpec.MoveAndRenameAttribute -> {
        val oldF = Entity.Field(spec.sourceTypeFqn, spec.fieldName)
        val newF = Entity.Field(spec.destinationTypeFqn, spec.newFieldName)
        Effects(
            reads = setOf(oldF, Entity.Type(spec.sourceTypeFqn), Entity.Type(spec.destinationTypeFqn)),
            consumes = setOf(oldF),
            produces = setOf(newF),
        )
    }

    is RefactoringSpec.MoveInstanceMethod -> {
        val src = Entity.Method(spec.sourceTypeFqn, spec.methodName, ParamTypes.Opaque())
        Effects(
            reads = setOf(src, Entity.Type(spec.sourceTypeFqn)),
            consumes = setOf(src),
            writes = setOf(Entity.Type(spec.sourceTypeFqn)),
        )
    }

    is RefactoringSpec.MoveAndRenameMethod -> {
        val src = Entity.Method(spec.sourceTypeFqn, spec.methodName, ParamTypes.Opaque())
        val dst = Entity.Method(spec.targetTypeFqn, spec.newMethodName, ParamTypes.Opaque())
        Effects(
            reads = setOf(src, Entity.Type(spec.sourceTypeFqn), Entity.Type(spec.targetTypeFqn)),
            consumes = setOf(src),
            produces = setOf(dst),
            writes = setOf(Entity.Type(spec.sourceTypeFqn)),
        )
    }

    is RefactoringSpec.MovePackage -> {
        val oldP = Entity.Package(spec.oldPackage)
        val newP = Entity.Package("${spec.newParentPackage}.${simpleNameOf(spec.oldPackage)}")
        Effects(reads = setOf(oldP), consumes = setOf(oldP), produces = setOf(newP))
    }

    is RefactoringSpec.MoveStaticMembers -> {
        val readsB = mutableSetOf<Entity>(
            Entity.Type(spec.sourceTypeFqn), Entity.Type(spec.destinationTypeFqn),
        )
        val consumesB = mutableSetOf<Entity>()
        val producesB = mutableSetOf<Entity>()
        spec.fieldNames.forEach { f ->
            val src = Entity.Field(spec.sourceTypeFqn, f); val dst = Entity.Field(spec.destinationTypeFqn, f)
            readsB += src; consumesB += src; producesB += dst
        }
        spec.methodNames.forEach { m ->
            val src = Entity.Method(spec.sourceTypeFqn, m, ParamTypes.Opaque())
            val dst = Entity.Method(spec.destinationTypeFqn, m, ParamTypes.Opaque())
            readsB += src; consumesB += src; producesB += dst
        }
        Effects(reads = readsB, consumes = consumesB, produces = producesB,
            writes = setOf(Entity.Type(spec.sourceTypeFqn)))
    }

    // ── Extract / Inline ───────────────────────────────────────────

    is RefactoringSpec.ExtractMethod -> {
        val host = hostOf(spec.declaringTypeFqn, spec.hostMethodName, spec.hostMethodParamTypes)
        // Deliberately no `writes = setOf(host)` — see KDoc on `effectsOf`
        // ("Optimistic model for Extract* specs").
        Effects(
            reads = setOf(Entity.Region(host, spec.selectionSubtreeHash), host),
            produces = setOf(Entity.Method(spec.declaringTypeFqn, spec.newMethodName, ParamTypes.Opaque())),
        )
    }

    is RefactoringSpec.InlineMethod -> {
        val params = paramTypesOf(spec.paramTypeSignatures)
        val m = Entity.Method(spec.declaringTypeFqn, spec.methodName, params)
        Effects(
            reads = setOf(m),
            consumes = setOf(m),
        )
    }

    is RefactoringSpec.ExtractVariable -> {
        val host = hostOf(spec.declaringTypeFqn, spec.hostMethodName, spec.hostMethodParamTypes)
        // Deliberately no `writes = setOf(host)` — see KDoc on `effectsOf`.
        // Produced declaration's hash is unknown; opaque marker omitted.
        Effects(reads = setOf(Entity.Region(host, spec.selectionSubtreeHash), host))
    }

    is RefactoringSpec.InlineVariable -> {
        val host = hostOf(spec.declaringTypeFqn, spec.hostMethodName, spec.hostMethodParamTypes)
        val decl = Entity.Declaration(host, spec.declarationSubtreeHash)
        Effects(reads = setOf(decl), consumes = setOf(decl), writes = setOf(host))
    }

    is RefactoringSpec.ExtractAttribute -> {
        val host = hostOf(spec.declaringTypeFqn, spec.hostMethodName, spec.hostMethodParamTypes)
        // Deliberately no `writes = setOf(host)` — see KDoc on `effectsOf`.
        Effects(
            reads = setOf(Entity.Region(host, spec.selectionSubtreeHash), host),
            produces = setOf(Entity.Field(spec.declaringTypeFqn, spec.newName)),
        )
    }

    is RefactoringSpec.ExtractClass -> {
        val readsB = mutableSetOf<Entity>(Entity.Type(spec.sourceTypeFqn))
        val consumesB = mutableSetOf<Entity>()
        val producesB = mutableSetOf<Entity>()
        val newType = Entity.Type("${packageOf(spec.sourceTypeFqn)}.${spec.newClassName}")
        producesB += newType
        producesB += Entity.Field(spec.sourceTypeFqn, spec.delegateFieldName)
        spec.fieldNames.forEach { f ->
            val src = Entity.Field(spec.sourceTypeFqn, f)
            readsB += src; consumesB += src
            producesB += Entity.Field(newType.fqn, f)
        }
        Effects(reads = readsB, consumes = consumesB, produces = producesB)
    }

    is RefactoringSpec.ExtractSubclass -> {
        // No mapper arm in RM today; conservatively model like ExtractClass
        // minus the delegate field.
        val readsB = mutableSetOf<Entity>(Entity.Type(spec.sourceTypeFqn))
        val producesB = mutableSetOf<Entity>(
            Entity.Type("${packageOf(spec.sourceTypeFqn)}.${spec.newSubclassName}"),
        )
        Effects(reads = readsB, produces = producesB)
    }

    is RefactoringSpec.ExtractSuperclass -> {
        val readsB = mutableSetOf<Entity>(Entity.Type(spec.sourceTypeFqn))
        val producesB = mutableSetOf<Entity>(
            Entity.Type("${packageOf(spec.sourceTypeFqn)}.${spec.newSupertypeName}"),
        )
        Effects(reads = readsB, produces = producesB,
            writes = setOf(Entity.Type(spec.sourceTypeFqn)))
    }

    is RefactoringSpec.ExtractInterface -> {
        val readsB = mutableSetOf<Entity>(Entity.Type(spec.sourceTypeFqn))
        val producesB = mutableSetOf<Entity>(
            Entity.Type("${packageOf(spec.sourceTypeFqn)}.${spec.newInterfaceName}"),
        )
        Effects(reads = readsB, produces = producesB)
    }

    // ── Hierarchy ──────────────────────────────────────────────────

    is RefactoringSpec.PullUp -> {
        val readsB = mutableSetOf<Entity>(Entity.Type(spec.declaringTypeFqn))
        val consumesB = mutableSetOf<Entity>()
        spec.methodNames.forEach {
            val m = Entity.Method(spec.declaringTypeFqn, it, ParamTypes.Opaque())
            readsB += m; consumesB += m
        }
        spec.fieldNames.forEach {
            val f = Entity.Field(spec.declaringTypeFqn, it)
            readsB += f; consumesB += f
        }
        Effects(reads = readsB, consumes = consumesB)
    }

    is RefactoringSpec.PushDown -> {
        val readsB = mutableSetOf<Entity>(Entity.Type(spec.declaringTypeFqn))
        val consumesB = mutableSetOf<Entity>()
        spec.methodNames.forEach {
            val m = Entity.Method(spec.declaringTypeFqn, it, ParamTypes.Opaque())
            readsB += m; consumesB += m
        }
        spec.fieldNames.forEach {
            val f = Entity.Field(spec.declaringTypeFqn, it)
            readsB += f; consumesB += f
        }
        Effects(reads = readsB, consumes = consumesB)
    }

    is RefactoringSpec.ExtractAndMoveMethod -> {
        Effects(
            reads = setOf(Entity.Type(spec.declaringTypeFqn)),
            writes = setOf(Entity.Type(spec.declaringTypeFqn)),
            produces = setOf(Entity.Method(spec.declaringTypeFqn, spec.newMethodName, ParamTypes.Opaque())),
        )
    }

    // ── Change Signature / Type ────────────────────────────────────

    is RefactoringSpec.ChangeMethodSignature -> {
        val params = paramTypesOf(spec.paramTypeSignatures)
        val oldM = Entity.Method(spec.declaringTypeFqn, spec.oldMethodName, params)
        val newName = if (spec.newMethodName.isEmpty()) spec.oldMethodName else spec.newMethodName
        val newM = Entity.Method(spec.declaringTypeFqn, newName, ParamTypes.Opaque())
        Effects(
            reads = setOf(oldM),
            consumes = setOf(oldM),
            produces = setOf(newM),
            writes = setOf(Entity.Type(spec.declaringTypeFqn)), // coarse: call sites
        )
    }

    is RefactoringSpec.ChangeVariableType -> {
        val host = hostOf(spec.declaringTypeFqn, spec.hostMethodName, spec.hostMethodParamTypes)
        val decl = Entity.Declaration(host, spec.declarationSubtreeHash)
        Effects(reads = setOf(decl), consumes = setOf(decl), writes = setOf(host))
    }

    is RefactoringSpec.ChangeAttributeType -> {
        val f = Entity.Field(spec.declaringTypeFqn, spec.fieldName)
        Effects(reads = setOf(f), writes = setOf(f))
    }

    is RefactoringSpec.ParameterizeVariable -> {
        val host = hostOf(spec.declaringTypeFqn, spec.hostMethodName, spec.hostMethodParamTypes)
        val oldM = Entity.Method(spec.declaringTypeFqn, spec.hostMethodName, ParamTypes.Known(spec.hostMethodParamTypes))
        val newM = Entity.Method(spec.declaringTypeFqn, spec.hostMethodName, ParamTypes.Opaque())
        Effects(
            reads = setOf(Entity.Region(host, spec.selectionSubtreeHash), host),
            writes = setOf(host, Entity.Type(spec.declaringTypeFqn)), // coarse: call sites of host
            consumes = setOf(oldM),
            produces = setOf(newM),
        )
    }

    is RefactoringSpec.ParameterizeAttribute -> {
        val host = hostOf(spec.declaringTypeFqn, spec.hostMethodName, spec.hostMethodParamTypes)
        val oldM = Entity.Method(spec.declaringTypeFqn, spec.hostMethodName, ParamTypes.Known(spec.hostMethodParamTypes))
        val newM = Entity.Method(spec.declaringTypeFqn, spec.hostMethodName, ParamTypes.Opaque())
        Effects(
            reads = setOf(Entity.Region(host, spec.selectionSubtreeHash), host),
            writes = setOf(host, Entity.Type(spec.declaringTypeFqn)),
            consumes = setOf(oldM),
            produces = setOf(newM),
        )
    }

    is RefactoringSpec.ReplaceVariableWithAttribute -> {
        val host = hostOf(spec.declaringTypeFqn, spec.hostMethodName, spec.hostMethodParamTypes)
        val decl = Entity.Declaration(host, spec.declarationSubtreeHash)
        Effects(
            reads = setOf(decl, host),
            consumes = setOf(decl),
            writes = setOf(host),
            produces = setOf(Entity.Field(spec.declaringTypeFqn, spec.newFieldName)),
        )
    }
}

private fun hostOf(typeFqn: String, methodName: String, paramTypes: List<String>) =
    Entity.HostMethodBody(typeFqn, methodName, paramTypes)

private fun paramTypesOf(list: List<String>?): ParamTypes =
    if (list == null) ParamTypes.Opaque() else ParamTypes.Known(list)

private fun simpleNameOf(qualified: String): String =
    qualified.substringAfterLast('.', missingDelimiterValue = qualified)

private fun packageOf(typeFqn: String): String =
    typeFqn.substringBeforeLast('.', missingDelimiterValue = "")

private fun renameSimpleName(typeFqn: String, newSimpleName: String): String {
    val pkg = packageOf(typeFqn)
    return if (pkg.isEmpty()) newSimpleName else "$pkg.$newSimpleName"
}
