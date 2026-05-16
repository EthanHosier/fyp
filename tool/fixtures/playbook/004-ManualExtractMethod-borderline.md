# Session 004 — ManualExtractMethod (borderline)

**Pattern**: ManualExtractMethod
**Strength**: borderline
**Expected kind**: IDE_REPLAY
**Target step**: 2
**Paired control**: 042
**Manifest row to add after capture**:
```
004,ManualExtractMethod,borderline,2,IDE_REPLAY,sessions/004/,042
```

## What this session demonstrates

Borderline-sized (7-line) manual extract in `LateFeeCalculator.calculateFee`. Sits near the IDE_REPLAY magnitude floor — Tier 1 should still flag it; Tier 2 may waver depending on the floor calibration.

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

1. Open `LateFeeCalculator.java`. In `calculateFee`, locate `base` at line 19.
2. **Shift-F6** on `base` (the one at line 19 only — IntelliJ will scope to that block). Rename to `tier1Base`. Confirm.
3. Save. Run tests. Expect green.
4. Terminal: `git commit -am "warmup: rename base -> tier1Base"`.

## Step 2 — Manual Extract Method (the bad step)

1. Open `LateFeeCalculator.java`. Locate `calculateFee` (starts line 9) and the `daysLate <= 7` branch at lines 22-26.
2. **Manually** select lines 22-26 (the `else if (daysLate <= 7) { ... fee = helper(fee); }` body — the 5 lines between the braces plus the surrounding 2 lines so you cut 7 lines: from `} else if (daysLate <= 7) {` to the closing `}` of that branch — about 7 lines). Press **Cmd-X**.
3. Where the block was, type by hand:
   ```java
               } else if (daysLate <= 7) {
                   fee = tieredSurcharge(daysLate, member);
   ```
   plus the closing `}` if you removed it.
4. Below the closing brace of `calculateFee`, type by hand:
   ```java
       private double tieredSurcharge(long daysLate, Member member) {
           double base = 0.25 * 3 + 0.50 * (daysLate - 3);
           double fee = base * (member.getType() == MemberType.PREMIUM ? 0.5 : member.getType() == MemberType.STAFF ? 0.7 : 1.0);
           return helper(fee);
       }
   ```
5. Save. Run all tests. Expect green.
6. Terminal: `git commit -am "manual extract: tieredSurcharge"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 004
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
