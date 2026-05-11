# Multi-step alt-trajectory synthesis (Slice 2a, synthesis-only)

Wire a new pipeline stage that, for each VALID reorder window,
materialises every valid ordering of the window's specs as a
chain of git commits in the shadow repo. Use an **ordered-prefix
cache** so orderings that share a prefix re-use the materialised
commits instead of re-applying. No metrics, no scoring, no
dashboard — just synthesis + log + report fields naming the
resulting branch refs.

## Context

The pipeline currently:
1. Mines refactorings (`RefactoringMinerRunner`).
2. Validates per-step (`RefactoringStepValidator`) — marks each
   step `VALID` / `UNTYPED` / `AST_DIVERGED` / `REFACTOR_FAILED`.
3. Splits the trace into reorder windows of contiguous VALID
   steps (`ReorderWindowLogger.splitOnInvalid`) and just *logs*
   them — no synthesis happens for multi-step orderings.
4. Single-step alt synthesis runs (`AlternativeTrajectoryRunner`).
   This produces one alt SHA per VALID step, by replaying the
   user's refactoring on the same `fromSha`. It does **not**
   produce alt SHAs for *different orderings* of consecutive
   steps.

For the thesis question — "did the user's chosen ordering minimise
process cost vs valid permutations?" — we need alt SHAs for
*orderings*, not just per-step replays. This slice is the
synthesis half; metrics/scoring is Slice 2b.

The DAG-based dependency analysis (`SpecDependencyAnalyzer` +
`TopologicalEnumerator`) is over-optimistic about commutativity:
two orderings that the DAG says reach the same end-state may in
fact produce different trees (or one may JDT-fail where the other
doesn't). So we **synthesise every ordering individually** rather
than dedup by bitset. The savings come from an **ordered-prefix
cache** — two orderings sharing `[R0, R1]` reuse the same
materialised commit for that prefix; only their divergent
suffixes are re-applied.

Recently shipped `RefactoringClient.withBatchSession` lets a
sequence of applies on the same worktree share one JDT
init+index. Each ordering's uncached suffix runs inside one
session.

## Out of scope

- **Metrics / scoring.** Synthesised alt SHAs go into the report
  but `MetricsRunner` doesn't run on them yet. Slice 2b adds
  `alternativeShas` for these.
- **Dashboard.** No frontend changes. JSON shape is added; UI
  picks it up later.
- **Parallel ordering synthesis.** Orderings within a window are
  processed sequentially so the prefix cache hit rate is
  maximal. Cross-window parallelism deferred.
- **Replacing single-step `AlternativeTrajectoryRunner`.** Stays
  as-is; it covers per-step replay which is independent of
  ordering exploration.
- **Validator integration.** The validator's bracket flow
  doesn't change; it doesn't need orderings.

## Architecture

### Stage placement

`ReorderWindowLogger` was a temporary inspection-only stage; its
window-splitting logic is the only load-bearing part. The new
`ReorderSynthesiser` **replaces** it entirely — absorbs
`splitOnInvalid`, takes ownership of the summary counts the
pipeline currently logs, and adds the synthesis loop on top.

`AlternativeTrajectoryRunner` (single-step alts) stays after the
new stage, unchanged.

```
… validator → [NEW: ReorderSynthesiser] → singleStep alt → metrics …
```

Files removed in this slice:
- `analysis/.../alternative/reorder/ReorderWindowLogger.kt`
- `analysis/.../alternative/reorder/ReorderWindowLoggerTest.kt`

The split-on-invalid concerns from those tests migrate into
`ReorderSynthesiserTest`. `ReorderDebug` (per-window DAG describe
helper) stays — the synthesiser still calls it for diagnostic
output.

### Pipeline wiring

`AnalysisPipeline.kt:158` — replace the
`ReorderWindowLogger.log(...)` call with:

```kotlin
val synthSummary = ReorderSynthesiser(
    client = refactoringClient,
    shadowGit = shadowGit,
    pool = worktreePool,
).run(
    steps = miner.steps,
    validations = validationsByIndex,
    log = { line -> log(line) },
)
val reorderTrajectories = synthSummary.trajectories
log(
    "reorder synth: ${synthSummary.eligibleWindows} eligible window(s), " +
        "${synthSummary.singletonWindows} singleton(s) of " +
        "${synthSummary.totalWindows} total " +
        "(${synthSummary.typedCount} typed, ${synthSummary.untypedCount} untyped, " +
        "${synthSummary.divergentCount} diverged, ${synthSummary.refactorFailedCount} failed); " +
        "synthesised ${synthSummary.orderingsSynthesised} orderings, " +
        "${synthSummary.commitsCreated} commits, " +
        "${synthSummary.prefixCacheHits} prefix-cache hits"
)
```

The synthesiser's `run` takes the unsplit `steps + validations`
inputs (same shape `ReorderWindowLogger.log` took today). It
does the splitting internally — no extra "expose splitWindows"
step needed.

### ReorderSynthesiser — core type

New file
`analysis/.../alternative/synthesise/ReorderSynthesiser.kt`.

```kotlin
class ReorderSynthesiser(
    private val client: RefactoringClient,
    private val shadowGit: GitRunner,
    private val pool: WorktreePool,
    private val dispatcher: SpecDispatcher = SpecDispatcher(client),
    private val budget: EnumerationBudget = EnumerationBudget(),
) {
    data class Summary(
        val trajectories: List<ReorderTrajectory>,
        // Splitting counts (migrated from ReorderWindowLogger.Summary)
        val totalWindows: Int,
        val eligibleWindows: Int,
        val singletonWindows: Int,
        val typedCount: Int,
        val untypedCount: Int,
        val divergentCount: Int,
        val refactorFailedCount: Int,
        // Synthesis counts (new)
        val orderingsSynthesised: Int,
        val commitsCreated: Int,
        val prefixCacheHits: Int,
    )

    fun run(
        steps: List<RefactoringStep>,
        validations: Map<Int, RefactoringStepValidator.StepValidation>,
        log: (String) -> Unit,
    ): Summary
}
```

Algorithm:

1. **Split the trace into windows.** Private
   `splitOnInvalid(steps, validations, log)` — verbatim copy of
   the same method on the deleted logger. Steps whose validation
   status is not VALID are splitters (and emit a
   `"reorder synth: split at step #N (...)"` log line, identical
   in shape to today's `reorder-log: split at …`).
2. **Compute splitter counts** for the summary by walking
   `validations` once.
3. For each window:
   a. Filter to `step.spec != null` (typed). If `< 2` typed
      specs, increment `singletonWindows`, skip.
   b. Increment `eligibleWindows`. Log
      `"reorder synth: window #wIdx fromSha→toSha (N typed specs, step indices=[…]):"`
      and pipe `ReorderDebug.describe(specs)` through the logger
      (same diagnostic output the deleted logger produced).
   c. Build the DAG: `SpecDependencyAnalyzer.analyze(specs)`.
   d. Enumerate orderings:
      `TopologicalEnumerator.enumerate(dag, budget)`.
      If `skipReason != null`, log it and continue.
      If `truncated`, log a warning but proceed with what we got.
   e. **Drop the user's own ordering.** The user's order is the
      identity permutation `[0, 1, …, N-1]` (steps arrive in
      stepIndex order from the validator). That commit chain
      already exists in the user's actual trace.
   f. Set up an empty `PrefixCache(fromSha = window.first().fromSha)`
      for this window.
   g. For each remaining ordering, call
      `synthesiseOrdering(window, ordering, cache, wIdx, ordIdx)`.
4. Aggregate per-window `ReorderOrdering` lists into one
   `ReorderTrajectory` per window. Tally synthesis counts.
   Return the `Summary`.

### Prefix cache

`OrderedPrefix` is a `List<Int>` of spec indices into the window
(e.g. `[0, 1]` means "applied window-spec #0, then #1"). The
empty list `[]` always maps to `fromSha` (the window's start).

```kotlin
internal class PrefixCache(private val fromSha: String) {
    private val cache: MutableMap<List<Int>, String> =
        mutableMapOf(emptyList<Int>() to fromSha)
    fun deepestHit(ordering: List<Int>): Pair<List<Int>, String>
    fun put(prefix: List<Int>, sha: String)
}
```

`deepestHit` walks the ordering left-to-right, returning the
longest cached prefix and its SHA. The remaining suffix is what
needs to be replayed. `put` caches each new commit's prefix as
synthesis progresses.

Cache lifetime: per-window. Reset between windows (different
`fromSha`s and different specs anyway).

### synthesiseOrdering (per ordering)

```kotlin
private fun synthesiseOrdering(
    window: List<RefactoringStep>,
    ordering: List<Int>,                  // permutation of [0..N-1]
    cache: PrefixCache,
    windowIdx: Int,
    orderIdx: Int,
): OrderingResult {
    val (prefix, prefixSha) = cache.deepestHit(ordering)
    val suffix = ordering.drop(prefix.size)

    val worktree = pool.borrow(prefixSha)
    try {
        val worktreeGit = GitRunner(worktree)
        worktreeGit.setLocalIdentity("alt@analysis.local", "Alternative Trajectory")
        val stepShas = mutableListOf<String>()
        var currentPrefix = prefix.toMutableList()

        client.withBatchSession {
            for (specIdx in suffix) {
                val spec = window[specIdx].spec!!
                val outcome = dispatcher.apply(spec, worktree)
                if (outcome is SpecDispatcher.Result.Failed) {
                    return@withBatchSession  // partial prefix already cached
                }
                worktreeGit.addAll()
                if (!worktreeGit.hasStagedChanges()) {
                    return@withBatchSession  // empty refactoring; skip
                }
                val sha = worktreeGit.commit("reorder win=$windowIdx ord=$orderIdx step=$specIdx")
                val ref = "reorder/win$windowIdx/ord$orderIdx/step-${currentPrefix.size}"
                shadowGit.branchForce(ref, sha)
                currentPrefix.add(specIdx)
                cache.put(currentPrefix.toList(), sha)
                stepShas.add(sha)
            }
        }
        return OrderingResult(orderIdx, ordering, stepShas, success = stepShas.size == suffix.size)
    } finally {
        pool.release(worktree)
    }
}
```

Notes:
- `pool.borrow(prefixSha)` works for both `fromSha` (cache miss)
  and any cached intermediate (cache hit) — `WorktreePool` calls
  `git worktree add` which accepts any SHA.
- The branch ref naming `reorder/win$winIdx/ord$ordIdx/step-$k`
  uses a separate namespace from `alt/$stepIndex` so single-step
  refs and reorder refs don't collide.
- On per-step failure: short-circuit via
  `return@withBatchSession`. Successful prefix commits are kept
  and cached; future orderings benefit.
  `OrderingResult.success = false` flags the incompleteness.

### Schema additions

New file `analysis/.../metrics/model/ReorderTrajectory.kt`:

```kotlin
@Serializable
data class ReorderTrajectory(
    val windowIndex: Int,                   // 0-based across the trace
    val windowFromSha: String,
    val windowToSha: String,                // user's last toSha in the window
    val windowStepIndexes: List<Int>,       // global stepIndex values, in user's order
    val orderings: List<ReorderOrdering>,
)

@Serializable
data class ReorderOrdering(
    val orderIndex: Int,                    // 0-based within the window
    val permutation: List<Int>,             // window-local indices (0..N-1)
    val stepShas: List<String>,             // size == permutation.size on success, < on failure
    val branchRefs: List<String>,           // parallel to stepShas
    val terminalSuccess: Boolean,
    val failedAt: Int? = null,              // window-local index of failing spec
)
```

Add to `AnalysisReport.kt`:

```kotlin
val reorderTrajectories: List<ReorderTrajectory> = emptyList()
```

Sibling field — does **not** replace `alternativeTrajectories`
(that's the single-step shape and remains as-is).

### Pipeline log

The synthesiser emits the same `split` and `window` log lines
the deleted logger did (prefix changed from `reorder-log:` to
`reorder synth:`), plus per-ordering counts:

```
reorder synth: split at step #4 (AST_DIVERGED — file-content mismatch)
reorder synth: window #0 abc12345→def67890 (2 typed specs, step indices=[2, 3]):
  <ReorderDebug.describe output, indented>
  ord #0 [1, 0]: 2 commits (cache hits: 0)
reorder synth: 3 eligible window(s), 1 singleton(s) of 5 total (…); synthesised 12 orderings, 27 commits, 5 prefix-cache hits
```

## Critical files

**New:**
- `analysis/.../alternative/synthesise/ReorderSynthesiser.kt` —
  the new stage.
- `analysis/.../alternative/synthesise/PrefixCache.kt` — small
  helper, separately testable.
- `analysis/.../metrics/model/ReorderTrajectory.kt` — schema.
- `analysis/.../alternative/synthesise/ReorderSynthesiserTest.kt`
  — unit tests with fake `SpecDispatcher`.
- `analysis/.../alternative/synthesise/PrefixCacheTest.kt` —
  unit tests for cache lookup logic.

**Modified:**
- `analysis/.../pipeline/AnalysisPipeline.kt` — replace the
  `ReorderWindowLogger.log(...)` call with
  `ReorderSynthesiser.run(...)`.
- `analysis/.../AnalysisReport.kt` — add the new field.

**Deleted:**
- `analysis/.../alternative/reorder/ReorderWindowLogger.kt`.
- `analysis/src/test/.../reorder/ReorderWindowLoggerTest.kt`
  (split-on-invalid concerns migrate into
  `ReorderSynthesiserTest`).

**Reused (no change):**
- `RefactoringClient.withBatchSession`.
- `SpecDispatcher.apply`.
- `SpecDependencyAnalyzer.analyze`.
- `TopologicalEnumerator.enumerate` + `EnumerationBudget`.
- `WorktreePool.borrow / release`.
- `GitRunner.addAll / hasStagedChanges / commit / branchForce /
  setLocalIdentity`.
- The `AlternativeTrajectoryRunner.synthesiseOne` pattern is the
  template for our per-spec block.

## Tests

### `PrefixCacheTest`

1. `empty_cache_returns_fromSha_for_any_ordering` —
   `deepestHit([2,0,1])` returns `([], fromSha)`.
2. `populated_prefix_returns_deepest_match` — put `[0]→shaA`,
   `[0,1]→shaB`. Lookup `[0,1,2]` returns `([0,1], shaB)`.
   Lookup `[0,2,1]` returns `([0], shaA)`.
3. `divergent_orderings_share_no_prefix` — put `[0,1]→shaB`.
   Lookup `[1,0,2]` returns `([], fromSha)`.

### `ReorderSynthesiserTest` (fakes)

Fake `SpecDispatcher` returns `Ok` for known
(specIdx, worktreeState) combos; tracks invocation count. Real
`GitRunner` on a `@TempDir` git repo. Real `PrefixCache`.

The window-splitting concerns from the deleted
`ReorderWindowLoggerTest` migrate here as a `Nested` group
(`@Nested class WindowSplitting`) covering: split on UNTYPED,
split on AST_DIVERGED, split on REFACTOR_FAILED, missing
validation entries treated as splitters, empty input, summary
counts. Same fixtures, retargeted at `ReorderSynthesiser.run`'s
returned summary.

Synthesis-specific cases:

4. `two_orderings_sharing_a_prefix_apply_each_spec_once_for_the_prefix`
   — orderings `[0,1,2]` and `[0,1,3]` (4 specs in window).
   Assert the dispatcher was called once for `0`, once for `1`,
   once for `2`, once for `3` — total 4 applies, not 6.
5. `two_orderings_with_no_common_prefix_apply_independently` —
   orderings `[0,1]` and `[1,0]`. Assert dispatcher was called 4
   times (no cache reuse beyond the empty prefix).
6. `partial_failure_keeps_succeeded_prefix_for_later_orderings`
   — fake dispatcher fails at spec 1 in ordering `[0,1,2]`.
   Subsequent ordering `[0,2,1]` should reuse the cached `[0]`
   prefix (only one apply for spec 0 across both orderings).
7. `user_ordering_is_skipped` — N=3, identity ordering `[0,1,2]`
   is the user's. Assert it's not in the resulting
   `ReorderOrdering` list, and dispatcher is never called for
   the identity ordering.
8. `windows_smaller_than_2_are_skipped` — window of 1 typed
   spec produces zero `ReorderOrdering`s.
9. `enumerator_truncation_is_recorded` — window of 8 specs
   (above default `maxNodes=7`). Assert the window appears in
   the summary with a "truncated/skipped" marker but no commits.
10. `branchRef_namespace_does_not_collide_with_single-step_alts`
    — fake `shadowGit` records branch creations. Assert all
    refs match `"reorder/win\\d+/ord\\d+/step-\\d+"`.

### Integration smoke (drives the real bundle)

11. `reorder_synth_two_orderings_extract_method_then_rename` —
    fixture project + window of [ExtractMethod, RenameMethod].
    Both pairwise commute (no edge in the DAG). `enumerate`
    gives 2 orderings: `[0,1]` (user's, skipped) and `[1,0]`.
    Synthesise `[1,0]`. Assert two commits, two refs, each
    ref's tree matches expected post-step-K snapshot (compare
    via `JavaFileAstHasher.hashFile`).

### Existing tests

All existing pipeline / validator / single-step alt tests must
pass unchanged.

## Verification

1. `./gradlew :analysis:test :refactoring-bundle:test` — green.
2. **Real-session smoke**: run the pipeline on the example
   session. Inspect the new `reorder synth` log lines. Confirm
   at least one window produces alt orderings, branch refs are
   reachable in the shadow repo, the report's
   `reorderTrajectories` array is non-empty.
3. **Branch reachability**: pick one synthesised SHA from
   `reorderTrajectories[0].orderings[0].stepShas`, check it out
   into a fresh worktree from the shadow repo, eyeball that the
   tree differs from `fromSha` and is consistent with the
   recorded permutation.
4. **Prefix cache effectiveness check**: in the synthesiser
   log, verify "prefix-cache hits: K" matches what we'd expect
   by hand for a small window (K=2 → at most 1 hit; K=3 → up to
   ~4 hits depending on enumerator order).

## Risks

- **Enumerator emits orderings in an order that defeats the
  prefix cache.** If it emits `[0,1,2]`, then `[2,0,1]`, then
  `[0,2,1]`, the cache loses hits between consecutive orderings.
  *Mitigation:* the enumerator currently uses Kahn's-style DFS
  which tends to produce lexicographically-grouped orderings;
  this is good for prefix reuse. If it ever changes, sort the
  orderings by lex-prefix-stable order before iterating.
- **JDT failure on uncommuting orderings.** Two orderings the
  DAG says commute may in fact produce different trees, or one
  may JDT-fail. *Mitigation:* that's the *whole point* of
  synthesising each ordering individually. Failures are
  recorded (`failedAt`) and successful prefixes are kept for
  cache reuse. Slice 2b will also end-state-hash-check each
  ordering's terminal SHA against `windowToSha` to flag
  divergences.
- **Combinatorial blow-up.** N=7 with full commutativity = 5040
  orderings. *Mitigation:* `EnumerationBudget` already caps
  `maxNodes=7` and `maxOrderings=5040`. Beyond that the window
  is skipped with a logged reason.
- **Worktree pool exhaustion.** Each ordering's `borrow` blocks
  if the pool is full. Sequential ordering synthesis means at
  most one borrow at a time per window — well within any
  reasonable pool size.
- **`AnalysisReport` schema migration.** `reorderTrajectories`
  is a new field with a default of `emptyList()`. Older saved
  reports deserialise without it, so no migration needed.
