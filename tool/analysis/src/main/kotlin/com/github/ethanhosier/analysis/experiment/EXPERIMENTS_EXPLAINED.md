# Experiments — quick reference

Three experiment drivers, one corpus, three different questions. This
document describes *what each experiment measures and what its
output looks like*, not what the results say. Detailed results
writeups live in `explained_results/`.

## Shared metrics

### Kendall τ-b

A rank-correlation coefficient that measures how well two rankings of
the same items agree.

- For every pair of items, classify as *concordant* (same relative
  order in both rankings) or *discordant* (opposite order).
- τ-b = (concordant − discordant) / √((n₀ − n₁)(n₀ − n₂))
  where n₀ = total pairs, n₁ = tied pairs in ranking 1, n₂ = tied
  pairs in ranking 2. The "b" variant subtracts ties from the
  denominator so tied items don't artificially depress the score.
- Range: −1 (perfect reversal) to +1 (identical ordering). 0 = no
  correlation.
- We use τ-b for comparing perturbed divergence-point rankings to
  the baseline, since DP magnitudes commonly tie (especially at zero).

### Top-N hit rate (we use N = 5)

Set-membership agreement between the top-N of two rankings.

- top-N hit rate = |baseline_topN ∩ perturbed_topN| / N.
- Range: 0 (no overlap) to 1 (same top-N items, in any order).
- Insensitive to within-top-N reshuffling. Captures the user-
  facing surface (the dashboard surfaces the top few DPs first).
- More stable than τ-b on small DP counts: if a fixture has ≤ N
  DPs, the top-N is just the full set, so any perturbation that
  preserves the set scores 1.0.

## Driver 1: `SensitivityExperiment.kt` → `--sensitivity`

**Asks:** *Is the divergence-point ranking robust to small/medium
changes in the score formula weights I chose?*

**Method:** for each of the 12 ScoringConfig knobs (6 process +
6 cleanliness), scale it by one of 9 factors (×0.1 to ×10) one at
a time, leaving the other 11 at production. Re-assemble the
analysis report. Compare the perturbed divergence-point ranking
against the production-config baseline using Kendall τ-b and
top-5 hit rate.

**Run size:** 12 knobs × 9 factors × 45 fixtures = **4860 rows**.

**Output columns** (one row per `(fixture, knob, factor)`):

- `kendall_tau` — full-ranking agreement with the baseline.
- `topn_hit_rate_5` — fraction of the top-5 baseline DPs still
  in the perturbed top-5.
- `baseline_saturated_dp_count` /
  `perturbed_saturated_dp_count` — how many DPs sit at the
  [0, 100] score clamp (used to filter saturation artefacts —
  saturated DPs have magnitude 0 regardless of weights, which
  can mute apparent sensitivity).

## Driver 2: `AblationExperiment.kt` → `--ablation`

**Asks:** *Does every term in the process-score formula
contribute, or is the result driven by one or two terms with the
rest as decoration?*

**Method:** for each of the 6 process-score terms (`gain`,
`broken`, `skipTests`, `manualIde`, `length`, `commitGap`), build
every subset of the 64 possible variants (full power set,
2⁶ = 64). Terms in the subset stay at production; terms outside
are zeroed. Re-assemble and compare against production using τ,
top-5 hit rate, *magnitude recovery* (sum of |magnitude| over all
DPs in the variant divided by the same sum at production), and
mean/max |Δmagnitude| per paired DP.

**Run size:** 64 variants × 45 fixtures = **2880 rows**.

**Output columns** (one row per `(fixture, variant)`):

- `active_count` — number of terms active in the variant (0–6).
- `kendall_tau`, `topn_hit_rate_5` — same definitions as
  Sensitivity, but compared against production with this variant
  rather than a single-knob perturbation.
- `total_abs_magnitude_recovery` — fraction of total magnitude
  this variant recovers (1.0 = production).
- `mean_abs_delta_magnitude` / `max_abs_delta_magnitude` —
  how much DP magnitudes shift under this variant.

## Driver 3: `DivergenceExperiment.kt` → `--divergence`

**Asks:** *How good is the detector at flagging deliberately-
injected bad-process patterns, per-kind?*

**Method:** the 45-session corpus has multi-label ground truth
in `manifest-v2.csv` (each session can be tagged with multiple
kinds in `expected_kinds`, plus an `injection_kind` for the
deliberately-injected bad pattern). For each session, assemble
the analysis report at production config and ask, per kind:

- did the detector emit a DP of that kind?
- if so, what is the maximum magnitude, the alt count, the
  injection's DP rank, and does any DP target-step match?

Internal baselines (`endpoint_improved` based on cleanliness
delta; `perstep_hit` based on per-step regression) are computed
alongside as comparison signals.

**Run size:** **45 rows** (one per session).

**Output columns** (one row per session). Per kind ∈ {ORDERING,
IDE_REPLAY, REWORK, HYGIENE}:

- `<kind>_dp_count`, `<kind>_alt_count` — surfacing volume.
- `<kind>_tp`, `<kind>_fp`, `<kind>_fn`, `<kind>_tn` —
  binary classification against `expected_kinds`.
- `<kind>_max_magnitude`, `<kind>_min_magnitude`,
  `<kind>_mean_magnitude` — DP magnitude distribution.
- `<kind>_beats_count` — DPs of that kind with positive
  magnitude (i.e. the alt is actually better than the user).
- `<kind>_magnitudes` — semicolon-separated list of all
  magnitudes in DP order, for downstream histogram/scatter
  plotting.

Plus injection-prominence and baseline columns:

- `injection_kind`, `injection_caught`,
  `injection_max_magnitude` — did the deliberately-injected
  pattern get detected?
- `injection_dp_count`, `injection_dp_rank` — number of DPs of
  the injection's kind, and the 1-indexed rank of the
  highest-magnitude one in the magnitude-sorted DP list.
- `injection_step_match` — does any DP of the injection's kind
  anchor on the injected step (or contain it in its
  orderingWindowSteps)?
- `endpoint_improved`, `perstep_hit`,
  `cleanliness_delta`, `perstep_regression_steps` — internal
  baselines for comparison.

## Shared infrastructure

- `RankingMetrics.kt` — Kendall τ-b and top-N hit rate
  implementations. Reused by Sensitivity and Ablation.
- `baselines/Baselines.kt` — internal baseline computations
  (`endpoint_improved`, `perstep_hit`, `perstep_regression`).
  Reused by Divergence.
- All three drivers consume `PhaseAResult` JSON dumps (the
  output of `:phaseA`) so they re-score the corpus without
  re-running the analysis pipeline from raw sessions.
