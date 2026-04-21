package com.github.ethanhosier.analysis.metrics.model

import com.github.ethanhosier.analysis.metrics.gitdiff.DiffStats
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
