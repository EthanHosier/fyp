# Process score — single 0..100 trajectory metric

## Context

The dashboard currently shows 6 code-quality metrics (`cohesion`, `coupling`, `smells`, `duplication`, `readability`, `cognitive`) plus per-checkpoint build/test status, refactoring steps, and code-smell buckets. Users have to read all of these together to judge whether a refactoring session went well.

This plan adds a single **process score** ∈ `[0, 100]` per checkpoint that summarises "is this refactoring trajectory producing cleaner code safely and efficiently, up to here?" — prefix-cumulative so it tells a story across the trajectory, decomposable so the user can see *why* the score moved.

The score is computed entirely dashboard-side in `view-model.ts` from data already on the VM. No analysis-side changes.

## Design decisions (these go in the file's top-of-file comment)

1. **Prefix-cumulative, not point-in-time.** The score at checkpoint `t` is "process quality from `c0` through `c_t`", not "code quality at `c_t`". This is what lets it answer "did the user refactor *well*", not just "is the code good now".

2. **Anchor at 50, not 100.** A trajectory that doesn't improve cleanliness but doesn't damage anything sits mid-range. Pure improvement and pure damage are both visible; a flat trajectory isn't misread as "perfect".

3. **Frame as process quality, not absolute code quality.** The thesis claim is "this score compares trajectories", not "this project is 73/100". The number is meaningful relatively (between checkpoints, between sessions, between user-vs-IDE alternative paths) — not absolutely.

4. **Bounded (rate-based) penalties, with Laplace smoothing.** All penalties are fractions in `[0, 1]` (e.g. `broken / (t+1)`), so weights map directly to point costs ("worst-case manual-IDE costs 8 points"). Rates rather than counters because the user's "don't penalise thinking" rule means long thoughtful sessions shouldn't accumulate penalties just for being long. Laplace smoothing `(bad + 1) / (total + 2)` on the refactoring-step rates avoids the cliff where one bad step in a one-step session pins the rate at 100%.

5. **No time term.** Explicit per user constraint: thinking is not a process failure. The "stayed broken" intuition is captured by *number of broken checkpoints*, not duration.

6. **Net smells, no drag for carried.** A `carried` smell is already on the ledger from when it was `added`; adding a separate carry penalty double-counts. The cumulative net formula handles this correctly.

7. **Cleanliness composite uses literature-informed weights, not equal mean.** No paper covers this exact bundle, but the closest references converge on a hierarchy:

   | Metric | Weight | Justification |
   |---|---|---|
   | Cognitive complexity | 0.25 | Campbell 2018 (Sonar): strongest empirical correlate with comprehension time; supersedes cyclomatic for human effort |
   | Coupling | 0.20 | Chidamber & Kemerer 1994 (CBO/RFC): coupling repeatedly the strongest defect predictor in OO studies |
   | Duplication | 0.20 | Heitlager et al. 2007 (SIG maintainability model): one of four equal pillars |
   | Readability | 0.15 | Buse & Weimer 2010: validated readability score, but smaller effect than structural metrics |
   | Smells | 0.15 | Priority-weighted PMD; literature treats smells as a *symptom* of the above, so weighting it as primary risks double-counting |
   | Cohesion | 0.05 | LCOM family is noisy and contested (Etzkorn, Counsell); kept low |

   Weights sum to 1.0. When a metric is missing at a checkpoint, its weight is redistributed proportionally over the remaining present metrics so the composite stays on `[0, 1]`.

8. **Min-max normalisation across the trajectory's observed range.** Self-tunes per project: a 5-WMC complexity bump on a project ranging 0..50 is significant; on a project ranging 0..200 it isn't. Mirrors the existing spike-detector convention in `checkpoint-body.tsx:213`. Metrics absent at a checkpoint are skipped (composite averages whatever's present).

9. **No churn term.** The user excluded it; bigger refactorings shouldn't be inherently worse than smaller ones.

## Formula

```
score(t) = clamp(
  50
  + W_gain      · CleanlinessGain(t)
  − W_broken    · brokenFraction(0..t)
  − W_smell     · netSmellFraction(0..t)
  − W_skipTests · testsSkippedFraction(0..t)
  − W_manualIde · manualWhenIdePossibleFraction(0..t),
  0, 100
)
```

V0 weights:

| Term | Weight |
|---|---|
| `W_gain` | 35 |
| `W_broken` | 20 |
| `W_smell` | 15 |
| `W_skipTests` | 10 |
| `W_manualIde` | 8 |

### Cleanliness composite

```
W = { cognitive: 0.25, coupling: 0.20, duplication: 0.20,
      readability: 0.15, smells: 0.15, cohesion: 0.05 }

normalised(c, m):
  let raw       = c.values[m]                         // skip if undefined
  let [lo, hi]  = min/max of m across all checkpoints
  if hi == lo:  skip                                   // degenerate range, no signal
  let n         = (raw - lo) / (hi - lo)               // 0..1
  if m.better == "lower": n = 1 - n                    // flip so 1 = best everywhere
  return n

Cleanliness(t):
  let present = metrics m where normalised(c_t, m) is defined
  let totalW  = Σ W[m] for m in present                // re-base when some absent
  return (Σ W[m] · normalised(c_t, m)) / totalW        // ∈ [0, 1]

CleanlinessGain(t) = Cleanliness(t) − Cleanliness(0)   // ∈ [-1, +1]
```

Re-basing the weights over only the present metrics keeps the composite on `[0, 1]` if some metric is missing at a checkpoint (e.g. CPD couldn't run). A metric with degenerate `hi == lo` across the whole trajectory is treated as absent — same re-basing applies.

### Process penalty fractions

All ∈ `[0, 1]`. Refactoring-step rates use Laplace smoothing.

```
brokenFraction(t)
  = count of c_i (i ≤ t) where build=="fail" || tests=="fail"
  / (t + 1)

netSmellFraction(t)
  = max(0, addedWeight(0..t) − resolvedWeight(0..t))
  / max(1, totalWeightEverSeen(0..t))
where weight(smell) = 6 − smell.priority    // priority 1 (worst) → weight 5

testsSkippedFraction(t)
  = (skipped + 1) / (totalRefactoringSteps + 2)
where skipped = steps s with checkpointIndex ≤ t and !s.userRanTests
      totalRefactoringSteps = steps s with checkpointIndex ≤ t

manualWhenIdePossibleFraction(t)
  = (manual + 1) / (ideRelevantTotal + 2)
where manual = steps s with checkpointIndex ≤ t, s.ideRelevant, !s.wasPerformedByIde
      ideRelevantTotal = steps s with checkpointIndex ≤ t and s.ideRelevant
```

When the relevant denominator is zero (no refactoring steps yet, no smells yet), the penalty is `0`, not the smoothed mid-value — refactor-related and smell-related penalties only kick in once there's evidence to evaluate.

## Decomposition

Each `CheckpointVM` carries a `processScore` and a `processBreakdown` so the detail panel can show the reasoning:

```ts
type ProcessScoreBreakdown = {
  total: number              // 0..100
  baseline: number           // 50
  contributions: Array<{
    id: "cleanliness" | "broken" | "smells" | "skipTests" | "manualIde"
    label: string            // "Cleanliness gain"
    points: number           // signed; sums (with baseline) to total before clamping
    detail: string           // e.g. "5 broken / 12 checkpoints (42%)"
  }>
  /** True if the unclamped sum was outside [0,100] — surfaced subtly so the
   *  panel can show "≤0" or "≥100" rather than implying precision. */
  clamped: boolean
}
```

Detail panel renders this as a contributions table mirroring the existing `MetricTile` grid in `checkpoint-body.tsx:51`.

## UI

- Process score is the **default primary** on the chart (replaces whatever's currently default).
- It's a 7th `MetricId` (`"process"`) so the existing primary-toggle / overlay / legend / tooltip plumbing handles it for free.
- `group: "process"` distinguishes it from the 6 code metrics; descriptor declares `better: "higher"`, unit `""` (just a number), tone reserved (e.g. a non-`brand-N` tone or a new dedicated one).
- New section in `checkpoint-body.tsx`: "Process score" with the breakdown table, rendered when the selected checkpoint has the score (i.e. always except possibly degenerate cases).

## Critical files

**New:**
- `tool/dashboard/src/data/process-score.ts` — pure functions: `computeProcessScores(vm) → { score, breakdown }[]` over all checkpoints. Top-of-file comment captures the design decisions above. All knobs (weights, smoothing) defined as named constants at the top so they're easy to tune.

**Modified:**
- `tool/dashboard/src/data/types.ts` — add `"process"` to `MetricId`; add `processScore: number` and `processBreakdown: ProcessScoreBreakdown` to `CheckpointVM`; export the breakdown type.
- `tool/dashboard/src/data/view-model.ts` — call `computeProcessScores` after the existing checkpoint assembly; thread results onto each `CheckpointVM`. Inject `process` into the `metrics` array so the chart picks it up.
- `tool/dashboard/src/data/metric-descriptors.ts` — register the `process` descriptor (label `"Process score"`, unit `""`, `better: "higher"`, group `"process"`, tone).
- `tool/dashboard/src/features/detail-panel/checkpoint-body.tsx` — new "Process score" section showing the breakdown rows for the selected checkpoint. Sits above or alongside the existing metric tiles; final placement decided when implementing.
- Wherever the default primary metric is decided (likely `view-model.ts` or a small selector) — set default to `"process"`.

**Reused (no change):**
- All chart/legend/overlay/tooltip code — the new metric flows through existing `MetricVM` paths.

## Verification

1. **Unit — composite normalisation**: synthetic VM with known per-metric ranges and `better` directions; assert `Cleanliness(0) ≈ 0..1`, `Cleanliness(t)` for the best/worst observation lands at 1.0 / 0.0 respectively.
2. **Unit — degenerate range**: a metric with constant value across the trajectory contributes nothing; composite is the mean of the remaining metrics.
3. **Unit — penalty fractions**: hand-built fixtures hitting each of broken / smells / skip-tests / manual-IDE in isolation; assert the breakdown's `points` lines up with `weight × fraction`.
4. **Unit — Laplace smoothing**: a single-step session with one manual-IDE refactor produces fraction ≤ 0.5, not 1.0.
5. **Unit — clamping**: synthetic worst-case trajectory drives unclamped sum below 0; assert `total === 0` and `clamped === true`.
6. **Unit — score(0) ≈ 50**: at the seed checkpoint there's no gain yet and no penalty signals; the breakdown's only non-zero entry is the baseline.
7. **Visual smoke**: load an existing session report, confirm the new line renders as primary, the breakdown panel populates on selection, contributions sum to total (modulo clamping), and tooltip shows the score value.
8. **`npm run typecheck` + `npm run lint` green.**

## Sequencing

1. Land `process-score.ts` with full unit tests (no UI yet) — the formula is the load-bearing piece, isolate it.
2. Wire types + view-model integration so each `CheckpointVM` carries the score and breakdown.
3. Register the metric descriptor so the existing chart picks it up; verify the line renders.
4. Make it the default primary.
5. Add the breakdown section to the detail panel.
6. Tune weights / verify visually against a couple of real sessions; document any adjustments in the top-of-file comment.

## Open follow-ups (not V0)

- Per-metric weighting in the cleanliness composite once we have evidence one factor dominates in practice. The structure already supports this (named weight constants, single mean → weighted-mean change).
- A second "alternative-path process score" computed for each `AlternativeTrajectoryVM` so the user can compare their actual score against what the IDE-driven path would have scored. Same formula, different input series.
- Smell *severity* in the chart's existing smell line vs the process score's priority-weighted treatment — currently they disagree on what "smells" means. Worth reconciling once the score is in.
