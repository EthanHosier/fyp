# HYGIENE divergence points

## Context

`DivergenceKind` already has a `HYGIENE` variant but nothing emits it.
The three existing kinds (ORDERING, IDE_REPLAY, REWORK) each synthesise
a real alternative trajectory with re-run metrics, so their DP magnitude
is a process-score delta against a concrete counterfactual. We want
HYGIENE to fit the same shape — but only where the counterfactual is
honest.

**Honest counterfactual = the code state is identical between user and
alt; only a process decision differs.** Two hygiene patterns satisfy
this:

- **Skipped tests after a refactor.** The user didn't run the tests.
  Same code, just a decision to run them or not. Flipping
  `tests.wasSkipped = false` (and treating the result as a pass) gives
  a legitimate counterfactual — W_SKIP_TESTS = 14 lifts off the score.
- **Commit gap.** The user didn't commit at points where they should
  have. Same code, just a decision to commit. We flip a "this checkpoint
  is a commit" bit; today commit cadence isn't in the process score so
  the magnitude is 0, but the *infrastructure* lands now so when
  cadence is added later the DPs light up automatically.

Patterns we explicitly DO NOT counterfactualise:

- **Build broken / tests red.** We have no way to know what code would
  have made them pass. Flipping `build.success = true` on the existing
  code is wishful thinking — the build failed because of *this* code.
  Skip these for now.

The result is a smaller v1 than the earlier draft: only
`TESTS_SKIPPED_AFTER_REFACTOR` produces score-moving DPs today;
`COMMIT_GAP` lands the flip + alt machinery with a zero magnitude in
preparation for commit cadence joining the process score.

## Predicates

| Predicate | Anchor step | Flip target | Counterfactual magnitude today |
|-----------|-------------|-------------|-------------------------------|
| `TESTS_SKIPPED_AFTER_REFACTOR` | refactoring step whose post-checkpoint has `tests.wasSkipped=true` | `tests.wasSkipped → false`, `tests.success → true` | Δ process score (W_SKIP_TESTS=14 lifts) |
| `COMMIT_GAP` | checkpoint where a "should have committed" predicate fires (gap > `MIN_COMMIT_GAP` checkpoints since the previous commit) | single `isUserCommit = true` flip on the anchor checkpoint only | **0 today** — cadence not in score yet; same plumbing surfaces real Δ once it is |

**One flip per alt.** Each finding mutates exactly one checkpoint: the
anchor. Long commit gaps yield multiple DPs (one per recommended commit
point), each with its own one-flip alt. Keeps each alt a clean "what
if you'd done this one thing" counterfactual rather than a batched
"what if you'd been better for 10 steps."

Skipped (no honest counterfactual): `BUILD_BROKEN`, `TESTS_RED`.

## Algorithm

### `HygieneDetector` (new) — `analysis/src/main/kotlin/com/github/ethanhosier/analysis/divergence/HygieneDetector.kt`

```
detect(report): List<HygieneFinding>
  // 1. TESTS_SKIPPED_AFTER_REFACTOR:
  //    for each refactoring step whose post-checkpoint has
  //    tests.wasSkipped = true → emit a finding pointing at that
  //    checkpoint with a single-checkpoint flip set.
  //
  // 2. COMMIT_GAP:
  //    walk checkpoints, tracking the index of the most recent user
  //    commit (or -1). When (currentIndex - lastCommitIndex) reaches
  //    MIN_COMMIT_GAP (default 6), emit ONE finding anchored at
  //    currentIndex with a single flip on that checkpoint, then reset
  //    lastCommitIndex = currentIndex (treat the synthetic commit as
  //    if it happened). Continue walking — another finding fires after
  //    another MIN_COMMIT_GAP run without a real commit. This yields
  //    one DP per "recommended commit point", each alt flipping exactly
  //    one checkpoint.
  //
  // For each finding:
  //    a. build mutated CheckpointReport list applying the flips
  //    b. re-run advanceMainStep over the mutated chain
  //    c. magnitude = mutatedProcessAtToSha - userProcessAtToSha
  //    d. drop TESTS_SKIPPED findings with magnitude < MIN_PROCESS_DELTA
  //       (3.0). COMMIT_GAP findings emit regardless of magnitude — the
  //       hygiene signal stands on its own; the magnitude will become
  //       meaningful once cadence is in the score.
```

`HygieneFinding` carries the anchor step, sub-kind, flips, and
recomputed process score — enough to build an `AlternativeTrajectory`
and its `DivergencePoint`.

### Carrying "was committed" into the metric pipeline

Today, `CheckpointReport` has `sha` but no boolean for "the user
committed at this checkpoint" — that's joined live by the dashboard
via timestamps. To make the flip a property of the data the
process-score walker sees, add:

- `CheckpointReport.isUserCommit: Boolean = false` (defaults preserve
  backward compatibility). Populate it in `AnalysisPipeline` from
  `userGitCommits` SHA-set lookup.
- A no-op consumer in `advanceMainStep` for now — read it but don't
  use it in the score formula. The future commit-cadence change will
  add the penalty term that consumes this field. Defining it now means
  HYGIENE alts can already flip it, and the diff vs. user score is
  literally 0 until the term is wired in.

### Plumbing into the pipeline — `AnalysisPipeline.kt`

After the existing alt-synthesis blocks and before `DivergencePointBuilder`:

1. Run `HygieneDetector.detect(report)` → `List<HygieneFinding>`.
2. For each finding, build an `AlternativeTrajectory`:
   - `kind = HYGIENE`
   - `fromSha = checkpoint[anchor-1].sha` (or report.startSha at step 0)
   - `userToSha = checkpoint[anchor].sha` for TESTS_SKIPPED, or
     `checkpoint[nextCommitOrEnd].sha` for COMMIT_GAP
   - `stepIndexes = listOf(anchor)`
   - `altCheckpoints = mutated checkpoints with the flips applied`
   - `specs = emptyList()` — no refactorings; this is a status flip
   - `branchRefs = emptyList()` — no shadow repo
   - `altValues` carries the recomputed process score
   - `continuationCheckpoints` — same continuation walk as other kinds
   - `altCheckpointUserIndexes` = the flipped-checkpoint indices
     (REWORK trick so the chart polyline anchors at user xPos)
3. Append all hygiene alts to `alternativeTrajectories`.

### `DivergencePointBuilder.buildHygiene()` — new method

Same shape as `buildIdeReplay` (lines 62–93):

- TESTS_SKIPPED: emit DP at anchor with `magnitude = altProcess - userProcess`,
  threshold `MIN_PROCESS_DELTA = 3.0`.
- COMMIT_GAP: emit DP at anchor with `magnitude = altProcess - userProcess`
  (literally 0 today). No threshold — these need to surface even at
  zero magnitude so the UX is ready for when cadence joins the score.
  Optional: surface the gap length somewhere on the DP for the
  template (e.g., `hygieneStretchLength: Int?`).
- `hygieneSubKind: String?` on `DivergencePoint` discriminates UI copy:
  `"TESTS_SKIPPED"` | `"COMMIT_GAP"`.

### Counterfactual recompute helper

Pure helper that reuses `advanceMainStep`:

```kotlin
data class HygieneFlip(
    val testsToPassing: Boolean = false,  // wasSkipped=false, success=true
    val markAsCommit: Boolean = false,    // isUserCommit=true
)

fun recomputeWithHygieneFlips(
    checkpoints: List<CheckpointReport>,
    flips: Map<Int, HygieneFlip>,
): List<ProcessScore> {
    val mutated = checkpoints.mapIndexed { i, cp ->
        flips[i]?.let { f ->
            cp.copy(
                metrics = cp.metrics.copy(
                    tests = if (f.testsToPassing)
                        cp.metrics.tests.copy(success = true, wasSkipped = false)
                    else cp.metrics.tests,
                ),
                isUserCommit = if (f.markAsCommit) true else cp.isUserCommit,
            )
        } ?: cp
    }
    // run advanceMainStep across mutated chain → fresh ProcessScore list
}
```

Pure — no I/O, no shadow repo, no metric re-extraction.

## Files

**New:**
- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/divergence/HygieneDetector.kt`
- `analysis/src/test/kotlin/com/github/ethanhosier/analysis/divergence/HygieneDetectorTest.kt`
  - TESTS_SKIPPED trigger + no-trigger
  - COMMIT_GAP trigger + no-trigger
  - assert: anchor index, flip set, magnitude sign (positive for SKIPPED,
    zero for COMMIT_GAP today)

**Modified:**
- `analysis/.../metrics/model/AnalysisReport.kt`:
  - `CheckpointReport` gains `isUserCommit: Boolean = false`
  - `DivergencePoint` gains `hygieneSubKind: String? = null` and
    optional `hygieneStretchLength: Int? = null`
- `analysis/.../pipeline/AnalysisPipeline.kt`:
  - Populate `isUserCommit` from `userGitCommits` SHA set when building
    `CheckpointReport`s
  - Call `HygieneDetector`, build hygiene alts, append to alt list
- `analysis/.../divergence/DivergencePointBuilder.kt` — add
  `buildHygiene()`, call it alongside the existing three builders
- `analysis/.../metrics/derived/DerivedMetricsRunner.kt`:
  - Read `isUserCommit` in `advanceMainStep` (read-only for v1; sets up
    future cadence-penalty term without changing today's scores)
  - Expose `recomputeWithHygieneFlips` if the existing pure walker isn't
    callable as-is from outside
- `dashboard/src/data/types.ts` — `DivergencePointVM` gains
  `hygieneSubKind?: string` and `hygieneStretchLength?: number`
- `dashboard/src/data/view-model.ts` — passthrough
- `dashboard/src/features/detail-panel/alternative-body.tsx` — HYGIENE
  kind-aware copy, branching on `hygieneSubKind` (TESTS_SKIPPED vs
  COMMIT_GAP)

## Why infrastructure-now for COMMIT_GAP

Two reasons to ship the flip + alt + DP plumbing even though the
magnitude is 0 today:

1. **No future migration cost.** When commit cadence is added to the
   process score, the existing COMMIT_GAP alts immediately produce real
   Δs; no second pass on the schema, the detector, or the UI.
2. **The flip lives in the data.** `CheckpointReport.isUserCommit` is
   a property the metric walker can read — same channel as
   `build.success`, `tests.success`. The dashboard's commit overlay
   stays as-is (display-only), but the *score-affecting* commit
   boolean lives where it'll be consumed.

The DP still surfaces today as a non-quantified hygiene flag — the
explanation template can say "You went N checkpoints between commits.
Frequent commits give you cheap restore points." without claiming a
process-score gain.

## "Anything else to consider?" — direct answers

- **You can't honestly counterfactualise build/test failures.** Same
  code → same failure. Skip those for now.
- **You CAN counterfactualise skipped tests and missed commits.** Code
  is identical; only the user's process decision differs. These two
  flips are honest.
- **Re-use `advanceMainStep`, don't reinvent.** The function is pure
  and already powers alt scoring; counterfactual = clone the checkpoint
  list, apply flips, walk again.
- **Anchor at the predicate-fire step.** For TESTS_SKIPPED, that's the
  refactoring step. For COMMIT_GAP, anchor at the checkpoint where the
  gap predicate first trips (e.g., `MIN_COMMIT_GAP` checkpoints past
  the last commit).
- **One flip per alt.** Each alt mutates exactly one checkpoint. Long
  commit gaps produce multiple DPs (one per recommended commit point),
  not one giant DP with a batched flip.
- **Chart anchoring.** Use `altCheckpointUserIndexes` (REWORK trick) so
  the hygiene alt's polyline anchors at the user-checkpoint xPos.
- **No shadow repo, no cherry-pick.** Hygiene alts are status-flip-only.

## Verification

1. **Unit tests in `HygieneDetectorTest.kt`** — per-sub-kind trigger
   and no-trigger cases.
   - TESTS_SKIPPED: fabricate a 3-checkpoint trajectory whose middle
     checkpoint has `tests.wasSkipped=true` post-refactor → expect one
     finding with positive magnitude.
   - COMMIT_GAP: fabricate a 10-checkpoint trajectory with no commits →
     expect one finding anchored ≥ `MIN_COMMIT_GAP`, magnitude = 0.
2. **Integration on a known session.** Pick a session with a known
   tests-skipped post-refactor. Regenerate the report; confirm a
   TESTS_SKIPPED DP appears, alt's terminal process score exceeds the
   user's, clicking opens the alt panel.
3. **Commit-gap smoke.** Same session: confirm COMMIT_GAP DP appears
   on long stretches with `magnitude == 0` and the sidebar copy renders
   without claiming a score gain.
4. **Chart rendering smoke.** HYGIENE alt polyline anchors at
   `altCheckpointUserIndexes`, not stacked at toSha.
5. **Typecheck:** `cd dashboard && npm run typecheck` after schema regen.
6. **Backend tests:** `./gradlew :analysis:test`.

## Out of scope

- Adding commit cadence as a process-score term. Separate change that
  shifts existing scores. Once it lands, COMMIT_GAP magnitudes light
  up without further work.
- BUILD_BROKEN / TESTS_RED counterfactuals. No honest simulation of
  "what code would have made it pass."
- Multi-gap collapsing (two short commit gaps close together → one DP).
  V1 emits one DP per gap.
- LLM-generated hygiene explanations. Templates only.
- Hygiene predicates that overlap existing kinds (manual-when-IDE,
  smell accumulation).
