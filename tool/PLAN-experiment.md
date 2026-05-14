# Experiment plans

This file contains two complementary evaluation experiments:

1. **Sensitivity analysis + ablation** *(this section)* — defends the
   process-score *weights* against the "why these numbers?" critique.
   Cheap, static-recomputation only.
2. **Injection-variant divergence-detector evaluation** *(below)* —
   defends the *detector* by showing it flags known-bad patterns and
   beats the obvious baselines.

Both feed the evaluation chapter; both move the same rubric dimension
("Evaluation and Reflection", 20%) but address different attack
surfaces.

---

# Experiment 1: Sensitivity analysis + ablation for the process score

## Context

The process score is a weighted-sum composite over trajectory signals
(`W_BROKEN`, `W_SKIP_TESTS`, `W_COMMIT_GAP`, `W_GAIN`, `W_DEGRADATION`, `W_SMELL`) plus
the literature-backed cleanliness sub-score. Its *structure* is
defensible by literature analogy to SQALE / Quamoco / QMOOD; its
*magnitudes* are axiom-constrained heuristic priors (see
`PLAN-metric-justification.md` and `RESEARCH-metrics-weighting.md`,
§"Weight derivation"). To convert the priors into a defensible thesis
claim, two empirical experiments run over the fixture trajectories:

1. **Sensitivity analysis** — does the divergence-point ranking change
   when each weight is perturbed by ±50%?
2. **Ablation** — does each process-layer term actually do work, or is
   one redundant with the cleanliness layer or with other process
   terms?

Together these answer the strongest line of methodology-chapter
attack: *"why these numbers and not others?"*. The answer becomes
*"any constants satisfying C1–C4 give the same top-K within ±50%
perturbation, and every term changes the output non-trivially."*

The experiments are **cheap** because they reuse static metric
aggregates already computed and persisted per checkpoint — only the
final weighted-sum step (`DerivedMetricsRunner.computeProcess`) runs
per perturbation. Per `MEMORY.md` "Checkpoint cost profile":
build/test runs are expensive; static-metric recomputation is not.

## Fixture corpus (shared with Experiment 2)

Both experiments share the corpus assembled for the injection-variant
evaluation below (`F-small`, `F-medium`, `F-real`, plus the 45
injected trajectories). For weight-sensitivity testing, the 45
injected trajectories are particularly useful because each carries a
known-bad ground truth — sensitivity can be measured against *whether
the detector still flags the right step* under perturbation, not just
against arbitrary ranking-stability.

## Note on top-K

Both experiments below talk in terms of *"top-K divergence-point
ranking"*. **Top-K is an evaluation-time concept only.** The
production pipeline (`DivergencePointBuilder`, `AnalysisPipeline`)
returns every DP that clears its per-kind magnitude floor —
unsorted, untruncated. The dashboard renders them all. The
experiment harness does the sorting (`sortedByDescending(magnitude)`)
and the K-slicing when scoring against ground truth. Default K=5 for
both experiments; sensitivity over K∈{3, 5, 10} reported as
robustness checks. Justification for not truncating in production:
realistic session sizes produce <20 DPs, so truncation is wasted
engineering and obscures honest output — see Experiment 2 threats-
to-validity → "Performance and scalability".

## Experiment 1a — Sensitivity analysis

### Question

For each weight, does perturbing its magnitude by ±50% change the
top-K divergence-point ranking on the fixture corpus?

### Protocol

1. **Baseline.** Run `DivergenceDetector.detect(report)` on every
   fixture (clean + injected) with the production weights. Persist
   the top-5 divergence points per fixture as `baseline.json`.
2. **Perturb one weight at a time.** For each $w$ in the perturbation
   set:
   - **Process-layer weights:** $W_\text{BROKEN}$, $W_\text{SKIP\_TESTS}$,
     $W_\text{COMMIT\_GAP}$, $W_\text{GAIN}$, $W_\text{DEGRADATION}$,
     $W_\text{SMELL}$.
   - **Composite-layer weights:** the six within-cleanliness weights
     ($w_\text{cohesion}$, $w_\text{coupling}$, $w_\text{smells}$,
     $w_\text{duplication}$, $w_\text{readability}$, $w_\text{cognitive}$;
     all currently $1/6$ — see
     [`RESEARCH-cleanliness-metrics.md`](RESEARCH-cleanliness-metrics.md), §7).
   - **Readability-blend weights:** the five within-readability blend
     weights (line-length, indentation, identifier-length,
     single-letter, dictionary-word; all currently $0.20$ — see
     `RESEARCH-cleanliness-metrics.md`, §5).
   - **Sub-signal aggregation choices:** swap mean ↔ P90 for cohesion,
     coupling, and cognitive-complexity over the trajectory-touched
     set, and swap touched-file-rate ↔ whole-codebase-rate for
     duplication. These are *categorical* perturbations rather than
     ±50% scalar perturbations; report top-K stability under each
     swap independently.

   For scalar weights:
   - Set $w' = 0.5 \cdot w$. Re-run scoring + detection on every
     fixture. Record top-5 divergence points.
   - Set $w' = 1.5 \cdot w$. Re-run. Record top-5.
3. **Per-fixture similarity.** For each fixture and each perturbation,
   compare the perturbed top-5 to the baseline top-5 via:
   - **Kendall's τ** over the intersection of step-indices (rank
     correlation).
   - **Top-5 hit rate** (fraction of baseline top-5 still in perturbed
     top-5, ignoring order).
   - **Ground-truth retention** *(injected trajectories only)*: does
     the perturbed detector still flag the injected step in the top-K
     with the correct kind? This is the strongest evidence — it
     measures whether the *answer* changes, not just the ranking.
4. **Aggregate.** For each weight × perturbation pair, report the mean
   and 95% bootstrap CI of Kendall's τ, hit rate, and ground-truth
   retention rate across the corpus.

### Threshold for "stable"

- **τ ≥ 0.8 and hit rate ≥ 80% and ground-truth retention ≥ 90%** →
  weight is robust. Acceptable for thesis claim.
- **0.5 ≤ τ < 0.8** → weight is mildly sensitive. Justify magnitude
  more tightly or report as a limitation.
- **τ < 0.5** → weight is fragile. Discuss as a methodology
  limitation and either pin the magnitude to a tighter range or
  reformulate the term.

### Implementation

- `analysis/src/test/.../experiment/SensitivityExperiment.kt` — Kotlin
  test that wires a `ProcessScoreWeights` data class into
  `DerivedMetricsRunner` via constructor injection. Production default
  unchanged; the experiment passes perturbed copies.
- One JUnit `@Test` per weight, parameterised over ±50% perturbation.
  Output a `sensitivity-results.csv` for the evaluation chapter.

### Threats to validity

- **Fixture diversity.** If all fixtures come from the same Java
  style, sensitivity results don't generalise. Mitigation: ensure
  the corpus spans at least 3 distinct codebases (already required
  for Experiment 2).
- **Top-K choice.** K=5 is somewhat arbitrary. Report results for
  K ∈ {3, 5, 10} and confirm robustness conclusions don't pivot on K.
- **One-at-a-time perturbation.** Doesn't capture interactions.
  Optional follow-up: a small grid over the three most sensitive
  weights, time permitting.

## Experiment 1b — Ablation

### Question

Is each term in the process layer non-redundant — i.e. does removing
it materially change the divergence-point output?

### Protocol

Run the detector on every fixture under five score variants. Compare
the resulting top-K divergence rankings to the full-score baseline.

| Variant | Cleanliness | W_BROKEN | W_SKIP_TESTS | W_COMMIT_GAP | W_GAIN | W_DEGRADATION | W_SMELL |
|---------|-------------|----------|--------------|--------------|--------|---------------|---------|
| A1 — Cleanliness only         | yes | 0    | 0    | 0    | 0    | 0    | 0    |
| A2 — + safety penalties       | yes | full | full | full | 0    | 0    | 0    |
| A3 — + trajectory shape       | yes | 0    | 0    | 0    | full | full | 0    |
| A4 — + smell ledger           | yes | 0    | 0    | 0    | 0    | 0    | full |
| A5 — Full score (reference)   | yes | full | full | full | full | full | full |

W_COMMIT_GAP is folded into A2 alongside W_BROKEN and W_SKIP_TESTS:
all three are process-hygiene penalties (severity-ordered per axiom
C2) and live or die together as a category. If ablation finds the
A2 block redundant, that's a finding about the whole hygiene
category, not about W_COMMIT_GAP in isolation.

For each variant × fixture, compute Kendall's τ and top-5 hit rate
between the variant's top-5 and A5's top-5. On injected fixtures, also
report ground-truth retention (did the variant still flag the right
step?).

### What good ablation results look like

- **A1 vs A5** — large divergence. Proves the process layer earns its
  place over a snapshot-only score. This is the strongest result for
  the "novel composite over trajectory signals" claim.
- **A2 vs A5** and **A3 vs A5** — moderate divergence each. Proves
  both halves of the process layer (safety vs trajectory-shape) are
  non-redundant.
- **A4 vs A5** — measurable divergence. If τ ≈ 1.0, the smell ledger
  is redundant with the cleanliness layer's smell-count sub-signal,
  and the thesis result is *"we proposed five terms; ablation showed
  the smell ledger is redundant; the production score uses four."*
  That's a *stronger* finding than "all five matter" — it demonstrates
  that ablation was done honestly.

### Implementation

- `analysis/src/test/.../experiment/AblationExperiment.kt` — JUnit
  test running the five variants over the corpus and writing
  `ablation-results.csv`.
- The cleanest way to express "term off" is to expose
  `ProcessScoreWeights` (data class, refactor from Experiment 1a)
  and pass a variant copy with zeroed fields rather than introducing
  per-term feature flags.

### Threats to validity

- **Ablation doesn't isolate non-linearities.** A term may be
  redundant on the current corpus but useful on broader inputs.
  Discuss as future work.
- **Cleanliness layer subsumes some signals.** The smell ledger
  (W_SMELL) double-reads PMD; if redundant, that's an honest result,
  not a flaw.

## Deliverables (Experiment 1)

1. **`sensitivity-results.csv`** + a one-figure summary chart
   (Kendall's τ + 95% CI per weight × perturbation) for the
   evaluation chapter.
2. **`ablation-results.csv`** + a one-figure summary chart
   (Kendall's τ vs A5 per variant) for the evaluation chapter.
3. **Methodology-chapter prose** weaving the results into the
   weight-justification argument:
   > *"The constants W_GAIN=50, W_BROKEN=28, W_DEGRADATION=21,
   > W_SMELL=21, W_SKIP_TESTS=14, W_COMMIT_GAP=7 are one instantiation
   > satisfying axioms C1–C4. Sensitivity analysis (±50% perturbation, N≈20
   > fixtures) shows the top-5 divergence-point ranking is stable
   > (Kendall's τ ≥ 0.X) under perturbation of every weight except
   > Y. Ablation confirms each term is non-redundant — variant A_i
   > diverges from the full score by Kendall's τ ≈ Z_i."*
4. **One paragraph in the limitations subsection** acknowledging
   the axiomatic-prior framing and pointing at the corpus diversity
   threat.

## Sequencing (Experiment 1)

- **Day 1.** Refactor `DerivedMetricsRunner` to accept a
  `ProcessScoreWeights` parameter (constructor-injected; production
  default unchanged). Verify existing tests still pass.
- **Day 2.** Implement `SensitivityExperiment.kt`. Run on the
  available fixtures, generate CSV + chart. (Can run before
  Experiment 2's full corpus is built — uses whatever fixtures
  exist.)
- **Day 3.** Implement `AblationExperiment.kt`. Run, generate CSV +
  chart.
- **Day 4.** Methodology-chapter prose + figure embedding +
  threats-to-validity paragraph.

Total: ~4 days, all cheap static recomputation. Can run in parallel
with Experiment 2 development.

## Out of scope (Experiment 1)

- **Multi-language fixtures.** Java-only for v1; consistent with the
  rest of the thesis scope.
- **AHP-style pairwise elicitation** as an alternative weight-derivation
  method. The axiom-constrained framing is the contribution; AHP
  would be an alternative methodology paper.
- **Grid-search over the joint weight space.** One-at-a-time
  perturbation is the affordable first pass; a joint sensitivity
  study is future work.
- **Learned weights from a labelled corpus of "good" / "bad"
  trajectories.** No such labelled corpus exists at thesis scale;
  building one is itself a research contribution and out of scope.

---

# Experiment 2: Injection-variant evaluation experiment

## Context

The thesis question is *"given start + end, can we evaluate the
developer's process and identify divergence points where a better
process was available?"* The implementation answers this via the
`DivergenceDetector` (see `plans/PLAN-divergence-detection.md`). The
present plan is the **evaluation experiment** that demonstrates the
detector works.

The pitch in one sentence: *take a clean trajectory, deliberately
inject a known sub-optimal pattern at a known step, run the pipeline,
and check whether the detector flagged that step with the correct
kind*. Repeating this at scale yields per-kind precision/recall and a
top-K hit rate — the kind of quantitative result the MEng rubric needs
to move "Evaluation and Reflection" out of the 40–59 band.

The experiment also produces a strong baseline-comparison story for
free: endpoint-only metric Δ catches 0/10 of these patterns by
construction (every injection preserves the start and end states), so
the headline result writes itself.

## What "divergence" means in this experiment

A **divergence** is a specific step $k$ in a recorded trajectory where
the developer made a process choice that the detector should flag,
because there is a verifiably better way to reach the same end-state.
"Better" is defined per detector kind:

- **Ordering** — a permutation of the same refactorings scores higher
  cumulatively at step $k$.
- **IDE replay** — the IDE could have done in one step what the user
  spread across several manual edits.
- **Hygiene** — process predicate fires (no commit, tests red, build
  red) regardless of any alt.
- **Rework** — step $k$ is the signed inverse of some earlier step.
- **Local spike** — step $k$ caused a transient process-score dip that
  alt orderings avoid.

Each injection below maps to exactly one kind.

## The 10 injection patterns

Two per detector kind. Each is mechanically injectable into a clean
fixture trajectory at a specified step $k$, with a clear ground truth.

### Ordering (kind 1)

**1. Rename Field, then Extract Method using it.**
Clean order: extract first (helper reads `oldName`), then rename
(rewrites the helper's body too — one rename touches everything).
Worse order (the user trajectory): rename first, then extract a helper
that reads `newName`. The reverse permutation — extract-then-rename —
forces the rename to also rewrite the new helper, increasing churn and
the cognitive-complexity dip. Inject the worse order. **Ground truth:**
the extract step.

**2. Move Class, then Extract Interface from it.**
Better: extract interface first (local change), then move both
artefacts. Worse: move first (rewrites all imports across the project),
then extract interface (rewrites those same imports again to add the
new interface). Inject the worse order. **Ground truth:** the move
step.

### IDE replay (kind 2)

**3. Manual Extract Method via cut-and-paste.**
User trajectory: 3 commits — (a) cut 20 lines and paste into a new
method body, (b) replace the original site with a call to it, (c) fix
up the parameter list because one parameter was missed. The IDE's
Extract Method does all of this in one operation with correct
parameter inference. **Ground truth:** the first of the 3 commits.

**4. Manual Rename via find-and-replace.**
User renames `User` → `Account` with a sed-like sweep. Misses comments
+ a string-literal reference + one wildcard import. Three follow-up
fix commits. IDE Rename handles all of those atomically. **Ground
truth:** the first sweep commit.

### Hygiene (kind 3)

**5. Long no-commit stretch.**
Take a clean fixture, delete all `GIT_COMMIT` events between checkpoint
4 and checkpoint 12. The detector should flag step 4 (start of the
drought) when the predicate is "no commit since step $k - n$".
**Ground truth:** step 4.

**6. Tests go red and stay red.**
Mutate the `TEST_RUN_FINISHED` event payload at step 5 to status =
FAILED. Leave subsequent test events untouched (they stay red because
no fix is applied). **Ground truth:** step 5 (regression onset).

**7. Build broken across multiple checkpoints.**
Flip `BUILD_FINISHED` status to FAILED at steps 4, 7, and 9. Even-
spaced breakage signals systemic process issues rather than a one-off.
**Ground truth:** step 4 (first breakage); top-K should also include
steps 7 and 9.

> Two hygiene-flavoured injections plus the build-breakage one gives
> three hygiene patterns. Keep all three; hygiene is the easiest
> predicate to mechanically inject and so cheap to evaluate at scale.

### Rework (kind 4)

**8. Extract-then-Inline.**
At step 3 the user extracts `getDiscount()` (adds ~15 lines, +1
method). At step 7 they inline it back (removes the same ~15 lines,
−1 method). Net zero churn but two checkpoints of disruption. The
diff at step 7 is the signed inverse of the diff at step 3 over the
same line set. **Ground truth:** step 7 (the undo).

**9. Field added then removed.**
At step 2 the user adds `private LocalDateTime lastAccessed`, plus its
getter, setter, and a constructor parameter. At step 6 they remove all
of it (decided not to expose it). Detector matches the add-then-remove
pair via diff-inversion overlap. **Ground truth:** step 6.

### Local complexity spike (kind 5)

**10. Inline Method causing transient cognitive-complexity spike.**
At step 3 the user inlines `formatDate()` into 5 call sites. Cognitive
complexity at those 5 methods spikes by +6 each; the process score
dips by ~12 points. At step 6 the user does an unrelated refactoring
that absorbs the inlined logic into a different helper, recovering the
score. The dip exists only in the user's trajectory — a reorder alt
that delays or skips the inline keeps the score flat. **Ground truth:**
step 3 (the spike onset).

## Injection harness

A test-only Kotlin module that mutates fixture trajectories:

**New: `analysis/src/test/kotlin/com/github/ethanhosier/analysis/eval/InjectionHarness.kt`**

```kotlin
sealed interface Injection {
    val targetStep: Int
    val expectedKind: DivergenceKind
}
data class RenameThenExtract(override val targetStep: Int) : Injection { ... }
data class MoveThenExtract(override val targetStep: Int) : Injection { ... }
data class ManualExtractMethod(override val targetStep: Int) : Injection { ... }
data class ManualRenameSweep(override val targetStep: Int) : Injection { ... }
data class NoCommitStretch(override val targetStep: Int, val length: Int) : Injection { ... }
data class TestsGoRed(override val targetStep: Int) : Injection { ... }
data class BuildBroken(override val targetStep: Int, val recurrences: List<Int>) : Injection { ... }
data class ExtractThenInline(override val originStep: Int, override val targetStep: Int) : Injection { ... }
data class FieldAddedRemoved(override val originStep: Int, override val targetStep: Int) : Injection { ... }
data class InlineSpike(override val targetStep: Int) : Injection { ... }

object InjectionHarness {
    fun inject(base: Trace, injection: Injection): Trace = ...
}
```

Each `inject(base, injection)` produces a deterministic mutated trace
$\tau'$. Mutations operate at the *event level* (`TraceEvent` list +
`FileSnapshot` contents) rather than at the SHA level, so the shadow-
repo builder still works end-to-end on the perturbed trace.

For ordering injections, the harness picks a specific known-valid pair
of refactorings from the base fixture and swaps them in the event list
(keeping all other events). For IDE-replay injections, the harness
takes an existing single IDE refactoring event and splits it into N
manual `EDIT_BURST` events with hand-crafted file snapshots that mimic
manual application.

## Fixtures

Three base fixtures, each a clean trajectory with no divergences:

1. **F-small.** ~6 checkpoints, 1 short class, 2 refactorings (e.g.
   one Extract Method, one Rename). For sanity tests.
2. **F-medium.** ~15 checkpoints, 3–4 classes, ~6 refactorings of
   varying kinds. The main evaluation surface.
3. **F-real.** A captured session from a real user (recruit one
   participant; record them doing a 30-minute refactoring task on a
   small open-source utility class). Bigger n is great but one is
   enough to demonstrate the detector works on non-synthetic data.

`F-small` and `F-medium` are hand-authored (extend the existing
`analysis-report.json` fixture in the dashboard or build dedicated
ones via `TraceLoader` test helpers). `F-real` needs ~1 hour of
participant time.

## Experimental procedure

For each (fixture, injection) pair:

1. Apply `InjectionHarness.inject(base, injection)` to produce
   $\tau'$.
2. Run `AnalysisPipeline.run(τ')` end-to-end, get the
   `AnalysisReport` with both `alternativeTrajectories` (all alts the
   synth produced, regardless of magnitude) and `divergencePoints`
   (only alts above their per-kind magnitude floor — `MIN_PROCESS_DELTA
   = 3.0` for ORDERING/IDE_REPLAY, `MIN_REWORK_LINES = 2` for REWORK).
3. Score against ground truth on **two tiers** (see "Two-tier success
   criterion" below):
   - **Tier 1 — Pattern detection (binary):** does *any*
     `AlternativeTrajectory` of the right shape (kind / step / file)
     for `injection.expectedKind` exist within `[targetStep − 1,
     targetStep + 1]`? Tests capability — did the underlying machinery
     find the pattern at all?
   - **Tier 2 — Divergence-point flag (binary):** does the report's
     `divergencePoints` list include a flagged divergence with kind ==
     `injection.expectedKind` within the same step window? Tests the
     user-facing behaviour — did the system actually surface it above
     threshold?
   - **Top-K (binary, on Tier 2):** is that flagged divergence in the
     top-K (default K=5) by magnitude?
   - **Specificity:** how many false-positive flagged divergences
     appear on $\tau'$ that don't appear on $\tau_0$ (the un-injected
     base)?
4. Also run the pipeline on $\tau_0$ as a control — log all
   divergences the detector emits on a clean trajectory. These are
   the "background false-positive rate".

Repeat each injection at 2 distinct step positions across the medium
fixture (so e.g. inject `ManualRenameSweep` at step 4 and step 9
separately). 10 patterns × 2 positions × 2 fixtures (small + medium)
+ 5 patterns on `F-real` = roughly 45 injected trajectories. Plus 3
controls. Within each pattern, vary the injection magnitude across
**loud / borderline / sub-threshold** strengths (see "Engineering
borderline injections" below) — this is what makes Tier 1 vs Tier 2
gap meaningful and feeds Experiment 1's sensitivity analysis.

## Two-tier success criterion

A pattern can be **detected** without being **flagged**. The
underlying machinery (`ReorderSynthesiser`, `AlternativeTrajectoryRunner`,
`HygieneDetector`, AST-hash rework matcher) generates an alt
unconditionally; `DivergencePointBuilder` then applies per-kind
magnitude floors and only flags alts above the floor. Both states are
correct behaviour — Tier 1 measures *capability*, Tier 2 measures
*calibrated UX*. Reporting both lets us distinguish two failure modes
that look identical if conflated:

- **Tier 1 fail.** The synth/predicate never found the pattern. This
  is a *real* miss — the detector cannot identify this category of
  process issue.
- **Tier 1 pass, Tier 2 fail.** The synth/predicate found the pattern
  but its magnitude fell below the magnitude floor (e.g. an ordering
  alt with delta = 1.5 points). This is *correct* suppression of a
  low-signal finding, and conflating it with a Tier 1 fail would
  unfairly penalise the system for working as designed.

**HYGIENE collapses both tiers.** `COMMIT_GAP` emits regardless of
magnitude and `TESTS_SKIPPED` has no separate magnitude floor; if the
predicate fires, the divergence point is flagged. So Tier 1 == Tier 2
for hygiene rows — report a single value with a footnote.

## Engineering borderline injections

For each pattern, inject at three magnitude levels by varying the
surrounding context (class size, churn, number of touched
methods). The injection-harness `data class` carries an explicit
strength tag so the eval driver can stratify results:

```kotlin
enum class Strength { LOUD, BORDERLINE, SUBTHRESHOLD }
data class ManualRenameSweep(
    override val targetStep: Int,
    val strength: Strength,
) : Injection { ... }
```

Approximate targets per kind:

| Kind | Loud | Borderline | Sub-threshold |
|------|------|------------|---------------|
| ORDERING / IDE_REPLAY (`MIN_PROCESS_DELTA = 3.0`) | delta ≈ 10+ | delta ≈ 3.5–5.0 | delta ≈ 1.5–2.5 |
| REWORK (`MIN_REWORK_LINES = 2`) | ≥10 reverted lines | 2–4 lines | 1 line (boundary case) |
| HYGIENE COMMIT_GAP (`MIN_COMMIT_GAP = 6`) | gap ≥ 10 | gap = 6–8 | gap = 4–5 |

Tier 1 detection rate should approach 100% across all strengths
(synths/predicates aren't strength-sensitive). Tier 2 flag rate
should approach 100% on loud, ~80–100% on borderline, ~0% on
sub-threshold. Sub-threshold injections double as a *false-positive
test* — the detector should not flag them, and doing so correctly is
a Tier-2 success in the opposite direction (recorded as
`expected_unflagged_and_unflagged = true`).

## Metrics + reporting

For each detector kind $\kappa$:

- **Tier 1 detection rate** = (# injections where an alt of kind
  $\kappa$ was generated at the target step) / (# injections of kind
  $\kappa$).
- **Tier 2 precision** = (# flagged divergences correctly at target
  step) / (# flagged divergences of kind $\kappa$ emitted on $\tau'$).
- **Tier 2 recall** = (# flagged divergences correctly at target
  step) / (# injections of kind $\kappa$, **excluding sub-threshold**
  — sub-threshold injections are explicitly expected to remain
  unflagged).
- **Top-K hit rate** = (# injections where the target flagged
  divergence was in the top-K) / (# injections of kind $\kappa$).
- **Sub-threshold suppression rate** = (# sub-threshold injections
  correctly *not* flagged) / (# sub-threshold injections). Should
  approach 100%.
- **Per-kind background FP rate** (from controls on $\tau_0$).

Headline table for the evaluation chapter:

| Kind | Tier 1 detection | Tier 2 precision | Tier 2 recall (loud + borderline) | Top-5 hit | Sub-threshold suppression | Background FP |
|------|------------------|------------------|-----------------------------------|-----------|---------------------------|---------------|
| Ordering    | … | … | … | … | … | … |
| IDE replay  | … | … | … | … | … | … |
| Hygiene*    | … | (collapsed) | … | … | n/a | … |
| Rework      | … | … | … | … | … | … |
| Local spike | … | … | … | … | … | … |
| **Macro avg** | … | … | … | … | … | … |

*Hygiene rows: Tier 1 == Tier 2 (no separate magnitude floor); the
"Tier 2 precision" column is the same as Tier 1 detection.

Plus two figures:
- **Cumulative-detection curve** as K varies from 1 to 10 (Tier 2).
- **Tier 1 vs Tier 2 gap chart** per kind × strength — bars showing
  100% Tier 1 across the board, and Tier 2 dropping to 0% at the
  sub-threshold band. This visualisation directly justifies the
  threshold calibration.

## Baseline comparisons

Three baselines, each run on the same 45 injected trajectories. None
of them require new infrastructure — they're all derivable from the
existing `AnalysisReport`.

1. **Endpoint-only Δ.** Predicate: "is `report.checkpoints.last()
   .derivedMetrics.process.total ≥ report.checkpoints.first()`?"
   Boolean per trajectory. Cannot flag a step at all. **Expected
   result:** 0/10 detection — every injection preserves the endpoint
   by construction. This is the headline anti-result that motivates the
   whole thesis.
2. **Per-step regression flag.** Predicate: "flag every step $k$ where
   $J(\tau_k) < J(\tau_{k-1}) - \theta$". A naive per-step view of
   process. **Expected result:** catches local spikes (kind 5), maybe
   some hygiene-flavoured drops; misses ordering, IDE-replay, and
   rework entirely (those don't manifest as monotone score drops).
3. **TrajectoryAdvisor's existing rules.** Run the 7-rule advisor on
   each $\tau'$. **Expected result:** catches some hygiene patterns
   (the build/test/commit ones it already has rules for) but
   trajectory-wide, not located to a step. Cannot flag rework,
   ordering, IDE replay, or local spikes per-step.

Report a per-kind comparison: detector × baseline matrix.

## Optional: small human study

Once the quantitative results are in, run a small qualitative
follow-up. 4–6 participants (fellow MEng / supervisor's group). For
each participant, show 3 injected trajectories. For each trajectory,
show two outputs side-by-side:

- The detector's top-3 divergence points with explanations.
- A baseline output (see "what counts as a baseline output" below).

Ask:
- "Which output better helps you understand what went wrong in this
  refactoring session?"
- "Which divergence point would be most useful to act on first?"
- Free-text: "Was anything unclear or misleading?"

Even with n=5 this adds an "Evaluation" section the rubric explicitly
rewards at the 70%+ band ("appropriate quantitative and/or qualitative
methods"). Don't oversell — frame it as supplementary qualitative
evidence, not a primary result.

### What counts as a "baseline output"

A *baseline* is another way of producing some output from the same
trajectory, used as a comparison point for the detector. In the human
study specifically, the baseline output is whatever a *simpler/dumber*
analysis would have produced for the same input — shown alongside the
detector's output so participants can judge which is more useful.

The three baselines defined above in "Baseline comparisons" each work
in this role. Pick **baseline #2 (per-step regression flag)** for the
qualitative study: it's the strongest honest competition (locates
issues to steps, gestures at "something happened here", but offers no
reasoning or counterfactual), so beating it is the most meaningful win.
Baseline #1 (endpoint-only) is too easy to beat (it has no per-step
output at all); baseline #3 (TrajectoryAdvisor) is uncomfortably close
to the detector on some patterns and harder to read side-by-side.

Concretely, what a participant sees per trajectory (A/B labels
randomised so they don't know which is yours):

```
┌────── Trajectory chart (same for both columns) ──────┐
└───────────────────────────────────────────────────────┘

┌────── System A ──────────┐  ┌────── System B ──────────┐
│ TOP 3 ISSUES             │  │ STEPS WHERE SCORE DROPPED│
│                          │  │                          │
│ 1. Step 4 (ORDERING)     │  │ • Step 4: -8 points      │
│    Rename-then-Extract.  │  │ • Step 7: -3 points      │
│    Alt ordering would    │  │ • Step 11: -5 points     │
│    have scored +9 pts.   │  │                          │
│    [view alt on chart]   │  │                          │
│                          │  │                          │
│ 2. Step 9 (REWORK)       │  │                          │
│    Order.java ended up   │  │                          │
│    identical to step 3.  │  │                          │
│    Wasted 38 LOC churn.  │  │                          │
│                          │  │                          │
│ 3. Step 12 (HYGIENE)     │  │                          │
│    No commit since step  │  │                          │
│    3 — 9 checkpoints.    │  │                          │
└──────────────────────────┘  └──────────────────────────┘
```

The detector's output offers locations, *kinds* (what went wrong), and
*counterfactuals* (what could have been done). The baseline offers
locations only. The human-study question becomes: do the kinds and
counterfactuals materially help the participant understand the
session?

### Interpreting the responses

You're not claiming statistical significance with n=5. You're looking
for a *pattern*:

- If 4–5 of 5 participants pick the detector consistently → strong
  qualitative signal that the structured output is more useful.
- If the split is 3/2 or 2/3 → honest "the qualitative evidence is
  mixed", and you report exactly that. A mixed result is still a
  result; the rubric rewards honesty.
- Free-text comments should converge on themes. If they don't, that's
  itself a finding worth writing up.

Frame all of this as *illustrative qualitative validation* in the
evaluation chapter, sitting alongside (not in place of) the
quantitative precision/recall results. Do not aggregate participant
counts into percentages — at n=5, "80% preferred the detector" is
worse than "4 of 5 preferred the detector".

## Threats to validity

The rubric explicitly rewards "anticipates possible objections" at
70%+. Address head-on:

- **Synthetic-data bias.** Most injections are constructed on hand-
  authored fixtures. Mitigation: `F-real` provides at least one
  non-synthetic surface; future work could mine open-source repos.
- **Author-as-injector bias.** I designed both the detector and the
  injections — risk of injecting only what I know the detector can
  catch. Mitigation: include at least one "wild" pattern outside the
  five-kind taxonomy and report that the detector misses it (honesty
  beats inflated precision).
- **Threshold sensitivity.** The detector uses thresholds
  ($\theta_\text{ord}$, $\theta_\text{rew}$, etc.). Different
  thresholds give different results. Mitigation: report sensitivity
  analysis — sweep each threshold across a sensible range and plot
  precision/recall curves.
- **Single language.** Java only. Detector design is language-agnostic
  but the harness is JDT-bound. Mitigation: explicit scope statement;
  future-work note.
- **Process-score weight validity.** The process score's weights are
  not empirically validated. The detector's magnitudes inherit that
  weakness. Mitigation: acknowledge; cite the methodology chapter's
  literature-based weight choices; flag as future work.
- **Small participant pool for human study.** n=5 is too small for
  statistical significance. Mitigation: report individual responses
  rather than aggregated significance claims; frame as illustrative.
- **Performance and scalability.** The detector enumerates *all*
  alternative trajectories the synth produces and *all* divergence
  points above the magnitude floor, with no top-K truncation in the
  production pipeline. (Top-K is an evaluation-time concept — the
  experiment harness sorts and slices when scoring against ground
  truth, but the score itself doesn't truncate.) On bounded session
  sizes (≤30 checkpoints per fixture in this corpus) the pipeline
  completes in under a minute end-to-end; the dominant cost is
  alternative-trajectory generation (`ReorderSynthesiser`,
  `AlternativeTrajectoryRunner`), not divergence-point assembly.
  Scaling to session sizes beyond ~100 checkpoints would require a
  top-K-aware enumeration strategy that prunes low-magnitude alts
  before full synthesis. This is deliberately out of scope: the
  research question concerns the *content* of process-quality
  evaluation, not the scaling characteristics of the implementation.
  Mitigation: report measured wall-clock timings on the evaluation
  corpus (see "Output artefacts" → `timings.csv` and the headline
  timings table) so the trade-off is visible rather than hidden.

## File-level changes

**New:**
- `analysis/src/test/kotlin/com/github/ethanhosier/analysis/eval/InjectionHarness.kt`
- `analysis/src/test/kotlin/com/github/ethanhosier/analysis/eval/InjectionHarnessTest.kt`
  — tests for the harness itself (verify each injection deterministically
  produces the expected event-level mutation).
- `analysis/src/test/kotlin/com/github/ethanhosier/analysis/eval/DivergenceExperimentRunner.kt`
  — the main experiment driver. Runs all (fixture, injection) pairs,
  collects results, writes a CSV/JSON summary for plotting.
- `analysis/src/test/resources/fixtures/F-small/` — hand-authored small
  fixture.
- `analysis/src/test/resources/fixtures/F-medium/` — hand-authored
  medium fixture.
- `analysis/src/test/resources/fixtures/F-real/` — captured session
  (after participant recording).
- `analysis/src/test/kotlin/com/github/ethanhosier/analysis/eval/baselines/`
  — directory for the three baseline implementations.

**Reused:**
- `AnalysisPipeline.run` for end-to-end execution.
- `DivergenceDetector.detect` (built per `PLAN-divergence-detection.md`).
- `TraceLoader` test helpers for fixture construction.
- `TrajectoryAdvisor` for baseline #3.

## Output artefacts

After running:

1. `experiment-results.csv` — one row per (fixture, injection,
   position, strength) with columns: fixture, injection_kind,
   target_step, strength (loud/borderline/subthreshold),
   tier1_detected (bool), tier2_flagged (bool), in_top_k (bool),
   fp_count, detector_magnitude, baseline1_detected,
   baseline2_detected, baseline3_detected.
2. `precision-recall.csv` — per-kind aggregated, with separate
   Tier 1 / Tier 2 columns and a `subthreshold_suppression_rate`
   column.
3. `sensitivity-sweep/*.csv` — threshold sensitivity per kind.
4. `timings.csv` — one row per fixture with columns: fixture,
   checkpoints, refactoring_steps, alts_generated, dps_flagged,
   pipeline_wall_ms, alt_synth_ms, divergence_build_ms. Breaking
   `alt_synth_ms` and `divergence_build_ms` out lets the evaluation
   chapter show that the cost lives in alt synthesis, not in
   divergence-point assembly — which justifies the "no top-K
   truncation" scope decision. Implemented as wall-clock
   `System.nanoTime()` brackets around `AlternativeTrajectoryRunner`,
   `ReorderSynthesiser`, and `DivergencePointBuilder.build` in
   `AnalysisPipeline.run`; results logged at INFO and written to the
   CSV by the experiment driver.
5. Plots generated from the CSVs (matplotlib or Vega-Lite). Bar chart
   for per-kind precision/recall, line plot for top-K cumulative
   detection, line plot for threshold sensitivity. Plus a small
   headline timings table — 3–4 representative fixtures showing
   wall-clock cost growing with checkpoint count, embedded in the
   evaluation chapter alongside the "Performance and scalability"
   threats-to-validity paragraph.
6. Optional: `human-study-responses.csv` if the qualitative study is
   run.

These artefacts go straight into the evaluation chapter as
tables/figures.

## Sequencing

Roughly 5–7 days. Order matters — get a minimal end-to-end loop
working before adding patterns.

1. **Day 1.** Build `InjectionHarness` skeleton + 2 simplest
   injections (NoCommitStretch, TestsGoRed). End-to-end loop:
   inject → pipeline → detector → check ground truth. Even with
   2 patterns this proves the experiment harness works.
2. **Day 2.** Add the remaining hygiene injection (BuildBroken) + both
   rework injections (ExtractThenInline, FieldAddedRemoved). These
   are the cheapest because they're pure event-list mutations.
3. **Day 3.** Add the two IDE-replay injections (ManualExtractMethod,
   ManualRenameSweep). Slightly harder because they require crafting
   intermediate file snapshots.
4. **Day 4.** Add the two ordering injections (RenameThenExtract,
   MoveThenExtract). Hardest because they require coordinating
   refactoring events that the reorder synth can then enumerate
   alternatives for.
5. **Day 5.** Add the local-spike injection (InlineSpike). Wire the
   three baselines. Run the full experiment on F-small + F-medium.
   Generate the headline table.
6. **Day 6.** Threshold sensitivity sweep. Plots. Threats-to-validity
   writeup.
7. **Day 7** *(optional, but high-value if time permits).* Capture
   F-real with one participant. Run the 5 highest-leverage injections
   on it. Run the small human study (4–6 participants × 3 trajectories,
   ~20 min each).

## What this lifts in the rubric

Mapping back to `MENG_PROJECT_ASSESSMENT_CRITERIA.md`:

- **Evaluation and Reflection (20%).** Moves this dimension from
  ~50% to ~70%. The injection-variant story is the single most
  effective thing you can do here — the rubric rewards "appropriate
  quantitative methods", "critically interrogates own work",
  "trade-offs / threats to validity / alternative explanations".
  This plan delivers all three.
- **Execution and Technical Quality (50%).** Pushes a few points
  higher because the experiment validates that the detector actually
  works, not just that it compiles. The rubric rewards "rigorous
  experimental design" at 70–84%.
- **Communication (15%).** Gives you concrete tables, figures, and
  numbers for the evaluation chapter — far easier to write than
  prose-only evaluation.
- **Framing (15%).** Closes the loop with the thesis question. The
  "endpoint-only catches 0/10" headline result is exactly the kind of
  pointed empirical comparison that justifies the framing.

Concrete arithmetic: every 10 percentage points on Evaluation moves the
final mark by 2 points. Going from 50% to 70% on Evaluation is the
difference between a low first and a comfortable first.

## Risks

- **Time pressure.** Day 7 is optional for a reason — if injections
  4 (ordering) take longer than budgeted, drop F-real and the human
  study. Days 1–5 alone are enough for a credible evaluation.
- **Ordering injections are the hardest.** They require the reorder
  synth to *actually find* the alternative ordering and score it
  higher. If the synth doesn't generate the alt for the injected
  trajectory, the detector can't flag it. Mitigation: when designing
  ordering injections, pick patterns the synth is *known* to handle
  (the `RenameThenExtract` pair has been tested in the reorder
  tests).
- **Human-study latency.** Recruiting + scheduling takes calendar
  time even if it doesn't take work-hours. Send the recruitment
  message on Day 1, not Day 7.
- **Baseline #3 (TrajectoryAdvisor) may overlap with the detector.**
  The advisor's `REORDER_BEATS_USER` and `BUILD_OFTEN_BROKEN` rules
  cover ground the detector also covers. Mitigation: when comparing,
  report "located to a step" as a distinguishing capability — the
  advisor cannot locate; the detector can.
