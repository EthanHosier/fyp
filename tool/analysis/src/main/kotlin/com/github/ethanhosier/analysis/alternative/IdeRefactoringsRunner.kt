package com.github.ethanhosier.analysis.alternative

import com.github.ethanhosier.analysis.alternative.validate.SpecDispatcher
import com.github.ethanhosier.analysis.metrics.WorktreePool
import com.github.ethanhosier.analysis.metrics.model.ResidualSummary
import com.github.ethanhosier.analysis.miner.model.RefactoringSpec
import com.github.ethanhosier.analysis.miner.model.RefactoringStep
import com.github.ethanhosier.analysis.model.ReconstructionResult
import com.github.ethanhosier.analysis.reconstruct.GitRunner
import com.github.ethanhosier.analysis.refactoring.RefactoringClient
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path

class IdeRefactoringsRunner(
    private val refactoringClient: RefactoringClient,
    private val sourceFolders: List<String> = listOf("src/main/java"),
    private val classpathJars: List<Path> = emptyList(),
) {

    private val dispatcher = SpecDispatcher(refactoringClient, sourceFolders, classpathJars)

    @Serializable
    data class SynthesisedGroup(
        val stepIndexes: List<Int>,
        val fromSha: String,
        val userToSha: String,
        val altShas: List<String>,
        val branchRefs: List<String>,
        val residual: ResidualSummary,
    )

    @Serializable
    data class Summary(
        // Number of candidate groups (post-filter), not steps.
        val candidates: Int,
        // Successful groups, sorted by min stepIndex per group.
        val synthesised: List<SynthesisedGroup>,
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

        val groups = candidateSteps
            .groupBy { it.fromSha to it.toSha }
            .map { (key, members) ->
                val ordered = members.sortedBy { it.stepIndex }
                Group(
                    fromSha = key.first,
                    toSha = key.second,
                    settledSha = ordered.map { it.settledSha }.lastOrNull() ?: key.second,
                    steps = ordered,
                )
            }
            .sortedBy { it.steps.first().stepIndex }

        val worktreeBase = sessionFolder.resolve("alternative-worktrees")
        val shadowGit = GitRunner(reconstruction.repoDir)
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

    private fun rejectReason(s: RefactoringStep, shaIndex: Map<String, Int>): String? {
        if (s.wasPerformedByIde) return "performed by IDE (already an automated refactoring)"
        val spec = s.spec ?: return "no typed RefactoringSpec produced by the miner"
        if (spec is RefactoringSpec.Other) return "RefactoringSpec.Other (no RM-typed mapper for this kind)"
        if (s.fromSha !in shaIndex) return "fromSha not in event-commits"
        if (s.toSha !in shaIndex) return "toSha not in event-commits"
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
            worktreeGit.setLocalIdentity("alt@analysis.local", "Alternative Trajectory")

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
                    worktreeGit.addAllExcept(".project", ".classpath", ".settings")
                    if (!worktreeGit.hasStagedChanges()) {
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

            val lastRefactoringSha = stepShas.last()
            val residualOutcome = applyResidual(
                worktreeGit = worktreeGit,
                refactoringOnlySha = lastRefactoringSha,
                userToSha = group.settledSha,
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
                userToSha = group.settledSha,
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
        val settledSha: String,
        val steps: List<RefactoringStep>,
    )

    private sealed interface GroupResult {
        data class Ok(val synth: SynthesisedGroup) : GroupResult
        data class PartialOk(
            val synth: SynthesisedGroup,
            val failedStepReasons: Map<Int, String>,
        ) : GroupResult
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
