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
