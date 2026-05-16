# Session 020 — ManualExtractVariable (borderline)

**Pattern**: ManualExtractVariable
**Strength**: borderline
**Expected kind**: IDE_REPLAY
**Target step**: 2
**Paired control**: 042
**Manifest row to add after capture**:
```
020,ManualExtractVariable,borderline,2,IDE_REPLAY,sessions/020/,042
```

## What this session demonstrates

The expression `"Loan " + loan.getBookId() + " " + member.getName() + " status="` appears in 4 branches of `ReportFormatter.formatReport` (lines 30, 35, 39, 43, 47). Pick 2 of these as the borderline case: extract a `prefix` local that they share. 2 uses = borderline magnitude.

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

1. Open `ReportFormatter.java`. Caret on local `overdue` at line 21. **Shift-F6** -> rename to `isOverdue`. Confirm.
2. Save. Run tests. Expect green.
3. Terminal: `git commit -am "warmup: rename overdue -> isOverdue"`.

## Step 2 — Manual Extract Variable (the bad step)

1. In `ReportFormatter.java` `formatReport`, locate the `for (Loan loan : loans)` body around lines 19-50. Inside the for-body, just before the `if (overdue) {` at line 29, type by hand:
   ```java
               String prefix = "Loan " + loan.getBookId() + " " + member.getName() + " status=";
   ```
2. **Manually** edit the 2 branches at lines 35 and 39 (AVAILABLE and LENT) to use `prefix`:
   - Line 35: change `String line = "Loan " + loan.getBookId() + " " + member.getName() + " status=AVAILABLE returned=true";` to `String line = prefix + "AVAILABLE returned=true";`.
   - Line 39: change to `String line = prefix + "LENT due=" + loan.getDueDate();`.
   Leave the other branches as-is — the borderline test is only 2 uses.
3. Do NOT use Refactor -> Extract Variable.
4. Save. Run tests. Expect green.
5. Terminal: `git commit -am "manual extract var: prefix (2 uses)"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 020
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
