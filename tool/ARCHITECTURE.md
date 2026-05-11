# Architecture

A dev-level tour of the codebase. Skimmable section-by-section: every
module gets a feature list and, where the mechanism is non-obvious, a
"how it works" subsection with concrete class names and file paths.

## Contents

1. [Top-level architecture](#1-top-level-architecture)
2. [`ide-plugin/`](#2-ide-plugin)
3. [`analysis/`](#3-analysis)
4. [`dashboard/`](#4-dashboard)
5. [`refactoring-bundle/`](#5-refactoring-bundle)
6. [`shared/`](#6-shared)
7. [`metrics-core/` and `metrics-lambda/`](#7-metrics-core-and-metrics-lambda)
8. [End-to-end walkthrough](#8-end-to-end-walkthrough)
9. [Build & run commands](#9-build--run-commands)

---

## 1. Top-level architecture

The tool is a four-module Gradle multi-build plus a standalone React
app. Three of the modules sit inside the JVM build
(`settings.gradle.kts` includes `:ide-plugin`, `:shared`, `:analysis`,
`:refactoring-bundle`); the `dashboard/` directory is a Vite-managed
TypeScript app that consumes a JSON report.

```
   ┌──────────────┐   session.json   ┌────────────────┐
   │  ide-plugin  │ ───────────────► │    analysis    │
   │ (IntelliJ)   │   events.jsonl   │   (pipeline)   │
   └──────────────┘   initial-src/   └────────┬───────┘
          │            via HTTP POST          │
          │             /analyze              │ in-process
          │           (multipart)             │ via OSGi
          │                                   ▼
          │                          ┌───────────────────┐
          │                          │ refactoring-bundle│
          │                          │   (Eclipse JDT)   │
          │                          └───────────────────┘
          │                                   │
          │  HTTP response                    │ shadow git
          │  (AnalysisReport JSON)            │ commits + refs
          │                                   ▼
          ▼                          ┌───────────────────┐
   analysis-report.json              │  shadow-repo/     │
          │                          │  (per-session)    │
          │  injected as             └───────────────────┘
          │  window.__REPORT__
          ▼
   ┌──────────────┐
   │  dashboard   │
   │  (React/TS)  │
   └──────────────┘
```

### Modules

- **`ide-plugin/`** — IntelliJ plugin. Captures editor / file / build /
  test / refactoring / git events into `events.jsonl` during a recorded
  session, snapshots the project sources, and POSTs the bundle to the
  analysis server at session end.

- **`analysis/`** — Kotlin pipeline. Normalises the event stream,
  replays it into a shadow git repo, mines refactorings, validates and
  reorder-synthesises alternative trajectories, computes per-checkpoint
  metrics, and emits an `AnalysisReport`. Ships with a CLI
  (`cli/Main.kt`) and an HTTP server (`server/Main.kt`).

- **`refactoring-bundle/`** — Headless Eclipse JDT refactorings,
  loaded into the analysis process via an embedded Equinox OSGi
  framework. Drives every IDE-style refactoring used during alt-
  trajectory synthesis.

- **`dashboard/`** — React/TypeScript visualisation of the
  `AnalysisReport`. Trajectory chart, detail panel, metric rail,
  session-takeaways advice, refactoring filmstrip.

- **`shared/`** — `@Serializable` types crossing the plugin/analysis
  wire: `EventType`, `TraceEvent`, `FileSnapshot`, `Session`,
  `SessionMetadata`.

- **`metrics-core/` and `metrics-lambda/`** — historical/in-progress
  remote-metrics work (tasks #61–#63). Not currently in the build —
  see [§7](#7-metrics-core-and-metrics-lambda).

### Cross-cutting concerns

- **Schema sharing.** `@Serializable` Kotlin data classes in `shared/`
  (and in `analysis/.../metrics/model/`) are the single source of
  truth. The dashboard's TypeScript types are codegenned from them
  via the `:analysis:generateDashboardTypes` Gradle task into
  `dashboard/src/generated/report-types.ts`. Any backend schema
  change must regen frontend types before `npm run typecheck` will
  pass.
- **Plugin/analysis boundary.** The plugin has no Gradle dependency on
  the analysis module; it writes a `Session` (from `shared/`) plus
  raw `events.jsonl` and uploads them. The server unpacks the upload
  into a temp dir, runs the pipeline, and returns the report inline
  in the HTTP response.

---

## 2. `ide-plugin/`

IntelliJ plugin recording a developer session. Java/Kotlin, IntelliJ
Platform SDK.

### Features

- Session lifecycle (Start / End actions in
  `actions/StartSessionAction.kt`, `actions/EndSessionAction.kt`).
- Event capture across editor, file structure, refactorings,
  builds, tests, and VCS.
- Initial project snapshot (`initial-src/` zip).
- Live append to `events.jsonl`; final flush to `session.json`.
- HTTP upload to the analysis server and injection of the resulting
  report into the dashboard.

### Listener architecture

Each listener subscribes to an IntelliJ message bus topic and emits a
typed `TraceEvent` via the project-level `SessionService`. All under
`ide-plugin/src/main/kotlin/com/github/ethanhosier/ideplugin/listeners/`:

- **`EditorEventListener.kt`** — feeds keystrokes / focus / save into
  `EditBurstTracker`. Produces `FILE_FOCUSED`, `FILE_UNFOCUSED`,
  `FILE_SAVED`, and (indirectly) `EDIT_BURST`.
- **`FileEditorListener.kt`** — file open/close. Produces
  `FILE_OPENED`, `FILE_CLOSED`.
- **`VfsListener.kt`** — file create / delete / rename / move / external
  modification. Produces `FILE_CREATED`, `FILE_DELETED`,
  `FILE_RENAMED`, `FILE_MOVED`, `FILE_MODIFIED_EXTERNAL`.
- **`FileSaveListener.kt`** — explicit save events.
- **`RefactoringListener.kt`** — IDE-driven refactoring lifecycle.
  Produces `REFACTORING_STARTED` / `REFACTORING_FINISHED` with typed
  spec info in the payload.
- **`RefactoringCommandListener.kt`** — legacy multi-refactoring
  commands (compound IDE actions).
- **`RefactoringTemplateListener.kt`** — IntelliJ live templates that
  result in refactorings.
- **`BuildListener.kt`** — Gradle / Maven build start/finish via the
  IntelliJ build infrastructure. Produces `BUILD_STARTED`,
  `BUILD_FINISHED`.
- **`TestRunListener.kt`** — test runs. Produces `TEST_RUN_STARTED`,
  `TEST_RUN_FINISHED` with pass/fail counts in the payload.
- **`ProjectCloseListener.kt`** — auto-flushes a still-active session
  if the project closes without an explicit End action.

`GitCommitWatcher` (under `services/`, not `listeners/`) is a thread
rather than a bus subscriber — see "Git commit watching" below.

### Edit burst tracking

`services/EditBurstTracker.kt` debounces keystroke-level
`DocumentEvent`s into one `EDIT_BURST` per file per quiet window. Each
file has its own accumulator; the scheduled flush task captures the
in-memory document text (not the on-disk file) so the recorded snapshot
matches what the user actually typed, regardless of auto-save or
on-save formatters.

Payload: char-level adds/removes, the post-edit file contents, and
`touchedMembers` resolved via `refactoring/TouchedMemberResolver.kt`
(PSI walk identifying the enclosing class/method of every changed
range).

### Git commit watching

`services/GitCommitWatcher.kt` is a polling thread, not a reflog
watcher. It runs `git log --since=<sessionStart>` once a second
against the user's working repo, deduplicates against an internal
`emitted` SHA set, and emits one `GIT_COMMIT` event per new commit.
Reflog was tried earlier and abandoned: fresh `git init` repos may
disable it, and the JDK macOS `WatchService` is itself a polling shim
with ~10 s latency.

Git-root resolution covers two layouts: `basePath` at-or-below the git
toplevel, or one level above (workspaces where `.git` lives in an
immediate child). Multi-repo projects are out of scope — the first
immediate child with a `.git` directory wins.

The payload carries `sha`, `parentSha`, `authorTimestamp`, `message`,
and `action` (commit / amend); the analysis pipeline prefers
`authorTimestamp` over the event timestamp when assembling
`AnalysisReport.userGitCommits`.

### Session storage layout

`services/SessionService.kt` owns metadata + the in-memory event list
(guarded by a single lock). On `startSession(name)`:

1. Generates a UUID session id, captures IDE version + plugin
   version + current git branch + current commit.
2. Snapshots the project sources to `initial-src/` via
   `util/SnapshotFilter.kt` (skips out/, build/, .git/, etc.).
3. Writes a `SESSION_STARTED` event.

During the session, every emitted `TraceEvent` is both appended to
the in-memory list and written to `events.jsonl` (one JSON object per
line) via `services/StorageService.kt`.

On `endSession()`, `services/StorageService.kt` flushes pending edit
bursts, writes `SESSION_ENDED`, and serialises the full `Session`
(metadata + events) to `session.json`. Default disk location:
`~/.refactoring-tracer/sessions/<session-id>/`.

### Plugin → analysis upload

`services/AnalysisClient.kt` POSTs to
`$REFACTORING_TRACER_SERVER_URL/analyze` (default
`http://localhost:8080/analyze`) as multipart:

- `session.json` (text part)
- `initial-src.zip` (binary part, file permissions preserved)
- Optional `executable-paths.json` listing test executables.

The server response body is the `AnalysisReport` JSON. The plugin
writes it back to the session folder as `analysis-report.json` and
opens it in the dashboard tool window
(`toolWindow/DashboardToolWindow.kt`), which assigns
`window.__REPORT__` and dispatches the `refdash:report-loaded`
CustomEvent. The dashboard's `useReport()` hook reads from either.

---

## 3. `analysis/`

The deepest module. A one-shot pipeline that ingests a session folder
and emits an in-memory `AnalysisReport`. Two entry points share the
pipeline:

- **CLI** — `cli/Main.kt` runs against a local session dir, writes
  the report to disk next to the inputs.
- **HTTP server** — `server/Main.kt` + `server/AnalyzeRoute.kt` (Ktor
  on Netty, port 8080) accepts the plugin's multipart upload, runs
  the pipeline in a tempdir, returns the report JSON inline, and
  deletes the tempdir.

Both entry points instantiate `RefactoringClient` once at boot via
`refactoring/RefactoringClientFactory.kt` and inject it into
`AnalysisPipeline`. The client owns an embedded Equinox framework
(see [§5](#5-refactoring-bundle)) and stays alive for the process
lifetime.

### Pipeline stages

`pipeline/AnalysisPipeline.kt` orchestrates the stages below. Each one
logs `[pipeline]` lines to stdout with stage durations.

1. **Load + normalise.** `ingest/TraceLoader.kt` reads
   `session.json` and validates internal consistency.
   `normalize/TraceNormalizer.kt` orders events by timestamp,
   reconciles file creations against `initial-src/`, and drops no-op
   edit bursts (`EDIT_BURST`s with zero net diff against the previous
   snapshot).

2. **Shadow repo reconstruction.**
   `reconstruct/ShadowRepoBuilder.kt` replays the normalised event
   stream into a fresh git repo under `<sessionDir>/shadow-repo/`,
   one commit per event. Output: `ReconstructionResult` carrying the
   repo dir and `eventCommits` (event id → SHA mapping). No-op events
   alias the previous SHA, so the unique-SHA count is typically much
   smaller than the event count.

3. **Refactoring mining.** `miner/RefactoringMinerRunner.kt` runs
   RefactoringMiner over the sliding window of unique-SHA pairs.
   Detected refactorings are mapped to typed `RefactoringSpec`s
   inline in the runner (one `when` arm per RM kind:
   ExtractOperationRefactoring → `RefactoringSpec.ExtractMethod`,
   RenameOperationRefactoring → `RefactoringSpec.RenameMethod`,
   etc.; unrecognised kinds fall through to `RefactoringSpec.Other`).
   `miner/BracketSpecReorderer.kt` reorders steps inside a single
   `(fromSha, toSha)` bracket so dependent ops (e.g. ExtractVariable
   inside a method that an InlineMethod later removes) apply in a
   sensible order. The `wasPerformedByIde` flag is set by correlating
   an RM detection's `(fromSha, toSha)` window against the trace's
   `REFACTORING_FINISHED` events.

4. **Validation.**
   `alternative/validate/RefactoringStepValidator.kt` replays each
   mined step against a worktree at `fromSha` via
   `SpecDispatcher.apply`, hashes the post-apply ASTs with
   `JavaFileAstHasher`, and AST-compares to the user's `toSha`.
   Verdicts per step: `VALID | REFACTOR_FAILED | AST_DIVERGED |
   UNTYPED`. Steps sharing the same `(fromSha, toSha)` bracket are
   replayed together (all-or-nothing verdict). The validator holds a
   single worktree and a single `withBatchSession` for the whole
   call — between brackets it `git reset --hard`s + calls
   `refreshProject()`. See [§5](#5-refactoring-bundle) for the
   session pattern.

5. **Reorder synthesis.**
   `alternative/synthesise/ReorderSynthesiser.kt` — see
   [non-trivial mechanism §3.A below](#3a-alternative-trajectory-synthesis).

6. **Single-step IDE replay.**
   `alternative/AlternativeTrajectoryRunner.kt` — see
   [§3.A](#3a-alternative-trajectory-synthesis).

7. **Metrics.** `metrics/MetricsRunner.kt` checks out every unique
   SHA (user + alt) sequentially into a single persistent worktree,
   runs `./gradlew build test`, and collects: CK metrics
   (`metrics/ck/`), PMD violations (`metrics/pmd/`), CPD duplications
   (`metrics/cpd/`), readability scores (`metrics/readability/`),
   git diff stats (`metrics/gitdiff/`), build/test outcomes
   (`metrics/gradlebuild/`, `metrics/tests/`). Results land in
   `<sessionDir>/checkpoint-metrics/<sha>.json`. This is the slow
   stage — every other stage is cheap by comparison.

8. **Diffs.** `diffs/DiffsRunner.kt` produces unified-diff patches per
   user checkpoint transition, per mined refactoring step, and per
   alt step (single-step or reorder). `diffs/PatchFilter.kt` strips
   diff hunks the dashboard doesn't need (binary, generated files).

9. **PMD violation tracking.** `metrics/pmd/PmdTrackingRunner.kt`
   computes a per-SHA `firstSeenAtSha` for every open PMD violation
   (and a `resolvedAtSha` for closed ones), threading violations
   across diffs via `DiffLineMapper`. The dashboard's
   "code-smells introduced by a refactoring" badge fires when
   `firstSeenAtSha` equals the post-refactoring SHA.

10. **CPD duplication tracking.** `metrics/cpd/CpdTrackingRunner.kt`
    same idea but for clone groups.

11. **Derived metrics.**
    `metrics/derived/DerivedMetricsRunner.kt` computes the composite
    cleanliness sub-score (literature-weighted PMD/CPD/readability)
    and the headline **process score** (Int 0–100) for every user
    checkpoint + every alt checkpoint. For multi-step reorder alts,
    it walks the chain of alt SHAs in permutation order so each step
    gets its own derived score comparable against the user's
    analogous checkpoint.

12. **Report assembly.** `buildAnalysisReport()` in
    `pipeline/AnalysisPipeline.kt` groups events by SHA, joins
    metrics/diffs/tracking, applies the shared-prefix trim on
    reorder alts (so the chart anchors the alt at the first
    divergent step), and finally calls
    `advice/TrajectoryAdvisor.advise(...)` to compute the session
    takeaways. The trajectory advice runs *last* so it can read every
    other piece of the report — see §3.D below.

### 3.A Alternative trajectory synthesis

The headline research idea: for refactorings the user did manually,
synthesise an IDE-driven equivalent; for windows of refactorings,
synthesise alternative *orderings* and compare end-states.

Two producers populate the same `AlternativeTrajectory` shape
(`metrics/model/AlternativeTrajectory.kt`) — a list of step indexes,
anchor `fromSha`, end-state `userToSha`, parallel arrays of
`branchRefs`, `specs`, and synthesised `altCheckpoints`.

#### Single-step IDE replay

`alternative/AlternativeTrajectoryRunner.kt`. Trigger conditions
(all required, evaluated in `rejectReason()`):

- `!wasPerformedByIde` (user did it by hand).
- `spec != null && spec !is RefactoringSpec.Other` (typed enough to
  dispatch).
- `toIdx - fromIdx > 1` (the user took multiple commits to land it —
  single-commit refactorings have no detour to collapse).

For each candidate, the runner borrows a worktree at `fromSha` via
`WorktreePool`, applies the spec via `SpecDispatcher.apply`, commits
the result with a deterministic local identity, and force-creates a
branch ref `alt/<stepIndex>` in the shadow repo. Failed dispatches /
no-textual-change applies are recorded in `Summary.skipped` with a
human-readable reason and surface in the pipeline log.

#### Multi-step reorder synthesis

`alternative/synthesise/ReorderSynthesiser.kt`. The pipeline:

1. **Window splitting.** Contiguous runs of VALID steps form a
   window; any non-VALID step (FAILED, DIVERGED, UNTYPED) splits the
   trace. See `splitOnInvalid()`.

2. **Dependency DAG.**
   `alternative/reorder/SpecDependencyAnalyzer.kt` derives pairwise
   must-come-before edges from each spec's
   `produces/reads/writes/consumes` effects on `Entity`s — see the
   full collision table in `alternative/reorder/README.md`. Symmetric
   collisions (produces/produces, consumes/consumes) become
   *invalidations* rather than edges (those orderings are unreachable
   regardless of order).

3. **SSA versioning.**
   `alternative/reorder/SpecVersioner.kt` pre-passes the window and
   assigns `version` to every named entity so post-rename references
   chain off the renamed identity rather than looking like
   independent operations on the same name.

4. **Topological enumeration.**
   `alternative/reorder/TopologicalEnumerator.kt` backtracks all
   valid orderings under an `EnumerationBudget`. Identity ordering is
   filtered out (the user's actual order is already in the trace).

5. **Prefix-trie DFS with git-checkout backtracking.** Every alt
   ordering is folded into a `PrefixTrie`
   (`alternative/synthesise/PrefixTrie.kt`); one worktree is borrowed
   at `windowFromSha` and one `withBatchSession` is opened for the
   entire window. The DFS walks the trie:
   - Forward edge → `SpecDispatcher.apply` + commit + force a
     per-prefix branch `reorder/win<W>/path/<dash-prefix>`.
   - Back edge → `worktreeGit.checkoutDetach(parentSha)` +
     `client.refreshProject()` (Eclipse re-stats the on-disk files;
     the bundle's cached `IJavaProject` survives because
     `projectRoot` is unchanged).

   Each unique prefix is materialised exactly once and reused by
   every ordering passing through it — N orderings sharing a depth-k
   prefix pay one apply for those k steps, not N×k.

   An earlier attempt used JDT's per-`Change` undo to backtrack, but
   per-file JDT caches (working-copy buffers + JavaModel element
   info) didn't reliably invalidate, producing corrupt commits on
   the next forward apply. Git-checkout + `refreshProject()` goes
   through Eclipse's standard out-of-band-edit pathway, which JDT
   handles cleanly.

6. **Terminal AST audit.** At every leaf (= complete ordering), the
   synthesiser hashes the user-changed `.java` set in the worktree
   and compares to pre-computed `windowToSha` hashes. Divergent
   terminals are filtered out of the report (the commits + refs
   persist in the shadow repo for offline forensics) so downstream
   scoring only compares end-state-equivalent orderings. See
   `Summary.terminalsAstMatched` / `terminalsAstDiverged` for the
   per-run counts.

7. **Pipeline downstream.** Successful orderings' intermediate SHAs
   piggy-back on `MetricsRunner.alternativeShas` so they get built/
   tested alongside the single-step alts in one sweep. The terminal
   step of each successful ordering is AST-equivalent to
   `windowToSha` by construction, so the pipeline aliases the
   terminal's `CheckpointMetrics` to the user's `windowToSha`
   checkpoint — saving a build+test per ordering. `buildAnalysisReport()`
   also trims any shared *prefix* of an ordering against the user's
   chronological order: if the alt starts by doing steps in the
   user's order, those leading steps produced state identical to the
   user's, so they're folded out of the alt's chart trail.

### 3.B Metrics pipeline

`metrics/MetricsRunner.kt` is the only sequential stage in the
pipeline — every other stage uses parallelism. The reason: Gradle's
daemon caches per-project init/index work, and bouncing between
SHAs in one worktree lets each subsequent build reuse those caches.
A parallel scheme with N worktrees would multiply daemon-init cost
N-fold for very little overlap (PMD + CPD analysers are not
particularly CPU-bound).

`metrics/WorktreePool.kt` is still parallelism-friendly and is used
by the validator and reorder synthesiser (which call into the
refactoring bundle, which is itself serialised under a single
`ReentrantLock` — see [§5](#5-refactoring-bundle) — so the pool
parallelism mostly overlaps cheap local work like AST hashing).

### 3.C PMD violation tracking and `firstSeenAtSha`

`metrics/pmd/PmdTrackingRunner.kt` threads each violation across
consecutive checkpoint pairs. Within a pair, `metrics/pmd/DiffLineMapper.kt`
maps each violation's `(file, line)` from the "before" SHA to the
"after" SHA using the unified diff, marking it `resolved` if the
post-state has no violation with the same rule at the mapped line,
`firstSeen` if the post-state has a violation the pre-state didn't,
or `carried` otherwise. The same predicate runs for each per-step
alt pair, so `pmdTracking.alternativeTrackingBySha` lets the
trajectory advisor say "this refactoring introduced N new smells."

### 3.D Trajectory advice

`advice/TrajectoryAdvisor.kt`: a fixed registry of 7 rule functions,
each a pure `(AnalysisReport) -> AdviceItem?`. Rules cover build
reliability, test reliability, commit cadence, refactoring-introduced
smells, net smell accumulation, process-score degradation, and
reorder-beats-user. Severity is `INFO | WARNING | CRITICAL`. Schema is
sealed via `metrics/model/AnalysisReport.kt::AdviceKind` so the
dashboard can switch on `kind` for icon/colour without string parsing.

The advisor is invoked at the end of `buildAnalysisReport()` so it has
the entire finished report to reason about. Rule outputs go into
`AnalysisReport.advice`, which is what the dashboard's
`TrajectoryAdvicePanel` renders.

---

## 4. `dashboard/`

React 18 + TypeScript + Vite + Tailwind + Zustand. Consumes the
`AnalysisReport` and renders a single-page IDE-style layout.

### Top-level composition

```
┌─────────────────── HeaderBar ───────────────────┐
│           │                                     │
│ MetricRail│  ┌────── Main (scrollable) ─────┐  │ DetailPanel
│           │  │   TrajectoryChart            │  │  (right-side
│           │  │   TrajectoryAdvicePanel      │  │   overlay)
│           │  │                              │  │
│           │  └──────────────────────────────┘  │
│           │  BottomStrip (filmstrip + cards)   │
└─────────────────────────────────────────────────┘
```

Defined in `src/App.tsx`. State holders:

- `useReport()` — pulls the report into memory.
- `toViewModel(report)` — adapts it for rendering.
- `useDashboardStore` — Zustand store for cross-feature selection /
  layer / metric state.

### Features

- 8-metric registry (process, cleanliness, cognitive, readability,
  duplication, smells, coupling, cohesion).
- Trajectory chart with primary metric line + up to two dashed
  secondaries + layer toggles for build/test intervals, alternative
  trajectories, and user commit markers.
- Detail panel: polymorphic body per selection kind (checkpoint,
  refactoring step, alternative-trajectory interval).
- Metric rail (left side): per-metric trend sparklines + tile click
  promotes a metric to primary.
- Bottom strip: horizontal refactoring filmstrip + explanation cards
  for the currently selected step.
- Trajectory-advice panel
  (`features/trajectory-advice/trajectory-advice-panel.tsx`):
  "session takeaways" rendered from `report.advice`, sorted by
  severity.
- Process-score breakdown panel inside the checkpoint body.

### Report loading

`hooks/useReport.ts`. Two paths:

- **Plugin path.** The IntelliJ tool window assigns
  `window.__REPORT__` and dispatches a `refdash:report-loaded`
  CustomEvent. The hook seeds state from the synchronous global and
  also listens for the event in case it arrives after mount.
- **Dev path.** A `analysis-report.json` next to the hook is imported
  at build time and returned directly. The hook currently
  short-circuits to this path; flip the early return to use the
  plugin path in production.

### Schema codegen

The TS report types are generated, not hand-written.

- **Source of truth.** Kotlin `@Serializable` classes in
  `analysis/.../metrics/model/` and `shared/.../model/`.
- **Generator.** `analysis/.../codegen/` — Gradle task
  `:analysis:generateDashboardTypes`.
- **Output.** `dashboard/src/generated/report-types.ts`.

Workflow when changing a backend schema: edit the Kotlin type,
re-run the Gradle task, run `cd dashboard && npm run typecheck`.

### View-model layer

`src/data/view-model.ts` converts `AnalysisReport` →
`DashboardViewModel`. Headline shapes (`data/types.ts`):

- `CheckpointVM` — per-checkpoint metric values keyed by `MetricId`,
  plus churn, patch hunks, PMD/CPD tracking buckets.
- `AlternativeTrajectoryVM` — list of steps; each step has its own
  alt checkpoint with metrics; shared-prefix trimming is already
  applied backend-side.
- `RefactoringStepVM` — links a mined step to its checkpoint and
  patch.
- `CommitMarkerVM` — git commit markers for the chart layer.
- `AdviceItemVM` — passthrough from the backend advice rules.

### Zustand store

`src/stores/dashboard-store.ts`:

- `selection` — what the detail panel shows
  (`checkpoint | interval | refactoring | null`).
- `primary` — the metric drawn as the solid trajectory line. Default:
  `"process"`.
- `secondaries` — up to two dashed overlays; mutually exclusive with
  `primary` (a `setPrimary` evicts the new primary from the overlay
  list, and pushing a third overlay drops the oldest).
- `layers` — chart toggles (build intervals, test intervals,
  alternative trajectories, user commit markers).

### Trajectory chart

`src/features/trajectory-chart/`. Files are kebab-case, the exported
React components are PascalCase. Single SVG root, composed of:

- `trajectory-chart.tsx` (`TrajectoryChart`) — top-level layout, axes.
- `use-chart-scales.ts` — derives D3 scales from VM metric ranges.
- `chart-lines.tsx` — main + overlay metric lines.
- `chart-points.tsx` — user checkpoint dots.
- `chart-alternative-paths.tsx` — alt-trajectory mini-trails with
  shared-prefix collapse already applied.
- `chart-refactoring-points.tsx` (+ `chart-refactoring-glyphs.tsx`) —
  refactoring markers.
- `chart-commit-markers.tsx` — git commit markers; layer-toggled.
- `chart-interval-rail.tsx` — build / test interval bars under the
  main plot.
- `chart-hover-overlay.tsx` — pointer dispatcher that sets
  `useDashboardStore.selection` on click.

### Detail panel polymorphism

`src/features/detail-panel/detail-panel.tsx` (`DetailPanel`)
dispatches on `selection.kind`:

- **checkpoint** → `checkpoint-body.tsx` (metric tiles, status row,
  commit signals, code-smells section, duplications section,
  diff-hunk table, process-score breakdown).
- **interval** (alt trajectory) → `alt-interval-body.tsx`
  (side-by-side comparison vs. the user's analogous interval).
- **refactoring** → checkpoint body rendered with refactoring-
  specific patch (just hunks, no file headers), plus
  `refactoring-pitfalls.tsx` callouts.

---

## 5. `refactoring-bundle/`

Eclipse JDT refactoring engine running headlessly in an embedded
Equinox OSGi framework. The bundle is loaded from disk at analysis
startup and lives for the process lifetime.

### Features

- 25+ JDT refactorings exposed as `@JvmStatic` methods on
  `JdtRefactorer`: Extract Method / Variable / Field / Constant /
  Class / Interface / Superclass; Inline Method / Variable / Constant;
  Rename Method / Field / Local / Class / Package / Type Parameter /
  Compilation Unit; Move Method (static + instance) / Class /
  Inner-to-Top; Pull Up; Push Down; Change Method Signature; Change
  Type; Convert Anonymous to Inner; Convert Local to Field; etc.
- All ops take primitive params (String / Int / Array<String>) and
  return a JSON-serialised outcome — that's the OSGi boundary.
- Batch-session pattern for amortised project init + index across
  consecutive refactorings.

### OSGi boundary

`analysis/.../refactoring/RefactoringClient.kt` is the host-side
facade. At construction (via `RefactoringClientFactory`) it boots an
Equinox `Framework`, loads the bundle JAR, resolves the
`JdtRefactorer` class via reflection, and holds the instance.

Per refactoring:

1. The host-side per-op file (e.g. `refactoring/ExtractMethod.kt` in
   `analysis/`) calls `client.invokeOnBundle(name, paramTypes, args)`.
2. `invokeOnBundle` resolves the method on `JdtRefactorer` (cached
   in a `ConcurrentHashMap`), invokes it under `lock`, and parses
   the returned JSON into a typed `RefactoringOutcome` (Ok / Failed
   with reason).

JSON-as-wire-format keeps the host classloader from needing to share
Eclipse classes with the bundle — every bundle-internal type stays
inside the OSGi container.

### Locking

`RefactoringClient.invokeOnBundle` wraps every call in
`lock.withLock { ... }` (a `ReentrantLock`). The bundle's
`RefactoringHost` mutates shared cached-project state, and
correctly handling concurrent calls would require fine-grained locks
inside the bundle that we don't have. Validators and reorder
synthesisers were previously parallel; the parallelism was removed
once a profiler confirmed the per-call lock serialised everything
anyway — see `git log --grep="drop validator parallelism"` and
`tool/plans/PLAN.md`.

### Batch sessions

`RefactoringHost.withBatchSession` keeps the indexed
`IJavaProject` alive across consecutive `invokeOnBundle` calls *on
the same `projectRoot`*. Lifecycle:

1. The host-side `client.withBatchSession { body() }` acquires the
   client lock (reentrant, so per-op calls inside `body` don't
   deadlock).
2. Tells the bundle to keep the project cache after each call.
3. Runs `body` — every `invokeOnBundle` inside sees a warm
   `IJavaProject`.
4. In `finally`: tears down the cache.

Pairs with `client.refreshProject()`: when the reorder synthesiser
does a `git checkout --detach` between forward/back edges, the on-
disk files change under the bundle's feet. `refreshProject()` calls
into the bundle's `IResource.refreshLocal(...)` so Eclipse's resource
model re-stats files and JDT picks up the new state — the standard
"files changed under us" pathway that IDE users exercise constantly.

### Anchor resolution

Source-text-based selection is brittle (formatting changes, identical
identifiers in multiple methods). JDT-side ops in the bundle resolve
the target element from a `(line, column, subtreeHash)` triple — line
+ column are the rough anchor, subtree hash discriminates ambiguous
matches by AST shape. `JavaFileAstHasher` (in `analysis/`) computes
the same hash on the host side so spec construction and bundle-side
resolution agree.

---

## 6. `shared/`

Cross-wire types between plugin and analysis. Lives in
`shared/src/main/kotlin/com/github/ethanhosier/ideplugin/model/`.

- **`EventType.kt`** — enum of every event kind. Editor (`EDIT_BURST`,
  `FILE_*`), file structure (`FILE_CREATED`/`DELETED`/`RENAMED`/
  `MOVED`/`MODIFIED_EXTERNAL`), refactoring (`REFACTORING_STARTED`/
  `FINISHED`), build/test (`BUILD_STARTED`/`FINISHED`,
  `TEST_RUN_STARTED`/`FINISHED`), session lifecycle
  (`SESSION_STARTED`/`ENDED`), VCS (`GIT_COMMIT`).
- **`TraceEvent.kt`** — `id`, `type`, `timestamp`, `sessionId`,
  `changedFiles: List<FileSnapshot>`, `relatedFiles`, `payload:
  Map<String, String>` (typed-ish key/value bag for per-event
  metadata).
- **`FileSnapshot.kt`** — `path`, optional `previousPath` (for
  rename/move), `changeType`, `contents` (post-change), and
  `touchedMembers: List<TouchedMember>` (qualified name + kind).
- **`SessionMetadata.kt`** — `sessionId`, `name`, `projectName`,
  `projectPath`, `branch`, `commitHash`, `startTime`, optional
  `endTime`, `ideVersion`, `pluginVersion`.
- **`Session.kt`** — `metadata: SessionMetadata` + `events:
  List<TraceEvent>`. Serialised to `session.json`.

All `@Serializable`; both sides round-trip them through `kotlinx-
serialization` JSON.

---

## 7. `metrics-core/` and `metrics-lambda/`

Currently inert. Both directories exist on disk under `tool/` but
neither is listed in `settings.gradle.kts` and neither contains
Kotlin sources right now (just stale `build/` directories).

Intended future work (tasks #61–#63 in the task list):

- **`metrics-core/`** — extract the metric DTOs + analysers from
  `analysis/.../metrics/` so they can be linked from both the local
  `MetricsRunner` and a remote runner.
- **`metrics-lambda/`** — package the analysers as an AWS Lambda
  container image. The pipeline would dispatch per-checkpoint
  build+test+analyse work to a fan-out of Lambda invocations rather
  than serialising them through one local worktree, cutting the
  longest stage by an order of magnitude on large sessions.

The plumbing on the analysis side (`LambdaInvoker`,
`RemoteCheckpointExecutor`) is sketched in those task notes but not
on `main`. Treat this section as a forward-looking note, not a
description of current code.

---

## 8. End-to-end walkthrough

Concrete trace through every module for a minimal session.

**Setup.** User opens the project. Plugin loads. User clicks Start
Session, names it "demo".

**What happens.**

1. `StartSessionAction` → `SessionService.startSession("demo")`:
   - UUID generated, IDE + plugin versions captured, git HEAD read.
   - `initial-src/` snapshot written under
     `~/.refactoring-tracer/sessions/<uuid>/`.
   - `SESSION_STARTED` event written.
   - `GitCommitWatcher.start(...)` begins polling.
   - Listeners begin emitting events.

2. User edits two files. Each file accumulates keystrokes in
   `EditBurstTracker`. After ~1 s quiet on each, the tracker flushes
   one `EDIT_BURST` per file with the in-memory document text and
   the `TouchedMember` list (computed via PSI).

3. User invokes IntelliJ's Extract Method. `RefactoringListener`
   emits `REFACTORING_STARTED`, then `REFACTORING_FINISHED` with the
   typed spec in the payload (selection range, new method name,
   target class).

4. User runs `git commit`. `GitCommitWatcher` notices the new SHA on
   the next poll and emits `GIT_COMMIT` with `sha`, `parentSha`,
   `authorTimestamp`, and `message`.

5. User clicks End Session. `EndSessionAction` →
   `SessionService.endSession()`:
   - Pending edit bursts flush.
   - `SESSION_ENDED` event written.
   - `session.json` serialised.
   - `AnalysisClient.upload(sessionDir)` POSTs the multipart bundle.

6. Analysis server (`server/AnalyzeRoute.kt`) receives the upload,
   unpacks to a tempdir, calls `AnalysisPipeline.run(tempDir)`:
   - **Load + normalise:** 6 events kept after dedup.
   - **Reconstruct:** 4 unique SHAs in `shadow-repo/`.
   - **Mine:** one ExtractMethod step detected.
   - **Validate:** typed spec replays cleanly → `VALID`.
   - **Reorder synth:** window of 1 typed spec → singleton → skipped.
   - **Alt-traj runner:** single-step bracket
     (`toIdx - fromIdx == 1`) → skipped.
   - **Metrics:** 4 SHAs built+tested. Per-SHA JSON written under
     `checkpoint-metrics/`.
   - **Diffs:** 3 checkpoint patches + 1 refactoring patch.
   - **PMD + CPD tracking:** computed.
   - **Derived metrics:** process score for each of the 4
     checkpoints.
   - **Advice:** no rules fire on a clean session → empty advice.
   - **Report assembled** and returned in the HTTP response body.

7. Plugin receives the report, writes `analysis-report.json` to the
   session folder, opens the dashboard tool window
   (`ide-plugin/.../toolWindow/`), assigns `window.__REPORT__`, and
   dispatches `refdash:report-loaded`.

8. Dashboard's `useReport()` returns the report. `toViewModel()`
   shapes it. `App.tsx` renders:
   - 4-point trajectory line (default metric: process score).
   - 1 refactoring marker.
   - 1 git commit marker.
   - "Nothing to flag — clean session." in the advice panel.
   - Filmstrip with 1 refactoring card at the bottom.

   Clicking the refactoring card sets `selection`, the right-side
   detail panel slides in with the refactoring's patch + smell
   impact + metric tile diffs.

---

## 9. Build & run commands

From `tool/`:

- **Backend tests.** `./gradlew :analysis:test :refactoring-bundle:test`
- **Specific test class.**
  `./gradlew :analysis:test --tests TrajectoryAdvisorTest`
- **TS typecheck.** `cd dashboard && npm run typecheck`
- **TS dev server.** `cd dashboard && npm run dev` (loads the bundled
  `analysis-report.json` via the dev path in `useReport.ts`).
- **Regen dashboard types.** `./gradlew :analysis:generateDashboardTypes`
  — run after any backend schema change.
- **Analysis CLI.** `./gradlew :analysis:run --args="<sessionDir>"`
  (writes the report next to the inputs).
- **Analysis HTTP server.** `./gradlew :analysis:runServer` (listens
  on port 8080 by default; set `REFACTORING_TRACER_SERVER_URL` on
  the plugin side to point elsewhere).
- **Plugin sandbox.** `./gradlew :ide-plugin:runIde` (launches a
  sandboxed IntelliJ with the plugin installed).

