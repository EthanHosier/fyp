# Divergence points as first-class report + UI concept

## Context

The pipeline already produces three flavours of `AlternativeTrajectory`
— **ordering** (reorder synth), **IDE replay** (single-step automated
refactoring), and **rework** (surgical replay) — but they all land in
one undifferentiated list (`AnalysisReport.alternativeTrajectories`)
with no `kind` discriminator. Kind is inferred implicitly downstream
from `stepIndexes.size` and whether `specs` is empty
(`AnalysisPipeline.kt:585-615`, `view-model.ts:824`). The dashboard
renders every alt as a generic dashed branch on the chart with no
indication of *why* the alt exists or *what the user could have done
differently*.

The thesis question is "given start + end, can we identify
**divergence points** where a better process was available?" That
concept currently exists only in `PLAN-divergence-detection.md` —
nothing in the schema or UI surfaces a divergence point as a named
entity. This plan promotes `DivergencePoint` to a first-class type in
both the report and the dashboard, with each DP owning the
alternative trajectories that demonstrate it.

End state: `report.divergencePoints: List<DivergencePoint>` indexes
into the existing `alternativeTrajectories` list (kept alongside,
backwards-compatible) and is rendered as kind-tagged markers overlaid
on the user's chart-line. Clicking a marker selects + highlights its
alts and opens a kind-specific detail card.

## Design decisions (from user)

- **Cardinality:** one DP owns many alts (`altTrajectoryIndexes: List<Int>`). Natural for ordering windows with multiple winning permutations; IDE/rework lists just hold one index.
- **Kinds in scope:** `ORDERING`, `IDE_REPLAY`, `REWORK` only. `HYGIENE` is reserved in the enum but no detector / DP is emitted yet — added later.
- **UI surface:** overlay markers on the user's chart-line at each DP's step. No separate panel. Click → select + highlight.
- **Schema migration:** *additive*. `divergencePoints` lives alongside `alternativeTrajectories` (which keeps its current shape). Old cached reports deserialise unchanged.

## Approach

### A. Schema (Kotlin)

In `tool/analysis/src/main/kotlin/com/github/ethanhosier/analysis/metrics/model/AnalysisReport.kt`:

```kotlin
@Serializable enum class DivergenceKind { ORDERING, IDE_REPLAY, REWORK, HYGIENE }

@Serializable data class DivergencePoint(
    val stepIndex: Int,                  // anchor on user trajectory
    val kind: DivergenceKind,
    val magnitude: Double,               // process-score delta vs user, or churn for rework
    val title: String,                   // short headline e.g. "Manual extract → IDE would have scored +12"
    val explanation: String,             // 1–3 sentence body, backend-templated, no LLM
    val altTrajectoryIndexes: List<Int>, // indexes into report.alternativeTrajectories
    // Kind-specific extras (all nullable; populated only when meaningful)
    val orderingWindowSteps: List<Int>? = null,   // ORDERING: the window's user step indexes
    val originatingStepIndex: Int? = null,        // REWORK: step where the reverted code first appeared
    val file: String? = null,                     // REWORK
    val scopeLabel: String? = null,               // REWORK: "Foo#bar(int)"
    val reworkLineCount: Int? = null,             // REWORK
    val replacedRefactoringId: String? = null,    // IDE_REPLAY: the IntelliJ refactoringId
)

// On AnalysisReport:
val divergencePoints: List<DivergencePoint> = emptyList(),
```

On `AlternativeTrajectory`: add `kind: DivergenceKind = DivergenceKind.ORDERING` (default keeps old fixtures deserialisable). Each alt now self-identifies; downstream consumers stop inferring from `specs.isEmpty()` / `stepIndexes.size`.

### B. Pipeline assembly

In `tool/analysis/src/main/kotlin/com/github/ethanhosier/analysis/pipeline/AnalysisPipeline.kt::buildAnalysisReport`, at the alt-merging stage around lines 496–628:

1. **Tag alts with kind at creation time** — the three producers (`singleStepAlts` line 503 → `IDE_REPLAY`, `reorderAlts` line 527 → `ORDERING`, `reworkAlts` line 602 → `REWORK`) each set `kind` on the `AlternativeTrajectory`. No more implicit inference.
2. **Build `divergencePoints`** after the unified `baseAlternatives` list is finalised (so indexes are stable):
   - **IDE_REPLAY:** one DP per single-step alt. `stepIndex = alt.stepIndexes[0]`, `altTrajectoryIndexes = [i]`, `magnitude = alt.altCheckpoints[0].derivedMetrics.process.total - user.process.total at userToSha`. Title pulled from `specs[0].type` (e.g. "Manual Extract Method → IDE in 1 step").
   - **ORDERING:** group all ordering alts sharing the same `(fromSha, userToSha)` window. One DP per window, with `altTrajectoryIndexes = [i, j, k, …]`. `stepIndex` anchors at the window's terminal user step (`max(alt.stepIndexes)` is stable across permutations). `magnitude = best alt's process delta`. `orderingWindowSteps` carries the window's user step indexes (sorted).
   - **REWORK:** one DP per rework alt. `stepIndex = terminalStep` (alt's `userToSha` step). `originatingStepIndex` recovered from the index of `alt.fromSha` in `orderedShas`. `file`, `scopeLabel`, `reworkLineCount` flow through from `ReworkDetector.ChunkPair` — plumb them out via `SynthesisedRework` so the pipeline can attach them to the DP without re-running detection.
3. **Threshold filtering** — drop DPs whose `magnitude` falls below a conservative per-kind floor (e.g. `θ_ord = 3`, `θ_rep = 3`, `θ_rew = 2` lines) so the chart isn't peppered with noise. Constants live next to the new code; tune later.
4. Slot `divergencePoints` onto the report. Existing alt list keeps its current contract (chart still iterates it directly for rendering, view-model still maps it 1:1).

### C. View model (TypeScript)

In `tool/dashboard/src/data/types.ts`:

```ts
export type DivergenceKindVM = "ORDERING" | "IDE_REPLAY" | "REWORK" | "HYGIENE";

export type DivergencePointVM = {
  id: string;                         // synthetic stable key
  stepIndex: number;                  // chart-point index to anchor the marker
  kind: DivergenceKindVM;
  magnitude: number;
  title: string;
  explanation: string;
  altIndexes: number[];               // indexes into AlternativeTrajectoryVM[]
  orderingWindowSteps?: number[];
  originatingStepIndex?: number;
  file?: string;
  scopeLabel?: string;
  reworkLineCount?: number;
};
```

Also extend `AlternativeTrajectoryVM` with `kind: DivergenceKindVM` (today derived implicitly in `view-model.ts:824`; now passthrough). The fallback "Rework" label in `specLabel` (`view-model.ts:579-581`) becomes a switch on `kind` so labels are always kind-driven, never spec-shape-driven.

In `tool/dashboard/src/data/view-model.ts`: a direct projection of `report.divergencePoints` into `DivergencePointVM[]`, exposed as `viewModel.divergencePoints`.

### D. Chart overlay

In `tool/dashboard/src/features/trajectory-chart/`, add `chart-divergence-markers.tsx` rendering above `chart-alternative-paths.tsx`'s alt paths:

1. **Marker glyph per kind** anchored at `xPosByStepIndex(dp.stepIndex)`, just above the user's chart-line.
   - `ORDERING` → `⇄` (swap)
   - `IDE_REPLAY` → `⚙` (gear, "IDE could have done this")
   - `REWORK` → `↻` (round trip)
   Use the existing brand-blue tone when any owned alt wins, grey otherwise (mirrors `altWins` logic at line 89-105). Rework no-op alts: always grey.
2. **Hover tooltip:** `dp.title` + magnitude badge (e.g. "+12 process points", "3 lines reverted").
3. **Click:**
   - Dispatches a new selection variant `{ kind: "divergencePoint", id }` through the existing chart-selection reducer.
   - Renderer highlights every alt in `dp.altIndexes` (thicker stroke / saturated colour); dims others.
   - Detail panel area swaps to `DivergenceDetailCard` showing `title`, `explanation`, magnitude, kind-specific extras (window steps for ORDERING, file+scope+lineCount for REWORK, replacedRefactoringId for IDE_REPLAY), and a chevron list of the alts it owns (clicking an entry drills into the existing alt-detail card).
4. **Multi-alt ORDERING:** when an ORDERING DP owns >1 permutation, all are highlighted together; detail card shows "best alt: process +X | other alts: …".

Markers keyed by `dp.id` for React stability.

### E. Detail panel switching

Generalise the existing chart-selection reducer (driven today by `altInterval` / `altCheckpoint` selections in `chart-alternative-paths.tsx:251-318`):

```ts
type ChartSelection =
  | { kind: "none" }
  | { kind: "altInterval", altIndex: number, stepIndex: number }
  | { kind: "altCheckpoint", altIndex: number, stepIndex: number }
  | { kind: "altContinuation", altIndex: number, stepIndex: number }
  | { kind: "divergencePoint", dpId: string }; // NEW
```

When a DP is selected, the detail-panel root renders `DivergenceDetailCard`; otherwise the existing alt-detail card. Clicking through to a child alt from inside the DP card updates selection to `altCheckpoint` of the chosen alt's terminal step, so the existing detail flow takes over.

## Files to modify / create

**New backend (Kotlin):**
- `tool/analysis/src/main/kotlin/com/github/ethanhosier/analysis/divergence/DivergencePointBuilder.kt` — pure function `build(alts, checkpoints, orderedShas) → List<DivergencePoint>`. Per-kind grouping + thresholding.
- `tool/analysis/src/test/kotlin/com/github/ethanhosier/analysis/divergence/DivergencePointBuilderTest.kt` — per-kind cases.

**Modified backend:**
- `tool/analysis/src/main/kotlin/com/github/ethanhosier/analysis/metrics/model/AnalysisReport.kt` — add `DivergenceKind`, `DivergencePoint`, `divergencePoints` field.
- `tool/analysis/src/main/kotlin/com/github/ethanhosier/analysis/metrics/model/AlternativeTrajectory.kt` — add `kind: DivergenceKind` field with `ORDERING` default.
- `tool/analysis/src/main/kotlin/com/github/ethanhosier/analysis/pipeline/AnalysisPipeline.kt` — tag each alt with kind at creation (lines 503, 527, 602); call `DivergencePointBuilder.build` after `baseAlternatives` finalised; attach to report.
- `tool/analysis/src/main/kotlin/com/github/ethanhosier/analysis/alternative/rework/ReworkSynthesiser.kt` (and the `SynthesisedRework` model) — surface `file`, `scopeLabel`, `reworkLineCount`, `originatingStepIndex` so the pipeline can attach them to the DP.

**New frontend:**
- `tool/dashboard/src/features/trajectory-chart/chart-divergence-markers.tsx` — SVG marker layer above the user's chart line.
- `tool/dashboard/src/features/divergence/divergence-detail-card.tsx` — kind-aware detail card.

**Modified frontend:**
- `tool/dashboard/src/data/types.ts` — `DivergencePointVM`, `DivergenceKindVM`, extend `AlternativeTrajectoryVM` with `kind`.
- `tool/dashboard/src/data/view-model.ts` — project `divergencePoints`; replace implicit kind inference at `:579-581`, `:824-825` with passthrough from backend.
- `tool/dashboard/src/features/trajectory-chart/chart-alternative-paths.tsx` — accept a `selectedDpId` prop; highlight `dp.altIndexes` when set; mount the marker layer.
- Whichever component owns the alt-detail card today — wire the new `ChartSelection.divergencePoint` variant so it renders `DivergenceDetailCard`.

**Regenerated:**
- `tool/dashboard/src/generated/report-types.ts` via `./gradlew :analysis:generateDashboardTypes`.

## Reused functions / utilities

- `AlternativeTrajectoryRunner`, `ReorderSynthesiser`, `ReworkSynthesiser` — existing producers, just tagged with kind on output.
- `xPosByStepIndex` already computed in `chart-alternative-paths.tsx` — reuse for marker anchors.
- `altWins` colouring logic (`chart-alternative-paths.tsx:89-105`) — reuse for marker tone.
- Existing chart-selection reducer — extend, don't fork.
- `derivedMetrics.process.total` per checkpoint — fuel for `magnitude` calculation.

## Verification

### Unit tests

1. `DivergencePointBuilderTest`:
   - **`single_step_ide_alt_emits_ide_replay_dp`** — one IDE alt → one DP, `kind=IDE_REPLAY`, `altTrajectoryIndexes=[0]`, magnitude matches process delta.
   - **`reorder_window_with_two_permutations_collapses_to_one_dp`** — two ORDERING alts sharing `(fromSha, userToSha)` → one DP with `altTrajectoryIndexes=[0, 1]`, `magnitude` from best.
   - **`rework_alt_emits_rework_dp_with_file_and_scope`** — file/scope/lineCount round-trip through to DP.
   - **`magnitude_below_threshold_drops_dp`** — IDE alt with +1 process gain → no DP at `θ_rep=3`.

2. `AnalysisReportSerializationTest` (regression) — deserialise an existing fixture with no `divergencePoints` field and `kind`-less alts; expect default empty list + `ORDERING` kind.

### Pipeline integration

3. **`pipeline_emits_divergence_points_per_kind`** — fixture with one IDE refactoring, one reorder window, one rework round-trip. Assert `report.divergencePoints` contains exactly three entries with the right kinds + `altTrajectoryIndexes` indexing into a non-empty alts list.

### Frontend

4. `./gradlew :analysis:generateDashboardTypes && cd dashboard && npm run typecheck` clean.
5. Open the dashboard on a session with a known mix (extract-then-inline rework + a reorder window). Confirm:
   - A `↻` marker appears at the rework's terminal step; hover shows "N lines reverted"; click highlights the rework alt and opens the detail card with file + scope.
   - A `⇄` marker appears at the reorder window's terminal step; click highlights all permutation alts and lists them in the detail card.
   - A `⚙` marker appears at any single-step IDE replay; magnitude reads in process points.
   - Selecting a DP and then clicking a child alt in the detail card transitions to the existing alt-detail card (no broken state).
6. Open the dashboard on an old cached report (no `divergencePoints` field). Confirm no markers render and the chart behaves exactly as before — backwards-compatibility check.

## Out of scope

- **HYGIENE detector and rendering.** The enum slot exists; no DP is produced. Separate plan (`PLAN-divergence-detection.md` already drafts the predicates).
- **Threshold tuning UI.** Compile-time constants for v1; sensitivity analysis is evaluation-chapter work.
- **Cross-kind DPs** (e.g. an ordering alt that also flags hygiene). Each alt belongs to exactly one DP via its `kind`.
- **DP ranking + top-K UI.** Markers render in stepIndex order; no global "top issues" list.
- **Replacing the advice panel.** `TrajectoryAdvisor`'s trajectory-wide observations stay where they are; DPs are per-step, complementary.
