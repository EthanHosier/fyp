
package com.github.ethanhosier.analysis.pipeline

import com.github.ethanhosier.analysis.alternative.AlternativeTrajectoryRunner
import com.github.ethanhosier.analysis.diffs.DiffsRunner
import com.github.ethanhosier.analysis.ingest.TraceLoader
import com.github.ethanhosier.analysis.metrics.MetricsRunner
import com.github.ethanhosier.analysis.metrics.exec.CheckpointExecutorFactory
import com.github.ethanhosier.analysis.metrics.exec.LocalCheckpointExecutorFactory
import com.github.ethanhosier.analysis.metrics.gitdiff.DiffStats
import com.github.ethanhosier.analysis.metrics.model.AlternativeTrajectory
import com.github.ethanhosier.analysis.metrics.model.AnalysisReport
import com.github.ethanhosier.analysis.metrics.model.CheckpointReport
import com.github.ethanhosier.analysis.metrics.model.EventSummary
import com.github.ethanhosier.analysis.metrics.model.PmdTracking
import com.github.ethanhosier.analysis.metrics.model.RunInfo
import com.github.ethanhosier.analysis.metrics.pmd.PmdTrackingRunner
import com.github.ethanhosier.analysis.miner.RefactoringMinerRunner
import com.github.ethanhosier.analysis.model.ReconstructionResult
import com.github.ethanhosier.analysis.model.Trace
import com.github.ethanhosier.analysis.normalize.TraceNormalizer
import com.github.ethanhosier.analysis.reconstruct.GitRunner
import com.github.ethanhosier.analysis.reconstruct.ShadowRepoBuilder
import com.github.ethanhosier.analysis.refactoring.RefactoringClient
import com.github.ethanhosier.ideplugin.model.TouchedMember
import java.nio.file.Path

/**
 * One-shot orchestration of the full analysis pipeline: load a session
 * folder, normalize its events, reconstruct a shadow git repo, mine
 * refactorings, synthesise IDE-driven alternative trajectories for any
 * manual multi-checkpoint refactorings, compute per-checkpoint metrics
 * (covering both the user's trace SHAs and the synthesised alt SHAs),
 * and produce an in-memory [AnalysisReport].
 *
 * The pipeline does **not** write the report to disk — callers decide.
 * The CLI writes it next to the inputs; the server returns it in the
 * HTTP response and discards the scratch directory.
 *
 * Side effect: as part of running, the reconstruct, alternative, and
 * metrics stages write `shadow-repo/`, `event-commits.json`,
 * `shadow-worktrees/`, `alternative-worktrees/`, and
 * `checkpoint-metrics/<sha>.json` into [sessionDir]. For CLI callers
 * these are intended artefacts; for server callers [sessionDir] should
 * be a temp directory that is cleaned up after [run] returns.
 *
 * [refactoringClient] drives the alternative-trajectory stage. It is
 * expected to live for the duration of the host process — boot it
 * once via `RefactoringClientFactory.create(...)` and inject it here.
 */
class AnalysisPipeline(
    private val refactoringClient: RefactoringClient,
    private val parallelism: Int = defaultParallelism(),
    /**
     * Where per-SHA metric work runs. Default keeps today's behaviour
     * (in-process worktrees + thread pool); a [com.github.ethanhosier.analysis.metrics.remote.RemoteCheckpointExecutorFactory]
     * here switches the metrics stage to fan out across AWS Lambda
     * without changing anything else in the pipeline.
     */
    private val executorFactory: CheckpointExecutorFactory = LocalCheckpointExecutorFactory(),
) {

    data class Result(
        val trace: Trace,
        val reconstruction: ReconstructionResult,
        val metricsSummary: MetricsRunner.Summary,
        val metricsDurationMs: Long,
        val minerSummary: RefactoringMinerRunner.Summary,
        val minerDurationMs: Long,
        val alternativeSummary: AlternativeTrajectoryRunner.Summary,
        val alternativeDurationMs: Long,
        val diffsSummary: DiffsRunner.Summary,
        val diffsDurationMs: Long,
        val report: AnalysisReport,
    )

    fun run(sessionDir: Path): Result {
        // Stages can each take seconds-to-minutes; without progress
        // output the CLI looks hung. Logs go to stderr so they don't
        // pollute the report JSON the CLI writes alongside.
        log("starting analysis on $sessionDir (parallelism=$parallelism)")

        val loadStart = System.currentTimeMillis()
        log("load+normalize: starting")
        val raw = TraceLoader().load(sessionDir)
        val trace = TraceNormalizer.normalize(raw, sessionDir.resolve("initial-src"))
        log("load+normalize: ${trace.events.size} event(s) in ${System.currentTimeMillis() - loadStart}ms")

        val reconstructStart = System.currentTimeMillis()
        log("reconstruct: starting")
        val reconstruction = ShadowRepoBuilder().build(sessionDir, trace)
        val uniqueShaCount = reconstruction.eventCommits.mapping.values.toSet().size
        log("reconstruct: $uniqueShaCount unique SHA(s) at ${reconstruction.repoDir} in ${System.currentTimeMillis() - reconstructStart}ms")

        // Miner runs before metrics so the alternative-trajectory stage
        // can synthesise alt SHAs and feed them into the metrics pass —
        // one parallel-worktree sweep covers both groups.
        val minerStart = System.currentTimeMillis()
        log("miner: starting RefactoringMiner sliding-window pass")
        val miner = RefactoringMinerRunner(parallelism = parallelism).run(trace, reconstruction, sessionDir)
        val minerDurationMs = System.currentTimeMillis() - minerStart
        log("miner: ${miner.steps.size} step(s) detected across ${miner.checkpointsAnalysed} checkpoint(s) in ${minerDurationMs}ms")

        val alternativeStart = System.currentTimeMillis()
        log("alt-traj: starting (refactoringClient=ready)")
        val alternative = AlternativeTrajectoryRunner(
            refactoringClient = refactoringClient,
            parallelism = parallelism,
        ).run(reconstruction, miner.steps, sessionDir)
        val alternativeDurationMs = System.currentTimeMillis() - alternativeStart
        log("alt-traj: ${alternative.synthesised.size}/${alternative.candidates} synthesised, ${alternative.skipped.size} skipped in ${alternativeDurationMs}ms")

        val metricsStart = System.currentTimeMillis()
        val altShas = alternative.synthesised.map { it.altSha }
        val totalShas = reconstruction.eventCommits.mapping.values.toSet().size + altShas.size
        log("metrics: starting on $totalShas SHA(s) (${altShas.size} alt) at parallelism=$parallelism")
        val metrics = executorFactory.create(
            shadowRepoDir = reconstruction.repoDir,
            sessionDir = sessionDir,
            parallelism = parallelism,
        ).use { executor ->
            MetricsRunner(executor).run(
                reconstruction = reconstruction,
                alternativeShas = altShas,
                alternativeFromShas = alternative.synthesised.map { it.fromSha },
            )
        }
        val metricsDurationMs = System.currentTimeMillis() - metricsStart
        log("metrics: ${metrics.computed} computed, ${metrics.buildOk} build-ok, ${metrics.testsOk} tests-ok in ${metricsDurationMs}ms")

        val diffsStart = System.currentTimeMillis()
        log("diffs: starting")
        val diffs = DiffsRunner(GitRunner(reconstruction.repoDir)).run(reconstruction, miner.steps, alternative)
        val diffsDurationMs = System.currentTimeMillis() - diffsStart
        log("diffs: ${diffs.checkpointPatches.size} checkpoint, ${diffs.refactoringPatches.size} refactoring, ${diffs.alternativePatches.size} alternative patch(es) in ${diffsDurationMs}ms")

        val pmdTrackingStart = System.currentTimeMillis()
        log("pmd-tracking: starting")
        val altPairs = alternative.synthesised.map { it.fromSha to it.altSha }
        val pmdTracking = PmdTrackingRunner(GitRunner(reconstruction.repoDir)).run(
            orderedUserCheckpoints = metrics.checkpoints,
            alternativePairs = altPairs,
            alternativeCheckpoints = metrics.alternativeCheckpoints,
        )
        val pmdTrackingDurationMs = System.currentTimeMillis() - pmdTrackingStart
        log("pmd-tracking: ${pmdTracking.trackingBySha.size} user, ${pmdTracking.alternativeTrackingBySha.size} alt in ${pmdTrackingDurationMs}ms")

        val report = buildAnalysisReport(
            trace = trace,
            reconstruction = reconstruction,
            metrics = metrics,
            miner = miner,
            alternative = alternative,
            diffs = diffs,
            pmdTracking = pmdTracking,
            parallelism = parallelism,
            metricsDurationMs = metricsDurationMs,
        )

        return Result(
            trace = trace,
            reconstruction = reconstruction,
            metricsSummary = metrics,
            metricsDurationMs = metricsDurationMs,
            minerSummary = miner,
            minerDurationMs = minerDurationMs,
            alternativeSummary = alternative,
            alternativeDurationMs = alternativeDurationMs,
            diffsSummary = diffs,
            diffsDurationMs = diffsDurationMs,
            report = report,
        )
    }

    companion object {
        fun defaultParallelism(): Int =
            (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    }
}

private fun log(msg: String) {
    System.out.println("[pipeline] $msg")
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
    alternative: AlternativeTrajectoryRunner.Summary,
    diffs: DiffsRunner.Summary,
    pmdTracking: PmdTrackingRunner.Summary,
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
            diff = metrics.diffBySha[m.sha] ?: DiffStats.ZERO,
            touchedMembers = membersBySha[m.sha]?.toList().orEmpty(),
            pmdTracking = pmdTracking.trackingBySha[m.sha] ?: PmdTracking.EMPTY,
        )
    }

    val stepsByIndex = miner.steps.associateBy { it.stepIndex }
    val altMetricsBySha = metrics.alternativeCheckpoints.associateBy { it.sha }
    val alternativeTrajectories = alternative.synthesised.mapNotNull { synth ->
        val step = stepsByIndex[synth.stepIndex] ?: return@mapNotNull null
        val spec = step.spec ?: return@mapNotNull null
        val altMetrics = altMetricsBySha[synth.altSha] ?: return@mapNotNull null
        AlternativeTrajectory(
            stepIndex = synth.stepIndex,
            fromSha = synth.fromSha,
            userToSha = synth.userToSha,
            branchRef = synth.branchRef,
            spec = spec,
            altCheckpoint = CheckpointReport(
                sha = synth.altSha,
                // Alt SHAs aren't landed on by user events.
                events = emptyList(),
                metrics = altMetrics,
                diff = metrics.diffBySha[synth.altSha] ?: DiffStats.ZERO,
                touchedMembers = emptyList(),
                pmdTracking = pmdTracking.alternativeTrackingBySha[synth.altSha] ?: PmdTracking.EMPTY,
            ),
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
        refactoringSteps = miner.steps,
        trajectory = computeTrajectory(checkpoints),
        checkpointPatches = diffs.checkpointPatches,
        refactoringPatches = diffs.refactoringPatches,
        alternativeTrajectories = alternativeTrajectories,
        alternativePatches = diffs.alternativePatches,
    )
}
