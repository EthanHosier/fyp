package com.github.ethanhosier.analysis.alternative.rework

import com.github.ethanhosier.analysis.diffs.PatchLineRenumber
import com.github.ethanhosier.analysis.diffs.PatchLineSurgery
import com.github.ethanhosier.analysis.diffs.UnifiedDiffParser

/**
 * Plans (and optionally applies) the surgical-replay alt trajectory
 * for a single rework chunk pair. Composes [PatchLineSurgery],
 * [ReworkDriftTracker], and [PatchLineRenumber] into the per-step
 * pipeline described in PLAN-rework-detection.md.
 *
 * Per the "no partial trajectories" rule, any failure (renumber
 * rejects a patch as untranslatable; applier reports a failed apply)
 * aborts the whole alt — the planner returns `null` and the applier
 * shim returns `null` without committing.
 *
 * The planner is pure (no git, no filesystem). The git-level
 * application is supplied by the caller via [PatchApplier], which
 * keeps this layer unit-testable with literal patch fixtures.
 */
object ReworkAlternativeBuilder {

    enum class Direction { ADD_THEN_REMOVE, REMOVE_THEN_ADD }

    /** A single matched (+run, -run) pair identified by [ReworkDetector]. */
    data class ChunkPair(
        val originatingStep: Int,
        val terminalStep: Int,
        val file: String,
        val direction: Direction,
        /**
         * For `ADD_THEN_REMOVE`: the +run's startLine in step `k'`'s
         * post-tree (== newStart side).
         * For `REMOVE_THEN_ADD`: the -run's startLine in step `k'`'s
         * pre-tree (== oldStart side).
         */
        val originatingRunStartLine: Int,
        /**
         * For `ADD_THEN_REMOVE`: the -run's startLine in step `k`'s
         * pre-tree (== oldStart side).
         * For `REMOVE_THEN_ADD`: the +run's startLine in step `k`'s
         * post-tree (== newStart side).
         */
        val terminalRunStartLine: Int,
        /** Raw run length (before blank-line normalisation) — sized so
         *  the drift tracker's zone covers every user line the chunk
         *  actually spans. */
        val rawLineCount: Int,
    )

    /** One step of trajectory replay data — its raw user-tree patch. */
    data class StepInput(val stepIndex: Int, val patch: String)

    /** The result of planning: the synth patches to apply, in order. */
    data class Plan(
        val originatingStep: Int,
        val terminalStep: Int,
        val steps: List<PlanStep>,
    )

    /** One step's synth patch — empty when the surgery removed every change. */
    data class PlanStep(val stepIndex: Int, val patch: String)

    /** Caller-supplied git applier. Returns `true` on a successful apply. */
    fun interface PatchApplier {
        fun apply(stepIndex: Int, patch: String): Boolean
    }

    /**
     * Plan the sequence of synth patches for [pair] over [steps]. Returns
     * `null` if any patch is untranslatable. No I/O, no apply.
     */
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

    /**
     * Plan and apply atomically. Returns the [Plan] on full success;
     * `null` if planning rejected any patch, or if the [applier]
     * rejected any non-empty patch.
     */
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

    /**
     * Compute the zone's user-tree start line and register it.
     *
     * **ADD_THEN_REMOVE**: the +run is dropped by surgery, so synth
     * post-step lacks `rawLineCount` lines that user post-step has,
     * starting at the +run's `newStart`. Zone at that exact position.
     *
     * **REMOVE_THEN_ADD**: the -run is dropped by surgery, but any
     * other content in the same hunk (e.g. a `+blank` paired with a
     * 17-line `-switch`) is *preserved*, landing in both trees at
     * identical positions. The divergence therefore begins *after*
     * the preserved +run.
     *
     *  - For a pure-deletion originating hunk (`+run` length 0):
     *    `userStartLine = hunk.oldStart` (deletion shifts user
     *    post lines up by `length`; synth retains them).
     *  - For a modification (`+run` length M > 0):
     *    `userStartLine = hunk.newStart + M` (just past the kept +run
     *    in user post-tree).
     *
     * Both reduce to `max(hunk.newStart + hunk.newLen, hunk.oldStart)`,
     * because the conventional pure-deletion header has
     * `newStart = oldStart - 1` and `newLen = 0` so `max(O-1, O) = O`.
     */
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

    /** Locate the originating hunk that contains the matched run. */
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

    /**
     * After an intermediate step's patch applies (in the user's tree),
     * shift zones past each hunk by the hunk's net line delta. Walks
     * the *original* (user-coord) patch so the deltas are in the
     * tracker's reference frame.
     */
    private fun recordUserHunks(patch: String, tracker: ReworkDriftTracker) {
        for (file in UnifiedDiffParser.parse(patch).files) {
            val key = file.newPath ?: file.oldPath ?: continue
            for (hunk in file.hunks) {
                tracker.recordHunkApplied(key, hunk.oldStart, hunk.oldLen, hunk.newLen)
            }
        }
    }
}
