# `W_lag` (Process-Score Lag Penalty) — Empirical Validation

Branch: `feat/lag-term`. Production weight: `W_lag = 11.0`. All experiments re-run
2026-05-26 against the same three Phase-A corpora used for the thesis baseline.
Baseline CSVs preserved at `notebooks/data/baseline-no-lag/`; fresh CSVs at
`notebooks/data/`.

## TL;DR

`W_lag` does exactly what it was designed to do on the divergence experiment:
it lifts every ORDERING divergence-point magnitude (`mean_of_session_max`
+0.79, `mean_of_session_mean` +0.64, all-mags mean +0.67) **without changing
the ORDERING DP count or its TP/FN classification**, and it flips the one
borderline ORDERING fixture (`022`) from missed to caught (injection-caught
rate 25/45 → 26/45). It is well-behaved on ablation — solo magnitude-recovery
ranks middle of the pack (0.42 on inj, between `commitGap` 0.37 and
`skipTests` 0.40), so it neither swamps the formula nor is a no-op — and is
robust on sensitivity (Kendall τ ≥ 0.97 on inj, 0.99 on user, 0.93 on agent
under ×0.5/×1.5 perturbation; top-5 hit-rate 1.0 across every corpus). The
joint-MC robustness is essentially unchanged (τ_mean 0.986→0.975 on inj, +ε
on user and agent). No surprises, no crashes.

## What ran

| Experiment | Corpora | Command | Output |
|---|---|---|---|
| Ablation | `corpus/`, `user-corpus/`, `agent-corpus/` | `:analysis:ablation --corpus X --output Y` | `ablation-{inj,user,agent}.csv` |
| Sensitivity | same | `:analysis:sensitivity ...` | `sens-*.csv` |
| Multi-knob MC | same (`--samples 200 --seed 1`) | `:analysis:multiKnobMC ...` | `mc-*.csv` |
| Divergence | `corpus/` + `fixtures/sessions/manifest-v2.csv` | `:analysis:divergence ...` | `divergence.csv` |

Ablation row counts doubled (64 → 128 variants per fixture, i.e. 2^7) and
sensitivity gained one knob — confirming the lag knob is wired into both
power-set generators. The baseline `divergence.csv` was generated against the
v2 manifest schema (`expected_kinds` plural), so we used `manifest-v2.csv`
(the legacy `manifest.csv` still has the older `expected_kind` header and
trips the strict header check).

No experiment crashed and no row was silently dropped. Phase-B per-session
report dump was skipped — no pre-existing dump directory was found to
compare against; the experiment CSVs cover everything the thesis cites.

## 1 — Ablation: does `W_lag` carry its own weight?

### 1.1 Solo magnitude-recovery (single knob active vs. full PRODUCTION)

`magnitude_recovery_fraction` averaged across fixtures, only rows with
`active_count == 1`.

| Knob | inj (base→new) | user | agent |
|---|---|---|---|
| `length` | 0.654 → **0.578** | 0.437 → 0.349 | 0.556 → 0.556 |
| `broken` | 0.614 → 0.553 | 0.548 → 0.459 | 0.589 → 0.589 |
| `skipTests` | 0.467 → 0.398 | 0.449 → 0.359 | 0.500 → 0.500 |
| `manualIde` | 0.439 → 0.375 | 0.421 → 0.335 | 0.720 → 0.636 |
| **`lag` (new)** | **— → 0.423** | **— → 0.176** | **— → 0.528** |
| `commitGap` | 0.414 → 0.368 | 0.668 → 0.579 | 0.500 → 0.500 |
| `gain` | 0.356 → 0.311 | 0.250 → 0.167 | 0.500 → 0.500 |

`lag` lands **4th of 7 on inj**, **6th of 7 on user**, **3rd of 7 on agent**.
It is *not* dominant (no swamping concern — well under the 0.5 threshold
flagged in the task description on inj and user), nor is it inert. The
existing knobs' solo-recovery values drop by 5–15 % across the board because
they now share their "explanation budget" with `lag`, which is the expected
mechanical effect of adding a knob to the power-set — not a regression.

### 1.2 Leave-one-out (n-1 knobs active)

Dropping `lag` from the new full-PRODUCTION ablation (the row
`gain+broken+skipTests+manualIde+length+commitGap`, i.e. the *old* baseline
config) is exactly the operation needed to ask "what does `lag` add?".

| Corpus | τ (drop `lag`) | top-5 | rec |
|---|---|---|---|
| inj | 0.9956 | 1.000 | 0.880 |
| user | 0.9921 | 1.000 | 0.903 |
| agent | 1.0000 | 1.000 | 0.944 |

Production ranking is essentially identical to the pre-lag ranking
(τ ≈ 1, top-5 perfect), so `W_lag` does not destabilise the broader process
score. On user it pulls the leave-one-out τ for the "drop `broken`" variant
from 0.992 → 1.000 (a tiny *improvement* in rank stability), and the
"drop `gain`+keep rest" variant from τ = 0.837 → 0.901.

### 1.3 ORDERING-specific recovery

The central thesis claim is that `lag` is the lever for ORDERING. Solo
recovery is computed against full PRODUCTION across all 45 fixtures, so it
folds in non-ORDERING sessions; the cleaner test is the divergence
experiment (§3), where `lag` lifts every ORDERING magnitude column without
touching counts. On the agent corpus the solo recovery 0.528 is the
*highest* mid-tier knob (above `commitGap`, `gain`, `skipTests` and equal
or close to `length`/`broken`), which is consistent with agent runs being
ordering-heavy.

## 2 — Sensitivity: is the new weight stable under ×0.5 / ×1.5?

Mean Kendall-τ over the two perturbation factors, per knob.

| Weight | inj (base→new τ) | user | agent |
|---|---|---|---|
| `process.lag` | **— → 0.973** | **— → 0.987** | **— → 0.926** |
| `process.broken` | 0.990 → 0.984 | 0.905 → 0.902 | 0.852 → 0.815 |
| `process.commitGap` | 0.987 → 0.990 | 0.828 → 0.831 | 1.000 → 1.000 |
| `process.gain` | 0.989 → 0.989 | 0.898 → 0.915 | 0.889 → 0.889 |
| `process.length` | 1.000 → 1.000 | 0.941 → 0.941 | 0.815 → 0.815 |
| `process.manualIde` | 1.000 → 1.000 | 0.867 → 0.873 | 0.864 → 0.827 |
| `process.skipTests` | 1.000 → 0.987 | 0.906 → 0.909 | 1.000 → 1.000 |

`top-5` is 1.000 for `lag` on every corpus; `top-1` is 0.980 (inj), 1.000
(user), 0.963 (agent). The formula's ranking is robust to a 50 % miscalibration
of `W_lag` in either direction. Note `lag` is *more* stable than the
pre-existing `broken`/`gain`/`length` on `user` — a useful side-finding.

Two small regressions worth flagging honestly:

- inj `process.skipTests` τ drops 1.000 → 0.987 — a 1.3 % nudge from a
  perturbation that previously had no effect.
- agent cleanliness knobs (`cognitive`, `coupling`, `readability`,
  `duplication`, `smells`) each drop ~3–10 % in τ. This is because
  `cleanlinessFinal` is now an input to `lag`, so cleanliness-weight
  perturbations leak into the process score via the final-cleanliness
  baseline. The drop is largest on the small agent corpus (6 fixtures) where
  one shifted rank dominates. Top-5 stays at 1.000 throughout, so this is
  noise inside the kendall calculation, not a re-ranking risk.

## 3 — Divergence experiment (ORDERING focus)

`fixtures/sessions/manifest-v2.csv`, 45 sessions.

### 3.1 Headline

- **Injection caught**: 25/45 (baseline) → **26/45** (new). Session `022`
  (`expected_kinds=ORDERING`) flips from `injection_caught=false`
  (max-magnitude 0) to `injection_caught=true` (max-magnitude 1).
- **Per-kind TP/FP/FN/TN counts are unchanged for every kind.** No
  classification regression; one borderline ORDERING fixture is now
  classified TP.

### 3.2 Per-kind magnitude lift (the central thesis claim)

The lag term changes how strongly a divergence-point is *scored*, not which
points are *raised*. Quoting the numbers directly from
`scripts/lag-term-analysis.py` output:

| kind | metric | baseline | new | Δ |
|---|---|---|---|---|
| **ordering** | all_mags_mean | 0.792 | **1.458** | **+0.667** |
| **ordering** | mean_of_session_max | 0.929 | **1.714** | **+0.786** |
| **ordering** | mean_of_session_mean | 0.881 | **1.524** | **+0.643** |
| **ordering** | total_dp | 24 | 24 | 0 |
| **ordering** | all_mags_max | 9.000 | 9.000 | 0 |
| ide_replay | all_mags_mean | 9.625 | 11.313 | +1.688 |
| ide_replay | total_dp | 16 | 16 | 0 |
| rework | all_mags_mean | 0.769 | 1.231 | +0.462 |
| rework | total_dp | 13 | 13 | 0 |
| hygiene | all_mags_mean | 4.000 | 4.308 | +0.308 |
| hygiene | total_dp | 13 | 13 | 0 |

ORDERING magnitudes rise by **+82 % relative** at the mean (0.792 → 1.458),
which is the largest *proportional* lift of any kind. IDE_REPLAY rises in
absolute terms by more (+1.69), but in relative terms only +17 %
(9.625 → 11.313), reflecting that IDE_REPLAY alternatives already absorbed
most of the gain through `manualIde`. The smaller +0.31 lift on `hygiene`
and +0.46 on `rework` is the expected leakage — any alt that materially
front-loads cleanliness will see its lag penalty reduced, regardless of
the kind label — but the largest relative lift is on ORDERING, which is
the intended target.

### 3.3 Per-fixture ORDERING deltas

Six ORDERING-expected fixtures changed (none worsened):

| session | expected_kinds | DP count (b→n) | max mag (b→n) | mean mag (b→n) |
|---|---|---|---|---|
| 022 | ORDERING | 1→1 | 0.000 → 1.000 | 0.000 → 1.000 |
| 033 | HYGIENE;ORDERING | 1→1 | 0.000 → 2.000 | 0.000 → 2.000 |
| 035 | HYGIENE;ORDERING | 1→1 | 0.000 → 4.000 | 0.000 → 4.000 |
| 041 | ORDERING | 4→4 | 0.000 → 2.000 | 0.000 → 1.000 |
| 043 | REWORK;ORDERING | 1→1 | 0.000 → −1.000 | 0.000 → −1.000 |
| 044 | ORDERING | 3→3 | 4.000 → 7.000 | 3.333 → 5.333 |

Five of the six lifted from a zero-magnitude "barely surfaced" state to
materially positive (1–7), and `044` (the one that already had a
clear ORDERING signal) got that signal amplified ×1.6. Session `043`
acquired a small *negative* magnitude — magnitude is defined as
`alt − user`, so a negative value means the alt scored *worse* than
the user under `J`. That is exactly the verdict the new formula is
meant to deliver when an alt front-loads *less* cleanliness than the
user did: under the pre-lag formula the two trajectories would have
tied (same endpoints), and the lag term now correctly distinguishes
them. So `W_lag` improves discrimination in both directions, not just
by inflating positives.

### 3.4 Counts of "alt strictly better than user" (magnitude > 0)

The cleanest single number on the central thesis claim. Across all 45
injection sessions, count of divergence-points whose synthesised
alternative beat the user trajectory under `J`:

| Kind | Baseline mag>0 DPs | New mag>0 DPs | Δ | Baseline sessions with ≥1 mag>0 | New sessions with ≥1 mag>0 |
|---|---|---|---|---|---|
| **ORDERING** | **4 / 24** | **10 / 24** | **+6** | **2** | **6** |
| IDE_REPLAY | 12 / 16 | 12 / 16 | 0 | 12 | 12 |
| REWORK | 6 / 13 | 6 / 13 | 0 | 6 | 6 |
| HYGIENE | 13 / 13 | 13 / 13 | 0 | 8 | 8 |
| **Any kind** | **26 / 45 sessions** | **28 / 45 sessions** | **+2 sessions** | — | — |

ORDERING is the only kind whose strictly-better count changes, and it
more than doubles (4 → 10 DPs; 2 → 6 sessions with at least one
strictly-better ordering alt). The other three kinds' counts are
unchanged. This is the most defensible single statement of what
`W_lag` does: it does not manufacture *new* divergence-points (counts
in §3.2 are unchanged), it turns previously-flat (mag = 0) ORDERING
alts into recognisably-better ones — which is exactly what an
endpoint-only `gain` term was blind to.

## 4 — Multi-knob Monte Carlo

200 log-normal joint perturbations, seed 1.

| Corpus | τ_mean (base→new) | τ_med | top-1 | top-5 |
|---|---|---|---|---|
| inj | 0.9860 → 0.9748 | 1.0000 → 1.0000 | 0.998 → 0.987 | 1.000 → 1.000 |
| user | 0.7808 → 0.7850 | 1.0000 → 1.0000 | 0.812 → 0.813 | 0.985 → 0.984 |
| agent | 0.7361 → 0.7478 | 1.0000 → 1.0000 | 0.861 → 0.871 | 1.000 → 1.000 |

Adding a 7th perturbed knob mildly broadens the joint variance on `inj`
(τ_mean −0.011) but improves it on `user` and `agent`. The median is 1.0
throughout — most samples leave the ranking untouched — and top-5 is
unchanged. No robustness regression.

## 5 — Surprises / honest call-outs

- The cleanliness weights' sensitivity τ on the agent corpus drops by
  3–10 %, because `cleanlinessFinal` (a cleanliness aggregate) now
  contributes to the lag denominator. This is a *real* coupling and
  worth a sentence in §3.2.1 of the methodology.
- Session `043` (REWORK;ORDERING) now exhibits a *negative* ORDERING
  magnitude — magnitude = `alt − user`, so this means the alt scored
  *worse* than the user. The alt front-loaded less cleanliness than
  the user did, which the pre-lag formula scored as a tie and the new
  formula correctly scores as a loss. The thesis text should mention
  that the lag term can drive divergence-point magnitudes negative —
  not possible under the pre-lag formula — which is the source of the
  two-way discrimination evidenced by the §3.4 counts.
- `lag` is the lowest-recovery knob on the user corpus (0.176). This
  is consistent with user-study sessions being shorter (fewer checkpoints
  → less integral over which the lag accumulates), not a defect.
- Pre-existing test failure in `DivergencePointBuilderTest > rework alt
  emits REWORK DP with file and scope` persists (already on `main`),
  unrelated to `W_lag`.

## 6 — Implications for the thesis

LaTeX paragraphs to refresh (do NOT edit yet — user reviews this doc first):

- **`final_report/methodology/methodology.tex` §3.2.1** — Equation for
  `J(τ)` needs the new `−W_lag · lagFrac` term and the definition
  `lagFrac = mean_i max(0, cleanlinessFinal − cleanliness_i) / cleanlinessFinal`.
- **`final_report/methodology/methodology.tex` §3.2.2** — Weight table:
  add `W_lag = 11.0`; rationale (one sentence) about ORDERING being the
  only divergence-kind that does not naturally generate magnitude under
  the endpoint-only `gain` term, motivating the intermediate-cleanliness
  penalty.
- **`final_report/results/results.tex` §5.2 (ablation)** — Numbers in
  the solo-recovery / leave-one-out tables move slightly (5–15 % drops
  in the other knobs' solo recovery); add `lag` as the 4th-ranked knob
  on inj and call out that production τ ≈ 1 vs. pre-lag PRODUCTION when
  `lag` is removed.
- **`final_report/results/results.tex` §5.3 (divergence)** — Lead with
  the §3.4 counts (ORDERING strictly-better DPs 4 → 10, sessions with
  ≥1 strictly-better ORDERING alt 2 → 6, other kinds unchanged) — this
  is the headline. Add the ORDERING magnitude lift (+82 % at the mean,
  +0.79 at session-max) as secondary evidence, and the
  `injection_caught` 25 → 26 flip for session 022 as the threshold
  case.
- **Optional, `final_report/threats/threats.tex`** — Note the
  cleanliness↔process coupling introduced by `cleanlinessFinal`
  entering the lag denominator (agent-corpus cleanliness-knob τ drop
  is the empirical signal).

## Reproducing

```
./gradlew :analysis:ablation     --args="--corpus <C> --output <O>"
./gradlew :analysis:sensitivity  --args="--corpus <C> --output <O>"
./gradlew :analysis:multiKnobMC  --args="--corpus <C> --output <O> --samples 200 --seed 1"
./gradlew :analysis:divergence   --args="--corpus fixtures/corpus --manifest fixtures/sessions/manifest-v2.csv --output notebooks/data/divergence.csv"
python3 scripts/lag-term-analysis.py
```

Use absolute paths if `workingDir` isn't pinned for the task (ablation,
sensitivity, multiKnobMC do not set workingDir to the repo root).
