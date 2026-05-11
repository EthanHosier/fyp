# Reorder synthesis via DFS + git checkout

Replace per-ordering worktree-borrow + re-index with one worktree
+ one Eclipse project init per window. DFS the prefix trie of all
alt orderings; backtrack via `git checkout --detach <parentSha>`
+ `refreshProject` (Eclipse re-stats). Each unique prefix gets one
commit; orderings sharing a prefix share the same SHAs and refs.

## Context

The previous prefix-cache `ReorderSynthesiser` borrowed a fresh
worktree for every cache-miss alt ordering, paying one full
Eclipse project init+index each time (~1.5s on the example
session). Profiling showed that init+index dominated synthesis
cost; per-spec applies were ~20-100ms.

DFS+checkout amortises the init+index across all orderings in a
window:
- One worktree, one project init per window.
- Each forward edge in the trie = one apply (~25ms).
- Each backtrack edge = one `git checkout --detach <parent>` +
  `refreshProject` (~20ms).

For the example session the synthesis stage drops from 12s
(prefix-cache) to ~5.4s — ~2.2× faster. The speedup grows with
window size since indexing cost is amortised over more applies.

### Why git checkout instead of JDT undo

An earlier attempt (now abandoned) used JDT's
`Change.perform`-returned undo Change to revert between siblings.
After applying that undo, the next forward apply parsed against a
**stale ICompilationUnit/IFile buffer cache** (or the JavaModel
element-info cache below it) — invalidation via `refreshLocal` +
`discardWorkingCopy` didn't reach the layer that mattered, and
the resulting commits had stray characters from elsewhere in the
file.

Git checkout + refreshLocal goes through Eclipse's standard
"files changed externally" pathway, which JDT exercises on every
IDE keystroke that touches the filesystem. Robust and
well-tested. Empirically: clean commits matching the prefix-cache
reference output byte-for-byte (modulo whitespace / declaration
order, which our canonical AST hashing absorbs).

## Out of scope

- Metrics / scoring on synthesised SHAs. Slice 2b territory.
- Cross-window parallelism. Single-threaded DFS keeps state
  management simple.
- Replacing single-step `AlternativeTrajectoryRunner` (per-step
  replay of user's actual ops). Stays unchanged.

## Architecture

### Per-window flow

```
borrow worktree at fromSha          [1× per window]
client.withBatchSession {
    init+index Eclipse project       [1× per window]
    DFS the prefix trie:
        forward into child:
            outcome = dispatcher.apply(spec, worktree)
            addAll; commit; branchForce
        backtrack from child:
            git checkout --detach <parent>
            client.refreshProject()
}
release worktree
```

### Prefix trie

Built once per window from the enumerator's orderings (excluding
the user's identity). Children indexed by spec idx in
insertion order. DFS visits each node exactly once; each unique
prefix gets one materialised commit.

### Branch refs

Per-prefix, not per-ordering:

```
reorder/win<W>/path/<dash-joined-prefix>
e.g. reorder/win0/path/0-2-1
```

Two orderings sharing a prefix share the same refs.
`ReorderOrdering.branchRefs` and `stepShas` stay parallel to
`permutation`; entries deduplicate naturally where prefixes
overlap.

### Failure semantics

If a forward apply Failed (or produced no textual change at
depth k):
- Record the prefix as `Failed`; skip its subtree.
- `git checkout --detach <parent>` immediately to clean up the
  apply's partial dirty state, then `refreshProject`.
- Sibling subtrees still get explored normally.
- Every ordering passing through the failing prefix has
  `terminalSuccess = false`, `failedAt = k - 1`, and
  `stepShas`/`branchRefs` truncated to length `k - 1`.

### Backtrack mechanics

After a successful forward apply + commit, HEAD is at the child's
SHA, working tree matches it, index matches it. Recursing into
children just adds more commits on top.

When DFS returns from a child, we backtrack:
- `git checkout --detach <parentSha>` — moves HEAD + index +
  working tree to parent state in one git operation.
- `refreshProject()` — `IProject.refreshLocal(DEPTH_INFINITE)`
  on the bundle side, telling Eclipse to re-stat every file. JDT
  resource listeners pick up the changes and invalidate per-file
  buffers.

The next sibling then forwards from the cleanly-restored parent
state with Eclipse's view in sync.

## Critical files

**New:**
- `analysis/.../alternative/synthesise/PrefixTrie.kt` — small
  data structure (~50 LoC).
- `analysis/.../alternative/synthesise/PrefixTrieTest.kt` — 6
  tests for trie shape and lookup.

**Modified:**
- `analysis/.../alternative/synthesise/ReorderSynthesiser.kt` —
  rewritten. DFS over the trie inside `withBatchSession`; forward
  via `SpecDispatcher.apply`; backtrack via
  `worktreeGit.checkoutDetach(parent)` + `refreshProject()`.
- `analysis/.../alternative/synthesise/ReorderSynthesiserTest.kt`
  — `WindowSplitting` group unchanged; `Synthesis` group rewritten
  for the new ref pattern + DFS semantics. New counters
  (`appliesIssued`, `backtracksIssued`).
- `analysis/.../pipeline/AnalysisPipeline.kt` — log line updated
  to print apply/backtrack counts (was `prefixCacheHits`).
- `analysis/.../refactoring/RefactoringClient.kt` — adds
  `refreshProject()`.
- `refactoring-bundle/.../JdtRefactorer.kt` — adds
  `refreshProject()` `@JvmStatic`.
- `refactoring-bundle/.../internal/RefactoringHost.kt` — adds
  `refreshProject()` (calls `IProject.refreshLocal(DEPTH_INFINITE)`).

**Deleted:**
- `analysis/.../alternative/synthesise/PrefixCache.kt` and its
  test — superseded by the trie + DFS.

**Reused (no change):**
- `RefactoringClient.withBatchSession` (existing — keeps the
  Eclipse project alive for the duration of one window).
- `SpecDispatcher.apply`.
- `SpecDependencyAnalyzer.analyze`, `TopologicalEnumerator.enumerate`.
- `WorktreePool.borrow / release`.
- `GitRunner.addAll / hasStagedChanges / commit / branchForce /
  setLocalIdentity / checkoutDetach`.
- `ReorderTrajectory` schema types.

## Tests

### Unit (fakes for `applySpec` and `refreshProject`)

1. `dfs visits each prefix once across all orderings` — track
   apply call count; assert it equals the distinct-prefix count
   in the trie. For N=3 alt orderings: 13 prefixes → 13 applies.
2. `branch ref shared across orderings with same prefix` —
   orderings sharing prefix `[a]` should both list the same SHA
   + ref at index 0.
3. `partial failure in subtree skips subtree continues siblings`
   — fake fails at specIdx=1; assert each ordering's `failedAt`
   matches the depth where spec 1 first appears.
4. `user identity ordering is dropped`.
5. `windows smaller than 2 are skipped`.
6. `commits are reachable in shadow repo via the per-prefix refs`
   — round-trip verify.

`WindowSplitting` group: 6 cases migrated from the deleted
`ReorderWindowLoggerTest` covering split-on-non-VALID, missing
validations, empty input, summary counts.

`PrefixTrieTest`: 6 cases for trie shape, sharing, ordering.

### Integration smoke (manual)

Run the analysis pipeline on the example session. Verify:
- Synthesis log shows clean DFS traversal with apply/backtrack
  pairs balanced.
- All synthesised SHAs are reachable from the per-prefix refs.
- Spot-check file content at a previously-broken position
  (e.g. window-1, prefix `[1]`) — should match the fromSha state
  with only spec 1 applied.

## Verification

1. `./gradlew :analysis:test :refactoring-bundle:test` — green.
2. **Real-session smoke**: rerun pipeline. Compare wall-clock
   for the synthesis stage against main (prefix-cache) — expect
   ~2× speedup on the example session. Spot-check a synthesised
   SHA's file content for cleanliness.
3. **Branch reachability**: pick any synthesised SHA from
   `reorderTrajectories[0].orderings[0].stepShas`, check it out
   from the shadow repo; verify the file content matches the
   ordering's expected post-apply state.

## Risks

- **JDT cache staleness despite refreshProject.** If `refreshLocal`
  doesn't reach some deeper cache layer (e.g. JavaModelManager
  element info), the next forward apply could parse stale
  content. **Mitigation:** the path we use is the same one IDE
  users exercise constantly (Eclipse handling files modified
  externally), so the risk is low. Empirically clean on the
  example session.
- **`git checkout` cost on large worktrees.** Each backtrack
  rewrites whatever files differ between child and parent. For
  refactorings that touch one Java file, this is a single-file
  write + a few stat calls — tens of ms. For pathological
  refactorings touching many files (Move Class, Rename Package),
  cost grows linearly. Still bounded by the refactoring's actual
  scope; not a per-project cost.
- **Parallelism.** `withBatchSession` holds the bundle lock for
  the full window. Cross-window parallelism would require
  per-window project caches in the bundle — out of scope here.
