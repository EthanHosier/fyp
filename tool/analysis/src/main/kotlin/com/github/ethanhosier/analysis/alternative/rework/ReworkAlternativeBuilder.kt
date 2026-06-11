package com.github.ethanhosier.analysis.alternative.rework

import com.github.ethanhosier.analysis.diffs.PatchLineRenumber
import com.github.ethanhosier.analysis.diffs.PatchLineSurgery
import com.github.ethanhosier.analysis.diffs.UnifiedDiffParser

object ReworkAlternativeBuilder {

    enum class Direction { ADD_THEN_REMOVE, REMOVE_THEN_ADD }

    data class ChunkPair(
        val originatingStep: Int,
        val terminalStep: Int,
        val file: String,
        val direction: Direction,
        val originatingRunStartLine: Int,
        val terminalRunStartLine: Int,
        val rawLineCount: Int,
    )

    data class StepInput(val stepIndex: Int, val patch: String)

    data class Plan(
        val originatingStep: Int,
        val terminalStep: Int,
        val steps: List<PlanStep>,
    )

    data class PlanStep(val stepIndex: Int, val patch: String)

    fun interface PatchApplier {
        fun apply(stepIndex: Int, patch: String): Boolean
    }

    fun plan(pair: ChunkPair, steps: List<StepInput>): Plan? {
        require(pair.originatingStep < pair.terminalStep) {
            "originatingStep must precede terminalStep"
        }
        val sorted = steps.sortedBy { it.stepIndex }
        require(sorted.any { it.stepIndex == pair.originatingStep }) { "originating step missing" }
        require(sorted.any { it.stepIndex == pair.terminalStep }) { "terminal step missing" }

        val relevant = sorted.filter { it.stepIndex >= pair.originatingStep }
        val tracker = ReworkDriftTracker()
        var zoneId = -1
        val out = mutableListOf<PlanStep>()

        for (step in relevant) {
            val planned: String = when (step.stepIndex) {
                pair.originatingStep -> {
                    val surgered = surgeryAtOriginating(step.patch, pair)
                    zoneId = registerZone(pair, tracker, step.patch)
                    surgered
                }
                pair.terminalStep -> {
                    val surgered = surgeryAtTerminal(step.patch, pair)
                    val renumbered = PatchLineRenumber.renumber(surgered, tracker) ?: return null
                    if (zoneId >= 0) tracker.clearZone(zoneId)
                    renumbered
                }
                else -> {
                    val renumbered = PatchLineRenumber.renumber(step.patch, tracker) ?: return null
                    recordUserHunks(step.patch, tracker)
                    renumbered
                }
            }
            out += PlanStep(step.stepIndex, planned)
        }
        return Plan(pair.originatingStep, pair.terminalStep, out)
    }

    fun build(pair: ChunkPair, steps: List<StepInput>, applier: PatchApplier): Plan? {
        val plan = plan(pair, steps) ?: return null
        for (ps in plan.steps) {
            if (ps.patch.isEmpty()) continue
            if (!applier.apply(ps.stepIndex, ps.patch)) return null
        }
        return plan
    }

    private fun surgeryAtOriginating(patch: String, pair: ChunkPair): String =
        when (pair.direction) {
            Direction.ADD_THEN_REMOVE -> PatchLineSurgery.removeRuns(
                patch,
                addedRunStartLines = mapOf(pair.file to setOf(pair.originatingRunStartLine)),
            )
            Direction.REMOVE_THEN_ADD -> PatchLineSurgery.removeRuns(
                patch,
                removedRunStartLines = mapOf(pair.file to setOf(pair.originatingRunStartLine)),
            )
        }

    private fun surgeryAtTerminal(patch: String, pair: ChunkPair): String =
        when (pair.direction) {
            Direction.ADD_THEN_REMOVE -> PatchLineSurgery.removeRuns(
                patch,
                removedRunStartLines = mapOf(pair.file to setOf(pair.terminalRunStartLine)),
            )
            Direction.REMOVE_THEN_ADD -> PatchLineSurgery.removeRuns(
                patch,
                addedRunStartLines = mapOf(pair.file to setOf(pair.terminalRunStartLine)),
            )
        }

    private fun registerZone(
        pair: ChunkPair,
        tracker: ReworkDriftTracker,
        originatingPatch: String,
    ): Int = when (pair.direction) {
        Direction.ADD_THEN_REMOVE ->
            tracker.registerAddedZone(pair.file, pair.originatingRunStartLine, pair.rawLineCount)
        Direction.REMOVE_THEN_ADD -> {
            val hunk = findOriginatingHunk(originatingPatch, pair)
            val userStartLine = if (hunk != null) {
                maxOf(hunk.newStart + hunk.newLen, hunk.oldStart)
            } else {
                pair.originatingRunStartLine
            }
            tracker.registerRemovedZone(pair.file, userStartLine, pair.rawLineCount)
        }
    }

    private fun findOriginatingHunk(patch: String, pair: ChunkPair): UnifiedDiffParser.Hunk? {
        val files = UnifiedDiffParser.parse(patch).files
            .filter { (it.newPath ?: it.oldPath) == pair.file }
        for (f in files) {
            for (h in f.hunks) {
                val runs = HunkExtractor.partitionRuns(h)
                val match = when (pair.direction) {
                    Direction.ADD_THEN_REMOVE ->
                        runs.addedRuns.any { it.startLine == pair.originatingRunStartLine }
                    Direction.REMOVE_THEN_ADD ->
                        runs.removedRuns.any { it.startLine == pair.originatingRunStartLine }
                }
                if (match) return h
            }
        }
        return null
    }

    private fun recordUserHunks(patch: String, tracker: ReworkDriftTracker) {
        for (file in UnifiedDiffParser.parse(patch).files) {
            val key = file.newPath ?: file.oldPath ?: continue
            for (hunk in file.hunks) {
                tracker.recordHunkApplied(key, hunk.oldStart, hunk.oldLen, hunk.newLen)
            }
        }
    }
}
