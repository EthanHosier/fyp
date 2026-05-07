# Implementation plan: Per-step refactoring validator + windowing

A new pipeline stage that, for each typed `RefactoringStep`, replays
the spec from `fromSha` and checks whether the resulting AST matches
the user's `toSha` AST. The result classifies each step as **valid**
(our reapplication reproduces the user's end-state — no manual edits
in this step's bracket) or **divergent** (refactor failed, or AST
differs because the user did manual edits inside the same bracket).

Reorder windows are then built from contiguous runs of valid steps;
divergent steps act as window splitters and are excluded from
reordering.

This replaces the current windowing in `ReorderWindowLogger` (which
only splits on `null` / `RefactoringSpec.Other`) with a
ground-truth-checked windowing that the synthesis slice can rely on.

## Context

The current pipeline assumes that, within a `(fromSha, toSha)`
bracket, the only delta is the detected refactoring. That assumption
is false: RM groups *all* user changes in the bracket under the
detected op, including manual edits the analyser knows nothing about.
This breaks the planned end-state equivalence check for Approach A —
replaying just the spec from `fromSha` won't reproduce `toSha` when
the user also edited code by hand, and the reorder synthesis would
flag legitimate alt orderings as divergent for reasons unrelated to
the reordering itself.

The fix: validate each step before windowing. A step is only eligible
for reordering if our own reapplication of its spec reproduces the
user's `toSha` AST. Contiguous runs of validated steps are safe
reorder windows; breaks are inserted where the AST diverges.

This is a new slice; previous slices (PR #34 enumerator algorithm,
PR #35 enumerator pipeline log) shipped under a different scope.

## Goal

For each step `s` with a typed spec:

1. Compute the user-changed `.java` file set: `git diff --name-only
   s.fromSha s.toSha -- '*.java'`.
2. Borrow a worktree at `s.fromSha`. Apply `s.spec` via the existing
   JDT refactoring infrastructure.
3. Compute the *our-changed* file set (git diff between the
   worktree's HEAD and the dirty tree, scoped to `*.java`). If it
   doesn't equal the user-changed set → `AST_DIVERGED` (file-set
   mismatch). Release the worktree, stop.
4. Otherwise, for each file in the (now-shared) changed set, hash
   the post-apply AST in the worktree and the user's `toSha` AST.
   All match → `VALID`. Else → `AST_DIVERGED` (file-content
   mismatch + the list of mismatching files).

Classifications:

- `VALID` — replay reproduces user's end-state.
- `REFACTOR_FAILED` — apply errored or produced no diff.
- `AST_DIVERGED` — file-set or file-content mismatch (manual edits
  in the bracket, or our spec model misses something RM detected).
- `UNTYPED` — `spec` is null or `RefactoringSpec.Other`.

`wasPerformedByIde == true` is **not** a separate status — those
steps go through the same apply+compare path as any other typed
spec (the IDE's apply should match ours, so they should classify
`VALID`; if not, that's a real signal worth surfacing).

`ReorderWindowLogger` consumes the validator output and breaks
windows on any non-`VALID` step.

## Out of scope

- Multi-step synthesis. No replay of orderings end-to-end, no
  `altSha` for an ordering, no scoring. Slice 2 will consume this
  validator's output.
- Schema changes to `AnalysisReport`, `AlternativeTrajectory`,
  `RefactoringStep`. Validator output is in-memory + log-only.
- Frontend / dashboard work.
- Restructuring `AlternativeTrajectoryRunner.synthesiseOne()`.
  We extract its `dispatch()` body into a small `SpecDispatcher`
  so both call sites use one implementation; no other runner
  changes.

## Architecture

New package: `analysis/.../alternative/validate/`

### 1. `JavaFileAstHasher.kt`

Targeted, per-file hashing — not a whole-worktree snapshot. We only
ever hash files that appear in a git diff.

```kotlin
object JavaFileAstHasher {
    /** Parse a single .java file with JDT and return its canonical
     *  AST hash via AstSubtreeHasher.hashNode(cu). null if missing
     *  or unparseable. */
    fun hashFile(worktree: Path, relativePath: String): String?
}
```

Reuses the JDT parser config from `SpecAnchorBuilder`. Parse failure
on either side counts as a divergence.

### 2. `ChangedJavaFiles.kt`

Wraps `GitRunner` to list `.java` files changed between two refs or
between HEAD and the dirty worktree:

```kotlin
class ChangedJavaFiles(private val git: GitRunner) {
    fun between(fromSha: String, toSha: String): Set<String>
    fun fromHeadDirty(): Set<String>  // staged + unstaged
}
```

Implemented via `git diff --name-only --diff-filter=ACMRD <a> <b>
-- '*.java'` and `git status --porcelain -- '*.java'`. Renames count
as remove+add (both old and new path go in the set).

### 3. `SpecDispatcher.kt`

Extracted from `AlternativeTrajectoryRunner.dispatch(spec, worktree)`
(lines 244+):

```kotlin
class SpecDispatcher(
    private val client: RefactoringClient,
    private val sourceFolders: List<String>,
    private val classpathJars: List<Path>,
) {
    fun apply(spec: RefactoringSpec, worktree: Path): DispatchResult
}
```

`AlternativeTrajectoryRunner.dispatch()` becomes a one-line
delegation. No behaviour change.

### 4. `RefactoringStepValidator.kt`

```kotlin
data class StepValidation(
    val stepIndex: Int,
    val status: Status,
    val reason: String?,             // populated when not VALID
    val divergedFiles: List<String>?, // populated only on AST_DIVERGED
) {
    enum class Status { VALID, REFACTOR_FAILED, AST_DIVERGED, UNTYPED }
}

class RefactoringStepValidator(
    private val dispatcher: SpecDispatcher,
    private val pool: WorktreePool,
    private val parallelism: Int,
) {
    fun validate(steps: List<RefactoringStep>): List<StepValidation>
}
```

Per-step flow (parallelised over the `WorktreePool`):

1. Untyped (`spec == null` or `Other`) → `UNTYPED`. No worktree work.
2. Otherwise:
   - Compute user-changed file set via `ChangedJavaFiles.between(
     fromSha, toSha)`. If empty → `AST_DIVERGED` (user touched no
     `.java` files).
   - Borrow worktree at `fromSha`. Apply spec via `dispatcher`.
     `Failed` → `REFACTOR_FAILED`. Release. Stop.
   - Compute our-changed file set from the dirty worktree. Empty
     → `REFACTOR_FAILED` (no textual change).
   - Sets differ → `AST_DIVERGED` (file-set mismatch; reason
     carries `(only-in-user, only-in-ours)`). Release.
   - Otherwise hash each file in the shared set on both sides via
     `JavaFileAstHasher`. All equal → `VALID`. Else →
     `AST_DIVERGED` (file-content mismatch + mismatching paths in
     `divergedFiles`).
   - Release the fromSha worktree.

Caches:
- `(toSha, path) → astHash` — chained traces share `toSha` SHAs.
  Capped (e.g. 256 entries).
- `(fromSha, toSha) → Set<String>` user-changed file sets. Small
  bounded map.

No commit happens, so no `setLocalIdentity`.

### Thread safety / JDT serialisation

JDT (and the bundle's Equinox runtime) is not thread-safe.
`RefactoringClient.invokeOnBundle()` already guards every bundle
call behind a process-wide `ReentrantLock`
(`analysis/.../refactoring/RefactoringClient.kt:33`). As long as
the validator routes through `SpecDispatcher` →
`RefactoringClient`, parallel validator workers serialise on the
JDT call automatically. Everything *outside* JDT — borrowing
worktrees, computing changed-file sets via git, hashing files —
runs concurrently. Same pattern as the alt-trajectory runner.

### 5. `ReorderWindowLogger.kt` (modify)

New signature:

```kotlin
fun log(
    steps: List<RefactoringStep>,
    validations: Map<Int, StepValidation>,
    log: (String) -> Unit,
): Summary
```

Split on any step whose status is not `VALID`. The existing
untyped check is subsumed (untyped steps now produce
`Status.UNTYPED`). Per-window log line additionally identifies the
splitter's classification + reason.

`Summary` extended:
```kotlin
data class Summary(
    val totalWindows: Int,
    val eligibleWindows: Int,
    val singletonWindows: Int,
    val typedCount: Int,
    val untypedCount: Int,
    val divergentCount: Int,      // new
    val refactorFailedCount: Int, // new
)
```

### Pipeline wiring

`AnalysisPipeline.kt` between miner (~line 96) and the existing
reorder-logger call (~line 107):

```kotlin
val miner = RefactoringMinerRunner(parallelism = parallelism).run(...)

val dispatcher = SpecDispatcher(refactoringClient, sourceFolders, classpathJars)
val validatorPool = WorktreePool(reconstruction.repoDir, validatorWorktreeBase, parallelism)
val validationsList = try {
    RefactoringStepValidator(dispatcher, validatorPool, parallelism).validate(miner.steps)
} finally {
    validatorPool.close()
}
val validations = validationsList.associateBy { it.stepIndex }

// Per-step log line — one per validated step, in step-index order.
//   validator: step #12 VALID
//   validator: step #13 AST_DIVERGED — file-content mismatch [src/.../Bar.java]
//   validator: step #14 REFACTOR_FAILED — JdtRefactorer threw: ...
//   validator: step #15 UNTYPED
validationsList.sortedBy { it.stepIndex }.forEach { v -> log(formatLine(v)) }
log("validator: summary valid=… diverged=… refactorFailed=… untyped=…")

val reorderSummary = ReorderWindowLogger.log(miner.steps, validations) { line -> log(line) }
```

Pool base: a sibling of the alt-runner's worktree base (e.g.
`sessionDir/tmp/validator-worktrees/`). Closed after the validator
returns.

## Tests

`tool/analysis/src/test/kotlin/.../alternative/validate/`

- `JavaFileAstHasherTest`: identical AST → equal hash; comment-only
  change still equal (canonicalisation); whitespace-only still
  equal; structural change diverges; missing/unparseable file
  returns null.
- `ChangedJavaFilesTest`: two-SHA diff includes only `.java`;
  rename → both old and new in the set; HEAD-dirty picks up
  staged + unstaged.
- `RefactoringStepValidatorTest`: untyped → UNTYPED; dispatcher
  Failed → REFACTOR_FAILED; apply produces no diff →
  REFACTOR_FAILED; file-set mismatch → AST_DIVERGED; file-content
  mismatch → AST_DIVERGED with `divergedFiles`; all-match → VALID;
  toSha hash cache hit avoids re-hash.
- `ReorderWindowLoggerTest` (extend): pass `validations` map; new
  tests assert AST_DIVERGED / REFACTOR_FAILED act as splitters,
  the split log line includes the status, and `Summary` carries
  the new counts.

## Critical files

**New:**
- `analysis/.../alternative/validate/JavaFileAstHasher.kt`
- `analysis/.../alternative/validate/ChangedJavaFiles.kt`
- `analysis/.../alternative/validate/SpecDispatcher.kt`
- `analysis/.../alternative/validate/RefactoringStepValidator.kt`
- Tests under `analysis/src/test/kotlin/.../alternative/validate/`

**Modified:**
- `analysis/.../alternative/AlternativeTrajectoryRunner.kt` —
  `dispatch()` delegates to `SpecDispatcher`. No behaviour change.
- `analysis/.../alternative/reorder/ReorderWindowLogger.kt` —
  accept validations map; split on non-VALID; extend `Summary`.
- `analysis/.../alternative/reorder/ReorderWindowLoggerTest.kt` —
  pass through validations map; add new tests.
- `analysis/.../pipeline/AnalysisPipeline.kt` — insert validator
  stage and per-step log.

**Reused (no change):**
- `analysis/.../refactoring/anchor/AstSubtreeHasher.kt`
  (`hashNode(node)`).
- `analysis/.../refactoring/RefactoringClient.kt` (already locks).
- `analysis/.../metrics/WorktreePool.kt`.
- `analysis/.../miner/model/RefactoringStep.kt` (read-only).

## Verification

1. `./gradlew :analysis:test --tests "com.github.ethanhosier.analysis.alternative.validate.*"` passes.
2. `./gradlew :analysis:test --tests "com.github.ethanhosier.analysis.alternative.reorder.*"` passes.
3. `./gradlew :analysis:test` — no other regressions.
4. `./gradlew :analysis:detekt :analysis:ktlintCheck` clean.
5. **Real-session smoke**: rerun the session PR #35 exercised,
   eyeball:
   - Per-step `validator:` log lines in step order.
   - Plausible aggregate counts.
   - At least one `AST_DIVERGED` step's diff lines up with a
     user manual edit visible in `git diff fromSha toSha`.
   - Reorder windows are smaller / more numerous than before.

## Risks

- **Validator perf ≈ alt-runner per-step cost** — one apply + a
  few JDT parses per step. Mitigated by `(toSha, path)` cache;
  files outside the changed set aren't parsed at all.
- **Coarse spec models cause false divergences**. Right behaviour
  for the windower (better to over-split than admit a divergent
  step) but degrades eligible-window count. The diverged-files
  log surfaces what to fix.
- **JDT canonical hash isn't perfect** — member-order differences
  could cause a real divergence to look like a hash diff.
  Snapshotter tests pin comment/whitespace; member-order is a
  follow-up if it shows up.
- **IDE-performed step where IDE's apply ≠ ours** — would now
  show as `AST_DIVERGED` rather than being trusted. This is the
  honest behaviour and the divergence list will tell us what's
  off.
