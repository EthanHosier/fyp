# Session 041 — Control (control)

**Pattern**: Control
**Strength**: control
**Expected kinds**: ORDERING
**Target step**: -1
**Paired control**: —
**Manifest row to add after capture**:
```
041,Control,control,-1,ORDERING,sessions/041/,
```

## What this session demonstrates

5 clean IDE-driven renames on `LibrarySystem` fields and helpers, tests green after each, one commit at the end. No bad-process injection. Originally drafted as `NoCommitStretch (subthreshold)` to probe the COMMIT_GAP floor (`MIN_COMMIT_GAP = 6` → 5 should not fire HYGIENE), but recast as an additional control session: it exercises the same clean-cadence behaviour that the larger controls (042–045) cover, just at a smaller scale. The COMMIT_GAP "subthreshold negative" measurement that this row used to provide is now redundant with the natural absence of COMMIT_GAP DPs across the four controls.

A perfect detector should surface ORDERING alts for the 5 independent IDE refactors (their permutations exist as alternatives), but should NOT surface HYGIENE / REWORK / IDE_REPLAY since no bad behaviour was injected.

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

## Steps 1-5 — five refactors, tests after each, NO commits

1. **Step 1**: `LibrarySystem.java` line 15 — `Shift-F6` on `books` field -> `bookIndex`. Save. Tests green. **NO commit.**
2. **Step 2**: `LibrarySystem.java` line 16 — `Shift-F6` on `members` -> `memberIndex`. Save. Tests green. **NO commit.**
3. **Step 3**: `LibrarySystem.java` line 17 — `Shift-F6` on `loans` -> `loanIndex`. Save. Tests green. **NO commit.**
4. **Step 4**: `LibrarySystem.java` line 18 — `Shift-F6` on `notifications` -> `noticeLog`. Save. Tests green. **NO commit.**
5. **Step 5** — target_step = 5: `LibrarySystem.java` line 121 — `Shift-F6` on `helperA` -> `isOverdueNow`. Save. Tests green. **NO commit yet.**

## Step 6 — Finally commit

1. Terminal: `git commit -am "5 refactors batched"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 041
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
