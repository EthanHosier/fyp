package com.github.ethanhosier.analysis.pipeline

import com.github.ethanhosier.analysis.metrics.derived.ScoringConfig
import com.github.ethanhosier.analysis.miner.RefactoringMinerRunner

/**
 * Phase B: pure in-memory assembly of an [AnalysisReport] from a frozen
 * [PhaseAResult]. No I/O, no concurrency, no expensive runners — just
 * the report shape + derived metrics + divergence points + advice.
 *
 * Splitting this out lets callers (CLI re-runs, tests, the Phase 1.3
 * cached `:phaseB` task) iterate on assembly without re-paying the cost
 * of reconstruction / metrics / synthesis. `AnalysisPipeline.run`
 * continues to chain A → B internally; no behavioural change for
 * existing call sites.
 */
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

    /**
     * `RefactoringStep.spec` is `@Transient` (excluded from JSON to keep
     * the dashboard schema slim). Phase A persists specs in a side table
     * on [PhaseAResult]; here we reattach them so the assembly pass sees
     * a fully-populated miner summary regardless of whether the input
     * came from a live pipeline run or a `:phaseA` JSON dump.
     */
    private fun rehydrateMinerSpecs(phaseA: PhaseAResult): RefactoringMinerRunner.Summary {
        if (phaseA.specsByStepIndex.isEmpty()) return phaseA.miner
        val rehydrated = phaseA.miner.steps.map { step ->
            val sideSpec = phaseA.specsByStepIndex[step.stepIndex]
            if (sideSpec != null && step.spec == null) step.copy(spec = sideSpec) else step
        }
        return phaseA.miner.copy(steps = rehydrated)
    }
}
