package com.github.ethanhosier.analysis.alternative

import com.github.ethanhosier.analysis.metrics.WorktreePool
import com.github.ethanhosier.analysis.miner.model.RefactoringSpec
import com.github.ethanhosier.analysis.miner.model.RefactoringStep
import com.github.ethanhosier.analysis.model.ReconstructionResult
import com.github.ethanhosier.analysis.reconstruct.GitRunner
import com.github.ethanhosier.analysis.refactoring.RefactoringClient
import com.github.ethanhosier.analysis.refactoring.RefactoringOutcome
import com.github.ethanhosier.analysis.refactoring.ops.RenameClassRequest
import com.github.ethanhosier.analysis.refactoring.ops.RenameFieldRequest
import com.github.ethanhosier.analysis.refactoring.ops.RenameMethodRequest
import com.github.ethanhosier.analysis.refactoring.ops.RenamePackageRequest
import com.github.ethanhosier.analysis.refactoring.ops.renameClass
import com.github.ethanhosier.analysis.refactoring.ops.renameField
import com.github.ethanhosier.analysis.refactoring.ops.renameMethod
import com.github.ethanhosier.analysis.refactoring.ops.renamePackage
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

        val candidates = steps.filter { s -> isCandidate(s, shaIndex) }
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
                            is OneResult.Ok -> synthesised += outcome.synth
                            is OneResult.Skipped -> skipped[step.stepIndex] = outcome.reason
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

    private fun isCandidate(s: RefactoringStep, shaIndex: Map<String, Int>): Boolean {
        if (s.wasPerformedByIde) return false
        val spec = s.spec ?: return false
        if (spec is RefactoringSpec.Other) return false
        val fromIdx = shaIndex[s.fromSha] ?: return false
        val toIdx = shaIndex[s.toSha] ?: return false
        return toIdx - fromIdx > 1
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

    private sealed interface DispatchResult {
        data object Ok : DispatchResult
        data class Failed(val reason: String) : DispatchResult
    }

    private sealed interface OneResult {
        data class Ok(val synth: Synthesised) : OneResult
        data class Skipped(val reason: String) : OneResult
    }
}
