# Session 040 — NoCommitStretch (borderline)

**Pattern**: NoCommitStretch
**Strength**: borderline
**Expected kind**: HYGIENE
**Target step**: 6
**Paired control**: 045
**Manifest row to add after capture**:
```
040,NoCommitStretch,borderline,6,HYGIENE,sessions/040/,045
```

## What this session demonstrates

Same shape as 039 (6 refactors, no commits, commit at step 7) but with a different refactor sequence to give a second borderline data point.

**CRUCIAL**: Stop at 6. NO commits between steps 1 and 6.

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

1. **Step 1**: `ReportFormatter.java` line 14 — `Shift-F6` on local `out` -> `report`. Save. Tests green. **NO commit.**
2. **Step 2**: `ReportFormatter.java` line 21 — `Shift-F6` on local `overdue` -> `isOverdue`. Save. Tests green. **NO commit.**
3. **Step 3**: `ReportFormatter.java` line 54 — `Shift-F6` on `singleCallWrapper` -> `loanLabel`. Save. Tests green. **NO commit.**
4. **Step 4**: `LateFeeCalculator.java` line 11 — `Shift-F6` on local `daysLate` -> `daysOver`. Save. Tests green. **NO commit.**
5. **Step 5**: `LateFeeCalculator.java` line 42 — `Shift-F6` on `helper` -> `applyDiscount`. Save. Tests green. **NO commit.**
6. **Step 6** — target_step = 6: `LateFeeCalculator.java` line 19 — `Cmd-Opt-V` on `0.25 * daysOver` -> `tier1Surcharge`. Save. Tests green. **NO commit yet.**

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 040
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
