# Implementation plan: rework detection (chunk-level) + surgical-replay counterfactual

## Context

A trajectory exhibits *rework* when the user adds content in one step
and later removes the same content (or removes content and later adds
it back). The round-trip is wasted effort: the user incurred the cost
of the original edit, the cognitive load of revisiting the file, and
the time spent on the inverse edit, with zero net change in the code.

An earlier revision of this document framed detection as whole-file
AST-hash equality (`hash(post(k), f) == hash(pre(k'), f)`). That
is too coarse — realistic rework rarely returns an entire file to
a prior state, because the file accumulates other changes alongside
the reverted hunk. Rework is line-or-block scoped within a single
method.

This revision replaces that algorithm with **chunk-level matching
scoped by `(file, enclosing method)`**, and replaces the whole-step
truncation counterfactual with **per-hunk surgical replay published
as an `AlternativeTrajectory`** of kind `REWORK`. The existing
alt-trajectory machinery (metrics enrichment, the process-score
continuation walk, dashboard rendering) carries it from there —
broken or partial surgeries naturally score worse and surface in the
chart without an explicit abort/audit gate.

End state: one `DivergencePoint` per `(originating step, terminal
step, file, scope)` rework pair, plus a corresponding
`AlternativeTrajectory` of kind `REWORK` that surgically removes
the matched lines from the originating step's `+` hunks and the
terminal step's `-` hunks, replays the rest of history, and
publishes the result for the existing dashboard chart + metrics
pipeline.

## Scope

Two layers, both in this plan:

- **Layer A — Detection.** Per-(step, file) chunk-level matching
  with `(file, scopeId, contentHash)` grouping. Pure read-only over
  the shadow repo. Resolves enclosing-member scope via JDT.
- **Layer B — Counterfactual.** Per-hunk patch surgery + serial
  replay over a synthesised branch, published as a regular alt
  trajectory. No abort gate — partial / broken outputs are
  published and absorbed by downstream metrics scoring.

Out of scope for v1:
- **Partial-chunk reverts.** Adding `[A, B, C]` as one chunk and
  later deleting only `[A, C]` does not match (chunks differ).
  v2 candidate via longest-common-subsequence overlap.
- **Reordered reverts.** Adding `[A, B, C]` and later deleting
  `[B, A, C]` does not match (order-sensitive hash). v2 candidate
  via order-insensitive set matching.
- **Rename-aware tracking.** A method renamed between `k'` and `k`
  breaks the `scopeId` match. v2 candidate via RefactoringMiner
  rename data.
- **Whitespace/style-normalised line matching.** Strict text hash;
  `int x=5;` vs `int x = 5;` won't match. v2 candidate via
  tokenised hashing.
- **Cross-file rework.** Extracting a method to a new file and
  inlining back is multi-file; RefactoringMiner already detects
  that case at the spec layer. Out of scope here.

## Architecture: where it sits

Rework detection is part of the *structural* divergence layer (see
the two-layer framing in `tool/PLAN-divergence-detection.md`). It
depends only on the shadow repo, not on any refactoring-aware
stage, so it can run as early as the pipeline likes. Recommended
slot:

```
TraceLoader → TraceNormalizer → ShadowRepoBuilder
                                       │
                                       ├─> ReworkDetector            ◄── Layer A
                                       │      │
                                       │      └─> ReworkAlternativeBuilder ◄── Layer B
                                       │            │
                                       │            └─> alt feeds into existing
                                       │                DerivedMetricsRunner + chart
                                       │
                                       └─> RefactoringMinerRunner → ...
```

Running early has two benefits:
- The pipeline log shows `[pipeline] rework: N divergences` before
  the expensive miner/validator/synth stages — useful for
  debugging long pipeline runs.
- If the bundle fails to load or RefactoringMiner crashes, rework
  divergences still surface. Graceful-degradation story.

## Layer A: chunk-level detection

For each step `k`, extract its unified diff per touched Java file
at zero context (`git diff -U0`). Partition each hunk body into
*added chunks* (each maximal contiguous run of `+` lines) and
*removed chunks* (each maximal contiguous run of `-` lines). Each
chunk is one matchable unit:

```kotlin
data class EditChunk(
    val stepIndex: Int,
    val file: String,
    val scopeId: String,         // e.g. "com.example.Order#applyDiscount(double)"
    val contentHash: String,     // hash of the chunk's joined, normalised body
    val sourceText: String,      // chunk body joined as a single string (for reporting)
    val lineCount: Int,
    val side: Side,              // ADDED or REMOVED
)
```

### Hashing — the actual content, not the diff annotation

- Strip the leading `+` / `-` diff prefix from every line in the
  chunk before joining and hashing. Adds and removes must hash
  identically when their content matches.
- Trim trailing whitespace on every line.
- Drop pure-whitespace lines from the chunk before joining (so
  whitespace-only padding doesn't perturb the chunk hash).
- Join the surviving lines with `\n` and hash the resulting blob
  (SHA-256 truncated to 16 hex chars is plenty).

Comments are kept as-is — removing a `// TODO` block you added
earlier is still rework.

### Scope resolution

For each chunk, parse the appropriate side of the diff with JDT
(post-state file for added chunks, pre-state for removed chunks)
and find the smallest enclosing `MethodDeclaration`,
`Initializer`, or `FieldDeclaration` whose source range contains
the chunk's first line. Build a stable identifier:
`<FQClassName>#<methodName>(<paramTypes>)` for methods,
`<FQClassName>::<fieldName>` for fields. Reuse the JDT visitor
pattern from `SpecAnchorBuilder`
(`tool/analysis/src/main/kotlin/com/github/ethanhosier/analysis/refactoring/anchor/SpecAnchorBuilder.kt`).

A chunk that spans multiple members anchors to the outermost
member (the top-level type declaration). The scope falls back to
`<file>#<top-level>` for chunks outside any member (imports,
class-body whitespace, unparseable file).

### Matching algorithm

```
chunks: List<EditChunk> = []
for k in 0 until N:
    for file in filesTouchedAt[k]:
        patch = gitRunner.diffPatch(preShaForStep[k], shaForStep[k], paths=[file], contextLines=0)
        preAst  = parseJava(gitRunner.showAtSha(preShaForStep[k], file))
        postAst = parseJava(gitRunner.showAtSha(shaForStep[k], file))
        for hunk in unifiedDiffParser.parse(patch).hunks:
            for addedRun in hunk.contiguousAddedRuns():
                scopeId = enclosingScopeResolver.resolve(postAst, file, addedRun.newStartLine)
                chunks += EditChunk(k, file, scopeId, hash(normalize(addedRun)), join(addedRun), addedRun.size, ADDED)
            for removedRun in hunk.contiguousRemovedRuns():
                scopeId = enclosingScopeResolver.resolve(preAst, file, removedRun.oldStartLine)
                chunks += EditChunk(k, file, scopeId, hash(normalize(removedRun)), join(removedRun), removedRun.size, REMOVED)

reworks: List<DivergencePoint> = []
byGroup = chunks.groupBy { Triple(it.file, it.scopeId, it.contentHash) }
for ((file, scopeId, _), groupChunks) in byGroup:
    val adds = groupChunks.filter { it.side == ADDED }.sortedBy { it.stepIndex }
    val rems = groupChunks.filter { it.side == REMOVED }.sortedBy { it.stepIndex }.toMutableList()
    for (added in adds):
        val later = rems.firstOrNull { it.stepIndex > added.stepIndex } ?: continue
        rems.remove(later)
        reworks += DivergencePoint(
            stepIndex = later.stepIndex,                 // terminal step
            kind = REWORK,
            magnitude = added.lineCount.toDouble(),
            referenceAltStepIndexes = listOf(added.stepIndex),
            file = file,
            scopeLabel = scopeId,
            reworkLineCount = added.lineCount,
            reworkContentSummary = added.sourceText.take(80),
            explanation = templateRework(added.stepIndex, later.stepIndex, file, scopeId, added.lineCount),
        )
    // Symmetric direction: remove then add. Iterate the other way.
    val unmatchedAdds = adds.toMutableList()
    for (removed in rems):
        val later = unmatchedAdds.firstOrNull { it.stepIndex > removed.stepIndex } ?: continue
        unmatchedAdds.remove(later)
        reworks += DivergencePoint(
            stepIndex = later.stepIndex,
            kind = REWORK,
            magnitude = removed.lineCount.toDouble(),
            referenceAltStepIndexes = listOf(removed.stepIndex),
            file = file, scopeLabel = scopeId,
            reworkLineCount = removed.lineCount,
            reworkContentSummary = removed.sourceText.take(80),
            explanation = templateRework(removed.stepIndex, later.stepIndex, file, scopeId, removed.lineCount),
        )

// Aggregation: collapse multiple matches sharing
// (originatingStep, terminalStep, file, scopeId) into one DivergencePoint
// with magnitude summed.
return aggregate(reworks)
```

### Known limitations of chunk-level matching

- **Partial reverts not detected.** Adding `[A, B, C]` as one chunk
  and later deleting only `[A, C]` will not match.
- **Reordered reverts not detected.** Order-sensitive hash.
- **Repeated identical 1-line chunks in the same scope.** Two
  `if (x) return null;` statements added separately and one of them
  deleted: the algorithm may pair the wrong (add, delete) instances
  in the same group. Residual false-positive class.

These are all candidates for v2 refinement and are documented as
threats-to-validity in the writeup framing below.

### Edge cases

- **File created at `k'`, deleted at `k`.** Chunks are well-defined
  on both sides; AST parsing of a "deleted" file (or "freshly
  created") just gives a trivial CU. Chunks anchored to file scope.
  Probably emits a rework finding which is fine — creating and
  later deleting the same content really is rework.
- **Whitespace-only edits.** Skipped at the chunk-content
  normalisation step. ✓
- **Unparseable Java.** Scope resolution falls back to file-level.
  Detection still fires.
- **Renamed files.** v1 doesn't track renames. Different paths =
  different scopes = no match.

## Layer B: surgical replay published as an alt trajectory

For each rework `DivergencePoint`, produce an
`AlternativeTrajectory`:

1. Anchor at `fromSha = preSha(originating step k')`.
2. For each surviving step `j` in `k'..lastStep`, build its
   replayed patch:
   - At step `k'`: the original patch with the matched `+L` lines
     surgically removed (other hunks, other files preserved).
   - At step `k` (terminal): the original patch with the matched
     `-L` lines surgically removed.
   - Other steps replay verbatim.
3. Apply each replayed patch with `git apply --3way` against the
   previous synthesised SHA, committing each result to produce
   the alt checkpoint for that step.
4. The alt's final synthesised SHA is its `userToSha` anchor —
   set to the user's trace-terminal SHA so the alt converges with
   the user at the end (no continuation segment needed).

### Per-hunk patch surgery primitive

Add `PatchLineSurgery` next to `PatchFilter`. It takes a unified-
diff text, a file, a hunk header range, and a set of body-line
indices to drop, and emits a rewritten unified diff where:
- Dropped `+` lines vanish from the body.
- Dropped `-` lines vanish from the body.
- The hunk header's `-a,b +c,d` counts are recomputed.
- If a hunk's body collapses to context-only, the hunk is removed.

`PatchFilter`'s hunk-parsing internals
(`tool/analysis/src/main/kotlin/com/github/ethanhosier/analysis/diffs/PatchFilter.kt`,
including the `^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@` regex and
the prefix-aware body walker) are the model. Extract them into a
shared `UnifiedDiffParser` consumed by both `PatchFilter` and the
new `PatchLineSurgery`.

### No abort on failure

If `git apply --3way` fails partway through (e.g. drift in line
numbers from intervening edits makes the surgical hunks not apply),
the alt is published with however many synthesised checkpoints
applied successfully. The existing metrics pipeline scores the alt
forward; a broken or partial surgery scores worse and surfaces in
the chart naturally. No terminal-AST audit gate.

Rationale: the downstream metrics pipeline (process score, static
metrics) absorbs the cost. A "the surgery you'd have had to do is
risky" alt is *itself* informative — better to publish it as a
rough trajectory than suppress it.

### Alt-trajectory wiring

Each rework alt has:
- `fromSha = preSha(k')`
- `userToSha = traceTerminalSha`
- `kind = REWORK` (new variant alongside `ORDERING`, `IDE_REPLAY`)
- `altCheckpoints = [synth cp at k', synth cp at k'+1, ..., synth cp at lastStep]`
  (one per replayed step, skipping any that failed to apply)

These feed `AnalysisPipeline.baseAlternatives` and pick up
`DerivedMetricsRunner` enrichment + the just-merged process-score
continuation walk automatically. For rework alts whose `userToSha`
is at trace end the continuation is empty, but the alt's terminal
cumulative process score (which captures the avoided churn
penalty) is computed correctly.

### Edge cases (Layer B)

- **The originating step `k'` is the only step touched in its `+`
  hunks for this scope.** Standard case; surgery cleanly removes
  the `+` block.
- **`k'` is multi-purpose** (touches the matched scope plus other
  scopes/files). Surgery preserves the other hunks; only the
  matched `+` lines are stripped.
- **`k` is the last step in the trace.** Standard; the synthesised
  branch ends at the surgery of `k`.
- **Intervening step modifies a line near the surgical hunk.**
  `--3way` typically reconciles. If it can't, that step's commit is
  skipped and the alt's `altCheckpoints` list is shorter. No abort.

## Schema additions

In `tool/analysis/src/main/kotlin/com/github/ethanhosier/analysis/metrics/model/AnalysisReport.kt`:

```kotlin
@Serializable enum class DivergenceKind { ORDERING, IDE_REPLAY, HYGIENE, REWORK }
@Serializable enum class CounterfactualStrength { DESCRIPTIVE, SIMULATED }

@Serializable data class DivergencePoint(
    val stepIndex: Int,                                              // terminal step k
    val kind: DivergenceKind,
    val magnitude: Double,
    val explanation: String,
    val counterfactualStrength: CounterfactualStrength = CounterfactualStrength.DESCRIPTIVE,
    val referenceAltStepIndexes: List<Int>? = null,                  // [originating step k']
    // REWORK-only fields:
    val file: String? = null,
    val scopeLabel: String? = null,
    val reworkLineCount: Int? = null,
    val reworkContentSummary: String? = null,
    val reworkAltTrajectoryIndex: Int? = null,                       // index into report.alternativeTrajectories
)
```

In `tool/analysis/src/main/kotlin/com/github/ethanhosier/analysis/metrics/model/AlternativeTrajectory.kt`: add
`val kind: AlternativeTrajectoryKind = ORDERING` to discriminate
`ORDERING | IDE_REPLAY | REWORK` for dashboard rendering.

Defaults keep deserialisation backward-compatible with cached
reports.

## File-level changes

**New backend (Kotlin):**

- `tool/analysis/src/main/kotlin/com/github/ethanhosier/analysis/divergence/structural/ReworkDetector.kt`
  — `object ReworkDetector` with
  `fun detect(reconstruction, shadowGit): List<DivergencePoint>`
  plus private helpers for chunk extraction, hashing, scope
  resolution, and matching.
- `tool/analysis/src/main/kotlin/com/github/ethanhosier/analysis/divergence/structural/HunkExtractor.kt`
  — wraps `GitRunner.diffPatch(...,contextLines=0)` and returns
  per-file lists of hunks, each with separated added- and removed-
  chunk runs plus their source line numbers.
- `tool/analysis/src/main/kotlin/com/github/ethanhosier/analysis/divergence/structural/EnclosingScopeResolver.kt`
  — JDT walk borrowing patterns from `SpecAnchorBuilder` to map
  `(parsed AST, line number)` → enclosing-member identifier.
- `tool/analysis/src/main/kotlin/com/github/ethanhosier/analysis/divergence/structural/ReworkAlternativeBuilder.kt`
  — surgical-replay producer using
  `WorktreePool.withBatchSession` (same pattern as alternative-
  orderings synth).
- `tool/analysis/src/main/kotlin/com/github/ethanhosier/analysis/diffs/UnifiedDiffParser.kt`
  — extracted hunk-parser core (consumed by `PatchFilter` and the
  new `PatchLineSurgery`).
- `tool/analysis/src/main/kotlin/com/github/ethanhosier/analysis/diffs/PatchLineSurgery.kt`
  — rewrites a unified diff to drop specific `+` / `-` body lines
  and recompute hunk headers.

**New tests:**

- `tool/analysis/src/test/kotlin/com/github/ethanhosier/analysis/divergence/structural/ReworkDetectorTest.kt`
- `tool/analysis/src/test/kotlin/com/github/ethanhosier/analysis/divergence/structural/ReworkAlternativeBuilderTest.kt`
- `tool/analysis/src/test/kotlin/com/github/ethanhosier/analysis/diffs/UnifiedDiffParserTest.kt`
- `tool/analysis/src/test/kotlin/com/github/ethanhosier/analysis/diffs/PatchLineSurgeryTest.kt`

**Modified backend:**

- `tool/analysis/src/main/kotlin/com/github/ethanhosier/analysis/metrics/model/AnalysisReport.kt`
  — `DivergenceKind`, `CounterfactualStrength`, expanded
  `DivergencePoint`.
- `tool/analysis/src/main/kotlin/com/github/ethanhosier/analysis/metrics/model/AlternativeTrajectory.kt`
  — add `kind` discriminator.
- `tool/analysis/src/main/kotlin/com/github/ethanhosier/analysis/diffs/PatchFilter.kt`
  — delegate hunk parsing to `UnifiedDiffParser` (no functional
  change; existing tests pass unchanged).
- `tool/analysis/src/main/kotlin/com/github/ethanhosier/analysis/pipeline/AnalysisPipeline.kt`
  — call detector + alt-builder, splice rework alts and divergences
  into the report.

**Regenerated:**

- `tool/dashboard/src/generated/report-types.ts` via
  `./gradlew :analysis:generateDashboardTypes`.

**Modified frontend (TypeScript):**

- `tool/dashboard/src/data/types.ts` — extend `DivergencePointVM`
  with rework fields; add `kind` to `AlternativeTrajectoryVM`.
- `tool/dashboard/src/data/view-model.ts` — projection.
- `tool/dashboard/src/features/divergence/divergence-item.tsx` —
  rework row variant headlining
  `"In <scope>: reverted N lines added at step k' and removed at step k"`
  with a click handler that selects the corresponding alt
  trajectory on the chart.
- (Optional) `tool/dashboard/src/features/trajectory-chart/chart-alternative-paths.tsx`
  — kind-specific stroke style (e.g. dotted for rework alts).

**Reused (no change):**

- `SpecAnchorBuilder` JDT visitor pattern.
- `AstSubtreeHasher.hashNode` (available if simple
  `methodName(paramTypes)` scope IDs prove insufficient).
- `GitRunner.diffPatch`, `showAtSha`, `applyThreeWay`.
- `WorktreePool.withBatchSession`.
- Existing alt-trajectory plumbing (`AnalysisPipeline`,
  `DerivedMetricsRunner` enrichment, the process-score continuation
  walk).

## Tests

### `ReworkDetectorTest`

1. **`add_then_delete_same_chunk`** — 3-step trajectory: step 1
   adds a 3-line chunk `[A, B, C]` to `foo()`, step 2 unrelated edit
   elsewhere, step 3 deletes `[A, B, C]` from `foo()`. One
   divergence at step 3, `referenceAltStepIndexes = [1]`,
   scope = `...#foo(...)`, `reworkLineCount = 3`.
2. **`delete_then_add_back_chunk`** — reverse direction. Same
   assertion.
3. **`partial_chunk_revert_not_detected_v1`** — step 1 adds chunk
   `[A, B, C]`; step 3 deletes a 2-line chunk `[A, C]`. No match.
   Documents the known limitation; v2 candidate.
4. **`same_chunk_different_methods_no_match`** — add chunk in
   `foo()`, later delete identical chunk in `bar()`. No rework
   (scope mismatch).
5. **`same_chunk_different_files_no_match`** — analogous across
   files.
6. **`whitespace_only_padding_does_not_perturb_chunk_hash`** —
   chunk with internal blank padding lines matches a chunk that
   omits the blanks.
7. **`unparseable_file_falls_back_to_file_scope`** — file with
   syntax error in some step; chunk-level matching still works at
   file granularity.
8. **`multiple_round_trips_paired_greedily`** — add chunk X at 1,
   add chunk X at 3, delete chunk X at 5, delete chunk X at 7. Two
   pairs: (1, 5) and (3, 7).
9. **`intervening_edit_to_same_method_does_not_block_match`** —
   add chunk X to `foo()` at step 1, add unrelated chunk Y to
   `foo()` at step 2, delete chunk X at step 3. Rework still
   detected (the case the old AST-equality approach would miss).
10. **`chunk_split_into_two_steps_does_not_match`** — step 1 adds
    `[A, B]`, step 2 adds `[C]`, step 4 deletes `[A, B, C]` as one
    chunk. No match in v1 (the deleted chunk doesn't equal any
    single added chunk). Future work via chunk-merging.

### `UnifiedDiffParserTest` (regression)

1. Hunk-header parsing parity with the old `PatchFilter`
   internals — assert identical `Hunk` outputs on a corpus of
   diff fixtures.

### `PatchLineSurgeryTest`

1. **`drop_added_line_recomputes_header`** — `@@ -10,3 +10,5 @@`
   with two `+` lines → drop one → `@@ -10,3 +10,4 @@`.
2. **`drop_removed_line_recomputes_header`** — symmetric.
3. **`drop_all_body_collapses_hunk`** — hunk vanishes; surrounding
   context preserved; remaining hunks unaffected.

### `ReworkAlternativeBuilderTest`

1. **`clean_surgery_succeeds`** — extract-then-inline trajectory
   with no intervening edits to the helper. Alt terminal SHA's
   AST equivalent to user's terminal SHA's AST. Alt published
   with full `altCheckpoints`.
2. **`surgery_with_intervening_edit_publishes`** — step 2 adds an
   unrelated line in the same method; surgery drops only the
   matched pair; step 2's hunk replays unchanged.
3. **`apply_failure_partial_publish`** — fabricate a case where a
   middle step's `--3way` apply fails. Alt is published with the
   checkpoints that did apply; downstream metrics score the
   partial trajectory. No abort, no exception propagation.

### Integration

1. **`pipeline_emits_rework_alt`** — round-trip on a small
   fixture: assert `report.divergencePoints` contains a `REWORK`
   entry with non-null `reworkAltTrajectoryIndex`, and the
   referenced `alternativeTrajectories[i]` has `kind = REWORK`,
   `fromSha = preSha(k')`, `userToSha = traceTerminalSha`, and
   non-empty `altCheckpoints`.

## Verification

1. `./gradlew :analysis:test` clean.
2. `./gradlew :analysis:generateDashboardTypes` regenerates
   `report-types.ts`; `cd dashboard && npm run typecheck` clean.
3. Open the dashboard on a session containing a known rework (e.g.
   extract-then-inline within `OrderPricingServiceSuper`). Confirm:
   - A rework row appears in the divergence panel naming the
     method and line count.
   - Clicking it selects/highlights the rework alt on the chart.
   - With `primary = "process"`, the rework alt's process line
     sits at-or-below the user's at trace end (savings from
     avoided churn).
4. Fabricate a trajectory where the surgical replay can only
   partially apply. Confirm the alt still appears (possibly
   shorter), with a degraded process score reflecting partial
   application — no crash, no missing alt.

### JCEF verification

Reload the JCEF dashboard with a refreshed report; confirm both
visuals match the web rendering. (No JCEF-specific concerns —
rework alts reuse existing SVG primitives we've already verified
work in JCEF.)

## Sequencing

Roughly 3 days.

1. **Day 1 — Layer A + schema.** Add schema fields
   (`DivergenceKind`, `CounterfactualStrength`, expanded
   `DivergencePoint`, `AlternativeTrajectory.kind`). Extract
   `UnifiedDiffParser` from `PatchFilter`. Implement `HunkExtractor`,
   `EnclosingScopeResolver`, `ReworkDetector`. Write tests 1–11.
   Run through pipeline, confirm divergences emit at `DESCRIPTIVE`
   strength (no alt builder yet).
2. **Day 2 — Layer B.** Implement `PatchLineSurgery` and tests
   12–14. Implement `ReworkAlternativeBuilder` using
   `WorktreePool.withBatchSession`. Wire into `AnalysisPipeline`.
   Tests 15–17.
3. **Day 3 — Integration + dashboard.** Pipeline wire-in,
   view-model passthrough, divergence-item row, optional chart
   stroke variant. Test 18 + manual eyeball on a fixture.

## Risks

- **Repeated identical 1-line chunks in the same scope.** Two
  `if (x) return null;` statements added separately, one of them
  deleted: the algorithm pairs in step order, which may pair
  arbitrarily when the chunks are content-identical. Mitigation:
  in practice, single-line chunks of identical short content within
  the same method are uncommon enough to accept as a false-positive
  class. Document as threats-to-validity.
- **`git apply --3way` is finicky.** Patches with line-number drift
  from intervening edits can fail in non-obvious ways. Mitigation:
  per the "no abort on failure" rule, skip that step's commit and
  continue. Log the failed step in the alt's metadata.
- **Synthesised branches accumulate.** Long trajectories with many
  rework findings produce many synth branches in the shadow repo.
  Tens is fine; thousands would slow git operations. Mitigation:
  cap rework alt generation at the top-N findings by `magnitude`
  (default 10).
- **JDT parser cost.** Layer A parses every touched file twice per
  step (pre + post). For long traces this is non-trivial.
  Mitigation: memoise by `(sha, file)` — each unique
  (sha, file) is parsed at most once across the detection pass.
- **Multi-method chunk pairing.** A `+` chunk that straddles two
  methods (rare; usually structural surgery) anchors to the file
  scope, not either method. Documented; rare in practice.

## Writeup framing

For the methodology chapter:

> *Rework detection.* We say that step $k$ exhibits rework with
> respect to a content chunk $C$ in scope $S$ if a maximal
> contiguous removed-run at $k$ within $S$ has the same normalised
> body as a maximal contiguous added-run at some earlier step
> $k' < k$ within $S$. The scope $S$ is the smallest enclosing
> method or member-level declaration of the chunk's position, as
> resolved by an Eclipse JDT walk over the appropriate side of the
> diff. Normalisation strips diff prefixes, trims trailing
> whitespace, and elides pure-whitespace lines.
>
> *Surgical-replay counterfactual.* For each detected rework pair
> $(k', k)$, we attempt to synthesise the trajectory that would
> have resulted from omitting the added chunk at $k'$ and the
> removed chunk at $k$. We branch from $\text{pre}(k')$ in the
> shadow repository, then for each step $j \in [k', \text{end}]$
> we apply its patch with the matched lines surgically removed
> via `git apply --3way`. The resulting commit sequence becomes a
> first-class `AlternativeTrajectory` consumed by the same
> downstream metrics pipeline (static metrics, cumulative process
> score) that handles reorder and IDE-replay alternatives. We do
> not gate publication on apply success — a partially-applied
> surgical trajectory is itself informative, and the downstream
> process score penalises the partial outcome.
>
> *Limitations.* Detection is order-sensitive (added chunk
> `[A, B, C]` and reordered removed chunk `[B, A, C]` do not
> match) and chunk-identity-sensitive (partial reverts deleting a
> proper subset of an added chunk do not match). Method renames
> between $k'$ and $k$ break the scope-identifier match. These are
> all candidates for v2 refinement via overlap detection, order-
> insensitive set matching, and rename-aware scope resolution.

That paragraph is rubric-ready: formal predicate, computational
recipe, limitations, future-work bullets.
