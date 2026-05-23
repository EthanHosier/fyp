package com.github.ethanhosier.analysis.advice

import com.github.ethanhosier.analysis.pipeline.AdviceItem
import com.github.ethanhosier.analysis.pipeline.AdviceKind
import com.github.ethanhosier.analysis.pipeline.AdviceSeverity
import com.github.ethanhosier.analysis.pipeline.AnalysisReport
import com.github.ethanhosier.analysis.pipeline.CheckpointReport

/**
 * Trajectory-wide observations a senior engineer might call out after
 * watching a session play back. Each rule is a pure function over the
 * finished [AnalysisReport]; returns null when the pattern doesn't
 * apply. Results are emitted in registry order.
 *
 * Thresholds err on the loose side — we'd rather show three soft
 * observations than miss a real one. Numbers are baked into the body
 * strings so the frontend can stay a pure passthrough.
 */
object TrajectoryAdvisor {

    fun advise(report: AnalysisReport): List<AdviceItem> =
        rules.mapNotNull { it(report) }

    private val rules: List<(AnalysisReport) -> AdviceItem?> = listOf(
        ::adviseBuildOftenBroken,
        ::adviseTestRegressions,
        ::adviseLongStretchWithoutCommit,
        ::adviseRefactoringIntroducedSmells,
        ::adviseSmellsAccumulatedNet,
        ::adviseProcessScoreDegraded,
        ::adviseReorderBeatsUser,
    )

    private fun adviseBuildOftenBroken(report: AnalysisReport): AdviceItem? {
        val total = report.checkpoints.size
        if (total < 3) return null
        val failing = report.checkpoints.count { !it.metrics.build.success }
        if (failing == 0) return null
        val share = failing.toDouble() / total
        if (share < 0.15) return null
        val severity = if (share < 0.30) AdviceSeverity.WARNING else AdviceSeverity.CRITICAL
        return AdviceItem(
            kind = AdviceKind.BUILD_OFTEN_BROKEN,
            severity = severity,
            title = "You broke the build often",
            body = "You broke the build on $failing/$total checkpoints " +
                "(${pct(share)}). Frequent breakage masks the source of new " +
                "failures and forces you to debug stale code alongside new code.",
        )
    }

    private fun adviseTestRegressions(report: AnalysisReport): AdviceItem? {
        val regressions = countTestRegressions(report.checkpoints)
        if (regressions == 0) return null
        val severity = if (regressions >= 3) AdviceSeverity.CRITICAL else AdviceSeverity.WARNING
        return AdviceItem(
            kind = AdviceKind.TEST_REGRESSIONS,
            severity = severity,
            title = "Tests regressed mid-session",
            body = "Tests went from passing to failing $regressions " +
                pluralise(regressions, "time", "times") +
                ". Each regression is a fresh debug task — and the longer " +
                "one sits, the harder it gets to pin to the change that caused it.",
        )
    }

    private fun countTestRegressions(checkpoints: List<CheckpointReport>): Int {
        var lastSuccess: Boolean? = null
        var regressions = 0
        for (cp in checkpoints) {
            val tests = cp.metrics.tests
            if (tests.wasSkipped) continue
            val passing = tests.success
            if (lastSuccess == true && !passing) regressions++
            lastSuccess = passing
        }
        return regressions
    }

    private fun adviseLongStretchWithoutCommit(report: AnalysisReport): AdviceItem? {
        val total = report.checkpoints.size
        if (total < 3) return null
        val longest = longestGreenRefactorRunBetweenCommits(report)
        if (longest < MIN_COMMIT_GAP) return null
        val commits = report.userGitCommits.size
        if (commits == 0) {
            return AdviceItem(
                kind = AdviceKind.LONG_STRETCH_WITHOUT_COMMIT,
                severity = AdviceSeverity.CRITICAL,
                title = "You never committed during this session",
                body = "You accumulated $longest green-refactor checkpoints with no commit. " +
                    "Commits are cheap restore points; without them you can't bisect, " +
                    "can't revert cleanly, and your PR diff balloons.",
            )
        }
        val severity = if (longest < 12) AdviceSeverity.INFO else AdviceSeverity.WARNING
        return AdviceItem(
            kind = AdviceKind.LONG_STRETCH_WITHOUT_COMMIT,
            severity = severity,
            title = "Long stretch without committing",
            body = "Your longest run of green-refactor checkpoints between commits was " +
                "$longest. Smaller, more frequent commits give you restore points " +
                "and keep PR diffs reviewable.",
        )
    }

    /**
     * Mirrors the `greenSinceLastCommit` counter in DerivedMetricsRunner
     * so the advice rule fires on the same condition that drives the
     * COMMIT_GAP divergence point and the score-formula commit-gap
     * penalty: a contiguous run of green-refactor checkpoints (refactor
     * step landed, build passing, tests passing-not-skipped) without a
     * user commit landing within the run. Non-refactor and non-green
     * checkpoints don't add to the counter; user-commit checkpoints
     * close the run.
     */
    private fun longestGreenRefactorRunBetweenCommits(report: AnalysisReport): Int {
        val refactorTouchedShas = report.refactoringSteps.map { it.toSha }.toSet()
        var longest = 0
        var run = 0
        for (cp in report.checkpoints) {
            val landsRefactor = cp.sha in refactorTouchedShas
            val tests = cp.metrics.tests
            val testsOk = !tests.wasSkipped && tests.success
            val greenRefactor = landsRefactor && cp.metrics.build.success && testsOk
            when {
                cp.isUserCommit -> {
                    if (run > longest) longest = run
                    run = 0
                }
                greenRefactor -> run += 1
            }
        }
        if (run > longest) longest = run
        return longest
    }

    // Matches ScoringConfig.minCommitGap default; the advice rule must
    // not fire below this threshold or it diverges from the COMMIT_GAP
    // divergence point and the score-formula commit-gap penalty.
    private const val MIN_COMMIT_GAP = 6

    private fun adviseRefactoringIntroducedSmells(report: AnalysisReport): AdviceItem? {
        val cpBySha = report.checkpoints.associateBy { it.sha }
        var bad = 0
        for (step in report.refactoringSteps) {
            val cp = cpBySha[step.toSha] ?: continue
            val introducedHere = cp.pmdTracking.firstSeenAtSha.count { it == cp.sha }
            if (introducedHere > 0) bad++
        }
        if (bad == 0) return null
        return AdviceItem(
            kind = AdviceKind.REFACTORING_INTRODUCED_SMELLS,
            severity = AdviceSeverity.WARNING,
            title = "Refactorings introduced new smells",
            body = "$bad " + pluralise(bad, "refactoring", "refactorings") +
                " you applied introduced new smells. Refactoring should leave " +
                "things cleaner than it found them — check whether you extracted " +
                "a method that's now too long, or moved code without splitting it.",
        )
    }

    private fun adviseSmellsAccumulatedNet(report: AnalysisReport): AdviceItem? {
        val cps = report.checkpoints
        if (cps.size < 2) return null
        val startOpen = cps.first().derivedMetrics.smells
        val endOpen = cps.last().derivedMetrics.smells
        val net = endOpen - startOpen
        if (net <= 2) return null
        return AdviceItem(
            kind = AdviceKind.SMELLS_ACCUMULATED_NET,
            severity = AdviceSeverity.WARNING,
            title = "Smells piled up over the session",
            body = "You ended with $net more open " +
                pluralise(net, "smell", "smells") +
                " than you started ($startOpen → $endOpen). Untouched smells age " +
                "into tech debt — fix them before the context evaporates.",
        )
    }

    private fun adviseProcessScoreDegraded(report: AnalysisReport): AdviceItem? {
        val cps = report.checkpoints
        if (cps.size < 2) return null
        val start = cps.first().derivedMetrics.process.total
        val end = cps.last().derivedMetrics.process.total
        val delta = end - start
        if (delta > -5) return null
        val severity = if (delta <= -15) AdviceSeverity.CRITICAL else AdviceSeverity.WARNING
        return AdviceItem(
            kind = AdviceKind.PROCESS_SCORE_DEGRADED,
            severity = severity,
            title = "Process score fell over the session",
            body = "Your process score fell from $start to $end (${delta} points). " +
                "Your end-of-session state is worse than your start — look at the " +
                "chart for where the drop happened.",
        )
    }

    private fun adviseReorderBeatsUser(report: AnalysisReport): AdviceItem? {
        val cpBySha = report.checkpoints.associateBy { it.sha }
        var bestDelta = 0
        for (alt in report.alternativeTrajectories) {
            // Multi-step alts are reorders; single-step alts are IDE
            // replays of one user step and don't qualify as "reorder."
            if (alt.stepIndexes.size < 2) continue
            val altScore = alt.altCheckpoints.lastOrNull()
                ?.derivedMetrics?.process?.total ?: continue
            val userScore = cpBySha[alt.userToSha]?.derivedMetrics?.process?.total ?: continue
            val delta = altScore - userScore
            if (delta > bestDelta) bestDelta = delta
        }
        if (bestDelta < 3) return null
        return AdviceItem(
            kind = AdviceKind.REORDER_BEATS_USER,
            severity = AdviceSeverity.INFO,
            title = "A different ordering of your steps would have scored higher",
            body = "Doing your same refactoring steps in a different order would " +
                "have scored $bestDelta points higher on process score. Toggle " +
                "the alternative-trajectory layer on the chart to see which " +
                "ordering won.",
        )
    }

    private fun pct(share: Double): String = "${(share * 100).toInt()}%"

    private fun pluralise(n: Int, singular: String, plural: String): String =
        if (n == 1) singular else plural
}
