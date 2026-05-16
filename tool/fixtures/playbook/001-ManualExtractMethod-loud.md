# Session 001 — ManualExtractMethod (loud)

**Pattern**: ManualExtractMethod
**Strength**: loud
**Expected kind**: IDE_REPLAY
**Target step**: 2
**Paired control**: 042
**Manifest row to add after capture**:
```
001,ManualExtractMethod,loud,2,IDE_REPLAY,sessions/001/,042
```

## What this session demonstrates

The user manually extracts the entire overdue-processing branch (~36 self-contained lines) out of `LibrarySystem.processReturn` using cut/paste and a hand-typed signature instead of `Refactor -> Extract Method`. The synthesiser should detect that an `ExtractMethod` IDE refactor would have produced an equivalent edit at lower process cost, producing an `IDE_REPLAY` divergence point at step 2. This is the canonical loud IDE_REPLAY probe.

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

1. Open `src/main/java/org/library/LibrarySystem.java`.
2. Locate the local variable `discount` inside `bulkLendBooks` at line 101.
3. Place the caret on `discount`, press **Shift-F6** (Refactor -> Rename), rename to `bulkDiscount`. Confirm.
4. Save (Cmd-S).
5. Run all tests: right-click `src/test/java` -> Run 'All Tests' (or Cmd-Shift-F10 on the directory). Expect green.
6. In a terminal: `cd fixtures/library-fixture && git commit -am "warmup: rename discount -> bulkDiscount"`.

After step 1: tests pass, one commit on top of baseline.

## Step 2 — Manual Extract Method (the bad step)

We extract the **entire overdue branch** — every local declared inside lives inside the cut block, so no parameters need to come back out. The helper returns `fee` and that's the only value the call site cares about.

1. Open `src/main/java/org/library/LibrarySystem.java`. Locate `processReturn(...)` at line 32.
2. **Manually** select lines 48-83 inclusive — the entire body of the `if (overdueFlag) { ... }`, beginning with `long daysLate = loan.daysOverdue(today);` and ending with `fooBar(member.getId(), today);`. **Do NOT include** the closing `}` of the if at line 84. Press **Cmd-X** to cut.
3. In place of the cut block, type the call site by hand (still inside the if):
   ```java
               fee = handleOverdueReturn(loan, member, book, today);
   ```
   (Do NOT use Refactor -> Extract Method. Do NOT use Live Templates.)
4. Move the caret below the closing `}` of `processReturn` (the outer method-closing brace, near the bottom of the class). Type the new method by hand. First the signature:
   ```java
       private double handleOverdueReturn(Loan loan, Member member, Book book, LocalDate today) {
           double fee = 0.0;
   ```
   Press **Cmd-V** to paste the cut block. Then type the closing return + brace by hand:
   ```java
           return fee;
       }
   ```
5. Save (Cmd-S).
6. Run all tests (Cmd-Shift-F10 on `src/test/java`). Expect green. If a test fails, fix the wiring by hand — do NOT undo and use the IDE refactor.
7. Commit: in a terminal, `cd fixtures/library-fixture && git commit -am "manual extract: handleOverdueReturn"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 001
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
