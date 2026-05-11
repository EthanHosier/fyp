# Implementation plan: per-bracket batch session in RefactoringStepValidator + drop validator parallelism

Wrap each bracket's apply loop in `RefactoringClient.withBatchSession` so multi-step
brackets pay one project init+index instead of N. Drop the validator's
`Executors.newFixedThreadPool` plumbing — `RefactoringClient.invokeOnBundle` already
serialises every bundle call via `ReentrantLock`, so the executor only overlaps
non-bundle work (cheap hashing, git diffs) and adds avoidable complexity.

## Context

`RefactoringStepValidator` validates each `(fromSha, toSha)` bracket by replaying its
steps on a borrowed worktree and AST-comparing the result to the user's `toSha`. Today
the per-bracket apply loop calls the bundle once per step
(`RefactoringStepValidator.kt:148-160`), and the bundle's `RefactoringHost.run` does a
fresh `ProjectInitializer.initProject` + `IndexingGate.waitForIndex` per call when no
session is held. For multi-step brackets, this pays the JDT init/index cost N times
instead of once.

`ReorderSynthesiser` already has the right pattern: a `runInSession: (() -> Unit) -> Unit`
seam (`ReorderSynthesiser.kt:63, :81`) defaulting to `client.withBatchSession`. We
replicate that seam in the validator and wrap the bracket's apply loop with it.

The current validator parallelism (`Executors.newFixedThreadPool(parallelism)` at
`RefactoringStepValidator.kt:89-104`) is misleading: every bundle call goes through
`RefactoringClient.invokeOnBundle` which is `lock.withLock { ... }`
(`RefactoringClient.kt:49`). So bundle ops are *already* mutually exclusive — the
executor only overlaps the local-CPU bits (`changedJavaFilesFromHeadDirty`,
`JavaFileAstHasher.hashFile`, file copies). Net: speedup is real but small, and the
executor + futures + shutdown-await machinery is dead weight given how fast those
non-bundle bits are.

## Out of scope

- Migrating user-side hashing in `hashesAtSha` to `JavaFileAstHasher.hashFileAtSha`.
- Removing the CLI `--parallelism` flag (other stages still use it).
- Cross-bracket batch sharing.
- Caching changes (`toShaHashCache`, `changedFilesCache`).

## Architecture

### New seam

Mirror the synthesiser's pattern. Primary ctor adds a `runInSession` seam with a
no-op default; convenience ctor wires the real one off `RefactoringClient`.

```kotlin
class RefactoringStepValidator(
    private val applySpec: (RefactoringSpec, Path) -> SpecDispatcher.Result,
    private val pool: WorktreePool,
    private val shadowGit: GitRunner,
    private val debugDumpDir: Path? = null,
    private val runInSession: (() -> Unit) -> Unit = { it() },
) {
    constructor(
        client: RefactoringClient,
        pool: WorktreePool,
        shadowGit: GitRunner,
        dispatcher: SpecDispatcher = SpecDispatcher(client),
        debugDumpDir: Path? = null,
    ) : this(
        applySpec = dispatcher::apply,
        pool = pool,
        shadowGit = shadowGit,
        debugDumpDir = debugDumpDir,
        runInSession = { body -> client.withBatchSession(body) },
    )
    ...
}
```

### Per-bracket session wrap

Wrap the apply for-loop (`RefactoringStepValidator.kt:148-160`) in
`runInSession { ... }`. Use a `var failure` to short-circuit out of the session block
without requiring a non-local return through a non-inline lambda. Hash worktree files
stays *outside* the session — no bundle calls there.

### Drop the executor

In `validate(...)`, replace `Executors` / futures / shutdown-await with sequential
`flatMap`. Drop `parallelism: Int` from both ctors.

### Pipeline wiring

`AnalysisPipeline.kt:121-127` switches to the client-based ctor and stops passing
`parallelism`. `AnalysisPipeline.parallelism` and `defaultParallelism()` stay (used by
`MetricsRunner`, CLI flag).

## Critical files

**Modified:**
- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/alternative/validate/RefactoringStepValidator.kt`
- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/pipeline/AnalysisPipeline.kt`
- `analysis/src/test/kotlin/com/github/ethanhosier/analysis/alternative/validate/RefactoringStepValidatorTest.kt`
  — mechanical removal of `parallelism = 1` from every validator construction.

**Reused (no change):**
- `RefactoringClient.withBatchSession` (`RefactoringClient.kt:80`).
- `SpecDispatcher`.
- `WorktreePool` (works fine with sequential borrows).
- `JavaFileAstHasher.hashFile` and `hashesAtSha`.

## Tests

Existing tests pass after the `parallelism = 1` removal — they construct via the
primary `applySpec` ctor and inherit the no-op session default.

New test:

1. `multi_step_bracket_invokes_runInSession_once_around_the_apply_loop` — primary
   ctor with a counting `runInSession` and recording `applySpec`. Two-step bracket;
   assert `runInSession` invoked once and both applies happened inside.

## Verification

1. `./gradlew :analysis:test :refactoring-bundle:test` — green.
2. Real-session smoke for wall-clock check on multi-step brackets.
3. Determinism: same session twice → identical verdicts.

## Risks

- **`return@runInSession` non-local return** worked around with a `failure` var.
- **No-op session default in tests** is intentional; new test covers the wrap
  invariant.
