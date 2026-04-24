# More IDE-Relevant Refactorings — Plan

## Context

`RefactoringClient` today covers Extract Method and Rename Method. The
full `IdeRelevantRefactorings` allowlist in
`analysis/src/main/kotlin/com/github/ethanhosier/analysis/miner/IdeRelevantRefactorings.kt`
has ~30 entries; the eventual `AlternativeTrajectoryRunner` can only
simulate the IDE path for detected refactorings the client supports.

This plan covers the **easy tier** — the 7 refactorings whose bundle-
side implementation is a near-copy of one we already have. The medium
tier (pull up / push down / move static, move class/package) and hard
tier (change signature, extract class/super/interface) are deferred.

The 7:

1. Rename Class (`IJavaRefactorings.RENAME_TYPE`)
2. Rename Field (`RENAME_FIELD`) — RM calls it "Rename Attribute"
3. Rename Package (`RENAME_PACKAGE`)
4. Rename Local Variable (`RENAME_LOCAL_VARIABLE`) — one API covers
   both RM's "Rename Variable" and "Rename Parameter"
5. Extract Variable (`ExtractTempRefactoring`)
6. Inline Variable (`InlineTempRefactoring`)
7. Inline Method (`InlineMethodRefactoring` via
   `RefactoringCore.getRefactoringContribution(IJavaRefactorings.INLINE_METHOD)`
   if available, else direct)

## Step 0: Restructure for per-refactoring files

Goal: adding a new refactoring = one new bundle-side file + one new
host-side file + one delegation line in `JdtRefactorer`. No touching
existing code.

### Bundle side

```
refactoring-bundle/src/main/kotlin/com/github/ethanhosier/refactoringbundle/
  JdtRefactorer.kt                   — facade, ~10 LoC per method
  internal/
    RefactoringHost.kt               — shared `withProject(...)` wrapper
    ProjectInitializer.kt            — unchanged
    IndexingGate.kt                  — unchanged
    RefactoringRunner.kt             — unchanged
    OutcomeJson.kt                   — unchanged
    ops/
      ExtractMethodOp.kt             — moved from JdtRefactorer
      RenameMethodOp.kt              — moved from JdtRefactorer
```

`RefactoringHost.run(projectRoot, sourceFolders, classpathJars) { jp -> … }`
encapsulates: project creation, indexing wait, LTK outcome → JSON,
project teardown. Each `*Op.run(jp, ...)` returns `RefactoringRunner.Outcome`.

`JdtRefactorer.kt` becomes delegation-only:

```kotlin
@JvmStatic fun extractMethod(projectRoot, sourceFolders, classpathJars, …) =
    RefactoringHost.run(projectRoot, sourceFolders, classpathJars) { jp ->
        ExtractMethodOp.run(jp, …)
    }
```

### Host side

```
analysis/src/main/kotlin/com/github/ethanhosier/analysis/refactoring/
  RefactoringClient.kt              — class with lock + close; no per-op state
  RefactoringClientFactory.kt       — unchanged
  EquinoxBootstrap.kt               — unchanged
  RefactoringOutcome.kt             — sealed interface
  ExtractMethod.kt                  — ExtractMethodRequest + extension fn
  RenameMethod.kt                   — RenameMethodRequest + extension fn
```

Each per-op file uses extension functions + a `private val` caching
the reflective `Method` handle lazily:

```kotlin
// ExtractMethod.kt
data class ExtractMethodRequest(…)

private val handle: Method by lazy {
    RefactoringClient::class.java.getField("refactorerClass")
        // or resolve via the client instance
}

fun RefactoringClient.extractMethod(req: ExtractMethodRequest): RefactoringOutcome =
    invokeOnBundle("extractMethod", /* args… */)
```

`RefactoringClient` exposes an internal helper `invokeOnBundle(name, vararg args)`
that: acquires the lock, looks up the Method (cached in a
`ConcurrentHashMap<String, Method>`), invokes, parses JSON. Each per-op
file is ~30 LoC total (request data class + extension method).

### Verification for step 0

Existing `RefactoringClientTest` must still pass with no test changes.

### Commit

One commit: "Restructure refactoring layer for per-op files".

## Step 1..7: Add each refactoring

Each step is its own commit. Template per step:

1. **Bundle:** add `internal/ops/<Op>.kt`. Add `@JvmStatic` delegation
   in `JdtRefactorer.kt` (one line).
2. **Host:** add `<OpName>.kt` with request data class + extension fn.
3. **Test:** add one `@Test` in `RefactoringClientTest` — minimal
   fixture, assert the edit landed on disk.
4. Commit.

### Step 1 — Rename Class

- API: `renameClass(projectRoot, sourceFolders, classpathJars, typeFqn, newName)`
- Bundle: `javaProject.findType(typeFqn)` → `RenameJavaElementDescriptor`
  with `IJavaRefactorings.RENAME_TYPE`.
- Fixture: two files — one declaring `class Foo`, one referencing
  `new Foo()`. Rename to `Bar`. Assert both files updated; assert
  `Foo.java` file renamed to `Bar.java` on disk.

### Step 2 — Rename Field

- API: `renameField(projectRoot, sourceFolders, classpathJars, declaringTypeFqn, oldName, newName)`
- Bundle: `type.getField(oldName)` → `RENAME_FIELD` descriptor.
- Fixture: class with a field + another class reading/writing it.
  Rename; assert both files updated.

### Step 3 — Rename Package

- API: `renamePackage(projectRoot, sourceFolders, classpathJars, oldPackage, newPackage)`
- Bundle: walk `javaProject.packageFragments`, pick the one matching
  `oldPackage` (non-empty, on source root), build `RENAME_PACKAGE`
  descriptor.
- Fixture: package `com.example.old` with one class; another class in
  a different package importing it. Rename to `com.example.new`; assert
  directory moved on disk and import statement updated.

### Step 4 — Rename Local Variable

- API: `renameLocalVariable(projectRoot, sourceFolders, classpathJars, relativeFilePath, line, column, newName)`
- Bundle: convert `(line, column)` → offset, `icu.codeSelect(offset, 0)`
  returns `IJavaElement[]`; if element[0] is `ILocalVariable`, hand to
  `RENAME_LOCAL_VARIABLE` descriptor. Works for both parameters and
  body locals.
- Fixture: method with one parameter + one body local. Two tests or
  one test renaming each? Start with one test renaming a parameter;
  note that local-var rename exercises the same code path.

### Step 5 — Extract Variable

- API: `extractVariable(projectRoot, sourceFolders, classpathJars, relativeFilePath, startLine, startColumn, endLine, endColumn, newName)`
- Bundle: compute offset+length from line+column pairs, `ExtractTempRefactoring(icu, offset, length)`
  with `newName`. Replace-all-occurrences = true by default.
- Fixture: method with a duplicated expression `total * 0.2` on two
  lines. Extract to `discount`. Assert both uses replaced + declaration
  inserted.

### Step 6 — Inline Variable

- API: `inlineVariable(projectRoot, sourceFolders, classpathJars, relativeFilePath, line, column)`
- Bundle: offset from line+column, `icu.codeSelect` → ILocalVariable,
  `InlineTempRefactoring(astRoot, localVariable)` (or
  `InlineTempRefactoring(cu, astRoot, offset, length)` — pick whichever
  API compiles cleanly; both end up in the same LTK runner).
- Fixture: method declaring `int total = a + b;` used once as `return total + 1;`.
  Inline; assert the declaration is gone and `return (a + b) + 1;` appears.

### Step 7 — Inline Method

- API: `inlineMethod(projectRoot, sourceFolders, classpathJars, declaringTypeFqn, methodName, paramTypeSignatures?)`
- Bundle: resolve `IMethod`, get its `sourceRange`, parse the CU to an
  AST, `InlineMethodRefactoring.create(icu, astRoot, range.offset, range.length)`.
  When created from the declaration, it inlines all call sites.
- Fixture: class with `private int sum(int a, int b) { return a + b; }`
  and one caller `int x = sum(1, 2);`. Inline; assert `sum` method gone,
  caller reads `int x = 1 + 2;`.

## Deferred (not in this plan)

Medium/hard tier listed for reference; skip until needed:

- Pull Up / Push Down Method / Attribute
- Move Method / Move Attribute (static variant via `MOVE_STATIC_MEMBERS`)
- Move Class / Move Package
- Change Method Signature (+ 8 downstream param/type refactorings)
- Extract Class / Superclass / Interface
- Extract And Move Method
- Move Source Folder (not a JDT refactoring)

When we reach them, each will get its own plan entry — their request
shapes are richer (typed parameter arrays, member lists) and don't fit
the single-line-delegation pattern here.

## Out of scope

- Dashboard / TS type changes — backend-only for now.
- Pipeline integration (`AlternativeTrajectoryRunner`) — lands after
  the refactoring set is sufficient. Tracked in
  `PLAN-alternative-trajectories.md`.
- Caching / project reuse across calls — intentionally skipped; keep
  each call self-contained until a profiler says otherwise.
