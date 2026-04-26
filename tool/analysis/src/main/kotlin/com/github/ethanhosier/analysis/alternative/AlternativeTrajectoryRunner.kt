package com.github.ethanhosier.analysis.alternative

import com.github.ethanhosier.analysis.metrics.WorktreePool
import com.github.ethanhosier.analysis.miner.model.RefactoringSpec
import com.github.ethanhosier.analysis.miner.model.RefactoringStep
import com.github.ethanhosier.analysis.model.ReconstructionResult
import com.github.ethanhosier.analysis.reconstruct.GitRunner
import com.github.ethanhosier.analysis.refactoring.RefactoringClient
import com.github.ethanhosier.analysis.refactoring.RefactoringOutcome
import com.github.ethanhosier.analysis.refactoring.ops.ChangeAttributeTypeRequest
import com.github.ethanhosier.analysis.refactoring.ops.ChangeMethodSignatureRequest
import com.github.ethanhosier.analysis.refactoring.ops.ChangeVariableTypeRequest
import com.github.ethanhosier.analysis.refactoring.ops.ExtractAttributeRequest
import com.github.ethanhosier.analysis.refactoring.ops.ExtractClassRequest
import com.github.ethanhosier.analysis.refactoring.ops.ExtractInterfaceRequest
import com.github.ethanhosier.analysis.refactoring.ops.ExtractMethodRequest
import com.github.ethanhosier.analysis.refactoring.ops.ExtractSuperclassRequest
import com.github.ethanhosier.analysis.refactoring.ops.ExtractVariableRequest
import com.github.ethanhosier.analysis.refactoring.ops.InlineMethodRequest
import com.github.ethanhosier.analysis.refactoring.ops.InlineVariableRequest
import com.github.ethanhosier.analysis.refactoring.ops.SignatureParameter
import com.github.ethanhosier.analysis.refactoring.ops.MoveAndRenameAttributeRequest
import com.github.ethanhosier.analysis.refactoring.ops.MoveAndRenameClassRequest
import com.github.ethanhosier.analysis.refactoring.ops.MoveClassRequest
import com.github.ethanhosier.analysis.refactoring.ops.MoveInstanceFieldRequest
import com.github.ethanhosier.analysis.refactoring.ops.ParameterizeAttributeRequest
import com.github.ethanhosier.analysis.refactoring.ops.ParameterizeVariableRequest
import com.github.ethanhosier.analysis.refactoring.ops.PullUpRequest
import com.github.ethanhosier.analysis.refactoring.ops.PushDownRequest
import com.github.ethanhosier.analysis.refactoring.ops.RenameClassRequest
import com.github.ethanhosier.analysis.refactoring.ops.RenameFieldRequest
import com.github.ethanhosier.analysis.refactoring.ops.RenameLocalVariableRequest
import com.github.ethanhosier.analysis.refactoring.ops.RenameMethodRequest
import com.github.ethanhosier.analysis.refactoring.ops.RenamePackageRequest
import com.github.ethanhosier.analysis.refactoring.ops.ReplaceVariableWithAttributeRequest
import com.github.ethanhosier.analysis.refactoring.ops.changeAttributeType
import com.github.ethanhosier.analysis.refactoring.ops.changeMethodSignature
import com.github.ethanhosier.analysis.refactoring.ops.changeVariableType
import com.github.ethanhosier.analysis.refactoring.ops.extractAttribute
import com.github.ethanhosier.analysis.refactoring.ops.extractClass
import com.github.ethanhosier.analysis.refactoring.ops.extractInterface
import com.github.ethanhosier.analysis.refactoring.ops.extractMethod
import com.github.ethanhosier.analysis.refactoring.ops.extractSuperclass
import com.github.ethanhosier.analysis.refactoring.ops.extractVariable
import com.github.ethanhosier.analysis.refactoring.ops.inlineMethod
import com.github.ethanhosier.analysis.refactoring.ops.inlineVariable
import com.github.ethanhosier.analysis.refactoring.ops.moveAndRenameAttribute
import com.github.ethanhosier.analysis.refactoring.ops.moveAndRenameClass
import com.github.ethanhosier.analysis.refactoring.ops.moveClass
import com.github.ethanhosier.analysis.refactoring.ops.moveInstanceField
import com.github.ethanhosier.analysis.refactoring.ops.parameterizeAttribute
import com.github.ethanhosier.analysis.refactoring.ops.parameterizeVariable
import com.github.ethanhosier.analysis.refactoring.ops.pullUp
import com.github.ethanhosier.analysis.refactoring.ops.pushDown
import com.github.ethanhosier.analysis.refactoring.ops.renameClass
import com.github.ethanhosier.analysis.refactoring.ops.renameField
import com.github.ethanhosier.analysis.refactoring.ops.renameLocalVariable
import com.github.ethanhosier.analysis.refactoring.ops.renameMethod
import com.github.ethanhosier.analysis.refactoring.ops.renamePackage
import com.github.ethanhosier.analysis.refactoring.ops.replaceVariableWithAttribute
import java.nio.file.Path
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * For every detected refactoring the user performed manually across more
 * than one checkpoint, synthesises the IDE-driven equivalent: borrows a
 * worktree at `fromSha`, runs the matching [RefactoringClient] op, and
 * commits the resulting tree as a new SHA on a synthetic
 * `alt/<stepIndex>` branch in the shadow repo.
 *
 * Trigger conditions for a candidate [RefactoringStep] (all required):
 *  - [RefactoringStep.wasPerformedByIde] is `false` — the user did this
 *    by hand, so an IDE-driven version is genuinely an alternative.
 *  - [RefactoringStep.spec] is non-null and not [RefactoringSpec.Other] —
 *    we have enough typed info to drive [RefactoringClient].
 *  - At least one intermediate unique SHA lies strictly between
 *    `fromSha` and `toSha` — i.e. the user took multiple commits to
 *    land it. Single-commit refactorings have no detour to collapse.
 *
 * Steps that fail to synthesise (typed dispatch arm missing,
 * [RefactoringClient] returns [RefactoringOutcome.Failed], or the op
 * produced no textual change) are recorded under [Summary.skipped] with
 * a short reason.
 */
class AlternativeTrajectoryRunner(
    private val refactoringClient: RefactoringClient,
    // Sensible Maven/Gradle defaults; override if a project lays out
    // its sources elsewhere. Classpath-extraction-from-Gradle is a
    // separate workstream — empty list here covers JRE-only fixtures
    // and is a reasonable degraded mode for real projects (refactorings
    // that need third-party types fail with a typed JDT error message
    // instead of a misleading-looking success).
    private val sourceFolders: List<String> = listOf("src/main/java"),
    private val classpathJars: List<Path> = emptyList(),
    private val parallelism: Int = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1),
) {

    data class Synthesised(
        val stepIndex: Int,
        val fromSha: String,
        val userToSha: String,
        val altSha: String,
        val branchRef: String,
    )

    data class Summary(
        // Steps that satisfied the trigger filter — synthesis was attempted.
        val candidates: Int,
        // Successful synthesis, sorted by [Synthesised.stepIndex].
        val synthesised: List<Synthesised>,
        // Keyed by [RefactoringStep.stepIndex]; reason is human-readable
        // (e.g. "dispatch arm not implemented for RenameMethod",
        // "RefactoringClient: <reason>", "refactoring produced no textual change").
        val skipped: Map<Int, String>,
    )

    fun run(
        reconstruction: ReconstructionResult,
        steps: List<RefactoringStep>,
        sessionFolder: Path,
    ): Summary {
        val orderedShas = reconstruction.eventCommits.mapping.values
            .toCollection(LinkedHashSet()).toList()
        val shaIndex = orderedShas.withIndex().associate { (i, sha) -> sha to i }

        // Per-step trigger logging. Each step gets one line so it's easy
        // to see at a glance why every candidate was kept or rejected —
        // the rejection reasons are otherwise invisible in the JSON
        // report.
        val candidates = mutableListOf<RefactoringStep>()
        for (s in steps) {
            val rejectReason = rejectReason(s, shaIndex)
            if (rejectReason == null) {
                candidates += s
                log("step ${s.stepIndex} ${s.refactoring.type}: candidate (spec=${s.spec!!::class.simpleName})")
            } else {
                log("step ${s.stepIndex} ${s.refactoring.type}: skipped — $rejectReason")
            }
        }
        if (candidates.isEmpty()) {
            return Summary(candidates = 0, synthesised = emptyList(), skipped = emptyMap())
        }

        val worktreeBase = sessionFolder.resolve("alternative-worktrees")
        val shadowGit = GitRunner(reconstruction.repoDir)
        val pool = WorktreePool(reconstruction.repoDir, worktreeBase, parallelism)
        val executor = Executors.newFixedThreadPool(parallelism)

        val synthesised = mutableListOf<Synthesised>()
        val skipped = sortedMapOf<Int, String>()
        val resultsLock = Any()

        try {
            val futures = candidates.map { step ->
                executor.submit<Unit> {
                    val outcome = synthesiseOne(step, pool, shadowGit)
                    synchronized(resultsLock) {
                        when (outcome) {
                            is OneResult.Ok -> {
                                synthesised += outcome.synth
                                log("step ${step.stepIndex}: synthesised → ${outcome.synth.altSha.take(10)} on ${outcome.synth.branchRef}")
                            }
                            is OneResult.Skipped -> {
                                skipped[step.stepIndex] = outcome.reason
                                log("step ${step.stepIndex}: synthesis failed — ${outcome.reason}")
                            }
                        }
                    }
                }
            }
            futures.forEach { f ->
                try {
                    f.get()
                } catch (e: ExecutionException) {
                    throw e.cause ?: e
                }
            }
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(1, TimeUnit.MINUTES)
            pool.close()
        }

        return Summary(
            candidates = candidates.size,
            synthesised = synthesised.sortedBy { it.stepIndex },
            skipped = skipped,
        )
    }

    /** Null when the step is a candidate; otherwise a human-readable
     *  reason it didn't qualify. */
    private fun rejectReason(s: RefactoringStep, shaIndex: Map<String, Int>): String? {
        if (s.wasPerformedByIde) return "performed by IDE (already an automated refactoring)"
        val spec = s.spec ?: return "no typed RefactoringSpec produced by the miner"
        if (spec is RefactoringSpec.Other) return "RefactoringSpec.Other (no RM-typed mapper for this kind)"
        val fromIdx = shaIndex[s.fromSha] ?: return "fromSha not in event-commits"
        val toIdx = shaIndex[s.toSha] ?: return "toSha not in event-commits"
        if (toIdx - fromIdx <= 1) return "fromSha and toSha are adjacent — single-step refactoring"
        return null
    }

    private fun synthesiseOne(
        step: RefactoringStep,
        pool: WorktreePool,
        shadowGit: GitRunner,
    ): OneResult {
        val worktree = pool.borrow(step.fromSha)
        try {
            val worktreeGit = GitRunner(worktree)
            // Without a local identity, `git commit` falls back to the
            // host's global config or fails outright; pin both so the
            // commit author is deterministic.
            worktreeGit.setLocalIdentity("alt@analysis.local", "Alternative Trajectory")

            val outcome = dispatch(step.spec!!, worktree)
            when (outcome) {
                is DispatchResult.Failed -> return OneResult.Skipped(outcome.reason)
                is DispatchResult.Ok -> Unit
            }

            worktreeGit.addAll()
            if (!worktreeGit.hasStagedChanges()) {
                return OneResult.Skipped("refactoring produced no textual change")
            }
            val altSha = worktreeGit.commit("alt: step ${step.stepIndex} ${step.refactoring.type}")
            val branchRef = "alt/${step.stepIndex}"
            shadowGit.branchForce(branchRef, altSha)

            return OneResult.Ok(
                Synthesised(
                    stepIndex = step.stepIndex,
                    fromSha = step.fromSha,
                    userToSha = step.toSha,
                    altSha = altSha,
                    branchRef = branchRef,
                ),
            )
        } finally {
            pool.release(worktree)
        }
    }

    private fun dispatch(spec: RefactoringSpec, worktree: Path): DispatchResult {
        val outcome: RefactoringOutcome = when (spec) {
            is RefactoringSpec.RenameMethod -> refactoringClient.renameMethod(
                RenameMethodRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    declaringTypeFqn = spec.declaringTypeFqn,
                    oldName = spec.oldName,
                    newName = spec.newName,
                    paramTypeSignatures = spec.paramTypeSignatures,
                ),
            )

            is RefactoringSpec.RenameClass -> refactoringClient.renameClass(
                RenameClassRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    typeFqn = spec.typeFqn,
                    newName = spec.newName,
                ),
            )

            is RefactoringSpec.RenameField -> refactoringClient.renameField(
                RenameFieldRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    declaringTypeFqn = spec.declaringTypeFqn,
                    oldName = spec.oldName,
                    newName = spec.newName,
                ),
            )

            is RefactoringSpec.RenamePackage -> refactoringClient.renamePackage(
                RenamePackageRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    oldPackage = spec.oldPackage,
                    newPackage = spec.newPackage,
                ),
            )

            is RefactoringSpec.MoveClass -> refactoringClient.moveClass(
                MoveClassRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    typeFqn = spec.typeFqn,
                    destinationPackage = spec.destinationPackage,
                ),
            )

            is RefactoringSpec.MoveAndRenameClass -> refactoringClient.moveAndRenameClass(
                MoveAndRenameClassRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    typeFqn = spec.typeFqn,
                    destinationPackage = spec.destinationPackage,
                    newName = spec.newName,
                ),
            )

            is RefactoringSpec.MoveInstanceField -> refactoringClient.moveInstanceField(
                MoveInstanceFieldRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    sourceTypeFqn = spec.sourceTypeFqn,
                    fieldName = spec.fieldName,
                    destinationTypeFqn = spec.destinationTypeFqn,
                ),
            )

            is RefactoringSpec.MoveAndRenameAttribute -> refactoringClient.moveAndRenameAttribute(
                MoveAndRenameAttributeRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    sourceTypeFqn = spec.sourceTypeFqn,
                    fieldName = spec.fieldName,
                    destinationTypeFqn = spec.destinationTypeFqn,
                    newFieldName = spec.newFieldName,
                ),
            )

            is RefactoringSpec.PullUp -> refactoringClient.pullUp(
                PullUpRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    declaringTypeFqn = spec.declaringTypeFqn,
                    methodNames = spec.methodNames,
                    fieldNames = spec.fieldNames,
                ),
            )

            is RefactoringSpec.PushDown -> refactoringClient.pushDown(
                PushDownRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    declaringTypeFqn = spec.declaringTypeFqn,
                    methodNames = spec.methodNames,
                    fieldNames = spec.fieldNames,
                ),
            )

            is RefactoringSpec.ExtractMethod -> refactoringClient.extractMethod(
                ExtractMethodRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    relativeFilePath = spec.relativeFilePath,
                    startLine = spec.startLine,
                    startColumn = spec.startColumn,
                    endLine = spec.endLine,
                    endColumn = spec.endColumn,
                    newMethodName = spec.newMethodName,
                ),
            )

            is RefactoringSpec.InlineMethod -> refactoringClient.inlineMethod(
                InlineMethodRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    declaringTypeFqn = spec.declaringTypeFqn,
                    methodName = spec.methodName,
                    paramTypeSignatures = spec.paramTypeSignatures,
                ),
            )

            is RefactoringSpec.ExtractVariable -> refactoringClient.extractVariable(
                ExtractVariableRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    relativeFilePath = spec.relativeFilePath,
                    startLine = spec.startLine,
                    startColumn = spec.startColumn,
                    endLine = spec.endLine,
                    endColumn = spec.endColumn,
                    newName = spec.newName,
                ),
            )

            is RefactoringSpec.InlineVariable -> refactoringClient.inlineVariable(
                InlineVariableRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    relativeFilePath = spec.relativeFilePath,
                    line = spec.line,
                    column = spec.column,
                ),
            )

            is RefactoringSpec.ExtractAttribute -> refactoringClient.extractAttribute(
                ExtractAttributeRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    relativeFilePath = spec.relativeFilePath,
                    startLine = spec.startLine,
                    startColumn = spec.startColumn,
                    endLine = spec.endLine,
                    endColumn = spec.endColumn,
                    newName = spec.newName,
                    visibility = spec.visibility,
                ),
            )

            is RefactoringSpec.ChangeVariableType -> refactoringClient.changeVariableType(
                ChangeVariableTypeRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    relativeFilePath = spec.relativeFilePath,
                    line = spec.line,
                    column = spec.column,
                    newTypeFqn = spec.newTypeFqn,
                ),
            )

            is RefactoringSpec.ChangeAttributeType -> refactoringClient.changeAttributeType(
                ChangeAttributeTypeRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    declaringTypeFqn = spec.declaringTypeFqn,
                    fieldName = spec.fieldName,
                    newTypeFqn = spec.newTypeFqn,
                ),
            )

            is RefactoringSpec.RenameLocalVariable -> refactoringClient.renameLocalVariable(
                RenameLocalVariableRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    relativeFilePath = spec.relativeFilePath,
                    line = spec.line,
                    column = spec.column,
                    newName = spec.newName,
                ),
            )

            // JDT's "Rename Local Variable" op handles parameters too —
            // the position picked the symbol regardless of declaration kind.
            is RefactoringSpec.RenameParameter -> refactoringClient.renameLocalVariable(
                RenameLocalVariableRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    relativeFilePath = spec.relativeFilePath,
                    line = spec.line,
                    column = spec.column,
                    newName = spec.newName,
                ),
            )

            is RefactoringSpec.ParameterizeVariable -> refactoringClient.parameterizeVariable(
                ParameterizeVariableRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    relativeFilePath = spec.relativeFilePath,
                    startLine = spec.startLine,
                    startColumn = spec.startColumn,
                    endLine = spec.endLine,
                    endColumn = spec.endColumn,
                    newParameterName = spec.newParameterName,
                ),
            )

            is RefactoringSpec.ParameterizeAttribute -> refactoringClient.parameterizeAttribute(
                ParameterizeAttributeRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    relativeFilePath = spec.relativeFilePath,
                    startLine = spec.startLine,
                    startColumn = spec.startColumn,
                    endLine = spec.endLine,
                    endColumn = spec.endColumn,
                    newParameterName = spec.newParameterName,
                ),
            )

            is RefactoringSpec.ReplaceVariableWithAttribute -> refactoringClient.replaceVariableWithAttribute(
                ReplaceVariableWithAttributeRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    relativeFilePath = spec.relativeFilePath,
                    line = spec.line,
                    column = spec.column,
                    newFieldName = spec.newFieldName,
                    visibility = spec.visibility,
                ),
            )

            is RefactoringSpec.ChangeMethodSignature -> refactoringClient.changeMethodSignature(
                ChangeMethodSignatureRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    declaringTypeFqn = spec.declaringTypeFqn,
                    oldMethodName = spec.oldMethodName,
                    paramTypeSignatures = spec.paramTypeSignatures,
                    newMethodName = spec.newMethodName,
                    newReturnType = spec.newReturnType,
                    parameters = spec.parameters.map { it.toOpsParameter() },
                ),
            )

            is RefactoringSpec.ExtractSuperclass -> refactoringClient.extractSuperclass(
                ExtractSuperclassRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    sourceTypeFqn = spec.sourceTypeFqn,
                    newSupertypeName = spec.newSupertypeName,
                    methodNames = spec.methodNames,
                    fieldNames = spec.fieldNames,
                ),
            )

            is RefactoringSpec.ExtractClass -> refactoringClient.extractClass(
                ExtractClassRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    sourceTypeFqn = spec.sourceTypeFqn,
                    newClassName = spec.newClassName,
                    delegateFieldName = spec.delegateFieldName,
                    fieldNames = spec.fieldNames,
                    createGetterSetter = spec.createGetterSetter,
                ),
            )

            is RefactoringSpec.ExtractInterface -> refactoringClient.extractInterface(
                ExtractInterfaceRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    sourceTypeFqn = spec.sourceTypeFqn,
                    newInterfaceName = spec.newInterfaceName,
                    methodNames = spec.methodNames,
                ),
            )

            // Each remaining kind gets its own arm as the matching
            // RM-typed mapper lands. Until then, candidates fall through
            // to "not implemented" and are skipped at synthesis time.
            else -> return DispatchResult.Failed(
                "dispatch arm not implemented for ${spec::class.simpleName}",
            )
        }
        return when (outcome) {
            is RefactoringOutcome.Success -> DispatchResult.Ok
            is RefactoringOutcome.Failed -> DispatchResult.Failed(
                "RefactoringClient: ${outcome.reason}",
            )
        }
    }

    private fun RefactoringSpec.ChangeSignatureParameter.toOpsParameter(): SignatureParameter = when (this) {
        is RefactoringSpec.ChangeSignatureParameter.Existing -> SignatureParameter.Existing(
            oldName = oldName,
            newName = newName,
            newType = newType,
        )
        is RefactoringSpec.ChangeSignatureParameter.Added -> SignatureParameter.Added(
            name = name,
            type = type,
            defaultValue = defaultValue,
        )
    }

    private sealed interface DispatchResult {
        data object Ok : DispatchResult
        data class Failed(val reason: String) : DispatchResult
    }

    private sealed interface OneResult {
        data class Ok(val synth: Synthesised) : OneResult
        data class Skipped(val reason: String) : OneResult
    }

    private fun log(msg: String) {
        System.err.println("[alt-traj] $msg")
    }
}
