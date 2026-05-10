package com.github.ethanhosier.analysis.diffs

import com.github.ethanhosier.analysis.miner.model.RefactoringLocation
import com.github.ethanhosier.analysis.miner.model.RefactoringStep
import com.github.ethanhosier.analysis.model.ReconstructionResult
import com.github.ethanhosier.analysis.reconstruct.GitRunner

/**
 * Produces unified-diff patch strings at two granularities:
 *
 *  - **Per consecutive checkpoint** (keyed by `toSha`): the raw `git diff`
 *    between a checkpoint and its predecessor — "what changed to reach
 *    this state".
 *
 *  - **Per refactoring step** (keyed by `stepIndex`): the patch between
 *    `step.fromSha` and `step.toSha`, scoped to the files touched by the
 *    RefactoringMiner detection and then hunk-filtered by [PatchFilter]
 *    against the detection's left/right line ranges — "what textual change
 *    comprises this particular refactoring".
 *
 * Runs as its own stage after metrics + miner. Patches are pure `git diff`
 * output, so this stage has no dependency on either earlier one beyond
 * the shadow repo and the miner's step list.
 *
 * Cheap compared to build/test; sequential is fine.
 */
class DiffsRunner(private val git: GitRunner) {

    data class Summary(
        // Keyed by `toSha` of each transition. The seed commit's parent is
        // used as `fromSha` for the first checkpoint; if the seed has no
        // parent (root commit), the entry is an empty string.
        val checkpointPatches: Map<String, String>,
        // Keyed by [RefactoringStep.stepIndex]. Empty string if filtering
        // left no hunks (e.g. RM-only metadata detections with no textual
        // delta inside the declared ranges).
        val refactoringPatches: Map<Int, String>,
        // Keyed by `altSha` (= each alt step's synthesised SHA). Value
        // is the full unified diff `fromSha → altSha` — no hunk
        // filtering, since the alt refactoring's textual change is the
        // entire point of the comparison. Multi-step alts contribute
        // one entry per applied step.
        val alternativePatches: Map<String, String> = emptyMap(),
    )

    /**
     * @param alternativePairs `(fromSha, altSha)` pairs to diff, one per
     *   applied alt step. Single-step alts contribute one pair; multi-step
     *   reorder orderings contribute N pairs (each step's `fromSha` is the
     *   previous step's `altSha`, except the first which anchors at the
     *   window's `windowFromSha`). Pairs are deduplicated by `altSha`
     *   defensively.
     */
    fun run(
        reconstruction: ReconstructionResult,
        steps: List<RefactoringStep>,
        alternativePairs: List<Pair<String, String>> = emptyList(),
    ): Summary {
        val orderedShas = reconstruction.eventCommits.mapping.values
            .toCollection(LinkedHashSet()).toList()

        val checkpointPatches = LinkedHashMap<String, String>()
        for ((i, sha) in orderedShas.withIndex()) {
            val from = if (i == 0) git.parentOf(sha) else orderedShas[i - 1]
            checkpointPatches[sha] = if (from == null) "" else git.diffPatch(from, sha)
        }

        val refactoringPatches = LinkedHashMap<Int, String>()
        for (step in steps) {
            refactoringPatches[step.stepIndex] = refactoringPatch(step)
        }

        val alternativePatches = LinkedHashMap<String, String>()
        for ((from, altSha) in alternativePairs) {
            if (altSha in alternativePatches) continue
            alternativePatches[altSha] = git.diffPatch(from, altSha)
        }

        return Summary(checkpointPatches, refactoringPatches, alternativePatches)
    }

    private fun refactoringPatch(step: RefactoringStep): String {
        val leftRanges = rangesByFile(step.refactoring.leftSideLocations)
        val rightRanges = rangesByFile(step.refactoring.rightSideLocations)
        val files = (leftRanges.keys + rightRanges.keys).toList()
        if (files.isEmpty()) return ""

        val raw = git.diffPatch(step.fromSha, step.toSha, paths = files)
        return PatchFilter.filter(raw, leftRanges, rightRanges)
    }

    private fun rangesByFile(locations: List<RefactoringLocation>): Map<String, List<IntRange>> {
        val out = LinkedHashMap<String, MutableList<IntRange>>()
        for (loc in locations) {
            out.getOrPut(loc.filePath) { mutableListOf() }.add(loc.startLine..loc.endLine)
        }
        return out
    }
}
