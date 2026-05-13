package com.github.ethanhosier.analysis.divergence

import com.github.ethanhosier.analysis.metrics.model.AlternativeTrajectory
import com.github.ethanhosier.analysis.metrics.model.CheckpointReport
import com.github.ethanhosier.analysis.metrics.model.DivergenceKind
import com.github.ethanhosier.analysis.metrics.model.DivergencePoint

/**
 * Builds the per-step [DivergencePoint] list from already-tagged
 * alternative trajectories. Pure function — no I/O, no shadow-repo
 * access. The pipeline calls this after `baseAlternatives` has been
 * finalised so [AlternativeTrajectory] list indexes are stable.
 *
 * Grouping rules per [DivergenceKind]:
 *  - **IDE_REPLAY**: one DP per alt. `altTrajectoryIndexes=[i]`.
 *  - **ORDERING**: collapse all alts sharing `(fromSha, userToSha)` into
 *    one DP. `altTrajectoryIndexes=[i, j, …]` (sorted). Magnitude /
 *    `altWins`-style headline pulled from the best permutation.
 *  - **REWORK**: one DP per alt. Per-alt context (file, scope, line
 *    count, originating step) is passed in via [reworkInfoByAltIndex] —
 *    these don't live on the alt itself.
 *
 * Per-kind magnitude floors (compile-time constants) drop noise:
 *  - IDE_REPLAY / ORDERING: ≥ [MIN_PROCESS_DELTA] process points.
 *  - REWORK: ≥ [MIN_REWORK_LINES] reverted lines.
 */
object DivergencePointBuilder {

    const val MIN_PROCESS_DELTA: Double = 3.0
    const val MIN_REWORK_LINES: Int = 2

    /** Per-alt metadata for a REWORK divergence — fields the alt itself
     *  doesn't carry, threaded in from the synthesiser's record. */
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

    fun build(
        alts: List<AlternativeTrajectory>,
        userCheckpoints: List<CheckpointReport>,
        reworkInfoByAltIndex: Map<Int, ReworkInfo> = emptyMap(),
    ): List<DivergencePoint> {
        val userProcessBySha: Map<String, Int> = userCheckpoints
            .associate { it.sha to it.derivedMetrics.process.total }
        val userStepIndexBySha: Map<String, Int> = userCheckpoints
            .withIndex().associate { (i, cp) -> cp.sha to i }

        val out = mutableListOf<DivergencePoint>()
        out += buildIdeReplay(alts, userProcessBySha)
        out += buildOrdering(alts, userProcessBySha, userStepIndexBySha)
        out += buildRework(alts, reworkInfoByAltIndex)
        return out
    }

    private fun buildIdeReplay(
        alts: List<AlternativeTrajectory>,
        userProcessBySha: Map<String, Int>,
    ): List<DivergencePoint> {
        val out = mutableListOf<DivergencePoint>()
        for ((i, alt) in alts.withIndex()) {
            if (alt.kind != DivergenceKind.IDE_REPLAY) continue
            val stepIndex = alt.stepIndexes.firstOrNull() ?: continue
            val altProcess = alt.altCheckpoints.lastOrNull()
                ?.derivedMetrics?.process?.total ?: continue
            val userProcess = userProcessBySha[alt.userToSha] ?: continue
            val delta = (altProcess - userProcess).toDouble()
            if (delta < MIN_PROCESS_DELTA) continue

            val specLabel = alt.specs.firstOrNull()?.let { specDisplayName(it::class.simpleName) }
                ?: "Refactoring"
            val nSteps = alt.stepIndexes.size
            val stepWord = if (nSteps == 1) "step" else "steps"
            out += DivergencePoint(
                stepIndex = stepIndex,
                kind = DivergenceKind.IDE_REPLAY,
                magnitude = delta,
                title = "$specLabel: IDE replay would have scored +${formatDelta(delta)}",
                explanation = "You performed $specLabel manually across $nSteps $stepWord; " +
                    "the IDE-driven equivalent reached process score $altProcess vs your " +
                    "$userProcess — a +${formatDelta(delta)} gap.",
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
    ): List<DivergencePoint> {
        val grouped: Map<Pair<String, String>, List<IndexedValue<AlternativeTrajectory>>> =
            alts.withIndex()
                .filter { it.value.kind == DivergenceKind.ORDERING }
                .groupBy { it.value.fromSha to it.value.userToSha }

        val out = mutableListOf<DivergencePoint>()
        for ((key, group) in grouped) {
            val (_, userToSha) = key
            val userProcess = userProcessBySha[userToSha] ?: continue

            val scoredGroup = group.mapNotNull { iv ->
                val ap = iv.value.altCheckpoints.lastOrNull()
                    ?.derivedMetrics?.process?.total ?: return@mapNotNull null
                Triple(iv.index, iv.value, (ap - userProcess).toDouble())
            }
            if (scoredGroup.isEmpty()) continue

            val winning = scoredGroup.filter { it.third >= MIN_PROCESS_DELTA }
            if (winning.isEmpty()) continue

            val best = winning.maxBy { it.third }
            val windowSteps = scoredGroup
                .flatMap { (_, alt, _) -> alt.stepIndexes }
                .toSortedSet().toList()
            val anchorStepIndex = windowSteps.lastOrNull()
                ?: userStepIndexBySha[userToSha]
                ?: continue
            val nBeating = winning.size
            val orderingsWord = if (nBeating == 1) "ordering" else "orderings"

            out += DivergencePoint(
                stepIndex = anchorStepIndex,
                kind = DivergenceKind.ORDERING,
                magnitude = best.third,
                title = "Reordering ${windowSteps.size} steps would have scored +${formatDelta(best.third)}",
                explanation = "An alternative ordering of these ${windowSteps.size} refactorings " +
                    "reached process score ${best.second.altCheckpoints.last().derivedMetrics.process.total} " +
                    "vs your $userProcess — a +${formatDelta(best.third)} gap. " +
                    "$nBeating $orderingsWord beat yours.",
                altTrajectoryIndexes = winning.map { it.first }.sorted(),
                orderingWindowSteps = windowSteps,
            )
        }
        return out
    }

    private fun buildRework(
        alts: List<AlternativeTrajectory>,
        reworkInfoByAltIndex: Map<Int, ReworkInfo>,
    ): List<DivergencePoint> {
        val out = mutableListOf<DivergencePoint>()
        for ((i, alt) in alts.withIndex()) {
            if (alt.kind != DivergenceKind.REWORK) continue
            val info = reworkInfoByAltIndex[i] ?: continue
            if (info.lineCount < MIN_REWORK_LINES) continue

            out += DivergencePoint(
                stepIndex = info.originatingStepIndex,
                kind = DivergenceKind.REWORK,
                magnitude = info.lineCount.toDouble(),
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

    private fun specDisplayName(simpleName: String?): String =
        when (simpleName) {
            null -> "Refactoring"
            else -> simpleName.replace(Regex("([a-z])([A-Z])"), "$1 $2")
        }

    private fun formatDelta(d: Double): String = String.format("%.1f", d)

    /**
     * Trim a fully-qualified scope id like
     * `org.example.OrderPricingServiceSuper#doSomeMath(double)` down to
     * the member portion (`doSomeMath(double)`) — the FQN prefix
     * overflows the rework-DP card in the sidebar and the file name is
     * already shown alongside. Falls back to the original label when
     * there's no `#` separator (e.g. file-level scope sentinels).
     */
    private fun shortScopeLabel(scopeLabel: String): String {
        val hashIdx = scopeLabel.indexOf('#')
        return if (hashIdx >= 0) scopeLabel.substring(hashIdx + 1) else scopeLabel
    }
}
