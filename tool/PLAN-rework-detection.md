# Implementation plan: rework detection + truncated-trajectory counterfactual

## Context

Rework is one of the four divergence kinds in
`PLAN-divergence-detection.md`. It deserves its own plan because:

1. It's the cheapest divergence kind to implement — runs at the
   *structural* layer of the divergence pipeline, needs only the
   shadow repo and AST hashes, no `RefactoringSpec` / RefactoringMiner /
   bundle dependencies.
2. It comes with a natural counterfactual upgrade (truncated trajectory
   replay) that produces a *simulated* alternative — the strongest kind
   of evidence per the `counterfactualStrength` taxonomy in the
   divergence-detection plan.
3. The detection and counterfactual are designed in tandem, so the
   schema and pipeline wiring can be done in one slice rather than
   bolting the counterfactual on later.

End-state after this plan: rework divergence points are emitted with
file-level granularity, each carrying either `DESCRIPTIVE` evidence
(the detection alone) or `SIMULATED` evidence (a successfully-replayed
truncated trajectory branch in the shadow repo with a verified
AST-equivalent terminus).

## Scope

Two layers, both in this plan:

- **Layer A — Detection.** Per-(step, file) AST-hash equality check.
  Pure read-only over the shadow repo.
- **Layer B — Counterfactual.** Truncated-trajectory replay via
  `git apply` on a synthesised branch, with terminal AST audit. Run
  conditionally — only when the rework pair looks cleanly strippable.

Out of scope:
- Partial-rework detection via diff-line-set inversion (catches the
  case where step $k$ undoes *some* of step $k'$ but not all). Future
  work — file-level AST-hash matching is enough for v1.
- Per-file patch surgery in the counterfactual (stripping only the
  hunks that touched the reworked file from a multi-file step). v1
  strips whole steps; multi-file steps that aren't single-purpose
  downgrade to `DESCRIPTIVE`.
- Rename-aware tracking (a file moved from `Foo.java` to `Bar.java`
  between steps). v1 skips renamed files.
- Re-running `MetricsRunner` on the truncated end-state for dashboard
  chart parity. Useful nice-to-have — flagged as a possible Day-3
  stretch.

## Architecture: where it sits

Rework detection is part of the *structural* divergence layer
(see the two-layer framing in `PLAN-divergence-detection.md`). It
depends only on the shadow repo, not on any refactoring-aware stage,
so it can run as early as the pipeline likes. Recommended slot:

```
TraceLoader → TraceNormalizer → ShadowRepoBuilder
                                       │
                                       ├─> ReworkDetector  ◄── this plan
                                       │      │
                                       │      └─> TruncatedTrajectoryBuilder
                                       │
                                       └─> RefactoringMinerRunner → ...
```

Running early has two benefits:
- The pipeline log shows `[pipeline] rework: N divergences` before the
  expensive miner/validator/synth stages. Useful for long-running
  pipeline runs and debugging.
- If the bundle fails to load or RefactoringMiner crashes, rework
  divergences still surface in the report. Graceful-degradation story.

## Layer A: detection algorithm

Pseudocode for `ReworkDetector.detect`:

```
Input:  ReconstructionResult reconstruction
        GitRunner shadowGit
        Trace trace
Output: List<DivergencePoint>

shaForStep[k] = chronologically-ordered unique SHAs from reconstruction
preShaForStep[k] = shaForStep[k-1] if k > 0 else fromSha-of-first-event

filesTouchedAt[k] = shadowGit.changedJavaFilesBetween(preShaForStep[k], shaForStep[k])

dps = []
for k in 0 until N:
    for file in filesTouchedAt[k]:
        hashAfter = JavaFileAstHasher.hashFileAtSha(shadowGit, shaForStep[k], file)
        if hashAfter == null: continue          // unparseable / missing
        for k' in 0 until k:
            if file !in filesTouchedAt[k']: continue
            hashBefore = JavaFileAstHasher.hashFileAtSha(
                shadowGit, preShaForStep[k'], file
            )
            if hashBefore == null: continue     // file didn't exist before k'
            if hashAfter == hashBefore:
                churn = locChurn(shadowGit, preShaForStep[k'], shaForStep[k], file)
                dps += DivergencePoint(
                    stepIndex = k,
                    kind = REWORK,
                    magnitude = churn,
                    counterfactualStrength = DESCRIPTIVE,  // may upgrade in Layer B
                    referenceAltStepIndexes = [k'],
                    file = file,                 // new schema field
                    explanation = templateRework(k', k, file, churn),
                )
                break                            // one rework finding per (k, file)
return dps
```

Key points:

- **Per-(step, file) granularity.** A single step can rework one file
  without reworking another. Each `(k, file)` pair emits at most one
  divergence; the inner `break` ensures we don't double-report the same
  file's rework against multiple origin steps (take the *earliest*
  matching $k'$).
- **`changedJavaFilesBetween` already exists** in `GitRunner` and is
  used by the validator and reorder synth.
- **`hashFileAtSha` already exists.** Reads via `git show <sha>:<file>`
  — no worktree borrow needed, no live build state required.
- **Null hashes mean skip, not match.** Both `hashAfter == null` and
  `hashBefore == null` short-circuit. This prevents false positives on
  files that didn't exist before $k'$ touched them (creating the file
  at $k'$ and then deleting it at $k$ should *be* detected — but only
  if both pre-$k'$ and post-$k$ states are null *and* we explicitly
  want to flag create-then-delete as rework, which is a design choice).
- **`locChurn` is simple:** sum of added + removed lines across the
  diffs at $k'$ and $k$ restricted to `file`. Use
  `git diff --numstat` between the appropriate SHA pairs.

### Edge cases

- **File created at $k'$, deleted at $k$.** Both AST hashes are `null`.
  v1 decision: skip (the short-circuit handles this). Flagged as
  future work — the user might want this surfaced as a kind of rework.
- **File touched repeatedly.** Step 3 edits file A, step 5 edits it
  again, step 8 reverts A to the pre-step-3 state. The inner loop
  considers *every* earlier step that touched the file, so step 8 will
  be detected as reworking step 3 (not step 5).
- **Whitespace-only edits.** `JavaFileAstHasher` hashes the AST, not
  source text. Whitespace changes don't perturb the hash. ✓
- **Unparseable Java.** `hashFileAtSha` returns `null`. We skip. The
  detection is conservative; we miss some rework but never falsely
  report.
- **Renamed files.** v1 doesn't track renames — `Foo.java` and
  `Bar.java` are treated as different files even if the user did
  `git mv`. Future work: thread `Renamer` info from VFS events through
  the rework detector. Out of scope.

## Layer B: counterfactual (truncated trajectory) algorithm

For each `DivergencePoint` of kind `REWORK` emitted by Layer A, try to
synthesise a truncated trajectory. If successful, upgrade
`counterfactualStrength` from `DESCRIPTIVE` to `SIMULATED` and attach
the synthesised branch ref.

Pseudocode for `TruncatedTrajectoryBuilder.buildFor(rework)`:

```
Input:  ReworkFinding (k', k, file)
        ReconstructionResult reconstruction
        GitRunner shadowGit

Output: TruncatedResult — either Built(branchRef, ...) or Aborted(reason)

// Pre-conditions: detect over-eager strip
if filesTouchedAt[k'] != {file} or filesTouchedAt[k] != {file}:
    return Aborted("multi-purpose step — step-level strip over-strips")

// Build a side branch at the pre-k' SHA.
parentSha = preShaForStep[k']
branchName = "rework-counterfactual/${k'}-${k}-${shortHash(file)}"
shadowGit.checkout(parentSha, detach=true)
shadowGit.branchForce(branchName, parentSha)
shadowGit.checkout(branchName)

// Replay every step except k' and k, in order.
keptSteps = (0 until N) - {k', k}
keptSteps = keptSteps.filter { it > k' }  // only replay steps after k'
let currentSha = parentSha
for j in keptSteps:
    patch = shadowGit.format-patch-between(preShaForStep[j], shaForStep[j])
    apply = shadowGit.apply(patch, threeWay=true)
    if apply.failed:
        return Aborted("step ${j} did not apply cleanly to truncated parent")
    commitMsg = "rework-counterfactual: replay of step ${j}"
    currentSha = shadowGit.commit(commitMsg)

// Terminal AST audit.
endShaUser = shaForStep[lastStepIndex]
endShaTruncated = currentSha
divergentFiles = []
for f in shadowGit.changedJavaFilesBetween(parentSha, endShaUser):
    h_user = hashFileAtSha(shadowGit, endShaUser, f)
    h_trunc = hashFileAtSha(shadowGit, endShaTruncated, f)
    if h_user != h_trunc:
        divergentFiles += f
if divergentFiles.isNotEmpty():
    return Aborted("terminal AST mismatch on ${divergentFiles.size} file(s)")

// Compute churn savings.
strippedChurn = locChurn(shadowGit, preShaForStep[k'], shaForStep[k'], file)
              + locChurn(shadowGit, preShaForStep[k], shaForStep[k], file)
return Built(
    branchRef = branchName,
    truncatedEndSha = endShaTruncated,
    keptStepCount = keptSteps.size,
    strippedChurn = strippedChurn,
)
```

If `Built`, update the originating `DivergencePoint`:

```kotlin
rework.copy(
    counterfactualStrength = SIMULATED,
    truncatedTrajectoryBranch = result.branchRef,
    truncatedEndSha = result.truncatedEndSha,
    truncatedChurnDelta = result.strippedChurn,
)
```

### Edge cases

- **Conflict on apply.** Some intervening step depended on a line that
  $k'$ created. Abort, leave `DESCRIPTIVE`. This is *correct* behaviour:
  the rework wasn't actually wasted work, because something else relied
  on the intermediate state.
- **Non-Java files in the trajectory.** `git apply` may fail on binary
  files. Mitigation: filter patches to Java files only (or just `.java`
  / `.kt` / etc.) before applying. Step is still considered applied;
  non-Java changes are silently dropped from the counterfactual.
- **Multi-file step where only one file was reworked.** v1 aborts to
  `DESCRIPTIVE` (the pre-condition check at the top of the algorithm).
  Future work: per-file patch extraction.
- **Both `k'` and `k` touch the *same* file but also other files.** Same
  as above — abort to `DESCRIPTIVE`.
- **The trajectory ends *at* step $k$.** Then there are no surviving
  steps after $k$. The truncated trajectory ends at the pre-$k'$ SHA
  (i.e. step $k'$ and $k$ both stripped, nothing else replayed). Still
  valid; the audit just compares the user's end-state to the pre-$k'$
  state.

## Schema additions

In `analysis/.../metrics/model/AnalysisReport.kt`, extend the
`DivergencePoint` from `PLAN-divergence-detection.md` with rework-
specific fields:

```kotlin
@Serializable data class DivergencePoint(
    val stepIndex: Int,
    val kind: DivergenceKind,
    val magnitude: Double,
    val explanation: String,
    val counterfactualStrength: CounterfactualStrength,
    val referenceAltStepIndexes: List<Int>? = null,
    /** File path the rework was detected on. Non-null only for kind == REWORK. */
    val file: String? = null,
    /** Shadow-repo branch ref of the truncated counterfactual trajectory.
     *  Non-null only for kind == REWORK and counterfactualStrength == SIMULATED. */
    val truncatedTrajectoryBranch: String? = null,
    /** SHA at the end of the truncated trajectory. */
    val truncatedEndSha: String? = null,
    /** LOC of churn the truncation saves (added + removed across the two stripped steps). */
    val truncatedChurnDelta: Int? = null,
)
```

The non-null discipline keeps all rework-specific data in one place.
The dashboard can switch on `kind == REWORK` and render the truncated
fields when present.

## File-level changes

**New (under `analysis/src/main/kotlin/com/github/ethanhosier/analysis/divergence/structural/`):**

- `ReworkDetector.kt` — `object ReworkDetector` with
  `fun detect(reconstruction, shadowGit, trace): List<DivergencePoint>`
  plus private helpers `locChurn`, `templateRework`.
- `TruncatedTrajectoryBuilder.kt` — `class TruncatedTrajectoryBuilder`
  with `fun buildFor(rework, ...): TruncatedResult` and a sealed
  `TruncatedResult` of `Built | Aborted`.

**New (under `analysis/src/test/kotlin/...`):**

- `ReworkDetectorTest.kt`
- `TruncatedTrajectoryBuilderTest.kt`

**Modified:**

- `analysis/.../metrics/model/AnalysisReport.kt` — add the schema fields
  above and the `DivergenceKind`/`CounterfactualStrength` enums (if not
  already added per `PLAN-divergence-detection.md`).
- `analysis/.../pipeline/AnalysisPipeline.kt` — call `ReworkDetector`
  right after `ShadowRepoBuilder` completes; for each rework finding,
  call `TruncatedTrajectoryBuilder.buildFor` and either upgrade the
  finding to `SIMULATED` or leave it `DESCRIPTIVE`. Fold the resulting
  list into `report.divergencePoints` (existing field added by the
  divergence-detection plan).
- `dashboard/src/data/types.ts` — passthrough fields on
  `DivergencePointVM`.
- `dashboard/src/data/view-model.ts` — projection.
- `dashboard/src/features/divergence/divergence-item.tsx` — when
  `kind === "REWORK"` and the truncated branch is present, render a
  "Skip these steps to save N LOC of churn" sub-row with a button that
  highlights the truncated trajectory on the chart.
- `dashboard/src/features/trajectory-chart/chart-alternative-paths.tsx`
  — render `truncatedTrajectoryBranch` as a faded counterfactual chain
  (visually distinguishable from reorder alts — e.g. dotted instead of
  dashed).

**Reused (no change):**

- `GitRunner.changedJavaFilesBetween` for per-step file lists.
- `GitRunner.branchForce`, `checkout`, `commit`, `apply` for the branch
  build.
- `JavaFileAstHasher.hashFileAtSha` for hash equality and terminal
  audit.
- `WorktreePool` is *not* needed — all operations are on the shadow
  repo directly, no worktree borrow required.

## Tests

### `ReworkDetectorTest`

Hand-authored shadow-repo fixtures using `TraceLoader` test helpers.

1. **`extract_then_inline_detected`** — fabricate a 5-step trace where
   step 2 adds method `getDiscount()` and step 4 removes it (restoring
   the AST). Assert one divergence at `stepIndex=4`, `kind=REWORK`,
   `referenceAltStepIndexes=[2]`, `file=Order.java`.
2. **`field_added_removed_detected`** — same shape with a field.
3. **`unrelated_edits_not_detected`** — step 2 edits a method body,
   step 4 edits a different method. AST hashes don't match either's
   pre-state. Assert empty.
4. **`partial_edit_then_revert_not_detected`** — step 2 changes both
   `foo()` and `bar()`; step 4 only reverts `foo()`. File AST hash at
   step 4 ≠ file AST hash before step 2 (because `bar()` is still
   changed). Assert empty.
5. **`whitespace_does_not_perturb_detection`** — step 2 reformats the
   file; step 4 reverts the *logical* changes from step 1. Detection
   still fires because AST hash is whitespace-insensitive.
6. **`unparseable_file_skipped`** — fabricate a file with a syntax
   error in some step. Assert no false positive, no exception.
7. **`multi_file_per_step_per_file_findings`** — step 2 touches A and
   B; step 5 reverts only A. Assert one divergence on A only, none
   on B.

### `TruncatedTrajectoryBuilderTest`

8. **`clean_strip_succeeds`** — extract-then-inline trajectory where
   intervening steps don't touch the helper. Build truncation. Assert
   `Built`, terminal AST matches.
9. **`intervening_dependency_aborts`** — step 3 extracts helper, step
   4 modifies the helper, step 6 inlines. Truncation tries to apply
   step 4 against the pre-step-3 state where the helper doesn't exist
   — conflict. Assert `Aborted("step 4 did not apply...")`.
10. **`multi_purpose_step_aborts`** — step 3 extracts helper *and*
    renames a field; step 6 inlines the helper. The rework finding is
    on the helper's file; but step 3 also touched another file. Assert
    `Aborted("multi-purpose step...")`.
11. **`truncated_branch_persists_in_shadow_repo`** — `Built` result;
    verify the branch ref exists with the expected name pattern and
    points at the expected SHA.

### Integration

12. **`pipeline_emits_rework_divergence`** — wire the detector into
    `AnalysisPipeline`, run on the existing fixture session, assert
    `report.divergencePoints` contains at least one expected rework
    entry. Eyeball the resulting `analysis-report.json`.

## Verification

1. `./gradlew :analysis:test --tests ReworkDetectorTest` and
   `--tests TruncatedTrajectoryBuilderTest` pass.
2. Run the full pipeline on `F-medium` (the experiment fixture from
   `PLAN-experiment.md`) — pre-inject `ExtractThenInline` and
   `FieldAddedRemoved` patterns and confirm both are detected.
3. Inspect the `analysis-report.json` — each rework divergence has
   non-null `truncatedTrajectoryBranch` when the strip succeeded;
   `git log <branch>` shows the truncated chain.
4. `cd dashboard && npm run typecheck` — schema regeneration passes.
5. Open the dashboard, click a rework divergence, confirm the
   truncated trajectory shows on the chart as a faded overlay.

## Sequencing

Roughly 3 days. Layer A is dirt-cheap; Layer B is most of the work.

1. **Day 1 — Layer A + schema.** Add the schema fields. Implement
   `ReworkDetector.detect` (one private function, ~50 lines). Hand-
   author 5-step fixture and write tests 1–7. Run through pipeline,
   confirm divergences emit with `DESCRIPTIVE` strength.
2. **Day 2 — Layer B.** Implement `TruncatedTrajectoryBuilder` and
   the `git apply --3way` replay loop. Wire the conditional upgrade.
   Tests 8–11.
3. **Day 3 — Integration + dashboard.** Pipeline wire-in, view-model
   passthrough, divergence-item rendering with the truncated-trajectory
   sub-row, chart overlay. Test 12 + manual eyeball on a fixture.

Optional Day 3.5: run `MetricsRunner` on the truncated end-state SHA
so the chart can show *process score along the truncated trajectory*,
not just its existence. ~3 hours of work. Lifts the dashboard
demonstration value materially.

## Risks

- **Layer A is too eager.** Per-(step, file) AST-hash equality flags
  any incidental rework — e.g. a user who edited `Foo.java` to test
  something, decided not to keep it, and reverted. That's *technically*
  rework but maybe not interesting. Mitigation: filter by magnitude
  (`locChurn ≥ θ_rew`, e.g. 5+ LOC) so trivial flickers don't appear.
- **`git apply --3way` is finicky.** Patches with line-number drift
  from intervening edits can fail in non-obvious ways. Mitigation: log
  the failed step verbatim in the `Aborted` reason; treat any apply
  failure as a clean abort to `DESCRIPTIVE` (don't try to recover).
- **Truncated branches accumulate.** Long trajectories with many rework
  findings produce many synthesised branches in the shadow repo. Tens
  is fine; thousands would slow git operations. Mitigation: cap rework
  counterfactual generation at the top-N rework findings by magnitude
  (default 10).
- **Multi-file rework downgrades are common.** Most user steps touch
  multiple files in practice. Mitigation honestly is "live with it" —
  per-file patch surgery is real engineering and not worth v1. Be
  explicit about the downgrade behaviour in the writeup; it's a clean
  threats-to-validity bullet.

## Writeup framing

For the methodology chapter:

> *Rework detection.* We say that step $k$ exhibits rework with respect
> to file $f$ when the AST hash of $f$ at the post-state of step $k$
> equals the AST hash of $f$ at the pre-state of some earlier step
> $k' < k$ that also touched $f$. Detection runs over every
> $(k, f)$ pair in the trajectory; the algorithm is $O(N^2 \cdot F)$ in
> trajectory length and per-step touched-file count, both small in
> practice.
>
> *Truncated-trajectory counterfactual.* For each detected rework pair
> $(k', k)$, we attempt to synthesise the trajectory that would have
> resulted from omitting both steps. We branch from the pre-$k'$
> commit in the shadow repository and replay every surviving step's
> patch via `git apply --3way` in chronological order. If every patch
> applies cleanly and the resulting end-state's AST is equivalent to
> the user's actual end-state over the file set $f \in F$ originally
> changed across $[k', \text{end}]$, we promote the divergence's
> *counterfactual strength* from `DESCRIPTIVE` to `SIMULATED` and
> persist the resulting branch in the shadow repository for downstream
> visualisation.
>
> *Limitations.* Truncation operates at step granularity rather than
> file granularity, so multi-purpose steps (those touching files
> beyond the reworked one) yield `DESCRIPTIVE` rather than `SIMULATED`
> evidence even when their non-reworked changes are independent.
> Per-file patch surgery is left to future work.

That paragraph is rubric-ready: formal predicate, complexity analysis,
limitations section, future-work bullet.
