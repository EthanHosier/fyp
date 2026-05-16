# Session 024 — SuboptimalOrdering (borderline)

**Pattern**: SuboptimalOrdering
**Strength**: borderline
**Expected kind**: ORDERING
**Target step**: 3
**Paired control**: 045
**Manifest row to add after capture**:
```
024,SuboptimalOrdering,borderline,3,ORDERING,sessions/024/,045
```

## What this session demonstrates

2-step window on `ReportFormatter.formatReport`: extract method, then rename the extracted method. Better order is rename-then-extract (or extract with the chosen name in one shot). Borderline gap to best permutation.

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

1. Open `ReportFormatter.java`. Caret on local `out` at line 14. **Shift-F6** -> rename to `report`. Confirm.
2. Save. Run tests. Expect green.
3. Terminal: `git commit -am "warmup: rename out -> report"`.

## Step 2 — IDE Extract Method (suboptimal first)

1. Inside `formatReport`, select lines 30-33 — the OVERDUE branch body (4 lines: line builder + append + bump + surcharge append). **Refactor -> Extract Method** (Cmd-Opt-M). Name: `tmpOverdueBranch`. Confirm.
2. Save. Run tests. Expect green.
3. Terminal: `git commit -am "extract tmpOverdueBranch"`.

## Step 3 — IDE Rename (terminal step; target_step = 3)

1. Caret on `tmpOverdueBranch` (the just-extracted method). **Shift-F6** -> rename to `appendOverdueLine`. Confirm.
2. Save. Run tests. Expect green.
3. Terminal: `git commit -am "rename tmpOverdueBranch -> appendOverdueLine"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 024
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
