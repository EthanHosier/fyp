# Process score — single 0..100 trajectory metric

> **Status:** Shipped on `feat/process-score`. This document reflects what's in the code; the open-follow-ups section at the bottom is still open.

## Context

The dashboard currently shows 6 code-quality metrics (`cohesion`, `coupling`, `smells`, `duplication`, `readability`, `cognitive`) plus per-checkpoint build/test status, refactoring steps, and code-smell buckets. Users have to read all of these together to judge whether a refactoring session went well.

This adds a single **process score** ∈ `[0, 100]` per checkpoint that summarises "is this refactoring trajectory producing cleaner code safely and efficiently, up to here?" — prefix-cumulative so it tells a story across the trajectory, decomposable so the user can see *why* the score moved.

It also surfaces **code cleanliness** as its own first-class metric (the literature-weighted composite of the 6 code metrics) — not just an internal input to the process score.

The score is computed entirely dashboard-side from data already on the VM. No analysis-side changes.

## Design decisions

1. **Prefix-cumulative, not point-in-time.** Score(t) judges c0..c_t as a trajectory, not c_t in isolation. That's what makes it a process score rather than a quality score.

2. **Anchor at 50.** A trajectory that doesn't improve and doesn't damage anything sits mid-range; pure gain and pure damage are both visible. With the rescaled weights (see decision 9), perfect behaviour reaches 100 and worst-case clamps to 0.

3. **Frame as process quality, not absolute code quality.** Meaningful between checkpoints / sessions / user-vs-IDE alts — not as an absolute "73/100".

4. **Bounded (rate-based) penalties with asymmetric Laplace smoothing.** All penalties are fractions in [0, 1] so weights map directly to point costs ("worst-case manual-IDE costs 11 points"). Rates rather than counters because long thoughtful sessions shouldn't accumulate penalties just for being long. Laplace `(bad+1)/(total+2)` on the refactoring-step rates avoids the cliff where 1/1 pins to 100%, but is *only* applied when at least one bad step has occurred — a perfect record (0 bad of N) returns a 0 penalty so doing the right thing every time isn't punished.

5. **No time term.** Per user constraint: thinking is not a process failure. The "stayed broken" intuition is captured by the count of broken checkpoints, not their duration.

6. **Net smells, no separate carry penalty.** A `carried` smell is already on the cumulative ledger from when it was `added`; adding a carry drag would double-count.

7. **Intermediate degradation — running-peak dip integral.** Cleanliness gain is point-in-time so a trajectory that dips and recovers to baseline used to score identically to one that stayed clean — the score forgot the dip. The intermediate-degradation term remembers it: track the running max of cleanliness, sum `max(0, peak − clean(i))` across the prefix, and normalise by checkpoint count to keep the term in [0, 1]. Penalises both the depth of the dip and how long the trajectory stayed below its prior best. Picked running-peak over endpoint-floor because the latter lets a clever path hide a dip by ending where it started; running-peak captures "you got the code clean once, you don't get to forget that you broke it."

8. **No churn term.** Bigger refactorings shouldn't be inherently worse than smaller ones.

9. **Weights rescaled so perfect = 100.** Original V0 weights (35 / 20 / 15 / 10 / 8) gave a soft ceiling of 85. Rescaled by 50/35 ≈ 1.43 and rounded so a flawless trajectory with maximum cleanliness gain reaches exactly 100 (`BASELINE + W_GAIN`). Internal proportions follow the original design — relative ranking unchanged, scale just spans the full 0..100 range now.

10. **Cleanliness composite uses literature-informed weights, not equal mean.** No paper covers this exact bundle, but the closest references converge on a hierarchy:

    | Metric | Weight | Justification |
    |---|---|---|
    | Cognitive complexity | 0.25 | Campbell 2018 (Sonar): strongest empirical correlate with comprehension time |
    | Coupling | 0.20 | Chidamber & Kemerer 1994: strongest defect predictor in OO studies |
    | Duplication | 0.20 | Heitlager et al. 2007 (SIG): one of four equal pillars |
    | Readability | 0.15 | Buse & Weimer 2010: validated readability metric, smaller effect than structural ones |
    | Smells | 0.15 | Symptom of the above; weighting it as primary would risk double-counting |
    | Cohesion | 0.05 | LCOM family is noisy and contested (Etzkorn, Counsell) |

    Weights sum to 1.0. When a metric is missing at a checkpoint (or has degenerate range across the trajectory) its weight is redistributed proportionally over the rest, so the composite stays on [0, 1].

11. **Min-max normalisation across the trajectory's observed range.** Self-tunes per project: a 5-WMC bump on a 0..50 project is significant; on 0..200 it isn't. Mirrors the existing spike-detector convention. The cleanliness metric's descriptor calls out the relativity explicitly — 100 means "the cleanest checkpoint in this session", not absolute code quality.

## Formula

```
score(t) = clamp(
  50
  + 50 · CleanlinessGain(t)
  − 28 · brokenFraction(0..t)
  − 21 · netSmellFraction(0..t)
  − 21 · intermediateDegradation(0..t)
  − 14 · testsSkippedFraction(0..t)
  − 11 · manualWhenIdePossibleFraction(0..t),
  0, 100
)
```

Round at compute time so all consumers (chart hover, tile, breakdown card) display an integer.

### Cleanliness composite

Lives in `cleanliness.ts`. Process score consumes the 0..1 scalar via `computeCleanlinessSeries`; the cleanliness metric surfaces the rounded 0..100 score and the per-sub-metric breakdown.

```
W = { cognitive: 0.25, coupling: 0.20, duplication: 0.20,
      readability: 0.15, smells: 0.15, cohesion: 0.05 }

normalised(c, m):
  let raw       = c.values[m]                         // skip if undefined
  let [lo, hi]  = min/max of m across all checkpoints
  if hi == lo:  skip                                   // degenerate range
  let n         = (raw - lo) / (hi - lo)               // 0..1
  if m.better == "lower": n = 1 - n
  return n

Cleanliness(t):
  let present = metrics m where normalised(c_t, m) is defined
  let totalW  = Σ W[m] for m in present
  return (Σ W[m] · normalised(c_t, m)) / totalW        // ∈ [0, 1]

CleanlinessGain(t) = Cleanliness(t) − Cleanliness(0)   // ∈ [-1, +1]
```

The breakdown carries a `rebased` flag set when any literature-weighted metric was excluded (missing or degenerate range), so the UI can flag that the score isn't a full six-axis sweep.

### Process penalty fractions

All ∈ [0, 1].

```
brokenFraction(t)
  = count of c_i (i ≤ t) where build=="fail" || tests=="fail"
  / (t + 1)

netSmellFraction(t)
  = max(0, addedWeight(0..t) − resolvedWeight(0..t))
  / max(1, totalWeightEverSeen(0..t))
where weight(smell) = 6 − smell.priority

intermediateDegradation(t)
  = (Σ_{i ≤ t} max(0, peak(i) − clean(i))) / observations
where peak(i) = running max of cleanliness up to i

testsSkippedFraction(t)
  = 0                                       if skipped == 0
    (skipped + 1) / (refactoringSteps + 2)  otherwise
where skipped = steps with checkpointIndex ≤ t and !s.userRanTests

manualWhenIdePossibleFraction(t)
  = 0                                       if manual == 0
    (manual + 1) / (ideRelevantTotal + 2)   otherwise
where manual = steps with checkpointIndex ≤ t, s.ideRelevant, !s.wasPerformedByIde
```

When the relevant denominator is zero (no refactoring steps yet, no smells yet), the penalty is 0. When there's activity but zero bad behaviour, the penalty is also 0 (asymmetric Laplace — see design decision 4).

## Decomposition

Each `CheckpointVM` carries a `processScore`, `processBreakdown`, `cleanlinessScore`, and `cleanlinessBreakdown`:

```ts
type ProcessScoreBreakdown = {
  total: number              // 0..100, rounded
  baseline: number           // 50
  contributions: Array<{
    id: "cleanliness" | "degradation" | "broken" | "smells" | "skipTests" | "manualIde"
    label: string
    points: number           // signed; sums (with baseline) to total before clamping
    detail: string
  }>
  clamped: boolean
}

type CleanlinessBreakdown = {
  total: number              // 0..100, rounded
  contributions: Array<{
    id: MetricId             // one of the six code metrics
    label: string
    weight: number           // literature weight, 0..1
    normalised: number       // 0..1, 1 = best in trajectory
    raw: number
    points: number           // weight·normalised·100, rebased
  }>
  rebased: boolean           // true iff any weighted metric was excluded
}
```

## UI

- Process score is the **default primary** on the chart.
- Code cleanliness is the **default first overlay**, readability the second.
- Process and cleanliness are exposed as their own `MetricId`s with `group: "process"`. Two new brand tones (`brand-7` lime for cohesion's bumped slot, `brand-8` gold for the new headline metric).
- Breakdowns are surfaced via **hover-cards on the metric tiles** in the detail panel, not as inline sections. `MetricTile` gained a `hoverContent?: ReactNode` prop — a `?` icon next to the tile value reveals the hover-card. Keeps the detail panel compact while making the explainers discoverable.
- The chart toolbar's "How it's calculated" hover-card shows the full formula including the rescaled weights and the intermediate-degradation term.

## Files shipped

**New:**
- `src/data/process-score.ts` — `computeProcessScores`. Top-of-file comment carries design decisions 1–9.
- `src/data/cleanliness.ts` — `computeCleanlinessSeries` + `buildCleanlinessContext` + `evaluateCleanlinessFor`. Owns the literature-weighted composite, min-max normalisation, and breakdown shape.
- `src/components/process-score-breakdown.tsx` — hover-card content for the process tile.
- `src/components/cleanliness-breakdown.tsx` — hover-card content for the cleanliness tile.

**Modified:**
- `src/data/types.ts` — added `"process"`, `"cleanliness"` to `MetricId`; added `"brand-7"`, `"brand-8"` to `MetricTone`; added the breakdown types and the `processScore` / `cleanlinessScore` fields on `CheckpointVM`; added `"degradation"` to `ProcessScoreContribution.id`.
- `src/data/view-model.ts` — adds the two new metrics to METRICS, computes both series after the checkpoint loop, threads scores onto each checkpoint and onto `c.values` so the chart picks them up.
- `src/data/metric-descriptors.ts` — descriptors for `process` and `cleanliness`, including the rescaled formula and the relativity caveat.
- `src/components/metric-tile.tsx` — `hoverContent` prop + `?` icon.
- `src/components/{sparkline,text,ui/checkbox}.tsx` — added `brand-7` and `brand-8` variants.
- `src/lib/metric-tone.ts` — `brand-7`, `brand-8` in TONE_TEXT / TONE_BORDER / TONE_BG.
- `src/index.css` — `--brand-7` (lime), `--brand-8` (gold).
- `src/features/detail-panel/checkpoint-body.tsx` — wires `hoverContent` into the process and cleanliness tiles.
- `src/features/metric-rail/primary-metric-row.tsx` — fixed delta arrow to follow value direction (▲ when up regardless of "better"), so process going up shows ▲ in green.
- `src/features/trajectory-chart/chart-toolbar.tsx` — formats first→last value to dp consistent with the metric.
- `src/stores/dashboard-store.ts` — default primary `"process"`, default secondaries `["cleanliness", "readability"]`.

## Sequencing (as actually shipped)

1. Land `process-score.ts` with the formula. ✅
2. Wire types + view-model integration. ✅
3. Register the metric descriptor; verify the line renders. ✅
4. Make process the default primary. ✅
5. Surface the breakdown as a `MetricTile` hover-card (replaces the inline section originally planned). ✅
6. Visual smoke against the bundled sample report; tune. ✅
7. (Beyond V0) Expose code cleanliness as its own metric with its own hover-card breakdown. ✅
8. (Beyond V0) Split cleanliness logic into `cleanliness.ts`. ✅
9. (Beyond V0) Add intermediate-degradation term. ✅
10. (Beyond V0) Rescale weights so perfect = 100. ✅

## Verification

Dashboard project has no test framework set up — unit tests from the original plan were skipped. Verification was visual smoke against bundled sample reports:

1. Process line renders as default primary on the chart.
2. Cleanliness + readability render as overlays by default.
3. Tile `?` icons appear on `Process Score` and `Code Cleanliness`; hover reveals the breakdown.
4. Breakdown contributions sum to total (modulo clamping); detail rows match the running counters at the selected checkpoint.
5. `npm run typecheck` green.

If we later add a test framework, the priorities are:
- Composite normalisation + degenerate-range handling (cleanliness.ts)
- Each penalty fraction in isolation (process-score.ts)
- Asymmetric Laplace smoothing (perfect record → 0 penalty)
- Clamping behaviour
- Score(0) ≈ 50

## Open follow-ups

- **Process score for `AlternativeTrajectoryVM`.** Compute the score the user would have hit if the IDE had performed the alt at this divergence point, render alongside the user's actual score in `alternative-body.tsx`. Plan: snapshot per-checkpoint state inside `computeProcessScores`, expose a `computeAlternativeProcessScore(snapshot, alt)` helper, evaluate alt's cleanliness against the user trajectory's ranges via the new `evaluateCleanlinessFor` API. Assumptions for the alt step: `+1` IDE-relevant refactoring, `wasPerformedByIde: true`, tests assumed run, smell delta neutral.
- **Smell line vs process-score smell treatment.** Chart's existing smell line is a count; process score uses priority-weighted PMD. They disagree on what "smells" means. Worth reconciling.
- **Dashboard test infra.** The project has no vitest/jest setup. If/when added, port the unit-test plan above.
