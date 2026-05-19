# Session 045 — Control (control)

**Pattern**: Control
**Strength**: control
**Expected kind**: none
**Target step**: -1

(No manifest row.)

## What this session demonstrates

Pure-manual-edit control: the user opens one file and makes a handful of text edits that are deliberately *not* refactorings — adding a comment, tweaking a string literal, adding a no-op `System.out.println` then deleting it the natural way (not as a "revert"). No IDE refactorings, no rename/extract/move-shaped manual edits. Tests after each edit; commits scattered through.

The purpose is to measure the detector's background DP density on a session where there is genuinely no refactoring to find — both injected *and* un-injected refactorings absent. Used as the "long" baseline for ORDERING (022-026) and COMMIT_GAP (037-040) injection rows.

NOTE on COMMIT_GAP: this session has no refactor checkpoints, so it can't trigger COMMIT_GAP either. It is a true zero-DP expectation for that kind. For ORDERING / IDE_REPLAY background-density measurement, expect zero hits in principle; any DP that fires here is genuine detector noise worth investigating.

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

## Manual text edits (NO refactorings)

All edits in `LibrarySystem.java`. Type by hand — do not use Shift-F6, Cmd-Opt-M, F6, Cmd-Opt-V, etc.

1. **Edit 1**: At the top of the class (just before `public class LibrarySystem {` at line 13), type a Javadoc block by hand:
   ```java
   /**
    * Top-level entry point for the library domain.
    */
   ```
   Save (Cmd-S). Run all tests (Cmd-Shift-F10). Expect green.
2. Terminal: `cd fixtures/library-fixture && git commit -am "add Javadoc on LibrarySystem"`.
3. **Edit 2**: Inside the `LibrarySystem(...)` constructor body (lines 22-26), add a one-line comment above the first `for` loop:
   ```java
   // populate book index
   ```
   Save. Run tests. Expect green.
4. Terminal: `git commit -am "comme   ```
   This moves the trace dir to `sessions/<id>/` and (for injection whitespace"`.
5. **Edit 3**: Add a blank line between two existing method declarations near the bottom of `LibrarySystem.java`. Save. Tests green.
6.Terminal: `git commit -am "cosmetic blank line"`.

None of these edits are refactorings; `RefactoringMiner` should detect nothing across this session, and the synthesiser should have no alts to emit.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 045
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
