package com.github.ethanhosier.analysis.alternative.rework

import com.github.ethanhosier.analysis.diffs.DiffAnalysis
import com.github.ethanhosier.analysis.metrics.WorktreePool
import com.github.ethanhosier.analysis.model.ReconstructionResult
import com.github.ethanhosier.analysis.reconstruct.GitRunner
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path

class ReworkSynthesiser {

    @Serializable
    data class Summary(
        val candidates: Int,
        val synthesised: List<SynthesisedRework>,
        val failed: Map<String, String>,
    )

    @Serializable
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
        val originatingPatch: String,
        val terminalPatch: String,
        val planStepPositions: List<Int>,
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
                val effectiveAltShas = if (outcome.shas.isEmpty()) listOf(userToSha) else outcome.shas
                val effectiveBranchRefs = if (outcome.shas.isEmpty()) emptyList() else outcome.branchRefs
                val effectivePlanPositions = if (outcome.shas.isEmpty()) emptyList() else outcome.planPositions

                val (originatingPatch, terminalPatch) = buildFocusedPatches(pair)
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
                    originatingPatch = originatingPatch,
                    terminalPatch = terminalPatch,
                    planStepPositions = effectivePlanPositions,
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

    private fun buildFocusedPatches(pair: ReworkDetector.ChunkPair): Pair<String, String> {
        val chunkLines = pair.chunkSourceText
            .split("\n")
            .let { if (it.isNotEmpty() && it.last().isEmpty()) it.dropLast(1) else it }
        val addedBody = chunkLines.joinToString("") { "+$it\n" }
        val removedBody = chunkLines.joinToString("") { "-$it\n" }
        val n = chunkLines.size

        fun additionPatch(startLine: Int): String {
            val before = (startLine - 1).coerceAtLeast(0)
            return "diff --git a/${pair.file} b/${pair.file}\n" +
                "--- a/${pair.file}\n" +
                "+++ b/${pair.file}\n" +
                "@@ -$before,0 +$startLine,$n @@\n" +
                addedBody
        }

        fun removalPatch(startLine: Int): String {
            val after = (startLine - 1).coerceAtLeast(0)
            return "diff --git a/${pair.file} b/${pair.file}\n" +
                "--- a/${pair.file}\n" +
                "+++ b/${pair.file}\n" +
                "@@ -$startLine,$n +$after,0 @@\n" +
                removedBody
        }

        return when (pair.direction) {
            ReworkDetector.Direction.ADD_THEN_REMOVE ->
                additionPatch(pair.originatingRunStartLine) to
                    removalPatch(pair.terminalRunStartLine)
            ReworkDetector.Direction.REMOVE_THEN_ADD ->
                removalPatch(pair.originatingRunStartLine) to
                    additionPatch(pair.terminalRunStartLine)
        }
    }

    private data class ApplyOutcome(
        val shas: List<String>,
        val branchRefs: List<String>,
        val planPositions: List<Int>,
    )

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
            data class Committed(
                val sha: String,
                val branch: String,
                val planPosition: Int,
                val whitespaceOnly: Boolean,
            )

            val committed = mutableListOf<Committed>()
            var parentSha = fromSha
            for ((planPos, ps) in plan.steps.withIndex()) {
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
                    val branch = "rework-alt/$idx/${committed.size}"
                    shadowGit.branchForce(branch, sha)
                    val diff = worktreeGit.diffPatch(parentSha, sha)
                    committed += Committed(
                        sha = sha,
                        branch = branch,
                        planPosition = planPos,
                        whitespaceOnly = DiffAnalysis.isWhitespaceOnly(diff),
                    )
                    parentSha = sha
                } finally {
                    Files.deleteIfExists(patchFile)
                }
            }
            val lastIdx = committed.lastIndex
            val kept = committed.filterIndexed { i, c -> i == lastIdx || !c.whitespaceOnly }
            val dropped = committed.size - kept.size
            if (dropped > 0) {
                log("rework-$idx: absorbed $dropped whitespace-only step(s) into the next alt checkpoint")
            }
            return ApplyOutcome(
                shas = kept.map { it.sha },
                branchRefs = kept.map { it.branch },
                planPositions = kept.map { it.planPosition },
            )
        } finally {
            pool.release(worktree)
        }
    }

    private fun log(msg: String) {
        System.out.println("[pipeline] $msg")
    }
}
