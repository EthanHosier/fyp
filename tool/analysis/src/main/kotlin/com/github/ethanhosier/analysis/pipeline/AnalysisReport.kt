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
    // Unified-diff patch text per applied alt step, keyed by the
    // synthesised step's `altSha` (= `altCheckpoints[i].sha`). Each
    // entry's text is the `fromSha → altSha` diff. Single-step alts
    // contribute one entry; multi-step reorder orderings contribute
    // one entry per applied step. Frontend looks up by altSha rather
    // than stepIndex so multi-step alts (which span multiple user
    // step indexes) round-trip cleanly.
    val alternativePatches: Map<String, String> = emptyMap(),
    // Multi-step alt orderings synthesised by ReorderSynthesiser.
    // One entry per reorder window with ≥2 typed VALID specs;
    // each carries every alt ordering's commit chain (excluding
    // the user's own ordering). Slice 2a populates this with
    // synthesised SHAs only — metrics for these intermediates are
    // a future slice. Empty default keeps older cached reports
    // deserialisable.
    val reorderTrajectories: List<ReorderTrajectory> = emptyList(),
    // Commits the user made on their working repo during the session,
    // captured by the IDE plugin's reflog watcher. Chronological order.
    // Pure overlay — does not influence metrics / checkpoints. Empty
    // when the session ran without any commits or in a non-git project.
    val userGitCommits: List<UserGitCommit> = emptyList(),
    // Trajectory-wide observations produced by `TrajectoryAdvisor` —
    // a fixed registry of pattern-match rules over this report. Each
    // item is display-ready (title + body strings); the dashboard
    // renders them in a panel below the chart. Empty when no rule
    // fires (typical of very short or pristine sessions).
    val advice: List<AdviceItem> = emptyList(),
    // Per-step divergence points. Each entry pinpoints a moment in the
    // user's trajectory where a better process was available, grouping
    // one-or-more alternative trajectories (by index into
    // `alternativeTrajectories`) that demonstrate the counterfactual.
    // Kinds: ORDERING (reorder window), IDE_REPLAY (manual edit the
    // IDE could have done in one step), REWORK (add-then-undo round
    // trip). HYGIENE is reserved but not yet emitted.
    val divergencePoints: List<DivergencePoint> = emptyList(),
)

/**
 * Categorises a divergence point by the kind of counterfactual it
 * surfaces. Maps 1:1 to the producer that emitted the backing
 * alt trajectory.
 */
@Serializable
enum class DivergenceKind {
    ORDERING,
    IDE_REPLAY,
    REWORK,
    HYGIENE,
}

/**
 * One named moment in the user's trajectory where a better process was
 * available. Anchored at [stepIndex] (the user step where the divergence
 * resolves) and pointing at one-or-more [altTrajectoryIndexes] into
 * [AnalysisReport.alternativeTrajectories] that demonstrate the
 * counterfactual.
 *
 * Per-kind extras are nullable and populated only when meaningful for
 * that kind — the frontend switches on [kind] to render them.
 */
@Serializable
data class DivergencePoint(
    /** Anchor step on the user trajectory — for ORDERING this is the
     *  window's terminal user step, for IDE_REPLAY the single step
     *  replayed, for REWORK the **originating** step (where the
     *  reverted code first appeared) so the indicator surfaces on the
     *  user's fromSha rather than the toSha that cancels it out. */
    val stepIndex: Int,
    val kind: DivergenceKind,
    /** Magnitude for ranking / threshold filtering. Process-score
     *  delta (alt − user) for ORDERING / IDE_REPLAY; reverted line
     *  count for REWORK. Always non-negative; sign is implied by kind. */
    val magnitude: Double,
    /** Short headline (≤80 chars). */
    val title: String,
    /** 1–3 sentence body. Backend-templated, no LLM. */
    val explanation: String,
    /** Indexes into [AnalysisReport.alternativeTrajectories]. Most kinds
     *  carry exactly one; ORDERING may carry multiple permutations of
     *  the same window. */
    val altTrajectoryIndexes: List<Int>,
    /** ORDERING: the user step indexes covered by the reorder window. */
    val orderingWindowSteps: List<Int>? = null,
    /** REWORK: step where the reverted code first appeared. */
    val originatingStepIndex: Int? = null,
    /** REWORK: file containing the reworked region. */
    val file: String? = null,
    /** REWORK: enclosing-member identifier, e.g. `Foo#bar(int)`. */
    val scopeLabel: String? = null,
    /** REWORK: total reverted lines (sum across paired chunks). */
    val reworkLineCount: Int? = null,
    /** IDE_REPLAY: the IntelliJ refactoringId of the replaced step. */
    val replacedRefactoringId: String? = null,
    /** REWORK: focused unified-diff patch for the originating step,
     *  containing only the matched chunk's hunk (pure insertion for
     *  add-then-remove, pure removal for remove-then-add). The
     *  dashboard renders this directly — no client-side filtering. */
    val originatingPatch: String? = null,
    /** REWORK: focused unified-diff patch for the terminal step — the
     *  inverse of [originatingPatch]. */
    val terminalPatch: String? = null,
    /** REWORK: which side of the round trip was the originating edit.
     *  `"ADD_THEN_REMOVE"` means the user added the chunk at the
     *  originating step and removed it at the terminal step;
     *  `"REMOVE_THEN_ADD"` is the inverse. Used by the dashboard to
     *  pick correct "code added/removed" copy per side. */
    val reworkDirection: String? = null,
    /** HYGIENE: discriminates the hygiene sub-pattern for UI copy.
     *  Currently `"TESTS_SKIPPED"` or `"COMMIT_GAP"`. */
    val hygieneSubKind: String? = null,
    /** HYGIENE: contextual length (e.g. commit-gap length in checkpoints)
     *  surfaced in the explanation template. */
    val hygieneStretchLength: Int? = null,
)

/**
 * Sealed set of advice rules that can fire over a session's trajectory.
 * Each value identifies the *pattern* a rule matched — frontend uses
 * the kind to choose icon / colour and to group related advice.
 */
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

/**
 * One observation surfaced to the user. `title` is a short headline
 * suitable for a row header ("You broke the build often"); `body` is
 * 1–3 sentences explaining what fired the rule and why it matters,
 * with concrete numbers baked in. Both strings are produced backend-
 * side so the frontend stays a passthrough.
 */
@Serializable
data class AdviceItem(
    val kind: AdviceKind,
    val severity: AdviceSeverity,
    val title: String,
    val body: String,
)

/**
 * A commit landed on the user's working repo during the session. SHA +
 * parent + commit time come straight from the `.git/logs/HEAD` reflog
 * line; [message] is the full commit body fetched via `git log`. [action]
 * preserves the reflog action prefix so consumers can distinguish e.g.
 * `commit` from `commit (amend)` / `commit (merge)`.
 *
 * Used by the dashboard to render commit-cadence markers on the trajectory
 * chart and to support derived "refactorings between commits" analyses.
 */
@Serializable
data class UserGitCommit(
    val sha: String,
    val parentSha: String? = null,
    /** Reflog author/committer time in **milliseconds** since epoch. */
    val timestamp: Long,
    val message: String,
    val action: String,
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
    // True when this checkpoint's SHA matches one of the user's commits
    // (`AnalysisReport.userGitCommits`). Defaulted false so the process-
    // score walker can read it uniformly without breaking older reports.
    // V1: read-only — no penalty term consumes this yet. Wired so that
    // when commit cadence joins the score, HYGIENE COMMIT_GAP alts that
    // already flip this bit immediately produce a real magnitude.
    val isUserCommit: Boolean = false,
    // True iff this checkpoint's cleanliness aggregates were derived
    // from the analysers' own output at this SHA. False iff build or
    // tests were broken here and the aggregates were carried forward
    // (from a prior trustworthy checkpoint or, if none exists yet, set
    // to the session midpoint). Raw `metrics` block is unchanged
    // either way — only the derived cleanliness layer is affected.
    val metricsTrustworthy: Boolean = true,
    // When [metricsTrustworthy] is false, identifies the fallback used:
    //  - "PRIOR"    → aggregates carried from the previous trustworthy
    //                 checkpoint.
    //  - "MIDPOINT" → no prior trustworthy checkpoint existed yet;
    //                 aggregates were filled with the session midpoint
    //                 of each sub-signal's range.
    // Null when [metricsTrustworthy] is true.
    val metricsCarryForwardSource: String? = null,
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
    // Mean of CK `cbo` across classes whose file lies in the trajectory-
    // touched set, rounded to 1dp. 0.0 when the touched set yields no
    // CK data at this checkpoint.
    val coupling: Double = 0.0,
    // Mean of non-null CK `tcc` across touched classes, rounded to 2dp.
    // Null when no touched class has a defined TCC (the only sub-metric
    // that may be genuinely missing — < 2 eligible method pairs).
    val cohesion: Double? = null,
    // Touched-file duplication rate:
    //   100 * duplicated_lines_in_touched / total_lines_in_touched,
    // rounded to 1dp. 0.0 when the touched set has no lines.
    val duplication: Double = 0.0,
    // Uniform 0.20 blend of five Buse-Weimer 2010 features
    // (line length, indentation, identifier length, single-letter rate,
    // dictionary-word rate), line-count-weighted across touched files,
    // × 100, rounded to 1dp. 0.0 when no touched file exists.
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

/**
 * Code-cleanliness composite at one checkpoint. Min-max normalised across
 * the main trajectory's observed range per sub-metric; uniform 1/6
 * weighting across the six sub-signals (Laplace's principle of
 * insufficient reason — see RESEARCH-cleanliness-metrics.md §7). Weights
 * re-base when a sub-metric is missing or degenerate, in which case
 * [rebased] is true.
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
    alternative: IdeRefactoringsRunner.Summary,
    diffs: DiffsRunner.Summary,
    trackedCodeSmells: PmdTrackingRunner.Summary,
    trackedDuplication: CpdTrackingRunner.Summary = CpdTrackingRunner.Summary(emptyMap(), emptyMap()),
    parallelism: Int,
    metricsDurationMs: Long,
    reorderTrajectories: List<ReorderTrajectory> = emptyList(),
    /** CheckpointMetrics keyed by altSha across single-step alts and
     *  reorder per-step alts (terminals included via Slice 2b alias).
     *  Defaults to `metrics.alternativeCheckpoints.associateBy { it.sha }`
     *  for callers that don't synthesise terminal aliases (e.g. tests). */
    augmentedAltMetricsBySha: Map<String, CheckpointMetrics>? = null,
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

    // SHAs the user actually committed during the session, derived from
    // the GIT_COMMIT events. Used to tag CheckpointReports so the
    // process-score walker can see commits as a property of the data —
    // future commit-cadence penalty will consume this, and HYGIENE
    // COMMIT_GAP alts already flip it.
    val userCommitShas = trace.events.asSequence()
        .filter { it.type == EventType.GIT_COMMIT }
        .mapNotNull { it.payload["sha"] }
        .toSet()

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
            isUserCommit = m.sha in userCommitShas,
            diff = metrics.diffBySha[m.sha] ?: DiffStats.ZERO,
            touchedMembers = membersBySha[m.sha]?.toList().orEmpty(),
            pmdTracking = trackedCodeSmells.trackingBySha[m.sha] ?: PmdTracking.EMPTY,
            cpdTracking = trackedDuplication.trackingBySha[m.sha] ?: CpdTracking.EMPTY,
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
            pmdTracking = trackedCodeSmells.alternativeTrackingBySha[sha] ?: PmdTracking.EMPTY,
            cpdTracking = trackedDuplication.alternativeTrackingBySha[sha] ?: CpdTracking.EMPTY,
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
    val userIdxBySha = baseCheckpoints.withIndex().associate { (i, cp) -> cp.sha to i }
    val reworkAltsWithInfo: List<Pair<AlternativeTrajectory, ReworkSynthesiser.SynthesisedRework>> =
        reworkSummary?.synthesised.orEmpty().mapNotNull { rw ->
            val altCps = rw.altShas.map { sha ->
                val cp = altCheckpointFor(sha) ?: return@mapNotNull null
                if (sha in userShaSet) cp.copy(diff = DiffStats.ZERO) else cp
            }
            // Map each kept alt checkpoint to the user checkpoint it
            // semantically lands at. Plan position `k` cherry-picks the
            // user step originating at `fromSha + k`, landing on user
            // checkpoint `fromIdx + k + 1`. Used by the chart to anchor
            // alt steps against the user's tMs-interpolated layout
            // after whitespace-only intermediates have been dropped.
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

    val baseAlternatives = singleStepAlts + reorderAlts + reworkAlts
    val reworkBaseOffset = singleStepAlts.size + reorderAlts.size

    val derived = DerivedMetricsRunner().run(
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

    // Hygiene alts: scan the (now derived-metrics-bearing) user
    // checkpoints for skipped tests / commit gaps, then synthesise one
    // alt per finding by flipping the anchor checkpoint's status and
    // re-running the process-score walk over the mutated trajectory.
    // Done after the main derived run so the user's own derivedMetrics
    // are stable; the hygiene alts pre-bake their own derivedMetrics
    // so they don't need to pass through DerivedMetricsRunner again.
    val hygieneFindings = HygieneDetector.detect(checkpoints, miner.steps)
    val hygieneAlts = HygieneDetector.buildAlts(
        findings = hygieneFindings,
        checkpoints = checkpoints,
        refactoringSteps = miner.steps,
    )

    val userCpBySha = checkpoints.associateBy { it.sha }
    val alternativeTrajectoriesNoHygiene = baseAlternatives.mapIndexed { i, alt ->
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
    // Hygiene alts already carry pre-baked derivedMetrics on their
    // altCheckpoints + continuationCheckpoints (no second pass through
    // DerivedMetricsRunner needed). Appended after the IDE / reorder /
    // rework alts so their indexes are stable for the DP builder.
    val alternativeTrajectories = alternativeTrajectoriesNoHygiene + hygieneAlts
    val hygieneBaseOffset = alternativeTrajectoriesNoHygiene.size

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