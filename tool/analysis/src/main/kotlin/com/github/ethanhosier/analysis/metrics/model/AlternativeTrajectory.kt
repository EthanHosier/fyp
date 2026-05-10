package com.github.ethanhosier.analysis.metrics.model

import com.github.ethanhosier.analysis.miner.model.RefactoringSpec
import kotlinx.serialization.Serializable

/**
 * Top-level entry on [AnalysisReport] describing one alternative path the
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
 *  - [stepIndexes] joins back into [AnalysisReport.refactoringSteps]
 *    (and into [AnalysisReport.alternativePatches] via each
 *    `altCheckpoints[i].sha`) for the unified diff per applied step.
 *  - [fromSha] / [userToSha] are SHAs in [AnalysisReport.checkpoints]
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
    /** User stepIndexes covered by this alt, in the order applied
     *  by the alt. Single-step alts: 1 element. Reorder orderings:
     *  the window's stepIndexes permuted. */
    val stepIndexes: List<Int>,
    /** Anchor SHA — the user-trajectory state this alt branches from. */
    val fromSha: String,
    /** User-trajectory end-state for the covered range. For reorder
     *  orderings this is `windowToSha`; for single-step alts it's
     *  the user's post-step SHA. */
    val userToSha: String,
    /** Shadow-repo refs, one per applied step, parallel to
     *  [altCheckpoints]. */
    val branchRefs: List<String>,
    /** Specs applied, parallel to [altCheckpoints] and [stepIndexes]. */
    val specs: List<RefactoringSpec>,
    /** Synthesised checkpoints, one per applied step. `events` and
     *  `touchedMembers` are always empty — alt SHAs aren't landed on by
     *  user events. */
    val altCheckpoints: List<CheckpointReport>,
)