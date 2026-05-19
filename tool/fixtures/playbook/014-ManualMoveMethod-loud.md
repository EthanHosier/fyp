# Session 014 — ManualMoveMethod (loud)

**Pattern**: ManualMoveMethod
**Strength**: loud
**Expected kind**: IDE_REPLAY
**Target step**: 2
**Paired control**: 044
**Manifest row to add after capture**:
```
014,ManualMoveMethod,loud,2,IDE_REPLAY,sessions/014/,044
```

## What this session demonstrates

User manually moves `LibrarySystem.helperA(Loan)` into `Loan` as an instance method (`Loan.helperA()`) and rewires the 4 in-class call sites by hand instead of using `Refactor -> Move Instance Method`. helperA is called from 4 sites *inside LibrarySystem* — `processReturn`, `bulkLendBooks`, `findOverdueLoans`, and `fooBar` — so the move forces a visible cross-method cascade of updates. Loud IDE_REPLAY anchor.

(Previously this session targeted `validateMember`, but that method has no internal callers in LibrarySystem and only one external caller in `Main.java` — a weak "loud" example. `helperA` gives the strongest cross-method cascade available in the fixture.)

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

1. Open `LibrarySystem.java`. Caret on `loans` field at line 17. **Shift-F6** -> rename to `loanIndex`. Confirm.
2. Save. Run tests. Expect green.
3. Terminal: `git commit -am "warmup: rename loans -> loanIndex"`.

## Step 2 — Manual Move Method (the bad step)

1. In `LibrarySystem.java`, locate the `helperA(Loan loan)` declaration (around line 121). **Manually** select all 3 lines:
   ```java
       public boolean helperA(Loan loan) {
           return loan.isOverdue(LocalDate.now());
       }
   ```
   Press **Cmd-X** to cut. (Do NOT use `Refactor -> Move Instance Method` — that would be the IDE-driven equivalent.)

2. Open `Loan.java`. Place the caret on the blank line before the final `}` of the `Loan` class (around line 40). Press **Cmd-V** to paste.

3. **Manually** edit the pasted method to convert it from a static-shaped helper into an instance method:
   ```java
       public boolean helperA() {
           return isOverdue(LocalDate.now());
       }
   ```
   - Delete the `Loan loan` parameter.
   - Delete the `loan.` prefix on the `isOverdue` call.

4. Back in `LibrarySystem.java`, update the 4 internal call sites by hand. Use **Cmd-F** to find `helperA(` in the file (DO NOT use Refactor → Rename, DO NOT use Find/Replace across files — type each replacement at the call site). Replacements:
   - Line ~46 inside `processReturn`: `helperA(loan)` → `loan.helperA()`
   - Line ~112 inside `bulkLendBooks`: `helperA(ln)` → `ln.helperA()`
   - Line ~141 inside `findOverdueLoans`: `helperA(loan)` → `loan.helperA()`
   - Line ~157 inside `fooBar`: `helperA(loan)` → `loan.helperA()`

5. Save all open files (Cmd-S in each, or Cmd-Shift-S for "Save All").

6. Run all tests (Cmd-Shift-F10 on `src/test/java`). Expect green. If a test fails, fix the wiring by hand — do NOT undo and use the IDE refactor.

7. Commit: `cd fixtures/library-fixture && git commit -am "manual move: helperA into Loan"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 014
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
