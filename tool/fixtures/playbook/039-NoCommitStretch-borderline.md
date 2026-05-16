# Session 039 — NoCommitStretch (borderline)

**Pattern**: NoCommitStretch
**Strength**: borderline
**Expected kind**: HYGIENE
**Target step**: 6
**Paired control**: 045
**Manifest row to add after capture**:
```
039,NoCommitStretch,borderline,6,HYGIENE,sessions/039/,045
```

## What this session demonstrates

Exactly 6 green refactor checkpoints with no commits, single commit at step 7. Exactly at the MIN_COMMIT_GAP floor (= 6). HygieneDetector should just fire — borderline.

**CRUCIAL**: Stop at 6 refactors. Not 5, not 7. NO commits between steps 1 and 6.

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

## Steps 1-6 — six refactors, tests after each, NO commits

1. **Step 1**: `LibrarySystem.java` line 101 — `Cmd-Opt-V` on `1.0` (the `discount = 1.0` literal) -> `NO_DISCOUNT`. Save. Tests green. **NO commit.**
2. **Step 2**: `LibrarySystem.java` line 98 — `Cmd-Opt-V` on `5.0` -> `BOOK_BASE_FEE`. Save. Tests green. **NO commit.**
3. **Step 3**: `LibrarySystem.java` `bulkLendBooks` — `Shift-F6` on local `discount` -> `tierFactor`. Save. Tests green. **NO commit.**
4. **Step 4**: `LibrarySystem.java` `bulkLendBooks` — `Shift-F6` on local `total` -> `gross`. Save. Tests green. **NO commit.**
5. **Step 5**: `LibrarySystem.java` line 121 — `Shift-F6` on `helperA` -> `isOverdueNow`. Save. Tests green. **NO commit.**
6. **Step 6** — target_step = 6: `LibrarySystem.java` line 125 — `Shift-F6` on `helperB` -> `isPremium`. Save. Tests green. **NO commit yet.**

## Step 7 — Finally commit

1. Terminal: `git commit -am "6 refactors batched"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 039
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
