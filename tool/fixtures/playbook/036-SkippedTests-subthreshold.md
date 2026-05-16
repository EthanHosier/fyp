# Session 036 — SkippedTests (subthreshold)

**Pattern**: SkippedTests
**Strength**: subthreshold
**Expected kind**: HYGIENE
**Target step**: 1
**Paired control**: 042
**Manifest row to add after capture**:
```
036,SkippedTests,subthreshold,1,HYGIENE,sessions/036/,042
```

## What this session demonstrates

A single IDE refactor with no test run before commit. One refactor cannot form a composite of size >= 2, so HygieneDetector should NOT flag it.

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

## Step 1 — Single IDE Rename, no tests (target_step = 1)

1. Open `LibrarySystem.java`. Caret on `helperA` declaration at line 121. **Shift-F6** -> rename to `isOverdueNow`. Confirm.
2. Save. **DO NOT run tests.**
3. Terminal: `git commit -am "rename helperA -> isOverdueNow (no tests)"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 036
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
