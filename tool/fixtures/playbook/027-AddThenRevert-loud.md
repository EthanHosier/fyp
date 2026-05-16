# Session 027 — AddThenRevert (loud)

**Pattern**: AddThenRevert
**Strength**: loud
**Expected kind**: REWORK
**Target step**: 1
**Paired control**: 045
**Manifest row to add after capture**:
```
027,AddThenRevert,loud,1,REWORK,sessions/027/,045
```

## What this session demonstrates

User adds a 15-line "discount calc" block to `LibrarySystem.processReturn` at step 1 (the originating add — REWORK anchors here), performs an unrelated rename at step 2, then at step 3 realises the addition was wrong and deletes it. ReworkDetector should pair step 1 (add) with step 3 (revert) and emit a REWORK divergence point anchored at step 1.

Note: REWORK sessions have NO step-1 warmup — the bad add IS step 1.

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

## Step 1 — Add a 15-line block (target_step = 1)

1. Open `LibrarySystem.java`. Locate `processReturn` at line 32. Place caret just before `loan.markReturned();` at line 85.
2. Type by hand a 15-line "courtesy discount" block:
   ```java
           // courtesy discount
           double courtesy = 0.0;
           if (member.getType() == MemberType.PREMIUM) {
               courtesy = fee * 0.10;
           } else if (member.getType() == MemberType.STAFF) {
               courtesy = fee * 0.20;
           } else {
               courtesy = 0.0;
           }
           if (daysLate > 14) {
               courtesy = courtesy * 1.5;
           }
           if (courtesy > fee) {
               courtesy = fee;
           }
           fee = fee - courtesy;
   ```
3. Move the `double daysLate` calculation up if needed, or wrap in `if (overdueFlag)`. Save. Run tests. Expect green (the math is benign).
4. Terminal: `git commit -am "add courtesy discount block"`.

## Step 2 — Unrelated IDE Rename

1. Caret on `notifications` field at `LibrarySystem.java` line 18. **Shift-F6** -> rename to `noticeLog`. Confirm.
2. Save. Run tests. Expect green.
3. Terminal: `git commit -am "rename notifications -> noticeLog"`.

## Step 3 — Revert the addition

1. Open `LibrarySystem.java`. Locate the 15-line courtesy block you added at step 1.
2. **Manually** select all 15 lines and Backspace/Cmd-X to delete.
3. Save. Run tests. Expect green.
4. Terminal: `git commit -am "revert courtesy discount block"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 027
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
