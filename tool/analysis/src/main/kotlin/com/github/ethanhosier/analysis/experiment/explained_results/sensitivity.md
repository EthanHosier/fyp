# Experiment 1 — Sensitivity Sweep: Explained Results

## What the experiment asks

> *"Are the headline detector results sensitive to the specific weights I
> chose, or would a different but reasonable weighting produce more-or-
> less the same divergence-point ranking?"*

This is the defence against a "you cherry-picked the weights" review. If
the ranking is robust to perturbation around `ScoringConfig.PRODUCTION`,
the choice of weights doesn't carry the result.

## How it runs

For each Phase-A dump in `--corpus` (45 fixtures):

1. Assemble a baseline `AnalysisReport` at `ScoringConfig.PRODUCTION` and
   record the divergence-point ranking (ordered by descending magnitude).
2. For each of 12 knobs (6 `ProcessScoreWeights` + 6 `CleanlinessWeights`)
   and 9 scaling factors `[0.1, 0.25, 0.5, 0.75, 1.25, 1.5, 2.0, 4.0, 10.0]`:
   - Scale that one knob, leaving every other knob at production.
   - Reassemble. Compare the perturbed ranking against the baseline
     using `RankingMetrics.kendallTauB` and `RankingMetrics.topNHitRate(n=5)`.
3. Also record `baseline_saturated_dp_count` and `perturbed_saturated_dp_count`:
   number of divergence points whose terminal user- or alt-process score
   sits at the [0, 100] clamp boundary. Saturation can mute apparent
   sensitivity (both endpoints clipped → magnitude is 0 regardless of
   weights → τ = 1.0 even when weights "should" matter).

12 × 9 × 45 = **4860 rows**. ~2.6 s wall-clock.

## Run command

```
./gradlew :analysis:sensitivity \
    --args="--corpus fixtures/corpus --output /tmp/sensitivity-results.csv" -q
```

## Headline numbers

### τ distribution across all 4860 perturbations

| τ band       | rows | % of total |
|--------------|-----:|-----------:|
| τ = 1.00     | 4818 | **99.1 %** |
| τ ∈ [0.25, 1.00) | 22 | 0.5 %  |
| τ ∈ [0, 0.25)    |  2 | 0.0 %  |
| τ < 0            | 18 | 0.4 %  |

**99.1 % of perturbations leave the ranking unchanged.** The top-5 hit
rate is **1.0 on every single row** — the user-facing top of the
ranking is fully robust to every perturbation tested.

### Which knobs reshuffle the ranking when pushed hard

Mean τ across 45 fixtures, sorted ascending (lower = more sensitive):

| knob × factor       | mean τ |
|---------------------|-------:|
| `gain × 10`         | 0.896  |
| `manualIde × 10`    | 0.896  |
| `manualIde × 4`     | 0.896  |
| `gain × 4`          | 0.926  |
| `commitGap × 0.1`   | 0.948  |
| `broken × 0.1`      | 0.956  |
| `cohesion × 10`     | 0.956  |
| `readability × 0.1` | 0.956  |
| every other combination | ≥ 0.97 mean |

Two patterns:

- **Extreme factors dominate.** Every entry above lives at ×4, ×10, or
  ×0.1. Inside ±50 % (the default range we ran first) no knob
  meaningfully reshapes the ranking; you have to push by ×4 or more
  before single-knob perturbations start to matter.
- **Process knobs reshuffle 10× more often than cleanliness knobs.**
  Process: 38/2430 rows (1.56 %) have τ < 1. Cleanliness: 4/2430 rows
  (0.16 %). The four cleanliness exceptions are all extreme factors
  (×0.1 / ×10) on the same two small sessions (025, 037) where
  process knobs also reshuffled. **The ranking is driven by
  process-score Δs, not by absolute cleanliness levels** — which makes
  intuitive sense because cleanliness contributes a level shift shared
  by user and alt that mostly cancels in the alt − user delta.

### Saturation check

- **15.6 %** of rows have at least one divergence point at the [0, 100]
  clamp boundary (baseline_saturated_dp_count > 0).
- **12.3 %** of all baseline divergence points (972 / 7884) are saturated.
- Concentrated in 7 fixtures: **003, 004, 006, 008, 009, 026** (every DP
  saturated — these are sessions where the user's process is already at
  the ceiling) plus **044** (1 of 3 saturated).

**Even removing the saturated rows from the analysis, the remaining
84.4 % of rows still show ~99 % τ = 1.0.** The robustness story is not
an artefact of clipping; saturation is concentrated in a small subset
of sessions and the overall τ ≈ 1.0 finding holds on the unsaturated
remainder.

## Interpretation

### Is this a "good" result?

**Yes, and a richer kind of good than perfect uniformity would have been.**

If τ had stayed exactly 1.0 across the entire ×0.1–×10 range with zero
exceptions, a reviewer would reasonably suspect the metric is dominated
by something weight-independent or by saturation. Under the earlier V1
semantic this concern was real (REWORK was line-count-magnitude and
COMMIT_GAP magnitudes were structurally zero at the anchor checkpoint).
Under V2, all four kinds use trajectory-final process-score deltas, so
both REWORK and COMMIT_GAP respond to weight changes. The fact that
**τ does collapse to negative values on 0.4 % of perturbations** —
all on extreme factors of high-signal process knobs — means the
weights genuinely participate in the ranking. They just don't dominate
it within a plausible range.

### What I claim in the chapter

> Across 4,860 single-knob perturbations spanning ×0.1 to ×10, the
> divergence-point ranking is preserved (Kendall τ-b = 1.0) on 99.1 %
> of perturbations, and the top-5 hit rate is 1.0 on 100 %. Rankings
> begin to reshuffle (and in 0.4 % of cases invert) only when
> individual high-signal weights (`gain`, `manualIde`, `commitGap`,
> `broken`) are pushed beyond ×4. The 15.6 % of rows containing a
> saturated divergence point are concentrated in 7 sessions where the
> user's process score is already at the [0, 100] ceiling; the
> robustness finding holds independently on the unsaturated 84.4 % of
> rows. Cleanliness weights contribute essentially nothing to ranking
> order (0.16 % of cleanliness perturbations reshuffle), confirming
> that the divergence-point ranking is driven by *process-score*
> alt − user deltas rather than absolute cleanliness levels.

## Caveats / limitations

1. **Single-knob perturbations only.** Interaction effects (e.g.
   halving `gain` while doubling `broken`) are out of scope here;
   covered by `AblationExperiment` for the cumulative-zero case.
2. **τ-b on small DP counts is brittle.** Several sessions have only
   1–3 DPs; on a 3-DP ranking, τ = 0.333 means a single pair swap. The
   top-5 hit rate column (always 1.0 here) is the more stable signal
   for the user-facing surface of the detector.
3. **HYGIENE COMMIT_GAP DPs have a discrete magnitude exactly equal
   to `W_cg`** (= 7 in production). The commit-flip alt breaks the
   user's $\geq 6$-checkpoint green-refactor stretch into two sub-
   stretches that each fall below the threshold, so $n_{cg}$ drops
   from 1 to 0 at trajectory-final and the magnitude is exactly the
   weight. This means perturbing `W_cg` moves the COMMIT_GAP DPs'
   magnitudes by a fixed proportion — they respond to weight
   changes, unlike under the V1 semantic where COMMIT_GAP magnitudes
   were measured at the anchor (before the event accumulated) and
   were therefore structurally zero.
4. **REWORK magnitudes are now trajectory-final process-score deltas
   (V2 semantic), so they are weight-dependent like the other three
   kinds.** Under the earlier V1 semantic (REWORK magnitude =
   reverted-line count) REWORK DPs were perfectly stable under every
   perturbation by design and inflated τ artifactually. Under V2,
   REWORK DPs respond to weight changes — particularly to `length`
   (the W_L step-savings bonus) and `broken` — and the τ stability
   reported above is therefore a property of the score formula
   rather than of the magnitude semantic.

These caveats are honest disclosures; none of them change the
robustness verdict, but they shape what the τ ≈ 1.0 finding *means*.
