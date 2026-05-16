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

User manually moves `LibrarySystem.helperB(Member)` into `Member` as an instance method (`Member.helperB()`) and rewires the 3 in-class call sites by hand instead of using `Refactor -> Move Instance Method`. helperB is called from 3 sites *inside LibrarySystem* — `processReturn`, `bulkLendBooks`, and `fooBar` — so the move forces a visible cross-method cascade of updates. Loud IDE_REPLAY anchor.

(Previously this session targeted `formatReport`, but that method has no internal callers in LibrarySystem and no external production callers — a weak "loud" example. Moving `helperB` mirrors session 014's pattern on a different method/destination pair to give a second loud datapoint.)

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

1. In `LibrarySystem.java`, locate the `helperB(Member member)` declaration (around line 125). **Manually** select all 3 lines:
   ```java
       public boolean helperB(Member member) {
           return member.getType() == MemberType.PREMIUM;
       }
   ```
   Press **Cmd-X** to cut. (Do NOT use `Refactor -> Move Instance Method`.)

2. Open `Member.java`. Place the caret on the blank line before the final `}` of the `Member` class. Press **Cmd-V** to paste.

3. **Manually** convert the pasted method from static-shaped helper into an instance method:
   ```java
       public boolean helperB() {
           return getType() == MemberType.PREMIUM;
       }
   ```
   - Delete the `Member member` parameter.
   - Delete the `member.` prefix on `getType()`.
   - If `Member.java` doesn't yet import `MemberType` directly (it's a nested enum in the same file), the reference resolves without an import.

4. Back in `LibrarySystem.java`, update the 3 internal call sites by hand. Use **Cmd-F** to find `helperB(` in the file (DO NOT use Refactor → Rename, DO NOT use Find/Replace across files — type each replacement at the call site). Replacements:
   - Line ~58 inside `processReturn`: `helperB(member)` → `member.helperB()`
   - Line ~107 inside `bulkLendBooks`: `helperB(member)` → `member.helperB()`
   - Line ~161 inside `fooBar`: `helperB(m)` → `m.helperB()`

5. Save all open files (Cmd-S in each, or Cmd-Shift-S for "Save All").

6. Run all tests (Cmd-Shift-F10 on `src/test/java`). Expect green. If a test fails, fix the wiring by hand — do NOT undo and use the IDE refactor.

7. Commit: `cd fixtures/library-fixture && git commit -am "manual move: helperB into Member"`.

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
