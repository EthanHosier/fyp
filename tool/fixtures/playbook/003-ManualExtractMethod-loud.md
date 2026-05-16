# Session 003 — ManualExtractMethod (loud)

**Pattern**: ManualExtractMethod
**Strength**: loud
**Expected kind**: IDE_REPLAY
**Target step**: 2
**Paired control**: 043
**Manifest row to add after capture**:
```
003,ManualExtractMethod,loud,2,IDE_REPLAY,sessions/003/,043
```

## What this session demonstrates

Manual extraction of an 18-line repeated branch-builder region from `ReportFormatter.formatReport` into a hand-written `buildStatusLine` helper. Loud IDE_REPLAY probe on a different file.

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

1. Open `src/main/java/org/library/ReportFormatter.java`. Locate `out` at line 14.
2. Caret on `out`, **Shift-F6** -> rename to `sb`. Confirm.
3. Save. Run tests (Cmd-Shift-F10 on `src/test/java`). Expect green.
4. Terminal: `git commit -am "warmup: rename out -> sb"`.

## Step 2 — Manual Extract Method (the bad step)

1. Open `ReportFormatter.java`. Locate `formatReport` at line 12 and the if/else-if status chain at lines 29-49.
2. **Manually** select lines 29-49 inclusive — the entire chain from `if (overdue) { ...` through the trailing `}` of the final `else`. Press **Cmd-X**.
3. Where the block was, type by hand:
   ```java
               sb.append(buildStatusLine(loan, member, status, overdue));
   ```
4. Below the closing brace of `formatReport`, type a new method by hand:
   ```java
       private String buildStatusLine(Loan loan, Member member, BookStatus status, boolean overdue) {
           StringBuilder b = new StringBuilder();
   ```
   Press **Cmd-V** to paste the cut block. By hand, edit each `out.append(...)` inside the pasted block to `b.append(...)`. At the bottom of the helper type:
   ```java
           return b.toString();
       }
   ```
5. Save. Run all tests. Expect green.
6. Terminal: `git commit -am "manual extract: buildStatusLine"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 003
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
