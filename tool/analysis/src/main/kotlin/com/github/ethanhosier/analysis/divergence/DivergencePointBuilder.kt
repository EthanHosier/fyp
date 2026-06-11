package com.github.ethanhosier.analysis.divergence

import com.github.ethanhosier.analysis.metrics.model.AlternativeTrajectory
import com.github.ethanhosier.analysis.pipeline.CheckpointReport
import com.github.ethanhosier.analysis.pipeline.DivergenceKind
import com.github.ethanhosier.analysis.pipeline.DivergencePoint

object DivergencePointBuilder {

    const val MIN_REWORK_LINES: Int = 2

    data class ReworkInfo(
        val originatingStepIndex: Int,
        val terminalStepIndex: Int,
        val file: String,
        val scopeLabel: String,
        val lineCount: Int,
        val originatingPatch: String,
        val terminalPatch: String,
        val direction: String,
    )

    data class HygieneInfo(
        val anchorIndex: Int,
        val subKind: String,
        val gapLength: Int? = null,
    )

    fun build(
        alts: List<AlternativeTrajectory>,
        userCheckpoints: List<CheckpointReport>,
        reworkInfoByAltIndex: Map<Int, ReworkInfo> = emptyMap(),
        hygieneInfoByAltIndex: Map<Int, HygieneInfo> = emptyMap(),
    ): List<DivergencePoint> {
        val userProcessBySha: Map<String, Int> = userCheckpoints
            .associate { it.sha to it.derivedMetrics.process.total }
        val userStepIndexBySha: Map<String, Int> = userCheckpoints
            .withIndex().associate { (i, cp) -> cp.sha to i }
        val userFinalProcess: Int = userCheckpoints.lastOrNull()
            ?.derivedMetrics?.process?.total
            ?: return emptyList()

        val out = mutableListOf<DivergencePoint>()
        out += buildIdeReplay(alts, userFinalProcess)
        out += buildOrdering(alts, userProcessBySha, userStepIndexBySha, userFinalProcess)
        out += buildRework(alts, reworkInfoByAltIndex, userFinalProcess)
        out += buildHygiene(alts, userCheckpoints, hygieneInfoByAltIndex, userFinalProcess)
        return out
    }

    private fun altFinalProcess(alt: AlternativeTrajectory): Int? =
        (alt.altCheckpoints + alt.continuationCheckpoints).lastOrNull()
            ?.derivedMetrics?.process?.total

    private fun buildIdeReplay(
        alts: List<AlternativeTrajectory>,
        userFinalProcess: Int,
    ): List<DivergencePoint> {
        val out = mutableListOf<DivergencePoint>()
        for ((i, alt) in alts.withIndex()) {
            if (alt.kind != DivergenceKind.IDE_REPLAY) continue
            val stepIndex = alt.stepIndexes.firstOrNull() ?: continue
            val altFinal = altFinalProcess(alt) ?: continue
            val delta = (altFinal - userFinalProcess).toDouble()

            val specLabel = alt.specs.firstOrNull()?.let { specDisplayName(it::class.simpleName) }
                ?: "Refactoring"
            val nSteps = alt.stepIndexes.size
            val stepWord = if (nSteps == 1) "step" else "steps"
            out += DivergencePoint(
                stepIndex = stepIndex,
                kind = DivergenceKind.IDE_REPLAY,
                magnitude = delta,
                title = "$specLabel: IDE replay would have scored ${formatDelta(delta)}",
                explanation = "You performed $specLabel manually across $nSteps $stepWord; " +
                    "the IDE-driven equivalent would have reached trajectory-final process " +
                    "score $altFinal vs your $userFinalProcess — a ${formatDelta(delta)} gap.",
                altTrajectoryIndexes = listOf(i),
                replacedRefactoringId = alt.specs.firstOrNull()?.let { it::class.simpleName },
            )
        }
        return out
    }

    private fun buildOrdering(
        alts: List<AlternativeTrajectory>,
        userProcessBySha: Map<String, Int>,
        userStepIndexBySha: Map<String, Int>,
        userFinalProcess: Int,
    ): List<DivergencePoint> {
        val grouped: Map<Pair<String, String>, List<IndexedValue<AlternativeTrajectory>>> =
            alts.withIndex()
                .filter { it.value.kind == DivergenceKind.ORDERING }
                .groupBy { it.value.fromSha to it.value.userToSha }

        val out = mutableListOf<DivergencePoint>()
        for ((key, group) in grouped) {
            val (_, userToSha) = key

            val scoredGroup = group.mapNotNull { iv ->
                val ap = altFinalProcess(iv.value) ?: return@mapNotNull null
                Triple(iv.index, iv.value, (ap - userFinalProcess).toDouble())
            }
            if (scoredGroup.isEmpty()) continue

            val best = scoredGroup.maxBy { it.third }
            val windowSteps = scoredGroup
                .flatMap { (_, alt, _) -> alt.stepIndexes }
                .toSortedSet().toList()
            val anchorStepIndex = windowSteps.lastOrNull()
                ?: userStepIndexBySha[userToSha]
                ?: continue
            val nBeating = scoredGroup.count { it.third > 0.0 }
            val orderingsWord = if (nBeating == 1) "ordering" else "orderings"

            out += DivergencePoint(
                stepIndex = anchorStepIndex,
                kind = DivergenceKind.ORDERING,
                magnitude = best.third,
                title = "Reordering ${windowSteps.size} steps would have scored ${formatDelta(best.third)}",
                explanation = "An alternative ordering of these ${windowSteps.size} refactorings " +
                    "would have reached trajectory-final process score " +
                    "${altFinalProcess(best.second) ?: -1} vs your $userFinalProcess — a " +
                    "${formatDelta(best.third)} gap. $nBeating of ${scoredGroup.size} " +
                    "$orderingsWord beat yours.",
                altTrajectoryIndexes = scoredGroup.map { it.first }.sorted(),
                orderingWindowSteps = windowSteps,
            )
        }
        return out
    }

    private fun buildRework(
        alts: List<AlternativeTrajectory>,
        reworkInfoByAltIndex: Map<Int, ReworkInfo>,
        userFinalProcess: Int,
    ): List<DivergencePoint> {
        val out = mutableListOf<DivergencePoint>()
        for ((i, alt) in alts.withIndex()) {
            if (alt.kind != DivergenceKind.REWORK) continue
            val info = reworkInfoByAltIndex[i] ?: continue
            if (info.lineCount < MIN_REWORK_LINES) continue

            val altFinal = altFinalProcess(alt) ?: continue
            val delta = (altFinal - userFinalProcess).toDouble()

            out += DivergencePoint(
                stepIndex = info.originatingStepIndex,
                kind = DivergenceKind.REWORK,
                magnitude = delta,
                title = "Reverted ${info.lineCount} lines in ${shortScopeLabel(info.scopeLabel)}",
                explanation = "Lines added at step ${info.originatingStepIndex} were removed at " +
                    "step ${info.terminalStepIndex} — ${info.lineCount} lines of wasted churn in " +
                    "${info.file.substringAfterLast('/')} (${shortScopeLabel(info.scopeLabel)}).",
                altTrajectoryIndexes = listOf(i),
                originatingStepIndex = info.originatingStepIndex,
                file = info.file,
                scopeLabel = info.scopeLabel,
                reworkLineCount = info.lineCount,
                originatingPatch = info.originatingPatch,
                terminalPatch = info.terminalPatch,
                reworkDirection = info.direction,
            )
        }
        return out
    }

    private fun buildHygiene(
        alts: List<AlternativeTrajectory>,
        userCheckpoints: List<CheckpointReport>,
        hygieneInfoByAltIndex: Map<Int, HygieneInfo>,
        userFinalProcess: Int,
    ): List<DivergencePoint> {
        val out = mutableListOf<DivergencePoint>()
        for ((i, alt) in alts.withIndex()) {
            if (alt.kind != DivergenceKind.HYGIENE) continue
            val info = hygieneInfoByAltIndex[i] ?: continue
            val anchorCp = userCheckpoints.getOrNull(info.anchorIndex) ?: continue
            val altFinal = altFinalProcess(alt) ?: continue
            val delta = (altFinal - userFinalProcess).toDouble()

            val (title, explanation) = when (info.subKind) {
                "TESTS_SKIPPED" -> {
                    "Tests skipped here — running them would have scored ${formatDelta(delta)}" to
                        ("You skipped tests at this checkpoint. Running them and " +
                            "treating them as passing would have lifted trajectory-final " +
                            "process score $userFinalProcess → $altFinal (${formatDelta(delta)}).")
                }
                "COMMIT_GAP" -> {
                    val gap = info.gapLength ?: 0
                    "No commit for $gap checkpoints — frequent commits give you cheap restore points" to
                        ("You went $gap checkpoints without committing here. Frequent commits " +
                            "give you cheap restore points and keep PR diffs reviewable.")
                }
                else -> continue
            }

            out += DivergencePoint(
                stepIndex = info.anchorIndex,
                kind = DivergenceKind.HYGIENE,
                magnitude = delta,
                title = title,
                explanation = explanation,
                altTrajectoryIndexes = listOf(i),
                hygieneSubKind = info.subKind,
                hygieneStretchLength = info.gapLength,
            )
        }
        return out
    }

    private fun specDisplayName(simpleName: String?): String =
        when (simpleName) {
            null -> "Refactoring"
            else -> simpleName.replace(Regex("([a-z])([A-Z])"), "$1 $2")
        }

    private fun formatDelta(d: Double): String =
        if (d >= 0) String.format("+%.1f", d) else String.format("%.1f", d)

    private fun shortScopeLabel(scopeLabel: String): String {
        val hashIdx = scopeLabel.indexOf('#')
        return if (hashIdx >= 0) scopeLabel.substring(hashIdx + 1) else scopeLabel
    }
}
