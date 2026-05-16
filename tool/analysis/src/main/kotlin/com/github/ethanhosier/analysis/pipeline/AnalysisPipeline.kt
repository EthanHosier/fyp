
package com.github.ethanhosier.analysis.pipeline

import com.github.ethanhosier.analysis.alternative.IdeRefactoringsRunner
import com.github.ethanhosier.analysis.alternative.synthesise.ReorderSynthesiser
import com.github.ethanhosier.analysis.alternative.rework.ReworkSynthesiser
import com.github.ethanhosier.analysis.alternative.validate.RefactoringStepValidator
import com.github.ethanhosier.analysis.metrics.WorktreePool
import com.github.ethanhosier.analysis.diffs.DiffsRunner
import com.github.ethanhosier.analysis.ingest.TraceLoader
import com.github.ethanhosier.analysis.metrics.MetricsRunner
import com.github.ethanhosier.analysis.metrics.cpd.CpdTrackingRunner
import com.github.ethanhosier.analysis.metrics.derived.ScoringConfig
import com.github.ethanhosier.analysis.metrics.model.CheckpointMetrics
import com.github.ethanhosier.analysis.metrics.pmd.PmdTrackingRunner
import com.github.ethanhosier.analysis.miner.RefactoringMinerRunner
import com.github.ethanhosier.analysis.model.ReconstructionResult
import com.github.ethanhosier.analysis.model.Trace
import com.github.ethanhosier.analysis.normalize.TraceNormalizer
import com.github.ethanhosier.analysis.reconstruct.GitRunner
import com.github.ethanhosier.analysis.reconstruct.ShadowRepoBuilder
import com.github.ethanhosier.analysis.refactoring.RefactoringClient
import java.nio.file.Files
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
) {

    data class Result(
        val trace: Trace,
        val reconstruction: ReconstructionResult,
        val metricsSummary: MetricsRunner.Summary,
        val metricsDurationMs: Long,
        val minerSummary: RefactoringMinerRunner.Summary,
        val minerDurationMs: Long,
        val alternativeSummary: IdeRefactoringsRunner.Summary,
        val alternativeDurationMs: Long,
        val diffsSummary: DiffsRunner.Summary,
        val diffsDurationMs: Long,
        val report: AnalysisReport,
    )

    fun run(sessionDir: Path, config: ScoringConfig = ScoringConfig.PRODUCTION): Result {
        val (phaseA, alternativeDurationMs, minerDurationMs, diffsDurationMs) = runPhaseAInternal(sessionDir)
        val report = ReportAssembler.assemble(phaseA, config)
        return Result(
            trace = phaseA.trace,
            reconstruction = phaseA.reconstruction,
            metricsSummary = phaseA.metrics,
            metricsDurationMs = phaseA.metricsDurationMs,
            minerSummary = phaseA.miner,
            minerDurationMs = minerDurationMs,
            alternativeSummary = phaseA.alternative,
            alternativeDurationMs = alternativeDurationMs,
            diffsSummary = phaseA.diffs,
            diffsDurationMs = diffsDurationMs,
            report = report,
        )
    }

    /**
     * Run only the expensive Phase A: load + reconstruct + mine +
     * synthesise + metrics + diffs + tracking. Returns the frozen
     * [PhaseAResult] without invoking [ReportAssembler]. Use this when
     * you want to cache the heavy work and iterate on report assembly
     * separately (Phase 1.3 CLI / tests).
     */
    fun runPhaseA(sessionDir: Path): PhaseAResult =
        runPhaseAInternal(sessionDir).phaseA

    private data class PhaseATiming(
        val phaseA: PhaseAResult,
        val alternativeDurationMs: Long,
        val minerDurationMs: Long,
        val diffsDurationMs: Long,
    )

    private fun runPhaseAInternal(sessionDir: Path): PhaseATiming {
        log("starting analysis on $sessionDir (parallelism=$parallelism)")
        val trace = normalizeTrace(sessionDir)
        val reconstruction = reconstructRepo(sessionDir, trace)
        val (minedRefactorings, minerDurationMs) = mineRefactorings(trace, reconstruction, sessionDir)
        val reorderSynth = synthesizeAlternativeRefactorOrderings(reconstruction, sessionDir, minedRefactorings)
        val (automaticIdeRefactoringAlternatives, alternativeDurationMs) = performManualRefactoringsAutomaticallyViaIde(reconstruction, minedRefactorings, sessionDir)
        val reworkSummary = synthesizeReworkAlternatives(reconstruction, sessionDir)
        val (metricsSummary, metricsDurationMs) = computeMetrics(
            automaticIdeRefactoringAlternatives,
            reorderSynth,
            reworkSummary,
            reconstruction,
            sessionDir
        )

        val (altMetricsByShaMut, augmentedAltCheckpoints, allAltPairs) = computeAltPairs(
            metricsSummary,
            reorderSynth,
            reworkSummary,
            automaticIdeRefactoringAlternatives
        )

        val (diffs, diffsDurationMs) = computeDiffs(reconstruction, minedRefactorings, allAltPairs)
        val trackedCodeSmells = trackCodeSmells(reconstruction, metricsSummary, allAltPairs, augmentedAltCheckpoints)
        val trackedDuplication = trackDuplication(metricsSummary, allAltPairs, augmentedAltCheckpoints)

        val phaseA = PhaseAResult(
            trace = trace,
            reconstruction = reconstruction,
            metrics = metricsSummary,
            miner = minedRefactorings,
            alternative = automaticIdeRefactoringAlternatives,
            diffs = diffs,
            trackedCodeSmells = trackedCodeSmells,
            trackedDuplication = trackedDuplication,
            parallelism = parallelism,
            metricsDurationMs = metricsDurationMs,
            reorderTrajectories = reorderSynth.trajectories,
            augmentedAltMetricsBySha = altMetricsByShaMut,
            reworkSummary = reworkSummary,
            // Side-table specs so `PhaseAResult` round-trips through JSON.
            // `RefactoringStep.spec` is `@Transient` (the dashboard report
            // doesn't carry typed specs), but Phase B assembly reads
            // `step.spec` for divergence-point construction.
            specsByStepIndex = minedRefactorings.steps.mapNotNull { s ->
                s.spec?.let { s.stepIndex to it }
            }.toMap(),
        )
        return PhaseATiming(
            phaseA = phaseA,
            alternativeDurationMs = alternativeDurationMs,
            minerDurationMs = minerDurationMs,
            diffsDurationMs = diffsDurationMs,
        )
    }

    private fun trackDuplication(
        metricsSummary: MetricsRunner.Summary,
        allAltPairs: List<Pair<String, String>>,
        augmentedAltCheckpoints: List<CheckpointMetrics>
    ): CpdTrackingRunner.Summary {
        val cpdTrackingStart = System.currentTimeMillis()
        log("cpd-tracking: starting")
        val cpdTracking = CpdTrackingRunner().run(
            orderedUserCheckpoints = metricsSummary.checkpoints,
            alternativePairs = allAltPairs,
            alternativeCheckpoints = augmentedAltCheckpoints,
        )
        val cpdTrackingDurationMs = System.currentTimeMillis() - cpdTrackingStart
        log("cpd-tracking: ${cpdTracking.trackingBySha.size} user, ${cpdTracking.alternativeTrackingBySha.size} alt in ${cpdTrackingDurationMs}ms")
        return cpdTracking
    }

    private fun trackCodeSmells(
        reconstruction: ReconstructionResult,
        metricsSummary: MetricsRunner.Summary,
        allAltPairs: List<Pair<String, String>>,
        augmentedAltCheckpoints: List<CheckpointMetrics>
    ): PmdTrackingRunner.Summary {
        val pmdTrackingStart = System.currentTimeMillis()
        log("pmd-tracking: starting")
        val pmdTracking = PmdTrackingRunner(GitRunner(reconstruction.repoDir)).run(
            orderedUserCheckpoints = metricsSummary.checkpoints,
            alternativePairs = allAltPairs,
            alternativeCheckpoints = augmentedAltCheckpoints,
        )
        val pmdTrackingDurationMs = System.currentTimeMillis() - pmdTrackingStart
        log("pmd-tracking: ${pmdTracking.trackingBySha.size} user, ${pmdTracking.alternativeTrackingBySha.size} alt in ${pmdTrackingDurationMs}ms")
        return pmdTracking
    }

    private fun computeDiffs(
        reconstruction: ReconstructionResult,
        minedRefactorings: RefactoringMinerRunner.Summary,
        allAltPairs: List<Pair<String, String>>
    ): Pair<DiffsRunner.Summary, Long> {
        val diffsStart = System.currentTimeMillis()
        log("diffs: starting")
        val diffs = DiffsRunner(GitRunner(reconstruction.repoDir))
            .run(reconstruction, minedRefactorings.steps, allAltPairs)
        val diffsDurationMs = System.currentTimeMillis() - diffsStart
        log("diffs: ${diffs.checkpointPatches.size} checkpoint, ${diffs.refactoringPatches.size} refactoring, ${diffs.alternativePatches.size} alternative patch(es) in ${diffsDurationMs}ms")
        return Pair(diffs, diffsDurationMs)
    }

    private fun computeAltPairs(
        metricsSummary: MetricsRunner.Summary,
        reorderSynth: ReorderSynthesiser.Summary,
        reworkSummary: ReworkSynthesiser.Summary,
        automaticIdeRefactoringAlternatives: IdeRefactoringsRunner.Summary
    ): Triple<MutableMap<String, CheckpointMetrics>, List<CheckpointMetrics>, List<Pair<String, String>>> {
        // Augment alt CheckpointMetrics with synthetic entries for kept
        // reorder-ordering terminal SHAs. Terminals are AST-equivalent to
        // windowToSha by construction (Slice 2b), so their metrics alias
        // the user's windowToSha CheckpointMetrics — no rebuild. Pmd/Cpd
        // tracking + DerivedMetricsRunner then have a CheckpointMetrics
        // entry for every alt step SHA, terminals included.
        val userMetricsBySha = metricsSummary.checkpoints.associateBy { it.sha }
        val altMetricsByShaMut = metricsSummary.alternativeCheckpoints.associateBy { it.sha }.toMutableMap()
        for (traj in reorderSynth.trajectories) {
            for (ord in traj.orderings) {
                if (!ord.terminalSuccess) continue
                val terminalSha = ord.stepShas.lastOrNull() ?: continue
                if (terminalSha in altMetricsByShaMut) continue
                val terminalAlias = userMetricsBySha[traj.windowToSha] ?: continue
                altMetricsByShaMut[terminalSha] = terminalAlias.copy(sha = terminalSha)
            }
        }
        // No-op rework alts (user added then removed the same content,
        // or vice versa, with no other code change) anchor their single
        // "alt checkpoint" at the same SHA as the user's pre-rework
        // state — no new commit, same tree. Alias the user's metrics
        // for that SHA so altCheckpointFor can build a CheckpointReport.
        for (rw in reworkSummary.synthesised) {
            for (sha in rw.altShas) {
                if (sha in altMetricsByShaMut) continue
                val alias = userMetricsBySha[sha] ?: continue
                altMetricsByShaMut[sha] = alias
            }
        }
        val augmentedAltCheckpoints = altMetricsByShaMut.values.toList()

        // Per-step alt pairs feed Diffs / Pmd / Cpd tracking for every
        // applied alt step. Single-step alts contribute one pair; reorder
        // orderings contribute N pairs (chained: each step's `from` is
        // the previous step's altSha, except step 0 which anchors at
        // windowFromSha).
        val singleAltPairs = mutableListOf<Pair<String, String>>()
        for (synth in automaticIdeRefactoringAlternatives.synthesised) {
            synth.altShas.forEachIndexed { i, sha ->
                val from = if (i == 0) synth.fromSha else synth.altShas[i - 1]
                singleAltPairs += from to sha
            }
        }
        val reorderAltPairs = mutableListOf<Pair<String, String>>()
        for (traj in reorderSynth.trajectories) {
            for (ord in traj.orderings) {
                if (!ord.terminalSuccess) continue
                ord.stepShas.forEachIndexed { i, sha ->
                    val from = if (i == 0) traj.windowFromSha else ord.stepShas[i - 1]
                    reorderAltPairs += from to sha
                }
            }
        }
        val reworkAltPairs = mutableListOf<Pair<String, String>>()
        for (rw in reworkSummary.synthesised) {
            rw.altShas.forEachIndexed { i, sha ->
                val from = if (i == 0) rw.fromSha else rw.altShas[i - 1]
                reworkAltPairs += from to sha
            }
        }
        val allAltPairs = singleAltPairs + reorderAltPairs + reworkAltPairs
        return Triple(altMetricsByShaMut, augmentedAltCheckpoints, allAltPairs)
    }

    private fun computeMetrics(
        automaticIdeRefactoringAlternatives: IdeRefactoringsRunner.Summary,
        reorderSynth: ReorderSynthesiser.Summary,
        reworkSummary: ReworkSynthesiser.Summary,
        reconstruction: ReconstructionResult,
        sessionDir: Path
    ): Pair<MetricsRunner.Summary, Long> {
        val metricsStart = System.currentTimeMillis()
        // Per-group SHA chain: each group contributes one entry per
        // applied refactoring + an optional trailing residual SHA. The
        // chain is parented sequentially so each SHA's `from` is the
        // previous SHA in the same group (or `fromSha` for the first).
        val altShas = mutableListOf<String>()
        val altFromShas = mutableListOf<String>()
        for (synth in automaticIdeRefactoringAlternatives.synthesised) {
            synth.altShas.forEachIndexed { i, sha ->
                val from = if (i == 0) synth.fromSha else synth.altShas[i - 1]
                altShas += sha
                altFromShas += from
            }
        }
        // Reorder intermediates piggy-back on the alt-shas bucket so
        // MetricsRunner's existing dedup + sequential-walk path covers
        // them. Terminal stepShas of successful orderings are excluded:
        // they're AST-equivalent to windowToSha by construction (the
        // synthesiser already filtered divergent terminals), so the
        // user's windowToSha checkpoint metrics are reused for them at
        // attach time — saves a build+test per ordering. Failed
        // orderings (terminalSuccess=false) are skipped entirely per
        // Slice 2b scope.
        val reorderIntermediateShas = mutableListOf<String>()
        val reorderIntermediateFromShas = mutableListOf<String>()
        for (traj in reorderSynth.trajectories) {
            for (ord in traj.orderings) {
                if (!ord.terminalSuccess) continue
                val intermediates = ord.stepShas.dropLast(1)
                intermediates.forEachIndexed { i, sha ->
                    val from = if (i == 0) traj.windowFromSha else ord.stepShas[i - 1]
                    reorderIntermediateShas += sha
                    reorderIntermediateFromShas += from
                }
            }
        }
        // Rework alt SHAs: same chain pattern as the IDE-driven alts —
        // each step's parent is the previous step's altSha, except the
        // first step which parents at the rework's fromSha.
        val reworkShas = mutableListOf<String>()
        val reworkFromShas = mutableListOf<String>()
        for (rw in reworkSummary.synthesised) {
            rw.altShas.forEachIndexed { i, sha ->
                val from = if (i == 0) rw.fromSha else rw.altShas[i - 1]
                reworkShas += sha
                reworkFromShas += from
            }
        }
        val mergedAltShas = altShas + reorderIntermediateShas + reworkShas
        val mergedAltFromShas = altFromShas + reorderIntermediateFromShas + reworkFromShas
        val totalShas = reconstruction.eventCommits.mapping.values.toSet().size + mergedAltShas.size
        log(
            "metrics: starting on $totalShas SHA(s) (${altShas.size} alt-single, " +
                    "${reorderIntermediateShas.size} reorder-intermediate, " +
                    "${reworkShas.size} rework) at parallelism=$parallelism",
        )
        val metrics = MetricsRunner(parallelism = parallelism).run(
            reconstruction = reconstruction,
            sessionFolder = sessionDir,
            alternativeShas = mergedAltShas,
            alternativeFromShas = mergedAltFromShas,
        )
        val metricsDurationMs = System.currentTimeMillis() - metricsStart
        log("metrics: ${metrics.computed} computed, ${metrics.buildOk} build-ok, ${metrics.testsOk} tests-ok in ${metricsDurationMs}ms")
        return Pair(metrics, metricsDurationMs)
    }

    private fun synthesizeReworkAlternatives(
        reconstruction: ReconstructionResult,
        sessionDir: Path
    ): ReworkSynthesiser.Summary {
        // Rework alt synthesis: detect chunk-level rework (user added
        // then removed the same content, or vice versa) and surgically
        // replay the trajectory with the round-trip removed. Produces
        // alt SHAs that feed into the same metrics + diff + tracking
        // pipeline as the other alts.
        val reworkStart = System.currentTimeMillis()
        log("rework: starting")
        val reworkSummary = ReworkSynthesiser().run(reconstruction, sessionDir)
        val reworkDurationMs = System.currentTimeMillis() - reworkStart
        log("rework: ${reworkSummary.synthesised.size}/${reworkSummary.candidates} synthesised, ${reworkSummary.failed.size} failed in ${reworkDurationMs}ms")
        return reworkSummary
    }

    private fun performManualRefactoringsAutomaticallyViaIde(
        reconstruction: ReconstructionResult,
        minedRefactorings: RefactoringMinerRunner.Summary,
        sessionDir: Path
    ): Pair<IdeRefactoringsRunner.Summary, Long> {
        val alternativeStart = System.currentTimeMillis()
        log("alt-traj: starting (refactoringClient=ready)")
        val alternative = IdeRefactoringsRunner(
            refactoringClient = refactoringClient,
        ).run(reconstruction, minedRefactorings.steps, sessionDir)
        val alternativeDurationMs = System.currentTimeMillis() - alternativeStart
        log("alt-traj: ${alternative.synthesised.size}/${alternative.candidates} synthesised, ${alternative.skipped.size} skipped in ${alternativeDurationMs}ms")
        return Pair(alternative, alternativeDurationMs)
    }

    private fun synthesizeAlternativeRefactorOrderings(
        reconstruction: ReconstructionResult,
        sessionDir: Path,
        minedRefactorings: RefactoringMinerRunner.Summary,
    ): ReorderSynthesiser.Summary {
        // Multi-step alt-trajectory synthesis: for each VALID
        // reorder window, materialise every alt ordering as a chain
        // of git commits in the shadow repo, with an ordered-prefix
        // cache so orderings sharing a prefix re-use materialised
        // commits. Replaces the previous inspection-only
        // ReorderWindowLogger — same window-splitting + summary
        // counts, plus the synthesis loop. See
        // `tool/plans/PLAN-reorder-synthesis.md`.
        val validationsByIndex = validateMinedRefactorings(reconstruction, sessionDir, minedRefactorings)
        val reorderStart = System.currentTimeMillis()
        val reorderShadowGit = GitRunner(reconstruction.repoDir)
        val reorderPool = WorktreePool(
            shadowRepo = reconstruction.repoDir,
            baseDir = sessionDir.resolve("reorder-worktrees"),
            size = parallelism,
        )
        val reorderSynth = try {
            ReorderSynthesiser(
                client = refactoringClient,
                shadowGit = reorderShadowGit,
                pool = reorderPool,
            ).run(
                steps = minedRefactorings.steps,
                validations = validationsByIndex,
                log = { line -> log(line) },
            )
        } finally {
            reorderPool.close()
        }
        log(
            "reorder synth: ${reorderSynth.eligibleWindows} eligible window(s), " +
                    "${reorderSynth.singletonWindows} singleton(s) of " +
                    "${reorderSynth.totalWindows} total " +
                    "(${reorderSynth.typedCount} typed, ${reorderSynth.untypedCount} untyped, " +
                    "${reorderSynth.divergentCount} diverged, ${reorderSynth.refactorFailedCount} failed); " +
                    "synthesised ${reorderSynth.orderingsSynthesised} orderings, " +
                    "${reorderSynth.commitsCreated} commits, " +
                    "${reorderSynth.appliesIssued} applies, ${reorderSynth.backtracksIssued} backtracks; " +
                    "terminal AST: ${reorderSynth.terminalsAstMatched} matched, " +
                    "${reorderSynth.terminalsAstDiverged} diverged (filtered out) of " +
                    "${reorderSynth.terminalsChecked} checked in " +
                    "${System.currentTimeMillis() - reorderStart}ms",
        )
        return reorderSynth
    }

    private fun validateMinedRefactorings(
        reconstruction: ReconstructionResult,
        sessionDir: Path,
        minedRefactorings: RefactoringMinerRunner.Summary
    ): Map<Int, RefactoringStepValidator.StepValidation> {
        // Per-step ground-truth check before windowing: replay each
        // typed spec from `fromSha` and compare the resulting AST to
        // the user's `toSha`. Only steps that reproduce the user's
        // end-state are eligible to participate in a reorder window;
        // anything else (apply failed, AST diverged, untyped) becomes
        // a window splitter. See `tool/plans/PLAN-step-validator.md`.
        val validatorStart = System.currentTimeMillis()
        val shadowGitForValidator = GitRunner(reconstruction.repoDir)
        val validatorPool = WorktreePool(
            shadowRepo = reconstruction.repoDir,
            baseDir = sessionDir.resolve("validator-worktrees"),
            size = parallelism,
        )
        val validatorDebugDir = sessionDir.resolve("validator-debug").also {
            it.toFile().deleteRecursively()
            Files.createDirectories(it)
        }
        val validations = try {
            RefactoringStepValidator(
                client = refactoringClient,
                pool = validatorPool,
                shadowGit = shadowGitForValidator,
                debugDumpDir = validatorDebugDir,
            ).validate(minedRefactorings.steps)
        } finally {
            validatorPool.close()
        }
        val validationsByIndex = validations.associateBy { it.stepIndex }
        val stepsByIndex = minedRefactorings.steps.associateBy { it.stepIndex }
        validations.sortedBy { it.stepIndex }.forEach { v ->
            val type = stepsByIndex[v.stepIndex]?.refactoring?.type ?: "?"
            val tail = v.reason?.let { " — $it" } ?: ""
            val files = v.divergedFiles?.takeIf { it.isNotEmpty() }?.let { " $it" } ?: ""
            log("validator: step #${v.stepIndex} [$type] ${v.status}$tail$files")
        }
        val validatorDurationMs = System.currentTimeMillis() - validatorStart
        val validCount = validations.count { it.status == RefactoringStepValidator.Status.VALID }
        val divergedCount = validations.count { it.status == RefactoringStepValidator.Status.AST_DIVERGED }
        val failedCount = validations.count { it.status == RefactoringStepValidator.Status.REFACTOR_FAILED }
        val untypedCount = validations.count { it.status == RefactoringStepValidator.Status.UNTYPED }
        log(
            "validator: summary valid=$validCount diverged=$divergedCount " +
                    "refactorFailed=$failedCount untyped=$untypedCount in ${validatorDurationMs}ms",
        )
        if (divergedCount > 0) {
            log("validator: divergent step dumps under $validatorDebugDir (`*.ours` vs `*.user` per file)")
        }
        return validationsByIndex
    }

    private fun mineRefactorings(
        trace: Trace,
        reconstruction: ReconstructionResult,
        sessionDir: Path
    ): Pair<RefactoringMinerRunner.Summary, Long> {
        val minerStart = System.currentTimeMillis()
        log("miner: starting RefactoringMiner sliding-window pass")
        val miner = RefactoringMinerRunner(parallelism = parallelism).run(trace, reconstruction, sessionDir)
        val minerDurationMs = System.currentTimeMillis() - minerStart
        log("miner: ${miner.steps.size} step(s) detected across ${miner.checkpointsAnalysed} checkpoint(s) in ${minerDurationMs}ms")
        return Pair(miner, minerDurationMs)
    }

    private fun reconstructRepo(
        sessionDir: Path,
        trace: Trace
    ): ReconstructionResult {
        val reconstructStart = System.currentTimeMillis()
        log("reconstruct: starting")
        val reconstruction = ShadowRepoBuilder().build(sessionDir, trace)
        val uniqueShaCount = reconstruction.eventCommits.mapping.values.toSet().size
        log("reconstruct: $uniqueShaCount unique SHA(s) at ${reconstruction.repoDir} in ${System.currentTimeMillis() - reconstructStart}ms")
        return reconstruction
    }

    private fun normalizeTrace(sessionDir: Path): Trace {
        val loadStart = System.currentTimeMillis()
        log("load+normalize: starting")
        val raw = TraceLoader().load(sessionDir)
        val trace = TraceNormalizer.normalize(raw, sessionDir.resolve("initial-src"))
        log("load+normalize: ${trace.events.size} event(s) in ${System.currentTimeMillis() - loadStart}ms")
        return trace
    }

    companion object {
        internal fun defaultParallelism(): Int =
            (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    }
}

private fun log(msg: String) {
    println("[pipeline] $msg")
}

