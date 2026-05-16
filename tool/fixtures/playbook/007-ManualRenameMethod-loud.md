# Session 007 — ManualRenameMethod (loud)

**Pattern**: ManualRenameMethod
**Strength**: loud
**Expected kind**: IDE_REPLAY
**Target step**: 2
**Paired control**: 043
**Manifest row to add after capture**:
```
007,ManualRenameMethod,loud,2,IDE_REPLAY,sessions/007/,043
```

## What this session demonstrates

Manual project-wide find/replace `helper(` -> `applyDiscount(` across `LateFeeCalculator.java`, `ReportFormatter.java`, and `LibrarySystem.java` (5 call sites: LateFee lines 21, 25, 48, 62; ReportFormatter line 32; plus LibrarySystem line 66). Loud IDE_REPLAY anchor.

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

1. Open `LateFeeCalculator.java`. In `calculateFee` at line 13, locate `fee`. Resist the temptation — pick `daysLate` at line 11 instead.
2. **Shift-F6** on `daysLate` -> rename to `daysPastDue`. Confirm. All occurrences in `calculateFee` update.
3. Save. Run tests. Expect green.
4. Terminal: `git commit -am "warmup: rename daysLate -> daysPastDue"`.

## Step 2 — Manual Rename Method (the bad step)

1. Open **Edit -> Find -> Replace in Files** (Cmd-Shift-R).
2. Find: `helper(`. Replace: `applyDiscount(`. Scope: whole project. Click **Replace All**.
3. Also find `helper ` (with trailing space, as in the method declaration `public double helper(double base)`). Actually the declaration starts with `helper(` so step 2 covers it. Verify the declaration in `LateFeeCalculator.java` line 42 reads `public double applyDiscount(double base)`.
4. Do NOT use Refactor -> Rename.
5. Save all. Run all tests. Expect green.
6. Terminal: `git commit -am "manual rename: helper -> applyDiscount"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 007
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
