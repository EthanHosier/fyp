# Session 025 — SuboptimalOrdering (borderline)

**Pattern**: SuboptimalOrdering
**Strength**: borderline
**Expected kind**: ORDERING
**Target step**: 3
**Paired control**: 045
**Manifest row to add after capture**:
```
025,SuboptimalOrdering,borderline,3,ORDERING,sessions/025/,045
```

## What this session demonstrates

2-step window: move `LateFeeCalculator.calc2(Loan loan)` into `Loan` as an instance method first, then rename it. Better order: rename first, then move — because rename-while-still-in-source-class touches fewer locations than rename-after-move. Borderline gap.

(Previously this session targeted `LibrarySystem.validateMember` -> `MemberValidator`, but: (a) IntelliJ's `Refactor -> Move Instance Method` only offers destinations that are parameter types or instance-field types of the source class, so `MemberValidator` never appears in the dropdown; (b) `validateMember`'s body calls `fooBar(...)` — a `LibrarySystem` method — so moving it forces JDT to pass `sys` through as an extra parameter, which then breaks the `validateMember(null)` test case (`null.validateMember(sys)` doesn't compile). `LateFeeCalculator.calc2(Loan loan)` is dependency-free, has `Loan` as a parameter so IntelliJ offers it as a destination, and has a single in-class caller (`sumAll`) that JDT rewires cleanly to `loan.calc2()`.)

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

1. Open `LateFeeCalculator.java`. Caret on the local variable `base` at line 19 (inside `calculateFee`). Press **Shift-F6** -> rename to `tier1Base`. Confirm.
2. Save. Run tests. Expect green.
3. Terminal: `cd fixtures/library-fixture && git commit -am "warmup: rename base -> tier1Base"`.

## Step 2 — IDE Move Method (suboptimal first)

1. Open `LateFeeCalculator.java`. Caret on the `calc2(Loan loan)` declaration at line 51. Press **F6** (Refactor -> Move Instance Method).
2. In the wizard, the destination dropdown lists candidates (parameter types + instance-field types of `LateFeeCalculator`). Pick **`Loan loan`**. Accept defaults; confirm.
3. IntelliJ converts `calc2(Loan loan)` into an instance method `Loan.calc2()` and rewires the only caller (`sumAll` in `LateFeeCalculator`) to `loan.calc2()`.
4. Save. Run tests. Expect green.
5. Terminal: `cd fixtures/library-fixture && git commit -am "move calc2 into Loan"`.

## Step 3 — IDE Rename (terminal step; target_step = 3)

1. Caret on the moved `calc2` method (now declared in `Loan.java`). Press **Shift-F6** -> rename to `feeBucketTwo`. Confirm. IntelliJ updates the call in `LateFeeCalculator.sumAll`.
2. Save. Run tests. Expect green.
3. Terminal: `cd fixtures/library-fixture && git commit -am "rename calc2 -> feeBucketTwo"`.

## End

1. In the plugin: click **End session**. Plugin writes
   `library-fixture/.refactoring-traces/<uuid>/`.
2. From `fixtures/`:
   ```bash
   ./end-session.sh 025
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
