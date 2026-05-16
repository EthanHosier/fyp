# Session 022 — SuboptimalOrdering (loud)

**Pattern**: SuboptimalOrdering
**Strength**: loud
**Expected kind**: ORDERING
**Target step**: 4
**Paired control**: 045
**Manifest row to add after capture**:
```
022,SuboptimalOrdering,loud,4,ORDERING,sessions/022/,045
```

## What this session demonstrates

A 3-step IDE-driven refactor window on `LibrarySystem.fooBar` performed in the suboptimal order **rename -> extract -> move**. The better order is **extract -> rename -> move** (extracting before renaming means the helper inherits the cleaner name; renaming before moving means the move target receives the right name). The synthesiser permutes the 3-step window and finds the better permutation. The terminal step (move) is `target_step = 4` (step 1 warmup + 3 ordered steps).

## Setup (every session)

1. **First time only** (bootstrap once before recording session 001):
   ```bash
   cd fixtures/library-fixture
   git init
   git add -A && git commit -m "library-fixture v1 baseline"
   git branch baseline
   git checkout -b recording
   ```
   Then open `library-fixture/` in IntelliJ and keep it open for every session.

2. Reset to baseline:
   ```bash
   cd fixtures
   ./reset-for-session.sh
   ```
3. In IntelliJ: press the "Reload from disk" toolbar icon (or just wait a couple of seconds — the IDE picks up the on-disk reset automatically).
4. Start the refactoring-trajectory plugin in **record mode**.

## Step 1 — Warmup

1. Open `LibrarySystem.java`. Caret on `books` field at line 15. **Shift-F6** -> rename to `bookIndex`. Confirm.
2. Save. Run tests. Expect green.
3. Terminal: `git commit -am "warmup: rename books -> bookIndex"`.

## Step 2 — IDE Rename (suboptimally first)

1. Caret on the `fooBar` method declaration at `LibrarySystem.java` line 151. Press **Shift-F6** (Refactor -> Rename). Rename to `processOverdue`. Confirm. All 5+ call sites update.
2. Save. Run tests. Expect green.
3. Terminal: `git commit -am "rename fooBar -> processOverdue"`.

## Step 3 — IDE Extract Method (from inside the renamed method)

1. Inside `processOverdue` (formerly `fooBar`) at lines 154-160, select the `for` loop body together with the surrounding `for` header (`for (Loan loan : loans.values()) { ... }` — 5 lines).
2. **Refactor -> Extract Method** (Cmd-Opt-M). Name: `appendOverdueLoans`. Pass `sb`, `memberId` as parameters. Confirm.
3. Save. Run tests. Expect green.
4. Terminal: `git commit -am "extract appendOverdueLoans"`.

## Step 4 — IDE Move Method (terminal step; target_step = 4)

1. Caret on the `processOverdue` method declaration (still in `LibrarySystem.java`). Press **F6** (Refactor -> Move). Move to `ReportFormatter.java`. In the wizard, accept defaults; IntelliJ will rewire callers to `reportFormatter.processOverdue(...)`.
2. Save. Run tests. Expect green.
3. Terminal: `git commit -am "move processOverdue -> ReportFormatter"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 022
   ```
   This moves the trace dir to `sessions/<id>/` and (for injection
   sessions) appends the manifest row above to `sessions/manifest.csv`.
   For Control sessions (042-045) it skips the manifest append.

## Sanity check

After `end-session.sh` finishes:

- `wc -l ../sessions/<id>/events.jsonl` should report > 20 lines.
- `cat ../sessions/<id>/session.json` should be valid JSON.
- For injection sessions: `tail -n1 ../sessions/manifest.csv` should be
  this session's row.
