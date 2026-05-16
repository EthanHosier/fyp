# Session 037 — NoCommitStretch (loud)

**Pattern**: NoCommitStretch
**Strength**: loud
**Expected kind**: HYGIENE
**Target step**: 7
**Paired control**: 045
**Manifest row to add after capture**:
```
037,NoCommitStretch,loud,7,HYGIENE,sessions/037/,045
```

## What this session demonstrates

7 green refactor checkpoints (refactor + tests pass after each) with **NO COMMITS** between them. A single commit at step 8 closes the gap. HygieneDetector flags COMMIT_GAP at the checkpoint where the gap reaches the floor (step 7).

**CRUCIAL**: DO NOT commit between steps 1 and 7. The whole point is the missing commits.

Note: no separate step-1 warmup — every step IS a refactor that contributes to the gap.

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

## Step 1 — IDE Extract Variable (NO COMMIT)

1. Open `LibrarySystem.java`. In `bulkLendBooks` at line 103, caret on `0.85`. **Refactor -> Extract Variable** (Cmd-Opt-V). Name: `LARGE_TIER`. Confirm.
2. Save. Run all tests. Expect green. **DO NOT COMMIT.**

## Step 2 — IDE Extract Variable (NO COMMIT)

1. In `bulkLendBooks` at line 105, caret on `0.95`. **Cmd-Opt-V** -> `MEDIUM_TIER`. Confirm.
2. Save. Run tests. Green. **DO NOT COMMIT.**

## Step 3 — IDE Extract Variable (NO COMMIT)

1. In `bulkLendBooks` at line 108, caret on `0.70`. **Cmd-Opt-V** -> `PREMIUM_DISCOUNT`. Confirm.
2. Save. Run tests. Green. **DO NOT COMMIT.**

## Step 4 — IDE Rename local (NO COMMIT)

1. In `LibrarySystem.java` `bulkLendBooks`, caret on local `total` at line 93. **Shift-F6** -> `accruedTotal`. Confirm.
2. Save. Run tests. Green. **DO NOT COMMIT.**

## Step 5 — IDE Inline Variable (NO COMMIT)

1. In `LibrarySystem.java` `processReturn`, caret on local `rate` at line 50 (the variable that aliases `baseRate`). **Cmd-Opt-N** (Refactor -> Inline) -> inline. Confirm — IntelliJ will likely refuse for a multi-assignment local; if so, instead inline `baseRate` (line 49) which is single-assignment. Confirm.
2. Save. Run tests. Green. **DO NOT COMMIT.**

## Step 6 — IDE Extract Method (NO COMMIT)

1. In `LibrarySystem.java` `findOverdueLoans`, select the for-loop body lines 140-144. **Cmd-Opt-M** -> `collectOverdue`. Confirm.
2. Save. Run tests. Green. **DO NOT COMMIT.**

## Step 7 — IDE Rename method (NO COMMIT) — target_step = 7

1. Caret on `findOverdueLoans` declaration. **Shift-F6** -> `listOverdueLoans`. Confirm.
2. Save. Run tests. Green. **DO NOT COMMIT YET.**

## Step 8 — Finally commit

1. Terminal: `git commit -am "7 refactors batched"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 037
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
