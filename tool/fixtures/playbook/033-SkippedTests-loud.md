# Session 033 — SkippedTests (loud)

**Pattern**: SkippedTests
**Strength**: loud
**Expected kind**: HYGIENE
**Target step**: 4
**Paired control**: 043
**Manifest row to add after capture**:
```
033,SkippedTests,loud,4,HYGIENE,sessions/033/,043
```

## What this session demonstrates

4 IDE refactors back-to-back across `LateFeeCalculator` and `ReportFormatter` with NO test runs between. Final step runs tests + commits. Loud HYGIENE at step 4 (terminal of the untested composite).

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

## Step 1 — IDE Rename (NO TESTS)

1. Open `LateFeeCalculator.java`. Caret on `helper` declaration at line 42. **Shift-F6** -> rename to `applyDiscount`. Confirm.
2. Save only. NO tests. NO commit.

## Step 2 — IDE Rename (NO TESTS)

1. Caret on `calc1` declaration at line 46. **Shift-F6** -> rename to `feeStandard`. Confirm.
2. Save only. NO tests. NO commit.

## Step 3 — IDE Extract Method (NO TESTS)

1. Open `ReportFormatter.java`. In `formatReport`, select lines 14-18 (the header builder up to the empty-loans guard return). **Cmd-Opt-M** -> Extract Method. Name: `buildHeader`. Parameter: `member`. Confirm.
2. Save only. NO tests. NO commit.

## Step 4 — IDE Rename (NO TESTS) — target_step = 4

1. In `ReportFormatter.java`, caret on local `out` at line 14. **Shift-F6** -> rename to `report`. Confirm.
2. Save only. NO tests. NO commit YET.

## Step 5 — Tests + commit

1. Run all tests on `src/test/java`. Fix anything that broke.
2. Terminal: `git commit -am "4 untested refactors batch"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 033
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
