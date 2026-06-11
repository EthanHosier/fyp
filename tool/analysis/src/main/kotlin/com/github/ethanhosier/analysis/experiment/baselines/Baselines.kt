package com.github.ethanhosier.analysis.experiment.baselines

import com.github.ethanhosier.analysis.pipeline.AnalysisReport

object Baselines {

    fun endpointOnlyImproved(report: AnalysisReport): Boolean {
        val cps = report.checkpoints
        if (cps.size < 2) return false
        val first = cps.first().derivedMetrics.cleanliness?.score ?: return false
        val last = cps.last().derivedMetrics.cleanliness?.score ?: return false
        return last > first
    }

    fun perStepRegression(report: AnalysisReport, theta: Double = 3.0): List<Int> {
        val cps = report.checkpoints
        if (cps.size < 2) return emptyList()
        return cps.windowed(2).mapIndexedNotNull { idx, (prev, curr) ->
            val drop = prev.derivedMetrics.process.total - curr.derivedMetrics.process.total
            if (drop > theta) idx + 1 else null
        }
    }
}
