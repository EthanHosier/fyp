# Implementation plan: injection-variant evaluation experiment

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
   `AnalysisReport` with `divergencePoints`.
3. Score against ground truth:
   - **Detection (binary):** does *any* divergence with kind ==
     `injection.expectedKind` land within `[targetStep − 1,
     targetStep + 1]`?
   - **Top-K (binary):** is that divergence in the top-K (default K=5)
     by magnitude?
   - **Specificity:** how many false-positive divergences appear on
     $\tau'$ that don't appear on $\tau_0$ (the un-injected base)?
4. Also run the pipeline on $\tau_0$ as a control — log all
   divergences the detector emits on a clean trajectory. These are
   the "background false-positive rate".

Repeat each injection at 2 distinct step positions across the medium
fixture (so e.g. inject `ManualRenameSweep` at step 4 and step 9
separately). 10 patterns × 2 positions × 2 fixtures (small + medium)
+ 5 patterns on `F-real` = roughly 45 injected trajectories. Plus 3
controls.

## Metrics + reporting

For each detector kind $\kappa$:

- **Precision** = (# correctly flagged at target step) / (# divergences
  of kind $\kappa$ emitted).
- **Recall** = (# correctly flagged at target step) / (# injections of
  kind $\kappa$).
- **Top-K hit rate** = (# injections where the target divergence was
  in the top-K) / (# injections of kind $\kappa$).
- **Per-kind background FP rate** (from controls).

Headline table for the evaluation chapter:

| Kind | Precision | Recall | Top-5 hit | Background FP rate |
|------|-----------|--------|-----------|--------------------|
| Ordering | … | … | … | … |
| IDE replay | … | … | … | … |
| Hygiene | … | … | … | … |
| Rework | … | … | … | … |
| Local spike | … | … | … | … |
| **Macro avg** | … | … | … | … |

Plus a figure: cumulative-detection curve as K varies from 1 to 10.

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

1. `experiment-results.csv` — one row per (fixture, injection, position)
   with columns: fixture, injection_kind, target_step, detected (bool),
   in_top_k (bool), fp_count, detector_magnitude, baseline1_detected,
   baseline2_detected, baseline3_detected.
2. `precision-recall.csv` — per-kind aggregated.
3. `sensitivity-sweep/*.csv` — threshold sensitivity per kind.
4. Plots generated from the CSVs (matplotlib or Vega-Lite). Bar chart
   for per-kind precision/recall, line plot for top-K cumulative
   detection, line plot for threshold sensitivity.
5. Optional: `human-study-responses.csv` if the qualitative study is
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
