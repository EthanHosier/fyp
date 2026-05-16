# Session 015 — ManualMoveMethod (loud)

**Pattern**: ManualMoveMethod
**Strength**: loud
**Expected kind**: IDE_REPLAY
**Target step**: 2
**Paired control**: 044
**Manifest row to add after capture**:
```
015,ManualMoveMethod,loud,2,IDE_REPLAY,sessions/015/,044
```

## What this session demonstrates

User manually moves `LibrarySystem.formatReport` (lines 168-178) into `ReportFormatter`. External call-sites (in tests) must be rewired by hand. Loud IDE_REPLAY anchor.

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

## Step 2 — Manual Move Method (the bad step)

1. In `LibrarySystem.java`, **manually** select lines 168-178 — the entire `formatReport(Member m)` method. **Cmd-X**.
2. Open `ReportFormatter.java`. Paste (Cmd-V) below the existing `formatReport(List<Loan>, Member)` method (around line 53). Rename the pasted method by hand to `formatMemberReport(Member m, Map<String, Loan> loans)` and pass dependencies in (no `this.loans` available in ReportFormatter).
3. Edit by hand:
   - Replace `loans.values()` with the passed-in `loans.values()`.
   - Replace `fooBar(m.getId(), LocalDate.now())` — `fooBar` doesn't exist in `ReportFormatter`; inline a stub `String header = "notify:" + m.getName();` or pass the header in.
4. In `LibrarySystem.java`, replace the now-gone `formatReport(m)` method with a delegating one-liner that calls `reportFormatter.formatMemberReport(m, loans)`. Type by hand.
5. Update test callers in `LibrarySystemTest.java` and `ReportFormatterTest.java` if needed.
6. Save all. Run tests. Expect green.
7. Terminal: `git commit -am "manual move: formatReport -> ReportFormatter"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 015
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
