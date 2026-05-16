# Session 019 — ManualExtractVariable (loud)

**Pattern**: ManualExtractVariable
**Strength**: loud
**Expected kind**: IDE_REPLAY
**Target step**: 2
**Paired control**: 043
**Manifest row to add after capture**:
```
019,ManualExtractVariable,loud,2,IDE_REPLAY,sessions/019/,043
```

## What this session demonstrates

Manual extraction of the magic constants `0.85`, `0.95`, `0.70` in `LibrarySystem.bulkLendBooks` (lines 103, 105, 108) into named `static final` constants `BULK_TIER_LARGE`, `BULK_TIER_MEDIUM`, `BULK_TIER_PREMIUM`. Multiple uses each = loud IDE_REPLAY.

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

1. Open `LibrarySystem.java`. Caret on local `total` in `bulkLendBooks` at line 93. **Shift-F6** -> rename to `gross`. Confirm.
2. Save. Run tests. Expect green.
3. Terminal: `git commit -am "warmup: rename total -> gross"`.

## Step 2 — Manual Extract Variable (the bad step)

1. In `LibrarySystem.java`, just below the class field declarations (after line 20), type by hand:
   ```java
       private static final double BULK_TIER_LARGE = 0.85;
       private static final double BULK_TIER_MEDIUM = 0.95;
       private static final double BULK_TIER_PREMIUM = 0.70;
   ```
2. In `bulkLendBooks`:
   - Line 103: replace `0.85` with `BULK_TIER_LARGE` by hand.
   - Line 105: replace `0.95` with `BULK_TIER_MEDIUM` by hand.
   - Line 108: replace `0.70` with `BULK_TIER_PREMIUM` by hand.
3. Do NOT use Refactor -> Introduce Constant.
4. Save. Run tests. Expect green.
5. Terminal: `git commit -am "manual extract const: BULK_TIER_*"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 019
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
