package com.github.ethanhosier.analysis.metrics.model

import com.github.ethanhosier.analysis.metrics.gitdiff.DiffStats
import com.github.ethanhosier.analysis.miner.model.ManualRefactoringSegment
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
    val manualRefactorings: List<ManualRefactoringSegment> = emptyList(),
    val trajectory: TrajectoryStats = TrajectoryStats.ZERO,
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
) {
    companion object {
        val ZERO = TrajectoryStats(0, 0, 0.0, 0, 0, emptyMap(), 0, 0.0, 0, 0, 0)
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
