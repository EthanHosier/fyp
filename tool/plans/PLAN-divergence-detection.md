# Implementation plan: divergence-point detection as a first-class contribution

## Context

The thesis question is "given start + end, can we evaluate the
developer's process and identify *divergence points* where a better
process was available?" The codebase already produces all the *data*
needed to answer this — reorder-synthesised alt trajectories, single-
step IDE replays, per-checkpoint derived metrics, the TrajectoryAdvisor's
trajectory-wide rules — but no named algorithm currently emits a
ranked, located, explained list of divergence points. The comparison
is implicit (advice rule `REORDER_BEATS_USER` fires *trajectory-wide*
without saying which step lost ground).

This plan turns divergence detection into a first-class algorithm with
a formal definition, a typed schema, an explainable output, and a
dashboard surface. It directly addresses the largest gap flagged in
`HONEST_REVIEW.md`: making the thesis-question answer visible in the
implementation rather than implicit in the data.

## Divergence kinds

Four categories, each mapping to a distinct action the developer could
have taken instead. The taxonomy is grounded in the operations the
system can structurally distinguish from a trajectory — typed
refactoring operations, version-control events, and per-file diffs —
rather than ad-hoc heuristics.

| # | Kind                                          | Action the user could have taken | What it tells the user | Existing data source |
|---|-----------------------------------------------|----------------------------------|------------------------|----------------------|
| 1 | **Ordering**                                  | Reorder the same operations | "Doing these same N refactorings in order Y→X→Z would have scored higher" | `ReorderSynthesiser` — `AlternativeTrajectory` with `stepIndexes.size ≥ 2` |
| 2 | **IDE replay (automatic Refactoring vs IDE)** | Use the IDE's automated refactoring tool | "You did this manually across 3 commits; the IDE could have done it cleanly in one" | `AlternativeTrajectoryRunner` — `AlternativeTrajectory` with `stepIndexes.size == 1` |
| 3 | **Hygiene**                                   | Commit / test / keep the build green more often | "No commit for 8 checkpoints", "tests went red at step 5 and stayed red", "build broke twice in a row" | Existing user-trace events + `metrics.tests` + `metrics.gradleBuild` |
| 4 | **Detour / rework**                           | Skip the operations that cancelled each other out | "Step 4 added 60 lines that step 7 deleted — wasted churn" | Shadow repo + `JavaFileAstHasher` (see `PLAN-rework-detection.md`) |

Today only #1 and #3 are partially surfaced (and even those only as
trajectory-wide advice). #2 and #4 are unsurfaced.

**Why exactly these four.** Each kind has a crisp predicate, strong
evidence (`SIMULATED` for Ordering and IDE Replay via the existing
alt-trajectory machinery, structural pattern for Rework via AST-hash
equality, behavioural for Hygiene via trace events), and a clear
actionable recommendation. An earlier draft included a fifth kind
("local complexity spike") that turned out to be redundant: when an
alt covers the window, Ordering already captures the divergence; when
no alt is available, the evidence collapses to "score dipped and
recovered, we don't know why", which is not actionable. Dropping it
yields a stronger taxonomy, not a weaker one. The narrow class of
"transient disruption absorbed by later refactoring" patterns the
spike kind would have caught is documented as future work.

## Formal definition (for the methodology chapter)

For each kind, give the report a one-line predicate. The unified
shape is:

> A *divergence point* is a tuple $(k,\,\kappa,\,\delta,\,e)$ where $k$ is
> a step index in the user trajectory $\tau$, $\kappa$ is a divergence
> kind, $\delta \in \mathbb{R}_{\geq 0}$ is the magnitude (units depend
> on $\kappa$), and $e$ is a human-readable explanation.

Per-kind predicates:

- **Ordering.** Step $k$ is an ordering divergence iff some alt $\tau'$
  covers a window containing $k$ and
  $J(\tau'_{1..k}) - J(\tau_{1..k}) > \theta_{\text{ord}}$
  where $J$ is the cumulative process score.
- **IDE replay.** Step $k$ is an IDE-replay divergence iff
  `AlternativeTrajectoryRunner` synthesised a single-step alt for step
  $k$ and $J(\text{alt}) - J(\text{userToSha}) > \theta_{\text{rep}}$.
- **Hygiene.** Step $k$ is a hygiene divergence iff some predicate $H_i$
  on the prefix $\tau_{1..k}$ fires (no commit since step $k - n$;
  tests red at step $k$; build red at step $k$ after green at $k-1$;
  etc.).
- **Rework.** Step $k$ is a rework divergence with respect to file $f$
  iff $f$'s AST hash at the post-state of step $k$ equals $f$'s AST
  hash at the pre-state of some earlier step $k' < k$ that also
  touched $f$. (See `PLAN-rework-detection.md` for the full predicate
  including the optional truncated-trajectory counterfactual upgrade.)

Each threshold $\theta_*$ is a tunable config; pick conservative
defaults (e.g. 3-point process-score gap, 6-checkpoint commit gap).

## Algorithm

Pseudocode (presentable in the report's methodology chapter):

```
Input:  AnalysisReport report
Output: List<DivergencePoint>

dps = []

// 1. Ordering divergences from reorder synth.
for alt in report.alternativeTrajectories where alt.stepIndexes.size >= 2:
    user_steps_in_window = report.checkpoints filter step ∈ alt.stepIndexes
    for k in alt.stepIndexes:
        δ_k = alt.altCheckpoints[k].process.total
             - user_checkpoint_for(k).process.total
        if δ_k > θ_ord:
            dps += DivergencePoint(
                stepIndex=k, kind=ORDERING, magnitude=δ_k,
                explanation=templ_ordering(k, alt, δ_k))

// 2. IDE-replay divergences from single-step alts.
for alt in report.alternativeTrajectories where alt.stepIndexes.size == 1:
    k = alt.stepIndexes[0]
    Δ = alt.altCheckpoints[0].process.total
       - user_checkpoint_for(k).process.total
    if Δ > θ_rep:
        dps += DivergencePoint(
            stepIndex=k, kind=IDE_REPLAY, magnitude=Δ,
            explanation=templ_replay(k, alt, Δ))

// 3. Hygiene divergences per checkpoint.
for k, cp in report.checkpoints.withIndex():
    for predicate H_i in HYGIENE_PREDICATES:
        if H_i(report, k):
            dps += DivergencePoint(
                stepIndex=k, kind=HYGIENE, magnitude=H_i.severity(report, k),
                explanation=templ_hygiene(k, H_i))

// 4. Rework divergences (per-(step, file) AST-hash equality).
for k in 0 until report.checkpoints.size:
    for file in filesTouchedAt(k):
        hashAfter = JavaFileAstHasher.hashFileAtSha(SHA(k), file)
        if hashAfter == null: continue
        for k' in 0 until k where file ∈ filesTouchedAt(k'):
            hashBefore = JavaFileAstHasher.hashFileAtSha(preSHA(k'), file)
            if hashBefore == null: continue
            if hashAfter == hashBefore:
                dps += DivergencePoint(
                    stepIndex=k, kind=REWORK, magnitude=churn(k', k, file),
                    counterfactualStrength=DESCRIPTIVE,  // may upgrade per PLAN-rework-detection
                    referenceAltStepIndexes=[k'],
                    file=file,
                    explanation=templ_rework(k, k', file))
                break  // one rework finding per (k, file)

return dps.sortedByDescending(magnitude)
```

**Top-K is an evaluation-time concept, not a production-pipeline
feature.** The production detector returns *every* DP that clears its
per-kind magnitude floor (`MIN_PROCESS_DELTA`, `MIN_REWORK_LINES`),
unsorted and untruncated. The dashboard renders all of them. The
experiment harness (see `PLAN-experiment.md`) is what sorts by
magnitude and slices the top-K when scoring against ground truth.
Rationale: realistic session sizes produce <20 DPs, so production
truncation is unnecessary cost and obscures honest output. A future
UI affordance could expose "top N prominently, expand for all" but
that's out of scope.

## Explanations (template, not LLM)

Each kind has a template that pulls numbers from the report. No
generation — just substitution. Examples:

- **Ordering:** "At step 4 (Rename Method `foo`), your trajectory's
  process score dropped 72 → 64 (cognitive +6, smells +3). An alternative
  ordering (Extract Method first, then Rename) avoided this dip — its
  terminal process score was 81 vs. your 73."
- **IDE replay:** "Step 7 (manual Extract Method across 3 commits) could
  have been done by the IDE in one step. The IDE version scored 85
  vs. your trajectory's 73 at this point — a 12-point gap, mostly from
  reduced churn (−42 LOC delta) and no transient cognitive-complexity
  spike."
- **Hygiene:** "You went 9 checkpoints without committing between steps
  3 and 12. Frequent commits give you cheap restore points and keep PR
  diffs reviewable."
- **Rework:** "Step 9 deleted code added in step 4. `Order.java` ended
  up structurally identical to its state before step 4 — 38 lines of
  churn cancelled itself out, likely a design backtrack."

## Schema additions

In `analysis/.../metrics/model/AnalysisReport.kt`:

```kotlin
@Serializable enum class DivergenceKind {
    ORDERING, IDE_REPLAY, HYGIENE, REWORK,
}

@Serializable data class DivergencePoint(
    val stepIndex: Int,
    val kind: DivergenceKind,
    val magnitude: Double,
    val explanation: String,
    /** For ORDERING / IDE_REPLAY — points to the alt that beat the user. */
    val referenceAltStepIndexes: List<Int>? = null,
)

// On AnalysisReport:
val divergencePoints: List<DivergencePoint> = emptyList(),
```

Sealed enum mirrors `AdviceKind` so the dashboard can switch on it
for icon / colour without string parsing.

## File-level changes

**New:**
- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/divergence/DivergenceDetector.kt`
  — the `object DivergenceDetector` with `fun detect(report): List<DivergencePoint>`
  and one private function per kind.
- `analysis/src/test/kotlin/com/github/ethanhosier/analysis/divergence/DivergenceDetectorTest.kt`
  — per-kind trigger + no-trigger cases, fabricated `AnalysisReport`
  fixtures (reuse `TrajectoryAdvisorTest` helpers).
- `dashboard/src/features/divergence/divergence-panel.tsx` — sorted list
  with click-to-select-step behaviour; mirrors `TrajectoryAdvicePanel`.
- `dashboard/src/features/divergence/divergence-item.tsx` — single-row
  rendering with kind icon, magnitude badge, explanation text.

**Modified:**
- `analysis/.../metrics/model/AnalysisReport.kt` — add the schema above.
- `analysis/.../pipeline/AnalysisPipeline.kt::buildAnalysisReport` — call
  `DivergenceDetector.detect(...)` *after* the advisor runs, fold into
  report.
- `dashboard/src/data/types.ts` — add `DivergencePointVM`.
- `dashboard/src/data/view-model.ts` — passthrough projection.
- `dashboard/src/App.tsx` — mount the panel near the chart (sibling
  of `TrajectoryAdvicePanel`).
- `dashboard/src/features/trajectory-chart/chart-points.tsx` —
  highlight checkpoints that are referenced by a divergence point
  (subtle ring, hover tooltip showing the explanation).

**Reused:**
- `AdviceKind` / `AdviceSeverity` patterns from `TrajectoryAdvisor.kt`.
- The `AlternativeTrajectory` shape — already carries everything the
  detector needs for kinds 1 and 2.
- `derivedMetrics.process.total` per checkpoint for kinds 1 and 2.
- `JavaFileAstHasher.hashFileAtSha` + shadow repo for kind 4.
- `metrics.tests` / `metrics.gradleBuild` for hygiene predicates.

## Evaluation hook

This algorithm gives the evaluation experiment a concrete target.

Injected-variant experiment (the one flagged as highest-leverage in
`HONEST_REVIEW.md`):

1. Take a clean fixture trajectory $\tau_0$ as ground truth.
2. Inject a known bad pattern at step $k$:
   - **Ordering injection:** swap two adjacent independent refactorings
     where the original order is known to score better.
   - **IDE-replay injection:** spread one IDE refactoring across 3
     manual edits with extra churn.
   - **Hygiene injection:** delete commits from a stretch, or set the
     test status red.
   - **Rework injection:** add a step pair $k', k$ where $k$ restores
     the file's pre-$k'$ AST.
3. Run the pipeline on the perturbed trajectory $\tau'$.
4. Measure: did `DivergenceDetector` flag step $k$ with the correct
   kind? Is $k$ in the top-K?
5. Repeat for ≥10 cases per kind. Report per-kind precision/recall.

Baseline comparison:
- **Endpoint-only baseline:** "process score at end ≥ start?" — yes/no.
  Show this misses all four divergence kinds by construction (every
  injection preserves the endpoint).
- **Per-step regression baseline:** "flag every step where process
  score drops" — show this fires on some hygiene-flavoured drops but
  misses ordering / IDE replay / rework entirely.
- **Existing `TrajectoryAdvisor` baseline:** the 7 trajectory-wide
  rules. Show that even where it overlaps in *what* it detects (e.g.
  `REORDER_BEATS_USER`), it cannot *locate* the divergence to a step.

## Out of scope

- LLM-generated explanations. Templates + numbers are enough for v1.
- A fifth "local complexity spike" kind. Earlier draft removed —
  redundant with Ordering when an alt covers the window, and reduces
  to weak descriptive-only evidence otherwise. The narrow class of
  "transient disruption absorbed by later refactoring without an
  AST-hash match for Rework" is future work.
- Multi-language. Stay Java-only.
- Cross-session aggregation ("this developer always reworks at week
  N"). Single-session only.
- Configurable threshold UI. Compile-time constants for v1.

## Verification

1. **Unit tests** in `DivergenceDetectorTest.kt`: per-kind trigger +
   no-trigger cases, all 4 kinds. Reuse the fixture helpers from
   `TrajectoryAdvisorTest.kt`.
2. **Integration:** run on the existing fixture session through
   `./gradlew :analysis:test`, eyeball the `divergencePoints` field
   in the produced `analysis-report.json` — make sure they look
   sane.
3. **Frontend:** `cd dashboard && npm run typecheck` after schema
   regen. Open the dashboard, confirm the panel renders with the
   expected kinds + magnitudes.
4. **Evaluation pipeline:** run the injected-variant experiment
   (above) on ≥10 cases per kind. Per-kind precision/recall, plus
   top-K hit rate. Write up in the methodology + evaluation
   chapters.

## Sequencing

Roughly 3–5 days of code work; another 3–5 days for the experiment +
writeup.

1. **Day 1:** schema + `DivergenceDetector` skeleton + Ordering and
   IDE-replay detection (data is right there).
2. **Day 2:** Hygiene predicates + tests for the four hygiene
   sub-predicates (commit cadence, test red-stays-red, build broken,
   build broken multiple times).
3. **Day 3:** Rework detection — see `PLAN-rework-detection.md` for
   the per-(step, file) AST-hash algorithm and the optional
   truncated-trajectory counterfactual upgrade. Tests included there.
4. **Day 4:** Frontend panel + chart-point highlighting + view-model
   passthrough.
5. **Day 5:** Polish + integration test on fixture session.
6. **Days 6–10:** Inject-variant harness + experiments + writeup.

## Why this lifts the marks

Mapped to the rubric in `MENG_PROJECT_ASSESSMENT_CRITERIA.md`:

- **Execution and Technical Quality (50%).** Turns an implicit
  data-comparison into a named algorithm with formal predicates and
  per-kind explanations. This is the "non-trivial novelty in method"
  the rubric asks for at the 70–84% band.
- **Evaluation and Reflection (20%).** Gives the evaluation experiment
  a concrete target. The inject-variant story per kind is much more
  compelling than "we synthesised some alternative orderings and they
  scored higher".
- **Communication (15%).** You get a methodology chapter with formal
  definitions, pseudocode, worked examples — exactly the structure the
  rubric rewards.
- **Framing (15%).** Closes the loop between the thesis question
  ("identify points where a better process was available") and the
  implementation. A marker reading the question first and the
  implementation second will now see a direct answer.

## Risks

- **Threshold sensitivity.** Magnitude thresholds are arbitrary; one
  marker may push back on "why $\theta_{\text{ord}} = 3$". Mitigation:
  sensitivity analysis in the evaluation chapter ("at $\theta = 1$ we
  get N divergences with precision P; at $\theta = 5$ we get M with
  precision Q").
- **Explanation quality plateau.** Templates can feel mechanical. Don't
  paper over this — call it out as a limitation and gesture at
  LLM-augmented explanations as future work.
- **Rework detection false positives.** Trivial edits that revert
  themselves (e.g. a 2-line typo fix and undo) would technically
  match the predicate. Mitigation: magnitude threshold on churn
  (default $\theta_\text{rew} = 5$ LOC); details in
  `PLAN-rework-detection.md`.
- **Time pressure.** If days 6–10 (experiment + writeup) run long, cut
  Rework first — kinds 1, 2, 3 alone are enough to ground the
  thesis-question answer. Ordering and IDE Replay are the strongest
  counterfactuals; Hygiene is the cheapest evidence.
