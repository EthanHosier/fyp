# Session 002 — ManualExtractMethod (loud)

**Pattern**: ManualExtractMethod
**Strength**: loud
**Expected kind**: IDE_REPLAY
**Target step**: 2
**Paired control**: 042
**Manifest row to add after capture**:
```
002,ManualExtractMethod,loud,2,IDE_REPLAY,sessions/002/,042
```

## What this session demonstrates

Same pattern as 001 but on the notification block inside `processReturn`. The user cuts a 12-line notification-building region and pastes it into a hand-typed `sendOverdueNotice(...)` method. The detector should fire IDE_REPLAY at step 2 with an `ExtractMethod` alternative.

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

1. Open `src/main/java/org/library/LibrarySystem.java`. In `bulkLendBooks` at line 94, locate the local variable `bid`.
2. Caret on `bid`, **Shift-F6** -> rename to `bookId`. Confirm.
3. Save. Run all tests (Cmd-Shift-F10 on `src/test/java`). Expect green.
4. Terminal: `git commit -am "warmup: rename bid -> bookId"`.

## Step 2 — Manual Extract Method (the bad step)

1. Open `LibrarySystem.java`. Locate `processReturn` at line 32 and find the notification block at lines 69-82.
2. **Manually** select lines 69-82 inclusive (from `String msg = "OVERDUE: ..."` through `notifications.add(summary);`). Press **Cmd-X**.
3. Where the block was, type the call site by hand:
   ```java
               sendOverdueNotice(loan, book, member, today, daysLate, fee);
   ```
4. Move the caret below the closing brace of `processReturn`. Type the new method header by hand:
   ```java
       private void sendOverdueNotice(Loan loan, Book book, Member member, LocalDate today, long daysLate, double fee) {
   ```
   Press **Cmd-V** to paste. Type the closing `}` by hand.
5. Save. Run all tests on `src/test/java`. Expect green.
6. Terminal: `git commit -am "manual extract: sendOverdueNotice"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 002
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
