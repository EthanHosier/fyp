# Implementation plan: terminal AST-divergence filter for ReorderSynthesiser

After the DFS completes each ordering, verify its terminal SHA reaches the
same end-state as the user's `windowToSha` (AST-equivalent over the user-
changed file set). Filter divergent orderings out of the report and log
their count + which file(s) diverged.

## Context

`ReorderSynthesiser` currently materialises every alt ordering as a
chain of commits. The DAG-based dependency analyzer
(`SpecDependencyAnalyzer` + `TopologicalEnumerator`) is *over-optimistic*
about commutativity: two orderings the DAG says reach the same end-state
may in fact produce different trees. Without a check, the report can
contain orderings whose terminal tree disagrees with the user's
`windowToSha` — those are useless for Slice 2b scoring (you'd be
comparing process cost across non-equivalent end-states).

This is exactly the same equivalence problem `RefactoringStepValidator`
already solves for *single brackets* — it hashes per-file ASTs at the
synthesised SHA against the user's `toSha` over the bracket's
user-changed file set
(`RefactoringStepValidator.kt:134, :141, :191-205`). We reuse the same
machinery here, but at the *window* level: `windowFromSha → windowToSha`
file set, terminal-SHA hashes vs user `windowToSha` hashes.

### Existing primitives we reuse

- `AstSubtreeHasher.hashNode` — SHA-256 of a canonicalised AST string.
  `bodyDeclarations` are sorted by `name(paramTypes)` so method-order
  permutations hash equivalently. Lives at
  `analysis/.../refactoring/anchor/AstSubtreeHasher.kt:29`.
- `JavaFileAstHasher.hashFile(worktree, relativePath): String?` — wraps
  the parser + hasher. `null` on missing/unparseable
  (`analysis/.../alternative/validate/JavaFileAstHasher.kt:23`).
- `GitRunner.changedJavaFilesBetween(fromSha, toSha)` — used by the
  validator at `RefactoringStepValidator.kt:218`. Same call here for
  the window's user-changed set.

## Out of scope

- Hash **caching across windows**. Validator caches `(toSha, path) →
  hash`; we won't share across pipeline stages. Premature.
- Hash check at every DFS node. Only **terminal prefixes** (length ==
  `typedSpecs.size`) are checked — non-terminal prefixes are
  intentionally partial states and need not match anything.
- Surfacing divergent orderings on the dashboard. They're filtered out;
  dashboard sees a clean list.
- Hashing/comparing files outside the user-changed set. Files the user
  didn't touch in this window are uninteresting.

## Architecture

### Where the check happens

Inside `walkTrie`, immediately after a successful commit, when
`prefix.size == typedSpecs.size` (the prefix is a leaf, i.e. a complete
ordering). The worktree is *already* at the terminal state — no extra
checkout. Compare per-file hashes against pre-computed user hashes for
the window. Record the verdict on the `Materialised` outcome.

Why inline (not a post-pass): post-pass would have to checkout each
terminal SHA again, defeating the DFS+cache speedup. Inline pays one
hash-pass per terminal, on a worktree we already hold.

### Pre-compute user hashes per window

`ReorderSynthesiser.run`, per window, before the worktree borrow:

```kotlin
val userChanged: Set<String> = shadowGit.changedJavaFilesBetween(
    window.first().fromSha,
    window.last().toSha,
)
val userHashes: Map<String, String?> =
    userChanged.associateWith { JavaFileAstHasher.hashFileAtSha(shadowGit, windowToSha, it) }
```

**Subtlety:** `JavaFileAstHasher.hashFile` reads from a worktree, but
the user's `windowToSha` files aren't on disk anywhere convenient
during synthesis. Add a `hashFileAtSha(git, sha, path)` that reads via
`git show <sha>:<path>` — no worktree needed, strictly cheaper than
borrowing a second worktree.

### New verdict on `NodeResult.Materialised`

```kotlin
private sealed interface NodeResult {
    data class Materialised(
        val sha: String,
        val ref: String,
        val terminalDivergence: TerminalDivergence? = null,
    ) : NodeResult
    data class Failed(val reason: String) : NodeResult
}

/** Non-null only when this prefix is a terminal AND its hash didn't
 *  match the user's windowToSha. */
private data class TerminalDivergence(val divergedFiles: List<String>)
```

Non-terminal prefixes always have `terminalDivergence == null` (no
check ran). Terminals have `null` on match, populated on mismatch.

### `walkTrie` change

After the existing commit/branchForce block (currently
`ReorderSynthesiser.kt:299-302`):

```kotlin
val divergence: TerminalDivergence? =
    if (prefix.size == typedSpecs.size) {
        checkTerminalDivergence(
            worktree = worktree,
            userChanged = userChanged,
            userHashes = userHashes,
            log = log,
            prefix = prefix,
        )
    } else null

prefixOutcomes[prefix] = NodeResult.Materialised(
    sha = sha, ref = ref, terminalDivergence = divergence,
)
```

`checkTerminalDivergence`:
- Compute `ourHashes = userChanged.associateWith { JavaFileAstHasher.hashFile(worktree, it) }`
  (matches `RefactoringStepValidator.kt:181`).
- Find mismatching paths via the validator's predicate
  (`ours == null || theirs == null || ours != theirs`).
- Return `null` on full match; `TerminalDivergence(mismatching)` on
  mismatch.
- Emit a log line either way:
  - Match: `[depth N prefix=… terminal]: ast-match ✓`
  - Mismatch: `[depth N prefix=… terminal]: ast-divergence — N file(s) differ: [foo.java, bar.java]`

### `buildOrdering` and filter in `run`

`buildOrdering` propagates the terminal divergence into the
`ReorderOrdering`:

```kotlin
@Serializable
data class ReorderOrdering(
    val orderIndex: Int,
    val permutation: List<Int>,
    val permutationLabels: List<String>,
    val stepShas: List<String>,
    val branchRefs: List<String>,
    val terminalSuccess: Boolean,
    val failedAt: Int? = null,
    /** Non-null when the ordering completed all steps but the
     *  terminal AST diverged from the window's `windowToSha`. */
    val terminalDivergedFiles: List<String>? = null,
)
```

Then in `run`, filter divergent orderings out of the report:

```kotlin
val orderingResults = altOrderings.mapIndexed { … buildOrdering(…) }
val (kept, divergent) = orderingResults.partition { it.terminalDivergedFiles == null }
divergedOrderings += divergent.size
trajectories.add(ReorderTrajectory(…, orderings = kept))
```

Schema keeps the field for diagnostic transparency, but the report's
`orderings` only contains end-state-equivalent ones — directly usable
by Slice 2b. Synthesised commits/refs for divergent orderings stay in
the shadow repo for offline forensics; no rollback.

### Summary additions

```kotlin
data class Summary(
    …,
    val terminalsChecked: Int,           // count of terminal hash-checks run
    val terminalsAstMatched: Int,
    val terminalsAstDiverged: Int,       // == orderings filtered out
)
```

Pipeline log line gets:

```
reorder synth: … synthesised X orderings, Y commits, Z apply/backtracks;
  terminal AST: A matched, B diverged (B filtered out)
```

## Critical files

**Modified:**
- `analysis/.../alternative/synthesise/ReorderSynthesiser.kt` — new
  `userChanged` / `userHashes` precompute per window, terminal hash
  check inside `walkTrie`, filter in `run`, summary fields.
- `analysis/.../alternative/validate/JavaFileAstHasher.kt` — add
  `hashFileAtSha(git, sha, relativePath): String?` reading via
  `git show sha:path`.
- `analysis/.../metrics/model/ReorderTrajectory.kt` — add
  `terminalDivergedFiles: List<String>? = null` to `ReorderOrdering`
  (default null preserves backward compatibility).
- `analysis/.../alternative/synthesise/ReorderSynthesiserTest.kt` —
  new tests (below).

**Reused (no change):**
- `AstSubtreeHasher.hashNode`.
- `JavaFileAstHasher.hashFile` (worktree side).
- `GitRunner.changedJavaFilesBetween`.

## Tests

### `ReorderSynthesiserTest`

1. `terminal_matching_user_toSha_keeps_ordering` — Fake apply produces
   files matching the user's `windowToSha`. Assert ordering kept;
   summary `terminalsAstMatched == 1`.
2. `terminal_diverging_from_user_toSha_filters_ordering` — Fake apply
   produces different content. Assert ordering absent from
   `trajectories[0].orderings`; summary `terminalsAstDiverged == 1`.
   Logs contain `"ast-divergence"`.
3. `non_terminal_prefixes_are_not_hash_checked` — Spy hash function;
   for orderings `[0,1,2]` and `[0,2,1]` (5 prefixes total, 2
   terminals) assert hash calls happen only on terminal prefixes (×
   user-changed-set size).
4. `divergent_terminal_keeps_branch_refs_in_shadow_repo` — Even though
   filtered from the report, `shadowGit.branchForce` was still called
   for every materialised prefix.
5. `user_toSha_unparseable_file_treated_as_divergence` —
   `hashFileAtSha` returns null for one file → `null != ours` →
   counted as divergence and ordering filtered.

### `JavaFileAstHasherTest` (new)

6. `hashFileAtSha_matches_hashFile_for_committed_content` — tempdir
   git repo, commit a `.java`, both APIs produce the same hash.
7. `hashFileAtSha_returns_null_for_missing_path` — path absent at SHA
   returns null.

### Existing tests

All existing `ReorderSynthesiserTest` cases stay green. Existing
fixtures produce textually matching trees in the success path.

## Verification

1. `./gradlew :analysis:test :refactoring-bundle:test` — green.
2. **Real-session smoke** on the example session
   (`/Users/ethanhosier/Desktop/random/fyp/.../37e269ae-…`): inspect
   the new `terminal AST: …` summary line. Confirm at least one window
   shows `terminalsAstMatched > 0`. If a window shows divergences,
   eyeball the file list in the per-ordering log line.
3. **Determinism**: re-run twice on the same session; the
   matched/diverged counts must be identical.

## Risks

- **`git show` performance.** One `git show` per (windowToSha, file)
  per window. For 6 files in a window that's 6 forks — small, not
  free. Acceptable now; if hot, batch via `git cat-file --batch`.
- **Canonical hash misses an equivalence we want.** `AstSubtreeHasher`
  sorts methods by `name(paramTypes)` so it tolerates declaration-
  order reshuffles but not e.g. semantic-preserving local rewrites.
  Same caveat the validator already lives with.
- **Schema drift.** New nullable field on `ReorderOrdering` defaults
  to null; older serialised reports deserialise unchanged.