# Session 005 — ManualExtractMethod (subthreshold)

**Pattern**: ManualExtractMethod
**Strength**: subthreshold
**Expected kind**: IDE_REPLAY
**Target step**: 2
**Paired control**: 042
**Manifest row to add after capture**:
```
005,ManualExtractMethod,subthreshold,2,IDE_REPLAY,sessions/005/,042
```

## What this session demonstrates

A 2-line manual extract from `findOverdueLoans`. Sits below the IDE_REPLAY magnitude floor — Tier 2 should NOT flag it. False-positive probe.

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

1. Open `LibrarySystem.java`. Locate `out` at line 139 inside `findOverdueLoans`.
2. **Shift-F6** -> rename `out` to `overdue`. Confirm.
3. Save. Run tests. Expect green.
4. Terminal: `git commit -am "warmup: rename out -> overdue"`.

## Step 2 — Manual Extract Method (the bad step)

1. In `LibrarySystem.java`, locate `findOverdueLoans` at line 138.
2. **Manually** select lines 141-143 — the `if (helperA(loan)) { out.add(loan); }` body (2 logical lines). Press **Cmd-X**.
3. In place, type by hand:
   ```java
                   filterOverdue(loan, overdue);
   ```
4. Below the closing brace of `findOverdueLoans`, type by hand:
   ```java
       private void filterOverdue(Loan loan, List<Loan> overdue) {
           if (helperA(loan)) {
               overdue.add(loan);
           }
       }
   ```
5. Save. Run tests. Expect green.
6. Terminal: `git commit -am "manual extract: filterOverdue (tiny)"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 005
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
