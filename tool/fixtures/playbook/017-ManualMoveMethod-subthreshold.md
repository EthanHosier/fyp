# Session 017 — ManualMoveMethod (subthreshold)

**Pattern**: ManualMoveMethod
**Strength**: subthreshold
**Expected kind**: IDE_REPLAY
**Target step**: 2
**Paired control**: 042
**Manifest row to add after capture**:
```
017,ManualMoveMethod,subthreshold,2,IDE_REPLAY,sessions/017/,042
```

## What this session demonstrates

Move of `LateFeeCalculator.calc3` (lines 56-59) into `ReportFormatter`. Zero external callers (only `sumAll` at line 62 calls it locally). Trivial scope. Tier 2 should NOT flag.

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

1. Open `LateFeeCalculator.java`. Caret on local `daysLate` at line 11. **Shift-F6** -> rename to `lateDays`. Confirm.
2. Save. Run tests. Expect green.
3. Terminal: `git commit -am "warmup: rename daysLate -> lateDays"`.

## Step 2 — Manual Move Method (the bad step)

1. In `LateFeeCalculator.java`, manually select lines 56-59 (`public double calc3(Loan loan) { ... }`). **Cmd-X**.
2. Open `ReportFormatter.java`. Paste below the existing methods, around line 60.
3. Update the single internal caller: `sumAll` at line 62 in `LateFeeCalculator.java` now reads `calc3(loan)`. Change it by hand to `new ReportFormatter().calc3(loan)`.
4. Save. Run tests. Expect green.
5. Terminal: `git commit -am "manual move: calc3 -> ReportFormatter"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 017
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
