# Session 018 — ManualExtractVariable (loud)

**Pattern**: ManualExtractVariable
**Strength**: loud
**Expected kind**: IDE_REPLAY
**Target step**: 2
**Paired control**: 043
**Manifest row to add after capture**:
```
018,ManualExtractVariable,loud,2,IDE_REPLAY,sessions/018/,043
```

## What this session demonstrates

The ternary `(member.getType() == MemberType.PREMIUM ? 0.5 : member.getType() == MemberType.STAFF ? 0.7 : 1.0)` appears 4 times in `LateFeeCalculator.calculateFee` (lines 20, 24, 28, 32). The user manually pulls it into a `final double discountFactor = ...;` local above the switch — by typing it by hand and find/replacing each occurrence, NOT using `Refactor -> Extract Variable`. Loud IDE_REPLAY.

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

1. Open `LateFeeCalculator.java`. Caret on local `fee` at line 13. **Shift-F6** -> rename to `total`. Confirm.
2. Save. Run tests. Expect green.
3. Terminal: `git commit -am "warmup: rename fee -> total"`.

## Step 2 — Manual Extract Variable (the bad step)

1. In `LateFeeCalculator.java` `calculateFee`, place the caret just above the `switch (member.getType()) {` on line 14. Type by hand:
   ```java
           final double discountFactor = member.getType() == MemberType.PREMIUM ? 0.5 : member.getType() == MemberType.STAFF ? 0.7 : 1.0;
   ```
2. Open **Edit -> Find -> Replace in Files** (Cmd-Shift-R) scoped to `LateFeeCalculator.java` only.
3. Find: `(member.getType() == MemberType.PREMIUM ? 0.5 : member.getType() == MemberType.STAFF ? 0.7 : 1.0)`. Replace: `discountFactor`. **Replace All** (4 occurrences expected at lines 20, 24, 28, 32).
4. Do NOT use Refactor -> Extract Variable (Cmd-Opt-V).
5. Save. Run all tests. Expect green.
6. Terminal: `git commit -am "manual extract var: discountFactor"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 018
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
