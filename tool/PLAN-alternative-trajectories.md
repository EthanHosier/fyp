# Alternative Trajectories — Plan

## Goal

For each refactoring the user performed manually (detected by RefactoringMiner), simulate the IDE-automated path and surface a "what if" comparison — time saved, churn reduction, build/tests pass, metric deltas — in the dashboard.

## Status

Spike landed on branch `alternative-trajectories` (commit `3ebff11`).

Proves: we can drive JDT's real refactoring framework (LTK + `ExtractMethodRefactoring`) from a plain Gradle JUnit test with no Eclipse IDE present. The test rewrites a fixture source file correctly and passes cleanly.

What the spike covers today (single-file):
- `:refactoring-bundle` Kotlin subproject packaged as an OSGi bundle with `JdtRefactorer.extractMethod(...)`.
- `analysis/src/test/.../jdt/EquinoxBootstrap.kt` boots an in-process Equinox framework, scans the test classpath, wraps non-bundle jars (commonmark, kotlin-stdlib) with fabricated OSGi manifests, installs + starts Eclipse platform bundles + Felix SCR in dependency order.
- Spike test installs the refactoring bundle, invokes the entry point reflectively through the bundle's classloader, asserts output. Workspace lifecycle is clean (no `@TempDir` lock races).

What the spike does **not** cover yet:
- Project-wide (multi-file) rename/refactor.
- Classpath extraction from a real Gradle/Maven project.
- Integration into the analysis pipeline.
- `alternativeTrajectories` data model, runner, TS types, dashboard UI.

## One-time infrastructure (next step)

Turn the single-file spike into a real project-wide harness. After this, new refactoring types are ~10–30 LoC each.

### 1. Point the Eclipse project at the user's worktree

Change `JdtRefactorer.materialise` to:
- `desc.setLocation(IPath(projectRoot))` — project's physical location *is* the worktree, no copy. Edits land directly on disk.
- Keep `osgi.instance.area` on a temp dir distinct from the worktree so Eclipse's `.metadata/` never lands inside the project.

### 2. Configure the build path

Today we set only the Java nature. For reference resolution we need:

- One `IClasspathEntry` per source folder (`src/main/java`, `src/test/java`, …). `JavaCore.newSourceEntry(path)`.
- `JavaRuntime.getDefaultJREContainerEntry()` — pulls in the JRE via the `org.eclipse.jdt.launching` bundle (already installed by the spike).
- One `JavaCore.newLibraryEntry(jarPath, …)` per compile-time dependency jar.
- Output folder `bin/` so JDT has somewhere to park compiled state.

Combine into `javaProject.setRawClasspath(entries, monitor)`.

### 3. Wait for indexing

After classpath setup, JDT indexes asynchronously. Before running any refactoring:

```
new SearchEngine().searchAllTypeNames(
  null, SearchPattern.R_EXACT_MATCH,
  "__force_index__".toCharArray(), SearchPattern.R_EXACT_MATCH,
  IJavaSearchConstants.TYPE, scope, requestor,
  IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH, monitor);
```

or equivalent blocking search. Avoid `internal.*` IndexManager APIs.

### 4. Classpath extraction from Gradle

Separate concern — callers pass in the list of jars. Two options:

- **Snapshot at record time.** The IDE plugin already knows the module's classpath (it's the IntelliJ project model). Record it into the session payload alongside events; analysis pipeline reads it back.
- **Invoke Gradle on demand.** Add a `printRuntimeClasspath` task, run it from the pipeline against the worktree SHA. Slower; repeated per refactoring.

Start with (1) — cheaper, uses info we already have in-process on the IDE side.

### 5. Multi-file API shape

```kotlin
fun renameMethod(
    projectName: String,
    projectRoot: Path,
    sourceFolders: List<String>,     // relative to projectRoot
    classpathJars: List<Path>,
    declaringTypeFqn: String,
    oldName: String,
    newName: String,
    paramTypeSignatures: List<String>? = null,  // disambiguate overloads
)
```

No return value — edits land on the worktree directly. Caller diffs against the pre-refactor SHA to collect the patch.

### 6. Finding the IMethod in a real project

With a proper classpath, `icu.javaProject.findType(fqn)` works. For overloads use `type.getMethod(name, paramTypeSignatures)` where signatures are JDT-encoded (`Ljava/lang/String;`, `I`, `V`, …).

### 7. Failure isolation

The worktree is mutated in place; if a refactoring fails we need to undo. Two options:

- **Copy-on-write worktree per simulation.** Reuse `WorktreePool` (already exists for RM). Each alt-trajectory runs in its own worktree; discarded after.
- **Git reset after each run.** Simpler, but the pipeline already provisions worktrees so (1) is free.

Go with (1).

## Verification (multi-file spike)

Before wiring into the pipeline:

- Fixture: two hand-written `.java` files — a class defining `foo()` and another calling it.
- Test: call `renameMethod(...)` with `foo` → `bar`.
- Assert: both files on disk contain `bar`, neither contains `foo`.
- Classpath: empty (or just JRE) — no external deps needed for this fixture.

## Per-new-refactoring cost

Once the multi-file harness is in place, each new refactoring is:

- One new method on `JdtRefactorer`.
- Pick the right JDT descriptor constant or refactoring class:
  - Extract Method — `ExtractMethodRefactoring` (already done).
  - Rename Method — `IJavaRefactorings.RENAME_METHOD` descriptor.
  - Inline Method — `IJavaRefactorings.INLINE_METHOD` descriptor.
  - Move Method / Move Static — `IJavaRefactorings.MOVE_METHOD` / `MOVE`.
  - Change Method Signature — `IJavaRefactorings.CHANGE_METHOD_SIGNATURE`.
  - Pull Up / Push Down — `IJavaRefactorings.PULL_UP` / `PUSH_DOWN`.
- Set the refactoring-specific options via the descriptor's setters.
- Reuse `materialise` + `runRefactoring` unchanged.

Occasional one-liners added to the `init` block when a new refactoring trips on a preference that `jdt.ui` normally seeds.

## Residual maintenance cost

- Eclipse version bumps can reshuffle bundle manifests. Rare, non-zero.
- New preferences from `jdt.ui` surface per refactoring. Add as they hit.

## Pipeline integration (after harness works)

### Server

- New stage `alternatives/AlternativeTrajectoryRunner` after `DiffsRunner` + `RefactoringMinerRunner`.
- Eligibility: `wasPerformedByIde == false && refactoring.ideRelevant && type == "Extract Method"` initially; spans >1 manual-edit checkpoint since the last anchor.
- For each eligible step: snapshot a worktree at `fromSha`, invoke `JdtRefactorer.extractMethod(...)`, commit → `toSha'`, run build + tests + metrics in that worktree.
- Output: `List<AlternativeTrajectory>` on `AnalysisReport`.
- Time constant: `AUTOMATED_REFACTOR_DURATION_MS = 5_000L`. `timeSavedMs = realDurationMs - 5_000`.

### Data model

```kotlin
@Serializable
data class AlternativeTrajectory(
    val refactoringStepIndex: Int,
    val realStartSha: String,
    val realEndSha: String,
    val realDurationMs: Long,
    val steps: List<AlternativeStep>,
    val comparison: AlternativeComparison,
)

@Serializable
data class AlternativeStep(
    val fromSha: String,
    val toSha: String,
    val label: String,
    val patch: String,
    val metrics: CheckpointMetrics,
)

@Serializable
data class AlternativeComparison(
    val timeSavedMs: Long,
    val churnDelta: Int,
    val buildPassedAlt: Boolean,
    val testsPassedAlt: Boolean,
    val complexityDelta: Double,
    val couplingDelta: Double,
    val duplicationDelta: Double,
)
```

`AnalysisReport.alternativeTrajectories: List<AlternativeTrajectory>`. Regenerate TS types.

### Dashboard

Out of scope for v0. Backend emits the data; UI lands in a follow-up.

## Sequencing

1. Multi-file fixture spike (validates classpath + indexing).
2. Classpath extraction via IDE plugin → session payload.
3. `AlternativeTrajectory*` data types + TS regen.
4. `AlternativeTrajectoryRunner` pipeline stage wiring.
5. Real session end-to-end test.
6. Dashboard UI (later PR).

## Open questions

- Is step (2) — classpath via IDE plugin — feasible per-checkpoint, or should it be snapshotted once per session? Session-once is cheaper but risks drift if Gradle deps change mid-session. Per-checkpoint is expensive. Default: session-once, re-snapshot if `build.gradle*` mutates.
- Worktree pool sizing: the RM runner already sizes by parallelism × 2. Alt-trajectory runner may want more; revisit after measuring.
- What to do when the JDT refactoring rejects the simulation (e.g. selection doesn't cover a clean statement set). Proposed: record as `simulationFailed: true` + reason string; dashboard surfaces a muted "couldn't simulate" card rather than a comparison.
