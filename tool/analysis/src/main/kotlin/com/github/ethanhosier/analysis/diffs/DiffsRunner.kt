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
    )

    fun run(
        reconstruction: ReconstructionResult,
        steps: List<RefactoringStep>,
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

        return Summary(checkpointPatches, refactoringPatches)
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
