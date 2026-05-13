package com.github.ethanhosier.analysis.alternative.rework

import com.github.ethanhosier.analysis.metrics.WorktreePool
import com.github.ethanhosier.analysis.model.ReconstructionResult
import com.github.ethanhosier.analysis.reconstruct.GitRunner
import java.nio.file.Files
import java.nio.file.Path

/**
 * Synthesises rework alternative trajectories. For each detected
 * rework chunk pair (originating step `k'`, terminal step `k`), this
 * runner:
 *
 *  1. Borrows a worktree anchored at `preSha(k')`.
 *  2. Asks [ReworkAlternativeBuilder.plan] for the per-step synth
 *     patches over `[k', k]`.
 *  3. Applies each non-empty plan step via [GitRunner.applyDirect]
 *     (no `--3way`), commits, branchForces a ref.
 *  4. Any apply failure aborts the whole alt — atomic per the v1
 *     no-partial-trajectory rule.
 *
 * Outputs [SynthesisedRework] records the pipeline can convert into
 * `AlternativeTrajectory` entries.
 */
class ReworkSynthesiser {

    data class Summary(
        val candidates: Int,
        val synthesised: List<SynthesisedRework>,
        val failed: Map<String, String>,
    )

    data class SynthesisedRework(
        val fromSha: String,
        val userToSha: String,
        val altShas: List<String>,
        val branchRefs: List<String>,
        // Display metadata:
        val originatingStep: Int,
        val terminalStep: Int,
        val file: String,
        val scopeId: String,
        val direction: ReworkDetector.Direction,
        val rawLineCount: Int,
        val contentSummary: String,
    )

    fun run(
        reconstruction: ReconstructionResult,
        sessionFolder: Path,
        minNormalizedLineCount: Int = ReworkDetector.DEFAULT_MIN_NORMALIZED_LINE_COUNT,
    ): Summary {
        val shadowGit = GitRunner(reconstruction.repoDir)
        val orderedShas = reconstruction.eventCommits.mapping.values
            .toCollection(LinkedHashSet()).toList()
        if (orderedShas.size < 2) {
            log("rework: <2 shas — skipping")
            return Summary(0, emptyList(), emptyMap())
        }

        val stepInputs = buildStepInputs(shadowGit, orderedShas)
        val pairs = ReworkDetector.detectChunkPairs(stepInputs, minNormalizedLineCount)
        log("rework: ${pairs.size} candidate chunk pair(s) over ${stepInputs.size} steps (minNorm=$minNormalizedLineCount)")
        for (p in pairs) {
            log("  candidate: ${p.direction} step ${p.originatingStep}->${p.terminalStep} ${p.file} ${p.scopeId}  raw=${p.rawLineCount} norm=${p.normalizedLineCount}")
        }
        if (pairs.isEmpty()) return Summary(0, emptyList(), emptyMap())

        val worktreeBase = sessionFolder.resolve("rework-worktrees")
        val pool = WorktreePool(reconstruction.repoDir, worktreeBase, size = 1)

        val synthesised = mutableListOf<SynthesisedRework>()
        val failed = sortedMapOf<String, String>()
        try {
            for ((idx, pair) in pairs.withIndex()) {
                val key = "rework-$idx ${pair.file}:${pair.originatingStep}->${pair.terminalStep}"
                val fromSha = orderedShas.getOrNull(pair.originatingStep)
                val userToSha = orderedShas.getOrNull(pair.terminalStep + 1)
                if (fromSha == null || userToSha == null) {
                    failed[key] = "missing fromSha/userToSha (originating=${pair.originatingStep}, terminal=${pair.terminalStep})"
                    log("$key: skipped — ${failed[key]}")
                    continue
                }

                // Only feed the planner steps in [originatingStep, terminalStep]
                // — past-terminal renumbering is identity (zone cleared)
                // and adds no signal to the alt.
                val relevantSteps = stepInputs
                    .filter { it.stepIndex in pair.originatingStep..pair.terminalStep }
                    .map { ReworkAlternativeBuilder.StepInput(it.stepIndex, it.patch) }

                val plan = ReworkAlternativeBuilder.plan(toBuilderPair(pair), relevantSteps)
                if (plan == null) {
                    failed[key] = "untranslatable patch"
                    log("$key: skipped — untranslatable patch")
                    continue
                }

                val outcome = try {
                    applyPlan(pool, fromSha, plan, idx, shadowGit)
                } catch (e: Exception) {
                    failed[key] = "exception: ${e.message ?: e.javaClass.simpleName}"
                    log("$key: skipped — ${failed[key]}")
                    null
                }
                if (outcome == null) {
                    failed.putIfAbsent(key, "apply failed")
                    continue
                }
                // Every plan step empty means the user's rework exactly
                // cancelled itself out (e.g. delete then undelete the
                // same function). The optimal alt is "do nothing" — its
                // terminal tree equals the user's terminal tree at
                // `userToSha`. Anchor the alt's single checkpoint at
                // `userToSha` so the chart's apex lands at the merge
                // point (visually right where the user ends up too);
                // the pipeline zeroes the diff for SHAs that collide
                // with user checkpoints so the alt isn't charged for
                // the user's churn at that SHA.
                val effectiveAltShas = if (outcome.shas.isEmpty()) listOf(userToSha) else outcome.shas
                val effectiveBranchRefs = if (outcome.shas.isEmpty()) emptyList() else outcome.branchRefs

                synthesised += SynthesisedRework(
                    fromSha = fromSha,
                    userToSha = userToSha,
                    altShas = effectiveAltShas,
                    branchRefs = effectiveBranchRefs,
                    originatingStep = pair.originatingStep,
                    terminalStep = pair.terminalStep,
                    file = pair.file,
                    scopeId = pair.scopeId,
                    direction = pair.direction,
                    rawLineCount = pair.rawLineCount,
                    contentSummary = pair.contentSummary,
                )
                val sizeLabel = if (outcome.shas.isEmpty()) "0 (no-op alt, anchored at fromSha)" else outcome.shas.size.toString()
                log("$key: synthesised $sizeLabel checkpoint(s)")
            }
        } finally {
            pool.close()
        }

        return Summary(
            candidates = pairs.size,
            synthesised = synthesised,
            failed = failed.toMap(),
        )
    }

    private fun buildStepInputs(
        shadowGit: GitRunner,
        orderedShas: List<String>,
    ): List<ReworkDetector.StepInput> {
        val out = mutableListOf<ReworkDetector.StepInput>()
        for (k in 0 until orderedShas.size - 1) {
            val pre = orderedShas[k]
            val post = orderedShas[k + 1]
            val patch = shadowGit.diffPatch(pre, post, paths = emptyList(), contextLines = 0)
            val touched = touchedJavaFiles(patch)
            val preContent = touched.associateWith { shadowGit.showAtSha(pre, it).orEmpty() }
            val postContent = touched.associateWith { shadowGit.showAtSha(post, it).orEmpty() }
            out += ReworkDetector.StepInput(
                stepIndex = k,
                patch = patch,
                preFileContent = preContent,
                postFileContent = postContent,
            )
        }
        return out
    }

    private fun touchedJavaFiles(patch: String): Set<String> =
        com.github.ethanhosier.analysis.diffs.UnifiedDiffParser.parse(patch).files
            .mapNotNull { it.newPath ?: it.oldPath }
            .filter { it.endsWith(".java") }
            .toSet()

    private fun toBuilderPair(pair: ReworkDetector.ChunkPair): ReworkAlternativeBuilder.ChunkPair =
        ReworkAlternativeBuilder.ChunkPair(
            originatingStep = pair.originatingStep,
            terminalStep = pair.terminalStep,
            file = pair.file,
            direction = when (pair.direction) {
                ReworkDetector.Direction.ADD_THEN_REMOVE ->
                    ReworkAlternativeBuilder.Direction.ADD_THEN_REMOVE
                ReworkDetector.Direction.REMOVE_THEN_ADD ->
                    ReworkAlternativeBuilder.Direction.REMOVE_THEN_ADD
            },
            originatingRunStartLine = pair.originatingRunStartLine,
            terminalRunStartLine = pair.terminalRunStartLine,
            rawLineCount = pair.rawLineCount,
        )

    private data class ApplyOutcome(val shas: List<String>, val branchRefs: List<String>)

    private fun applyPlan(
        pool: WorktreePool,
        fromSha: String,
        plan: ReworkAlternativeBuilder.Plan,
        idx: Int,
        shadowGit: GitRunner,
    ): ApplyOutcome? {
        val worktree = pool.borrow(fromSha)
        try {
            val worktreeGit = GitRunner(worktree)
            worktreeGit.setLocalIdentity("rework@analysis.local", "Rework Alt")
            val shas = mutableListOf<String>()
            val branchRefs = mutableListOf<String>()
            for (ps in plan.steps) {
                if (ps.patch.isEmpty()) continue
                val patchFile = Files.createTempFile("rework-$idx-step${ps.stepIndex}-", ".patch")
                try {
                    Files.writeString(patchFile, ps.patch)
                    val applyResult = worktreeGit.applyDirect(patchFile)
                    if (applyResult !is GitRunner.ApplyResult.Ok) {
                        val reason = (applyResult as? GitRunner.ApplyResult.Conflict)?.reason
                            ?: "apply failed"
                        log("rework-$idx step ${ps.stepIndex}: $reason")
                        return null
                    }
                    if (!worktreeGit.hasStagedChanges()) continue
                    val sha = worktreeGit.commit("rework-$idx: step ${ps.stepIndex}")
                    val branch = "rework-alt/$idx/${shas.size}"
                    shadowGit.branchForce(branch, sha)
                    shas += sha
                    branchRefs += branch
                } finally {
                    Files.deleteIfExists(patchFile)
                }
            }
            return ApplyOutcome(shas, branchRefs)
        } finally {
            pool.release(worktree)
        }
    }

    private fun log(msg: String) {
        System.out.println("[pipeline] $msg")
    }
}
