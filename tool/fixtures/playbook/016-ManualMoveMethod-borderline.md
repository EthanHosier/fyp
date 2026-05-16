# Session 016 — ManualMoveMethod (borderline)

**Pattern**: ManualMoveMethod
**Strength**: borderline
**Expected kind**: IDE_REPLAY
**Target step**: 2
**Paired control**: 042
**Manifest row to add after capture**:
```
016,ManualMoveMethod,borderline,2,IDE_REPLAY,sessions/016/,042
```

## What this session demonstrates

Manual move of `LateFeeCalculator.sumAll` (lines 61-63) into `LibrarySystem`. One external caller (a test). Borderline IDE_REPLAY magnitude.

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

1. Open `LateFeeCalculator.java`. Caret on local `fee` at line 13. **Shift-F6** -> rename to `accumulated`. Confirm.
2. Save. Run tests. Expect green.
3. Terminal: `git commit -am "warmup: rename fee -> accumulated"`.

## Step 2 — Manual Move Method (the bad step)

1. In `LateFeeCalculator.java`, manually select lines 61-63 (`public double sumAll(Loan loan) { ... }`). **Cmd-X**.
2. Open `LibrarySystem.java`. Paste (Cmd-V) below `findMember` (around line 183, before the class closing brace).
3. By hand, edit the pasted method: it calls `calc1`, `calc2`, `calc3`, `helper` which only exist on `LateFeeCalculator`. Adjust: change to `feeCalc.calc1(loan) + feeCalc.calc2(loan) + feeCalc.calc3(loan) + feeCalc.helper(0.10)`.
4. Find external callers (`LateFeeCalculatorTest.java`). Replace `feeCalc.sumAll(loan)` (or however the test calls it) with `new LibrarySystem(...).sumAll(loan)` or rewire by hand. If the rewiring is too noisy, leave a thin pass-through in `LateFeeCalculator`: `public double sumAll(Loan loan) { return new LibrarySystem(...).sumAll(loan); }` — but that defeats the move; better, update the test.
5. Save all. Run tests. Expect green.
6. Terminal: `git commit -am "manual move: sumAll -> LibrarySystem"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 016
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
