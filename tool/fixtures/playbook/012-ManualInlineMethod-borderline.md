# Session 012 — ManualInlineMethod (borderline)

**Pattern**: ManualInlineMethod
**Strength**: borderline
**Expected kind**: IDE_REPLAY
**Target step**: 2
**Paired control**: 042
**Manifest row to add after capture**:
```
012,ManualInlineMethod,borderline,2,IDE_REPLAY,sessions/012/,042
```

## What this session demonstrates

Manual inline of `LateFeeCalculator.calc2` (declared line 51, called from `sumAll` line 62 — and one test reference). Effective 2 sites = borderline magnitude.

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

1. Open `LateFeeCalculator.java`. Caret on local `base` at line 23 (the `daysLate <= 7` branch). **Shift-F6** -> rename to `weekBase`. Confirm.
2. Save. Run tests. Expect green.
3. Terminal: `git commit -am "warmup: rename base -> weekBase"`.

## Step 2 — Manual Inline Method (the bad step)

1. In `LateFeeCalculator.java`, find both call sites of `calc2`:
   - Line 62 inside `sumAll`: replace `calc2(loan)` with `(loan == null ? 0.0 : 0.50 * 2.0)`. Type by hand.
   - In `LateFeeCalculatorTest.java`, find any `calc2(` reference and replace inline by hand (paste the body).
2. Manually delete the `calc2` method (lines 51-54). Do NOT use Refactor -> Inline.
3. Save. Run tests. Expect green.
4. Terminal: `git commit -am "manual inline: calc2"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 012
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
