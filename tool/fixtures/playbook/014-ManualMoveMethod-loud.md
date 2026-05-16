# Session 014 — ManualMoveMethod (loud)

**Pattern**: ManualMoveMethod
**Strength**: loud
**Expected kind**: IDE_REPLAY
**Target step**: 2
**Paired control**: 044
**Manifest row to add after capture**:
```
014,ManualMoveMethod,loud,2,IDE_REPLAY,sessions/014/,044
```

## What this session demonstrates

User manually moves `LibrarySystem.validateMember` (lines 129-136) into `MemberValidator` and rewires external callers by hand. Should have been `Refactor -> Move`. Loud IDE_REPLAY anchor.

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

## Step 2 — Manual Move Method (the bad step)

1. In `LibrarySystem.java`, **manually** select the entire `validateMember` method body (lines 129-136). Press **Cmd-X**.
2. Open `MemberValidator.java`. Paste (Cmd-V) the method below the existing `validate` method, around line 22 (before the closing brace of the class).
3. By hand, rename the pasted method (do NOT use Shift-F6; just retype) — leave it as `validateMember` for now, or retype to `validate2`. Adjust by hand: remove the `fooBar(...)` call inside it because `fooBar` doesn't exist in `MemberValidator` scope.
4. Add a `MemberValidator validator = new MemberValidator();` field to `LibrarySystem.java` if needed for callers, or simply construct one inline at each caller.
5. Find all callers of `validateMember` in the project (use Cmd-F to grep textually). Replace `validateMember(m)` with `new MemberValidator().validateMember(m)` (or appropriate) by hand at every caller. Check `LibrarySystemTest.java`.
6. Save all. Run tests. Expect green.
7. Terminal: `git commit -am "manual move: validateMember -> MemberValidator"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 014
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
