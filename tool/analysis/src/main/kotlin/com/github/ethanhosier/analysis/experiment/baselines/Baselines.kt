package com.github.ethanhosier.analysis.experiment.baselines

import com.github.ethanhosier.analysis.pipeline.AnalysisReport

/**
 * Naive baselines the proposed process score is benchmarked against in
 * `DivergenceExperiment`. Each one is a deliberately weak strawman that
 * exercises a single signal the full score is supposed to subsume; they
 * exist to make the headline claim ("the composite catches bad-process
 * patterns these alone miss") falsifiable.
 *
 * All baselines are pure functions over [AnalysisReport]; no Phase A
 * re-run needed.
 */
object Baselines {

    /**
     * Baseline #1 — endpoint-only Δ cleanliness.
     *
     * Did the touched-file cleanliness composite improve between the
     * session's first and last checkpoint? Returns true for "good"
     * sessions. By construction this never localises a *step* — it
     * collapses the whole trajectory to a single boolean and so cannot
     * score per-step injection ground truth. Used as the headline
     * negative result: any session whose endpoint improves passes here
     * regardless of intermediate badness.
     */
    fun endpointOnlyImproved(report: AnalysisReport): Boolean {
        val cps = report.checkpoints
        if (cps.size < 2) return false
        val first = cps.first().derivedMetrics.cleanliness?.score ?: return false
        val last = cps.last().derivedMetrics.cleanliness?.score ?: return false
        return last > first
    }

    /**
     * Baseline #2 — per-step process-score regression.
     *
     * Flags every checkpoint where the (cumulative) process score
     * dropped by more than [theta] points from the previous one.
     * Catches local spikes but, because it consumes the very score the
     * full detector composes, it can only differ from the detector by
     * what it ignores — namely the divergence-point machinery that
     * groups, attributes, and explains the spikes. Returned as
     * 1-indexed checkpoint indices to align with the divergence-point
     * `targetStep` convention used in `manifest.csv`.
     */
    fun perStepRegression(report: AnalysisReport, theta: Double = 3.0): List<Int> {
        val cps = report.checkpoints
        if (cps.size < 2) return emptyList()
        return cps.windowed(2).mapIndexedNotNull { idx, (prev, curr) ->
            val drop = prev.derivedMetrics.process.total - curr.derivedMetrics.process.total
            if (drop > theta) idx + 1 else null
        }
    }
}
