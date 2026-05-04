# Implementation plan: AST-anchored specs (PR1)

Single PR delivering robustness substrate for Approach A
(topologically-constrained reordering) from
`tool/PLAN-multi-checkpoint-alts.md`. Migrates 10 position-based
`RefactoringSpec` subtypes from line/column addressing to
content-addressable AST-subtree-hash anchors, so the apply path can
re-resolve them against a live AST whose line numbers may have
shifted (because of any prior op in the same file).

The dependency DAG / topological enumerator is **out of scope here**
— that's a follow-up PR, deferred until we revisit. This PR is purely
the spec-anchor migration.

---

## Goal

Replace fragile `(file, startLine, startCol, endLine, endCol)`
addressing in 10 specs with a content-addressable anchor:
`(declaringTypeFqn, hostMethodName, hostMethodParamTypes,
extractedSubtreeHash, extractedNodeCount)`. Apply path re-resolves
the anchor against the live AST at each apply, so position drift from
prior ops in the same file no longer breaks the spec.

Original line/column kept on the spec as
`originalStart{Line,Column}Hint` **only as a multi-match tiebreaker**
— never used as the primary anchor.

## Specs to migrate (10 total)

Range-based:
- `ExtractMethod`
- `ExtractVariable`
- `ExtractAttribute`
- `ParameterizeVariable`
- `ParameterizeAttribute`

Point-based (single declaration node):
- `InlineVariable`
- `RenameLocalVariable`
- `RenameParameter`
- `ChangeVariableType`
- `ReplaceVariableWithAttribute`

`ExtractAndMoveMethod` is range-based too but its dispatch arm is not
yet implemented (`else -> "dispatch arm not implemented"` in
`AlternativeTrajectoryRunner.kt`); leave it for when its arm lands.

## Architecture decision: where the hash lives

The mining side (`analysis` module) and the bundle side
(`refactoring-bundle`) are in **separate classloaders** — the bundle
is OSGi-isolated and reached via reflection over primitive-only
signatures (`JdtRefactorer.kt:51`). Both have JDT on their classpath
(analysis via RM's transitive deps; bundle via Eclipse JDT Core
directly).

**Decision:** define a canonical hashing function in *both* modules,
intentionally duplicated. The function is small and pure; sharing
source across the OSGi boundary would require packaging gymnastics
for negligible benefit. Add a shared test fixture (a Java snippet +
its expected hash) to both modules' test suites to catch divergence.

## Phase 1.1 — Canonical AST hash utility

**New file (analysis side):**
`analysis/src/main/kotlin/com/github/ethanhosier/analysis/refactoring/anchor/AstSubtreeHasher.kt`

**New file (bundle side):**
`refactoring-bundle/src/main/kotlin/com/github/ethanhosier/refactoringbundle/internal/anchor/AstSubtreeHasher.kt`

Both files contain the same `object AstSubtreeHasher` exposing:

```kotlin
fun hashNode(node: ASTNode): String          // SHA-256 hex of canonical bytes
fun hashNodes(nodes: List<ASTNode>): String  // hash of synthetic Block wrapping the list
```

**Canonical serialization rules** (must match exactly across both sides):
- Per-node: emit `nodeType:identifierOrLiteralPayload` then recursively
  emit each structural child (in JDT's child-property order), each
  wrapped in `(...)`.
- `nodeType` = `node.nodeType` (a stable JDT integer).
- Payload:
  - `SimpleName` → name
  - `StringLiteral` → escaped value
  - `NumberLiteral` → token
  - `BooleanLiteral` → "true"/"false"
  - `NullLiteral` → "null"
  - `CharacterLiteral` → escaped value
  - other nodes → empty
- Skip: positions, comments, javadoc text. Include: modifier flags as
  a sorted bitmask payload on the parent declaration node.
- Output: `MessageDigest.getInstance("SHA-256")` over UTF-8 bytes →
  hex-encoded.

**Why this scheme:** invariant to whitespace, line shifts, comments,
formatting; sensitive to identifier renames, literal value changes,
and structural reshapes inside the region. Aligns with the design
point in `PLAN-multi-checkpoint-alts.md` ("hash is invariant to
anything outside the extracted region; sensitive to anything inside
it").

**Tests** (both modules, same fixtures):
- `hash_of_identical_blocks_is_equal` — parse a 3-statement block
  twice (different parser invocations, same source) → same hash.
- `hash_changes_when_literal_changes` — `log.info("a")` vs
  `log.info("b")` → different hashes.
- `hash_invariant_to_whitespace` — same code with different
  indentation/blank lines → same hash.
- `hash_invariant_to_comments` — `// foo` added inside the block →
  same hash.
- `hash_changes_on_identifier_rename` — `log.info` → `logger.info` →
  different hash.

## Phase 1.2 — Spec field migration

Update `analysis/src/main/kotlin/com/github/ethanhosier/analysis/miner/model/RefactoringSpec.kt`.

For each of the 10 specs, replace position fields with the anchor block.
Example for `ExtractMethod`:

```kotlin
@Serializable @SerialName("ExtractMethod")
data class ExtractMethod(
    val relativeFilePath: String,                   // kept: lets the apply path locate the CU quickly
    val declaringTypeFqn: String,                   // NEW
    val hostMethodName: String,                     // NEW
    val hostMethodParamTypes: List<String>,         // NEW — JDT-style param type erasures
    val extractedSubtreeHash: String,               // NEW
    val extractedStatementCount: Int,               // NEW — narrows search window
    val originalStartLineHint: Int? = null,         // NEW — tiebreaker only
    val originalStartColumnHint: Int? = null,       // NEW — tiebreaker only
    val newMethodName: String,
) : RefactoringSpec
```

For point-based specs (`InlineVariable`, `RenameLocalVariable`, etc.),
the anchor is the single declaration node; `extractedStatementCount`
becomes implicit (1) and isn't carried. Schema example:

```kotlin
@Serializable @SerialName("RenameLocalVariable")
data class RenameLocalVariable(
    val relativeFilePath: String,
    val declaringTypeFqn: String,                   // NEW
    val hostMethodName: String,                     // NEW
    val hostMethodParamTypes: List<String>,         // NEW
    val declarationSubtreeHash: String,             // NEW
    val originalLineHint: Int? = null,              // NEW
    val originalColumnHint: Int? = null,            // NEW
    val newName: String,
) : RefactoringSpec
```

Backwards-compat: not needed. `RefactoringSpec` on `RefactoringStep`
is `@Transient` (recomputed from RM each pipeline run — see comment
at `RefactoringSpec.kt:13-15`). Specs serialised onto
`AlternativeTrajectory` are output-only; they're not read back. No
migration shim needed.

## Phase 1.3 — RM mapper updates

Update `RefactoringMinerRunner.kt:286-424` (the relevant `is X ->`
arms).

For `ExtractOperationRefactoring`:
- `declaringTypeFqn = r.sourceOperationBeforeExtraction.className`
- `hostMethodName = r.sourceOperationBeforeExtraction.name`
- `hostMethodParamTypes = r.sourceOperationBeforeExtraction.parameterTypeList.map { it.toString() }`
  (verify exact accessor name in the RM 3.x API — might be
  `getParameterTypeList()` or similar).
- For the hash: parse the host method's source range with JDT
  `ASTParser` (set kind `K_COMPILATION_UNIT`, source = the file's
  bytes at this commit, which RM has loaded for detection). Locate
  the host method node, then within it locate the contiguous
  statement range matching the extracted fragments' line/column
  boundaries. Hash those statements via `AstSubtreeHasher.hashNodes()`.
  - The fragments come from the existing filter at
    `RefactoringMinerRunner.kt:293`.
- `originalStartLineHint = first.startLine`,
  `originalStartColumnHint = first.startColumn`.

For each point-based spec (e.g. `RenameVariableRefactoring` →
`RenameLocalVariable`): use `r.originalVariable.locationInfo` to find
the declaration site, locate the corresponding `VariableDeclaration*`
AST node, hash it.

Plumbing: extract a small helper
`parseAndHash(commitFile, range) -> AnchorPayload` to keep the
per-spec arms terse.

## Phase 1.4 — Bundle-side apply path

Update each affected `*Op.kt` in
`refactoring-bundle/src/main/kotlin/com/github/ethanhosier/refactoringbundle/internal/ops/`.

**Pattern (e.g. `ExtractMethodOp.kt`):**

```kotlin
fun run(
    javaProject: IJavaProject,
    relativeFilePath: String,
    declaringTypeFqn: String,
    hostMethodName: String,
    hostMethodParamTypes: Array<String>,
    extractedSubtreeHash: String,
    extractedStatementCount: Int,
    originalStartLineHint: Int,                 // -1 when absent
    originalStartColumnHint: Int,               // -1 when absent
    newMethodName: String,
): RefactoringRunner.Outcome {
    val icu = findCompilationUnit(javaProject, relativeFilePath)
        ?: return Outcome.Failure("no compilation unit at $relativeFilePath")

    val parsed = parseToAst(icu)                                    // org.eclipse.jdt.core.dom.CompilationUnit
    val hostMethod = AnchorResolver.findHostMethod(
        parsed, declaringTypeFqn, hostMethodName, hostMethodParamTypes
    ) ?: return Outcome.Failure("host method not found: $declaringTypeFqn#$hostMethodName(...)")

    val match = AnchorResolver.findStatementWindow(
        hostMethod, extractedSubtreeHash, extractedStatementCount,
        tieBreakLineHint = originalStartLineHint.takeIf { it > 0 },
    ) ?: return Outcome.Failure("no AST subtree match for hash=$extractedSubtreeHash")

    val selStart = match.firstStatement.startPosition
    val selEnd   = match.lastStatement.startPosition + match.lastStatement.length
    val refactoring = ExtractMethodRefactoring(icu, selStart, selEnd - selStart).apply {
        methodName = newMethodName
        visibility = Modifier.PRIVATE
    }
    return RefactoringRunner.run(refactoring)
}
```

**New file (bundle side):**
`refactoring-bundle/src/main/kotlin/com/github/ethanhosier/refactoringbundle/internal/anchor/AnchorResolver.kt`

Exposes:
```kotlin
data class StatementWindow(val firstStatement: Statement, val lastStatement: Statement, val all: List<Statement>)

fun findHostMethod(
    cu: CompilationUnit,
    declaringTypeFqn: String,
    methodName: String,
    paramTypes: Array<String>,
): MethodDeclaration?

fun findStatementWindow(
    host: MethodDeclaration,
    expectedHash: String,
    statementCount: Int,
    tieBreakLineHint: Int?,
): StatementWindow?

fun findDeclarationNode(                 // for point-based specs
    host: MethodDeclaration,
    expectedHash: String,
    tieBreakLineHint: Int?,
): ASTNode?
```

Implementation:
- `findHostMethod`: walk all `TypeDeclaration` (and nested) under the
  CU, match FQN by composing package + nested type names, then match
  the method by name + param-type-erasure list.
- `findStatementWindow`: collect `host.body.statements()` into a list,
  slide a window of size `statementCount`, hash each window via
  `AstSubtreeHasher.hashNodes()`, return the first match. On multiple
  matches, tiebreak by smallest absolute distance from
  `tieBreakLineHint` to window's first statement's start line.
- `findDeclarationNode`: walk the host body collecting all
  `VariableDeclarationFragment` / `SingleVariableDeclaration`, hash
  each, return matching one (with hint tiebreak).

## Phase 1.5 — `JdtRefactorer.kt` signature updates

Update the 10 affected `@JvmStatic` methods in
`refactoring-bundle/.../JdtRefactorer.kt` to accept the new params.
Each delegates to its `*Op.kt`.

Before:
```kotlin
fun extractMethod(
    projectRoot: String, sourceFolders: Array<String>, classpathJars: Array<String>,
    relativeFilePath: String, startLine: Int, startColumn: Int, endLine: Int, endColumn: Int,
    newMethodName: String,
): String
```

After:
```kotlin
fun extractMethod(
    projectRoot: String, sourceFolders: Array<String>, classpathJars: Array<String>,
    relativeFilePath: String,
    declaringTypeFqn: String, hostMethodName: String, hostMethodParamTypes: Array<String>,
    extractedSubtreeHash: String, extractedStatementCount: Int,
    originalStartLineHint: Int, originalStartColumnHint: Int,
    newMethodName: String,
): String
```

## Phase 1.6 — Analysis-side request DTOs + client invocation

Update the 10 affected files under
`analysis/src/main/kotlin/com/github/ethanhosier/analysis/refactoring/ops/`
(`ExtractMethod.kt`, `ExtractVariable.kt`, `InlineVariable.kt`, …).

For each, update the `*Request` data class to mirror the new field
set, update `paramTypes: Array<Class<*>>` to match the new bundle
signature, and update the `arrayOf(...)` invocation args.

Pattern (for `ExtractMethod.kt`):

```kotlin
data class ExtractMethodRequest(
    val projectRoot: Path,
    val sourceFolders: List<String>,
    val classpathJars: List<Path>,
    val relativeFilePath: String,
    val declaringTypeFqn: String,
    val hostMethodName: String,
    val hostMethodParamTypes: List<String>,
    val extractedSubtreeHash: String,
    val extractedStatementCount: Int,
    val originalStartLineHint: Int? = null,
    val originalStartColumnHint: Int? = null,
    val newMethodName: String,
)
```

## Phase 1.7 — `AlternativeTrajectoryRunner.dispatch` updates

Update the 10 affected `is RefactoringSpec.X ->` arms in
`AlternativeTrajectoryRunner.kt:244-573` to thread the new fields.
Mechanical 1:1 mapping from spec field → request field.

## Phase 1.8 — Bundle-side unit tests

`refactoring-bundle/src/test/.../AnchorResolverTest.kt`:
- `finds_host_method_by_fqn_and_signature`
- `finds_statement_window_when_hash_matches`
- `returns_null_when_no_hash_match`
- `tiebreak_picks_window_closest_to_original_line` — synthesise a
  method with the same 3-statement block appearing twice; verify hint
  resolves the ambiguity.
- `survives_unrelated_edit_above_window` — apply a no-op edit (e.g.
  change a literal in an earlier method) → still finds the window.

`refactoring-bundle/src/test/.../ExtractMethodOpTest.kt`:
- End-to-end: prepare a `JavaProject` fixture with a long method
  containing two extractable regions, run two `ExtractMethodOp.run`s
  in reverse order, verify both succeed and produce the expected
  end-state.
- Critical: any existing `ExtractMethodOpTest` needs updating to the
  new signature.

## Phase 1.9 — Mining-side unit tests

`analysis/src/test/.../miner/RefactoringMinerRunnerTest.kt` (new or
extended): for each of the 10 migrated kinds, feed RM a minimal
two-commit fixture, invoke the mapper, assert spec fields including
`extractedSubtreeHash` is populated and (re-)hashing the same source
range produces the same value.

## Phase 1.10 — End-to-end smoke

Pick one fixture session that contains a position-based refactoring
(probably an `ExtractMethod`) and run the full pipeline. Verify:
- Spec lands with new fields populated.
- `AlternativeTrajectoryRunner` synthesises the alt successfully.
- Resulting alt SHA's git diff matches what the previous (line/col)
  version produced.

## Deliverable summary

| What                                  | Files (rough)                              |
| ------------------------------------- | ------------------------------------------ |
| Hash utility, both sides              | 2 new files + tests                        |
| Spec field migration                  | `RefactoringSpec.kt` (10 sealed subtypes)  |
| Anchor resolver, bundle               | 1 new file + tests                         |
| RM mapper updates                     | `RefactoringMinerRunner.kt` (10 arms)      |
| Bundle ops                            | 10 `*Op.kt` files                          |
| `JdtRefactorer.kt`                    | 10 method signature changes                |
| Analysis request DTOs + invocations   | 10 `ops/*.kt` files                        |
| Dispatch arms                         | `AlternativeTrajectoryRunner.kt:244-573`   |
| Tests                                 | per-module unit tests + 1 e2e smoke        |

## Explicit non-goals

- No dependency analysis / reordering / permutation enumeration —
  deferred to a follow-up PR.
- No `ExtractAndMoveMethod` migration (dispatch arm not yet
  implemented).
- No call-graph or inheritance analysis.
- No frontend/dashboard changes.
- No migration of existing JSON reports — specs are recomputed.

## Risks

- **Hash divergence between modules.** Mitigation: a shared
  canonicalisation test fixture (a Java snippet + its expected SHA)
  executed in both modules' test suites. Any drift fails CI.

- **AnchorResolver false matches.** Mitigation: tiebreak by line
  hint; verify match via secondary AST structural compare (cheap once
  you have the candidate).

- **No user-visible value alone.** This PR is infrastructure for the
  reordering work; it doesn't change pipeline output. Behaviour
  parity with the current (line/col) implementation is verified via
  the e2e smoke (Phase 1.10).
