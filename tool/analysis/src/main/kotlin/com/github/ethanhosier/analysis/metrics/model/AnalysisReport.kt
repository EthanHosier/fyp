package com.github.ethanhosier.analysis.metrics.model

import com.github.ethanhosier.analysis.metrics.cpd.CpdOccurrence
import com.github.ethanhosier.analysis.metrics.gitdiff.DiffStats
import com.github.ethanhosier.analysis.metrics.pmd.ResolvedPmdViolation
import com.github.ethanhosier.analysis.miner.model.RefactoringStep
import com.github.ethanhosier.ideplugin.model.EventType
import com.github.ethanhosier.ideplugin.model.SessionMetadata
import com.github.ethanhosier.ideplugin.model.TouchedMember
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * Consolidated, human-viewable output of a full analysis run.
 *
 * One entry per unique commit SHA — each [CheckpointReport] carries the
 * metrics computed for that checkpoint alongside the events that landed on
 * it, so a reader can trace `which edits produced this state → what that
 * state looks like structurally`.
 *
 * Events are slimmed to id/type/timestamp: the full payloads (including file
 * snapshots, which can be megabytes) already live in `events.jsonl`. This
 * report is a summary, not a replacement.
 */
@Serializable
data class AnalysisReport(
    val session: SessionMetadata,
    val run: RunInfo,
    val checkpoints: List<CheckpointReport>,
    val refactoringSteps: List<RefactoringStep> = emptyList(),
    val trajectory: TrajectoryStats = TrajectoryStats.ZERO,
    // Unified-diff patch text for the transition *into* each checkpoint,
    // keyed by the checkpoint's SHA. Empty string when there is no previous
    // commit (root). Kept as a side table so [CheckpointReport] stays
    // compact — patches can be kilobytes.
    val checkpointPatches: Map<String, String> = emptyMap(),
    // Unified-diff patch text for each refactoring step, keyed by its
    // `stepIndex`. Scoped to the files RefactoringMiner flagged and
    // hunk-filtered against the detection's left/right line ranges, so
    // unrelated edits in the same window are excluded.
    val refactoringPatches: Map<Int, String> = emptyMap(),
    // Synthesised IDE-driven equivalents for refactorings the user
    // performed manually across multiple checkpoints. Empty when no
    // candidate steps were found, when no `RefactoringClient` was
    // injected (e.g. tests), or when synthesis was skipped for every
    // candidate.
    val alternativeTrajectories: List<AlternativeTrajectory> = emptyList(),
    // Unified-diff patch text from `fromSha` → `altSha` for each
    // synthesised trajectory, keyed by the same `stepIndex` used by
    // [refactoringPatches]. Mirrors that map's shape so the frontend
    // can render IDE-driven and manual diffs side-by-side.
    val alternativePatches: Map<Int, String> = emptyMap(),
    // Multi-step alt orderings synthesised by ReorderSynthesiser.
    // One entry per reorder window with ≥2 typed VALID specs;
    // each carries every alt ordering's commit chain (excluding
    // the user's own ordering). Slice 2a populates this with
    // synthesised SHAs only — metrics for these intermediates are
    // a future slice. Empty default keeps older cached reports
    // deserialisable.
    val reorderTrajectories: List<ReorderTrajectory> = emptyList(),
)

/**
 * Trajectory-level aggregates across every checkpoint transition. "Step" =
 * one checkpoint (one transition from its predecessor). Broken time uses
 * build + tests success as the health signal.
 */
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

/**
 * Per-step PMD-CPD duplication aggregates across a trajectory. Captures the
 * "copy first, clean later" signal — a spike followed by a drop coinciding
 * with Extract Method / Extract Class is strong evidence that the
 * refactoring worked.
 *
 * `perStep` lists are aligned with [AnalysisReport.checkpoints] (including
 * the seed-state step, so readers see the baseline). Delta / intro /
 * remove totals cover transitions only.
 *
 * Two natural follow-ups consciously deferred from the first PR:
 *  1. **Per-group persistence** (first seen / last seen / checkpoints
 *     survived). Cheap-ish — fingerprint each block by its normalised
 *     occurrence set (sorted `(file, lines)` tuples) and accumulate across
 *     checkpoints. Aggregate-level spike-then-drop already captures the
 *     transient-duplication story.
 *  2. **Duplication churn** (how often duplicated regions are re-edited).
 *     Needs joining CPD occurrence line ranges to git-diff hunks per
 *     transition.
 */
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

/**
 * Per-step touch concentration for a member kind over a trajectory. For
 * classes the key is the class name; for methods the key is
 * `className#signature` so the same signature in different classes stays
 * distinct (and the class is recoverable by splitting on `#`). A "touch"
 * means the member appears in a step's [CheckpointReport.touchedMembers];
 * at most one touch per step per member.
 */
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

/**
 * Trajectory-level readability aggregates. Per-step lists are aligned with
 * [AnalysisReport.checkpoints] (the seed-equivalent first entry is included
 * as the baseline). Delta lists cover transitions only, so their length is
 * `checkpoints.size - 1`.
 *
 * All values come from each checkpoint's [ReadabilitySummary] — no composite
 * score is computed here. A Scalabrino / Buse-Weimer model score is an
 * explicit follow-up (phase 2); once it lands it fits naturally as another
 * per-step series in this struct.
 */
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
    // Diff against the previous checkpoint (or the seed commit for the first).
    // Sibling of `metrics` — `metrics` describes this checkpoint's state,
    // `diff` describes the transition into it.
    val diff: DiffStats = DiffStats.ZERO,
    // Flat, deduped union of every `(class, method?)` pair touched by the
    // events attributed to this checkpoint — i.e. the transition from the
    // previous checkpoint.
    val touchedMembers: List<TouchedMember> = emptyList(),
    // Cross-checkpoint enrichment of `metrics.pmd.violations`: which
    // violation was first seen when, plus the prev-checkpoint violations
    // that no longer fire here. Stitched in by the trajectory walk at
    // report-assembly time, same lifecycle as `diff` and `touchedMembers`.
    val pmdTracking: PmdTracking = PmdTracking.EMPTY,
    // Cross-checkpoint enrichment of `metrics.cpd.duplications`: which
    // clone group was first seen when, plus the prev-checkpoint groups
    // whose body no longer appears here. Identity is body-hash only, so
    // file moves / occurrence-count changes don't surface as churn.
    val cpdTracking: CpdTracking = CpdTracking.EMPTY,
    // Backend-computed scores derived from the per-checkpoint metric blocks
    // and the trajectory walk: per-checkpoint aggregators, cleanliness
    // composite, prefix-cumulative process score. Populated for main and
    // alt checkpoints alike (alts read main's running counters as their
    // starting state and use main's value range for cleanliness norms;
    // main scores never depend on alt data). Defaulted so older cached
    // reports still deserialise.
    val derivedMetrics: DerivedMetrics = DerivedMetrics.EMPTY,
)

/**
 * Backend-computed scores attached to a [CheckpointReport]. Replaces what
 * the dashboard used to derive client-side at view-model construction.
 *
 * The point-in-time aggregators are nullable — each one depends on a
 * specific raw metric block (CK / PMD / CPD / readability), and a runner
 * may legitimately fail to produce one (e.g. PMD failure on a checkpoint
 * still produces a report; that checkpoint just has `cognitive`/`smells`
 * null). [cleanliness] is null when no sub-metric is normalisable across
 * the trajectory (degenerate range or all absent). [process] is always
 * present — at minimum it anchors at the baseline of 50 with no
 * contributions.
 */
@Serializable
data class DerivedMetrics(
    // P90 of CK `cbo` across classes, rounded to 1dp. Always emitted —
    // empty class list yields 0 via the percentile fallback.
    val coupling: Double = 0.0,
    // Mean of non-null CK `tcc` across classes, rounded to 2dp. Null when
    // no class has a defined TCC (the only sub-metric that may be
    // genuinely missing — < 2 eligible method pairs).
    val cohesion: Double? = null,
    // `cpd.duplicatedLinesShare * 100`, rounded to 1dp. Always emitted.
    val duplication: Double = 0.0,
    // 5-signal weighted blend of `readability.summary` × 100, rounded to
    // 1dp. Always emitted (degenerate inputs blend to a stable 60).
    val readability: Double = 0.0,
    // Σ `pmd.methodMetrics[].cognitive`. Always emitted.
    val cognitive: Int = 0,
    // `pmd.violations.size`. Always emitted.
    val smells: Int = 0,
    val cleanliness: Cleanliness? = null,
    val process: ProcessScore = ProcessScore.EMPTY,
) {
    companion object {
        val EMPTY = DerivedMetrics()
    }
}

/**
 * Code-cleanliness composite at one checkpoint. Min-max normalised across
 * the main trajectory's observed range per sub-metric; weights match the
 * literature-informed mix used previously in the frontend (cognitive 0.25,
 * coupling 0.20, duplication 0.20, readability 0.15, smells 0.15,
 * cohesion 0.05). Weights re-base when a sub-metric is missing or
 * degenerate, in which case [rebased] is true.
 *
 * For alt checkpoints, the same main-trajectory range is used and the
 * normalised value is clamped to [0, 1] before blending — so alts read on
 * the same scale as the main path without their extremes shifting main
 * scores.
 */
@Serializable
data class Cleanliness(
    // 0..100, rounded; what the dashboard tile renders.
    val score: Int,
    // 0..1; the raw scalar consumed by the process-score gain term.
    val scalar: Double,
    // True if any literature-weighted sub-metric was excluded from this
    // checkpoint (absent on this checkpoint, or degenerate across the
    // trajectory). Surface as a UI hint that the score isn't a full sweep.
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

/**
 * Prefix-cumulative process score at one checkpoint. Anchored at 50; gain
 * + penalty terms add/subtract points; [total] is the clamped, rounded
 * 0..100 score the dashboard renders.
 */
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

/**
 * Trajectory-derived tracking for the violations on a single checkpoint.
 *
 * `firstSeenAtSha` is aligned 1:1 with `metrics.pmd.violations`: index `i`
 * holds the SHA of the earliest checkpoint at which `violations[i]` was first
 * observed in the trajectory (as judged by [com.github.ethanhosier.analysis.metrics.pmd.PmdViolationTracker]'s
 * line-mapping + `(file, rule)` keying). For the seed checkpoint every entry
 * is the seed's own SHA.
 *
 * `resolvedSincePrev` lists the previous checkpoint's violations that no
 * longer fire here. Empty on the seed and on any checkpoint that hasn't been
 * through the tracking pass.
 *
 * Together these let consumers derive added / carried / resolved per
 * checkpoint by index without re-running line mapping client-side.
 */
@Serializable
data class PmdTracking(
    val firstSeenAtSha: List<String> = emptyList(),
    val resolvedSincePrev: List<ResolvedPmdViolation> = emptyList(),
) {
    companion object {
        val EMPTY = PmdTracking()
    }
}

/**
 * Trajectory-derived tracking for the duplication groups on a single
 * checkpoint. `firstSeenAtSha` is aligned 1:1 with
 * `metrics.cpd.duplications`: index `i` holds the SHA of the earliest
 * checkpoint at which `duplications[i]` (matched by snippet-body identity)
 * was first observed. `resolvedSincePrev` lists the previous checkpoint's
 * clone groups that no longer appear here, snippets and all.
 *
 * Identity is the body-text hash carried on each `CpdDuplication.identity`
 * — pure code-motion (file/line shifts) preserves identity, edits to the
 * cloned body change it.
 */
@Serializable
data class CpdTracking(
    val firstSeenAtSha: List<String> = emptyList(),
    val resolvedSincePrev: List<ResolvedCpdDuplication> = emptyList(),
) {
    companion object {
        val EMPTY = CpdTracking()
    }
}

/**
 * A clone group that fired at the previous checkpoint but no longer fires
 * at the current one. Carries enough state (occurrences + snippets) for
 * the dashboard to render the resolved card without needing the previous
 * checkpoint's report.
 */
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
    // IntelliJ `refactoringId` (e.g. `refactoring.extractSuper`), lifted
    // from payload on REFACTORING_STARTED / REFACTORING_FINISHED. Omitted
    // entirely from the serialised form for non-refactoring events.
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val refactoringId: String? = null,
)
