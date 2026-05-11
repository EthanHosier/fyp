# `withBatchSession` batch primitive for multi-step refactoring

Expose a `RefactoringClient.withBatchSession` block that lets a
caller run multiple consecutive `apply()` calls against the same
worktree with the JDT project initialised and indexed **once**
for the whole batch. Caller writes their own loop, picks their
own commit/ref strategy.

## Context

Current shape: every refactoring application goes through
`RefactoringClient.invokeOnBundle` →
`RefactoringHost.run` → `ProjectInitializer.initProject` →
`IndexingGate.waitForIndex` → run → `project.delete`. The full
init/index/teardown cycle runs **on every spec**, even when a
caller is about to apply a second spec on the same worktree.

For multi-step alternative-trajectory synthesis, we replay reorder
windows of N specs in different orders. With the current shape, an
N-spec ordering pays N full reindexes. JDT indexing dominates
per-call cost; reusing the indexed project across the batch is
the obvious lever.

The design also needs each successful step to be addressable as a
named ref afterwards, because per-checkpoint metrics will run on
those intermediate states. Worktree commits alone aren't enough —
when `WorktreePool.release()` calls `git worktree remove`, the
worktree's HEAD goes away and the commits become unreachable
(GC'd). The existing `AlternativeTrajectoryRunner.synthesiseOne`
pattern handles this with `worktreeGit.commit` +
`shadowGit.branchForce(ref, sha)` to promote each commit to a
permanent ref before release.

## Out of scope

- A `SpecDispatcher.applyAll` convenience wrapper. Earlier draft
  shipped one with mandatory commit + branchForce per step, but
  that bakes in a synthesis-specific commit policy. Different
  callers (validator, future metric prober) want different loop
  bodies; the right primitive is the session, not the loop.
- Wiring into Slice 2a synthesis or the validator's bracket flow.
  Both can adopt `withBatchSession` later — independent slices.
- Caching the JDT project across separate top-level calls. The
  cache lifetime is exactly one `withBatchSession` invocation.
  Single-spec `apply()` keeps current init+teardown semantics —
  zero risk of regression on existing call paths.

## Architecture

### Key insight

The bundle doesn't need to know about the spec **list**. It only
needs to know "don't tear down the project after this call —
another one is coming on the same root." So the change is two
trivial flag/cache-control methods on the bundle, plus a wrapper
on the analysis side that drives the existing per-spec dispatch
in a loop with the flag set.

### Shape

```
caller code (e.g. synthesis driver):
  client.withBatchSession {                     [holds bundle lock]
    setKeepProject(true)                        [bundle: flag flips]
    try {
      for spec in specs:
        SpecDispatcher.apply(spec, worktree)    [existing single-spec path]
        ─ first call: full init+index, project NOT torn down
        ─ subsequent calls: cache hit, skip init+index
        // caller's own commit / branchForce / break logic here
    } finally {
      clearProjectCache()                       [bundle: tears down + evicts]
      setKeepProject(false)
    }
  }
```

### Bundle-side: cached project + keep flag

`RefactoringHost.kt` gains three process-wide volatile fields:
- `cachedProject: IJavaProject?`
- `cachedProjectRoot: String?`
- `keepProjectFlag: Boolean = false`

`RefactoringHost.run`:
1. **Cache hit** (`keepProjectFlag && cachedProjectRoot == projectRoot && cachedProject != null`):
   - Skip `ProjectInitializer.initProject`.
   - Still call `IndexingGate.waitForIndex` — fast-path returns
     immediately when the index has settled, expensive only on
     first call where it builds the full index.
   - Run the body.
   - **Don't** tear down on exit. The project stays cached.
2. **Cache miss**:
   - If anything is cached, tear it down first (different root).
   - Init project, index it.
   - If `keepProjectFlag`: store in cache, **don't** tear down on exit.
   - Else: current behaviour — tear down on exit (no caching).

`JdtRefactorer.kt` gains two `@JvmStatic` methods:
- `setKeepProject(keep: Boolean): String` — flips the flag, returns `OutcomeJson.ok([])`.
- `clearProjectCache(): String` — tears down whatever's cached, returns ok.

The bundle stays JSON-string-free for inputs (just primitive
booleans and outcome strings — current ABI shape).

### Analysis-side: batch session helper

`RefactoringClient.kt` gains:
- `withBatchSession(block: () -> T): T` — acquires the existing
  `ReentrantLock` for the duration of `block`, sets keep-flag
  before, clears cache + resets flag after. The lock is reentrant
  so individual `invokeOnBundle` calls inside the block don't
  deadlock.
- `setKeepProject(keep: Boolean)` — `invokeOnBundle` wrapper.
- `clearProjectCache()` — `invokeOnBundle` wrapper.

### Caller usage

`SpecDispatcher.kt` is **not** modified. The synthesis slice (or
any other batch caller) writes its own loop:

```kotlin
client.withBatchSession {
    for ((k, spec) in specs.withIndex()) {
        when (val r = dispatcher.apply(spec, worktree)) {
            is Result.Failed -> { record failure; break }
            is Result.Ok -> {
                worktreeGit.addAll()
                val sha = worktreeGit.commit("alt step #$k")
                shadowGit.branchForce("alt/win$winIdx/ord$ordIdx/step-$k", sha)
            }
        }
    }
}
```

No reset-to-fromSha needed — JDT writes each apply's edits to disk
incrementally and the caller commits them sequentially. The
worktree HEAD walks from `fromSha` → `sha_0` → `sha_1` → …
naturally.

Caller chooses commit policy, ref namespacing, failure handling,
and what to record. The session primitive only owns "JDT cache
lifetime" — nothing else.

### Cache lifetime + concurrency

The `withBatchSession` block holds `RefactoringClient.lock` for
the full batch. Since `lock` is a `ReentrantLock` and every
`invokeOnBundle` call inside the block also acquires it (re-entrant
no-op), no other caller can interleave. So the keep-flag and
cached project state are bounded by the batch.

After `withBatchSession` returns:
- `clearProjectCache()` has torn down the cached project.
- `setKeepProject(false)` has reset the flag.
- Next caller (`apply()` or another `applyAll()`) starts from a
  clean state.

## Critical files

**Modified (bundle side):**
- `refactoring-bundle/.../internal/RefactoringHost.kt` — add
  static cache, modify `run()` to consult cache + keep-flag.
- `refactoring-bundle/.../JdtRefactorer.kt` — add
  `setKeepProject` and `clearProjectCache` `@JvmStatic` methods.

**Modified (analysis side):**
- `analysis/.../refactoring/RefactoringClient.kt` — add
  `withBatchSession`, `setKeepProject` (private),
  `clearProjectCache` (private), `initCount` (test-only).

**New:**
- `analysis/.../refactoring/RefactoringClientBatchSessionTest.kt`
  — drives the bundle for real to confirm caching works
  end-to-end.

**Reused (no change):**
- `RefactoringClient.invokeOnBundle` (existing reflection path).
- `SpecDispatcher.apply` — unchanged. Callers use it inside their
  own `withBatchSession` block.
- `RefactoringStepValidator`, `AlternativeTrajectoryRunner`,
  `WorktreePool`, `GitRunner` — all unchanged.

## Tests

### `RefactoringClientBatchSessionTest` (drives real bundle)

1. `two_specs_in_session_index_once` — `withBatchSession { apply;
   apply }` two specs on a fixture project. Assert both succeed
   and `client.initCount()` increments by exactly 1.
2. `two_specs_outside_session_index_twice` — `apply; apply` (no
   session). Assert `client.initCount()` increments by 2. Locks
   in the perf-baseline behaviour the new code optimises against.
3. `session_evicts_cache_on_normal_exit` — run a session, then a
   single-spec `apply()` on a *different* worktree. Assert init
   counter increments (cache was evicted, fresh project built).
4. `session_evicts_cache_on_exception` — `withBatchSession` body
   throws. After the throw, single-spec `apply()` on a *different*
   worktree still does a fresh init. Catches a regression where
   the `finally` block doesn't run.
5. `nested_session_is_a_no_op` — calling `withBatchSession` from
   inside an outer one shouldn't double-toggle the keep-flag in
   a way that breaks. Validates the reentrant-lock semantics.

### Existing tests

All `RefactoringStepValidatorTest` and other existing tests must
pass unchanged — single-spec `apply()` path is untouched.

## Verification

1. `./gradlew :analysis:test :refactoring-bundle:test` — green.
2. **Real-session smoke**: rerun analysis pipeline with a
   throwaway script that wraps a multi-spec bracket in
   `withBatchSession`, times it vs the same specs sequenced
   through bare `apply()`. Expect the per-call indexing cost
   (`IndexingGate.waitForIndex` measured today as ~hundreds of
   ms) to drop to ~zero on calls 2..N inside the session.

## Risks

- **JDT in-process state across consecutive applies.** When a
  refactoring fails mid-`change.perform`, JDT's change object
  *should* roll back partial edits, but I don't fully trust that.
  **Mitigation:** any per-step failure short-circuits the batch.
  `clearProjectCache` runs in finally regardless. The next caller
  gets a fresh project. So worst case: "step k fails → batch
  ends, project torn down, next caller fine."
- **Index incrementality.** We reuse the cached project's index
  across edits made by previous applies. JDT's incremental
  indexer SHOULD propagate these via Eclipse's resource change
  events, and `IndexingGate.waitForIndex` blocks until search is
  ready. **Mitigation:** every batch step still calls
  `IndexingGate.waitForIndex` — cheap on incremental updates,
  expensive only on the first (full-build) call. If we ever see
  incrementality bugs, a per-step `project.refreshLocal` is the
  fallback.
- **Worktree-state surprise.** Caller passes `fromSha` so we can
  assert the worktree HEAD matches before we start. A precondition
  failure means caller bug, not applyAll bug.
- **Concurrency.** `withBatchSession` holds the bundle lock for
  the full batch. Other callers queue. The JDT lock makes
  parallel bundle calls impossible at the bundle layer regardless,
  so this isn't a regression — same effective throughput.
- **Memory of cached project.** A very long batch keeps the
  IJavaProject + indexed metadata alive in memory. **Mitigation:**
  `clearProjectCache` runs in finally; can't leak past
  `withBatchSession`.

## Design notes

### Why no `applyAll` convenience wrapper

An earlier draft included `SpecDispatcher.applyAll(specs, …)` that
encapsulated a "loop + commit + branchForce + record outcomes"
pattern. Dropped because it bakes in a synthesis-specific commit
policy: the validator's bracket flow doesn't commit between steps
(it just hashes the terminal worktree state); a future metric
prober might commit only on selected steps. Two batch primitives
covering similar ground is worse than one session primitive that
each caller wraps with their own loop. We can extract a helper
later if a second caller wants the exact same loop body.

### Why not a JSON batch endpoint on the bundle

Original draft: bundle gets `applyMultiple(projectRoot,
specsJson)` reflection method, hand-rolls JSON parsing (no
kotlinx.serialization in OSGi classpath), has a parallel 25-arm
dispatch table mirroring the existing per-op machinery, captures
per-step file-content snapshots, ships them back. ~600 LoC.

The cache-flag approach delivers the same indexing-reuse win with
~80 LoC and zero new dispatch arms. The trade-off is that the
cache state lives across reflection calls within the batch; this
is bounded by the `withBatchSession` lock so it doesn't leak.
