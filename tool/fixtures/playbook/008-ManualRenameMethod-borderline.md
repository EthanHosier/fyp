# Session 008 — ManualRenameMethod (borderline)

**Pattern**: ManualRenameMethod
**Strength**: borderline
**Expected kind**: IDE_REPLAY
**Target step**: 2
**Paired control**: 042
**Manifest row to add after capture**:
```
008,ManualRenameMethod,borderline,2,IDE_REPLAY,sessions/008/,042
```

## What this session demonstrates

Manual find/replace of `calc1` -> `feeForStandard` across its 2 call sites (declaration at LateFeeCalculator line 46, call at line 62). Borderline magnitude — Tier 1 fires, Tier 2 may waver.

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

1. Open `LateFeeCalculator.java`. Caret on `sumAll` at line 61. **Shift-F6** -> rename to `totalFee`. Confirm (updates call site if any; check `LateFeeCalculatorTest.java`).
2. Save. Run tests. Expect green.
3. Terminal: `git commit -am "warmup: rename sumAll -> totalFee"`.

## Step 2 — Manual Rename Method (the bad step)

1. **Edit -> Find -> Replace in Files** (Cmd-Shift-R).
2. Find: `calc1`. Replace: `feeForStandard`. Scope: whole project. **Replace All**.
3. Verify `LateFeeCalculator.java` line 46 declaration and line 62 call site are both updated. Check `LateFeeCalculatorTest.java` for any references.
4. Do NOT use Refactor -> Rename.
5. Save all. Run tests. Expect green.
6. Terminal: `git commit -am "manual rename: calc1 -> feeForStandard"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 008
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
