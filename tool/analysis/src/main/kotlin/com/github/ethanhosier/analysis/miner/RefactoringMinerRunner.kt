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
import gr.uom.java.xmi.diff.MoveOperationRefactoring
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
import kotlinx.serialization.Serializable
import java.nio.file.Path

class RefactoringMinerRunner(
    private val parallelism: Int = defaultParallelism(),
) {

    @Serializable
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
        val userCommitShas = shasWithUserCommit(trace, reconstruction)
        val windowBoundaries: Set<String> = idePerformedShas + userCommitShas

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

                var rExtIdx = rIdx
                var latestDetections = detections
                while (rExtIdx + 1 < checkpoints.size) {
                    if (checkpoints[rExtIdx] in windowBoundaries) break
                    val nextSha = checkpoints[rExtIdx + 1]
                    val nextDetections = runRM(pool, lSha, nextSha)
                    val nextKeys = nextDetections.map(::canonicalKey).toSet()
                    if (nextKeys != targetKeys) break
                    rExtIdx++
                    latestDetections = nextDetections
                }
                val tightestRSha = checkpoints[rExtIdx]

                var tightestLSha = lSha
                var probeIdx = lIdx + 1
                while (probeIdx < rExtIdx) {
                    val probeSha = checkpoints[probeIdx]
                    val probeKeys = runRM(pool, probeSha, tightestRSha).map(::canonicalKey).toSet()
                    if (probeKeys == targetKeys) {
                        tightestLSha = probeSha
                        probeIdx++
                    } else break
                }

                val coarseTargetKeys = latestDetections.map(::coarseKey).toSet()
                var settledIdx = rExtIdx
                while (settledIdx + 1 < checkpoints.size) {
                    if (checkpoints[settledIdx] in windowBoundaries) break
                    val probeSha = checkpoints[settledIdx + 1]
                    val probeCoarse = runRM(pool, tightestLSha, probeSha)
                        .map(::coarseKey)
                        .toSet()
                    if (probeCoarse != coarseTargetKeys) break
                    settledIdx++
                }
                val settledSha = checkpoints[settledIdx]

                val toCheckpointIndex = rExtIdx
                val timestamp = shaToTimestamp[tightestRSha] ?: 0L
                val anchorWorktree = pool.borrow(tightestLSha)
                try {
                    val anchors = SpecAnchorBuilder(anchorWorktree)
                    val resolvedSpecs = latestDetections.map { toSpec(it, anchors) }
                    val permutation = BracketSpecReorderer.reorder(resolvedSpecs)
                    for (idx in permutation) {
                        val d = latestDetections[idx]
                        steps.add(
                            RefactoringStep(
                                stepIndex = steps.size,
                                fromSha = tightestLSha,
                                toSha = tightestRSha,
                                toCheckpointIndex = toCheckpointIndex,
                                timestamp = timestamp,
                                refactoring = toDetected(d),
                                wasPerformedByIde = tightestRSha in idePerformedShas,
                                settledSha = settledSha,
                                spec = resolvedSpecs[idx],
                            ),
                        )
                    }
                } finally {
                    pool.release(anchorWorktree)
                }

                lIdx = rExtIdx
                rIdx = rExtIdx + 1
            }
        }

        return Summary(checkpointsAnalysed = checkpoints.size, steps = steps)
    }

    private fun runRM(pool: WorktreePool, fromSha: String, toSha: String): List<Refactoring> {
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

    private fun toSpec(r: Refactoring, anchors: SpecAnchorBuilder): RefactoringSpec = when (r) {
        is RenameOperationRefactoring -> RefactoringSpec.RenameMethod(
            declaringTypeFqn = r.originalOperation.className,
            oldName = r.originalOperation.name,
            newName = r.renamedOperation.name,
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
            oldPackage = r.pattern.before.trimEnd('.'),
            newPackage = r.pattern.after.trimEnd('.'),
        )

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

        is MoveOperationRefactoring -> {
            val sourceFqn = r.originalOperation.className
            val destFqn = r.movedOperation.className
            val destSimple = destFqn.substringAfterLast('.')
            val target = r.originalOperation.parameterDeclarationList
                .firstOrNull { p ->
                    val typeStr = p.type?.toString() ?: ""
                    typeStr == destFqn || typeStr == destSimple
                }
                ?.variableName
            if (target == null) RefactoringSpec.Other
            else RefactoringSpec.MoveInstanceMethod(
                sourceTypeFqn = sourceFqn,
                methodName = r.originalOperation.name,
                targetName = target,
            )
        }

        is ExtractOperationRefactoring -> {
            val fragments = r.leftSide().filter { it.codeElementType.toString() != "METHOD_DECLARATION" }
            if (fragments.isEmpty()) RefactoringSpec.Other
            else {
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
                    isStatic = r.extractedOperation.isStatic,
                )
            }
        }

        is InlineOperationRefactoring -> RefactoringSpec.InlineMethod(
            declaringTypeFqn = r.inlinedOperation.className,
            methodName = r.inlinedOperation.name,
        )

        is ExtractVariableRefactoring -> {
            val expr = r.subExpressionMappings.firstOrNull()?.fragment1?.codeRange()
            if (expr == null) RefactoringSpec.Other
            else {
                val endColInclusive = (expr.endColumn - 1).coerceAtLeast(expr.startColumn)
                val anchor = anchors.rangeAnchor(
                    expr.filePath, expr.startLine, expr.startColumn, expr.endLine, endColInclusive,
                )
                if (anchor == null) RefactoringSpec.Other
                else RefactoringSpec.ExtractVariable(
                    relativeFilePath = expr.filePath,
                    declaringTypeFqn = anchor.declaringTypeFqn,
                    hostMethodName = anchor.hostMethodName,
                    hostMethodParamTypes = anchor.hostMethodParamTypes,
                    selectionSubtreeHash = anchor.selectionSubtreeHash,
                    selectionNodeCount = anchor.selectionNodeCount,
                    originalLineHint = expr.startLine,
                    originalColumnHint = expr.startColumn,
                    newName = r.variableDeclaration.variableName,
                )
            }
        }

        is InlineVariableRefactoring -> {
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

        is ExtractClassRefactoring -> r.attributeOfExtractedClassTypeInOriginalClass?.let { delegate ->
            RefactoringSpec.ExtractClass(
                sourceTypeFqn = r.originalClass.name,
                newClassName = r.extractedClass.name.substringAfterLast('.'),
                delegateFieldName = delegate.name,
                fieldNames = r.extractedAttributes.keys.map { it.name },
            )
        } ?: RefactoringSpec.Other

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

    private fun canonicalKey(r: Refactoring): String {
        val left = r.leftSide().map(::codeRangeKey).sorted()
        val right = r.rightSide().map(::codeRangeKey).sorted()
        return "${r.refactoringType.displayName}|L=$left|R=$right"
    }

    private fun coarseKey(r: Refactoring): String {
        val firstLeft = r.leftSide().firstOrNull()?.let(::codeRangeKey) ?: ""
        return "${r.refactoringType.displayName}|L0=$firstLeft"
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

    private fun shasWithUserCommit(
        trace: Trace,
        reconstruction: ReconstructionResult,
    ): Set<String> {
        val mapping = reconstruction.eventCommits.mapping
        val out = HashSet<String>()
        for (event in trace.events) {
            if (event.type != EventType.GIT_COMMIT) continue
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
