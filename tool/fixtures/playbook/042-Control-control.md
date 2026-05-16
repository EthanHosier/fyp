# Session 042 — Control (control)

**Pattern**: Control
**Strength**: control
**Expected kind**: none
**Target step**: -1

(No manifest row — controls are referenced via `control_session_id` in injection rows, they do not appear as their own manifest rows.)

## What this session demonstrates

A small clean session: 3 IDE-driven refactors on `LibrarySystem`, each followed by a test run and a commit. Sensible cadence throughout. Used by ~17 IDE_REPLAY/REWORK/HYGIENE subthreshold or borderline rows as the "small/simple" control for `control_fp_count`. The detector should emit zero (or at most one) divergence points on this trace.

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

## Step 1 — IDE Rename field

1. Open `LibrarySystem.java`. Caret on `books` field at line 15. **Shift-F6** (Refactor -> Rename) -> `bookIndex`. Confirm.
2. Save. Run all tests on `src/test/java`. Expect green.
3. Terminal: `git commit -am "rename books -> bookIndex"`.

## Step 2 — IDE Extract Variable

1. In `LibrarySystem.bulkLendBooks` at line 98, caret on `5.0`. **Cmd-Opt-V** (Refactor -> Extract Variable) -> `BOOK_FEE`. Convert to `static final` if prompted.
2. Save. Run tests. Green.
3. Terminal: `git commit -am "extract BOOK_FEE constant"`.

## Step 3 — IDE Rename method

1. Caret on `helperA` declaration at line 121. **Shift-F6** -> `isOverdueNow`. Confirm.
2. Save. Run tests. Green.
3. Terminal: `git commit -am "rename helperA -> isOverdueNow"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 042
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
