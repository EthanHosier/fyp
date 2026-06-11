package com.github.ethanhosier.analysis.pipeline

import com.github.ethanhosier.analysis.metrics.derived.ScoringConfig
import com.github.ethanhosier.analysis.miner.RefactoringMinerRunner

object ReportAssembler {
    fun assemble(
        phaseA: PhaseAResult,
        config: ScoringConfig = ScoringConfig.PRODUCTION,
    ): AnalysisReport = buildAnalysisReport(
        trace = phaseA.trace,
        reconstruction = phaseA.reconstruction,
        metrics = phaseA.metrics,
        miner = rehydrateMinerSpecs(phaseA),
        alternative = phaseA.alternative,
        diffs = phaseA.diffs,
        trackedCodeSmells = phaseA.trackedCodeSmells,
        trackedDuplication = phaseA.trackedDuplication,
        parallelism = phaseA.parallelism,
        metricsDurationMs = phaseA.metricsDurationMs,
        reorderTrajectories = phaseA.reorderTrajectories,
        augmentedAltMetricsBySha = phaseA.augmentedAltMetricsBySha,
        reworkSummary = phaseA.reworkSummary,
        config = config,
    )

    private fun rehydrateMinerSpecs(phaseA: PhaseAResult): RefactoringMinerRunner.Summary {
        if (phaseA.specsByStepIndex.isEmpty()) return phaseA.miner
        val rehydrated = phaseA.miner.steps.map { step ->
            val sideSpec = phaseA.specsByStepIndex[step.stepIndex]
            if (sideSpec != null && step.spec == null) step.copy(spec = sideSpec) else step
        }
        return phaseA.miner.copy(steps = rehydrated)
    }
}
