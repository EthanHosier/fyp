package com.github.ethanhosier.analysis.miner

import com.github.ethanhosier.analysis.metrics.WorktreePool
import com.github.ethanhosier.analysis.miner.model.DetectedRefactoring
import com.github.ethanhosier.analysis.miner.model.RefactoringLocation
import com.github.ethanhosier.analysis.miner.model.RefactoringSpec
import com.github.ethanhosier.analysis.miner.model.RefactoringStep
import com.github.ethanhosier.analysis.model.ReconstructionResult
import com.github.ethanhosier.analysis.model.Trace
import com.github.ethanhosier.analysis.refactoring.anchor.SpecAnchorBuilder
import com.github.ethanhosier.ideplugin.model.EventType
import gr.uom.java.xmi.VariableDeclarationContainer
import gr.uom.java.xmi.diff.AddParameterRefactoring
import gr.uom.java.xmi.diff.ChangeAttributeTypeRefactoring
import gr.uom.java.xmi.diff.ChangeReturnTypeRefactoring
import gr.uom.java.xmi.diff.ChangeVariableTypeRefactoring
import gr.uom.java.xmi.diff.CodeRange
import gr.uom.java.xmi.diff.ExtractAttributeRefactoring
import gr.uom.java.xmi.diff.ExtractClassRefactoring
import gr.uom.java.xmi.diff.ExtractOperationRefactoring
import gr.uom.java.xmi.diff.ExtractSuperclassRefactoring
import gr.uom.java.xmi.diff.ExtractVariableRefactoring
import gr.uom.java.xmi.diff.InlineOperationRefactoring
import gr.uom.java.xmi.diff.InlineVariableRefactoring
import gr.uom.java.xmi.diff.MoveAndRenameAttributeRefactoring
import gr.uom.java.xmi.diff.MoveAndRenameClassRefactoring
import gr.uom.java.xmi.diff.MoveAttributeRefactoring
import gr.uom.java.xmi.diff.MoveClassRefactoring
import gr.uom.java.xmi.diff.PullUpAttributeRefactoring
import gr.uom.java.xmi.diff.PullUpOperationRefactoring
import gr.uom.java.xmi.diff.PushDownAttributeRefactoring
import gr.uom.java.xmi.diff.PushDownOperationRefactoring
import gr.uom.java.xmi.diff.RemoveParameterRefactoring
import gr.uom.java.xmi.diff.RenameAttributeRefactoring
import gr.uom.java.xmi.diff.RenameClassRefactoring
import gr.uom.java.xmi.diff.RenameOperationRefactoring
import gr.uom.java.xmi.diff.RenamePackageRefactoring
import gr.uom.java.xmi.diff.RenameVariableRefactoring
import gr.uom.java.xmi.diff.ReorderParameterRefactoring
import org.refactoringminer.api.Refactoring
import org.refactoringminer.api.RefactoringType
import org.refactoringminer.api.RefactoringHandler
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl
import java.nio.file.Path

/**
 * Runs RefactoringMiner on pairs of shadow-repo commits to detect
 * refactorings across the whole session — one end-to-end sliding window
 * over every consecutive checkpoint, regardless of whether a checkpoint
 * came from an edit burst or an automated IDE refactoring.
 *
 * Earlier iterations anchored at `SESSION_STARTED` / `REFACTORING_FINISHED`
 * events and treated IDE refactorings as a separate source. That produced
 * two parallel streams and split co-located manual + automated work. A
 * single unified pass gives one list of refactoring steps, which is what
 * the dashboard renders as primary chart points.
 *
 * Algorithm (sliding window):
 *  - Grow `R` forward from `L = checkpoint 0`.
 *  - On the first non-empty detection between `(L, R)`, shrink `L` forward
 *    while the canonical-keyed detection set stays equal — locking onto
 *    the tightest window `[L*, R]`.
 *  - Emit one [RefactoringStep] per detection, then restart with `L = R`.
 *
 * RM is invoked via [GitHistoryRefactoringMinerImpl.detectAtDirectories]
 * against two worktrees borrowed from [WorktreePool] at the two SHAs.
 */
class RefactoringMinerRunner(
    private val parallelism: Int = defaultParallelism(),
) {

    data class Summary(
        val checkpointsAnalysed: Int,
        val steps: List<RefactoringStep>,
    )

    fun run(
        trace: Trace,
        reconstruction: ReconstructionResult,
        sessionFolder: Path,
    ): Summary {
        val checkpoints = orderedUniqueShas(reconstruction)
        if (checkpoints.size < 2) return Summary(checkpoints.size, emptyList())

        val shaToTimestamp = firstTimestampPerSha(trace, reconstruction)
        val idePerformedShas = shasWithIdeRefactoring(trace, reconstruction)

        // Pool size = 2 per concurrent RM call; the sliding window is
        // sequential here (probe walks dominate), so parallelism × 2
        // covers the single-call worst case without oversubscription.
        val poolSize = (parallelism * 2).coerceAtLeast(2)
        val worktreeBase = sessionFolder.resolve("refactoring-miner-worktrees")
        val pool = WorktreePool(reconstruction.repoDir, worktreeBase, poolSize)

        val steps = mutableListOf<RefactoringStep>()
        pool.use { pool ->
            var lIdx = 0
            var rIdx = 1
            while (rIdx < checkpoints.size) {
                val lSha = checkpoints[lIdx]
                val rSha = checkpoints[rIdx]
                val detections = runRM(pool, lSha, rSha)
                if (detections.isEmpty()) {
                    rIdx++
                    continue
                }

                val targetKeys = detections.map(::canonicalKey).toSet()
                var tightestLSha = lSha
                var probeIdx = lIdx + 1
                while (probeIdx < rIdx) {
                    val probeSha = checkpoints[probeIdx]
                    val probeKeys = runRM(pool, probeSha, rSha).map(::canonicalKey).toSet()
                    if (probeKeys == targetKeys) {
                        tightestLSha = probeSha
                        probeIdx++
                    } else break
                }

                val toCheckpointIndex = rIdx
                val timestamp = shaToTimestamp[rSha] ?: 0L
                // Borrow the pre-refactoring worktree once for the whole
                // detection batch so position-anchored specs can resolve
                // their AST-subtree hashes from the source at fromSha.
                val anchorWorktree = pool.borrow(tightestLSha)
                try {
                    val anchors = SpecAnchorBuilder(anchorWorktree)
                    for (d in detections) {
                        steps.add(
                            RefactoringStep(
                                stepIndex = steps.size,
                                fromSha = tightestLSha,
                                toSha = rSha,
                                toCheckpointIndex = toCheckpointIndex,
                                timestamp = timestamp,
                                refactoring = toDetected(d),
                                wasPerformedByIde = rSha in idePerformedShas,
                                spec = toSpec(d, anchors),
                            ),
                        )
                    }
                } finally {
                    pool.release(anchorWorktree)
                }

                lIdx = rIdx
                rIdx++
            }
        }

        return Summary(checkpointsAnalysed = checkpoints.size, steps = steps)
    }

    private fun runRM(pool: WorktreePool, fromSha: String, toSha: String): List<Refactoring> {
        // Borrows are nested so if the second borrow throws, the first one
        // is still released. A flat try { borrow; borrow } finally { release;
        // release } leaks the first slot on a failed second borrow, and the
        // pool eventually blocks forever.
        val prev = pool.borrow(fromSha)
        return try {
            val next = pool.borrow(toSha)
            try {
                val collected = mutableListOf<Refactoring>()
                // RM's default handleException is a no-op — override so internal failures
                // surface instead of being silently treated as "no refactorings".
                var captured: Exception? = null
                GitHistoryRefactoringMinerImpl().detectAtDirectories(
                    prev.toAbsolutePath(),
                    next.toAbsolutePath(),
                    object : RefactoringHandler {
                        override fun handle(commitId: String, refactorings: List<Refactoring>) {
                            collected.addAll(refactorings)
                        }
                        override fun handleException(commitId: String, e: Exception) {
                            captured = e
                        }
                    },
                )
                captured?.let { throw IllegalStateException("RefactoringMiner failed on $fromSha → $toSha", it) }
                collected
            } finally {
                pool.release(next)
            }
        } finally {
            pool.release(prev)
        }
    }

    private fun toDetected(r: Refactoring): DetectedRefactoring {
        val type = r.refactoringType.displayName
        return DetectedRefactoring(
            type = type,
            description = r.toString(),
            leftSideLocations = r.leftSide().map(::toLocation),
            rightSideLocations = r.rightSide().map(::toLocation),
            ideRelevant = IdeRelevantRefactorings.isIdeRelevant(type),
        )
    }

    /**
     * Maps an RM detection to a typed [RefactoringSpec] suitable for
     * driving `RefactoringClient`. Each IDE-relevant RM type gets its
     * own arm; everything else falls through to [RefactoringSpec.Other],
     * which `AlternativeTrajectoryRunner` skips with a clear reason.
     *
     * Typed arms are added incrementally — each commit covers one
     * refactoring kind (RM subclass → spec field extraction). Until
     * a kind has its arm, manual detections of that kind don't
     * synthesise an alternative.
     */
    private fun toSpec(r: Refactoring, anchors: SpecAnchorBuilder): RefactoringSpec = when (r) {
        is RenameOperationRefactoring -> RefactoringSpec.RenameMethod(
            declaringTypeFqn = r.originalOperation.className,
            oldName = r.originalOperation.name,
            newName = r.renamedOperation.name,
            // JDT can auto-disambiguate when the name is unique on the
            // declaring type. RM doesn't surface the JDT-encoded
            // signature; the rename op falls back to the unique-name
            // path, which covers the common case.
            paramTypeSignatures = null,
        )

        is RenameClassRefactoring -> RefactoringSpec.RenameClass(
            typeFqn = r.originalClassName,
            newName = r.renamedClassName.substringAfterLast('.'),
        )

        is RenameAttributeRefactoring -> RefactoringSpec.RenameField(
            declaringTypeFqn = r.classNameBefore,
            oldName = r.originalAttribute.name,
            newName = r.renamedAttribute.name,
        )

        is RenamePackageRefactoring -> RefactoringSpec.RenamePackage(
            // RM's RenamePattern strings are the matched prefix of the
            // package path with a trailing dot (e.g. `com.foo.`); strip
            // it so the JDT op gets a clean package FQN.
            oldPackage = r.pattern.before.trimEnd('.'),
            newPackage = r.pattern.after.trimEnd('.'),
        )

        // Pull Up / Push Down extend MoveOperation/MoveAttribute, so
        // they MUST be matched before their parents — otherwise a
        // pull-up gets routed through the move-op arm and synthesised
        // with the wrong JDT op.
        is PullUpOperationRefactoring -> RefactoringSpec.PullUp(
            // JDT's PullUp takes the *source* (subclass) FQN; methods
            // listed are pulled up to its supertype.
            declaringTypeFqn = r.originalOperation.className,
            methodNames = listOf(r.originalOperation.name),
        )

        is PullUpAttributeRefactoring -> RefactoringSpec.PullUp(
            declaringTypeFqn = r.originalAttribute.className,
            fieldNames = listOf(r.originalAttribute.name),
        )

        is PushDownOperationRefactoring -> RefactoringSpec.PushDown(
            // JDT's PushDown takes the *superclass* FQN where the
            // member currently lives; the op pushes it to subclasses.
            declaringTypeFqn = r.originalOperation.className,
            methodNames = listOf(r.originalOperation.name),
        )

        is PushDownAttributeRefactoring -> RefactoringSpec.PushDown(
            declaringTypeFqn = r.originalAttribute.className,
            fieldNames = listOf(r.originalAttribute.name),
        )

        // Move + Move-and-Rename — must come *after* the Pull/Push
        // arms above because PullUp/PushDown extend Move{Op,Attr}.
        is MoveAndRenameClassRefactoring -> RefactoringSpec.MoveAndRenameClass(
            typeFqn = r.originalClassName,
            destinationPackage = r.renamedClassName.substringBeforeLast('.', missingDelimiterValue = ""),
            newName = r.renamedClassName.substringAfterLast('.'),
        )

        is MoveClassRefactoring -> RefactoringSpec.MoveClass(
            typeFqn = r.originalClassName,
            destinationPackage = r.movedClassName.substringBeforeLast('.', missingDelimiterValue = ""),
        )

        is MoveAndRenameAttributeRefactoring -> RefactoringSpec.MoveAndRenameAttribute(
            sourceTypeFqn = r.originalAttribute.className,
            fieldName = r.originalAttribute.name,
            destinationTypeFqn = r.movedAttribute.className,
            newFieldName = r.movedAttribute.name,
        )

        is MoveAttributeRefactoring -> RefactoringSpec.MoveInstanceField(
            sourceTypeFqn = r.sourceClassName,
            fieldName = r.originalAttribute.name,
            destinationTypeFqn = r.targetClassName,
        )

        is ExtractOperationRefactoring -> {
            // RM's leftSide() returns BOTH the extracted code-fragments
            // AND the containing METHOD_DECLARATION. The latter is
            // context, not a selection — including its line range here
            // would balloon the bounding box to the whole source method
            // and JDT would reject the selection. Filter it out and
            // keep only the actual extracted fragments.
            val fragments = r.leftSide().filter { it.codeElementType.toString() != "METHOD_DECLARATION" }
            if (fragments.isEmpty()) RefactoringSpec.Other
            else {
                // Earliest start, latest end — JDT then needs every
                // statement strictly inside that range to be extractable
                // together (which is exactly what RM has already
                // verified by detecting the refactoring at all).
                val sorted = fragments.sortedWith(compareBy({ it.startLine }, { it.startColumn }))
                val first = sorted.first()
                val last = sorted.maxByOrNull { it.endLine * 10_000 + it.endColumn }!!
                val anchor = anchors.rangeAnchor(
                    first.filePath, first.startLine, first.startColumn, last.endLine, last.endColumn,
                )
                if (anchor == null) RefactoringSpec.Other
                else RefactoringSpec.ExtractMethod(
                    relativeFilePath = first.filePath,
                    declaringTypeFqn = anchor.declaringTypeFqn,
                    hostMethodName = anchor.hostMethodName,
                    hostMethodParamTypes = anchor.hostMethodParamTypes,
                    selectionSubtreeHash = anchor.selectionSubtreeHash,
                    selectionNodeCount = anchor.selectionNodeCount,
                    originalLineHint = first.startLine,
                    originalColumnHint = first.startColumn,
                    newMethodName = r.extractedOperation.name,
                )
            }
        }

        is InlineOperationRefactoring -> RefactoringSpec.InlineMethod(
            declaringTypeFqn = r.inlinedOperation.className,
            methodName = r.inlinedOperation.name,
        )

        is ExtractVariableRefactoring -> {
            // leftSide() points at the original expression's location;
            // its (line, col) range is what JDT's Extract Local
            // Variable selects.
            val left = r.leftSide().firstOrNull()
            if (left == null) RefactoringSpec.Other
            else {
                val anchor = anchors.rangeAnchor(
                    left.filePath, left.startLine, left.startColumn, left.endLine, left.endColumn,
                )
                if (anchor == null) RefactoringSpec.Other
                else RefactoringSpec.ExtractVariable(
                    relativeFilePath = left.filePath,
                    declaringTypeFqn = anchor.declaringTypeFqn,
                    hostMethodName = anchor.hostMethodName,
                    hostMethodParamTypes = anchor.hostMethodParamTypes,
                    selectionSubtreeHash = anchor.selectionSubtreeHash,
                    selectionNodeCount = anchor.selectionNodeCount,
                    originalLineHint = left.startLine,
                    originalColumnHint = left.startColumn,
                    newName = r.variableDeclaration.variableName,
                )
            }
        }

        is InlineVariableRefactoring -> {
            // The variable's declaration location is what JDT's Inline
            // Variable needs to find the symbol — leftSide first entry
            // covers the declaration site for inline detections.
            val loc = r.variableDeclaration.locationInfo
            val anchor = anchors.pointAnchor(loc.filePath, loc.startLine, loc.startColumn)
            if (anchor == null) RefactoringSpec.Other
            else RefactoringSpec.InlineVariable(
                relativeFilePath = loc.filePath,
                declaringTypeFqn = anchor.declaringTypeFqn,
                hostMethodName = anchor.hostMethodName,
                hostMethodParamTypes = anchor.hostMethodParamTypes,
                declarationSubtreeHash = anchor.declarationSubtreeHash,
                originalLineHint = loc.startLine,
                originalColumnHint = loc.startColumn,
            )
        }

        is ExtractAttributeRefactoring -> {
            val left = r.leftSide().firstOrNull()
            if (left == null) RefactoringSpec.Other
            else {
                val anchor = anchors.rangeAnchor(
                    left.filePath, left.startLine, left.startColumn, left.endLine, left.endColumn,
                )
                if (anchor == null) RefactoringSpec.Other
                else RefactoringSpec.ExtractAttribute(
                    relativeFilePath = left.filePath,
                    declaringTypeFqn = anchor.declaringTypeFqn,
                    hostMethodName = anchor.hostMethodName,
                    hostMethodParamTypes = anchor.hostMethodParamTypes,
                    selectionSubtreeHash = anchor.selectionSubtreeHash,
                    selectionNodeCount = anchor.selectionNodeCount,
                    originalLineHint = left.startLine,
                    originalColumnHint = left.startColumn,
                    newName = r.variableDeclaration.name,
                )
            }
        }

        is ChangeVariableTypeRefactoring -> {
            val loc = r.originalVariable.locationInfo
            val anchor = anchors.pointAnchor(loc.filePath, loc.startLine, loc.startColumn)
            if (anchor == null) RefactoringSpec.Other
            else RefactoringSpec.ChangeVariableType(
                relativeFilePath = loc.filePath,
                declaringTypeFqn = anchor.declaringTypeFqn,
                hostMethodName = anchor.hostMethodName,
                hostMethodParamTypes = anchor.hostMethodParamTypes,
                declarationSubtreeHash = anchor.declarationSubtreeHash,
                originalLineHint = loc.startLine,
                originalColumnHint = loc.startColumn,
                newTypeFqn = r.changedTypeVariable.type.toString(),
            )
        }

        is ChangeAttributeTypeRefactoring -> RefactoringSpec.ChangeAttributeType(
            declaringTypeFqn = r.classNameBefore,
            fieldName = r.originalAttribute.name,
            newTypeFqn = r.changedTypeAttribute.type.toString(),
        )

        is RenameVariableRefactoring -> {
            // RM bundles several IDE-relevant kinds into a single class;
            // the discriminator is its dynamically-computed
            // refactoringType. Each branch builds the matching spec.
            val loc = r.originalVariable.locationInfo
            val newName = r.renamedVariable.variableName
            when (r.refactoringType) {
                RefactoringType.RENAME_VARIABLE -> {
                    val a = anchors.pointAnchor(loc.filePath, loc.startLine, loc.startColumn)
                    if (a == null) RefactoringSpec.Other
                    else RefactoringSpec.RenameLocalVariable(
                        relativeFilePath = loc.filePath,
                        declaringTypeFqn = a.declaringTypeFqn,
                        hostMethodName = a.hostMethodName,
                        hostMethodParamTypes = a.hostMethodParamTypes,
                        declarationSubtreeHash = a.declarationSubtreeHash,
                        originalLineHint = loc.startLine,
                        originalColumnHint = loc.startColumn,
                        newName = newName,
                    )
                }

                RefactoringType.RENAME_PARAMETER -> {
                    val a = anchors.pointAnchor(loc.filePath, loc.startLine, loc.startColumn)
                    if (a == null) RefactoringSpec.Other
                    else RefactoringSpec.RenameParameter(
                        relativeFilePath = loc.filePath,
                        declaringTypeFqn = a.declaringTypeFqn,
                        hostMethodName = a.hostMethodName,
                        hostMethodParamTypes = a.hostMethodParamTypes,
                        declarationSubtreeHash = a.declarationSubtreeHash,
                        originalLineHint = loc.startLine,
                        originalColumnHint = loc.startColumn,
                        newName = newName,
                    )
                }

                RefactoringType.PARAMETERIZE_VARIABLE -> {
                    val a = anchors.rangeAnchor(
                        loc.filePath, loc.startLine, loc.startColumn, loc.endLine, loc.endColumn,
                    )
                    if (a == null) RefactoringSpec.Other
                    else RefactoringSpec.ParameterizeVariable(
                        relativeFilePath = loc.filePath,
                        declaringTypeFqn = a.declaringTypeFqn,
                        hostMethodName = a.hostMethodName,
                        hostMethodParamTypes = a.hostMethodParamTypes,
                        selectionSubtreeHash = a.selectionSubtreeHash,
                        selectionNodeCount = a.selectionNodeCount,
                        originalLineHint = loc.startLine,
                        originalColumnHint = loc.startColumn,
                        newParameterName = newName,
                    )
                }

                RefactoringType.PARAMETERIZE_ATTRIBUTE -> {
                    val a = anchors.rangeAnchor(
                        loc.filePath, loc.startLine, loc.startColumn, loc.endLine, loc.endColumn,
                    )
                    if (a == null) RefactoringSpec.Other
                    else RefactoringSpec.ParameterizeAttribute(
                        relativeFilePath = loc.filePath,
                        declaringTypeFqn = a.declaringTypeFqn,
                        hostMethodName = a.hostMethodName,
                        hostMethodParamTypes = a.hostMethodParamTypes,
                        selectionSubtreeHash = a.selectionSubtreeHash,
                        selectionNodeCount = a.selectionNodeCount,
                        originalLineHint = loc.startLine,
                        originalColumnHint = loc.startColumn,
                        newParameterName = newName,
                    )
                }

                RefactoringType.REPLACE_VARIABLE_WITH_ATTRIBUTE -> {
                    val a = anchors.pointAnchor(loc.filePath, loc.startLine, loc.startColumn)
                    if (a == null) RefactoringSpec.Other
                    else RefactoringSpec.ReplaceVariableWithAttribute(
                        relativeFilePath = loc.filePath,
                        declaringTypeFqn = a.declaringTypeFqn,
                        hostMethodName = a.hostMethodName,
                        hostMethodParamTypes = a.hostMethodParamTypes,
                        declarationSubtreeHash = a.declarationSubtreeHash,
                        originalLineHint = loc.startLine,
                        originalColumnHint = loc.startColumn,
                        newFieldName = newName,
                    )
                }

                else -> RefactoringSpec.Other
            }
        }

        // ── Change-signature family ─────────────────────────────────
        // RM emits one event per delta (one parameter added, one type
        // changed, …) but JDT's Change Method Signature op takes the
        // FULL desired parameter list — so we always rebuild it from
        // operationAfter. Existing-vs-Added is decided by name presence
        // in operationBefore.
        //
        // newReturnType uses JDT's "" sentinel = "leave unchanged" for
        // every refactoring that doesn't touch the return type;
        // ChangeReturnType passes the new type explicitly. Doing it
        // this way avoids needing to read the current return type
        // (VariableDeclarationContainer doesn't expose it directly —
        // only the UMLOperation subtype does).
        is AddParameterRefactoring -> changeMethodSignatureSpec(
            r.operationBefore, r.operationAfter, newReturnType = "",
        )

        is RemoveParameterRefactoring -> changeMethodSignatureSpec(
            r.operationBefore, r.operationAfter, newReturnType = "",
        )

        is ReorderParameterRefactoring -> changeMethodSignatureSpec(
            r.operationBefore, r.operationAfter, newReturnType = "",
        )

        is ChangeReturnTypeRefactoring -> changeMethodSignatureSpec(
            r.operationBefore, r.operationAfter, newReturnType = r.changedType.toString(),
        )

        // ── Extract Class ───────────────────────────────────────────
        // The "delegate field" is the new field on the original class
        // whose type is the extracted class — RM exposes it via
        // getAttributeOfExtractedClassTypeInOriginalClass(). When that
        // returns null (e.g. the extracted class is referenced only via
        // getters), JDT can't drive an extract-class without it, so we
        // fall through to Other.
        is ExtractClassRefactoring -> r.attributeOfExtractedClassTypeInOriginalClass?.let { delegate ->
            RefactoringSpec.ExtractClass(
                sourceTypeFqn = r.originalClass.name,
                newClassName = r.extractedClass.name.substringAfterLast('.'),
                delegateFieldName = delegate.name,
                fieldNames = r.extractedAttributes.keys.map { it.name },
            )
        } ?: RefactoringSpec.Other

        // ── Extract Superclass / Extract Interface ──────────────────
        // RM uses one class for both, discriminated by the extracted
        // class's `isInterface` flag.
        is ExtractSuperclassRefactoring -> r.subclassSetBefore.firstOrNull()?.let { sourceFqn ->
            val extracted = r.extractedClass
            val newSimpleName = extracted.name.substringAfterLast('.')
            val methodNames = extracted.operations.map { it.name }
            val fieldNames = extracted.attributes.map { it.name }
            if (extracted.isInterface) {
                RefactoringSpec.ExtractInterface(
                    sourceTypeFqn = sourceFqn,
                    newInterfaceName = newSimpleName,
                    methodNames = methodNames,
                )
            } else {
                RefactoringSpec.ExtractSuperclass(
                    sourceTypeFqn = sourceFqn,
                    newSupertypeName = newSimpleName,
                    methodNames = methodNames,
                    fieldNames = fieldNames,
                )
            }
        } ?: RefactoringSpec.Other

        else -> RefactoringSpec.Other
    }

    private fun changeMethodSignatureSpec(
        operationBefore: VariableDeclarationContainer,
        operationAfter: VariableDeclarationContainer,
        newReturnType: String,
    ): RefactoringSpec.ChangeMethodSignature {
        val beforeNames = operationBefore.parametersWithoutReturnType.map { it.name }.toSet()
        val parameters = operationAfter.parametersWithoutReturnType.map { p ->
            if (p.name in beforeNames) {
                RefactoringSpec.ChangeSignatureParameter.Existing(
                    oldName = p.name,
                    newName = p.name,
                    newType = p.type.toString(),
                )
            } else {
                RefactoringSpec.ChangeSignatureParameter.Added(
                    name = p.name,
                    type = p.type.toString(),
                    defaultValue = "",
                )
            }
        }
        return RefactoringSpec.ChangeMethodSignature(
            declaringTypeFqn = operationBefore.className,
            oldMethodName = operationBefore.name,
            paramTypeSignatures = null,
            newMethodName = "",
            newReturnType = newReturnType,
            parameters = parameters,
        )
    }

    private fun toLocation(c: CodeRange): RefactoringLocation = RefactoringLocation(
        key = codeRangeKey(c),
        filePath = c.filePath,
        startLine = c.startLine,
        endLine = c.endLine,
        startColumn = c.startColumn,
        endColumn = c.endColumn,
        codeElementType = c.codeElementType.toString(),
        codeElement = c.codeElement,
    )

    // Canonical key: type + sorted left location keys + sorted right location keys.
    // Same string form as RefactoringLocation.key so the "same refactoring"
    // relation used by the sliding window matches what a reader sees in the
    // serialized output.
    private fun canonicalKey(r: Refactoring): String {
        val left = r.leftSide().map(::codeRangeKey).sorted()
        val right = r.rightSide().map(::codeRangeKey).sorted()
        return "${r.refactoringType.displayName}|L=$left|R=$right"
    }

    private fun codeRangeKey(c: CodeRange): String =
        "${c.filePath}:${c.startLine}-${c.endLine} [${c.codeElementType}] ${c.codeElement}"

    private fun orderedUniqueShas(reconstruction: ReconstructionResult): List<String> =
        reconstruction.eventCommits.mapping.values.toCollection(LinkedHashSet()).toList()

    private fun shasWithIdeRefactoring(
        trace: Trace,
        reconstruction: ReconstructionResult,
    ): Set<String> {
        val mapping = reconstruction.eventCommits.mapping
        val out = HashSet<String>()
        for (event in trace.events) {
            if (event.type != EventType.REFACTORING_FINISHED) continue
            val sha = mapping[event.id] ?: continue
            out.add(sha)
        }
        return out
    }

    private fun firstTimestampPerSha(
        trace: Trace,
        reconstruction: ReconstructionResult,
    ): Map<String, Long> {
        val out = LinkedHashMap<String, Long>()
        val mapping = reconstruction.eventCommits.mapping
        for (event in trace.events) {
            val sha = mapping[event.id] ?: continue
            if (sha !in out) out[sha] = event.timestamp
        }
        return out
    }

    companion object {
        fun defaultParallelism(): Int =
            (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    }
}
