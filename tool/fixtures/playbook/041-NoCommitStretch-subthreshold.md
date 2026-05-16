# Session 041 — NoCommitStretch (subthreshold)

**Pattern**: NoCommitStretch
**Strength**: subthreshold
**Expected kind**: HYGIENE
**Target step**: 5
**Paired control**: 042
**Manifest row to add after capture**:
```
041,NoCommitStretch,subthreshold,5,HYGIENE,sessions/041/,042
```

## What this session demonstrates

Only 5 green refactor checkpoints uncommitted before the commit at step 6. Below MIN_COMMIT_GAP (= 6). HygieneDetector should NOT flag.

**CRUCIAL**: Stop at 5. Not 6, not 4. NO commits between steps 1 and 5.

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

## Steps 1-5 — five refactors, tests after each, NO commits

1. **Step 1**: `LibrarySystem.java` line 15 — `Shift-F6` on `books` field -> `bookIndex`. Save. Tests green. **NO commit.**
2. **Step 2**: `LibrarySystem.java` line 16 — `Shift-F6` on `members` -> `memberIndex`. Save. Tests green. **NO commit.**
3. **Step 3**: `LibrarySystem.java` line 17 — `Shift-F6` on `loans` -> `loanIndex`. Save. Tests green. **NO commit.**
4. **Step 4**: `LibrarySystem.java` line 18 — `Shift-F6` on `notifications` -> `noticeLog`. Save. Tests green. **NO commit.**
5. **Step 5** — target_step = 5: `LibrarySystem.java` line 121 — `Shift-F6` on `helperA` -> `isOverdueNow`. Save. Tests green. **NO commit yet.**

## Step 6 — Finally commit

1. Terminal: `git commit -am "5 refactors batched"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 041
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
