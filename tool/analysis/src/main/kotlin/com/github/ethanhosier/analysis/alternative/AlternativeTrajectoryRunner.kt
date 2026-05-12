package com.github.ethanhosier.analysis.alternative

import com.github.ethanhosier.analysis.alternative.validate.SpecDispatcher
import com.github.ethanhosier.analysis.metrics.WorktreePool
import com.github.ethanhosier.analysis.metrics.model.ResidualSummary
import com.github.ethanhosier.analysis.miner.model.RefactoringSpec
import com.github.ethanhosier.analysis.miner.model.RefactoringStep
import com.github.ethanhosier.analysis.model.ReconstructionResult
import com.github.ethanhosier.analysis.reconstruct.GitRunner
import com.github.ethanhosier.analysis.refactoring.RefactoringClient
import java.nio.file.Files
import java.nio.file.Path

/**
 * For every detected refactoring the user performed manually across more
 * than one checkpoint, synthesises the IDE-driven equivalent: borrows a
 * worktree at `fromSha`, runs the matching [RefactoringClient] op, and
 * commits the resulting tree as a new SHA on a synthetic `alt/group-N`
 * branch in the shadow repo.
 *
 * Refactorings sharing the same `(fromSha, toSha)` window — e.g. an
 * Extract Method plus a Move detected in the same edit burst — are
 * grouped into one synthesis job: each spec is applied in miner emission
 * order on the same worktree inside a single
 * [RefactoringClient.withBatchSession], then squashed into one alt-SHA.
 * This amortises Eclipse's project init+index cost and avoids the noise
 * mode where N independent alt branches each apply only one of N
 * detected refactorings.
 *
 * After the refactoring(s) land, a residual 3-way merge layers the
 * user's leftover edits on top: `diff(refactoring-only, userToSha)` →
 * `git apply --3way`. Clean apply ⇒ the alt-SHA carries both the IDE
 * refactoring and the user's unrelated edits, so any remaining
 * `alt → userToSha` divergence is the IDE-vs-manual delta itself.
 * Conflicted apply ⇒ alt-SHA stays at refactoring-only and the report
 * surfaces which files were dropped via [ResidualSummary].
 *
 * Trigger conditions for a candidate [RefactoringStep] (all required):
 *  - [RefactoringStep.wasPerformedByIde] is `false`.
 *  - [RefactoringStep.spec] is non-null and not [RefactoringSpec.Other].
 *  - At least one intermediate unique SHA lies strictly between
 *    `fromSha` and `toSha` — i.e. the user took multiple commits to
 *    land it.
 *
 * Steps that fail to synthesise (dispatch arm missing, [RefactoringClient]
 * returns Failed, or the op produced no textual change) are recorded
 * under [Summary.skipped] with a short reason. When a group has some
 * succeeding and some failing steps, the succeeding ones land on the
 * alt-SHA and the failing ones still appear in [Summary.skipped].
 *
 * Concurrency: single-threaded. `RefactoringClient.invokeOnBundle`
 * already serialises every JDT call under a coarse `ReentrantLock`, so
 * parallel synthesis was bottlenecked on the bundle lock regardless.
 * Sequential + `withBatchSession` is strictly faster for multi-spec
 * groups (one index init per group instead of one per spec) and
 * identical for single-spec groups.
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
) {

    private val dispatcher = SpecDispatcher(refactoringClient, sourceFolders, classpathJars)

    /**
     * One synthesised alt produced by grouping every [RefactoringStep]
     * that shares a `(fromSha, toSha)` window. [stepIndexes] is in the
     * order the specs were applied (miner emission order). [altShas] is
     * a chain of commits — one per applied refactoring, plus an
     * optional trailing residual SHA when the residual 3-way merge
     * landed cleanly with non-zero churn. Each SHA is parented on the
     * previous SHA in the chain (or on [fromSha] for the first), and
     * has a matching entry in [branchRefs].
     *
     * Length invariant:
     *  - `altShas.size == stepIndexes.size` when residual was empty
     *    or conflicted (no residual step).
     *  - `altShas.size == stepIndexes.size + 1` when residual landed
     *    cleanly — the trailing SHA carries the user's leftover edits
     *    layered on top of the last refactoring SHA.
     */
    data class SynthesisedGroup(
        val stepIndexes: List<Int>,
        val fromSha: String,
        val userToSha: String,
        val altShas: List<String>,
        val branchRefs: List<String>,
        val residual: ResidualSummary,
    )

    data class Summary(
        // Number of candidate groups (post-filter), not steps.
        val candidates: Int,
        // Successful groups, sorted by min stepIndex per group.
        val synthesised: List<SynthesisedGroup>,
        // Keyed by [RefactoringStep.stepIndex]; reason is human-readable
        // (e.g. "dispatch arm not implemented for RenameMethod",
        // "RefactoringClient: <reason>", "refactoring produced no textual change",
        // "failed inside group at <fromSha> after step N").
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
        val candidateSteps = mutableListOf<RefactoringStep>()
        for (s in steps) {
            val rejectReason = rejectReason(s, shaIndex)
            if (rejectReason == null) {
                candidateSteps += s
            } else {
                log("step ${s.stepIndex} ${s.refactoring.type}: skipped — $rejectReason")
            }
        }
        if (candidateSteps.isEmpty()) {
            return Summary(candidates = 0, synthesised = emptyList(), skipped = emptyMap())
        }

        // Group co-located refactorings. Miner emission order is
        // preserved within each group by sorting on stepIndex — RM
        // assigns stepIndex monotonically in detection order.
        val groups = candidateSteps
            .groupBy { it.fromSha to it.toSha }
            .map { (key, members) ->
                Group(
                    fromSha = key.first,
                    toSha = key.second,
                    steps = members.sortedBy { it.stepIndex },
                )
            }
            .sortedBy { it.steps.first().stepIndex }

        val worktreeBase = sessionFolder.resolve("alternative-worktrees")
        val shadowGit = GitRunner(reconstruction.repoDir)
        // Pool size 1: synthesis is sequential, so we only ever need
        // one worktree at a time. Keeping the pool keeps the borrow/
        // release lifecycle consistent with other synthesisers.
        val pool = WorktreePool(reconstruction.repoDir, worktreeBase, size = 1)

        val synthesised = mutableListOf<SynthesisedGroup>()
        val skipped = sortedMapOf<Int, String>()

        try {
            for (group in groups) {
                when (val outcome = synthesiseGroup(group, pool, shadowGit)) {
                    is GroupResult.Ok -> synthesised += outcome.synth
                    is GroupResult.PartialOk -> {
                        synthesised += outcome.synth
                        outcome.failedStepReasons.forEach { (idx, reason) ->
                            skipped[idx] = reason
                            log("step $idx: failed inside group at ${group.fromSha.take(7)} — $reason")
                        }
                    }
                    is GroupResult.Skipped -> {
                        group.steps.forEach { skipped[it.stepIndex] = outcome.reason }
                        log(
                            "group at ${group.fromSha.take(7)} → ${group.toSha.take(7)}: " +
                                "skipped — ${outcome.reason}",
                        )
                    }
                }
            }
        } finally {
            pool.close()
        }

        return Summary(
            candidates = groups.size,
            synthesised = synthesised.sortedBy { it.stepIndexes.first() },
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

    private fun synthesiseGroup(
        group: Group,
        pool: WorktreePool,
        shadowGit: GitRunner,
    ): GroupResult {
        val worktree = pool.borrow(group.fromSha)
        try {
            val worktreeGit = GitRunner(worktree)
            // Without a local identity, `git commit` falls back to the
            // host's global config or fails outright; pin both so the
            // commit author is deterministic.
            worktreeGit.setLocalIdentity("alt@analysis.local", "Alternative Trajectory")

            // Apply + commit per step so each refactoring renders as its
            // own node on the chart. Eclipse's project init is held open
            // across the loop by `withBatchSession`; the per-step git
            // operations run after each apply (outside any JDT call), so
            // there's no contention with the bundle's cached project.
            val appliedSteps = mutableListOf<RefactoringStep>()
            val stepShas = mutableListOf<String>()
            val stepBranchRefs = mutableListOf<String>()
            val failed = sortedMapOf<Int, String>()
            val groupId = group.steps.first().stepIndex

            refactoringClient.withBatchSession {
                for (step in group.steps) {
                    when (val outcome = dispatcher.apply(step.spec!!, worktree)) {
                        is SpecDispatcher.Result.Failed -> {
                            failed[step.stepIndex] = outcome.reason
                            continue
                        }
                        is SpecDispatcher.Result.Ok -> Unit
                    }
                    worktreeGit.addAll()
                    if (!worktreeGit.hasStagedChanges()) {
                        // Spec accepted by JDT but the worktree is
                        // byte-identical — count as a failure for this
                        // step so the report makes it visible.
                        failed[step.stepIndex] = "refactoring produced no textual change"
                        continue
                    }
                    val stepSha = worktreeGit.commit(
                        "alt: step ${step.stepIndex} ${step.refactoring.type}",
                    )
                    val stepBranch = "alt/group-$groupId/${appliedSteps.size}"
                    shadowGit.branchForce(stepBranch, stepSha)
                    appliedSteps += step
                    stepShas += stepSha
                    stepBranchRefs += stepBranch
                }
            }

            if (appliedSteps.isEmpty()) {
                return GroupResult.Skipped(
                    "no step in group applied: ${failed.values.joinToString("; ")}",
                )
            }

            // Residual 3-way merge layered on top of the last
            // refactoring SHA. Clean + non-empty ⇒ commits a final
            // residual SHA as its own node on the alt path. Conflicted
            // ⇒ reset back to the last refactoring SHA so the chain
            // still represents a valid IDE outcome (just without the
            // user's leftover edits). Empty ⇒ no extra commit, chain
            // ends at the last refactoring SHA.
            val lastRefactoringSha = stepShas.last()
            val residualOutcome = applyResidual(
                worktreeGit = worktreeGit,
                refactoringOnlySha = lastRefactoringSha,
                userToSha = group.toSha,
            )

            when (residualOutcome) {
                is ResidualOutcome.Clean -> {
                    if (residualOutcome.added == 0 && residualOutcome.deleted == 0) {
                        // 3-way merge succeeded but landed nothing
                        // textual — treat as Empty for chart purposes.
                    } else {
                        val residualSha = worktreeGit.commit(
                            "alt: group $groupId residual cleanup",
                        )
                        val residualBranch = "alt/group-$groupId/residual"
                        shadowGit.branchForce(residualBranch, residualSha)
                        stepShas += residualSha
                        stepBranchRefs += residualBranch
                    }
                }
                is ResidualOutcome.Conflicted -> {
                    worktreeGit.resetHard(lastRefactoringSha)
                }
                is ResidualOutcome.Empty -> Unit
            }

            val synth = SynthesisedGroup(
                stepIndexes = appliedSteps.map { it.stepIndex },
                fromSha = group.fromSha,
                userToSha = group.toSha,
                altShas = stepShas.toList(),
                branchRefs = stepBranchRefs.toList(),
                residual = residualOutcome.summary,
            )
            return if (failed.isEmpty()) {
                GroupResult.Ok(synth)
            } else {
                GroupResult.PartialOk(synth, failed.toMap())
            }
        } finally {
            pool.release(worktree)
        }
    }

    private fun applyResidual(
        worktreeGit: GitRunner,
        refactoringOnlySha: String,
        userToSha: String,
    ): ResidualOutcome {
        val patch = worktreeGit.diffPatch(refactoringOnlySha, userToSha)
        if (patch.isBlank()) {
            return ResidualOutcome.Empty
        }

        val patchFile = Files.createTempFile("alt-residual-", ".patch")
        try {
            Files.writeString(patchFile, patch)
            return when (val applyResult = worktreeGit.applyThreeWay(patchFile)) {
                is GitRunner.ApplyResult.Ok ->
                    ResidualOutcome.Clean(added = applyResult.added, deleted = applyResult.deleted)
                is GitRunner.ApplyResult.Conflict ->
                    ResidualOutcome.Conflicted(
                        rejectedFiles = applyResult.rejectedFiles,
                        droppedAdded = applyResult.added,
                        droppedDeleted = applyResult.deleted,
                    )
            }
        } finally {
            Files.deleteIfExists(patchFile)
        }
    }

    private data class Group(
        val fromSha: String,
        val toSha: String,
        val steps: List<RefactoringStep>,
    )

    private sealed interface GroupResult {
        data class Ok(val synth: SynthesisedGroup) : GroupResult
        /** Some steps in the group failed but at least one applied,
         *  so the alt-SHA still exists. Per-step failures are surfaced
         *  to the caller via [failedStepReasons] keyed by stepIndex. */
        data class PartialOk(
            val synth: SynthesisedGroup,
            val failedStepReasons: Map<Int, String>,
        ) : GroupResult
        /** Nothing in the group landed — either every spec failed,
         *  no textual change resulted, or applies threw a wholesale
         *  error. Every step in the group is added to [Summary.skipped]
         *  with this reason. */
        data class Skipped(val reason: String) : GroupResult
    }

    private sealed interface ResidualOutcome {
        val summary: ResidualSummary

        object Empty : ResidualOutcome {
            override val summary = ResidualSummary(
                applied = true,
                addedLines = 0,
                deletedLines = 0,
                rejectedFiles = emptyList(),
            )
        }

        data class Clean(val added: Int, val deleted: Int) : ResidualOutcome {
            override val summary = ResidualSummary(
                applied = true,
                addedLines = added,
                deletedLines = deleted,
                rejectedFiles = emptyList(),
            )
        }

        data class Conflicted(
            val rejectedFiles: List<String>,
            val droppedAdded: Int,
            val droppedDeleted: Int,
        ) : ResidualOutcome {
            override val summary = ResidualSummary(
                applied = false,
                addedLines = droppedAdded,
                deletedLines = droppedDeleted,
                rejectedFiles = rejectedFiles,
            )
        }
    }

    private fun log(msg: String) {
        System.err.println("[alt-traj] $msg")
    }
}
