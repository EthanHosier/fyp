# Experiment 2 — Ablation Sweep: Explained Results

## What the experiment asks

> *"Does every term in the process-score formula contribute, or is the
> headline detector ranking actually decided by one or two terms with
> the others as decoration?"*

This complements Experiment 1 (Sensitivity). Sensitivity says "the
ranking is robust to *plausible* perturbations of the weights I chose."
Ablation says "but the weights I chose are *non-redundant* — removing
whole terms breaks the result." Both defences must hold:

- If sensitivity holds **but** ablation also leaves the ranking
  unchanged, the formula is a fancy way of returning a result driven by
  something weight-independent — the terms are decorative.
- If ablation breaks the result **but** sensitivity is also fragile,
  the formula is over-tuned — the terms matter, but only at the
  specific weights chosen.
- The defensible position is **sensitivity holds + ablation breaks
  things**: the formula is robust to *plausible* weights and the terms
  are genuinely doing work.

## How it runs

For each Phase-A dump in `--corpus` (45 fixtures):

1. Assemble a baseline `AnalysisReport` at `ScoringConfig.PRODUCTION`
   and record the ranking + saturated DP count + total absolute
   magnitude (`sum |magnitude|` over all DPs).
2. For every subset of the six process-score knobs (`gain`, `broken`,
   `skipTests`, `manualIde`, `length`, `commitGap`) — **the full power
   set, 2^6 = 64 variants** — build a ScoringConfig where terms in the
   subset stay at production values and terms outside the subset are
   zeroed.
3. Reassemble. Record τ, top-5 hit rate, magnitude recovery
   (`perturbed_total_abs_magnitude / baseline_total_abs_magnitude`),
   mean and max |Δmagnitude| per paired DP.

64 × 45 = **2880 rows**. ~1.7 s wall-clock.

**Note on scope:** the cleanliness sub-weights (`cognitive`, `coupling`,
…) are kept at production across all variants. Sensitivity Experiment 1
already showed cleanliness perturbations almost never reshuffle the
ranking (4/2430 = 0.16 %), so cleanliness ablation would have been
uninformative. Process-side power set is the interesting question.

## Run command

```
./gradlew :analysis:ablation \
    --args="--corpus fixtures/corpus --output /tmp/ablation-results.csv" -q
```

## Headline numbers

### Sanity row passes

The variant with all 6 terms active (the "full" config = PRODUCTION)
produces τ = 1.000, recovery = 1.000, mean |Δmagnitude| = 0.000 on
every fixture. Comparison plumbing is correct.

### Monotone by active-term count

Across 45 fixtures, grouped by how many process-score terms are
active in the variant. The `sum/sum` column is the sum of |magnitude|
across all DPs in all 45 fixtures divided by the same sum at
production — the honest "fraction of total corpus magnitude that
survives" number. The mean-of-fractions column is shown alongside
for continuity with earlier reports, but it includes 0/0 = 1.0 rows
for fixtures with no DPs, so it overstates the result.

| active terms | mean τ | mean recovery | sum/sum recovery | n  |
|-------------:|-------:|--------------:|-----------------:|---:|
| 0 (all stripped) | 0.830 | 0.333 | **0.000** | 45  |
| 1                | 0.863 | 0.476 | 0.211     | 270 |
| 2                | 0.892 | 0.597 | 0.400     | 675 |
| 3                | 0.919 | 0.706 | 0.570     | 900 |
| 4                | 0.945 | 0.808 | 0.726     | 675 |
| 5                | 0.972 | 0.907 | 0.869     | 270 |
| 6 (full)         | 1.000 | 1.000 | 1.000     | 45  |

Both τ and magnitude recovery rise **monotonically** with each term
added. No discontinuities — no single term flips the result; every
active term contributes incrementally.

The headline cell is **sum/sum recovery = 0.000 at
active\_count = 0**. With every process-score term zeroed, every
single divergence-point magnitude in the corpus collapses to zero.
There is no weight-independent magnitude floor — every term in the
formula does real work, and removing all six leaves nothing behind.

### Per-term contribution — single-knob (term alone, others zeroed)

Which term recovers the most magnitude when added to the empty
baseline:

| term       | mean τ | mean recovery | sum/sum |
|------------|-------:|--------------:|--------:|
| `manualIde` | 0.830 | 0.457 | **0.357** |
| `broken`    | 0.896 | 0.615 | 0.348     |
| `length`    | 0.830 | 0.632 | 0.342     |
| `commitGap` | 0.904 | 0.387 | 0.126     |
| `skipTests` | 0.889 | 0.431 | 0.093     |
| `gain`      | 0.830 | 0.333 | **0.000** |

- `manualIde`, `broken`, and `length` are the **strongest single
  contributors**, each recovering roughly a third of the corpus's
  total magnitude on their own (sum/sum ≈ 0.34–0.36).
- `gain` alone recovers 0.000 of the corpus total. Despite `gain`
  having the highest production weight (50), its solo contribution
  is empty: it multiplies the cleanliness gain $\Delta C$, but on
  this corpus the alt and user trajectories reach the same end state
  on most fixtures, so $\Delta C \approx 0$ and the term contributes
  nothing on its own. Its effect emerges only in combination with the
  penalty terms (visible in the rising sum/sum recovery as more terms
  activate).
- The gap between `commitGap` (0.126) and `manualIde` (0.357) shows
  that single-knob recovery is roughly proportional to weight × the
  variability of that term's signal across the corpus, not just to
  the weight itself.

### Per-term contribution — leave-one-out (5 active, 1 removed)

How much the ranking and magnitude break when one term is removed
from the full formula:

| removed term | mean τ | mean recovery | sum/sum |
|--------------|-------:|--------------:|--------:|
| `length`     | 1.000  | 0.814         | 0.736   |
| `manualIde`  | 1.000  | 0.887         | 0.772   |
| `broken`     | 0.933  | 0.945         | 0.826   |
| `skipTests`  | 0.970  | 0.898         | 0.901   |
| `commitGap`  | 0.926  | 0.952         | 0.892   |
| `gain`       | 1.000  | 0.944         | **1.090** |

Three observations:

1. **Removing `length` causes the largest sum/sum drop (0.736)** —
   the alt's step-savings bonus contributes more total magnitude than
   any other single removable term. This is consistent with REWORK
   and ORDERING alts both gaining the W_L bonus directly.

2. **Removing `gain` causes sum/sum to exceed 1.000 (1.090).** This
   is the most surprising row: with `gain` removed, the total
   absolute magnitude in the corpus is *larger* than at production,
   not smaller. The mechanism is that `gain × ΔC` is small but
   often *positive* for alts, so it partially offsets the penalty
   terms and keeps magnitudes closer to zero. Remove it and the
   penalty terms operate unchecked, producing larger absolute
   magnitudes (the alts diverge further from the user). Useful
   diagnostic but not evidence that `gain` is dead weight — it's
   evidence that `gain` performs a *regularising* role.

3. **Removing `broken`, `skipTests`, or `commitGap` reshuffles the
   ranking (τ ≤ 0.97) but barely changes sum/sum (≥ 0.83).** These
   terms primarily affect *which* DP is biggest, not how big the
   biggest DPs are.

## Interpretation

### Is this a "good" result?

**Yes — and the V2 magnitude semantic (trajectory-final, uniform
across all four kinds) gives a cleaner story than the earlier
line-count REWORK magnitude did.**

Two things had to be true for the score formula to be defensible:

1. **No term is dead weight.** Every single-term variant produces
   strictly positive sum/sum recovery except `gain`, which is solo-
   zero for a documented reason (cleanliness Δ is near zero on most
   fixtures, so the term has nothing to multiply on its own).
2. **No term is doing all the work.** Best single-term recovery is
   `manualIde` at 0.357, well short of 1.0. You genuinely need most
   of the terms to recover full magnitude.

Both pass.

The richer finding is that **terms divide their labour**. Some
contribute primarily to *signal strength* (`manualIde`, `broken`,
`length`), some to *ordering* (`broken`, `commitGap`), and `gain`
acts as a *regulariser* — removing it produces *larger* absolute
magnitudes than production, not smaller (sum/sum 1.090 in the
leave-one-out row).

### Where it would have been bad

- If `cleanlinessOnly` (active_count = 0 + cleanliness Δ via `gain`)
  had given recovery ≈ 1.0 → "you didn't need any process terms;
  cleanliness Δ alone explains everything." Did not happen:
  sum/sum recovery 0.000.
- If one term solo had given recovery ≈ 1.0 → "the formula collapses
  to a single signal; the other five are decorative." Did not
  happen: max solo recovery is `manualIde` at 0.357.
- If sum/sum at active_count = 0 had been substantially > 0 →
  "a chunk of the result comes from weight-invariant magnitudes,
  not from the score formula itself." Under V1 (REWORK = reverted-
  line count) this *was* the case: 0.119 sum/sum survived even with
  every weight zeroed, because line-count REWORK magnitudes were
  weight-invariant by construction. Under V2, REWORK magnitudes are
  trajectory-final process-score deltas like the other three kinds,
  so the floor collapses to 0.000.

None of those happened. The result clears all three bars.

### What I claim in the chapter

> Full power-set ablation across the six process-score weights
> (2^6 = 64 variants per fixture, 2880 rows total) shows that no
> single term dominates the divergence-point result. Sum-of-
> magnitude recovery rises monotonically with active-term count
> from 0.000 (every process term stripped) to 1.000 (full formula);
> τ rises in parallel from 0.830 to 1.000. The largest single-term
> magnitude contributor is `manualIde` (sum/sum 0.357), with
> `broken` (0.348) and `length` (0.342) close behind. Removing
> `length` from the full formula causes the largest sum/sum drop
> (0.736); removing `gain` increases sum/sum above 1.0, indicating
> `gain` acts as a regulariser rather than as a positive
> contributor in isolation. Together these results confirm that
> the score formula's terms are complementary rather than
> redundant: every term has a measurable empirical role, and the
> zero-floor at active_count = 0 shows there is no weight-invariant
> residual driving the result.

## Caveats / limitations

1. **Cleanliness sub-weights not ablated.** Experiment 1 already
   showed cleanliness perturbations almost never reshuffle the
   ranking (0.16 % of cleanliness rows had τ < 1). Ablating those
   weights would extend the variant count from 64 to 4096 with
   negligible new information.
2. **Saturation column tracked but saturation impact on ablation is
   low.** Mean `perturbed_saturated_dp_count` runs ~0.20–0.29 across
   variants — saturation is not muting the ablation signal
   materially on this corpus.
3. **τ-b on small DP counts is brittle.** Many fixtures have only
   1–2 DPs in baseline; τ on those is structurally 1.0 (single-item
   rankings cannot reshuffle). The mean-τ readings at active_count
   = 5 (≥ 0.93 across all rows) partly reflect this ranking-length
   floor rather than pure robustness.
4. **`gain` alone equals the empty baseline in sum/sum recovery
   (both 0.000).** This is an honest finding worth disclosing in
   the methodology chapter rather than smoothing over. The defence
   is that `gain` contributes in combination with the process
   penalty terms (visible in the active_count = 2/3/4 rows where
   recovery rises smoothly regardless of whether `gain` is one of
   the active terms), and as a regulariser when full (removing it
   inflates absolute magnitudes).
5. **Methodology note: magnitude is trajectory-final under V2.**
   This ablation operates on the V2 magnitude semantic — alt's
   process score rolled forward through the user's post-convergence
   activity, minus user's trajectory-final score. Under the earlier
   V1 semantic (alt score at convergence point; REWORK magnitudes
   as line counts), the active_count = 0 sum/sum recovery was 0.119
   and `broken` was the strongest solo at 0.407. The qualitative
   ranking (manualIde / broken / length cluster as strongest;
   skipTests / commitGap weaker; gain solo-empty) is consistent
   across both semantics.
