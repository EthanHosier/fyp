# Session 030 — AddThenRevert (borderline)

**Pattern**: AddThenRevert
**Strength**: borderline
**Expected kind**: REWORK
**Target step**: 1
**Paired control**: 043
**Manifest row to add after capture**:
```
030,AddThenRevert,borderline,1,REWORK,sessions/030/,043
```

## What this session demonstrates

5-line guard clause added to `LibrarySystem.bulkLendBooks` at step 1, then deleted at step 2 (user realises it duplicates existing logic). Borderline rework size.

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

## Step 1 — Add 5-line guard (target_step = 1)

1. Open `LibrarySystem.java`. In `bulkLendBooks` at line 90, just after `if (member == null) return 0.0;` at line 92, type by hand:
   ```java
           if (bookIds == null) {
               return 0.0;
           }
           if (bookIds.isEmpty()) {
               return 0.0;
           }
   ```
2. Save. Run tests. Expect green.
3. Terminal: `git commit -am "add bulk guard clauses"`.

## Step 2 — Revert (realise duplication)

1. In `bulkLendBooks`, manually delete the 6 added lines (the two if blocks).
2. Save. Run tests. Expect green.
3. Terminal: `git commit -am "revert bulk guard clauses (duplicates loop guard)"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 030
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
