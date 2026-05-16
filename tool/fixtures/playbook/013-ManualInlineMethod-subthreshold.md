# Session 013 — ManualInlineMethod (subthreshold)

**Pattern**: ManualInlineMethod
**Strength**: subthreshold
**Expected kind**: IDE_REPLAY
**Target step**: 2
**Paired control**: 042
**Manifest row to add after capture**:
```
013,ManualInlineMethod,subthreshold,2,IDE_REPLAY,sessions/013/,042
```

## What this session demonstrates

Manual inline of `ReportFormatter.singleCallWrapper` (declared line 54, called only from `describe` at line 59) — a single call, 2-line body. Tier 2 should NOT flag this.

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

1. Open `ReportFormatter.java`. Caret on `feeCalc` field at line 10. **Shift-F6** -> rename to `feeCalculator`. Confirm.
2. Save. Run tests. Expect green.
3. Terminal: `git commit -am "warmup: rename feeCalc -> feeCalculator"`.

## Step 2 — Manual Inline Method (the bad step)

1. In `ReportFormatter.java` at line 59 inside `describe`, replace `return singleCallWrapper(loan, member);` by hand with `return loan.getId() + ":" + member.getName();`.
2. Manually delete the `singleCallWrapper` method (lines 54-56). Do NOT use Refactor -> Inline.
3. Save. Run tests. Expect green.
4. Terminal: `git commit -am "manual inline: singleCallWrapper (trivial)"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 013
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
