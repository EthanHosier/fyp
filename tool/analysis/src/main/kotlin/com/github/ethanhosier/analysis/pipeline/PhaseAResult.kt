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

/**
 * Frozen output of Phase A — every artefact `ReportAssembler.assemble`
 * needs to produce a final [AnalysisReport]. Captures the entire result
 * of the expensive part of the pipeline (load + reconstruct + mine +
 * synthesise + metrics + diffs + tracking) so Phase B (report assembly,
 * derived metrics, divergence points, advice) can be re-run cheaply
 * against cached Phase A output.
 *
 * Field-for-field this is exactly the parameter list of the legacy
 * `buildAnalysisReport` function — discover-and-mirror rather than
 * invent. The split is purely an internal refactor; `AnalysisPipeline.run`
 * still chains A → B in-process and produces a full report end-to-end.
 *
 * Round-trip note: every field is serialisable via kotlinx-serialization.
 * `ReconstructionResult.repoDir` uses [com.github.ethanhosier.analysis.pipeline.serialization.PathAsStringSerializer]
 * to render the Path as its string form. The encoded JSON is lossless
 * w.r.t. anything Phase B reads — `ReportAssembler.assemble(decoded, config)`
 * produces an [AnalysisReport] equal to the in-process version.
 */
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
    /** CheckpointMetrics keyed by altSha across single-step alts and
     *  reorder per-step alts (terminals included via Slice 2b alias).
     *  Null lets the assembler fall back to
     *  `metrics.alternativeCheckpoints.associateBy { it.sha }` — same
     *  default as the legacy `buildAnalysisReport`. */
    val augmentedAltMetricsBySha: Map<String, CheckpointMetrics>? = null,
    val reworkSummary: ReworkSynthesiser.Summary? = null,
    /** Side table: `RefactoringStep.spec` is `@Transient` on the step
     *  itself (excluded from `AnalysisReport` JSON + dashboard TS to
     *  keep the report compact), but Phase B's divergence-point /
     *  reorder-alt construction does read it. We keep specs here keyed
     *  by `stepIndex` so the Phase-A JSON dump is lossless, and
     *  `ReportAssembler` re-attaches them on decode. Empty when the
     *  in-process pipeline didn't populate it — `ReportAssembler` falls
     *  back to whatever specs `miner.steps` already carry. */
    val specsByStepIndex: Map<Int, RefactoringSpec> = emptyMap(),
)
