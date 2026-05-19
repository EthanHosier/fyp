# Session 043 — AddThenRevert (borderline)

**Pattern**: AddThenRevert
**Strength**: borderline
**Expected kinds**: REWORK;ORDERING
**Target step**: 1
**Paired control**: 045
**Manifest row to add after capture**:
```
043,AddThenRevert,borderline,1,REWORK;ORDERING,sessions/043/,045
```

## What this session demonstrates

Originally drafted as the "medium" control session (5 IDE refactors mixing `LibrarySystem` and `ReportFormatter`, tests after each, 3 commits grouped logically). The actual recording included a 2-line add-then-revert in `ReportFormatter.formatReport`, sitting exactly at `MIN_REWORK_LINES` — borderline REWORK. The session was reclassified post-recording rather than re-recorded.

A perfect detector should surface REWORK (the 2-line revert) and ORDERING (the IDE refactors are independent and reorderable).

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

1. `LibrarySystem.java` line 18 — **Shift-F6** on `notifications` -> `noticeLog`. Confirm.
2. Save. Run tests. Green.

## Step 2 — IDE Rename method (commit at end of group)

1. `LibrarySystem.java` line 121 — **Shift-F6** on `helperA` -> `isOverdueNow`. Confirm.
2. Save. Run tests. Green.
3. Terminal: `git commit -am "rename notifications + helperA"`.

## Step 3 — IDE Extract Variable

1. `ReportFormatter.java` `formatReport` — in the for-loop body, just before `if (overdue)` at line 29, manually no — use IDE. Select the expression `"Loan " + loan.getBookId() + " " + member.getName() + " status="` on line 30. **Cmd-Opt-V** -> `prefix`. Confirm (or apply to that occurrence).
2. Save. Run tests. Green.

## Step 4 — IDE Rename local (commit at end of group)

1. `ReportFormatter.java` line 14 — **Shift-F6** on `out` -> `report`. Confirm.
2. Save. Run tests. Green.
3. Terminal: `git commit -am "report formatter cleanup"`.

## Step 5 — IDE Extract Method (final commit)

1. `ReportFormatter.formatReport` — select lines 16-18 (the empty-loans guard). **Cmd-Opt-M** -> `emptyReport(report, member)`. Confirm.
2. Save. Run tests. Green.
3. Terminal: `git commit -am "extract emptyReport guard"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 043
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
