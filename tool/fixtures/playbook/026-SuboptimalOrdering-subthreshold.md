# Session 026 — SuboptimalOrdering (subthreshold)

**Pattern**: SuboptimalOrdering
**Strength**: subthreshold
**Expected kind**: ORDERING
**Target step**: 3
**Paired control**: 042
**Manifest row to add after capture**:
```
026,SuboptimalOrdering,subthreshold,3,ORDERING,sessions/026/,042
```

## What this session demonstrates

2-step window of two independent renames (different methods, no shared call sites). Either order is equivalent. Synth gap < 3 process points. Tier 2 should NOT flag.

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

1. Open `LibrarySystem.java`. Caret on `loans` field at line 17. **Shift-F6** -> rename to `loanIndex`. Confirm.
2. Save. Run tests. Expect green.
3. Terminal: `git commit -am "warmup: rename loans -> loanIndex"`.

## Step 2 — IDE Rename method A

1. Caret on `helperA` method declaration at `LibrarySystem.java` line 121. **Shift-F6** -> rename to `isLoanOverdue`. Confirm. All 4 call sites update.
2. Save. Run tests. Expect green.
3. Terminal: `git commit -am "rename helperA -> isLoanOverdue"`.

## Step 3 — IDE Rename method B (terminal step; target_step = 3)

1. Caret on `helperB` method declaration at `LibrarySystem.java` line 125. **Shift-F6** -> rename to `isPremium`. Confirm. All 3 call sites update.
2. Save. Run tests. Expect green.
3. Terminal: `git commit -am "rename helperB -> isPremium"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 026
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
