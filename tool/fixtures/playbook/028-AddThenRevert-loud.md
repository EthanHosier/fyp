# Session 028 — AddThenRevert (loud)

**Pattern**: AddThenRevert
**Strength**: loud
**Expected kind**: REWORK
**Target step**: 1
**Paired control**: 045
**Manifest row to add after capture**:
```
028,AddThenRevert,loud,1,REWORK,sessions/028/,045
```

## What this session demonstrates

User adds a 12-line helper method to `LateFeeCalculator` at step 1, wires it into one call site at step 2, then at step 3 deletes both the helper and the call. ReworkDetector pairs the add with the revert.

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

## Step 1 — Add a 12-line helper (target_step = 1)

1. Open `LateFeeCalculator.java`. Place caret just before the closing brace of the class (after line 63).
2. Type by hand:
   ```java
       public double penaltyAdjustment(Member member, long daysLate) {
           double adj = 0.0;
           if (member == null) return 0.0;
           if (daysLate <= 0) return 0.0;
           if (member.getType() == MemberType.STAFF) {
               adj = -0.10;
           } else if (member.getType() == MemberType.PREMIUM) {
               adj = -0.05;
           } else {
               adj = 0.10;
           }
           return adj * daysLate;
       }
   ```
3. Save. Run tests. Expect green.
4. Terminal: `git commit -am "add penaltyAdjustment helper"`.

## Step 2 — Wire it into calculateFee

1. In `calculateFee` at line 38, just before `fee = Math.round(...)`, type by hand:
   ```java
           fee = fee + penaltyAdjustment(member, daysLate);
           if (fee < 0) fee = 0.0;
   ```
2. Save. Run tests. Expect green (the test values may need to tolerate the adjustment — if they fail, revert the second statement and keep only the `fee +=` line).
3. Terminal: `git commit -am "use penaltyAdjustment in calculateFee"`.

## Step 3 — Revert: delete the helper and the call

1. In `calculateFee`, manually delete the 2 lines added at step 2.
2. Manually delete the entire `penaltyAdjustment` method (all 13 lines including signature and brace).
3. Save. Run tests. Expect green.
4. Terminal: `git commit -am "revert penaltyAdjustment"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 028
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
