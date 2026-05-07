# Implementation plan: Reorder enumerator (Approach A — slice 1)

Single focused PR delivering the **dependency DAG + topological
enumerator** for a window's mined refactoring specs. No synthesis, no
scoring, no schema changes, no frontend. The deliverable is a pure
algorithm module that, given `List<RefactoringSpec>`, returns the set
of valid orderings — so the logic can be inspected and signed off
before any wiring.

This is the first slice of Approach A from
`tool/PLAN-multi-checkpoint-alts.md` (topologically-constrained step
reordering). Subsequent PRs will: (a) wire in multi-step synthesis,
(b) add end-state equivalence check, (c) extend `DerivedMetricsRunner`
to score `J(τ_alt)`, (d) update `AlternativeTrajectory` schema +
frontend polyline rendering.

## Context

The pipeline already produces single-step alts via
`AlternativeTrajectoryRunner.synthesiseOne()` — one `(fromSha, spec)
→ altSha` per typed mined refactoring. The thesis question (interim
report §1.3) requires **trajectory-level** counterfactuals:
permutations of the same op set that traverse different intermediate
states but rejoin at the same end-state. PR1 just landed AST-anchored
specs (`PLAN-ast-anchor-specs.md`), which are the prerequisite for
re-resolving an op against a body whose line numbers shifted because
of a prior op in the window.

Approach A's claim: keep the *set* of refactorings the user performed
in a window unchanged, search over valid permutations of their
**order**. Per the user's note in `PLAN-multi-checkpoint-alts.md:1-7`:
*assuming any valid ordering reaches the same final AST, the upper
bound on intermediate states is 2ⁿ (each spec ∈/∉ prefix); the DAG
prunes that hard.*

This PR delivers only the *enumeration* primitive. Synthesising,
scoring, and rendering happen in follow-ups, after the user reviews
the orderings the analyser produces on real windows and confirms the
edge logic is correct.

## Goal

Given `List<RefactoringSpec>` (the typed specs mined inside a window),
produce:

1. A `SpecDag` — nodes = specs, edges = `i → j` iff `spec[j]` cannot
   be applied before `spec[i]`.
2. The set of valid topological orderings of that DAG, bounded by an
   enumeration budget (default n ≤ 7 → up to 5040 perms; in practice
   the DAG cuts this hard).

The user can then run this on representative windows from collected
sessions, eyeball the edge derivations and orderings, and decide
whether to greenlight wiring it into synthesis.

## Out of scope (explicit)

- Window selection. Caller passes a `List<RefactoringSpec>`. How
  windows are chosen is a follow-up.
- Multi-step synthesis. No `JdtRefactorer` calls, no worktree replay,
  no commits, no `altSha` emitted.
- Schema changes. `AlternativeTrajectory`, `AnalysisReport`,
  `RefactoringStep`, `RefactoringSpec` — all untouched.
- Scoring extension. `DerivedMetricsRunner.computeAltProcess()`
  unchanged.
- Frontend. No dashboard work.
- Beam search beyond the exhaustive budget. v1 returns
  `truncated=true` when the orderings would exceed the budget; the
  caller can decide whether to skip or fall back to the user's
  observed ordering.
- Approach B (coalescing).
- End-state equivalence check (deferred to the synthesis PR — only
  needed once we actually replay an ordering).

## Architecture

New self-contained Kotlin package:
`tool/analysis/src/main/kotlin/com/github/ethanhosier/analysis/alternative/reorder/`

Pure module — no JDT, no IO, no worktree, no `RefactoringClient`.
Operates entirely on `RefactoringSpec` field values. Easy to unit-test
without fixtures.

### Files

#### 1. `Entity.kt` — the unit of dependency tracking

Sealed type modelling the entities a spec can read/write/produce/consume.
Designed so that two specs touching the *same* `Entity` value implies
a potential ordering constraint.

```kotlin
sealed interface Entity {
    /** Class / interface, identified by FQN. */
    data class Type(val fqn: String) : Entity

    /** Method, identified by declaring type FQN + name + erased param types. */
    data class Method(
        val declaringTypeFqn: String,
        val name: String,
        val paramTypeSignatures: List<String>,
    ) : Entity

    /** Instance / static field on a class. */
    data class Field(val declaringTypeFqn: String, val name: String) : Entity

    /** Package namespace. */
    data class Package(val name: String) : Entity

    /**
     * Mutable interior of a host method body. Two specs that mutate
     * the same body must be ordered (their AST-subtree-hash anchors
     * are computed against a specific body state).
     */
    data class HostMethodBody(
        val declaringTypeFqn: String,
        val methodName: String,
        val paramTypeSignatures: List<String>,
    ) : Entity

    /**
     * A specific local variable / parameter declaration, anchored by
     * its AST subtree hash inside a host method body. Two specs
     * whose anchor hashes match must be talking about the same node.
     */
    data class Declaration(
        val host: HostMethodBody,
        val declarationSubtreeHash: String,
    ) : Entity

    /**
     * A specific code region in a host body, anchored by selection
     * subtree hash. Distinct from Declaration because a region may
     * span multiple statements.
     */
    data class Region(
        val host: HostMethodBody,
        val selectionSubtreeHash: String,
    ) : Entity
}
```

#### 2. `SpecEffects.kt` — what each spec touches

Pure function with one `when` arm per `RefactoringSpec` subtype:

```kotlin
data class Effects(
    val reads: Set<Entity>,
    val writes: Set<Entity>,
    val produces: Set<Entity>,
    val consumes: Set<Entity>,
)

fun effectsOf(spec: RefactoringSpec): Effects
```

Semantics:
- **reads**: entities the spec needs to exist *before* it runs.
- **writes**: entities the spec mutates in place (post-state still
  has the same identity, but contents differ).
- **produces**: entities that exist *after* the spec but did not before.
- **consumes**: entities that existed before but do not after (renamed
  away, inlined, moved out).

Per-subtype wiring (illustrative; full list lives in code):

| Spec                                                  | reads                                                 | consumes                       | produces                                                              | writes                                                          |
| ----------------------------------------------------- | ----------------------------------------------------- | ------------------------------ | --------------------------------------------------------------------- | --------------------------------------------------------------- |
| `RenameMethod(C, m, m', P)`                           | `Method(C, m, P)`                                     | `Method(C, m, P)`              | `Method(C, m', P)`                                                    | call sites — coarse: any `HostMethodBody` on C                  |
| `RenameClass(C, C')`                                  | `Type(C)`                                             | `Type(C)`                      | `Type(C')`                                                            | —                                                               |
| `RenameField(C, f, f')`                               | `Field(C, f)`                                         | `Field(C, f)`                  | `Field(C, f')`                                                        | —                                                               |
| `RenameLocalVariable(host, h, n')`                    | `Declaration(host, h)`                                | `Declaration(host, h)`         | new declaration (opaque hash)                                         | `HostMethodBody(host)`                                          |
| `RenameParameter(host, h, n')`                        | `Declaration(host, h)`                                | `Declaration(host, h)`         | new declaration (opaque)                                              | `HostMethodBody(host)` (param name lives in body)               |
| `RenamePackage(p, p')`                                | `Package(p)`                                          | `Package(p)`                   | `Package(p')`                                                         | —                                                               |
| `MoveClass(C, p')`                                    | `Type(C)`                                             | `Type(C)`                      | `Type(p'.simpleNameOf(C))`                                            | —                                                               |
| `MoveAndRenameClass(C, p', n')`                       | `Type(C)`                                             | `Type(C)`                      | `Type(p'.n')`                                                         | —                                                               |
| `MoveInstanceField(C, f, D)`                          | `Field(C, f)`, `Type(C)`, `Type(D)`                   | `Field(C, f)`                  | `Field(D, f)`                                                         | —                                                               |
| `MoveAndRenameAttribute(C, f, D, f')`                 | `Field(C, f)`, `Type(C)`, `Type(D)`                   | `Field(C, f)`                  | `Field(D, f')`                                                        | —                                                               |
| `PullUp(C, methodNames, fieldNames)`                  | each `Method(C, m, ?)` / `Field(C, f)`                | each pulled member on C        | opaque `Pulled` sentinels keyed by (C, name)                          | —                                                               |
| `PushDown(C, methodNames, fieldNames)`                | mirror of PullUp                                      | each pushed member on C        | opaque `PushedDown` sentinels                                         | —                                                               |
| `ExtractMethod(host, sel, n')`                        | `Region(host, sel)`, `HostMethodBody(host)`           | —                              | `Method(host.declaringTypeFqn, n', OPAQUE)`                           | `HostMethodBody(host)`                                          |
| `InlineMethod(C, m, P)`                               | `Method(C, m, P)`                                     | `Method(C, m, P)`              | —                                                                     | every `HostMethodBody` on C — coarse: writes `Type(C)`          |
| `ExtractVariable(host, sel, n')`                      | `Region(host, sel)`, `HostMethodBody(host)`           | —                              | new `Declaration(host, opaque)`                                       | `HostMethodBody(host)`                                          |
| `InlineVariable(host, declHash)`                      | `Declaration(host, declHash)`                         | `Declaration(host, declHash)`  | —                                                                     | `HostMethodBody(host)`                                          |
| `ExtractAttribute(host, sel, n')`                     | `Region(host, sel)`, `HostMethodBody(host)`           | —                              | `Field(host.declaringTypeFqn, n')`                                    | `HostMethodBody(host)`                                          |
| `ParameterizeVariable(host, sel, n')`                 | `Region(host, sel)`, `HostMethodBody(host)`           | `Method(host as identity)`     | `Method(host.declaringTypeFqn, host.methodName, host.paramTypes ⊕ T)` | call sites — coarse: writes `Type(host.declaringTypeFqn)`       |
| `ParameterizeAttribute(host, sel, n')`                | as above                                              | as above                       | as above                                                              | as above                                                        |
| `ReplaceVariableWithAttribute(host, declHash, f', v)` | `Declaration(host, declHash)`, `HostMethodBody(host)` | `Declaration(host, declHash)`  | `Field(host.declaringTypeFqn, f')`                                    | `HostMethodBody(host)`                                          |
| `ChangeMethodSignature(C, m, P, m', ret, params)`     | `Method(C, m, P)`                                     | `Method(C, m, P)`              | `Method(C, m' ?: m, P')`                                              | call sites — coarse: writes `Type(C)`                           |
| `ChangeVariableType(host, declHash, T')`              | `Declaration(host, declHash)`                         | `Declaration(host, declHash)`  | new declaration (opaque)                                              | `HostMethodBody(host)`                                          |
| `ChangeAttributeType(C, f, T')`                       | `Field(C, f)`                                         | —                              | —                                                                     | `Field(C, f)`                                                   |
| `ExtractClass(C, n', delegate, fields, gs)`           | `Type(C)`, listed `Field(C, *)`                       | listed `Field(C, *)`           | `Type(packageOf(C).n')`, `Field(C, delegate)`, listed `Field(n', *)`  | —                                                               |
| `ExtractSuperclass / ExtractInterface(...)`           | mirror of ExtractClass                                | listed members on C            | `Type(packageOf(C).n')`, listed members on n'                         | —                                                               |
| `Other`                                               | rejected by analyser — caller must filter             | —                              | —                                                                     | —                                                               |

**Opaque payloads.** Some entities the spec produces aren't fully
identifiable from the spec alone (e.g. an extracted method's param
list depends on data flow). Wherever a field is unknown, model it as
`OPAQUE` — a sentinel that compares equal only to itself. Does not
match any real entity, so it's safe (no spurious edges) but also
inert (no false negatives — the *known* fields still drive matching).

**Coarse models.** A few spec types affect things outside the entities
we track explicitly (e.g. `RenameMethod` rewrites every call site
across the codebase; `InlineMethod` modifies every host calling it).
v1 conservatively models these as touching the *declaring* `Type` —
i.e. any later op on the same class is ordered after. False positives
inflate the edge set, never deflate it. The synthesis PR can refine
once we have data.

#### 3. `SpecVersioner.kt` — SSA pre-pass

Pure module that walks the user trace once and assigns a *version*
to every named entity (`Type`, `Method`, `Field`, `Package`). Reads
and consumes resolve to the version of the latest live producer at
that trace position; productions mint a new version equal to the
producer's trace index. Host-anchored entities (`HostMethodBody`,
`Declaration`, `Region`) are content-addressed and not versioned.

Without this pre-pass, the same logical name produced multiple times
in a window would conflate across productions — e.g. `extract helper
→ inline helper → re-extract helper → rename helper` would generate
spurious read-after-consume edges between the *first* extract and
the rename, when the rename actually depends on the *second*
extract's helper.

```kotlin
internal object SpecVersioner {
    data class Result(
        val effects: List<Effects>,                     // versioned, per spec
        val crossRangeEdges: List<CrossRangeEdge>,      // see below
    )
    sealed interface NameKey { ... }                    // un-versioned identity
    fun version(specs: List<RefactoringSpec>): Result
}
```

The pre-pass also tracks **live ranges** per `NameKey` — pairs of
`(producerIdx, consumerIdx?)`. When the same name has multiple live
ranges, an additional precedence edge is required: the consumer that
closed range *k* must precede the producer that opens range *k+1*,
otherwise both versions would be live simultaneously (which JDT
rejects). These surface as `crossRangeEdges` and feed into the
analyser as `EdgeReason.Kind.PRODUCES_AFTER_CONSUME` edges.

If a previous range has no closing consumer (open until end of
window), fall back to a `producer → producer` edge to at least
preserve user order; this case shouldn't arise in a valid user trace
but the analyser handles it gracefully rather than throwing.

#### 4. `SpecDependencyAnalyzer.kt` — pairwise edge derivation

```kotlin
data class SpecDag(
    val nodes: List<RefactoringSpec>,            // input order preserved
    val edges: Map<Int, Set<Int>>,               // predecessor → successors
) {
    fun predecessors(i: Int): Set<Int>
    fun successors(i: Int): Set<Int>
}

object SpecDependencyAnalyzer {
    fun analyze(specs: List<RefactoringSpec>): SpecDag
}
```

Algorithm:
1. Compute raw `Effects` via `effectsOf(spec)` for every spec.
2. Run `SpecVersioner.version(...)` (see file 3) to stamp named
   entities with version tags and to collect cross-range edges.
3. For every pair `(i, j)` with `i < j` (i = earlier in user trace),
   add edge `i → j` iff any of:
   - `effects[j].reads ∩ (effects[i].produces ∪ effects[i].writes) ≠ ∅`
     — j needs something i creates or modifies.
   - `effects[j].reads ∩ effects[i].consumes ≠ ∅`
     — j tries to read something i destroyed (read-after-consume —
     conservatively ordered).
   - `effects[j].writes ∩ (effects[i].writes ∪ effects[i].consumes) ≠ ∅`
     — write-write conflict on the same entity.
   - `effects[j].consumes ∩ effects[i].consumes ≠ ∅`
     — both can't consume the same entity; second one fails.
4. Merge in the SSA cross-range edges as
   `EdgeReason.Kind.PRODUCES_AFTER_CONSUME`.
5. Edges are "must-precede" — no cycles by construction (each pair
   inspected only with `i < j`, so edges go forward in input order).
   The user's order is acyclic by definition; we never derive `j → i`
   for `i < j`.

**Note on transitive closure.** We don't materialise the closure
explicitly. The enumerator walks the direct-edge representation
Kahn-style.

#### 5. `TopologicalEnumerator.kt` — enumerate valid orderings

```kotlin
data class EnumerationBudget(
    val maxNodes: Int = 7,                       // skip windows over this size in v1
    val maxOrderings: Int = 5040,                // 7!
)

data class EnumerationResult(
    val orderings: List<List<Int>>,              // each = permutation of node indices
    val truncated: Boolean,                      // budget hit → caller may fall back
    val skipReason: String?,                     // populated when nodes > budget
)

object TopologicalEnumerator {
    fun enumerate(
        dag: SpecDag,
        budget: EnumerationBudget = EnumerationBudget(),
    ): EnumerationResult
}
```

Algorithm: classic backtracking topological-sort enumeration.
- Maintain `inDegree[i]` and `available = { i : inDegree[i] == 0 }`.
- Recurse: pick any `i ∈ available`, append to current ordering,
  decrement inDegree of successors, recurse, undo.
- When `current.size == n`, emit.
- If emitted count exceeds `budget.maxOrderings`, set `truncated =
  true` and stop.
- If `nodes.size > budget.maxNodes`, return immediately with empty
  `orderings`, `truncated = true`, `skipReason = "n=N exceeds budget
  maxNodes=7"`. Reordering is skipped for that window in v1; beam
  search is a follow-up PR.

User's observed ordering is always one of the emitted orderings
(since the DAG is built so that user order = a valid topo order);
useful as a sanity check in tests.

#### 6. `ReorderDebug.kt` — inspection helper

A small `fun describe(specs: List<RefactoringSpec>): String` that
returns a human-readable summary:

```
Specs (n=4):
  [0] RenameMethod(com.foo.Bar#oldFn → newFn)
  [1] MoveInstanceMethod(com.foo.Bar#newFn → com.foo.Baz)
  [2] ExtractVariable(com.foo.Quux#run, hash=ab12.., name=tmp)
  [3] RenameField(com.foo.Bar#count → counter)

Edges:
  0 → 1   (1 reads Method(com.foo.Bar, newFn) which 0 produces)
  (no other edges)

Orderings (8 valid):
  [0,1,2,3]   ← user's
  [0,1,3,2]
  [0,2,1,3]
  [0,3,1,2]
  [2,0,1,3]
  [2,0,3,1]
  [2,3,0,1]
  [3,0,1,2]
```

Used by tests + by you when running on real windows. Not exposed in
production reports.

### Tests

`tool/analysis/src/test/kotlin/com/github/ethanhosier/analysis/alternative/reorder/`

#### `SpecEffectsTest.kt`
One test per major subtype, asserting `effectsOf(spec)` returns the
expected `(reads, writes, produces, consumes)`. Focus on:
- All five range-based specs (ExtractMethod / ExtractVariable /
  ExtractAttribute / ParameterizeVariable / ParameterizeAttribute) —
  verify `HostMethodBody(host)` is in writes.
- All five point-based specs — verify `Declaration(host, hash)` is in
  `consumes` for renames and `InlineVariable` /
  `ReplaceVariableWithAttribute`, `writes` for `ChangeVariableType`.
- Type-level renames produce a fresh `Type` / `Method` / `Field` and
  consume the old one.
- `Other` causes the analyser to throw (caller must filter).

#### `SpecDependencyAnalyzerTest.kt`
Case-study tests, each constructing 2–4 fake specs and asserting the
expected edge set:

1. `independent_renames_on_different_classes_have_no_edge` —
   `RenameClass(A) + RenameClass(B)` → 0 edges → 2 orderings.
2. `rename_then_move_chains` — `RenameMethod(C, m → m')` followed by
   `MoveInstanceMethod` referring to the renamed name → edge.
3. `extract_followed_by_rename_of_extracted_method` — ExtractMethod
   produces `Method(C, newName)`; later `RenameMethod` with
   `oldName=newName` reads it → edge. Validates the OPAQUE-paramTypes
   carve-out described under Risks.
4. `two_extracts_in_same_host_body_have_edge` — both write
   `HostMethodBody(host)`, must be ordered.
5. `two_extracts_in_disjoint_hosts_have_no_edge` — different
   `HostMethodBody` → no edge.
6. `parameterize_then_call_site_op_has_edge` — `ParameterizeVariable`
   changes host signature; any later op naming the original Method
   depends on it.
7. `inline_method_blocks_later_renames_in_calling_classes` — coarse
   model: `InlineMethod` writes `Type(C)`; any `RenameMethod` on C is
   ordered after.
8. `pull_up_then_rename_pulled_method` — opaque "Pulled" sentinel
   matches by `(C, methodName)`; documents the conservative behaviour
   when parent FQN isn't carried on the spec.
9. `user_observed_ordering_is_always_valid` — for every fixture
   above, assert `[0, 1, 2, ...]` (input order) appears in
   `enumerate()`'s output.

#### `TopologicalEnumeratorTest.kt`
1. `empty_dag_yields_one_empty_ordering`.
2. `independent_3_nodes_yield_6_orderings`.
3. `linear_chain_yields_one_ordering`.
4. `diamond_dag_yields_two_orderings` — A→B, A→C, B→D, C→D.
5. `nodes_above_budget_returns_truncated_with_skip_reason` — n=8,
   asserts `orderings.isEmpty() && truncated && skipReason != null`.
6. `orderings_above_max_returns_truncated_set` — n=7 fully
   independent → 5040 orderings, then `maxOrderings=100` → asserts
   100 + truncated.
7. `user_ordering_is_always_emitted` — assert input order appears in
   `orderings` whenever input order is a valid topo sort (always, by
   construction).

#### `ReorderDebugSnapshotTest.kt`
Snapshot test on `describe(...)` for a 4-spec fixture, so any change
to entity-modelling shows up as a diff in CI.

### Manual inspection workflow (the point of this PR)

After landing, run on real session data:

1. Pick a `RefactoringStep` window from a session — any `(fromSha,
   userToSha)` group with ≥2 typed specs (visible in
   `analysis-report.json`).
2. Reconstruct the spec list. `RefactoringMinerRunner.Summary.steps`
   exposes per-step specs; collect typed ones from one group.
3. Call `ReorderDebug.describe(specs)` from a one-off Kotlin test or
   `main()` in the test source set.
4. Review:
   - **Effects.** Are produces/reads/writes/consumes sane? Especially
     coarse-model entries (RenameMethod's call sites, InlineMethod's
     host bodies).
   - **Edges.** Any *missing* edges (ordering would actually fail at
     synthesis) or *spurious* edges (would prune a clearly-commutative
     pair)?
   - **Orderings.** Is the count plausible? Does the user's ordering
     appear?
5. Iterate on `SpecEffects.kt` / analyser conditions until happy.

Once signed off, the next PR adds:
- A real window-selection policy on `RefactoringMinerRunner` output.
- A multi-step variant of `synthesiseOne()` that takes an ordering,
  borrows a worktree, applies each spec in sequence, commits between
  ops, and emits intermediate SHAs.
- An end-state equivalence check (AST-exact modulo whitespace) to
  validate the assumption from `PLAN-multi-checkpoint-alts.md:4`.

## Critical files

- New: `tool/analysis/src/main/kotlin/com/github/ethanhosier/analysis/alternative/reorder/Entity.kt`
- New: `.../alternative/reorder/SpecEffects.kt`
- New: `.../alternative/reorder/SpecVersioner.kt`
- New: `.../alternative/reorder/SpecDependencyAnalyzer.kt`
- New: `.../alternative/reorder/TopologicalEnumerator.kt`
- New: `.../alternative/reorder/ReorderDebug.kt`
- New: tests under `tool/analysis/src/test/kotlin/com/github/ethanhosier/analysis/alternative/reorder/`
- Read-only references:
  `tool/analysis/src/main/kotlin/com/github/ethanhosier/analysis/miner/model/RefactoringSpec.kt`
  (the 31-subtype sealed hierarchy — full field shapes drive the
  `effectsOf` arms).

No edits to:
- `AlternativeTrajectoryRunner.kt`
- `RefactoringMinerRunner.kt`
- `AnalysisPipeline.kt`
- `AnalysisReport.kt` / `AlternativeTrajectory.kt`
- `DerivedMetricsRunner.kt`
- Any dashboard file
- Any bundle-side file

## Verification

End-to-end checks:

1. `./gradlew :analysis:test --tests "com.github.ethanhosier.analysis.alternative.reorder.*"`
   — unit tests above all pass.
2. Pick a real session JSON (any in `tool/dashboard/src/hooks/`) and
   write a one-off main/test that reads its `refactoringSteps`,
   filters to typed-only, groups by `(fromSha, userToSha)`, picks a
   window with ≥2 specs, calls `ReorderDebug.describe(specs)`, prints
   the result. Eyeball:
   - Each spec's printed entity touches look right.
   - Edges match intuition (e.g. RenameMethod → MoveInstanceMethod on
     the renamed name should chain).
   - Number of orderings is plausible given the DAG.
3. `./gradlew :analysis:detekt :analysis:ktlintCheck` — no warnings
   on the new files.
4. Confirm no behavioural change on `./gradlew :analysis:test`
   outside the new package (existing tests untouched, new module is
   not yet wired).

## Risks

- **Coarse models inflate the edge set.** RenameMethod conservatively
  writes `Type(C)`, blocking any later op on C. Mitigation: print
  edge-derivation reasons in `ReorderDebug` so spurious edges are
  obvious; refine arms after inspection.
- **Opaque-produced entities under-detect chains.** ExtractMethod
  produces `Method(C, newName, OPAQUE)`; a later RenameMethod with
  matching `(C, oldName=newName)` wants to read `Method(C, newName,
  someParamList)`. Solution: match by `(declaringTypeFqn, name)`
  prefix on `Method` when one side has `OPAQUE` paramTypes — handle
  inside an `effectsOverlap()` helper as a one-line carve-out.
- **`Other` specs in a window.** Filtered out by the caller in
  follow-up PRs; v1 the analyser throws on `RefactoringSpec.Other`.
  Tests assert the throw fires.

## Non-goals (re-stating for clarity)

This PR ships *only* the algorithm. No alternatives are synthesised,
scored, or rendered. The deliverable is a function that takes a list
of specs and prints/returns valid orderings — for review.

## Future refinement: per-spec Merkle for region overlap detection

**Current behaviour (slice 1).** `Extract*` specs deliberately do
*not* declare `writes = {host}` in their effects, so two extracts on
the same host body never auto-conflict. This is the *optimistic*
model — the `selectionSubtreeHash` is content-addressed on the
selection only, so disjoint regions genuinely commute, and we can't
distinguish disjoint-from-overlapping from spec fields alone. False
permutations (regions that overlap or contain each other) get
enumerated freely and will fail at apply time once the synthesis PR
adds an end-state-equivalence check.

**Refinement.** Store on each range-based spec the full set of
subtree hashes inside the selection (not just the root) — i.e. a
Merkle of the selection. Then in `SpecDependencyAnalyzer`:

| Case | Test | Decision |
|---|---|---|
| Disjoint | `A.allHashes ∩ B.allHashes == ∅` | no edge |
| A contains B | `B.rootHash ∈ A.allHashes` | edge B → A |
| B contains A | `A.rootHash ∈ B.allHashes` | edge A → B |
| Overlap | hashes intersect, neither root in the other | edge (only user order works) |

Cost: one extra `Set<String>` per range-based spec (~20–200 hashes
per realistic selection, a few KB serialised). Implementation:
extend `AstSubtreeHasher` (both modules) to emit the full set,
populate it in `RefactoringMinerRunner`'s mapper (cheap — already
walking the subtree), and refine the analyser's overlap check.

Caveat: structurally identical regions in physically distinct
locations hash the same, so they'd be flagged as overlapping when
they aren't. Conservative miss — keeps an edge that wasn't strictly
needed. Acceptable.

Worth doing once the synthesis PR is in place and we can measure how
often the optimistic model produces orderings that actually fail at
apply time.