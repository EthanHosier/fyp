
package com.github.ethanhosier.analysis.pipeline

import com.github.ethanhosier.analysis.advice.TrajectoryAdvisor
import com.github.ethanhosier.analysis.alternative.AlternativeTrajectoryRunner
import com.github.ethanhosier.analysis.alternative.synthesise.ReorderSynthesiser
import com.github.ethanhosier.analysis.alternative.rework.ReworkSynthesiser
import com.github.ethanhosier.analysis.alternative.validate.RefactoringStepValidator
import com.github.ethanhosier.analysis.metrics.WorktreePool
import com.github.ethanhosier.analysis.diffs.DiffsRunner
import com.github.ethanhosier.analysis.ingest.TraceLoader
import com.github.ethanhosier.analysis.metrics.MetricsRunner
import com.github.ethanhosier.analysis.metrics.derived.DerivedMetricsRunner
import com.github.ethanhosier.analysis.metrics.gitdiff.DiffStats
import com.github.ethanhosier.analysis.metrics.model.AlternativeTrajectory
import com.github.ethanhosier.analysis.metrics.cpd.CpdTrackingRunner
import com.github.ethanhosier.analysis.metrics.model.AnalysisReport
import com.github.ethanhosier.analysis.metrics.model.CheckpointReport
import com.github.ethanhosier.analysis.metrics.model.CpdTracking
import com.github.ethanhosier.analysis.metrics.model.DerivedMetrics
import com.github.ethanhosier.analysis.divergence.DivergencePointBuilder
import com.github.ethanhosier.analysis.metrics.model.DivergenceKind
import com.github.ethanhosier.analysis.metrics.model.EventSummary
import com.github.ethanhosier.analysis.metrics.model.PmdTracking
import com.github.ethanhosier.analysis.metrics.model.RunInfo
import com.github.ethanhosier.analysis.metrics.model.UserGitCommit
import com.github.ethanhosier.analysis.metrics.pmd.PmdTrackingRunner
import com.github.ethanhosier.analysis.miner.RefactoringMinerRunner
import com.github.ethanhosier.analysis.model.ReconstructionResult
import com.github.ethanhosier.analysis.model.Trace
import com.github.ethanhosier.analysis.normalize.TraceNormalizer
import com.github.ethanhosier.analysis.reconstruct.GitRunner
import com.github.ethanhosier.analysis.reconstruct.ShadowRepoBuilder
import com.github.ethanhosier.analysis.refactoring.RefactoringClient
import com.github.ethanhosier.ideplugin.model.EventType
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
            java.nio.file.Files.createDirectories(it)
        }
        val validations = try {
            RefactoringStepValidator(
                client = refactoringClient,
                pool = validatorPool,
                shadowGit = shadowGitForValidator,
                debugDumpDir = validatorDebugDir,
            ).validate(miner.steps)
        } finally {
            validatorPool.close()
        }
        val validationsByIndex = validations.associateBy { it.stepIndex }
        val stepsByIndex = miner.steps.associateBy { it.stepIndex }
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

        // Multi-step alt-trajectory synthesis: for each VALID
        // reorder window, materialise every alt ordering as a chain
        // of git commits in the shadow repo, with an ordered-prefix
        // cache so orderings sharing a prefix re-use materialised
        // commits. Replaces the previous inspection-only
        // ReorderWindowLogger — same window-splitting + summary
        // counts, plus the synthesis loop. See
        // `tool/plans/PLAN-reorder-synthesis.md`.
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
                steps = miner.steps,
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

        val alternativeStart = System.currentTimeMillis()
        log("alt-traj: starting (refactoringClient=ready)")
        val alternative = AlternativeTrajectoryRunner(
            refactoringClient = refactoringClient,
        ).run(reconstruction, miner.steps, sessionDir)
        val alternativeDurationMs = System.currentTimeMillis() - alternativeStart
        log("alt-traj: ${alternative.synthesised.size}/${alternative.candidates} synthesised, ${alternative.skipped.size} skipped in ${alternativeDurationMs}ms")

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

        val metricsStart = System.currentTimeMillis()
        // Per-group SHA chain: each group contributes one entry per
        // applied refactoring + an optional trailing residual SHA. The
        // chain is parented sequentially so each SHA's `from` is the
        // previous SHA in the same group (or `fromSha` for the first).
        val altShas = mutableListOf<String>()
        val altFromShas = mutableListOf<String>()
        for (synth in alternative.synthesised) {
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

        // Augment alt CheckpointMetrics with synthetic entries for kept
        // reorder-ordering terminal SHAs. Terminals are AST-equivalent to
        // windowToSha by construction (Slice 2b), so their metrics alias
        // the user's windowToSha CheckpointMetrics — no rebuild. Pmd/Cpd
        // tracking + DerivedMetricsRunner then have a CheckpointMetrics
        // entry for every alt step SHA, terminals included.
        val userMetricsBySha = metrics.checkpoints.associateBy { it.sha }
        val altMetricsByShaMut = metrics.alternativeCheckpoints.associateBy { it.sha }.toMutableMap()
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
        for (synth in alternative.synthesised) {
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

        val diffsStart = System.currentTimeMillis()
        log("diffs: starting")
        val diffs = DiffsRunner(GitRunner(reconstruction.repoDir))
            .run(reconstruction, miner.steps, allAltPairs)
        val diffsDurationMs = System.currentTimeMillis() - diffsStart
        log("diffs: ${diffs.checkpointPatches.size} checkpoint, ${diffs.refactoringPatches.size} refactoring, ${diffs.alternativePatches.size} alternative patch(es) in ${diffsDurationMs}ms")

        val pmdTrackingStart = System.currentTimeMillis()
        log("pmd-tracking: starting")
        val pmdTracking = PmdTrackingRunner(GitRunner(reconstruction.repoDir)).run(
            orderedUserCheckpoints = metrics.checkpoints,
            alternativePairs = allAltPairs,
            alternativeCheckpoints = augmentedAltCheckpoints,
        )
        val pmdTrackingDurationMs = System.currentTimeMillis() - pmdTrackingStart
        log("pmd-tracking: ${pmdTracking.trackingBySha.size} user, ${pmdTracking.alternativeTrackingBySha.size} alt in ${pmdTrackingDurationMs}ms")

        val cpdTrackingStart = System.currentTimeMillis()
        log("cpd-tracking: starting")
        val cpdTracking = CpdTrackingRunner().run(
            orderedUserCheckpoints = metrics.checkpoints,
            alternativePairs = allAltPairs,
            alternativeCheckpoints = augmentedAltCheckpoints,
        )
        val cpdTrackingDurationMs = System.currentTimeMillis() - cpdTrackingStart
        log("cpd-tracking: ${cpdTracking.trackingBySha.size} user, ${cpdTracking.alternativeTrackingBySha.size} alt in ${cpdTrackingDurationMs}ms")

        val report = buildAnalysisReport(
            trace = trace,
            reconstruction = reconstruction,
            metrics = metrics,
            augmentedAltMetricsBySha = altMetricsByShaMut,
            miner = miner,
            alternative = alternative,
            diffs = diffs,
            pmdTracking = pmdTracking,
            cpdTracking = cpdTracking,
            parallelism = parallelism,
            metricsDurationMs = metricsDurationMs,
            reorderTrajectories = reorderSynth.trajectories,
            reworkSummary = reworkSummary,
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
    cpdTracking: CpdTrackingRunner.Summary = CpdTrackingRunner.Summary(emptyMap(), emptyMap()),
    parallelism: Int,
    metricsDurationMs: Long,
    reorderTrajectories: List<com.github.ethanhosier.analysis.metrics.model.ReorderTrajectory> = emptyList(),
    /** CheckpointMetrics keyed by altSha across single-step alts and
     *  reorder per-step alts (terminals included via Slice 2b alias).
     *  Defaults to `metrics.alternativeCheckpoints.associateBy { it.sha }`
     *  for callers that don't synthesise terminal aliases (e.g. tests). */
    augmentedAltMetricsBySha: Map<String, com.github.ethanhosier.analysis.metrics.model.CheckpointMetrics>? = null,
    reworkSummary: ReworkSynthesiser.Summary? = null,
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

    // First pass: assemble checkpoints + alts WITHOUT derived metrics, so
    // `DerivedMetricsRunner` can read their already-stitched-in events,
    // pmdTracking, and metrics blocks. Second pass below splices the
    // derived metrics back onto each.
    val baseCheckpoints = metrics.checkpoints.map { m ->
        val events = eventsBySha[m.sha].orEmpty()
        CheckpointReport(
            sha = m.sha,
            events = events,
            metrics = m,
            diff = metrics.diffBySha[m.sha] ?: DiffStats.ZERO,
            touchedMembers = membersBySha[m.sha]?.toList().orEmpty(),
            pmdTracking = pmdTracking.trackingBySha[m.sha] ?: PmdTracking.EMPTY,
            cpdTracking = cpdTracking.trackingBySha[m.sha] ?: CpdTracking.EMPTY,
        )
    }

    val stepsByIndex = miner.steps.associateBy { it.stepIndex }
    val altMetricsBySha = augmentedAltMetricsBySha
        ?: metrics.alternativeCheckpoints.associateBy { it.sha }

    // Helper: build a CheckpointReport for one alt step SHA. Alt SHAs
    // never carry user events / touched members; metrics + tracking come
    // from the augmented alt-checkpoint map (which includes terminal
    // aliases for reorder orderings).
    fun altCheckpointFor(sha: String): CheckpointReport? {
        val m = altMetricsBySha[sha] ?: return null
        return CheckpointReport(
            sha = sha,
            events = emptyList(),
            metrics = m,
            diff = metrics.diffBySha[sha] ?: DiffStats.ZERO,
            touchedMembers = emptyList(),
            pmdTracking = pmdTracking.alternativeTrackingBySha[sha] ?: PmdTracking.EMPTY,
            cpdTracking = cpdTracking.alternativeTrackingBySha[sha] ?: CpdTracking.EMPTY,
        )
    }

    // IDE-driven alts: one entry per synthesised group. `altCheckpoints`
    // is the per-step chain (one per applied refactoring + optional
    // trailing residual SHA). `specs` and `stepIndexes` are parallel to
    // *each other* (length N) and cover only the refactoring portion;
    // `altCheckpoints` is length N or N+1 depending on whether the
    // residual landed as its own step. When `altCheckpoints.size ==
    // specs.size + 1`, the trailing entry is the residual cleanup.
    val singleStepAlts = alternative.synthesised.mapNotNull { synth ->
        val specs = synth.stepIndexes.map { idx ->
            stepsByIndex[idx]?.spec ?: return@mapNotNull null
        }
        val altCps = synth.altShas.map { altCheckpointFor(it) ?: return@mapNotNull null }
        AlternativeTrajectory(
            kind = DivergenceKind.IDE_REPLAY,
            stepIndexes = synth.stepIndexes,
            fromSha = synth.fromSha,
            userToSha = synth.userToSha,
            branchRefs = synth.branchRefs,
            specs = specs,
            altCheckpoints = altCps,
            residual = synth.residual,
        )
    }

    // Multi-step alts from kept reorder orderings. Each ordering becomes
    // one AlternativeTrajectory entry covering N applied steps in the
    // permutation order. Terminal step's CheckpointReport is built off
    // the aliased CheckpointMetrics (Slice 2b) so its `metrics` field
    // matches the user's windowToSha checkpoint for build/test/PMD —
    // PMD/CPD tracking entries for the terminal sha were also computed
    // upstream (inter-bracket pair `(prev_step, terminal_sha)` is in
    // `allAltPairs`). Failed orderings emit no entry per Slice 2b scope.
    val reorderAlts = reorderTrajectories.flatMap { traj ->
        traj.orderings.mapNotNull { ord ->
            if (!ord.terminalSuccess) return@mapNotNull null
            if (ord.stepShas.size != ord.permutation.size) return@mapNotNull null
            val orderedStepIndexes = ord.permutation.map { i -> traj.windowStepIndexes[i] }
            val orderedSpecs = ord.permutation.mapNotNull { i ->
                stepsByIndex[traj.windowStepIndexes[i]]?.spec
            }
            if (orderedSpecs.size != ord.permutation.size) return@mapNotNull null
            val altCps = ord.stepShas.mapNotNull { altCheckpointFor(it) }
            if (altCps.size != ord.stepShas.size) return@mapNotNull null

            // Trim leading shared prefix: any contiguous run from the
            // start where the alt's permutation matches the user's
            // chronological order (window-local indices 0,1,2,...).
            // Those steps produced state identical to the user's, so
            // counting them as alt-side divergence inflates the alt's
            // process-score penalties and clutters the chart with
            // overlapping dots. Anchor the alt at the user's checkpoint
            // *after* the shared prefix and drop the prefix entries.
            //
            // TODO(efficiency): the trimmed step SHAs are still listed
            // in `reorderIntermediateShas` (collected in `run()`), so
            // MetricsRunner builds + tests them and Pmd/Cpd tracking
            // computes entries for them. None of that work surfaces in
            // the report. A follow-up should also trim the
            // `reorderIntermediateShas` collection in `run()` to skip
            // those builds. Visually + score-wise, trimming here alone
            // is correct.
            var sharedPrefixLen = 0
            while (sharedPrefixLen < ord.permutation.size &&
                ord.permutation[sharedPrefixLen] == sharedPrefixLen
            ) {
                sharedPrefixLen++
            }
            // Defensive: an entirely-shared ordering would mean the alt
            // == the user's order, which the synthesiser excludes —
            // drop if it ever leaks through.
            if (sharedPrefixLen >= ord.permutation.size) return@mapNotNull null

            val anchorSha = if (sharedPrefixLen == 0) {
                traj.windowFromSha
            } else {
                val lastSharedIdx = traj.windowStepIndexes[sharedPrefixLen - 1]
                stepsByIndex[lastSharedIdx]?.toSha ?: traj.windowFromSha
            }

            AlternativeTrajectory(
                kind = DivergenceKind.ORDERING,
                stepIndexes = orderedStepIndexes.drop(sharedPrefixLen),
                fromSha = anchorSha,
                userToSha = traj.windowToSha,
                branchRefs = ord.branchRefs.drop(sharedPrefixLen),
                specs = orderedSpecs.drop(sharedPrefixLen),
                altCheckpoints = altCps.drop(sharedPrefixLen),
            )
        }
    }

    // Rework alts: one entry per synthesised rework. `specs` /
    // `stepIndexes` are empty because rework replays raw user commits
    // rather than miner-typed refactoring steps; the chart and
    // continuation walk only consume `altCheckpoints` + from/userToSha
    // anchors, which the rest of the alt-trajectory plumbing handles
    // uniformly.
    // Rework alts whose `altShas[i]` aliases a user SHA (the no-op
    // case — delete-then-undelete cancels out, alt branches at fromSha
    // and never moves) must NOT inherit the user's main-walk diff at
    // that SHA. The user's diffBySha[fromSha] reflects fromSha's churn
    // from its parent — i.e. the code that was already there before
    // the rework — which has nothing to do with the alt. Attributing
    // it to the alt's first step inflates the alt's churn / smells
    // delta and depresses the process score artificially. Zero out
    // the diff for any alt checkpoint whose SHA collides with a user
    // checkpoint.
    val userShaSet = baseCheckpoints.map { it.sha }.toSet()
    // Build rework alts alongside their originating SynthesisedRework so
    // the divergence-point builder can attach file/scope/lineCount/step
    // metadata without re-running detection.
    val reworkAltsWithInfo: List<Pair<AlternativeTrajectory, ReworkSynthesiser.SynthesisedRework>> =
        reworkSummary?.synthesised.orEmpty().mapNotNull { rw ->
            val altCps = rw.altShas.map { sha ->
                val cp = altCheckpointFor(sha) ?: return@mapNotNull null
                if (sha in userShaSet) cp.copy(diff = DiffStats.ZERO) else cp
            }
            AlternativeTrajectory(
                kind = DivergenceKind.REWORK,
                stepIndexes = emptyList(),
                fromSha = rw.fromSha,
                userToSha = rw.userToSha,
                branchRefs = rw.branchRefs,
                specs = emptyList(),
                altCheckpoints = altCps,
            ) to rw
        }
    val reworkAlts = reworkAltsWithInfo.map { it.first }

    val baseAlternatives = singleStepAlts + reorderAlts + reworkAlts
    val reworkBaseOffset = singleStepAlts.size + reorderAlts.size

    val derived = DerivedMetricsRunner().run(
        mainCheckpoints = baseCheckpoints,
        alternatives = baseAlternatives,
        refactoringSteps = miner.steps,
    )
    val checkpoints = baseCheckpoints.map { cp ->
        cp.copy(derivedMetrics = derived.main[cp.sha] ?: DerivedMetrics.EMPTY)
    }
    val userCpBySha = checkpoints.associateBy { it.sha }
    val alternativeTrajectories = baseAlternatives.mapIndexed { i, alt ->
        val cont = derived.continuations.getOrNull(i)
            ?: DerivedMetricsRunner.AltProcessContinuation.EMPTY
        // Each continuation checkpoint clones the user's post-merge
        // checkpoint (same code state ⇒ same static metrics, build,
        // tests, smells, cleanliness, diff, events) and overlays the
        // alt's recomputed process score. The user's checkpoint
        // already carries `derivedMetrics` from the main walk above;
        // we just swap `process`.
        val continuationCheckpoints = cont.checkpointShas.mapIndexedNotNull { ci, sha ->
            val userCp = userCpBySha[sha] ?: return@mapIndexedNotNull null
            val score = cont.processScores.getOrNull(ci) ?: return@mapIndexedNotNull null
            userCp.copy(
                derivedMetrics = userCp.derivedMetrics.copy(process = score),
            )
        }
        alt.copy(
            altCheckpoints = alt.altCheckpoints.map { cp ->
                cp.copy(derivedMetrics = derived.alt[cp.sha] ?: DerivedMetrics.EMPTY)
            },
            continuationCheckpoints = continuationCheckpoints,
        )
    }

    // Build divergence points after `alternativeTrajectories` (and thus
    // their indexes) are stable. Rework metadata is threaded through by
    // alt-index keyed map; the builder doesn't see the synthesiser
    // record directly.
    val stepIndexBySha: Map<String, Int> =
        checkpoints.withIndex().associate { (i, cp) -> cp.sha to i }
    val reworkInfoByAltIndex: Map<Int, DivergencePointBuilder.ReworkInfo> =
        reworkAltsWithInfo.withIndex().mapNotNull { (offset, pair) ->
            val (_, rw) = pair
            val orig = stepIndexBySha[rw.fromSha]
            val term = stepIndexBySha[rw.userToSha]
            if (orig == null || term == null) return@mapNotNull null
            (reworkBaseOffset + offset) to DivergencePointBuilder.ReworkInfo(
                originatingStepIndex = orig,
                terminalStepIndex = term,
                file = rw.file,
                scopeLabel = rw.scopeId,
                lineCount = rw.rawLineCount,
                originatingPatch = rw.originatingPatch,
                terminalPatch = rw.terminalPatch,
                direction = rw.direction.name,
            )
        }.toMap()
    val divergencePoints = DivergencePointBuilder.build(
        alts = alternativeTrajectories,
        userCheckpoints = checkpoints,
        reworkInfoByAltIndex = reworkInfoByAltIndex,
    )

    val preAdviceReport = AnalysisReport(
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
        reorderTrajectories = reorderTrajectories,
        divergencePoints = divergencePoints,
        userGitCommits = trace.events.asSequence()
            .filter { it.type == EventType.GIT_COMMIT }
            .mapNotNull { e ->
                val sha = e.payload["sha"] ?: return@mapNotNull null
                UserGitCommit(
                    sha = sha,
                    parentSha = e.payload["parentSha"],
                    // Prefer the reflog's author/commit timestamp (carried
                    // on the event payload) over `event.timestamp` so the
                    // SHA's own commit time is what surfaces, even if the
                    // listener wrote the event a moment later.
                    timestamp = e.payload["authorTimestamp"]?.toLongOrNull() ?: e.timestamp,
                    message = e.payload["message"].orEmpty(),
                    action = e.payload["action"] ?: "commit",
                )
            }
            .toList(),
    )
    return preAdviceReport.copy(advice = TrajectoryAdvisor.advise(preAdviceReport))
}
