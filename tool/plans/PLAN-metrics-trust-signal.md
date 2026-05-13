# Trust signal for broken-state checkpoints

## Context

When a user's working tree has syntax errors (or otherwise won't fully
parse), CK / PMD / CPD all produce sparse output for that checkpoint:

- `CkResult.perClass` is empty for files the parser couldn't read →
  P90 CBO collapses to 0 (`DerivedMetricsRunner.percentile(empty,…)`
  fallback at line 1013–1014).
- `PmdResult.violations` is sparse — PMD records the parse failure in
  `processingErrors` and emits no rule hits on the unreadable file →
  `pmd.violations.size` (`smells` aggregate) artificially drops, plus
  every previous violation looks "resolved" to `PmdViolationTracker`.
- `pmd.methodMetrics` is sparse → `cognitive` aggregate collapses to 0.
- `CpdResult` defaults to `EMPTY` (0% duplication) when files can't be
  tokenised.
- `ReadabilityResult.summary` defaults to `EMPTY` → degenerate 0.6
  readability.

`DerivedMetricsRunner.aggregate` takes those raw signals at face value
and rolls them into the six-signal cleanliness tuple. Downstream that
distorts three terms of the process score:

1. **`computeRanges`** (line 324) uses every checkpoint's aggregate to
   build min/max ranges for normalisation. A broken checkpoint's
   artificial zeros depress the `lo` floor → every other checkpoint
   normalises higher than it should.
2. **Cleanliness gain** (`W_GAIN = +50`, line 1036, `cleanT - cleanliness0`):
   a broken checkpoint pushes `cleanT` low; the next trustworthy
   checkpoint's recovery is then credited as a big improvement that
   didn't actually happen.
3. **Degradation dip** (`W_DEGRADATION = -21`, lines 540–543): broken
   checkpoint's low `cleanT` resets the peak floor; subsequent dips
   are measured against the depressed peak.
4. **Smell ledger** (`W_SMELL = -21`, lines 510–520): every prior
   violation looks "resolved" since `pmd.violations` is sparse. Smell
   load artificially drops.

The W_BROKEN penalty (line 1037) still fires correctly from
`build.success` — that part isn't affected. What's broken is the
*metric-derived* terms.

Net effect today: a user who breaks their build during a refactor and
then fixes it gets credited a fake "cleanliness gain" on the recovery
checkpoint. The process score can rise across a broken→fixed pair when
it should be flat or down.

There's also a *gaming* angle that argues for tightening the criterion
beyond "did it parse?": code can compile cleanly while tests fail,
which lets a user claim cleanliness wins they didn't earn (refactor
aggressively, break tests, score goes up, fix tests at the end). The
honest signal is "the code was demonstrably working" — i.e. both
build AND tests passed.

No existing logic detects "this checkpoint's metrics are untrustworthy"
— all runners run unconditionally per `MetricsRunner.computeOne`
(lines 172–198); only the test runner short-circuits on `!build.success`
(in which case `TestResult.skipped(...)` writes `success = false`).

## Approach

**Carry-forward at the aggregate layer.** Introduce a per-checkpoint
*trust signal* derived from existing parse-error fields. For
untrustworthy checkpoints:

- The six-signal `Aggregates` tuple inherits the previous trustworthy
  checkpoint's tuple (or zero if none exists yet).
- The checkpoint is skipped when computing ranges.
- The smell delta `(added, resolved)` returns `(0, 0)` — the broken
  state is a no-op for the smell ledger.

The W_BROKEN term keeps firing from `build.success`, so the user
*still gets penalised* for breaking their build. What goes away is the
fake metric-side movement before / during / after the broken stretch.

### Trust signal

A pure helper inside `DerivedMetricsRunner`:

```kotlin
private fun isTrustworthy(cp: CheckpointReport): Boolean =
    cp.metrics.build.success && cp.metrics.tests.success
```

Why `build.success && tests.success`:
- It's exactly the "code was demonstrably working" criterion: build
  green AND tests green at this checkpoint.
- `tests.success` is false for *every* non-pass case via the existing
  data model — `TestResult.skipped(...)` writes `success = false` (the
  pipeline skips tests only when build failed, so this folds in the
  build-broken-so-tests-skipped case automatically).
- It mirrors the existing W_BROKEN penalty's `!build || testsFailed`
  shape, so trust and W_BROKEN agree about which checkpoints are
  "untrusted / penalised" vs "trusted / credited".
- It blocks the cleanliness gaming angle: a user can't refactor with
  tests broken, claim a cleanliness win, then fix tests at the end —
  the broken-tests stretch carries the previous trustworthy aggregate.

### Aggregator carry-forward (two-pass)

A single forward fold can't handle "untrustworthy from the start of
the session" — there's no prior trustworthy aggregate to carry from,
and we don't yet know the session ranges. So the runner does two
passes:

**Pass 1 — forward carry-forward, marking the head gap.** Walk
checkpoints; for trustworthy ones use `aggregate(cp)` and remember it
as `lastGood`; for untrustworthy ones, if `lastGood` exists carry it,
otherwise mark the slot with a sentinel `null` so pass 2 can fill it.

```kotlin
val rawAgg = mainCheckpoints.map { aggregate(it) }       // for ranges
val effectiveAgg = MutableList<Aggregates?>(mainCheckpoints.size) { null }
var lastGood: Aggregates? = null
for (i in mainCheckpoints.indices) {
    if (isTrustworthy(mainCheckpoints[i])) {
        effectiveAgg[i] = rawAgg[i]
        lastGood = rawAgg[i]
    } else if (lastGood != null) {
        effectiveAgg[i] = lastGood
    } // else: leave null — fill in pass 2
}
```

**Pass 2 — fill leading untrustworthy gap with session midpoint.**
Compute `ranges` from trustworthy checkpoints' raw aggregates. For
each sub-signal, the midpoint is `(lo + hi) / 2`. Build a midpoint
`Aggregates` from those. Fill every `null` slot with it.

```kotlin
val ranges = computeRanges(
    mainCheckpoints.zip(rawAgg)
        .filter { (cp, _) -> isTrustworthy(cp) }
        .map { (_, agg) -> agg }
)
val midpoint = midpointAggregates(ranges)  // (lo+hi)/2 per sub-signal
for (i in effectiveAgg.indices) {
    if (effectiveAgg[i] == null) effectiveAgg[i] = midpoint
}
val mainAgg = effectiveAgg.map { it!! }
```

Degenerate cases:
- **Every checkpoint untrustworthy** ⇒ `ranges` from empty input is
  degenerate (`lo == hi`). Midpoint exists but cleanliness collapses
  to a constant. Fine — there's no signal to display either way; the
  existing `computeCleanliness` handles flat ranges via its
  `clamp = false` semantics (returns null on degenerate).
- **Only one trustworthy checkpoint** ⇒ `lo == hi == that value`,
  midpoint == that value. Effectively the same as carry-forward.

### Range exclusion

Already covered in pass 1 above — `ranges` are computed from the
`(cp, rawAgg)` pairs filtered to `isTrustworthy(cp)`. Untrustworthy
raw aggregates never enter the range computation, so they can't pull
the `lo` floor down.

### Smell delta carry-forward

`smellsForCheckpoint(checkpoints, cpIndex)` returns `(added, resolved)`
read from `cp.pmdTracking`. Wrap it:

```kotlin
val (added, resolved) =
    if (isTrustworthy(checkpoints[cpIndex])) smellsForCheckpoint(checkpoints, cpIndex)
    else 0 to 0
```

Untrustworthy ⇒ zero added, zero resolved. The smell ledger holds
station during the broken stretch.

### Alt-side symmetry

The same carry-forward applies to alt trajectories (`altAgg`,
`altCleanliness`). Alts can have sparse PMD/CK too if the synthesised
tree won't parse. Mirror the two-pass on alts — `lastGood` is per-alt,
doesn't cross alts. Pass 2 midpoint uses the alt's own range (alt
checkpoints already get their own range clamping in today's code).

### Schema + UI signal

To surface the carry-forward in the sidebar, add a per-checkpoint
flag:

```kotlin
// CheckpointReport
val metricsTrustworthy: Boolean = true,
```

Set by `DerivedMetricsRunner` *after* it has run pass 1 / pass 2:

- `true` when the checkpoint's static cleanliness aggregates came
  from running CK/PMD/CPD/readability against this checkpoint's own
  worktree (i.e. `isTrustworthy(cp)` held).
- `false` when build or tests were broken and aggregates were either
  carried forward or filled with the session midpoint.

The pipeline copies this flag onto each `CheckpointReport.copy(...)`
call alongside the derivedMetrics overlay (same place where derived
metrics are spliced in — `AnalysisPipeline.kt` around line 660).

**Frontend**: `dashboard/src/data/types.ts` `CheckpointVM` gains
`metricsTrustworthy: boolean`. The view-model projection in
`view-model.ts` reads it via `report.checkpoints[i].metricsTrustworthy
?? true`.

In `dashboard/src/features/detail-panel/checkpoint-body.tsx`, each
metric tile rendered for an *untrustworthy* checkpoint gets a small
tag (icon or short pill) next to the value. Tooltip / hover copy:

> Metrics since last green build + tests pass. The build or tests
> were broken at this checkpoint, so cleanliness signals can't be
> read directly — the displayed values are carried forward from when
> the trajectory was last fully green.

For the leading-gap edge case (no prior trustworthy at all), the
tooltip variant reads:

> Build / tests were broken from the start of this session — using
> the session midpoint as a placeholder for cleanliness signals.

Distinguishing those two variants needs one bit of extra context.
Simplest implementation: also surface `metricsCarryForwardSource:
"prior" | "midpoint" | null` on the checkpoint (null when
trustworthy). The dashboard picks the copy off this enum.

Only cleanliness-related tiles get the tag (coupling, cohesion,
duplication, readability, cognitive, smells, and the composite
`cleanliness`). Build / tests / process tiles aren't carried — they
keep showing the actual measurement.

## Files

**Modified:**

- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/metrics/model/AnalysisReport.kt`:
  - `CheckpointReport` gains `metricsTrustworthy: Boolean = true` and
    `metricsCarryForwardSource: String? = null` (values: `"PRIOR"`,
    `"MIDPOINT"`, or null when trustworthy).

- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/metrics/derived/DerivedMetricsRunner.kt`
  - Add private `isTrustworthy(CheckpointReport): Boolean` helper.
  - Replace the `mainAgg = mainCheckpoints.map { aggregate(it) }`
    (line 153) with the two-pass fold described above; same shape per
    alt at line 156.
  - Range computation filters to trustworthy checkpoints (folded into
    pass 1).
  - In `advanceMainStep` (line 510) and `advanceAltStep` (line 745):
    skip the smell-ledger update when the checkpoint is untrustworthy.
  - Expose the trust flag + carry-forward source per main checkpoint
    on `Result` so the pipeline can splice them onto `CheckpointReport`
    alongside `derivedMetrics`.

- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/pipeline/AnalysisPipeline.kt`:
  - When splicing `derivedMetrics` onto each `CheckpointReport.copy(...)`
    (around line 660), also splice `metricsTrustworthy` and
    `metricsCarryForwardSource`.

- `analysis/src/test/kotlin/com/github/ethanhosier/analysis/metrics/derived/DerivedMetricsRunnerTest.kt`
  - Trustworthy middle ⇒ unaffected (today's behaviour).
  - Build-failed middle ⇒ carry-forward; cleanliness equal to
    previous trustworthy; smell ledger flat across the broken step;
    W_BROKEN still fires.
  - Tests-failed middle (build OK) ⇒ same as above (catches the
    gaming case).
  - **Leading-gap**: first two checkpoints have `build.success=false`,
    later checkpoints trustworthy. Assert the first two get the
    session midpoint aggregate; `metricsCarryForwardSource == "MIDPOINT"`.

- `dashboard/src/data/types.ts`:
  - `CheckpointVM` gains `metricsTrustworthy: boolean` and
    `metricsCarryForwardSource?: "PRIOR" | "MIDPOINT"`.

- `dashboard/src/data/view-model.ts`: passthrough projection.

- `dashboard/src/features/detail-panel/checkpoint-body.tsx`:
  - For each cleanliness-related metric tile, when the selected
    checkpoint is `!metricsTrustworthy`, render a small tag/pill
    next to the value with hover copy explaining the source
    (`PRIOR` ⇒ "Carried forward from last green checkpoint";
    `MIDPOINT` ⇒ "Session midpoint placeholder — build/tests broken
    from the start").

## Verification

1. **Unit test in `DerivedMetricsRunnerTest.kt`**: 3-checkpoint trajectory
   where the middle has `build.success = false` (so `tests.success = false`
   too) along with realistic sparse CK/PMD output. Assert:
   - `derived.main[middle].cleanliness == derived.main[first].cleanliness`
     (carry-forward held cleanliness flat).
   - `derived.main[third].process.contributions` shows zero
     `cleanlinessGain` between first→middle and middle→third (gain
     measured against trustworthy baseline).
   - W_BROKEN still fires for the middle: `broken` contribution
     unchanged from today.
2. **Second unit test**: middle has `build.success = true` but
   `tests.success = false` (tests ran and failed). Same assertions —
   carry-forward triggers off the test-failure path, blocking the
   gaming scenario.
3. **Integration on a real session** with a known broken stretch:
   regenerate `analysis-report.json`, confirm cleanliness line stays
   flat through the broken stretch instead of spiking on recovery.
4. **Backend tests:** `./gradlew :analysis:test` — full suite green.
5. **Dashboard typecheck:** `cd dashboard && npm run typecheck`.
   No frontend changes expected since the carry-forward is invisible
   to consumers; cleanliness just looks flatter where build or tests
   were broken.

## Why this approach over alternatives

- **Carry forward at aggregate layer** keeps the change surgical: the
  fix lives entirely inside `DerivedMetricsRunner` and the raw runner
  outputs are preserved on the report (still useful for diagnostics).
- **Range exclusion** is the smallest fix for the normalisation
  distortion — better than capping with sentinel values, which would
  need per-metric tuning.
- **Smell-delta zeroing** is the only way to avoid the "all prev
  violations look resolved" artifact without re-architecting
  `PmdViolationTracker`.
- Symmetry between main and alt code paths means no second pass on
  alts later.

## Out of scope

- Using parse-error fields (`ck.parseErrors`, `pmd.processingErrors`)
  as an *additional* trust signal. Build-failure already covers nearly
  every parse-failure case (syntax errors → javac fails → build fails);
  the rare divergent case isn't worth a second signal in v1.
- Reworking `PmdViolationTracker` to special-case sparse-violations
  inputs. Smell-delta zeroing at the consumer is sufficient.
- Cross-trajectory carry-forward (alts inheriting main's last
  trustworthy). Alts are self-contained for v1.
- Chart-side glyph for untrustworthy checkpoints. The build/tests
  rail already signals breakage; the sidebar metric tag is enough.
