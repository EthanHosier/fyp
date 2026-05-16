# Session 034 — SkippedTests (borderline)

**Pattern**: SkippedTests
**Strength**: borderline
**Expected kind**: HYGIENE
**Target step**: 2
**Paired control**: 042
**Manifest row to add after capture**:
```
034,SkippedTests,borderline,2,HYGIENE,sessions/034/,042
```

## What this session demonstrates

2 IDE refactors on `LibrarySystem` back-to-back, no tests between. Then tests + commit. Borderline (exactly 2 refactors in the composite — at the composite-size floor).

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

1. Open `LibrarySystem.java`. Caret on `helperA` declaration at line 121. **Shift-F6** -> rename to `isOverdueNow`. Confirm.
2. Save only. NO tests. NO commit.

## Step 2 — IDE Rename (NO TESTS) — target_step = 2

1. Caret on `helperB` declaration at line 125 (or wherever it now sits). **Shift-F6** -> rename to `isPremium`. Confirm.
2. Save only. NO tests. NO commit YET.

## Step 3 — Tests + commit

1. Run all tests on `src/test/java`. Expect green.
2. Terminal: `git commit -am "2 untested refactors: rename helperA/helperB"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 034
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
