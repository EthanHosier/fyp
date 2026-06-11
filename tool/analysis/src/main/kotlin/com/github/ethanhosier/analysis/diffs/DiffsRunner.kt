package com.github.ethanhosier.analysis.diffs

import com.github.ethanhosier.analysis.miner.model.RefactoringLocation
import com.github.ethanhosier.analysis.miner.model.RefactoringStep
import com.github.ethanhosier.analysis.model.ReconstructionResult
import com.github.ethanhosier.analysis.reconstruct.GitRunner
import kotlinx.serialization.Serializable

class DiffsRunner(private val git: GitRunner) {

    @Serializable
    data class Summary(
        val checkpointPatches: Map<String, String>,
        val refactoringPatches: Map<Int, String>,
        val alternativePatches: Map<String, String> = emptyMap(),
    )

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
