# Session 021 — ManualExtractVariable (subthreshold)

**Pattern**: ManualExtractVariable
**Strength**: subthreshold
**Expected kind**: IDE_REPLAY
**Target step**: 2
**Paired control**: 042
**Manifest row to add after capture**:
```
021,ManualExtractVariable,subthreshold,2,IDE_REPLAY,sessions/021/,042
```

## What this session demonstrates

Single-use extraction of `loan.getDueDate()` into a local in one branch of `ReportFormatter.formatReport`. 1 use = below the IDE_REPLAY floor. Tier 2 should NOT flag.

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

1. Open `ReportFormatter.java`. Caret on local `status` at line 20. **Shift-F6** -> rename to `bookStatus`. Confirm.
2. Save. Run tests. Expect green.
3. Terminal: `git commit -am "warmup: rename status -> bookStatus"`.

## Step 2 — Manual Extract Variable (the bad step)

1. In `ReportFormatter.java`, at line 30 inside the `if (overdue)` branch:
   - Type by hand at the start of the branch (just after line 29 `if (overdue) {`):
     ```java
                   LocalDate due = loan.getDueDate();
     ```
   - On line 30, by hand replace `loan.getDueDate()` with `due`.
2. Do NOT use Refactor -> Extract Variable.
3. Add the import for `java.time.LocalDate` if not already present (it isn't in `ReportFormatter.java` — type by hand at top of file).
4. Save. Run tests. Expect green.
5. Terminal: `git commit -am "manual extract var: due (1 use)"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 021
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
