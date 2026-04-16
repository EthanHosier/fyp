# Manual Refactoring Detection (RefactoringMiner stage)

## Context

Today the analysis pipeline segments the event stream into checkpoints, reconstructs the project at each checkpoint in a shadow git repo, and computes per-checkpoint static metrics (CK, PMD, build, tests). It does **not** yet identify which manual edit runs the user *could have done* with an IDE-driven refactoring instead.

We add a new stage that runs [RefactoringMiner](https://github.com/tsantalis/RefactoringMiner) across pairs of shadow-repo commits to detect refactoring patterns in the manual-edit intervals between automated IDE refactorings.

**Domain terms:**
- **Anchor checkpoint** — a checkpoint reached via an automated IDE refactoring (first event at that SHA is `REFACTORING_FINISHED`), or the baseline commit at session start.
- **Manual checkpoint** — any other checkpoint.
- **Segment** — the contiguous run `[A_L, m_1, …, m_k, A_R]` between two consecutive anchors. Anchors are included as segment endpoints; the middle is manual-only by construction.

## Approach

Two-level detection per segment:

1. **Segment-level pass.** One RM run over `(A_L → A_R)`, the full manual-edit interval between anchors. Captures the net set of refactorings the user made between two automated refactorings.
2. **Inner sliding window.** Localise each manual refactoring to the tightest checkpoint window `[L*, R*]` inside the segment where RM still detects it. Enables pinpointing *when* during the segment the refactoring was made.

The transition `m_k → A_R` is never analysed: the edits crossing that boundary include the automated refactoring itself, which is not what we're measuring. The loop invariant `R is a manual checkpoint` is enforced with an explicit `check()` so any mis-segmentation fails loudly.

### Algorithm per segment

```
segment = [A_L, m_1, …, m_k, A_R]      // both anchors included; interior is manual
L = A_L
R = m_1
while R != A_R:                         // never analyse up to a refactoring checkpoint
    check(R is MANUAL)
    detections = runRM(commit(L), commit(R))
    if detections.isNotEmpty():
        // shrink L forward while detection set stays equal
        tightestL = L
        probe = L + 1
        while probe < R:
            next = runRM(commit(probe), commit(R))
            if canonicalSet(next) == canonicalSet(detections):
                tightestL = probe
                probe += 1
            else:
                break
        emit Finding(tightestL, R, detections, scope = INNER)
        L = R                  // new baseline = state at the locked R*
        R = next(R)             // R* + 1
    else:
        R = next(R)
```

The outermost `(A_L → A_R)` run is also emitted once as `scope = SEGMENT` so callers see both the coarse and the fine view.

### Canonical equality (for "same refactorings")

A detection set is canonicalised to `Set<Key>` where

```
Key = { refactoringType.displayName, sorted(leftSideLocations), sorted(rightSideLocations) }
```

— enough signal to distinguish "Extract Method foo from Bar" from "Extract Method baz from Qux", but order-independent (so RM's output order doesn't leak into equality) and stable across reruns.

### Running RefactoringMiner

RM's `GitHistoryRefactoringMinerImpl.detectAtDirectories(Path prev, Path next, RefactoringHandler)` diffs two on-disk trees directly — no JGit required in our code. We reuse the existing `WorktreePool` to borrow two worktrees, check each out to a different SHA via the existing `GitRunner`, and hand both paths to RM.

### Parallelism

Segments are independent — process them in parallel across a thread pool sized like `MetricsRunner` (default `availableProcessors / 2`). Inside a segment the sliding window is sequential because each step's decision depends on the prior outcome. Each RM call borrows two worktrees from `WorktreePool`; pool capacity stays at `parallelism * 2` or similar — confirmed during implementation.

### No in-memory cache needed

Every `(L, R)` pair the algorithm evaluates is unique — shrink moves L forward, grow moves R forward, and restart jumps both. An optional on-disk cache keyed `<fromSha>_<toSha>.json` (mirroring `checkpoint-metrics/<sha>.json`) could accelerate incremental re-runs but is deferred.

## IDE relevance

Every detection records `ideRelevant: Boolean`, decided by a whitelist of refactorings IntelliJ offers as a built-in action. First-pass list:

- Extract Method / Inline Method
- Extract Class / Extract Interface / Extract Superclass
- Rename Class / Rename Method / Rename Field / Rename Variable / Rename Parameter / Rename Package
- Move Method / Move Class / Move Field / Move Package
- Pull Up Method / Push Down Method / Pull Up Field / Push Down Field
- Change Signature (method parameter add/remove/reorder, return type change)
- Extract Variable / Inline Variable

Other RM types (e.g. `Change Return Type`, `Modify Method Annotation`) are recorded with `ideRelevant = false`. The allowlist is a single `Set<RefactoringType>` in `IdeRelevantRefactorings.kt` — easy to tune later.

## Critical files

**New:**
- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/miner/RefactoringMinerRunner.kt` — orchestrator: segment partition + outer + inner sliding window + parallelism.
- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/miner/IdeRelevantRefactorings.kt` — allowlist of IntelliJ-supported `RefactoringType`s.
- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/miner/model/RefactoringFinding.kt` — `{segmentIndex, scope, fromSha, toSha, fromEventId, toEventId, refactorings}`.
- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/miner/model/DetectedRefactoring.kt` — `{type, description, leftSideLocations, rightSideLocations, ideRelevant}` — normalised so RM's types don't leak into serialized output.

**Modified:**
- `gradle/libs.versions.toml` — add `org.refactoringminer:refactoring-miner` version.
- `analysis/build.gradle.kts` — depend on the new library.
- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/pipeline/AnalysisPipeline.kt` — invoke `RefactoringMinerRunner` after `MetricsRunner`, pass output into the report builder.
- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/metrics/model/AnalysisReport.kt` — add top-level `manualRefactorings: List<RefactoringFinding>`.
- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/reconstruct/WorktreePool.kt` — confirm / extend to borrow two worktrees at distinct SHAs concurrently (likely already fine; verify during implementation).
- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/cli/Main.kt` — print a short summary line for the new stage (`manual refactorings: 4 findings (3 ide-relevant)` style), matching existing summary output.

## Output shape

```json
"manualRefactorings": [
  {
    "segmentIndex": 0,
    "scope": "SEGMENT",
    "fromSha": "a1b2…",  "toSha": "c3d4…",
    "fromEventId": "…",  "toEventId": "…",
    "refactorings": [
      {
        "type": "Extract Method",
        "description": "Extract Method foo in class Bar from method baz",
        "leftSideLocations":  ["src/main/java/Bar.java:12-20"],
        "rightSideLocations": ["src/main/java/Bar.java:22-30"],
        "ideRelevant": true
      }
    ]
  },
  {
    "segmentIndex": 0,
    "scope": "INNER",
    "fromSha": "e5f6…",  "toSha": "c3d4…",
    ...
  }
]
```

## Reused utilities

- `WorktreePool` + `GitRunner` — the existing worktree machinery handles the checkouts. No new git code.
- `MetricsRunner.Summary.checkpoints` — already ordered by first-appearance; we reuse this as the canonical checkpoint sequence.
- `ReconstructionResult.eventCommits` — maps each event ID to its SHA, so we can classify a checkpoint as anchor vs manual by looking up its first event's type.

## Verification

1. **Minimal synthetic session** — construct a fake session with two anchors and three manual checkpoints where the middle manual checkpoint contains an obvious Extract Method. Assert RM surfaces it, and that the inner sliding window locks down to exactly that one checkpoint.
2. **CLI regression** — run against the canned session we've been using throughout the project; existing metrics output must be unchanged; `manualRefactorings` should populate.
3. **End-to-end via server** — record a fresh IntelliJ session with a mix of automated and manual refactorings, let the plugin upload to the local server, inspect `analysis-report.json`.

## Non-goals (deferred)

- Disk cache for `(fromSha, toSha) → detections` across reruns.
- Non-Java language support (RM's Java path is the only well-tested one; target codebases are Java).
- Reporting refactorings that RM finds at the outer segment scope but the inner sliding window misses due to strict equality.
- UI surfacing in the ide-plugin — for now findings live in `analysis-report.json`; a results panel is a separate follow-up.
- Suggesting which IntelliJ action key the user *should have* used — `ideRelevant: Boolean` is the current granularity.
