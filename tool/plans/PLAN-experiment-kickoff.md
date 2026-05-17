# Plan: kick off the experimental campaign

## Context

The process-score methodology is locked (PR #56) and the literature
scaffolding is in place (`RESEARCH-metrics-weighting.md`,
`RESEARCH-cleanliness-metrics.md`). What's missing is the
*experimental* layer: sensitivity / ablation defence of the weights,
plus the injection-style evaluation that demonstrates the detector
actually catches deliberate process mistakes. `PLAN-experiment.md`
specifies these in detail; this plan is the kickoff that turns it
into shippable code + a recording protocol.

Two methodological decisions diverge from `PLAN-experiment.md` as
written:

1. **Manual recording, not mechanical injection.** Instead of
   building an `InjectionHarness` that mutates event lists in code,
   we record real sessions through the IDE plugin where deliberate
   bad patterns are performed. Stronger as evidence (exercises the
   plugin end-to-end, real captured events), smaller-N but
   cleaner-signal per session. Target: ~45 short focused sessions,
   ~3–5 refactor operations each, **one pattern per session**.
2. **Pipeline split: expensive synth phase + cheap scoring phase.**
   Refactor `AnalysisPipeline` so the expensive Phase A
   (RefactoringMiner / validator / synth / metrics / diffs /
   tracking) writes an intermediate artefact, and a cheap Phase B
   reads that artefact + a `ProcessScoreWeights` config to produce
   the final `AnalysisReport`. Makes sensitivity / ablation tractable
   (180+ Phase-B runs per sweep, each <1 s) and unblocks future
   weight-tuning work.

## Critical files

**Refactor / extend**:

- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/pipeline/AnalysisPipeline.kt` — split into Phase A + Phase B
- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/pipeline/AnalysisReport.kt` — `buildAnalysisReport(...)` becomes Phase B's entry point; everything it consumes becomes the Phase-A artefact shape
- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/metrics/derived/DerivedMetricsRunner.kt` — accept a `ProcessScoreWeights` constructor parameter (default = production constants)
- `analysis/build.gradle.kts` — add a `:phaseB` task for re-running Phase B with custom weights against a Phase-A dump

**New (experiment infra)**:

- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/metrics/derived/ProcessScoreWeights.kt` — data class
- `analysis/src/test/kotlin/com/github/ethanhosier/analysis/experiment/SensitivityExperiment.kt`
- `analysis/src/test/kotlin/com/github/ethanhosier/analysis/experiment/AblationExperiment.kt`
- `analysis/src/test/kotlin/com/github/ethanhosier/analysis/experiment/baselines/EndpointOnlyBaseline.kt` — baseline #1, ~20 lines
- `analysis/src/test/kotlin/com/github/ethanhosier/analysis/experiment/baselines/PerStepRegressionBaseline.kt` — baseline #2, ~30 lines
- `analysis/src/test/kotlin/com/github/ethanhosier/analysis/experiment/DivergenceExperiment.kt` — main injection-style driver
- `analysis/src/test/resources/fixtures/sessions/` — directory of recorded session traces (one per recorded session)
- `analysis/src/test/resources/fixtures/sessions/manifest.csv` — pattern + ground-truth-step + fixture-path index

**Read-only references** (no edit):

- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/divergence/DivergencePointBuilder.kt` — the named "detector"
- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/advice/TrajectoryAdvisor.kt` — baseline #3
- `PLAN-experiment.md` — protocol authority (pattern definitions, metrics, threats-to-validity)

## Phase 1 — Pipeline split + injectable weights (~2 days)

### 1.1 `ProcessScoreWeights` data class

Replace the `private const val` weights in `DerivedMetricsRunner.kt`'s
companion object with a public data class:

```kotlin
data class ProcessScoreWeights(
    val gain: Double = 50.0,
    val broken: Double = 28.0,
    val skipTests: Double = 14.0,
    val manualIde: Double = 11.0,
    val length: Double = 11.0,
    val commitGap: Double = 7.0,
    val baseline: Int = 50,
) {
    companion object {
        val PRODUCTION = ProcessScoreWeights()
    }
}
```

Inject via constructor:
`class DerivedMetricsRunner(val weights: ProcessScoreWeights = ProcessScoreWeights.PRODUCTION)`.
All existing tests pass `PRODUCTION` (default), so they're untouched.

Also injectable: `HygieneDetector`'s `MIN_COMMIT_GAP` and
`COMPOSITE_GAP_MS`. Same data class, different field — fold into
`ProcessScoreWeights` or use a parallel `ProcessScoreThresholds`,
whichever reads cleaner.

### 1.2 Phase A / Phase B split

The natural split point is *just before* `buildAnalysisReport` in
`AnalysisReport.kt`. Concretely:

**`PhaseAResult` data class** — everything `buildAnalysisReport`
currently reads from `AnalysisPipeline.run()`:

```kotlin
data class PhaseAResult(
    val trace: Trace,
    val reconstruction: ReconstructionResult,
    val metrics: MetricsRunner.Summary,
    val miner: RefactoringMinerRunner.Summary,
    val alternative: AlternativeTrajectoryRunner.Summary,
    val diffs: DiffsRunner.Summary,
    val pmdTracking: PmdTrackingRunner.Summary,
    val cpdTracking: CpdTrackingRunner.Summary,
    val reorderTrajectories: List<ReorderTrajectory>,
    val reworkSummary: ReworkSynthesiser.Summary,
    val augmentedAltMetricsBySha: Map<String, CheckpointMetrics>,
    val parallelism: Int,
    val metricsDurationMs: Long,
)
```

This is serialisable to JSON (kotlinx.serialization is already used
across the codebase). `AnalysisPipeline.run()` returns `PhaseAResult`
instead of running `buildAnalysisReport` internally.

**`ReportAssembler.assemble(phaseA, weights)`** — new function in
`AnalysisReport.kt` (or extracted next door). Just calls the existing
`buildAnalysisReport(...)` logic with the phase-A pieces + the weights.
This is Phase B.

**Existing call sites**:

- The CLI's main entry point (find via
  `grep -r "AnalysisPipeline" --include="*.kt" --include="*.kts"`)
  runs Phase A, dumps to JSON, then Phase B → final report. Two flags
  so callers can do "phase A only" or "phase B only".
- Tests that currently `DerivedMetricsRunner().run(...)` continue to
  work — Phase A wires up to Phase B in-process by default.

### 1.3 Phase-B-only CLI task

Add to `analysis/build.gradle.kts`:

```kotlin
tasks.register<JavaExec>("phaseB") {
    description = "Re-run Phase B (cheap derived metrics + scoring) against a Phase-A dump"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.github.ethanhosier.analysis.cli.PhaseBCli")
    // Usage: ./gradlew :analysis:phaseB --args="<phaseA.json> <weights.json> <outputReport.json>"
}
```

With matching `cli/PhaseBCli.kt` that reads the two JSON inputs, calls
`ReportAssembler.assemble`, writes the final report.

### 1.4 Verification

- `./gradlew :analysis:test` green (existing tests, unchanged semantics).
- Manual sanity: run end-to-end on `dashboard/src/hooks/`'s session,
  confirm output equals current production output byte-for-byte (or
  within rounding).
- Run Phase A once, then Phase B 5 times with perturbed weights;
  measure wall-clock — should be < 1 s for each Phase B run vs
  whatever Phase A takes.

## Phase 2 — Experiment drivers + baselines (~1.5 days)

### 2.1 Baselines

Two tiny pure functions over `AnalysisReport`:

```kotlin
// Baseline #1: did cleanliness improve from session start to end?
fun endpointOnlyBaseline(r: AnalysisReport): Boolean =
    r.checkpoints.last().derivedMetrics.cleanliness >
    r.checkpoints.first().derivedMetrics.cleanliness

// Baseline #2: flag every step where process score dropped by > θ
//   from the previous step
fun perStepRegressionBaseline(r: AnalysisReport, theta: Double = 3.0): List<Int> {
    return r.checkpoints.windowed(2).mapIndexedNotNull { i, (prev, curr) ->
        if (curr.derivedMetrics.process.total < prev.derivedMetrics.process.total - theta) i + 1 else null
    }
}
```

Baseline #3 is `TrajectoryAdvisor.advise(report)` — already exists,
just read its outputs.

### 2.2 `SensitivityExperiment.kt`

JUnit-style test that:

1. Loads a list of Phase-A dumps (the session corpus, see Phase 3).
2. For each weight `w` in `ProcessScoreWeights`, runs Phase B at
   `w' = 0.5·w` and `w' = 1.5·w` against every Phase-A dump.
3. Compares each perturbed run's top-K divergence points to the
   production-weights baseline run via Kendall's τ + top-5 hit rate.
4. For sessions with known ground truth (the recorded sessions),
   reports whether the perturbed detector still flags the right step.
5. Writes `sensitivity-results.csv`.

Output: per-weight × perturbation row with mean τ, mean hit rate, mean
ground-truth retention, 95% bootstrap CIs.

### 2.3 `AblationExperiment.kt`

Same shape but five variant rows per fixture (cleanliness-only, +
broken, + hygiene triplet, + length bonus, full) zeroing the relevant
`ProcessScoreWeights` fields. Writes `ablation-results.csv`.

### 2.4 `DivergenceExperiment.kt`

Loop over the session corpus. For each session:

1. Run pipeline → final report.
2. Look up ground truth from `manifest.csv`.
3. Score against ground truth on Tier 1 (alt of right kind exists at
   target step) and Tier 2 (flagged divergence point of right kind
   exists at target step ± 1).
4. Run the three baselines against the same report; score similarly.
5. Compute false-positive count on the un-injected control session
   for the same fixture codebase.
6. Write `experiment-results.csv`.

Aggregate to per-kind precision / recall / top-5 hit / FP rate +
baseline comparison.

### 2.5 Verification

- Each driver runs to completion on the existing single fixture and
  produces a non-empty CSV.
- Sensitivity sweep takes < 30 s end-to-end (Phase B is cheap).

## Phase 3 — Recording protocol + session corpus (~1–2 days)

### 3.1 Fixture codebase

Pick one small Java fixture codebase (~5–10 classes, ~500 LOC) and
freeze it. Reuse `ck-fixture` or similar from
`analysis/src/test/resources/` if scoped right; otherwise hand-author
one. The same codebase is used across all recorded sessions so the
trajectory-touched set is consistent.

### 3.2 Pattern playbook

One sentence per pattern in
`analysis/src/test/resources/fixtures/sessions/PROTOCOL.md`: exact
steps to perform, expected ground-truth step number, target file. E.g.:

> **ManualExtractMethod** — open `OrderService.computeTotal`, cut
> lines 12–32, paste into new method `applyDiscount`, replace the
> original with a call. Ground truth = step 1 (the first manual
> commit).

10 patterns × ~4–5 sessions per pattern × small variations (which
file, which method, magnitude) = ~45 sessions.

Magnitude tiers per `PLAN-experiment.md`:

- **Loud**: large block (20+ lines), many cps in the bad state.
- **Borderline**: small block (3–5 lines), boundary cps.
- **Sub-threshold**: marginal — used as the false-positive test.

### 3.3 Recording mechanics

Each session: launch the IDE plugin in record-mode, perform the
playbook, hit "end session", log the ground-truth step in
`manifest.csv`:

```csv
session_id,pattern,strength,target_step,fixture_path
001,ManualExtractMethod,loud,1,sessions/001/
002,ManualExtractMethod,borderline,1,sessions/002/
...
```

### 3.4 Recording cost

~5 min per session × 45 = ~4 hours of pure recording, plus ~1 hour of
capture-and-label glue. Realistic in a single day, or split over 2
sessions of recording.

### 3.5 Phase A on each session

Once recorded, run Phase A against each session and dump the artefact.
Per-session Phase A is ~30–60 s; 45 sessions ≈ 30–45 min total.
Outputs go into
`analysis/src/test/resources/fixtures/sessions/<id>/phaseA.json`.

After this phase, every experiment driver reads pre-computed
`phaseA.json` files and never re-runs the expensive synth.

## Phase 4 — Results + plots + writeup hooks (~1 day)

### 4.1 Plots

Generate from the three CSVs (matplotlib, embedded in a Python
notebook under `analysis/src/test/resources/notebooks/` or similar):

- Per-kind precision / recall bar chart (Experiment 2).
- Top-K cumulative detection line plot.
- Kendall's τ per weight × perturbation (Experiment 1a).
- Ablation Kendall's τ vs full score (Experiment 1b).
- Per-fixture timings table (Phase A wall-clock).

### 4.2 Methodology-chapter prose hooks

`RESEARCH-metrics-weighting.md`'s "Sensitivity analysis + ablation"
section gets concrete numbers wired in once results land. The section
already has placeholders ("Kendall's τ ≥ 0.X"); replace with the
measured value.

`PLAN-experiment.md`'s threats-to-validity section is mostly already
written; cross-link from the evaluation chapter.

### 4.3 GenAI disclosure section

One-paragraph appendix per the rubric. List: tools used, scope, review
process. Not coupled to the experiments but a known gap from
`HONEST_REVIEW.md` §4.

## Verification (end-to-end)

1. `./gradlew :analysis:test` — green; new experiment drivers succeed
   and write CSVs.
2. `./gradlew :analysis:phaseB --args="..."` — Phase B CLI runs
   against a Phase-A dump.
3. Spot-check: pick one recorded session (e.g.
   ManualExtractMethod-loud-001), run pipeline, confirm the pattern's
   ground-truth step appears in the report's flagged `divergencePoints`
   with the correct kind.
4. Run a sensitivity sweep over all 6 weights and confirm
   `sensitivity-results.csv` has 12 rows (6 × 2 perturbations) × 45
   fixtures × N metrics.
5. Final headline numbers (per-kind precision / recall / FP rate)
   plausible — Tier 1 detection ≈ 100% on loud injections, Tier 2 ≈
   100% on loud, drops on borderline, near-0 on sub-threshold.

## Sequencing (estimated days)

| Day | Work |
|-----|------|
| 1   | Phase 1 — `ProcessScoreWeights`, Phase A/B split, smoke tests |
| 2   | Phase 1 cont. — Phase B CLI, sanity-check vs production output |
| 3   | Phase 2 — Sensitivity + ablation drivers; run on existing fixture for preliminary numbers |
| 4   | Phase 2 cont. — `DivergenceExperiment.kt` + baselines; protocol document |
| 5   | Phase 3 — record ~25 sessions (first half of corpus) |
| 6   | Phase 3 — record remaining ~20 sessions; run Phase A on all |
| 7   | Phase 4 — plots, prose hooks, sanity-check headline numbers |

Total ~7 days. Compresses to 5–6 if Phase 1 can be done in 1 day or
recording goes faster than estimated.
