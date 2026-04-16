package com.github.ethanhosier.analysis.metrics.model

import com.github.ethanhosier.analysis.miner.model.RefactoringFinding
import com.github.ethanhosier.ideplugin.model.EventType
import com.github.ethanhosier.ideplugin.model.SessionMetadata
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
    val manualRefactorings: List<RefactoringFinding> = emptyList(),
)

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
)

@Serializable
data class EventSummary(
    val id: String,
    val type: EventType,
    val timestamp: Long,
)
