package com.github.ethanhosier.analysis.pipeline

import com.github.ethanhosier.analysis.advice.TrajectoryAdvisor
import com.github.ethanhosier.analysis.alternative.IdeRefactoringsRunner
import com.github.ethanhosier.analysis.alternative.rework.ReworkSynthesiser
import com.github.ethanhosier.analysis.diffs.DiffsRunner
import com.github.ethanhosier.analysis.divergence.DivergencePointBuilder
import com.github.ethanhosier.analysis.divergence.HygieneDetector
import com.github.ethanhosier.analysis.metrics.MetricsRunner
import com.github.ethanhosier.analysis.metrics.cpd.CpdOccurrence
import com.github.ethanhosier.analysis.metrics.cpd.CpdTrackingRunner
import com.github.ethanhosier.analysis.metrics.derived.DerivedMetricsRunner
import com.github.ethanhosier.analysis.metrics.derived.ScoringConfig
import com.github.ethanhosier.analysis.metrics.gitdiff.DiffStats
import com.github.ethanhosier.analysis.metrics.model.AlternativeTrajectory
import com.github.ethanhosier.analysis.metrics.model.CheckpointMetrics
import com.github.ethanhosier.analysis.metrics.model.ReorderTrajectory
import com.github.ethanhosier.analysis.metrics.pmd.PmdTrackingRunner
import com.github.ethanhosier.analysis.metrics.pmd.ResolvedPmdViolation
import com.github.ethanhosier.analysis.miner.RefactoringMinerRunner
import com.github.ethanhosier.analysis.miner.model.RefactoringStep
import com.github.ethanhosier.analysis.model.ReconstructionResult
import com.github.ethanhosier.analysis.model.Trace
import com.github.ethanhosier.ideplugin.model.EventType
import com.github.ethanhosier.ideplugin.model.SessionMetadata
import com.github.ethanhosier.ideplugin.model.TouchedMember
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
data class AnalysisReport(
    val session: SessionMetadata,
    val run: RunInfo,
    val checkpoints: List<CheckpointReport>,
    val refactoringSteps: List<RefactoringStep> = emptyList(),
    val trajectory: TrajectoryStats = TrajectoryStats.ZERO,
    val checkpointPatches: Map<String, String> = emptyMap(),
    val refactoringPatches: Map<Int, String> = emptyMap(),
    val alternativeTrajectories: List<AlternativeTrajectory> = emptyList(),
    val alternativePatches: Map<String, String> = emptyMap(),
    val reorderTrajectories: List<ReorderTrajectory> = emptyList(),
    val userGitCommits: List<UserGitCommit> = emptyList(),
    val advice: List<AdviceItem> = emptyList(),
    val divergencePoints: List<DivergencePoint> = emptyList(),
)

@Serializable
enum class DivergenceKind {
    ORDERING,
    IDE_REPLAY,
    REWORK,
    HYGIENE,
}

@Serializable
data class DivergencePoint(
    val stepIndex: Int,
    val kind: DivergenceKind,
    val magnitude: Double,
    val title: String,
    val explanation: String,
    val altTrajectoryIndexes: List<Int>,
    val orderingWindowSteps: List<Int>? = null,
    val originatingStepIndex: Int? = null,
    val file: String? = null,
    val scopeLabel: String? = null,
    val reworkLineCount: Int? = null,
    val replacedRefactoringId: String? = null,
    val originatingPatch: String? = null,
    val terminalPatch: String? = null,
    val reworkDirection: String? = null,
    val hygieneSubKind: String? = null,
    val hygieneStretchLength: Int? = null,
)

@Serializable
enum class AdviceKind {
    BUILD_OFTEN_BROKEN,
    TEST_REGRESSIONS,
    LONG_STRETCH_WITHOUT_COMMIT,
    REFACTORING_INTRODUCED_SMELLS,
    SMELLS_ACCUMULATED_NET,
    PROCESS_SCORE_DEGRADED,
    REORDER_BEATS_USER,
}

@Serializable
enum class AdviceSeverity { INFO, WARNING, CRITICAL }

@Serializable
data class AdviceItem(
    val kind: AdviceKind,
    val severity: AdviceSeverity,
    val title: String,
    val body: String,
)

@Serializable
data class UserGitCommit(
    val sha: String,
    val parentSha: String? = null,
    val timestamp: Long,
    val message: String,
    val action: String,
)

@Serializable
data class TrajectoryStats(
    val numSteps: Int,
    val totalChurn: Int,
    val avgChurnPerStep: Double,
    val maxChurnOnStep: Int,
    val totalFilesTouched: Int,
    val perFileTouchCount: Map<String, Int>,
    val retouchCount: Int,
    val churnTopNShare: Double,
    val topN: Int,
    val totalElapsedMs: Long,
    val totalBrokenMs: Long,
    val classes: MemberTouchStats = MemberTouchStats.ZERO,
    val methods: MemberTouchStats = MemberTouchStats.ZERO,
    val duplication: DuplicationTrajectoryStats = DuplicationTrajectoryStats.ZERO,
    val readability: ReadabilityTrajectoryStats = ReadabilityTrajectoryStats.ZERO,
) {
    companion object {
        val ZERO = TrajectoryStats(0, 0, 0.0, 0, 0, emptyMap(), 0, 0.0, 0, 0, 0)
    }
}

@Serializable
data class DuplicationTrajectoryStats(
    val duplicatedLinesPerStep: List<Int>,
    val duplicatedBlocksPerStep: List<Int>,
    val linesDeltaPerStep: List<Int>,
    val blocksDeltaPerStep: List<Int>,
    val totalLinesIntroduced: Int,
    val totalLinesRemoved: Int,
    val netLinesChange: Int,
    val totalBlocksIntroduced: Int,
    val totalBlocksRemoved: Int,
    val maxDuplicatedLines: Int,
    val maxSpikeLines: Int,
    val maxDropLines: Int,
    val stepsIncreasing: Int,
    val stepsAboveBaseline: Int,
    val spikeThenDropCount: Int,
    val spikeThreshold: Int,
    val dropThreshold: Int,
    val followupWindow: Int,
) {
    companion object {
        val ZERO = DuplicationTrajectoryStats(
            duplicatedLinesPerStep = emptyList(),
            duplicatedBlocksPerStep = emptyList(),
            linesDeltaPerStep = emptyList(),
            blocksDeltaPerStep = emptyList(),
            totalLinesIntroduced = 0,
            totalLinesRemoved = 0,
            netLinesChange = 0,
            totalBlocksIntroduced = 0,
            totalBlocksRemoved = 0,
            maxDuplicatedLines = 0,
            maxSpikeLines = 0,
            maxDropLines = 0,
            stepsIncreasing = 0,
            stepsAboveBaseline = 0,
            spikeThenDropCount = 0,
            spikeThreshold = 0,
            dropThreshold = 0,
            followupWindow = 0,
        )
    }
}

@Serializable
data class MemberTouchStats(
    val perTouchCount: Map<String, Int>,
    val retouchCount: Int,
    val consecutiveRetouchCount: Int,
    val distinctTouched: Int,
    val topTouched: List<String>,
    val topNShare: Double,
    val topN: Int,
    val perStepIndices: Map<String, List<Int>>,
) {
    companion object {
        val ZERO = MemberTouchStats(emptyMap(), 0, 0, 0, emptyList(), 0.0, 0, emptyMap())
    }
}

@Serializable
data class ReadabilityTrajectoryStats(
    val avgLineLengthPerStep: List<Double>,
    val maxLineLengthPerStep: List<Int>,
    val avgCommentRatioPerStep: List<Double>,
    val avgIndentationPerStep: List<Double>,
    val avgIdentifierLengthPerStep: List<Double>,
    val singleLetterRatioPerStep: List<Double>,
    val avgWordCountPerStep: List<Double>,
    val dictionaryWordRatioPerStep: List<Double>,
    val worstClassLocPerStep: List<Int>,
    val worstMethodLocPerStep: List<Int>,
    // Net change from the baseline (first checkpoint) to the last.
    val netAvgLineLengthChange: Double,
    val netAvgCommentRatioChange: Double,
    val netAvgIdentifierLengthChange: Double,
    val netSingleLetterRatioChange: Double,
    val netDictionaryWordRatioChange: Double,
    val netWorstMethodLocChange: Int,
    val netWorstClassLocChange: Int,
) {
    companion object {
        val ZERO = ReadabilityTrajectoryStats(
            emptyList(), emptyList(), emptyList(), emptyList(),
            emptyList(), emptyList(), emptyList(), emptyList(),
            emptyList(), emptyList(),
            0.0, 0.0, 0.0, 0.0, 0.0, 0, 0,
        )
    }
}

@Serializable
data class RunInfo(
    val parallelism: Int,
    val generatedAt: Long,
    val metricsDurationMs: Long,
)

@Serializable
data class CheckpointReport(
    val sha: String,
    val events: List<EventSummary>,
    val metrics: CheckpointMetrics,
    val isUserCommit: Boolean = false,
    val metricsTrustworthy: Boolean = true,
    val metricsCarryForwardSource: String? = null,
    val diff: DiffStats = DiffStats.ZERO,
    val touchedMembers: List<TouchedMember> = emptyList(),
    val pmdTracking: PmdTracking = PmdTracking.EMPTY,
    val cpdTracking: CpdTracking = CpdTracking.EMPTY,
    val derivedMetrics: DerivedMetrics = DerivedMetrics.EMPTY,
)

@Serializable
data class DerivedMetrics(
    val coupling: Double = 0.0,
    val cohesion: Double? = null,
    val duplication: Double = 0.0,
    val readability: Double = 0.0,
    // Mean cognitive complexity per method (Campbell 2018) over methods
    // whose file lies in the touched set, rounded to the nearest int.
    val cognitive: Int = 0,
    // Count of PMD violations in touched files.
    val smells: Int = 0,
    val cleanliness: Cleanliness? = null,
    val process: ProcessScore = ProcessScore.EMPTY,
) {
    companion object {
        val EMPTY = DerivedMetrics()
    }
}

@Serializable
data class Cleanliness(
    // 0..100, rounded; what the dashboard tile renders.
    val score: Int,
    // 0..1; the raw scalar consumed by the process-score gain term.
    val scalar: Double,
    val rebased: Boolean,
    val contributions: List<CleanlinessContribution>,
)

@Serializable
data class CleanlinessContribution(
    // Stable id matching MetricId on the frontend (cognitive, coupling, ...).
    val id: String,
    val label: String,
    val weight: Double,
    // 0..1 after better-direction flip + (for alts) clamp.
    val normalised: Double,
    // Raw sub-metric value at this checkpoint.
    val raw: Double,
    // (weight / totalW) * normalised * 100 — sums to `score` across rows.
    val points: Double,
)

@Serializable
data class ProcessScore(
    val total: Int,
    val baseline: Int = 50,
    // True if `baseline + Σ contributions.points` fell outside [0, 100].
    val clamped: Boolean,
    val contributions: List<ProcessContribution>,
) {
    companion object {
        val EMPTY = ProcessScore(total = 50, baseline = 50, clamped = false, contributions = emptyList())
    }
}

@Serializable
data class ProcessContribution(
    // cleanliness | degradation | broken | smells | skipTests | manualIde
    val id: String,
    val label: String,
    // Signed; sum + baseline = unclamped total.
    val points: Double,
    // Human-readable explanation rendered in the breakdown panel.
    val detail: String,
)

@Serializable
data class PmdTracking(
    val firstSeenAtSha: List<String> = emptyList(),
    val resolvedSincePrev: List<ResolvedPmdViolation> = emptyList(),
) {
    companion object {
        val EMPTY = PmdTracking()
    }
}

@Serializable
data class CpdTracking(
    val firstSeenAtSha: List<String> = emptyList(),
    val resolvedSincePrev: List<ResolvedCpdDuplication> = emptyList(),
) {
    companion object {
        val EMPTY = CpdTracking()
    }
}

@Serializable
data class ResolvedCpdDuplication(
    val tokens: Int,
    val lines: Int,
    val identity: String,
    val prevOccurrences: List<CpdOccurrence>,
    val firstSeenAtSha: String,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class EventSummary(
    val id: String,
    val type: EventType,
    val timestamp: Long,
    // Flat, deduped union of every `(class, method?)` pair this event's
    // snapshots reported. Defaults empty for events with no file changes.
    val touchedMembers: List<TouchedMember> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val refactoringId: String? = null,
)

internal fun buildAnalysisReport(
    trace: Trace,
    reconstruction: ReconstructionResult,
    metrics: MetricsRunner.Summary,
    miner: RefactoringMinerRunner.Summary,
    alternative: IdeRefactoringsRunner.Summary,
    diffs: DiffsRunner.Summary,
    trackedCodeSmells: PmdTrackingRunner.Summary,
    trackedDuplication: CpdTrackingRunner.Summary = CpdTrackingRunner.Summary(emptyMap(), emptyMap()),
    parallelism: Int,
    metricsDurationMs: Long,
    reorderTrajectories: List<ReorderTrajectory> = emptyList(),
    augmentedAltMetricsBySha: Map<String, CheckpointMetrics>? = null,
    reworkSummary: ReworkSynthesiser.Summary? = null,
    config: ScoringConfig = ScoringConfig.PRODUCTION,
): AnalysisReport {
    val (eventsBySha, membersBySha) = computeBySha(reconstruction, trace)

    val baseCheckpoints = metrics.checkpoints.map { m ->
        val events = eventsBySha[m.sha].orEmpty()
        CheckpointReport(
            sha = m.sha,
            events = events,
            metrics = m,
            isUserCommit = events.any { it.type == EventType.GIT_COMMIT },
            diff = metrics.diffBySha[m.sha] ?: DiffStats.ZERO,
            touchedMembers = membersBySha[m.sha]?.toList().orEmpty(),
            pmdTracking = trackedCodeSmells.trackingBySha[m.sha] ?: PmdTracking.EMPTY,
            cpdTracking = trackedDuplication.trackingBySha[m.sha] ?: CpdTracking.EMPTY,
        )
    }

    val stepsByIndex = miner.steps.associateBy { it.stepIndex }
    val altMetricsBySha = augmentedAltMetricsBySha
        ?: metrics.alternativeCheckpoints.associateBy { it.sha }


    val singleStepAlts =
        computeSingleStepAlts(alternative, stepsByIndex, altMetricsBySha, trackedCodeSmells, trackedDuplication, metrics)

    val reorderAlts =
        computeReorderAlts(reorderTrajectories, stepsByIndex, altMetricsBySha, trackedCodeSmells, trackedDuplication, metrics)

    val (reworkAltsWithInfo, reworkAlts) = computeReworkAlts(
        baseCheckpoints,
        reworkSummary,
        altMetricsBySha,
        trackedCodeSmells,
        trackedDuplication,
        metrics
    )

    val baseAlternatives = singleStepAlts + reorderAlts + reworkAlts
    val reworkBaseOffset = singleStepAlts.size + reorderAlts.size

    val derived = DerivedMetricsRunner(config = config).run(
        mainCheckpoints = baseCheckpoints,
        alternatives = baseAlternatives,
        refactoringSteps = miner.steps,
    )
    val checkpoints = baseCheckpoints.map { cp ->
        val trust = derived.mainTrust[cp.sha]
        cp.copy(
            derivedMetrics = derived.main[cp.sha] ?: DerivedMetrics.EMPTY,
            metricsTrustworthy = trust?.trustworthy ?: true,
            metricsCarryForwardSource = trust?.source,
        )
    }

    val hygieneDetector = HygieneDetector(config = config)
    val hygieneFindings = hygieneDetector.detect(checkpoints, miner.steps)
    val hygieneAlts = hygieneDetector.buildAlts(
        findings = hygieneFindings,
        checkpoints = checkpoints,
        refactoringSteps = miner.steps,
    )

    val userCpBySha = checkpoints.associateBy { it.sha }
    val alternativeTrajectoriesNoHygiene = baseAlternatives.mapIndexed { i, alt ->
        val cont = derived.continuations.getOrNull(i)
            ?: DerivedMetricsRunner.AltProcessContinuation.EMPTY
        val continuationCheckpoints = cont.checkpointShas.mapIndexedNotNull { ci, sha ->
            val userCp = userCpBySha[sha] ?: return@mapIndexedNotNull null
            val score = cont.processScores.getOrNull(ci) ?: return@mapIndexedNotNull null
            userCp.copy(
                derivedMetrics = userCp.derivedMetrics.copy(process = score),
            )
        }
        alt.copy(
            altCheckpoints = alt.altCheckpoints.map { cp ->
                val altTrust = derived.altTrust[cp.sha]
                cp.copy(
                    derivedMetrics = derived.alt[cp.sha] ?: DerivedMetrics.EMPTY,
                    metricsTrustworthy = altTrust?.trustworthy ?: true,
                    metricsCarryForwardSource = altTrust?.source,
                )
            },
            continuationCheckpoints = continuationCheckpoints,
        )
    }
    val alternativeTrajectories = alternativeTrajectoriesNoHygiene + hygieneAlts
    val hygieneBaseOffset = alternativeTrajectoriesNoHygiene.size

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
    val hygieneInfoByAltIndex: Map<Int, DivergencePointBuilder.HygieneInfo> =
        hygieneFindings.withIndex().associate { (offset, f) ->
            (hygieneBaseOffset + offset) to DivergencePointBuilder.HygieneInfo(
                anchorIndex = f.anchorIndex,
                subKind = f.subKind.name,
                gapLength = f.gapLength,
            )
        }
    val divergencePoints = DivergencePointBuilder.build(
        alts = alternativeTrajectories,
        userCheckpoints = checkpoints,
        reworkInfoByAltIndex = reworkInfoByAltIndex,
        hygieneInfoByAltIndex = hygieneInfoByAltIndex,
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
                    timestamp = e.payload["authorTimestamp"]?.toLongOrNull() ?: e.timestamp,
                    message = e.payload["message"].orEmpty(),
                    action = e.payload["action"] ?: "commit",
                )
            }
            .toList(),
    )
    return preAdviceReport.copy(advice = TrajectoryAdvisor.advise(preAdviceReport))
}

private fun computeReworkAlts(
    baseCheckpoints: List<CheckpointReport>,
    reworkSummary: ReworkSynthesiser.Summary?,
    altMetricsBySha: Map<String, CheckpointMetrics>,
    trackedCodeSmells: PmdTrackingRunner.Summary,
    trackedDuplication: CpdTrackingRunner.Summary,
    metrics: MetricsRunner.Summary
): Pair<List<Pair<AlternativeTrajectory, ReworkSynthesiser.SynthesisedRework>>, List<AlternativeTrajectory>> {
    val userShaSet = baseCheckpoints.map { it.sha }.toSet()
    val userIdxBySha = baseCheckpoints.withIndex().associate { (i, cp) -> cp.sha to i }
    val reworkAltsWithInfo: List<Pair<AlternativeTrajectory, ReworkSynthesiser.SynthesisedRework>> =
        reworkSummary?.synthesised.orEmpty().mapNotNull { rw ->
            val altCps = rw.altShas.map { sha ->
                val cp = altCheckpointFor(sha, altMetricsBySha, trackedCodeSmells, trackedDuplication, metrics)
                    ?: return@mapNotNull null
                if (sha in userShaSet) cp.copy(diff = DiffStats.ZERO) else cp
            }
            val fromIdx = userIdxBySha[rw.fromSha]
            val altCheckpointUserIndexes = if (fromIdx != null) {
                rw.planStepPositions.map { fromIdx + 1 + it }
            } else {
                emptyList()
            }
            AlternativeTrajectory(
                kind = DivergenceKind.REWORK,
                stepIndexes = emptyList(),
                fromSha = rw.fromSha,
                userToSha = rw.userToSha,
                branchRefs = rw.branchRefs,
                specs = emptyList(),
                altCheckpoints = altCps,
                altCheckpointUserIndexes = altCheckpointUserIndexes,
            ) to rw
        }
    val reworkAlts = reworkAltsWithInfo.map { it.first }
    return Pair(reworkAltsWithInfo, reworkAlts)
}

private fun computeReorderAlts(
    reorderTrajectories: List<ReorderTrajectory>,
    stepsByIndex: Map<Int, RefactoringStep>,
    altMetricsBySha: Map<String, CheckpointMetrics>,
    trackedCodeSmells: PmdTrackingRunner.Summary,
    trackedDuplication: CpdTrackingRunner.Summary,
    metrics: MetricsRunner.Summary
): List<AlternativeTrajectory> {
    val reorderAlts = reorderTrajectories.flatMap { traj ->
        traj.orderings.mapNotNull { ord ->
            if (!ord.terminalSuccess) return@mapNotNull null
            if (ord.stepShas.size != ord.permutation.size) return@mapNotNull null
            val orderedStepIndexes = ord.permutation.map { i -> traj.windowStepIndexes[i] }
            val orderedSpecs = ord.permutation.mapNotNull { i ->
                stepsByIndex[traj.windowStepIndexes[i]]?.spec
            }
            if (orderedSpecs.size != ord.permutation.size) return@mapNotNull null
            val altCps = ord.stepShas.mapNotNull {
                altCheckpointFor(
                    it,
                    altMetricsBySha,
                    trackedCodeSmells,
                    trackedDuplication,
                    metrics
                )
            }
            if (altCps.size != ord.stepShas.size) return@mapNotNull null

            var sharedPrefixLen = 0
            while (sharedPrefixLen < ord.permutation.size &&
                ord.permutation[sharedPrefixLen] == sharedPrefixLen
            ) {
                sharedPrefixLen++
            }
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
    return reorderAlts
}

private fun computeSingleStepAlts(
    alternative: IdeRefactoringsRunner.Summary,
    stepsByIndex: Map<Int, RefactoringStep>,
    altMetricsBySha: Map<String, CheckpointMetrics>,
    trackedCodeSmells: PmdTrackingRunner.Summary,
    trackedDuplication: CpdTrackingRunner.Summary,
    metrics: MetricsRunner.Summary
): List<AlternativeTrajectory> {
    val singleStepAlts = alternative.synthesised.mapNotNull { synth ->
        val specs = synth.stepIndexes.map { idx ->
            stepsByIndex[idx]?.spec ?: return@mapNotNull null
        }
        val altCps = synth.altShas.map {
            altCheckpointFor(
                it,
                altMetricsBySha,
                trackedCodeSmells,
                trackedDuplication,
                metrics
            ) ?: return@mapNotNull null
        }
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
    return singleStepAlts
}

private fun computeBySha(
    reconstruction: ReconstructionResult,
    trace: Trace
): Pair<LinkedHashMap<String, MutableList<EventSummary>>, LinkedHashMap<String, LinkedHashSet<TouchedMember>>> {
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
    return Pair(eventsBySha, membersBySha)
}

fun altCheckpointFor(
    sha: String,
    altCheckpointsFor: Map<String, CheckpointMetrics>,
    trackedCodeSmells: PmdTrackingRunner.Summary,
    trackedDuplication: CpdTrackingRunner.Summary,
    metrics: MetricsRunner.Summary
): CheckpointReport? {
    val m = altCheckpointsFor[sha] ?: return null
    return CheckpointReport(
        sha = sha,
        events = emptyList(),
        metrics = m,
        diff = metrics.diffBySha[sha] ?: DiffStats.ZERO,
        touchedMembers = emptyList(),
        pmdTracking = trackedCodeSmells.alternativeTrackingBySha[sha] ?: PmdTracking.EMPTY,
        cpdTracking = trackedDuplication.alternativeTrackingBySha[sha] ?: CpdTracking.EMPTY,
    )
}
