# Session 006 — ManualRenameMethod (loud)

**Pattern**: ManualRenameMethod
**Strength**: loud
**Expected kind**: IDE_REPLAY
**Target step**: 2
**Paired control**: 043
**Manifest row to add after capture**:
```
006,ManualRenameMethod,loud,2,IDE_REPLAY,sessions/006/,043
```

## What this session demonstrates

User project-wide find/replaces `fooBar` -> `processOverdueNotifications` across 5+ call sites instead of using `Refactor -> Rename`. The synthesiser detects an equivalent RenameMethod IDE refactor is cheaper. Loud IDE_REPLAY anchor at step 2.

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

1. Open `LibrarySystem.java`. In `bulkLendBooks` at line 101, locate `discount`.
2. **Shift-F6** -> rename to `tierFactor`. Confirm.
3. Save. Run tests. Expect green.
4. Terminal: `git commit -am "warmup: rename discount -> tierFactor"`.

## Step 2 — Manual Rename Method (the bad step)

1. Open the **Edit -> Find -> Replace in Files** dialog (Cmd-Shift-R).
2. Find: `fooBar`. Replace: `processOverdueNotifications`. Scope: Whole project. Click **Replace All**, confirm.
3. Do NOT use Refactor -> Rename (Shift-F6). The find/replace MUST be the textual one.
4. Save all (Cmd-S).
5. Verify the method declaration in `LibrarySystem.java` at line 151 now reads `public String processOverdueNotifications(...)` and call sites at lines 83, 117, 134, 146, 176 have been rewritten. Tests in `LibrarySystemTest.java` should also be rewritten if they reference `fooBar`.
6. Run all tests on `src/test/java`. Expect green.
7. Terminal: `git commit -am "manual rename: fooBar -> processOverdueNotifications"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 006
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
