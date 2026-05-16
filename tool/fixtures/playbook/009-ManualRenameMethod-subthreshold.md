# Session 009 — ManualRenameMethod (subthreshold)

**Pattern**: ManualRenameMethod
**Strength**: subthreshold
**Expected kind**: IDE_REPLAY
**Target step**: 2
**Paired control**: 042
**Manifest row to add after capture**:
```
009,ManualRenameMethod,subthreshold,2,IDE_REPLAY,sessions/009/,042
```

## What this session demonstrates

Manual rename of `singleCallWrapper` (a private 1-call helper in `ReportFormatter`, declared at line 54, called from `describe` at line 59). One call site means IDE_REPLAY magnitude is near zero. Tier 2 should NOT flag.

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

1. Open `ReportFormatter.java`. Caret on local var `sb` — actually the field is named `feeCalc`; instead use the local `b` if present; the cleanest target is the method-local `out` at line 14. **Shift-F6** on `out` -> rename to `report`. Confirm.
2. Save. Run tests. Expect green.
3. Terminal: `git commit -am "warmup: rename out -> report"`.

## Step 2 — Manual Rename Method (the bad step)

1. **Edit -> Find -> Replace in Files** (Cmd-Shift-R).
2. Find: `singleCallWrapper`. Replace: `formatLoanLabel`. Scope: whole project. **Replace All**.
3. Verify the declaration at `ReportFormatter.java` line 54 and the single call at line 59 are updated.
4. Do NOT use Refactor -> Rename.
5. Save all. Run tests. Expect green.
6. Terminal: `git commit -am "manual rename: singleCallWrapper -> formatLoanLabel"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 009
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
