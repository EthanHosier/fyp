# Session 010 — ManualInlineMethod (loud)

**Pattern**: ManualInlineMethod
**Strength**: loud
**Expected kind**: IDE_REPLAY
**Target step**: 2
**Paired control**: 043
**Manifest row to add after capture**:
```
010,ManualInlineMethod,loud,2,IDE_REPLAY,sessions/010/,043
```

## What this session demonstrates

User manually substitutes the body of `LibrarySystem.helperA` (declared line 121, called at lines 46, 112, 141, 157 — 4 sites) at every call site, then deletes the method. Should have been `Refactor -> Inline Method`. Loud IDE_REPLAY anchor.

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

1. Open `LibrarySystem.java`. Caret on `notifications` field at line 18. **Shift-F6** -> rename to `noticeLog`. Confirm.
2. Save. Run tests. Expect green.
3. Terminal: `git commit -am "warmup: rename notifications -> noticeLog"`.

## Step 2 — Manual Inline Method (the bad step)

1. Open `LibrarySystem.java`. `helperA` declaration is at lines 121-123: `return loan.isOverdue(LocalDate.now());`.
2. At each of the 4 call sites, **manually** replace `helperA(loan)` (or `helperA(ln)`) with `loan.isOverdue(LocalDate.now())` (or `ln.isOverdue(LocalDate.now())`). Use direct typing / cut+paste — do NOT use Refactor -> Inline (Cmd-Opt-N).
   - Line 46: `boolean overdueFlag = helperA(loan);` -> `boolean overdueFlag = loan.isOverdue(LocalDate.now());`
   - Line 112: `if (ln.getMemberId().equals(memberId) && helperA(ln)) {` -> `... && ln.isOverdue(LocalDate.now())) {`
   - Line 141: `if (helperA(loan)) {` -> `if (loan.isOverdue(LocalDate.now())) {`
   - Line 157: `if (loan.getMemberId().equals(memberId) && helperA(loan)) {` -> `... && loan.isOverdue(LocalDate.now())) {`
3. Manually delete the entire `helperA` method (lines 121-123). Select and Backspace.
4. Save. Run all tests. Expect green.
5. Terminal: `git commit -am "manual inline: helperA"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 010
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
