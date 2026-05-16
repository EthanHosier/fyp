# Session 023 — SuboptimalOrdering (loud)

**Pattern**: SuboptimalOrdering
**Strength**: loud
**Expected kind**: ORDERING
**Target step**: 4
**Paired control**: 045
**Manifest row to add after capture**:
```
023,SuboptimalOrdering,loud,4,ORDERING,sessions/023/,045
```

## What this session demonstrates

3-step window on `LateFeeCalculator.helper`: rename -> extract -> change-signature. Better order: extract -> change-signature -> rename. Loud ORDERING anchor at step 4.

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

## Step 2 — IDE Rename (suboptimally first)

1. Caret on the `helper` method declaration at `LateFeeCalculator.java` line 42. **Shift-F6** -> rename to `applyDiscount`. Confirm. All call sites (LateFee lines 21, 25, 48, 62; ReportFormatter line 32; LibrarySystem line 66) update.
2. Save. Run tests. Expect green.
3. Terminal: `git commit -am "rename helper -> applyDiscount"`.

## Step 3 — IDE Extract Method (from inside applyDiscount)

1. Inside `applyDiscount` (lines 42-44 originally, now renamed). The body is `return base + 0.0;`. To make extraction non-trivial, first widen the body by hand: replace the body with:
   ```java
       double rounded = Math.round(base * 100.0) / 100.0;
       double clamped = rounded < 0 ? 0.0 : rounded;
       return clamped;
   ```
   (Yes, type those 3 lines by hand to set up the extract — this is the suboptimal "I've just renamed so might as well bulk it out" path.)
2. Select the two lines `double rounded = ...; double clamped = ...;`. **Refactor -> Extract Method** (Cmd-Opt-M). Name: `normaliseFee`. Parameter: `base`. Confirm.
3. Save. Run tests. Expect green.
4. Terminal: `git commit -am "extract normaliseFee from applyDiscount"`.

## Step 4 — IDE Change Signature (terminal step; target_step = 4)

1. Caret on `applyDiscount`. Press **Cmd-F6** (Refactor -> Change Signature). Add a second parameter `double cap` with default `Double.MAX_VALUE`. Modify the body to clamp by `cap`. Confirm — IntelliJ updates all call sites.
2. Save. Run tests. Expect green.
3. Terminal: `git commit -am "change signature: applyDiscount(base, cap)"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 023
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
