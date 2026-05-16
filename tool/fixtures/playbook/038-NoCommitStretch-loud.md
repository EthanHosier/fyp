# Session 038 — NoCommitStretch (loud)

**Pattern**: NoCommitStretch
**Strength**: loud
**Expected kind**: HYGIENE
**Target step**: 8
**Paired control**: 045
**Manifest row to add after capture**:
```
038,NoCommitStretch,loud,8,HYGIENE,sessions/038/,045
```

## What this session demonstrates

8 green refactor checkpoints, no commits in between, single commit at step 9. Even louder COMMIT_GAP anchor at step 8.

**CRUCIAL**: NO commits between steps 1 and 8.

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

## Steps 1-8 — eight refactors, tests after each, NO commits

1. **Step 1**: `LibrarySystem.java` line 15 — `Shift-F6` on `books` field -> `bookIndex`. Save. Tests green. **NO commit.**
2. **Step 2**: line 16 — `Shift-F6` on `members` -> `memberIndex`. Save. Tests green. **NO commit.**
3. **Step 3**: line 17 — `Shift-F6` on `loans` -> `loanIndex`. Save. Tests green. **NO commit.**
4. **Step 4**: line 18 — `Shift-F6` on `notifications` -> `noticeLog`. Save. Tests green. **NO commit.**
5. **Step 5**: `LateFeeCalculator.java` line 42 — `Shift-F6` on `helper` -> `applyDiscount`. Save. Tests green. **NO commit.**
6. **Step 6**: `LateFeeCalculator.java` line 46 — `Shift-F6` on `calc1` -> `feeStandard`. Save. Tests green. **NO commit.**
7. **Step 7**: `LateFeeCalculator.java` line 51 — `Shift-F6` on `calc2` -> `feeMedium`. Save. Tests green. **NO commit.**
8. **Step 8** — target_step = 8: `LateFeeCalculator.java` line 56 — `Shift-F6` on `calc3` -> `feeHigh`. Save. Tests green. **NO commit yet.**

## Step 9 — Finally commit

1. Terminal: `git commit -am "8 refactors batched"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 038
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
