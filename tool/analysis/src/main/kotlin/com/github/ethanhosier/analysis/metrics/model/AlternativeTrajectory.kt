package com.github.ethanhosier.analysis.metrics.model

import com.github.ethanhosier.analysis.miner.model.RefactoringSpec
import com.github.ethanhosier.analysis.pipeline.CheckpointReport
import com.github.ethanhosier.analysis.pipeline.DivergenceKind
import kotlinx.serialization.Serializable

/**
 * Top-level entry on [com.github.ethanhosier.analysis.pipeline.AnalysisReport] describing one alternative path the
 * user could have taken in place of (a slice of) their actual trajectory.
 *
 * Multi-step shape: a single entry can cover **N synthesised steps** all
 * branching from the same anchor [fromSha] and ending at the same user-side
 * end-state [userToSha]. Two callers populate this:
 *
 * 1. **Single-step IDE-driven alts** (`AlternativeTrajectoryRunner`) —
 *    one user step replayed via the refactoring bundle. All list fields
 *    have size 1.
 * 2. **Multi-step reorder orderings** (`ReorderSynthesiser`) — one
 *    permutation of the user's window steps applied as a chain of
 *    synthesised commits. List size equals the permutation length.
 *
 * Join keys for the frontend:
 *  - [stepIndexes] joins back into [com.github.ethanhosier.analysis.pipeline.AnalysisReport.refactoringSteps]
 *    (and into [com.github.ethanhosier.analysis.pipeline.AnalysisReport.alternativePatches] via each
 *    `altCheckpoints[i].sha`) for the unified diff per applied step.
 *  - [fromSha] / [userToSha] are SHAs in [com.github.ethanhosier.analysis.pipeline.AnalysisReport.checkpoints]
 *    (the user's actual pre / post states).
 *  - Each `altCheckpoints[i].sha` is a synthesised alt SHA, reachable in
 *    the shadow repo via the parallel `branchRefs[i]`.
 *  - [specs] is the typed refactoring info per applied step, parallel
 *    to [altCheckpoints], in the order applied by the alt.
 *
 * The terminal `altCheckpoints.last()` represents the alt's end state.
 * For reorder orderings it aliases by SHA to the user's `windowToSha`
 * checkpoint — AST-equivalent terminal means identical bytecode and
 * identical metrics, so no rebuild is needed.
 */
@Serializable
data class AlternativeTrajectory(
    /** Discriminates the alt's origin / divergence kind: ORDERING
     *  (reorder synth), IDE_REPLAY (single-step IDE-driven), or
     *  REWORK (surgical replay). Set at creation by the respective
     *  producer; downstream consumers switch on this rather than
     *  inferring from [specs] / [stepIndexes] shape. Default is
     *  ORDERING so older cached reports deserialise without surprise
     *  (the historical un-tagged shape was reorder alts). */
    val kind: DivergenceKind = DivergenceKind.ORDERING,
    /** User stepIndexes covered by this alt, in the order applied
     *  by the alt. IDE-driven alts: 1+ elements (one per refactoring
     *  in the (fromSha, toSha) group). Reorder orderings: the
     *  window's stepIndexes permuted. */
    val stepIndexes: List<Int>,
    /** Anchor SHA — the user-trajectory state this alt branches from. */
    val fromSha: String,
    /** User-trajectory end-state for the covered range. For reorder
     *  orderings this is `windowToSha`; for IDE-driven alts it's
     *  the user's post-step SHA at the group's `toSha`. */
    val userToSha: String,
    /** Shadow-repo refs. For IDE-driven alts: one entry (the group's
     *  squashed alt-SHA). For reorder orderings: one per applied step,
     *  parallel to [altCheckpoints]. */
    val branchRefs: List<String>,
    /** Specs applied, in the order applied by the alt. Parallel to
     *  [stepIndexes]. For IDE-driven groups this has size N matching
     *  the number of refactorings in the group; [altCheckpoints] still
     *  has size 1 (one squashed endpoint, not per-step states). */
    val specs: List<RefactoringSpec>,
    /** Synthesised checkpoints. IDE-driven alts: 1 element (the
     *  group's endpoint after all specs + residual merge). Reorder
     *  orderings: one per applied step. `events` and `touchedMembers`
     *  are always empty — alt SHAs aren't landed on by user events. */
    val altCheckpoints: List<CheckpointReport>,
    /** Residual 3-way merge result. Null for reorder alts (no residual
     *  concept — terminals AST-alias the user's `windowToSha`).
     *  For IDE-driven alts: present whenever residual was attempted.
     *  `applied=true` means the alt-SHA carries the user's leftover
     *  edits (the IDE-vs-manual delta is now the only difference vs
     *  `userToSha`). `applied=false` means 3-way merge conflicted and
     *  the alt-SHA is refactoring-only. */
    val residual: ResidualSummary? = null,
    /** User-trajectory checkpoints the alt's process-score line
     *  extends through after merging back at [userToSha]. Each entry
     *  is a full [CheckpointReport] — same shape as [altCheckpoints]
     *  and the main `checkpoints` list — so the frontend can render
     *  a normal detail panel for them.
     *
     *  Static metrics (build/tests, CK, PMD, CPD, readability,
     *  cleanliness) are identical to the user's at that sha because
     *  the code state is the same once the trees converge.
     *  `derivedMetrics.process`, however, carries the alt's
     *  recomputed score: the alt's terminal cumulative snapshot
     *  rolled forward through the user's subsequent activity via
     *  main-walk semantics. Process score is the only *cumulative*
     *  metric in the system — every other metric is state-only and
     *  therefore identical to the user's by construction.
     *
     *  Empty when [userToSha] is the last main checkpoint or the
     *  alt's anchor snapshot was missing. */
    val continuationCheckpoints: List<CheckpointReport> = emptyList(),
    /** REWORK only: user-checkpoint index that each entry in
     *  [altCheckpoints] semantically corresponds to. After
     *  whitespace-only intermediates are absorbed, the kept alt
     *  checkpoints no longer map 1-to-1 onto consecutive user steps;
     *  the dashboard uses this list to anchor each alt step at the
     *  matching user checkpoint's `xPos` on the chart instead of
     *  collapsing onto the compressed `[fromCp.xPos, toCp.xPos]`
     *  window. Empty for non-REWORK kinds and for no-op rework alts
     *  (which alias `userToSha` directly). */
    val altCheckpointUserIndexes: List<Int> = emptyList(),
)

/**
 * Result of applying the user's residual edits (`diff(refactoring-only,
 * userToSha)`) on top of a group's synthesised refactoring output.
 */
@Serializable
data class ResidualSummary(
    /** True iff the residual patch applied (cleanly or via 3-way
     *  merge resolution). False means the apply conflicted and the
     *  alt-SHA was reset to refactoring-only state. */
    val applied: Boolean,
    /** Lines added on top of the refactoring-only state. Zero when
     *  the user did nothing outside the refactoring (or when the
     *  apply was rejected wholesale). */
    val addedLines: Int,
    /** Lines deleted relative to the refactoring-only state. */
    val deletedLines: Int,
    /** Files whose hunks `git apply --3way` couldn't merge. Empty
     *  when [applied] is true. */
    val rejectedFiles: List<String>,
)