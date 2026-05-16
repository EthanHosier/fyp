# Session 031 — AddThenRevert (subthreshold)

**Pattern**: AddThenRevert
**Strength**: subthreshold
**Expected kind**: REWORK
**Target step**: 1
**Paired control**: 042
**Manifest row to add after capture**:
```
031,AddThenRevert,subthreshold,1,REWORK,sessions/031/,042
```

## What this session demonstrates

2-line addition + 2-line revert. Sits exactly at `MIN_REWORK_LINES` floor (= 2). Surfaces under default config; if the floor is raised it falls off. Probes the floor calibration.

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

## Step 1 — Add 2 lines (target_step = 1)

1. Open `LibrarySystem.java`. In `findOverdueLoans` at line 138, just after `List<Loan> out = new ArrayList<>();` at line 139, type by hand:
   ```java
           int seen = 0;
           int kept = 0;
   ```
2. Save. Run tests. Expect green.
3. Terminal: `git commit -am "add seen/kept locals"`.

## Step 2 — Revert

1. Manually delete the 2 added lines.
2. Save. Run tests. Expect green.
3. Terminal: `git commit -am "revert seen/kept locals"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 031
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
