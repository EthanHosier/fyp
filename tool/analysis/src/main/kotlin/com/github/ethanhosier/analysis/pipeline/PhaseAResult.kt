package com.github.ethanhosier.analysis.pipeline

import com.github.ethanhosier.analysis.alternative.IdeRefactoringsRunner
import com.github.ethanhosier.analysis.alternative.rework.ReworkSynthesiser
import com.github.ethanhosier.analysis.diffs.DiffsRunner
import com.github.ethanhosier.analysis.metrics.MetricsRunner
import com.github.ethanhosier.analysis.metrics.cpd.CpdTrackingRunner
import com.github.ethanhosier.analysis.metrics.model.CheckpointMetrics
import com.github.ethanhosier.analysis.metrics.model.ReorderTrajectory
import com.github.ethanhosier.analysis.metrics.pmd.PmdTrackingRunner
import com.github.ethanhosier.analysis.miner.RefactoringMinerRunner
import com.github.ethanhosier.analysis.miner.model.RefactoringSpec
import com.github.ethanhosier.analysis.model.ReconstructionResult
import com.github.ethanhosier.analysis.model.Trace
import kotlinx.serialization.Serializable

@Serializable
data class PhaseAResult(
    val trace: Trace,
    val reconstruction: ReconstructionResult,
    val metrics: MetricsRunner.Summary,
    val miner: RefactoringMinerRunner.Summary,
    val alternative: IdeRefactoringsRunner.Summary,
    val diffs: DiffsRunner.Summary,
    val trackedCodeSmells: PmdTrackingRunner.Summary,
    val trackedDuplication: CpdTrackingRunner.Summary,
    val parallelism: Int,
    val metricsDurationMs: Long,
    val reorderTrajectories: List<ReorderTrajectory>,
    val augmentedAltMetricsBySha: Map<String, CheckpointMetrics>? = null,
    val reworkSummary: ReworkSynthesiser.Summary? = null,
    val specsByStepIndex: Map<Int, RefactoringSpec> = emptyMap(),
)
