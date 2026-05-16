# Session 044 — Control (control)

**Pattern**: Control
**Strength**: control
**Expected kind**: none
**Target step**: -1

(No manifest row.)

## What this session demonstrates

Mid-sized clean session: 4 IDE-driven refactors on `LibrarySystem` + `MemberValidator`, plus one comment-typo fix. Tests after each, commit per refactor. Used by `ManualMoveMethod` loud rows (014, 015) as the paired control.

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

## Step 1 — IDE Rename method

1. `LibrarySystem.java` line 121 — **Shift-F6** on `helperA` -> `isLoanOverdue`. Confirm.
2. Save. Run tests. Green.
3. Terminal: `git commit -am "rename helperA -> isLoanOverdue"`.

## Step 2 — IDE Rename field

1. `MemberValidator.java` line 7 — **Shift-F6** on `t1` field -> `validatedCount`. Confirm. All usages (lines 19, 23) update.
2. Save. Run tests. Green.
3. Terminal: `git commit -am "rename t1 -> validatedCount"`.

## Step 3 — Comment typo fix

1. Open `LibrarySystem.java`. Line 110 has a comment `// touch helperA so spec count is met`. By hand, edit it to `// touch isLoanOverdue so spec count is met` (keep the comment current after the rename).
2. Save. Run tests. Green.
3. Terminal: `git commit -am "fix comment to match rename"`.

## Step 4 — IDE Extract Variable

1. `MemberValidator.java` line 17 — caret on `1900`. **Cmd-Opt-V** -> `MIN_JOIN_YEAR` (as constant). Confirm.
2. Save. Run tests. Green.
3. Terminal: `git commit -am "extract MIN_JOIN_YEAR"`.

## Step 5 — IDE Rename local

1. `MemberValidator.java` line 13 — **Shift-F6** on local `n` -> `name`. Confirm.
2. Save. Run tests. Green.
3. Terminal: `git commit -am "rename n -> name"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 044
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
