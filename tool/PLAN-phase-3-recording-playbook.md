# Plan: Phase 3 — Fixture codebase + 45-session recording playbook

## Context

Phase 1 (injectable `ScoringConfig`, Phase A/B pipeline split) and
Phase 2 (sensitivity / ablation / divergence experiment drivers) are
**done and merged** (PRs #57, #58). The drivers consume serialised
`PhaseAResult` JSONs and a manifest CSV. What blocks headline results
now is the *corpus* — recorded sessions in which the user deliberately
performs each bad-process pattern, against a frozen fixture codebase
whose smell topology is known.

This plan answers two concrete questions the user asked:

1. **What exactly does the fixture codebase look like?** Hand-author a
   new ~500 LOC Java + JUnit 5 + Gradle project (`library-fixture`)
   with deliberate smell hotspots in known locations. The existing
   `exampleRefactoringProject` is too small (179 LOC, supports
   ~5–8 sessions max).
2. **What exactly does each of the ~45 recordings consist of?** A
   per-session script: which file to open, what edits to perform, in
   what order, where the "bad" step lives, and how that maps to a
   `DivergenceKind` + ground-truth `target_step`.

Total session count: **45**, split across **9 injection patterns
(41 sessions)** + **4 control sessions** referenced via
`control_session_id`.

Phase 4 (plots + writeup prose + GenAI disclosure) is downstream of
this plan and unchanged.

---

## Fixture codebase: `library-fixture`

A small library-lending domain (familiar enough that smells are
recognisable, but not large enough to overwhelm). Hand-authored,
frozen at a baseline git tag. Java 17, Gradle 8, JUnit 5.

### Files

Location: `/Users/ethanhosier/Desktop/random/fyp/tool/fixtures/library-fixture/`
(outside `analysis/` so it isn't packaged into the analysis JAR; the
recording IDE opens it directly).

```
library-fixture/
├── build.gradle.kts                       # JUnit 5; nothing exotic.
├── settings.gradle.kts
└── src/
    ├── main/java/org/library/
    │   ├── Main.java                      # ~15  LOC. Demo entry point.
    │   ├── Book.java                      # ~30  LOC. POJO: id, title, author, isbn, status.
    │   ├── Member.java                    # ~40  LOC. POJO + MemberType enum (STANDARD, PREMIUM, STAFF).
    │   ├── Loan.java                      # ~40  LOC. POJO: bookId, memberId, lentDate, dueDate.
    │   ├── LibrarySystem.java             # ~180 LOC. God class — the main smell vehicle.
    │   ├── LateFeeCalculator.java         # ~80  LOC. Long method + magic numbers + bad names.
    │   ├── ReportFormatter.java           # ~70  LOC. switch-on-type + string concat soup.
    │   └── MemberValidator.java           # ~45  LOC. Duplicate-of-LibrarySystem validation.
    └── test/java/org/library/
        ├── LibrarySystemTest.java         # ~80  LOC. Lending + return + overdue happy paths.
        ├── LateFeeCalculatorTest.java     # ~50  LOC. Each member-type fee tier covered.
        └── ReportFormatterTest.java       # ~40  LOC. Each report variant covered.
```

Approx **~500 LOC source + ~170 LOC test**, all tests green at the
baseline tag.

### Deliberate smell hotspots (the refactoring victims)

Every smell is placed where multiple sessions can target the same
file without conflicting with each other (sessions are recorded from
the **baseline tag** each time — no carryover between sessions).

**`LibrarySystem.java`** — the workhorse:
- `processReturn(loanId)` ~50 LOC, lines ~40-95. Deep nesting: overdue
  branch → fee calc → notification branch. **Extract-method targets**:
  the fee-calc block (lines ~55-75) and the notification block
  (lines ~80-92).
- `bulkLendBooks(memberId, bookIds)` ~25 LOC, magic numbers
  `0.85` / `0.95` / `0.70` for bulk-discount tiers. **Extract-variable
  targets**.
- `helperA(loan)` and `helperB(member)` — trivial 2-line wrappers,
  called 4× and 3× respectively. **Inline-method targets**.
- `validateMember(member)` — same body as `MemberValidator.validate()`.
  **Move-method target** (move *into* `MemberValidator`).
- `findOverdueLoans()` — ~10 LOC. Light smell; subthreshold-extract
  target.
- `fooBar()` — a method with an awful name that gets called from 6
  call-sites. **Rename target** (→ `processOverdueNotifications`).

**`LateFeeCalculator.java`**:
- `calculateFee(loan, member)` ~40 LOC, lines ~15-55. switch-on-
  member-type with repeated `(member.type == PREMIUM ? 0.5 : ...)`
  expression appearing 4×. **Extract-variable + extract-method
  targets**.
- `helper()` — opaque name, **rename target** (→ `applyDiscount`).
- `calc1()`, `calc2()`, `calc3()` — opaque names, **rename targets**.

**`ReportFormatter.java`**:
- `formatReport(loans, member)` ~50 LOC. switch-on-loan-status with
  duplicated string-concat in every branch. **Extract-method**
  (status-line builder) **+ extract-variable** (header string) targets.

**`MemberValidator.java`**:
- `validate(member)` shares body with `LibrarySystem.validateMember`.
  The *opposite* move-method case (target sits here; user might move
  it out to LibrarySystem). Plus `t1` field rename target.

### Baseline tag

`git tag library-fixture-v1` at the frozen state. Every recording
session begins by checking out this tag into a fresh worktree, opening
that worktree in the IDE, and starting the plugin in record mode.

---

## Pattern playbook

Nine injection patterns. Strength tiers per `PLAN-experiment.md`:

- **Loud**: well above the detector's magnitude floor — should be
  caught by both Tier 1 and Tier 2.
- **Borderline**: near the magnitude floor (3.0 process points for
  IDE_REPLAY/ORDERING/HYGIENE_TESTS_SKIPPED; 2 lines for REWORK;
  6 checkpoints for COMMIT_GAP).
- **Subthreshold**: below the floor — *should not* fire. False-positive
  probe.

For each pattern, all sessions start from the `library-fixture-v1`
baseline, do ≥1 IDE-driven refactor as warmup, then commit the
warmup so the bad-process step is clearly bounded by checkpoint
indices.

### 1. `ManualExtractMethod` (IDE_REPLAY)
Cut a contiguous block, paste into a new method, type the signature
by hand, replace the original block with a call. The plugin records
the keystrokes as a manual edit. The synthesiser detects "this looks
like an Extract Method that the IDE could have done" → IDE_REPLAY alt.
- Loud: block ≥ 15 lines.
- Borderline: block 5–8 lines.
- Subthreshold: block ≤ 3 lines.

### 2. `ManualRenameMethod` (IDE_REPLAY)
Multi-cursor or find/replace to rename a method across all call sites
without using IDE Rename. Synthesiser → IDE_REPLAY RenameMethod alt.
- Loud: method called from ≥ 6 sites.
- Borderline: 2–3 sites.
- Subthreshold: single private call site.

### 3. `ManualInlineMethod` (IDE_REPLAY)
Manually substitute each call-site with the method body, then delete
the method. IDE_REPLAY InlineMethod alt.
- Loud: method called from ≥ 4 sites.
- Borderline: 2 sites.
- Subthreshold: single call site, tiny body.

### 4. `ManualMoveMethod` (IDE_REPLAY)
Cut a method from one class, paste into another, update references
by hand. IDE_REPLAY MoveInstanceMethod / MoveStaticMembers alt.
- Loud: method has external call-sites that need updating.
- Borderline: 1 external call-site.
- Subthreshold: method has zero external callers — trivial move.

### 5. `ManualExtractVariable` (IDE_REPLAY)
Manually pull a repeated sub-expression into a `var` / `final
type x = ...` above its uses. IDE_REPLAY ExtractVariable alt.
- Loud: expression used ≥ 4 times.
- Borderline: 2 uses.
- Subthreshold: 1 use.

### 6. `SuboptimalOrdering` (ORDERING)
Perform a known-redundant order of refactors within a small window
(typically 3 steps). The synthesiser permutes the window and finds
the better order. ORDERING magnitude = (best alt — user) process-
score delta.
- Loud: 3-step window with ≥ 10 point gap to best permutation.
- Borderline: 2-step window, 3–8 point gap.
- Subthreshold: 2-step window where order genuinely doesn't matter
  (gap < 3).

### 7. `AddThenRevert` (REWORK)
Add a non-trivial block to a method (or add a helper method); make
some intermediate edits; later remove the added block (or method).
ReworkDetector pairs the originating add with the terminal remove.
- Loud: added/reverted block ≥ 15 lines.
- Borderline: block 4–10 lines.
- Subthreshold: block exactly 2 lines (at `MIN_REWORK_LINES` floor).

### 8. `SkippedTests` (HYGIENE / TESTS_SKIPPED)
String together ≥ 2 refactoring steps within the composite window
(60s default) **without running tests between them**. Then run tests.
HygieneDetector flags the composite anchor.
- Loud: 3 consecutive untested refactors before the eventual run.
- Borderline: 2 untested refactors.
- Subthreshold: 1 untested refactor (doesn't form a composite ≥ 2).

### 9. `NoCommitStretch` (HYGIENE / COMMIT_GAP)
Perform `≥ MIN_COMMIT_GAP (= 6)` consecutive green refactor
checkpoints (refactor + tests pass) without committing.
- Loud: 8+ green checkpoints uncommitted.
- Borderline: exactly 6 (at floor).
- Subthreshold: 5 (below floor — should not flag).

### 10. Controls
4 clean sessions with proper cadence: IDE-driven refactors only,
tests after each, commits at sensible intervals. Used by injected
sessions via `control_session_id` for the `control_fp_count` column —
gives the detector a fair sample of how many divergence points it
emits on un-injected behaviour.

---

## Session enumeration (45 rows)

`target_step` is 1-indexed and follows the **user-step convention**
already used by `DivergenceExperiment.kt` and `DivergencePoint.stepIndex`.
For each session, the *target_step* is the step at which the bad
behaviour anchors (originating step for REWORK, terminal step for
ORDERING, the manual edit for IDE_REPLAY, the composite anchor for
HYGIENE). Every session does an IDE-driven warmup refactor at step 1
so target_step never collides with step 0.

`ctl` = `control_session_id` (the paired control); blank for control
rows themselves.

| ID  | Pattern              | Strength      | Kind     | Target step | File / method (location)                                      | Action recipe (after step-1 warmup + commit)                                                                                     | Ctl |
|-----|----------------------|---------------|----------|-------------|---------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------|-----|
| 001 | ManualExtractMethod  | loud          | IDE_REPLAY | 2          | LibrarySystem.processReturn lines 55-75                       | Cut 20-line fee-calc block; paste into new method `applyLateFee(loan)`; replace with call. Run tests. Commit.                    | 042 |
| 002 | ManualExtractMethod  | loud          | IDE_REPLAY | 2          | LibrarySystem.processReturn lines 80-92                       | Cut 12-line notification block; paste into new method `sendOverdueNotice`. Run tests. Commit.                                    | 042 |
| 003 | ManualExtractMethod  | loud          | IDE_REPLAY | 2          | ReportFormatter.formatReport status-line branches             | Cut 18 lines of repeated branch builder; paste into new `buildStatusLine`. Run tests. Commit.                                    | 043 |
| 004 | ManualExtractMethod  | borderline    | IDE_REPLAY | 2          | LateFeeCalculator.calculateFee lines 30-37                    | Cut 7 lines into `tieredSurcharge`. Run tests. Commit.                                                                           | 042 |
| 005 | ManualExtractMethod  | subthreshold  | IDE_REPLAY | 2          | LibrarySystem.findOverdueLoans                                | Cut 2 lines into a helper `filterOverdue`. Run tests. Commit. (Should NOT fire — below extract-utility threshold.)                | 042 |
| 006 | ManualRenameMethod   | loud          | IDE_REPLAY | 2          | LibrarySystem.fooBar (6 call-sites)                           | Project-wide find/replace `fooBar` → `processOverdueNotifications`. Run tests. Commit.                                            | 043 |
| 007 | ManualRenameMethod   | loud          | IDE_REPLAY | 2          | LateFeeCalculator.helper (multi-site)                         | Find/replace `helper(` → `applyDiscount(` across files. Run tests. Commit.                                                       | 043 |
| 008 | ManualRenameMethod   | borderline    | IDE_REPLAY | 2          | LateFeeCalculator.calc1 (2-3 sites)                           | Find/replace `calc1` → `feeForStandard`. Run tests. Commit.                                                                       | 042 |
| 009 | ManualRenameMethod   | subthreshold  | IDE_REPLAY | 2          | LibrarySystem private 1-call method                           | Rename a private one-call helper. (Should NOT fire — IDE-replay magnitude near zero.)                                            | 042 |
| 010 | ManualInlineMethod   | loud          | IDE_REPLAY | 2          | LibrarySystem.helperA (4 call-sites)                          | Substitute body at all 4 call-sites; delete `helperA`. Run tests. Commit.                                                         | 043 |
| 011 | ManualInlineMethod   | loud          | IDE_REPLAY | 2          | LibrarySystem.helperB (3 call-sites)                          | Substitute body at all 3 call-sites; delete `helperB`. Run tests. Commit.                                                         | 043 |
| 012 | ManualInlineMethod   | borderline    | IDE_REPLAY | 2          | LateFeeCalculator small wrapper                               | Inline a 2-site wrapper manually. Run tests. Commit.                                                                              | 042 |
| 013 | ManualInlineMethod   | subthreshold  | IDE_REPLAY | 2          | ReportFormatter single-call wrapper                           | Inline a 1-site, 2-line wrapper. (Should NOT fire.)                                                                              | 042 |
| 014 | ManualMoveMethod     | loud          | IDE_REPLAY | 2          | LibrarySystem.validateMember → MemberValidator                | Cut `validateMember`; paste into MemberValidator; update LibrarySystem call-sites to `validator.validate(...)`. Run tests. Commit.| 044 |
| 015 | ManualMoveMethod     | loud          | IDE_REPLAY | 2          | LibrarySystem.formatReport stub → ReportFormatter             | Move the formatting stub method to ReportFormatter. Run tests. Commit.                                                            | 044 |
| 016 | ManualMoveMethod     | borderline    | IDE_REPLAY | 2          | LateFeeCalculator → LibrarySystem (1 external call)          | Move a method with one external caller. Run tests. Commit.                                                                        | 042 |
| 017 | ManualMoveMethod     | subthreshold  | IDE_REPLAY | 2          | Private method with zero external callers                     | Move a private method to a sibling class — trivial scope. (Should NOT fire.)                                                     | 042 |
| 018 | ManualExtractVariable| loud          | IDE_REPLAY | 2          | LateFeeCalculator member-type ternary (4 uses)                | Pull `(member.type == PREMIUM ? 0.5 : ...)` into `final double discountFactor = ...;` above the block. Run tests. Commit.        | 043 |
| 019 | ManualExtractVariable| loud          | IDE_REPLAY | 2          | LibrarySystem.bulkLendBooks magic numbers                     | Extract `0.85` / `0.95` / `0.70` into named constants `BULK_TIER_*`. Run tests. Commit.                                          | 043 |
| 020 | ManualExtractVariable| borderline    | IDE_REPLAY | 2          | ReportFormatter (2 uses)                                       | Extract a duplicated 2-use sub-expression. Run tests. Commit.                                                                     | 042 |
| 021 | ManualExtractVariable| subthreshold  | IDE_REPLAY | 2          | Single-use trivial expression                                  | Extract `loan.dueDate.toString()` (used once). (Should NOT fire.)                                                                | 042 |
| 022 | SuboptimalOrdering   | loud          | ORDERING | 4           | LibrarySystem 3-step window                                   | Step 2: IDE-rename `fooBar`→`processOverdue`. Step 3: IDE-extract a block from inside it. Step 4: IDE-move the renamed method. Synth finds (extract → rename → move) is better. | 045 |
| 023 | SuboptimalOrdering   | loud          | ORDERING | 4           | LateFeeCalculator 3-step window                               | Rename `helper`→`applyDiscount`, then extract a sub-expression from inside it, then change its signature. Better order: extract → change-signature → rename. | 045 |
| 024 | SuboptimalOrdering   | borderline    | ORDERING | 3           | ReportFormatter 2-step window                                  | Extract method, then rename it. Better order: rename first (only 2 sites at that point), then extract. Modest gap.                | 045 |
| 025 | SuboptimalOrdering   | borderline    | ORDERING | 3           | LibrarySystem 2-step window                                    | Move a method, then rename it. Better order: rename, then move. Modest gap.                                                       | 045 |
| 026 | SuboptimalOrdering   | subthreshold  | ORDERING | 3           | LibrarySystem 2 independent refactors                          | Rename method A, then rename method B (independent). Order doesn't matter — synth gap < 3. (Should NOT fire.)                    | 042 |
| 027 | AddThenRevert        | loud          | REWORK   | 1           | LibrarySystem.processReturn                                    | Step 1: add a 15-line "discount calc" block. Step 2: IDE-rename something unrelated. Step 3: realise the addition was wrong; delete it. | 045 |
| 028 | AddThenRevert        | loud          | REWORK   | 1           | LateFeeCalculator.calculateFee                                 | Step 1: add a 12-line helper method. Step 2: use it from one site. Step 3: delete the helper and the call. (Effective 13-line rework.) | 045 |
| 029 | AddThenRevert        | borderline    | REWORK   | 1           | ReportFormatter.formatReport                                   | Step 1: add 6 lines of branch logic. Step 2: refactor elsewhere. Step 3: delete the 6 added lines.                                | 043 |
| 030 | AddThenRevert        | borderline    | REWORK   | 1           | LibrarySystem.bulkLendBooks                                    | Step 1: add a 5-line guard clause. Step 2: realise it duplicates existing logic; delete it.                                       | 043 |
| 031 | AddThenRevert        | subthreshold  | REWORK   | 1           | Any method                                                     | Step 1: add 2 lines. Step 2: delete them. (At MIN_REWORK_LINES floor.) Surfaces under default config; loosen min and it falls off.| 042 |
| 032 | SkippedTests         | loud          | HYGIENE  | 3           | LibrarySystem chain                                            | IDE-extract method (step 1, no tests). IDE-rename (step 2, no tests). IDE-move (step 3, no tests). Step 4: run all tests + commit.| 043 |
| 033 | SkippedTests         | loud          | HYGIENE  | 4           | LateFeeCalculator + ReportFormatter chain                      | 4 IDE refactors back-to-back, no test runs between. Step 5: run tests + commit.                                                  | 043 |
| 034 | SkippedTests         | borderline    | HYGIENE  | 2           | LibrarySystem chain                                            | 2 IDE refactors back-to-back, no tests between. Step 3: run tests + commit.                                                       | 042 |
| 035 | SkippedTests         | borderline    | HYGIENE  | 2           | ReportFormatter chain                                          | 2 IDE refactors, no tests. Step 3: tests + commit.                                                                                | 042 |
| 036 | SkippedTests         | subthreshold  | HYGIENE  | 1           | Any                                                            | Single refactor (step 1), don't run tests, commit. (Doesn't form a composite ≥ 2; should NOT fire.)                              | 042 |
| 037 | NoCommitStretch      | loud          | HYGIENE  | 7           | Across all files                                               | 7 green refactor checkpoints (IDE refactor + tests pass after each), single commit at step 8.                                     | 045 |
| 038 | NoCommitStretch      | loud          | HYGIENE  | 8           | Across all files                                               | 8 green refactor checkpoints, single commit at step 9.                                                                            | 045 |
| 039 | NoCommitStretch      | borderline    | HYGIENE  | 6           | Across all files                                               | Exactly 6 green refactor checkpoints, single commit at step 7. (At MIN_COMMIT_GAP floor.)                                         | 045 |
| 040 | NoCommitStretch      | borderline    | HYGIENE  | 6           | Across all files                                               | Same shape as 039 with different refactor sequence.                                                                                | 045 |
| 041 | NoCommitStretch      | subthreshold  | HYGIENE  | 5           | Across all files                                               | 5 green refactor checkpoints, single commit at step 6. (Below floor; should NOT fire.)                                            | 042 |
| 042 | Control              | n/a           | n/a      | -1          | LibrarySystem small                                            | 3 IDE refactors, tests after each, commit after each. Clean process. Used as the "small/simple" control.                          | —   |
| 043 | Control              | n/a           | n/a      | -1          | LibrarySystem + ReportFormatter mix                            | 5 IDE refactors, tests after each, commit per logical group (3 commits). Used as the "medium" control.                            | —   |
| 044 | Control              | n/a           | n/a      | -1          | LibrarySystem + MemberValidator                                | 4 IDE refactors + one typo fix in a comment, tests after each, commit per refactor. Used by ManualMoveMethod loud rows.           | —   |
| 045 | Control              | n/a           | n/a      | -1          | Across all files                                               | 8 IDE refactors with tests after each and commits every ~2 refactors. Used as the "long" control for ORDERING and COMMIT_GAP.     | —   |

### Manifest CSV produced from the table

The 41 injection rows go into
`fixtures/library-fixture/sessions/manifest.csv` with the schema
`DivergenceExperiment` already expects:

```
session_id,pattern,strength,target_step,expected_kind,fixture_path,control_session_id
001,ManualExtractMethod,loud,2,IDE_REPLAY,sessions/001/,042
002,ManualExtractMethod,loud,2,IDE_REPLAY,sessions/002/,042
...
041,NoCommitStretch,subthreshold,5,HYGIENE,sessions/041/,042
```

Controls 042-045 do not appear as manifest rows; they live in the
corpus dir as `phaseA.json` dumps and are referenced by
`control_session_id` only.

---

## Recording mechanics

For each session:

1. `git worktree add /tmp/recording-<id> library-fixture-v1` — fresh
   tree at the baseline tag.
2. Open `/tmp/recording-<id>` in IntelliJ; plugin starts in record
   mode.
3. Perform the **step-1 warmup**: one IDE-driven refactor unrelated
   to the target (e.g. rename a private local var). Run tests.
   Commit. This anchors `target_step` semantics: the bad step is at
   index 2+ for IDE_REPLAY / REWORK, etc.
4. Perform the per-session script from the table above.
5. End the recording (plugin button); plugin writes the session
   dir under `.refactoring-traces/<uuid>/`.
6. `mv .refactoring-traces/<uuid> fixtures/library-fixture/sessions/<id>/`.
7. Add the row to `manifest.csv`.
8. `git worktree remove /tmp/recording-<id>`.

A scripted helper would be nice but not required: each session is
short enough (~5 min) that doing the shell commands by hand is fine.

### Recording cost estimate

45 × ~5 min = ~3.75 hours of focused recording. Plus ~1 hour of
manifest writing + worktree shuffling. Realistic in one day, or two
half-days.

---

## Bulk Phase A

After recording, one shell loop runs `:phaseA` over every session:

```bash
for s in fixtures/library-fixture/sessions/*/; do
  id=$(basename "$s")
  ./gradlew :analysis:phaseA \
      --args="$s fixtures/library-fixture/corpus/$id.json" -q
done
```

45 sessions × ~30-60s = ~30-45 min wall-clock. Could be parallelised
with `xargs -P 4` if impatient, but Phase A already uses multiple
threads internally so the speedup is modest.

Output: `fixtures/library-fixture/corpus/*.json` — the input
directory for `:sensitivity`, `:ablation`, `:divergence`.

---

## Critical files

**Authored anew**:

- `fixtures/library-fixture/build.gradle.kts` + `settings.gradle.kts`
- `fixtures/library-fixture/src/main/java/org/library/*.java` — 8
  classes per the table above.
- `fixtures/library-fixture/src/test/java/org/library/*Test.java` —
  3 test classes.
- `fixtures/library-fixture/sessions/PROTOCOL.md` — per-session
  scripts (verbatim copy of the table above plus the recording
  mechanics).
- `fixtures/library-fixture/sessions/manifest.csv` — 41 injection
  rows (populated as recording proceeds).
- `fixtures/library-fixture/sessions/<id>/` — 45 captured session
  directories.
- `fixtures/library-fixture/corpus/<id>.json` — 45 Phase-A dumps.

**No edits** to `analysis/` code are required. Phase 3 is data
collection, not code.

---

## Verification

1. `cd fixtures/library-fixture && ./gradlew test` — all green at
   `library-fixture-v1` baseline.
2. Each recorded session's `events.jsonl` non-empty, plugin
   `session.json` present.
3. `:phaseA` succeeds on all 45 sessions; 45 `phaseA.json` files
   in `corpus/`.
4. Sanity-spot one injection: e.g. session 001 (ManualExtractMethod
   loud). Run:
   ```
   ./gradlew :analysis:divergence --args="
       --corpus fixtures/library-fixture/corpus
       --manifest fixtures/library-fixture/sessions/manifest.csv
       --output /tmp/div.csv" -q
   grep '^001,' /tmp/div.csv
   ```
   Expect `tier1_hit=true` and `tier2_hit=true`.
5. Sanity-spot one subthreshold: e.g. session 005 (ManualExtractMethod
   subthreshold). Expect `tier1_hit` and `tier2_hit` both `false` —
   if they fire, the floor calibration on `MIN_PROCESS_DELTA` is
   wrong for this fixture.
6. Sanity-spot a control: session 042. Expect a low
   `control_fp_count` when referenced by other rows.
7. Full sweep:
   `:sensitivity` + `:ablation` complete in < 30 s end-to-end across
   45 fixtures.
8. Headline numbers plausible per kind:
   - IDE_REPLAY loud: Tier 1 ≈ 100 %, Tier 2 ≈ 100 %.
   - IDE_REPLAY borderline: Tier 1 ≈ 100 %, Tier 2 drops.
   - IDE_REPLAY subthreshold: Tier 2 ≈ 0 % (FP probe).
   - HYGIENE COMMIT_GAP loud: Tier 2 ≈ 100 % by construction (no
     floor); document as a transparency note, not a calibration win.
   - REWORK loud: Tier 2 ≈ 100 % (weight-independent magnitude).
   - Control sessions: `control_fp_count` low (ideally 0 or 1).
