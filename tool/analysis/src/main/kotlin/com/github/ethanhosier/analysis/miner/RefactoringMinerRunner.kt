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
 * Algorithm (sliding window — both sides):
 *  - Grow `R` forward from `L = checkpoint 0` until `RM(L, R)` is non-empty.
 *  - **Extend `R`** forward while the canonical-keyed detection set stays
 *    equal — pushes the window's right edge to the latest checkpoint that
 *    still carries the same refactoring. Without this, partial-refactoring
 *    workflows like "delete method first, then inline call sites" lock
 *    the window at the first checkpoint where RM sees the inline, leaving
 *    later in-progress checkpoints out and forcing the synth's residual
 *    3-way merge to revert the IDE-applied refactoring back to the user's
 *    intermediate broken state.
 *  - **Shrink `L`** forward while the same set holds — locks onto the
 *    latest L that still represents the pre-refactoring state. Cheaper
 *    synth (smaller chunk to apply) and tighter residual.
 *  - Emit one [RefactoringStep] per detection, then restart with `L = R`.
 *
 * RM is invoked via [GitHistoryRefactoringMinerImpl.detectAtDirectories]
 * against two worktrees borrowed from [WorktreePool] at the two SHAs.
 */
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
        // SHAs that are intentional landing points — never extend the
        // detection window across them. IDE-performed checkpoints carry
        // a wasPerformedByIde attribution that extending past would
        // silently re-attribute to manual editing; user commits are the
        // user's "I'm done with this chunk" markers and the next stretch
        // of work is logically a separate refactoring.
        val windowBoundaries: Set<String> = idePerformedShas + userCommitShas

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

                // Extend R forward while the same detection set holds.
                // Catches workflows where the user lands the visible
                // signal of the refactoring early (e.g. deletes the
                // inlined method before finishing call-site bodies)
                // and then takes several more checkpoints to settle
                // — without this, the window locks at the first
                // checkpoint where RM sees the refactoring and the
                // synth's residual has to revert the alt back to a
                // half-done intermediate.
                var rExtIdx = rIdx
                var latestDetections = detections
                while (rExtIdx + 1 < checkpoints.size) {
                    // Stop at intentional landing points — never absorb
                    // an IDE-performed checkpoint or a user commit into
                    // a window whose initial R didn't include it.
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

                // Settle the refactoring forward: walk past the
                // canonical-key window using a coarser key (type +
                // declaration anchor) to find the latest checkpoint
                // where this is still the same logical operation. RM
                // detection stays anchored at (tightestLSha, tightestRSha)
                // — `settledSha` is a separate concept for the synth's
                // residual target. Stops at the same window boundaries
                // (IDE-performed / commit checkpoints).
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

                // From here on, use the extended-R detections so the
                // emitted step is keyed off the *final* refactored
                // state (positions, signatures, anchors) rather than
                // the first-detected one. Important for spec
                // resolution: refactoring locations report ranges in
                // the to-side; using the latest R gives the alt-synth
                // accurate target ranges to replay.
                val toCheckpointIndex = rExtIdx
                val timestamp = shaToTimestamp[tightestRSha] ?: 0L
                // Borrow the pre-refactoring worktree once for the whole
                // detection batch so position-anchored specs can resolve
                // their AST-subtree hashes from the source at fromSha.
                val anchorWorktree = pool.borrow(tightestLSha)
                try {
                    val anchors = SpecAnchorBuilder(anchorWorktree)
                    // Resolve specs first, then reorder by host-method
                    // dependencies — if any spec's host method is the
                    // target of an InlineMethod in the same bracket, it
                    // must apply before that inline. RM's enumeration
                    // order is not applyable in general; the reorderer
                    // converts it into a sequence the validator + reorder
                    // synth + alt-traj runner can replay sequentially.
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

        // Move Method — JDT's MoveInstanceMethod refactoring rewires the
        // method into the type of one of its parameters (or instance
        // fields). RM detects the move structurally; we recover the
        // target-parameter name by matching parameter types against the
        // moved operation's new className (FQN or simple). If no
        // parameter's type matches (e.g. a static-shaped move via a
        // wholly new field), fall through to Other so the synth skips
        // rather than guesses.
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
                    isStatic = r.extractedOperation.isStatic,
                )
            }
        }

        is InlineOperationRefactoring -> RefactoringSpec.InlineMethod(
            declaringTypeFqn = r.inlinedOperation.className,
            methodName = r.inlinedOperation.name,
        )

        is ExtractVariableRefactoring -> {
            // `leftSide()` aggregates the whole containing method, every
            // post-extract reference statement, and every block on the
            // way — not the expression we need to feed to JDT's Extract
            // Local Variable. The actual original sub-expression lives
            // in `subExpressionMappings`: each LeafMapping is
            // `original-expression → new-variable-initialiser`, so
            // fragment1's codeRange is the pre-state expression range.
            //
            // CodeRange-from-fragment uses 1-based **exclusive** endColumn
            // (the column one past the last char of the expression),
            // unlike RM's `leftSide()` CodeRanges which are inclusive.
            // `rangeAnchor` expects 1-based inclusive, so we subtract 1.
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

    /**
     * Coarse-grained identity for use in [run]'s extend-R / shrink-L
     * window probes. Strips per-call-site CodeRanges and keys only on
     * the refactoring's type + the first left-side CodeRange — which
     * is the *declaration* of the affected element (the inlined
     * method, the renamed attribute, etc.) and so stays stable as the
     * refactoring's consequential edits accumulate across multiple
     * checkpoints. With [canonicalKey] alone, a user manually inlining
     * call sites one EDIT_BURST at a time produces a sequence of
     * detections whose right-side ranges grow as more sites land,
     * which makes the window-extension predicate ("are the keys
     * equal?") spuriously false at every checkpoint and locks the
     * window at the first detection.
     */
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

    /**
     * SHAs whose events include a `GIT_COMMIT` — i.e. checkpoints the
     * user intentionally landed as a commit (not just an EDIT_BURST
     * checkpoint). Treated as window-extension boundaries: the
     * detection window should not cross a deliberate commit, since
     * commits are the user's semantic "I'm done with this" markers
     * and the next stretch of work is its own logical refactoring.
     */
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
