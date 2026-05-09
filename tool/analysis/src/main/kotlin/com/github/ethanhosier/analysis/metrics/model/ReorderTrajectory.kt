package com.github.ethanhosier.analysis.metrics.model

import kotlinx.serialization.Serializable

/**
 * Multi-step alt trajectories synthesised over a single reorder
 * window. Sibling of [AlternativeTrajectory] (which captures
 * single-step replays of the user's actual ops). One entry per
 * reorder window; each entry carries every alt ordering we
 * synthesised for that window.
 *
 * Schema-wise this is "data Slice 2a writes; Slice 2b will
 * later attach metrics to each `stepShas[k]`." Empty list for
 * runs that found no eligible windows or where every window had
 * fewer than two typed VALID specs.
 */
@Serializable
data class ReorderTrajectory(
    /** Sequential index across the trace, matches the synthesiser's
     *  log line `window #N`. */
    val windowIndex: Int,
    /** First spec's `fromSha` in the window (= window's start state). */
    val windowFromSha: String,
    /** Last spec's `toSha` in user's actual order — the user's
     *  end-state for this window. */
    val windowToSha: String,
    /** Global stepIndex values for the window's specs, in user's
     *  actual order. Lets the dashboard cross-reference the alt
     *  orderings against the user's trajectory. */
    val windowStepIndexes: List<Int>,
    /** Human-readable label per window-local spec index, parallel
     *  to [windowStepIndexes]. Sourced from each step's
     *  `RefactoringStep.refactoring.description` (e.g.
     *  `"Rename Class com.example.Foo to com.example.Bar"`). The
     *  dashboard / a human reader can map each
     *  [ReorderOrdering.permutation] entry back to a description
     *  via `windowSpecLabels[permutation[k]]`. */
    val windowSpecLabels: List<String>,
    /** All alt orderings synthesised for this window. Excludes the
     *  user's own ordering (already in the trace). */
    val orderings: List<ReorderOrdering>,
)

/**
 * One alt ordering's synthesised commit chain.
 *
 * Sizes: on success, `stepShas.size == permutation.size` and
 * `branchRefs` is parallel. On per-step failure, both lists are
 * truncated at the failed step (the successful prefix's commits
 * survive in the shadow repo; only the failing step and any
 * suffix were not materialised).
 */
@Serializable
data class ReorderOrdering(
    /** 0-based within the parent window. */
    val orderIndex: Int,
    /** Window-local indices `(0..N-1)` in the order applied —
     *  e.g. `[1, 0, 2]` means "applied window-spec 1, then 0, then
     *  2." */
    val permutation: List<Int>,
    /** Human-readable label per applied step, parallel to
     *  [permutation]. `permutationLabels[k] ==
     *  trajectory.windowSpecLabels[permutation[k]]` — duplicated
     *  here so an ordering reads end-to-end without cross-referencing
     *  the parent trajectory. */
    val permutationLabels: List<String>,
    /** Synthesised commit SHAs, one per successful step. */
    val stepShas: List<String>,
    /** Shadow-repo branch refs `reorder/win<W>/ord<O>/step-<k>`,
     *  parallel to [stepShas]. */
    val branchRefs: List<String>,
    /** True iff every spec in [permutation] applied cleanly. */
    val terminalSuccess: Boolean,
    /** Window-local index of the first failing spec, or null if
     *  all succeeded. */
    val failedAt: Int? = null,
)
