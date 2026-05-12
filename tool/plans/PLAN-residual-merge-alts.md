# Residual-merge alts — group co-located refactorings + capture user leftover edits

## Context

`AlternativeTrajectoryRunner` today produces one synthesised alt per `RefactoringStep`. When the miner reports two refactorings on the same `(fromSha, toSha)` window (e.g. Extract Method + Move Method, which `RefactoringStep.kt:8-14` notes is rare but real), the runner emits two independent alt branches that each apply only *one* of the detected refactorings. The diff against the user's `toSha` then includes "the other refactoring you also did" as noise — not actually an alternative, just an artifact of the per-step loop.

Separately, even on single-refactoring windows the synthesised alt-SHA only contains the refactoring's output. Any unrelated edits the user made in the same window (typo fixes in another file, formatting tweaks, an extra `import`, a README change) are absent from the alt-SHA. The diff `alt → userToSha` therefore lights up red on metric tiles that shouldn't move at all, and the alt path appears more divergent than it really is.

This plan fixes both problems in one pass:

1. **Group** `RefactoringStep`s sharing `(fromSha, toSha)` and synthesise them on a single worktree, applying each spec in sequence before committing one alt-SHA per group.
2. **Residual 3-way merge:** after applying the refactoring(s), diff the worktree state against the user's `toSha` and `git apply --3way` the result so the alt-SHA also carries the user's unrelated edits. The alt-SHA's endpoint is now "what the user would have ended up with if they'd used the IDE for the refactoring portion."

The chart's "did the alt diverge?" comparison becomes meaningful: any remaining `alt vs userToSha` divergence is genuinely the IDE-vs-manual delta (e.g. a missed callsite the user forgot to rename), not bookkeeping noise.

## Triggering condition

Unchanged from today (`AlternativeTrajectoryRunner.rejectReason`) except evaluated per *group*, not per step:

- At least one step in the group has `wasPerformedByIde == false` *and* a non-`Other` `RefactoringSpec`.
- `(fromSha, toSha)` are non-adjacent in `reconstruction.eventCommits` (the existing "user took multiple commits to land it" check).

Groups where every step has `wasPerformedByIde == true` or `spec == null` / `RefactoringSpec.Other` are skipped wholesale; mixed groups (some IDE, some manual) keep only the manual+typed members and skip the rest as today.

## Ordering within a group

RM doesn't promise an ordering when it emits multiple refactorings per window. Three options, in order of fidelity:

- **Miner emission order** — whatever order RM returned them in. Deterministic, zero implementation cost.
- **Fixed type priority** — e.g. `Rename* < Extract* < Move* < Inline*`, breaking ties by miner order. Predictable; covers the common Extract-then-Move case correctly without an AST pass.
- **AST-dependency topological sort** — most correct (e.g. if step B renames the method step A extracted, A must run first). Requires `RefactoringSpec`-level dependency inspection.

**Ship miner emission order**. Multi-refactoring windows are rare (per the docstring on `RefactoringStep`) and any group that doesn't synthesise cleanly under that order falls into the existing `skipped` map with a descriptive reason — which is the same failure mode as a single bad step today. Escalate to type-priority only if real reports show ordering-related failures clustering.

## Implementation

### Concurrency model — single-threaded, one `withBatchSession` per group

The current runner submits each candidate step to `Executors.newFixedThreadPool(parallelism)` and parallelises across steps (`AlternativeTrajectoryRunner.kt:99-128`). Drop that. Match `ReorderSynthesiser`'s pattern (`ReorderSynthesiser.kt:219-247`): iterate groups sequentially in a `for` loop, borrow one worktree per group, and wrap that group's applies inside `client.withBatchSession { ... }` so Eclipse's project index is initialised once per group instead of once per spec.

Why drop parallelism:
- Multi-refactoring windows are rare per `RefactoringStep.kt:8-14`, so the parallelism budget would idle ~95% of the time anyway.
- `RefactoringClient` already serialises every `invokeOnBundle` under a coarse `ReentrantLock` (`RefactoringClient.kt:33,49`) — parallel synthesis is contended on the bundle lock regardless. The pool's value was concurrent *worktrees*, not concurrent *applies*.
- Sequential + `withBatchSession` is strictly faster than the existing parallel-per-step path on multi-spec groups because index init is amortised; single-spec groups perform identically (one batch wrapping one apply).
- The `parallelism` constructor param + `Executors` + `WorktreePool` machinery go away.

### `AlternativeTrajectoryRunner.kt` — group then synthesise sequentially

Replace the per-step parallel submission at `AlternativeTrajectoryRunner.kt:99-133` with:

```kotlin
val groups = candidates.groupBy { it.fromSha to it.toSha }
    .map { (key, steps) ->
        Group(fromSha = key.first, toSha = key.second, steps = steps.sortedBy { it.stepIndex })
    }

val worktreeBase = sessionFolder.resolve("alternative-worktrees")
val shadowGit = GitRunner(reconstruction.repoDir)
val synthesised = mutableListOf<SynthesisedGroup>()
val skipped = sortedMapOf<Int, String>()

for (group in groups) {
    val worktree = borrowWorktree(reconstruction.repoDir, worktreeBase, group.fromSha)
    try {
        val outcome = synthesiseGroup(group, worktree, shadowGit)
        when (outcome) {
            is GroupResult.Ok -> synthesised += outcome.synth
            is GroupResult.PartialOk -> {
                synthesised += outcome.synth
                outcome.failedStepReasons.forEach { (idx, reason) ->
                    skipped[idx] = reason
                    log("step $idx: failed inside group at ${group.fromSha.take(7)} — $reason")
                }
            }
            is GroupResult.Skipped -> {
                group.steps.forEach { skipped[it.stepIndex] = outcome.reason }
                log("group at ${group.fromSha.take(7)} → ${group.toSha.take(7)}: skipped — ${outcome.reason}")
            }
        }
    } finally {
        releaseWorktree(worktree)
    }
}
```

Worktree borrow/release: the per-group lifecycle is small enough that the existing `WorktreePool` is overkill — a direct `git worktree add <path> <sha>` / `git worktree remove <path>` pair (already exposed on `GitRunner.kt:69-87`) keeps each group self-contained, mirroring how `ReorderSynthesiser` uses the pool one-at-a-time. Easiest path: keep `WorktreePool` for now with `parallelism = 1` (zero call-site change at the pipeline layer) and tighten later.

### `synthesiseGroup` — `withBatchSession` wraps all applies

```kotlin
private fun synthesiseGroup(
    group: Group,
    worktree: Path,
    shadowGit: GitRunner,
): GroupResult {
    val worktreeGit = GitRunner(worktree)
    worktreeGit.setLocalIdentity("alt@analysis.local", "Alternative Trajectory")

    val appliedSteps = mutableListOf<RefactoringStep>()
    val failed = sortedMapOf<Int, String>()

    // Single batch session covers every dispatcher.apply for this group:
    // Eclipse indexes the project once on the first apply and reuses the
    // cached project for subsequent applies in the same loop. Same
    // pattern as ReorderSynthesiser.kt:224.
    refactoringClient.withBatchSession {
        for (step in group.steps) {
            val outcome = dispatcher.apply(step.spec!!, worktree)
            if (outcome is SpecDispatcher.Result.Failed) {
                failed[step.stepIndex] = outcome.reason
                continue
            }
            appliedSteps += step
        }
    }

    if (appliedSteps.isEmpty()) {
        return GroupResult.Skipped(
            "no step in group applied: ${failed.values.joinToString("; ")}"
        )
    }

    // Residual 3-way merge: bring across whatever the user did that
    // wasn't the refactoring. Need a committed baseline for `git diff`
    // and `git apply --3way` to compute against — stage + commit the
    // refactoring output first, then layer the residual on top.
    worktreeGit.addAll()
    if (!worktreeGit.hasStagedChanges()) {
        return GroupResult.Skipped("refactoring produced no textual change")
    }
    val refactoringOnlySha = worktreeGit.commit(
        "alt: group ${appliedSteps.first().stepIndex}+ (refactoring only)"
    )

    val residualOutcome = applyResidual(
        worktreeGit = worktreeGit,
        refactoringOnlySha = refactoringOnlySha,
        userToSha = group.toSha,
    )

    val finalSha = when (residualOutcome) {
        is ResidualOutcome.Clean -> {
            // Squash refactoring + residual into one alt-SHA so the
            // shadow repo has a single commit per group.
            worktreeGit.resetSoft(group.fromSha)
            worktreeGit.commit(
                "alt: group ${appliedSteps.first().stepIndex}+ " +
                    appliedSteps.joinToString(", ") { it.refactoring.type }
            )
        }
        is ResidualOutcome.Conflicted -> {
            // 3-way merge couldn't resolve cleanly. Fall back to the
            // refactoring-only SHA and record per-step that residual
            // was dropped. Surface stats so the report can warn
            // "alt excludes N LOC of unrelated edits".
            worktreeGit.resetHard(refactoringOnlySha)
            refactoringOnlySha
        }
        is ResidualOutcome.Empty -> refactoringOnlySha
    }

    val branchRef = "alt/group-${appliedSteps.first().stepIndex}"
    shadowGit.branchForce(branchRef, finalSha)

    val synth = SynthesisedGroup(
        stepIndexes = appliedSteps.map { it.stepIndex },
        fromSha = group.fromSha,
        userToSha = group.toSha,
        altSha = finalSha,
        branchRef = branchRef,
        residual = residualOutcome.summary,
    )
    return if (failed.isEmpty()) GroupResult.Ok(synth) else GroupResult.PartialOk(synth, failed)
}
```

The `withBatchSession` scope intentionally covers *only* the `dispatcher.apply` loop. The git plumbing (`addAll`, `commit`, `diffPatch`, `applyThreeWay`, `resetSoft`) and the residual merge run *outside* the session — they don't touch Eclipse's cached project, and keeping them outside means a 3-way-merge stall doesn't hold the bundle lock against other (future) callers.

### Residual merge mechanic — `applyResidual`

```kotlin
private fun applyResidual(
    worktreeGit: GitRunner,
    refactoringOnlySha: String,
    userToSha: String,
): ResidualOutcome {
    val patch = worktreeGit.diffPatch(refactoringOnlySha, userToSha)
    if (patch.isBlank()) return ResidualOutcome.Empty

    val patchFile = Files.createTempFile("alt-residual-", ".patch").apply {
        writeText(patch)
    }
    try {
        val applyResult = worktreeGit.applyThreeWay(patchFile)
        return when (applyResult) {
            is GitRunner.ApplyResult.Ok ->
                ResidualOutcome.Clean(addedLines = applyResult.added, deletedLines = applyResult.deleted)
            is GitRunner.ApplyResult.Conflict -> {
                worktreeGit.resetHard(refactoringOnlySha)  // wipe any conflict markers / partial apply
                ResidualOutcome.Conflicted(
                    rejectedFiles = applyResult.rejectedFiles,
                    droppedAdded = applyResult.added,
                    droppedDeleted = applyResult.deleted,
                    reason = applyResult.reason,
                )
            }
        }
    } finally {
        Files.deleteIfExists(patchFile)
    }
}
```

### `GitRunner` additions

Two new methods:

```kotlin
sealed interface ApplyResult {
    data class Ok(val added: Int, val deleted: Int) : ApplyResult
    data class Conflict(
        val rejectedFiles: List<String>,
        val added: Int,
        val deleted: Int,
        val reason: String,
    ) : ApplyResult
}

/**
 * `git apply --3way --index <patchFile>`. Stages successfully merged
 * hunks; conflicts are reported via the returned [ApplyResult.Conflict]
 * with the list of rejected files extracted from stderr (lines matching
 * `error: <path>: patch does not apply`). The caller is responsible
 * for `resetHard` on conflict.
 */
fun applyThreeWay(patchFile: Path): ApplyResult { … }

/** `git reset --soft <sha>` — moves HEAD without touching worktree or index. */
fun resetSoft(sha: String) {
    run("reset", "--soft", sha)
}
```

`applyThreeWay` is the only meaningful new git invocation. Implementation: shell out to `git apply --3way --index`, capture exit + stderr, parse `error:` lines for rejected paths. Line counts come from `git diff --numstat HEAD` after the apply.

### `Synthesised` → `SynthesisedGroup`

`AlternativeTrajectoryRunner.Synthesised` (one record per step) becomes `SynthesisedGroup` (one record per group with `stepIndexes: List<Int>`):

```kotlin
data class SynthesisedGroup(
    val stepIndexes: List<Int>,
    val fromSha: String,
    val userToSha: String,
    val altSha: String,
    val branchRef: String,
    val residual: ResidualSummary?,
)

data class ResidualSummary(
    val applied: Boolean,           // false ⇒ conflict, alt excludes the residual
    val addedLines: Int,
    val deletedLines: Int,
    val rejectedFiles: List<String>, // empty when applied == true
)
```

`Summary.synthesised: List<Synthesised>` becomes `List<SynthesisedGroup>`. `Summary.skipped` keeps its `Map<Int, String>` shape — failed individual steps within a partially-applied group still surface there, keyed by `RefactoringStep.stepIndex`.

### `AnalysisPipeline.kt` plumbing

At `AnalysisPipeline.kt:435-447`, the `singleStepAlts` mapping iterates `alternative.synthesised` and builds one `AlternativeTrajectory` per `Synthesised`. With grouping, the mapping becomes one `AlternativeTrajectory` per `SynthesisedGroup`:

```kotlin
val singleStepAlts = alternative.synthesised.mapNotNull { synth ->
    val specs = synth.stepIndexes.mapNotNull { stepsByIndex[it]?.spec }
    if (specs.size != synth.stepIndexes.size) return@mapNotNull null
    val altCp = altCheckpointFor(synth.altSha) ?: return@mapNotNull null
    AlternativeTrajectory(
        stepIndexes = synth.stepIndexes,
        fromSha = synth.fromSha,
        userToSha = synth.userToSha,
        branchRefs = listOf(synth.branchRef),
        specs = specs,
        // One altCheckpoint per group (a single SHA), even when stepIndexes.size > 1.
        // Distinct from reorder alts which have one altCheckpoint per applied step.
        altCheckpoints = listOf(altCp),
        residual = synth.residual,  // new field on AlternativeTrajectory; see below.
    )
}
```

The single-altCheckpoint shape is intentional: grouped synthesis produces one final SHA, not N intermediate ones. The frontend join logic treats this as "one alt path that applies N specs and lands at one SHA," which matches the user's mental model better than a synthetic per-step state.

`altShas`/`altFromShas` (line 206 onward) needs the same group-flattening — one entry per group, not per step. `singleAltPairs` (line 270) also collapses to per-group pairs.

## Data model — `AlternativeTrajectory`

Add an optional `residual` field, parallel to existing top-level fields:

```kotlin
@Serializable
data class AlternativeTrajectory(
    val stepIndexes: List<Int>,
    val fromSha: String,
    val userToSha: String,
    val branchRefs: List<String>,
    val specs: List<RefactoringSpec>,
    val altCheckpoints: List<CheckpointReport>,
    /** Residual merge result. Null for reorder alts (no residual concept).
     *  For single-step alts: present whenever residual was attempted —
     *  applied=true means the alt-SHA carries user leftovers; false means
     *  3-way merge conflicted and the alt-SHA is refactoring-only. */
    val residual: ResidualSummary? = null,
)

@Serializable
data class ResidualSummary(
    val applied: Boolean,
    val addedLines: Int,
    val deletedLines: Int,
    val rejectedFiles: List<String>,
)
```

Reorder alts (`reorderAlts` block in `AnalysisPipeline.kt:457-513`) pass `residual = null` — by design, their terminal `altCheckpoints.last()` aliases the user's `windowToSha`, so there is no residual to apply.

## Failure semantics

| Scenario                                                         | Outcome                                                                                 |
| ---                                                              | ---                                                                                     |
| Every step in group fails `dispatcher.apply`                     | Group skipped wholesale; all `stepIndex`es recorded in `Summary.skipped`.               |
| Some steps fail, ≥1 succeed                                      | `GroupResult.PartialOk`: succeeded steps land on alt-SHA; failed ones in `skipped`.     |
| All steps applied but produced no textual change                 | Group skipped (same as today's "refactoring produced no textual change").               |
| Refactoring applied; residual diff empty                         | `ResidualOutcome.Empty` → alt-SHA == refactoring-only state; `residual.applied=false`?  |
| Refactoring applied; residual `git apply --3way` clean           | Squashed into one alt-SHA carrying refactoring + leftovers; `residual.applied=true`.    |
| Refactoring applied; residual `git apply --3way` has conflicts   | Reset to refactoring-only SHA; `residual.applied=false`, `rejectedFiles` populated.     |

Open question for the table row marked `?`: when residual is empty (user did *nothing* outside the refactoring), should `residual.applied` be `true` (semantically: "alt fully captures user's window") or `null`/absent (semantically: "no residual to merge")? My take: emit `applied=true, addedLines=0, deletedLines=0` so the frontend can show "captured everything" for the common single-refactoring tight-window case.

## Rendering — dashboard impact

The dashboard already supports multi-step alt trajectories (`detail-panel.tsx:69-93`, the `altCheckpoint` selection branch reads `alt.steps`). Grouped alts populate `stepIndexes` with N entries and `altCheckpoints` with 1, which the view-model needs to handle — current `view-model.ts` probably assumes `altCheckpoints.length == stepIndexes.length` for IDE alts. Two options:

- **Plumb a 1-element `altCheckpoints` through as-is** and update the VM to handle the asymmetric case: alt is treated as "N refactorings applied at one chart point" with a single dot on the chart. The detail panel shows the list of refactoring types instead of a single one.
- **Synthesise per-step CheckpointReports** by snapshotting after each `dispatcher.apply` (commit between steps, then squash at the end). More commits in the shadow repo but the multi-step rendering works unchanged. Heavier.

Default to the first; the second is only worth it if per-step intermediate metrics turn out to be useful.

For the residual indicator: render a small badge next to the alt path label — `+12 / −3 residual` when applied, `⚠ excludes 12 LOC residual` when conflicted. Wire-level: `AlternativeTrajectory.residual` is already on the report, so this is pure presentation.

## Test plan

Unit tests on the runner (`AlternativeTrajectoryRunner` already has fixtures):

- Single-step window, residual empty → `ResidualOutcome.Empty`, alt-SHA == refactoring-only state.
- Single-step window, residual non-empty + applies cleanly → alt-SHA contains user's unrelated edits.
- Single-step window, residual conflicts → alt-SHA == refactoring-only, `residual.applied=false`, `rejectedFiles` populated.
- Two-refactoring window (Extract Method + Rename Method), both apply cleanly → one group, one alt-SHA, both `stepIndexes`.
- Two-refactoring window, second spec fails → `PartialOk`: first lands, second in `skipped`.
- Two-refactoring window, both apply, residual conflicts → alt-SHA has both refactorings, no residual.

Integration: `AnalysisPipelineTest` already exists with a small refactoring fixture. Add one fixture where the user does Rename + an unrelated comment edit on the same file; assert `alternativeTrajectories[0].residual.applied == true` and `addedLines == 1`.

## Non-goals

- Per-step intermediate alt-SHAs (use one squashed SHA per group).
- Stylistic normalisation of the synthesised refactoring (e.g. matching the user's chosen extracted-method name) — that's what the 3-way merge is meant to absorb.
- Re-ordering within a group beyond miner emission order.
- Anything for reorder alts; they're untouched by this plan (`residual = null`).

## Rollout

Single PR. Feature is invisible when there are no multi-refactoring windows and no residual edits — the common case stays a one-spec, no-residual alt — so risk of regression on existing reports is low. Add a `--no-residual-merge` CLI flag if a tripwire feels useful for the first deploy; otherwise straight in.
