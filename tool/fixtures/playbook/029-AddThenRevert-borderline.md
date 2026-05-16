# Session 029 — AddThenRevert (borderline)

**Pattern**: AddThenRevert
**Strength**: borderline
**Expected kind**: REWORK
**Target step**: 1
**Paired control**: 043
**Manifest row to add after capture**:
```
029,AddThenRevert,borderline,1,REWORK,sessions/029/,043
```

## What this session demonstrates

6-line addition to `ReportFormatter.formatReport` at step 1; unrelated refactor at step 2; deletion of the 6 lines at step 3. Borderline rework size (6 lines).

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

## Step 1 — Add 6 lines (target_step = 1)

1. Open `ReportFormatter.java`. In `formatReport`, just before the final `return out.toString();` at line 51, type by hand:
   ```java
           if (member.getType() == Member.MemberType.PREMIUM) {
               out.append("---\n");
               out.append("PREMIUM SUMMARY\n");
               out.append("loans=").append(loans.size()).append("\n");
               out.append("name=").append(member.getName()).append("\n");
               out.append("---\n");
           }
   ```
2. Save. Run tests. Expect green.
3. Terminal: `git commit -am "add premium summary block"`.

## Step 2 — Unrelated IDE Rename elsewhere

1. Open `LateFeeCalculator.java`. Caret on `fee` local at line 13. **Shift-F6** -> rename to `total`. Confirm.
2. Save. Run tests. Expect green.
3. Terminal: `git commit -am "rename fee -> total"`.

## Step 3 — Revert the 6 lines

1. Back in `ReportFormatter.java` `formatReport`, manually delete the 7 added lines (the if and its body — counts as 6 logical content lines).
2. Save. Run tests. Expect green.
3. Terminal: `git commit -am "revert premium summary block"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 029
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
