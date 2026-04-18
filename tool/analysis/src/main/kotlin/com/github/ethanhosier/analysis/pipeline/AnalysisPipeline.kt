package com.github.ethanhosier.analysis.pipeline

import com.github.ethanhosier.analysis.ingest.TraceLoader
import com.github.ethanhosier.analysis.metrics.MetricsRunner
import com.github.ethanhosier.analysis.metrics.model.AnalysisReport
import com.github.ethanhosier.analysis.metrics.model.CheckpointReport
import com.github.ethanhosier.analysis.metrics.model.EventSummary
import com.github.ethanhosier.analysis.metrics.model.RunInfo
import com.github.ethanhosier.analysis.miner.RefactoringMinerRunner
import com.github.ethanhosier.analysis.model.ReconstructionResult
import com.github.ethanhosier.analysis.model.Trace
import com.github.ethanhosier.analysis.normalize.TraceNormalizer
import com.github.ethanhosier.analysis.reconstruct.ShadowRepoBuilder
import java.nio.file.Path

/**
 * One-shot orchestration of the full analysis pipeline: load a session
 * folder, normalize its events, reconstruct a shadow git repo, compute
 * per-checkpoint metrics, and produce an in-memory [AnalysisReport].
 *
 * The pipeline does **not** write the report to disk — callers decide.
 * The CLI writes it next to the inputs; the server returns it in the
 * HTTP response and discards the scratch directory.
 *
 * Side effect: as part of running, the reconstruct and metrics stages
 * write `shadow-repo/`, `event-commits.json`, `shadow-worktrees/`, and
 * `checkpoint-metrics/<sha>.json` into [sessionDir]. For CLI callers
 * these are intended artefacts; for server callers [sessionDir] should
 * be a temp directory that is cleaned up after [run] returns.
 */
class AnalysisPipeline(
    private val parallelism: Int = defaultParallelism(),
) {

    data class Result(
        val trace: Trace,
        val reconstruction: ReconstructionResult,
        val metricsSummary: MetricsRunner.Summary,
        val metricsDurationMs: Long,
        val minerSummary: RefactoringMinerRunner.Summary,
        val minerDurationMs: Long,
        val report: AnalysisReport,
    )

    fun run(sessionDir: Path): Result {
        val raw = TraceLoader().load(sessionDir)
        val trace = TraceNormalizer.normalize(raw, sessionDir.resolve("initial-src"))

        val reconstruction = ShadowRepoBuilder().build(sessionDir, trace)

        val metricsStart = System.currentTimeMillis()
        val metrics = MetricsRunner(parallelism = parallelism).run(reconstruction, sessionDir)
        val metricsDurationMs = System.currentTimeMillis() - metricsStart

        val minerStart = System.currentTimeMillis()
        val miner = RefactoringMinerRunner(parallelism = parallelism).run(trace, reconstruction, sessionDir)
        val minerDurationMs = System.currentTimeMillis() - minerStart

        val report = buildAnalysisReport(trace, reconstruction, metrics, miner, parallelism, metricsDurationMs)

        return Result(
            trace = trace,
            reconstruction = reconstruction,
            metricsSummary = metrics,
            metricsDurationMs = metricsDurationMs,
            minerSummary = miner,
            minerDurationMs = minerDurationMs,
            report = report,
        )
    }

    companion object {
        fun defaultParallelism(): Int =
            (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    }
}

/**
 * Pure in-memory assembly of an [AnalysisReport] from the pieces the
 * pipeline already has. Visible for testing — no I/O, no concurrency,
 * just grouping [trace]'s events under the SHAs they landed on.
 */
internal fun buildAnalysisReport(
    trace: Trace,
    reconstruction: ReconstructionResult,
    metrics: MetricsRunner.Summary,
    miner: RefactoringMinerRunner.Summary,
    parallelism: Int,
    metricsDurationMs: Long,
): AnalysisReport {
    // Preserve event order so each checkpoint's event list reads
    // chronologically — LinkedHashMap's insertion order is what we want.
    val eventsBySha = LinkedHashMap<String, MutableList<EventSummary>>()
    val mapping = reconstruction.eventCommits.mapping
    for (event in trace.events) {
        val sha = mapping[event.id] ?: continue
        eventsBySha.getOrPut(sha) { mutableListOf() }.add(
            EventSummary(event.id, event.type, event.timestamp),
        )
    }

    val checkpoints = metrics.checkpoints.map { m ->
        CheckpointReport(
            sha = m.sha,
            events = eventsBySha[m.sha].orEmpty(),
            metrics = m,
        )
    }

    return AnalysisReport(
        session = trace.metadata,
        run = RunInfo(
            parallelism = parallelism,
            generatedAt = System.currentTimeMillis(),
            metricsDurationMs = metricsDurationMs,
        ),
        checkpoints = checkpoints,
        manualRefactorings = miner.segments,
    )
}
