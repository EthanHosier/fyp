package com.github.ethanhosier.analysis.pipeline

import com.github.ethanhosier.analysis.ingest.TraceLoader
import com.github.ethanhosier.analysis.metrics.MetricsRunner
import com.github.ethanhosier.analysis.metrics.model.AnalysisReport
import com.github.ethanhosier.analysis.metrics.model.CheckpointReport
import com.github.ethanhosier.analysis.metrics.model.EventSummary
import com.github.ethanhosier.analysis.metrics.model.RunInfo
import com.github.ethanhosier.analysis.metrics.model.TrajectoryStats
import com.github.ethanhosier.analysis.miner.RefactoringMinerRunner
import com.github.ethanhosier.analysis.model.ReconstructionResult
import com.github.ethanhosier.analysis.model.Trace
import com.github.ethanhosier.analysis.normalize.TraceNormalizer
import com.github.ethanhosier.analysis.reconstruct.ShadowRepoBuilder
import com.github.ethanhosier.ideplugin.model.TouchedMember
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
    val membersBySha = LinkedHashMap<String, LinkedHashSet<TouchedMember>>()
    val mapping = reconstruction.eventCommits.mapping
    for (event in trace.events) {
        val sha = mapping[event.id] ?: continue
        val eventMembers = LinkedHashSet<TouchedMember>()
        for (snap in event.changedFiles) {
            eventMembers.addAll(snap.touchedMembers)
        }
        eventsBySha.getOrPut(sha) { mutableListOf() }.add(
            EventSummary(
                id = event.id,
                type = event.type,
                timestamp = event.timestamp,
                touchedMembers = eventMembers.toList(),
                refactoringId = event.payload["refactoringId"],
            ),
        )
        membersBySha.getOrPut(sha) { LinkedHashSet() }.addAll(eventMembers)
    }

    val checkpoints = metrics.checkpoints.map { m ->
        val events = eventsBySha[m.sha].orEmpty()
        CheckpointReport(
            sha = m.sha,
            events = events,
            metrics = m,
            diff = metrics.diffBySha[m.sha] ?: com.github.ethanhosier.analysis.metrics.gitdiff.DiffStats.ZERO,
            touchedMembers = membersBySha[m.sha]?.toList().orEmpty(),
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
        trajectory = computeTrajectory(checkpoints),
    )
}

private const val TOP_N = 10

private fun computeTrajectory(checkpoints: List<CheckpointReport>): TrajectoryStats {
    if (checkpoints.isEmpty()) return TrajectoryStats.ZERO

    // checkpoints[0] is the starting state: its tree is identical to the seed
    // (SESSION_STARTED etc. don't mutate files, so the first event commit
    // collapses onto the seed). Real transitions are [0]→[1], [1]→[2], ...,
    // so the number of steps is checkpoints.size - 1.
    val transitions = checkpoints.drop(1)
    val numSteps = transitions.size

    val churns = transitions.map { it.diff.totalChurn }
    val totalChurn = churns.sum()
    val perFileTouchCount = LinkedHashMap<String, Int>()
    val perFileChurn = LinkedHashMap<String, Int>()
    for (c in transitions) {
        for (pf in c.diff.perFileChurn) {
            perFileTouchCount.merge(pf.path, 1) { a, b -> a + b }
            perFileChurn.merge(pf.path, pf.linesAdded + pf.linesDeleted) { a, b -> a + b }
        }
    }
    val topNChurn = perFileChurn.values.sortedDescending().take(TOP_N).sum()
    val churnTopNShare = if (totalChurn == 0) 0.0 else topNChurn.toDouble() / totalChurn

    val allEventTs = checkpoints.flatMap { it.events.map { e -> e.timestamp } }
    val totalElapsedMs = if (allEventTs.isEmpty()) 0L else allEventTs.max() - allEventTs.min()

    val totalBrokenMs = computeBrokenTime(checkpoints)

    return TrajectoryStats(
        numSteps = numSteps,
        totalChurn = totalChurn,
        avgChurnPerStep = if (numSteps == 0) 0.0 else totalChurn.toDouble() / numSteps,
        maxChurnOnStep = churns.maxOrNull() ?: 0,
        totalFilesTouched = perFileTouchCount.size,
        perFileTouchCount = perFileTouchCount,
        retouchCount = perFileTouchCount.values.sumOf { if (it > 1) it - 1 else 0 },
        churnTopNShare = churnTopNShare,
        topN = TOP_N,
        totalElapsedMs = totalElapsedMs,
        totalBrokenMs = totalBrokenMs,
    )
}

/**
 * Walk checkpoints in order. A checkpoint is healthy iff build + tests both
 * pass. When we enter a failing run, remember its first event timestamp;
 * when we hit a healthy checkpoint, add the elapsed ms to the broken total.
 * If the trajectory ends failing, charge the interval up to its last event.
 */
private fun computeBrokenTime(checkpoints: List<CheckpointReport>): Long {
    fun healthy(c: CheckpointReport) = c.metrics.build.success && c.metrics.tests.success
    fun firstTs(c: CheckpointReport) = c.events.minOfOrNull { it.timestamp }
    fun lastTs(c: CheckpointReport) = c.events.maxOfOrNull { it.timestamp }

    var broken = 0L
    var failStart: Long? = null
    for (c in checkpoints) {
        if (!healthy(c)) {
            if (failStart == null) failStart = firstTs(c)
        } else {
            val start = failStart
            val end = firstTs(c)
            if (start != null && end != null) broken += end - start
            failStart = null
        }
    }
    if (failStart != null) {
        val end = checkpoints.lastOrNull()?.let(::lastTs)
        if (end != null) broken += end - failStart
    }
    return broken
}
