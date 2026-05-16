# Session 032 — SkippedTests (loud)

**Pattern**: SkippedTests
**Strength**: loud
**Expected kind**: HYGIENE
**Target step**: 3
**Paired control**: 043
**Manifest row to add after capture**:
```
032,SkippedTests,loud,3,HYGIENE,sessions/032/,043
```

## What this session demonstrates

3 consecutive IDE refactors on `LibrarySystem` with NO test runs between them. Then a single test run + commit at the end. HygieneDetector flags the composite anchor at step 3 (the terminal refactor of the untested composite).

Note: NO step-1 warmup with tests — the whole point is that the user skips tests during the composite. Steps 1-3 are the untested refactors; step 4 runs tests and commits.

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

## Step 1 — IDE Extract Method (NO TESTS)

1. Open `LibrarySystem.java`. In `processReturn` at line 32, select lines 51-56 (the daysLate rate-tier if/if block: `if (daysLate > 7) ... if (daysLate > 30) ...`). **Refactor -> Extract Method** (Cmd-Opt-M). Name: `rateForDays`. Parameter: `daysLate`, `baseRate`. Confirm.
2. **DO NOT** run tests. **DO NOT** commit. Just save (Cmd-S).

## Step 2 — IDE Rename (NO TESTS)

1. Caret on `helperA` method declaration at line 121. **Shift-F6** -> rename to `isOverdueNow`. Confirm.
2. **DO NOT** run tests. **DO NOT** commit. Save only.

## Step 3 — IDE Move Method (NO TESTS) — target_step = 3

1. Caret on `validateMember` declaration at line 129. **F6** (Refactor -> Move). Move to `MemberValidator.java`. Confirm.
2. **DO NOT** run tests. **DO NOT** commit. Save only.

## Step 4 — Tests + commit

1. Run all tests on `src/test/java` (Cmd-Shift-F10). Fix any breakage by hand if needed.
2. Terminal: `git commit -am "3 refactors: rateForDays + rename helperA + move validateMember"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 032
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
