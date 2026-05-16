# Session 011 — ManualInlineMethod (loud)

**Pattern**: ManualInlineMethod
**Strength**: loud
**Expected kind**: IDE_REPLAY
**Target step**: 2
**Paired control**: 043
**Manifest row to add after capture**:
```
011,ManualInlineMethod,loud,2,IDE_REPLAY,sessions/011/,043
```

## What this session demonstrates

Manual inline of `LibrarySystem.helperB(member)` (declared at line 125, body `return member.getType() == MemberType.PREMIUM;`). Called at lines 58, 107, 161 — 3 sites. Loud IDE_REPLAY anchor.

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

1. Open `LibrarySystem.java`. Caret on `books` field at line 15. **Shift-F6** -> rename to `bookIndex`. Confirm.
2. Save. Run tests. Expect green.
3. Terminal: `git commit -am "warmup: rename books -> bookIndex"`.

## Step 2 — Manual Inline Method (the bad step)

1. In `LibrarySystem.java`, at each of these call sites manually replace `helperB(member)` (or `helperB(m)`) with `member.getType() == MemberType.PREMIUM` (substituting the actual variable):
   - Line 58 (inside `processReturn`): `if (helperB(member)) {` -> `if (member.getType() == MemberType.PREMIUM) {`
   - Line 107 (inside `bulkLendBooks`): `if (helperB(member)) {` -> `if (member.getType() == MemberType.PREMIUM) {`
   - Line 161 (inside `fooBar`): `if (helperB(m)) {` -> `if (m.getType() == MemberType.PREMIUM) {`
2. Use cut/paste or typing. Do NOT use Refactor -> Inline (Cmd-Opt-N).
3. Manually delete the `helperB` method declaration (lines 125-127).
4. Save. Run all tests. Expect green.
5. Terminal: `git commit -am "manual inline: helperB"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 011
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
