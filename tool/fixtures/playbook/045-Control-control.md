# Session 045 — Control (control)

**Pattern**: Control
**Strength**: control
**Expected kind**: none
**Target step**: -1

(No manifest row.)

## What this session demonstrates

Longest clean session: 8 IDE-driven refactors across all files. Tests after each. Commits every ~2 refactors (4 commits in total). Used as the "long" control for ORDERING (022-026) and COMMIT_GAP (037-040) injection rows — a fair sample of how many divergence points the detector emits on long un-injected behaviour.

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

## Refactors 1-8 (tests after each, commits in groups)

1. **Step 1**: `LibrarySystem.java` line 15 — **Shift-F6** on `books` -> `bookIndex`. Save. Tests green.
2. **Step 2**: `LibrarySystem.java` line 16 — **Shift-F6** on `members` -> `memberIndex`. Save. Tests green.
3. Terminal: `git commit -am "rename collection fields"`.
4. **Step 3**: `LibrarySystem.java` line 121 — **Shift-F6** on `helperA` -> `isOverdueNow`. Save. Tests green.
5. **Step 4**: `LibrarySystem.java` line 125 — **Shift-F6** on `helperB` -> `isPremium`. Save. Tests green.
6. Terminal: `git commit -am "rename helper methods"`.
7. **Step 5**: `LibrarySystem.java` `bulkLendBooks` line 103 — **Cmd-Opt-V** on `0.85` -> `BULK_TIER_LARGE` (constant). Save. Tests green.
8. **Step 6**: `LibrarySystem.java` `bulkLendBooks` line 105 — **Cmd-Opt-V** on `0.95` -> `BULK_TIER_MEDIUM`. Save. Tests green.
9. Terminal: `git commit -am "extract bulk tier constants"`.
10. **Step 7**: `LateFeeCalculator.java` line 42 — **Shift-F6** on `helper` -> `applyDiscount`. Save. Tests green.
11. **Step 8**: `ReportFormatter.java` line 14 — **Shift-F6** on `out` -> `report`. Save. Tests green.
12. Terminal: `git commit -am "rename applyDiscount + formatter local"`.

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
