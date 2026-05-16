# Session 025 — SuboptimalOrdering (borderline)

**Pattern**: SuboptimalOrdering
**Strength**: borderline
**Expected kind**: ORDERING
**Target step**: 3
**Paired control**: 045
**Manifest row to add after capture**:
```
025,SuboptimalOrdering,borderline,3,ORDERING,sessions/025/,045
```

## What this session demonstrates

2-step window: move a method first, then rename it. Better order: rename first, then move — because rename-while-still-in-source-class touches fewer locations than rename-after-move. Borderline gap.

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

1. Open `LibrarySystem.java`. Caret on `members` field at line 16. **Shift-F6** -> rename to `memberIndex`. Confirm.
2. Save. Run tests. Expect green.
3. Terminal: `git commit -am "warmup: rename members -> memberIndex"`.

## Step 2 — IDE Move Method (suboptimal first)

1. Caret on `validateMember` declaration at `LibrarySystem.java` line 129. **F6** (Refactor -> Move). Move to `MemberValidator.java`. Confirm.
2. Save. Run tests. Expect green.
3. Terminal: `git commit -am "move validateMember -> MemberValidator"`.

## Step 3 — IDE Rename (terminal step; target_step = 3)

1. Caret on the moved `validateMember` method (now in `MemberValidator.java`). **Shift-F6** -> rename to `validateFull`. Confirm. All callers update across the project.
2. Save. Run tests. Expect green.
3. Terminal: `git commit -am "rename validateMember -> validateFull"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 025
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
