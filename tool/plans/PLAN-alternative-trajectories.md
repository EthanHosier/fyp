# Alternative trajectories — synthesise IDE-driven equivalents of manual multi-step refactorings

## Context

`:analysis` already detects refactorings the user performed manually (via `RefactoringMinerRunner`) and now ships a production `RefactoringClient` that can apply any IDE-equivalent refactoring headlessly (PR #24, merged). The two have not been wired together.

This plan adds a new pipeline stage that, for every detected manual refactoring whose `[fromSha, toSha]` window contains at least one intermediate checkpoint (i.e. the user took a manual detour the IDE could have collapsed into one click), synthesises the IDE-driven version: it borrows a worktree at `fromSha`, runs the matching `RefactoringClient` op, and commits the result as a synthetic alt-SHA. Metrics are then computed for that alt-SHA in the same pass as the main trace, and the result is surfaced top-level on the report so the frontend can join it back to the corresponding `RefactoringStep` via `(fromSha, toSha)`.

A second goal is housekeeping: the miner is independent of metrics today (only `Reconstruction` feeds both), so we move the miner ahead of metrics in `AnalysisPipeline`. This lets us synthesise alt-SHAs *before* metrics runs and pass them as an extra param to a single `MetricsRunner.run(...)` call — no double-pass over the worktree pool.

## Triggering condition

For a `RefactoringStep s`, synthesise an alternative iff **all** of:

- `s.refactoring` is an IDE-relevant variant (i.e. not the `Other` leaf of the new sealed hierarchy below) — `RefactoringClient` supports the type at all.
- `s.wasPerformedByIde == false` — the user did it manually; an IDE-driven version is genuinely an alternative.
- At least one intermediate unique SHA lies strictly between `s.fromSha` and `s.toSha` in `reconstruction.eventCommits` ordered values — i.e. the user took >1 commit step to land it.

Steps not meeting all three are passed through unchanged (`alternativeTrajectories` simply omits them).

## Miner output enrichment — sealed `RefactoringSpec`, transient on `RefactoringStep`

The existing flat `DetectedRefactoring` data class stays exactly as it is today — every field, every JSON name. The frontend, the generated TS types, the dashboard view-model, and every existing consumer of the analysis report keep working unchanged.

The typed structured info needed to drive synthesis is added in a **parallel** sealed interface `RefactoringSpec`, attached to `RefactoringStep` as a `@Transient` field so it's part of the in-memory model but excluded from JSON serialization and from kxs-ts-gen output.

```kotlin
// Unchanged — kept exactly as today.
@Serializable
data class DetectedRefactoring(
    val type: String,
    val description: String,
    val leftSideLocations: List<RefactoringLocation>,
    val rightSideLocations: List<RefactoringLocation>,
    val ideRelevant: Boolean,
)

// New — internal-only, never serialized.
sealed interface RefactoringSpec {
    data class ExtractMethod(
        val sourceFilePath: String,
        val extractedSelectionStartLine: Int,
        val extractedSelectionEndLine: Int,
        val newMethodName: String,
    ) : RefactoringSpec

    data class RenameMethod(
        val declaringTypeFqn: String,
        val oldName: String,
        val newName: String,
        val paramTypeSignatures: List<String>,
    ) : RefactoringSpec

    // … one variant per IDE-relevant kind in IdeRelevantRefactorings, each
    //    mirroring the typed request shape its RefactoringClient op consumes …

    object Other : RefactoringSpec   // non-IDE-relevant or RM-typed-mapper-not-yet-implemented
}

// Modified — adds a transient spec field; existing JSON layout untouched.
@Serializable
data class RefactoringStep(
    // … existing fields …
    val refactoring: DetectedRefactoring,
    val wasPerformedByIde: Boolean,
    @Transient val spec: RefactoringSpec? = null,
)
```

`RefactoringMinerRunner.toDetected` is split into two: `toDetected(r) → DetectedRefactoring` (unchanged) and `toSpec(r) → RefactoringSpec` (new — pattern-matches RM's typed `Refactoring` subclasses and pulls the typed fields the matching `RefactoringClient` op needs). Both are populated when constructing each `RefactoringStep`. RM types not yet covered by a typed mapper fall back to `RefactoringSpec.Other`, and `AlternativeTrajectoryRunner` skips them with a clear "spec not implemented" reason.

Because `@Transient` excludes the field from kotlinx-serialization (and consequently from `KxsTsGenerator`'s descriptor walk), no TS regen and no dashboard changes are needed for this migration. The full sealed coverage + alternative-trajectory pipeline can ship in a single PR without any frontend churn.

## New stage — `AlternativeTrajectoryRunner`

New file: `analysis/src/main/kotlin/com/github/ethanhosier/analysis/alternative/AlternativeTrajectoryRunner.kt`.

```kotlin
class AlternativeTrajectoryRunner(
    private val refactoringClient: RefactoringClient,
    private val git: GitRunner,
    private val parallelism: Int = …,
) {
    data class Synthesised(
        val stepIndex: Int,
        val fromSha: String,
        val userToSha: String,        // s.toSha — handy for frontend joins
        val altSha: String,            // synthetic commit produced from fromSha + IDE refactoring
        val branchRef: String,         // "alt/<stepIndex>"
    )

    data class Summary(
        val candidates: Int,            // steps that met the trigger condition
        val synthesised: List<Synthesised>,
        val skipped: Map<Int, String>,  // stepIndex → reason (e.g. "RefactoringClient returned Failed: …")
    )

    fun run(
        reconstruction: ReconstructionResult,
        steps: List<RefactoringStep>,
        sessionFolder: Path,
    ): Summary
}
```

Per candidate step:

1. Borrow a worktree at `s.fromSha` from a `WorktreePool` instance scoped to this stage.
2. Resolve the right `RefactoringClient` op via a `when (val spec = s.spec) { is RefactoringSpec.ExtractMethod -> …; is RefactoringSpec.RenameMethod -> …; else -> skip }` dispatch — one short branch per type, each ~6 LoC: build the typed request from the spec fields, call the op, return the `RefactoringOutcome`.
3. On `Success`: `git add -A && git commit -m "alt: step <i> <type>"` *inside the worktree*, capture the resulting SHA, and push it onto a branch `alt/<stepIndex>` in the shared shadow repo via `git branch alt/<i> <sha>`. The branch ref keeps the commit reachable for downstream metrics + diffs.
4. On `Failed`: record `skipped[stepIndex] = reason`; do not commit. Worktree is released cleanly.

Parallelism mirrors `MetricsRunner`'s pattern (executor + per-task `borrow/release`). `RefactoringClient` itself is serialised internally by its own `ReentrantLock`, so the parallelism here is bounded by that lock — but the worktree setup, git commit, and branch ref work all parallelise. Acceptable for a first cut; revisit if it becomes a bottleneck.

## MetricsRunner — single run, two SHA groups

Per the user's preference: keep one entry point and add an extra param.

```kotlin
fun run(
    reconstruction: ReconstructionResult,
    sessionFolder: Path,
    alternativeShas: List<String> = emptyList(),
): Summary
```

Internal change is small: union `uniqueShas + alternativeShas` (preserving order, dedup'd) when seeding the executor; the existing `<sha>.json` cache + `computeOne` path is unchanged. `Summary` gains one field:

```kotlin
data class Summary(
    // … existing …
    val alternativeCheckpoints: List<CheckpointMetrics> = emptyList(),
)
```

Populated by partitioning the parallel results back into the two groups by SHA membership. `diffBySha` is still computed for the trace SHAs as today; for alt SHAs we additionally compute `fromSha → altSha` diffs via a small helper `DiffRunner.runPairs(pairs: List<Pair<String, String>>): Map<String, DiffStats>` keyed by the `altSha` and folded into `diffBySha`.

## Pipeline reordering

`AnalysisPipeline.run`:

```
Load → Normalize → Reconstruct
                   ├─ Miner            (was: after Metrics)
                   ├─ AlternativeTrajectoryRunner   (new, depends on Miner only)
                   ├─ Metrics(reconstruction, sessionDir, altShas = synthesised.map { it.altSha })
                   └─ Diffs(reconstruction, miner.steps, alternativeSummary)
                       │   produces existing checkpointPatches + refactoringPatches
                       │   plus new alternativePatches: Map<stepIndex, String>
                       │   each = git.diffPatch(step.fromSha, alt.altSha)
                  ▼
              buildAnalysisReport
```

The miner already has no metrics dependency, so promoting it ahead is a pure reorder. `alt/<stepIndex>` branches are persisted before metrics begins so the worktree pool can check them out like any other ref.

`AnalysisPipeline.Result` gains `alternativeSummary: AlternativeTrajectoryRunner.Summary` and `alternativeDurationMs: Long`.

`RefactoringClient` is constructed **once** at the pipeline level and passed into `AnalysisPipeline` via constructor. The pipeline itself doesn't own the lifecycle — the server's app-scoped singleton (already planned in the previous PR's server lifecycle wiring) owns it; CLI callers boot a client themselves and pass it in.

## Diffs for alternative paths

Reuse the existing `DiffsRunner` machinery — alt SHAs are real refs in the shadow repo (`alt/<stepIndex>` branch), so `git.diffPatch(fromSha, altSha)` already works. Extend `DiffsRunner.run(...)` to take `AlternativeTrajectoryRunner.Summary` as a third argument and emit a new map alongside the existing two:

```kotlin
data class Summary(
    val checkpointPatches: Map<String, String>,    // existing — keyed by toSha
    val refactoringPatches: Map<Int, String>,      // existing — keyed by stepIndex; fromSha → userToSha hunk-filtered
    val alternativePatches: Map<Int, String>,      // new — keyed by stepIndex; fromSha → altSha (no hunk-filter, the whole IDE-driven change is the point)
)
```

No new git plumbing required — `GitRunner.diffPatch(from, to)` is the same call as for refactoring patches. Symmetrical to `refactoringPatches`, so the frontend renders the IDE-driven alternative side-by-side with the user's manual version using identical patch-rendering plumbing.

Skipped: the `altSha → userToSha` "how the user diverged from the IDE alternative" diff. Not needed for v1; trivial to add later as a fourth map keyed by stepIndex.

## Report shape — top-level

`AnalysisReport` gains two top-level fields. Top-level (not nested under `RefactoringStep`) because the join can be reconstructed on the frontend from `(stepIndex, fromSha, userToSha)`.

```kotlin
@Serializable
data class AnalysisReport(
    // … existing fields …
    val alternativeTrajectories: List<AlternativeTrajectory> = emptyList(),
    val alternativePatches: Map<Int, String> = emptyMap(),   // mirrors refactoringPatches
)

@Serializable
data class AlternativeTrajectory(
    val stepIndex: Int,             // join key into refactoringSteps + alternativePatches
    val fromSha: String,
    val userToSha: String,          // join key into checkpoints (the user's actual end-state)
    val altSha: String,             // join key into checkpoints (alt CheckpointMetrics live alongside the user's)
    val branchRef: String,
)
```

Every `CheckpointMetrics` still lives in `report.checkpoints`; alt SHAs go in alongside trace SHAs (with the existing schema, no flag needed). The frontend joins each `AlternativeTrajectory` back to its `RefactoringStep` via `stepIndex`, to its metrics via the three SHA fields, and to its patch via `alternativePatches[stepIndex]`. No nested objects, no UI plumbing changes for existing checkpoint/refactoring views.

## Critical files

**New:**
- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/alternative/AlternativeTrajectoryRunner.kt`
- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/alternative/RefactoringClientDispatch.kt` — the `when (DetectedRefactoring)` → `RefactoringOutcome` dispatch table; one short arm per IDE-relevant type
- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/metrics/model/AlternativeTrajectory.kt`

**New:**
- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/miner/model/RefactoringSpec.kt` — sealed interface, in-memory only

**Modified:**
- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/miner/model/RefactoringStep.kt` — add `@Transient val spec: RefactoringSpec? = null`
- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/miner/RefactoringMinerRunner.kt` — add `toSpec(r)` mapper alongside the existing `toDetected(r)`; populate both on each `RefactoringStep`
- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/metrics/MetricsRunner.kt` — add `alternativeShas` param; partition `Summary.checkpoints` and add `alternativeCheckpoints`
- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/metrics/gitdiff/DiffRunner.kt` — add `runPairs(...)` helper for from→alt `DiffStats`
- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/diffs/DiffsRunner.kt` — accept `AlternativeTrajectoryRunner.Summary`; populate new `alternativePatches: Map<Int, String>` via `git.diffPatch(fromSha, altSha)`
- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/metrics/model/AnalysisReport.kt` — add `alternativeTrajectories: List<AlternativeTrajectory>` and `alternativePatches: Map<Int, String>`
- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/pipeline/AnalysisPipeline.kt` — reorder stages, accept `RefactoringClient`, plumb alt SHAs through metrics, populate report
- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/server/*.kt` — pass the app-singleton `RefactoringClient` into `AnalysisPipeline`

**Reused (no change):**
- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/refactoring/RefactoringClient.kt` and the 30+ ops under `analysis/src/main/kotlin/com/github/ethanhosier/analysis/refactoring/ops/` — drive synthesis
- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/metrics/WorktreePool.kt` — borrow at `fromSha` for synthesis
- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/reconstruct/GitRunner.kt` — `git add -A && git commit && git branch alt/<i>`

## Verification

1. **Unit test — DetectedRefactoring sealed migration**: existing `RefactoringMinerRunnerTest` (or its closest equivalent) keeps green after switching to sealed variants. Add at least one assertion per IDE-relevant variant that the typed fields are populated from RM's typed model.
2. **Unit test — `AlternativeTrajectoryRunner` happy path**: hand-built two-checkpoint fixture where the user does Extract Method across two commits. Assert `synthesised.size == 1`, the produced `altSha` exists in the shadow repo, and the alt commit's tree contains the new private method.
3. **Unit test — trigger filter**: a manual one-step refactoring (no intermediate SHA) → `synthesised` empty. An IDE-performed one (regardless of span) → empty. A non-`ideRelevant` (Other) variant → empty.
4. **Integration test — full pipeline**: extend `AnalysisPipelineTest` with a session that contains a multi-step manual rename method. Assert `report.alternativeTrajectories` has one entry whose `altSha` appears in `report.checkpoints` with green build/tests, and whose `patch` is non-empty.
5. **Pipeline ordering**: assert miner runs before metrics in `AnalysisPipeline.run` (test reads `Result` durations or relies on a thin spy).
6. **`./gradlew :analysis:check` green**.
7. **Server smoke test**: `./gradlew :analysis:runServer`, POST a session that exercises the new path, confirm the response JSON contains `alternativeTrajectories` with a populated `altSha`.

## Sequencing within the PR

1. Migrate `DetectedRefactoring` to a sealed hierarchy. Update `RefactoringMinerRunner` to populate typed variants. Get existing miner tests green.
2. Add `AlternativeTrajectoryRunner` + `RefactoringClientDispatch.kt`. Cover Extract Method end-to-end first (smallest dispatch arm to validate the loop).
3. Wire `MetricsRunner` to accept `alternativeShas`. Add `DiffRunner.runPairs`.
4. Reorder `AnalysisPipeline`; add `AlternativeTrajectory` to `AnalysisReport`.
5. Fill in remaining dispatch arms one by one; each lands with at least one synthesis fixture.
6. Server wiring; integration test; runServer smoke check.
