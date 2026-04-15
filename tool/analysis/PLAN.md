# Analysis Module — Phase 1 Setup

## Context

The monorepo contains `ide-plugin/` (emits JSONL traces with a baseline source snapshot) and `shared/` (event/session models). This `analysis/` module is a sibling Kotlin/JVM project that will become the offline analysis engine — trajectory scoring, RefactoringMiner integration, web server entrypoint, etc. Phase 1 is just the foundation.

Phase 1 goal: point the tool at an ide-plugin trace folder and produce a **shadow git repo** where each state-bearing event maps to a commit SHA. Using real git unlocks worktrees, diff, blame, etc. for later analysis stages without reimplementing them.

## Workflow

- All Phase 1 work on branch `analysis/phase-1-setup`.
- Step-by-step: finish a step, hand back to the user to verify, then move on. Keep implementation fluid — re-plan between steps when new information appears.
- Minimal tests. One happy-path sanity check per component; thorough fixtures arrive once shapes stabilise.
- Errors are fatal. Any parse error, replay anomaly, or git failure aborts the run.

## Design decisions (sticky constraints)

- **CLI framework:** Clikt. Single command for now — a future web server will reuse the pipeline underneath.
- **Package namespace:** `com.github.ethanhosier.analysis`.
- **File contents:** UTF-8 text only (matches `FileSnapshot.contents: String?`).
- **Git backend:** shell out to the system `git` binary via `ProcessBuilder`. Preferred over JGit because JGit lacks `git-worktree` support, which we want later. Assumes `git` is on `PATH`.
- **Phase 1 ordering rule:** stable sort by `timestamp` ascending. Placeholder — later the normalizer will also group automated refactoring events with nearby edit bursts, but that's deferred.

## Input layout (produced by ide-plugin)

```
.refactoring-traces/<sessionId>/
├── events.jsonl        # one TraceEvent per line
├── session.json        # pretty-printed Session with SessionMetadata
└── initial-src/        # baseline source tree captured at SESSION_STARTED
```

## Internal shape

Pipeline stages live in separate packages under `com.github.ethanhosier.analysis`:

- `cli/` — thin Clikt entrypoint.
- `ingest/` — JSONL + session.json loading into `shared` model types.
- `normalize/` — canonical ordering of the event stream.
- `reconstruct/` — shadow git repo builder (seed from `initial-src/`, apply each state-bearing event as a commit). Includes a small `git` subprocess wrapper.
- `model/` — analysis-owned domain types (ordered trace, reconstruction result, event-to-commit map, etc.) so the pipeline doesn't leak raw shared types everywhere downstream.

File and class names are intentionally unspecified here — let them emerge as we build.

## Shadow repo approach (high level)

Seed a fresh repo under `<sessionFolder>/shadow-repo/` from `initial-src/`, commit it as the baseline, then walk the ordered event stream. For each event that carries file changes, apply the changes to the working tree and commit. Events that don't change the tree (empty `changedFiles`, or edit bursts whose final state matches HEAD) map to the previous commit SHA — the mapping is preserved but no new commit is created. Persist the `eventId → commitSha` map alongside the repo.

Path normalization: trace paths are absolute; strip `SessionMetadata.projectPath` to get repo-relative paths. Paths escaping the project root are fatal.

## Step-by-step execution

1. **Branch + skeleton.** New branch, `:analysis` wired into the Gradle build, minimal `build.gradle.kts`, `analysis/PLAN.md` committed. Verify `./gradlew :analysis:build` succeeds with no source.
2. **Ingest.** JSONL + session.json loader. Sanity-load a real trace.
3. **Normalize.** Timestamp ordering stage. Eyeball a real trace output.
4. **Git wrapper.** Small `ProcessBuilder` shim around `git`. Single test hitting `version`, `init`, `commit`.
5. **Shadow repo builder.** Core reconstruction logic. Verify against a real session folder; inspect with `git log`.
6. **CLI wire-up.** Clikt command + application plugin + mainClass. Verify end-to-end via `./gradlew :analysis:run`.

Steps can re-open earlier ones as design pressure appears — the above is a direction, not a contract.

## Out of scope (explicitly deferred)

- Normalizer grouping of automated refactorings with nearby edit bursts.
- RefactoringMiner / CK / PMD integration.
- Anchor/step extraction, metrics, scoring, suggestions.
- Web server entrypoint.
- Binary file support.
- Worktree-based multi-state diff tooling (unlocked by this design but not built yet).

---

# Analysis Module — Phase 3: Per-Checkpoint Metrics

## Context

Phase 1 + 2 produce a shadow git repo under `<sessionFolder>/shadow-repo/` and an `event-commits.json` mapping every event to a commit SHA (many events collapse to the previous SHA). With reconstruction working, we now need to characterise the *state* of the project at each checkpoint so downstream trajectory analysis has signals to work with.

A "checkpoint" = a unique commit SHA in the shadow repo. For each checkpoint, compute:

1. **CK metrics** — class/method-level OO metrics (LCOM, CBO, WMC, RFC, NOM, LOC, …) via the `com.github.mauricioaniche:ck` library API.
2. **PMD violations** — rule violations via the `net.sourceforge.pmd:pmd-java` library API (`PmdAnalysis`).
3. **Build status** — does `./gradlew build` succeed? (Gradle-only; the project under analysis is assumed to be a Gradle Java project.)
4. **Test pass rate** — `./gradlew test` exit + JUnit XML report parsing for total/passed/failed/skipped.

Per-SHA dedup matters because long sessions produce hundreds of events but far fewer unique commits. Build+test is expensive, so unique SHAs run in parallel via `git worktree`.

## Workflow

- All Phase 3 work on branch `analysis/checkpoint-metrics`.
- Step-by-step, hand back to user between steps.
- **Errors are fatal** (Phase 1 rule carries over): any failure in any tool, for any SHA, aborts the whole run. No per-section status, no partial results — a checkpoint either has a full metrics file or the run halts and the user fixes the underlying issue.

## Design decisions (sticky constraints)

- **CK + PMD as Maven libraries** (not shelled out). Both publish to Maven Central with a programmatic API — no JAR vendoring, no version drift.
- **Build/test** shell out to `./gradlew --no-daemon` (no daemon since concurrent worktrees would contend).
- **Granularity:** per unique commit SHA, not per event. Many events share a SHA; recompute would be wasted.
- **Concurrency:** `Executors.newFixedThreadPool(parallelism)` + `WorktreePool` of equal size. Default parallelism = `availableProcessors() / 2`.
- **Build system supported:** Gradle only for now. If `./gradlew` is missing in the worktree, build/test sections record `status: "skipped"` rather than crashing.
- **Ruleset (PMD):** start with `category/java/bestpractices.xml,category/java/errorprone.xml,category/java/design.xml`. Configurable later.
- **Idempotency:** existing `<sha>.json` files are reused on re-run — incremental runs are cheap.

## Output layout (added by this phase)

```
<sessionFolder>/
├── shadow-repo/                    (Phase 2)
├── event-commits.json              (Phase 2)
├── checkpoint-metrics/
│   └── <sha>.json                  one per unique SHA
└── shadow-worktrees/               transient — created/cleaned per run
    ├── w0/
    └── w1/
```

`event-commits.json` already maps event-id → SHA, so no separate index is needed — downstream code joins on SHA.

Per-SHA file shape (sketch):

```json
{
  "sha": "abc123…",
  "ck":     { "perClass": [...], "summary": {...} },
  "pmd":    { "violations": [...], "summary": {...} },
  "build":  { "success": true, "durationMs": 12345, "stderrTail": "…" },
  "tests":  { "total": 50, "passed": 48, "failed": 2, "skipped": 0, "failures": [...] }
}
```

Note: `build.success: false` and `tests.failed > 0` are **valid outcomes**, not errors — they're signals the downstream analysis cares about. Errors here mean "the tool itself crashed / timed out / failed to run"; those abort the run.

## Internal shape

New package `com.github.ethanhosier.analysis.metrics`:

- `MetricsRunner.kt` — orchestrator: pulls unique SHAs from `EventCommitMap`, schedules them across the worktree pool, writes per-SHA JSON.
- `WorktreePool.kt` — fixed-size pool of `git worktree` directories. Workers borrow → checkout → run → return.
- `ck/CkRunner.kt` + `ck/CkResult.kt` — wraps CK's `CKNotifier` callback; produces typed result.
- `pmd/PmdRunner.kt` + `pmd/PmdResult.kt` — wraps `PmdAnalysis`; collects from `Report`.
- `build/GradleBuildRunner.kt` + `build/BuildResult.kt` — `./gradlew --no-daemon --console=plain build -x test` with timeout.
- `tests/GradleTestRunner.kt` + `tests/TestResult.kt` — `./gradlew --no-daemon --console=plain test` + parse `build/test-results/test/*.xml`.
- `tests/JUnitXmlParser.kt` — minimal JUnit-XML parsing using `javax.xml.parsers`.
- `model/CheckpointMetrics.kt` — top-level serializable record combining all four sections.

Files modified:

- `analysis/build.gradle.kts` — add `com.github.mauricioaniche:ck` and `net.sourceforge.pmd:pmd-java` deps.
- `reconstruct/GitRunner.kt` — add `worktreeAdd`, `worktreeRemove`, `worktreePrune`, `checkout`.
- `cli/Main.kt` — after `ShadowRepoBuilder().build(...)`, call `MetricsRunner(parallelism).run(result, folder)`. Add `--parallelism` flag.

## Concurrency model

Each task: borrow worktree → `git checkout <sha>` → run CK + PMD + build + tests sequentially → write `<sha>.json` → return worktree. Tasks themselves run in parallel up to `parallelism`. Each subprocess (build, test) has a hard timeout (default 5 min build, 10 min test). Timeout or crash → the task fails, the orchestrator propagates the exception and the run aborts.

## Open items to verify during implementation

- **initial-src completeness for Gradle:** confirm the ide-plugin's `initial-src/` capture includes `gradlew`, `gradle/wrapper/`, `build.gradle*`, `settings.gradle*`. If filtered out, build/test sections must skip cleanly rather than crash.
- **CK version compatibility** with JDK 21 toolchain — pin to a known-good version.
- **PMD ruleset choice** — start with the three default categories; expose `--pmd-ruleset` later if needed.

## Step-by-step execution

1. **Skeleton + deps.** Add CK + PMD to `build.gradle.kts`, create `metrics/` package skeleton, define `CheckpointMetrics` schema. Verify `./gradlew :analysis:build`.
2. **CK runner.** Implement, smoke-test against analysis module's own source tree.
3. **PMD runner.** Same shape.
4. **Gradle build runner.** Shell out, capture exit + stderr tail.
5. **Gradle test runner + JUnit XML parser.**
6. **GitRunner worktree extensions + WorktreePool.**
7. **MetricsRunner orchestrator.** Sequential first (parallelism=1), then parallel.
8. **CLI wire-up.** `--parallelism` flag, integrate after reconstruct, print summary line.

## Verification

```
./gradlew :analysis:run --args="<sessionFolder> --parallelism=4" -q
```

Expected:
- `<sessionFolder>/checkpoint-metrics/<sha>.json` exists for every unique SHA in `event-commits.json`.
- All four sections present with sensible statuses.
- `shadow-worktrees/` removed after run.
- Re-run is a no-op on already-computed SHAs.
- Spot-check: a deliberately broken-test session produces `tests.failed > 0` and `build.success` matching expectation.
