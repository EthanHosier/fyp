# Plan: Tool Architecture chapter pass

## Context

The Tool Architecture chapter is item #4 in `PLAN-thesis-writeup.md`
(estimated 2 days). Per `HONEST_REVIEW_2.md` §"Tool architecture
chapter":

> *"Keep brief; reuse ARCHITECTURE.md. The Phase A / Phase B split
> is the key engineering claim to surface here, with one diagram."*

The methodology chapter (now drafted) defers all
engineering-performance content here, leaving four explicit
`Chapter~4` forward-references in `methodology/methodology.tex`:

| Methodology location | What the forward-ref points to |
|----------------------|--------------------------------|
| §3 chapter preamble (line 6) | "JDT batch-session reuse, single-worktree borrow, git-checkout backtracking" |
| §3.5.2 MANUAL_REFACTOR (line 181) | "wrap-and-patch layer" |
| §3.6 reorder window-split (line 199) | "wrap-and-patch layer" (re-used) |
| §3.6 reorder synthesiser (line 196) | "single borrowed worktree, single JDT batch session, git-checkout backtracking" |
| §3.10 validator (line 249) | "single batch session, single worktree borrow, AST-hash via `git show`" |

This chapter must define labels those forward-refs land on, then
the methodology placeholders convert from plain `Chapter~4` to
`\ref{...}` in the same pass.

**Page budget: 8 target, 10 hard cap.** Tight enough that Phase A/B
+ JDT engineering get the spotlight without the chapter ballooning
into a code tour.

Sources are already in place:
- **Source-of-truth doc:** `tool/ARCHITECTURE.md` (857 lines,
  module-by-module dev-level tour).
- **Code:** `pipeline/AnalysisPipeline.kt`, `pipeline/PhaseAResult.kt`,
  `pipeline/ReportAssembler.kt`, `cli/PhaseACli.kt`, `cli/PhaseBCli.kt`,
  `alternative/synthesise/ReorderSynthesiser.kt`,
  `alternative/validate/RefactoringStepValidator.kt`,
  `refactoring/RefactoringClient.kt`, `refactoring/RefactoringClientFactory.kt`,
  `alternative/synthesise/PrefixTrie.kt`, `metrics/WorktreePool.kt`,
  `JavaFileAstHasher.kt`. Plus all of `ide-plugin/` and `dashboard/`.
- **Empirical anchor:** `explained_results/sensitivity.md` reports
  4860 Phase-B replays in ~2.6s - the headline number that justifies
  the Phase A / Phase B split. Cross-reference into §4.2.

**Pre-step:** create `final_report/architecture/architecture.tex`,
wire it into `main.tex` AND `main_dark.tex` between
`\input{methodology/...}` and `\input{project_plan/...}`. Both light
and dark variants must include the same chapter inputs.

**Important repo note.** ARCHITECTURE.md §7 documents
`metrics-core/` and `metrics-lambda/` as "currently inert / future
work". Those modules have since been **deleted from the repo** -
this chapter should not mention them at all (not even as deferred
work). The end-of-pass verification confirms via
`ls tool/` that the modules are gone.

## Chapter outline (with page budget)

| § | Section                                                        | Pages |
|---|----------------------------------------------------------------|------:|
| 4.1 | Module overview (with diagram)                                | 1.5   |
| 4.2 | The Phase A / Phase B split (headline engineering claim)      | 1.5   |
| 4.3 | IDE plugin: event capture and session storage                 | 1     |
| 4.4 | Analysis pipeline stages                                      | 1     |
| 4.5 | Refactoring bundle: embedded Eclipse JDT via OSGi             | 1     |
| 4.6 | The reorder-search engine: worktree borrow, batch session, git-checkout backtracking | 1.5 |
| 4.7 | The wrap-and-patch layer                                      | 0.5   |
| 4.8 | Dashboard                                                     | 0.5   |
| **Total** |                                                          | **~8.5** |

## Per-section detail

### §4.1 Module overview (1.5 pages)

`\label{ch:architecture}` at the chapter top.

Open with a one-paragraph topic-sentence summary: this thesis's
tool is a four-module JVM build (`:ide-plugin`, `:analysis`,
`:refactoring-bundle`, `:shared`) plus a standalone React app
(`dashboard/`). Each module has a single architectural
responsibility, and the wire formats between them are explicit
serialisable types.

Then the headline TikZ figure - see "TikZ diagram approach" below.
The figure replaces ARCHITECTURE.md's ASCII flow diagram (§1, lines
29-56).

After the figure, one paragraph per module (5 paragraphs):

- **`ide-plugin/`** - IntelliJ plugin recording the session.
- **`analysis/`** - Kotlin pipeline that ingests the session,
  reconstructs a shadow git repo, mines refactorings, synthesises
  alternative trajectories, and emits an `AnalysisReport`.
- **`refactoring-bundle/`** - headless Eclipse JDT loaded via an
  embedded Equinox OSGi framework; drives every IDE-style
  refactoring used in alternative-trajectory synthesis.
- **`dashboard/`** - React/TypeScript visualisation.
- **`shared/`** - cross-wire types between plugin and analysis
  (`Session`, `TraceEvent`, `SessionMetadata`, `FileSnapshot`,
  `EventType`).

Close §4.1 with a short paragraph on schema sharing: Kotlin
`@Serializable` types in `shared/` and `analysis/.../metrics/model/`
are the single source of truth; the dashboard's TypeScript types are
codegenned via the `:analysis:generateDashboardTypes` Gradle task.
This is a cross-cutting concern worth naming but not deep-diving.

### §4.2 The Phase A / Phase B split (1.5 pages)

`\label{sec:arch-phases}`.

The chapter's headline engineering claim. Topic sentence: the
analysis pipeline is structured as two phases - an **expensive
Phase A** that produces a serialisable `PhaseAResult`, and a
**cheap Phase B** that assembles an `AnalysisReport` from the
`PhaseAResult` plus a `ScoringConfig`.

Body covers:
- **Phase A** does the costly work: trace normalisation, shadow
  repo reconstruction, RefactoringMiner mining, validator,
  reorder + IDE_REPLAY + REWORK synthesisers, per-checkpoint
  metrics (Gradle build/test + CK + PMD + CPD + readability),
  PMD/CPD tracking. Cite `cli/PhaseACli.kt` and
  `pipeline/PhaseAResult.kt`. Per-fixture wall-clock: minutes.
- **Phase B** does only report assembly:
  `ReportAssembler.assemble(phaseA, scoringConfig)` re-derives
  cleanliness scores and process scores from the cached
  `PhaseAResult` checkpoint metrics under any `ScoringConfig`.
  Cite `cli/PhaseBCli.kt` and `pipeline/ReportAssembler.kt`.
  Per-fixture wall-clock: milliseconds.
- **Why this matters.** The sensitivity experiment runs
  $12 \times 9 \times 45 = 4860$ scoring configurations in
  **~2.6 s wall-clock total** (per `sensitivity.md`); the
  ablation experiment runs the full $2^6 = 64$-config power set
  across 45 fixtures in seconds. Without the split, each
  configuration would have to re-run Phase A end-to-end - the
  experiments in Chapter~\ref{ch:results} would not be tractable.
- **The architectural property that makes the split clean.**
  `ScoringConfig` (the weights of Equation~\ref{eq:process-score})
  enters the pipeline only at the derived-metrics step. Phase A
  caches the *raw* checkpoint metrics (CK / PMD / CPD /
  readability / build / test outcomes), not the scored output;
  Phase B is where weights become process-score numbers. So
  perturbing weights cannot invalidate any Phase A artefact.

Close with a forward-reference to the Chapter~5 experiments that
exploit this property. Mention - briefly - that the same
serialisable-checkpoint structure is what lets dashboards iterate
on the same `PhaseAResult` (the dashboard re-renders directly off
the assembled report).

### §4.3 IDE plugin: event capture and session storage (1 page)

`\label{sec:arch-plugin}`.

Topic sentence: the IDE plugin's responsibility is to record what
the developer did at a granularity finer than a git commit.

Body:
- **Listener architecture.** One paragraph naming the listeners:
  `EditorEventListener`, `FileEditorListener`, `VfsListener`,
  `FileSaveListener`, `RefactoringListener` (plus
  `RefactoringCommandListener` + `RefactoringTemplateListener`
  variants), `BuildListener`, `TestRunListener`,
  `ProjectCloseListener`. Cite source paths inline.
  Each listener subscribes to an IntelliJ message-bus topic and
  emits a typed `TraceEvent` via the project-level
  `SessionService`.
- **Edit burst tracking.** `services/EditBurstTracker.kt`
  debounces keystroke-level `DocumentEvent`s into one
  `EDIT_BURST` per file per quiet window. The scheduled flush
  reads the **in-memory document text**, not the on-disk file -
  so the recorded snapshot reflects what the user typed,
  regardless of auto-save / on-save formatters.
- **Git commit watching.** `services/GitCommitWatcher.kt` is a
  polling thread, not a reflog watcher: it runs
  `git log --since=<sessionStart>` once a second and emits one
  `GIT_COMMIT` event per new SHA. The reflog approach was
  abandoned because fresh `git init` repos may disable it.
  Disclose the design rationale honestly.
- **Session storage.** `SessionService` + `StorageService`:
  events.jsonl (live append) + session.json (final flush) +
  initial-src/ (snapshot at session start) under
  `~/.refactoring-tracer/sessions/<uuid>/`. Multipart POST to the
  analysis server at session end.
- **Upload boundary.** The plugin has **no Gradle dependency**
  on `:analysis`; the only shared types live in `:shared`. This
  is what lets the analysis HTTP server live anywhere - the
  plugin only knows the `Session` schema and the
  `$REFACTORING_TRACER_SERVER_URL/analyze` endpoint.

### §4.4 Analysis pipeline stages (1 page)

`\label{sec:arch-pipeline}`.

Topic sentence: the analysis pipeline is a 12-stage one-shot run
that ingests a session folder and emits an `AnalysisReport`. The
stage list is the spine of the architecture; pull-out details for
the headline algorithms appear in §4.5-§4.7.

A compact stages **table** rather than a long bullet list:

| # | Stage | Output | Detail in |
|---|-------|--------|-----------|
| 1 | Load + normalise (`TraceLoader`, `TraceNormalizer`) | ordered event stream | - |
| 2 | Shadow repo reconstruction (`ShadowRepoBuilder`) | `shadow-repo/` + event-id → SHA map | - |
| 3 | Refactoring mining (`RefactoringMinerRunner` + `BracketSpecReorderer`) | typed `RefactoringSpec` list | - |
| 4 | Validation (`RefactoringStepValidator`) | per-step verdicts | §4.6 |
| 5 | Reorder synthesis (`ReorderSynthesiser`) | alternative trajectories | §4.6 |
| 6 | Single-step IDE replay (`AlternativeTrajectoryRunner`) | single-step alts | §4.7 |
| 7 | Metrics (`MetricsRunner`) | per-SHA checkpoint metrics | - |
| 8 | Diffs (`DiffsRunner` + `PatchFilter`) | unified-diff patches | - |
| 9 | PMD violation tracking (`PmdTrackingRunner`) | `firstSeenAtSha` / `resolvedAtSha` per violation | - |
| 10 | CPD duplication tracking (`CpdTrackingRunner`) | clone-group tracking | - |
| 11 | Derived metrics (`DerivedMetricsRunner`) | cleanliness + process score per checkpoint | §4.2 |
| 12 | Report assembly (`buildAnalysisReport()`) | `AnalysisReport` | §4.2 |

After the table, a short paragraph naming the two entry points
(`cli/Main.kt` for local runs; `server/Main.kt` + `AnalyzeRoute.kt`
for the plugin upload path), and the persistent state both share -
a single `RefactoringClient` instance instantiated at boot via
`RefactoringClientFactory`, held for the process lifetime.

Half a paragraph on why Metrics is the only sequential stage: the
Gradle daemon caches per-project init/index work, so bouncing
between SHAs in one persistent worktree is faster than fanning out
to N parallel worktrees. The other stages parallelise freely under
`WorktreePool`.

### §4.5 Refactoring bundle: embedded Eclipse JDT via OSGi (1 page)

`\label{sec:arch-bundle}`.

Topic sentence: the analysis pipeline applies refactorings via a
real Eclipse JDT engine running headlessly inside the analysis
process.

Body:
- **Why headless JDT.** JDT is the only Java refactoring engine
  with the breadth (~25 operations covering Extract / Inline /
  Move / Rename / Pull-Up / Push-Down etc.) and the maturity to
  serve as ground truth. But JDT was designed to run inside
  Eclipse, not as a library. The standard headless solution is
  to host an embedded Equinox OSGi framework.
- **The OSGi boundary.** `refactoring/RefactoringClient.kt`
  boots an Equinox `Framework` at analysis startup, loads the
  bundle JAR, resolves `JdtRefactorer` via reflection, and holds
  the instance for process lifetime. Per refactoring,
  `invokeOnBundle(name, paramTypes, args)` invokes the bundle
  method under a `ReentrantLock` and parses a JSON-serialised
  outcome. **JSON-as-wire-format** keeps Eclipse classes from
  crossing the classloader boundary into the analysis host -
  every bundle-internal type stays inside OSGi.
- **Locking and serialised access.** `RefactoringClient`
  wraps every call in `lock.withLock { ... }`. `RefactoringHost`
  mutates shared cached-project state; fine-grained internal
  locks would be necessary for parallelism but don't exist. The
  validator and reorder synthesiser used to run parallel; the
  parallelism was removed after profiling confirmed the per-call
  lock serialised everything anyway.
- **Anchor resolution.** Source-text-based selection is brittle
  (formatting, identical identifiers). JDT-side ops resolve the
  target element from a `(line, column, subtreeHash)` triple:
  line+column are the rough anchor, the subtree hash
  discriminates ambiguous matches by AST shape.
  `JavaFileAstHasher` computes the same hash on the host side so
  spec construction and bundle-side resolution agree.

Forward-cite \S\ref{sec:arch-reorder-engine} for the batch-session
pattern that amortises JDT project init/index across
consecutive refactorings.

### §4.6 The reorder-search engine: worktree borrow, batch session, git-checkout backtracking (1.5 pages)

`\label{sec:arch-reorder-engine}`.

This section carries the methodology chapter's §3.6, §3 preamble,
and §3.10 forward-refs. Topic sentence: the reorder synthesiser
needs to apply hundreds of refactoring sequences against a single
project state; three engineering choices make this tractable.

Body:

1. **Single borrowed worktree.** A single worktree is borrowed at
   `windowFromSha` via `WorktreePool.borrow()` for the entire
   reorder window. Forward edges (`SpecDispatcher.apply` + commit
   + force per-prefix branch ref `reorder/win<W>/path/<dash-prefix>`)
   and back edges (`worktreeGit.checkoutDetach(parentSha)`)
   reuse the same on-disk checkout.

2. **Single JDT batch session.** One `client.withBatchSession`
   is opened for the entire window. The batch session keeps the
   indexed `IJavaProject` alive across every `invokeOnBundle`
   call inside the window, so each subsequent refactoring sees a
   warm project model. The teardown happens in `finally` after
   the window completes.

3. **Git-checkout backtracking.** Back edges in the DFS use
   `worktreeGit.checkoutDetach(parentSha)` + `client.refreshProject()`
   to roll the worktree back to a parent state. The
   `refreshProject()` call goes through Eclipse's
   `IResource.refreshLocal(...)` path - the same "files changed
   under us" pathway IDE users exercise constantly - so JDT
   re-stats the on-disk files cleanly.

Then a short **discarded-alternative paragraph**: an earlier
attempt used JDT's per-`Change` undo to backtrack. It didn't
work - per-file JDT caches (working-copy buffers + JavaModel
element info) didn't reliably invalidate, producing corrupt
commits on the next forward apply. Git-checkout +
`refreshProject()` is the standard pathway and works.

Then a **prefix-trie amortisation paragraph**: every alternative
ordering is folded into a `PrefixTrie`
(`alternative/synthesise/PrefixTrie.kt`); the DFS walks the trie
so each unique prefix is materialised exactly once. N orderings
sharing a depth-k prefix pay one apply for those k steps, not
N×k. This is what turns an exponential search into a
worst-case-product traversal.

Close the section by noting that the validator
(`RefactoringStepValidator`) reuses the same three engineering
choices for bracket replay - single worktree, single batch
session, git reset between brackets - and that the validator can
compute the user's reference AST hashes via `git show`
piped into `JavaFileAstHasher` rather than maintaining a second
worktree. This is what closes methodology §3.10's forward-ref.

### §4.7 The wrap-and-patch layer (0.5 page)

`\label{sec:arch-wrap-patch}`.

Carries methodology §3.5.2 and §3.6 window-split forward-refs.
Topic sentence: even when IntelliJ and Eclipse JDT agree on the
*semantics* of a refactoring, they sometimes produce
different terminal ASTs. The wrap-and-patch layer closes the gap.

Body identifies two real divergences (verified against the
codebase - cite the specific files):

- **`static` modifier divergence.** JDT and IntelliJ disagree on
  whether the post-extract method should carry the `static`
  modifier in certain contexts. Captured in
  `RefactoringSpec.kt`'s `isStatic` comment. The wrap layer
  inspects the user's terminal AST and post-patches the bundle's
  output to align.
- **Return-vs-void shape on Extract Method.** When the extracted
  region contains a `return`, JDT and IntelliJ disagree on
  whether the new method should return a value (and have its
  call site assign to a local) or return void (and have its
  call site bear an inlined return). Captured by commit
  `6005505 ExtractMethod: post-extract rewrite for void-return +
  inlined-return shapes`. A small manual git patch closes the
  delta after the JDT operation completes.

Honest disclosure paragraph: where the residual gap cannot be
patched safely, the bracket gets the `AST_DIVERGED` verdict and
is not reordered - methodology §3.6 explains how this gating
flows through. The wrap-and-patch layer expands the set of
brackets the validator can return `VALID` on, but it isn't a
complete bridge between the two engines.

### §4.8 Dashboard (0.5 page)

`\label{sec:arch-dashboard}`.

Topic sentence: the dashboard is a single-page React/TypeScript
app that consumes the `AnalysisReport` produced by Phase B and
renders the IDE-style layout.

Body, condensed:
- Stack: React 18, TypeScript, Vite, Tailwind, Zustand.
- **Schema codegen**, not hand-written: Kotlin
  `@Serializable` types in `analysis/.../metrics/model/` and
  `shared/.../model/` are source-of-truth; the
  `:analysis:generateDashboardTypes` Gradle task generates
  `dashboard/src/generated/report-types.ts`. Any backend schema
  change must regen frontend types before `npm run typecheck`
  passes.
- **Two report-loading paths.** Plugin path: the IntelliJ tool
  window assigns `window.__REPORT__` and dispatches a
  `refdash:report-loaded` CustomEvent. Dev path: a bundled
  `analysis-report.json` is imported at build time. `useReport()`
  reads from either.

One sentence noting the dashboard is a *presentation surface*,
not an engineering contribution - the load-bearing engineering
of this thesis is in Phase A / Phase B + the JDT reorder engine
+ the wrap-and-patch layer.

## TikZ diagram approach (load-bearing figure for §4.1)

Replace ARCHITECTURE.md's ASCII flow with a TikZ figure. Single
figure, two-row layout:

- **Top row.** Three boxes representing the recording side -
  `ide-plugin` (IntelliJ) on the left, with an arrow labelled
  "multipart POST (session.json + initial-src.zip)" pointing to
  the `analysis` box in the centre. To the right of `analysis`,
  the `refactoring-bundle` box with a bidirectional "in-process
  via OSGi" arrow.
- **Bottom row.** Below `analysis`, an arrow labelled
  "AnalysisReport JSON (HTTP response body)" looping back up to
  `ide-plugin`, then a dashed arrow labelled "window.__REPORT__"
  pointing to the `dashboard` box.
- **Shadow repo callout.** A small dashed box labelled
  "shadow-repo/ (per-session)" floating to the right of
  `analysis`, with a dashed line indicating "shadow git commits
  + branch refs" rather than HTTP traffic.

Use a colour palette compatible with both the light and dark mode
builds: black/grey strokes with white fill in light, white/grey
strokes with `darkbg` fill in dark. The `xcolor` definitions in
`main_dark.tex` (`darkfg`, `darkbg`, `darklink`, `darkcite`,
`darkrule`) can be re-used via `\ifcsname pagecolor@dark\else...`
or by leaving the figure stroke-only (no fill).

Caption: "High-level dataflow between the recording side (IDE
plugin), the analysis pipeline, the embedded Eclipse JDT engine,
and the dashboard. The shadow repo holds every reconstructed
state - both user checkpoints and synthesised alternatives - as
git commits in a per-session repository."

Label: `\label{fig:arch-overview}`. Cross-cite from §4.2 and §4.4.

## Forward-reference wiring (methodology pass)

Once `architecture.tex` defines its labels, convert the four
methodology forward-refs in `methodology/methodology.tex`:

| Line | Current text | Target |
|------|--------------|--------|
| 6 | `... is covered in Chapter~4 (Tool Architecture).` | `... is covered in Chapter~\ref{ch:architecture} (\S\ref{sec:arch-reorder-engine}).` |
| 181 | `... are described in Chapter~4 (Tool Architecture).` | `... are described in \S\ref{sec:arch-wrap-patch}.` |
| 196 | `... are described in Chapter~4.` | `... are described in \S\ref{sec:arch-reorder-engine}.` |
| 199 | `The wrap-and-patch layer described in Chapter~4 ...` | `The wrap-and-patch layer described in \S\ref{sec:arch-wrap-patch} ...` |
| 249 | `... are described in Chapter~4.` | `... are described in \S\ref{sec:arch-reorder-engine}.` |

Verify the LaTeX compile produces no unresolved references and
the rendered chapter cross-refs read naturally.

## Source-material map

| Architecture section | Primary ARCHITECTURE.md source | Primary code |
|---------------------|--------------------------------|--------------|
| §4.1 Module overview        | §1 (lines 21-101)         | `settings.gradle.kts`; module `build.gradle.kts` files |
| §4.2 Phase A / Phase B      | (not in ARCHITECTURE.md; derive from code) | `pipeline/AnalysisPipeline.kt`; `pipeline/PhaseAResult.kt`; `pipeline/ReportAssembler.kt`; `cli/PhaseACli.kt`; `cli/PhaseBCli.kt` |
| §4.3 IDE plugin             | §2 (lines 105-224)        | `ide-plugin/.../services/`, `.../listeners/`, `.../actions/` |
| §4.4 Pipeline stages        | §3 (lines 247-343)        | `pipeline/AnalysisPipeline.kt` |
| §4.5 Refactoring bundle     | §5 (lines 625-707)        | `refactoring/RefactoringClient.kt`, `refactoring/RefactoringClientFactory.kt` |
| §4.6 Reorder-search engine  | §3.A (lines 344-444)      | `alternative/synthesise/ReorderSynthesiser.kt`, `PrefixTrie.kt`, `alternative/validate/RefactoringStepValidator.kt`, `metrics/WorktreePool.kt` |
| §4.7 Wrap-and-patch layer   | (not in ARCHITECTURE.md; derive from code + git log) | `RefactoringSpec.kt` (isStatic block); commit `6005505` ExtractMethod post-extract rewrite |
| §4.8 Dashboard              | §4 (lines 490-622)        | `dashboard/src/App.tsx`, `dashboard/src/hooks/useReport.ts`, `analysis/.../codegen/` |

## Critical files

- `/Users/ethanhosier/Desktop/random/fyp/final_report/architecture/architecture.tex`
  - **new file** to create.
- `/Users/ethanhosier/Desktop/random/fyp/final_report/main.tex`
  - add `\input{architecture/architecture.tex}` between
    `\input{methodology/methodology.tex}` and
    `\input{project_plan/project_plan.tex}`.
- `/Users/ethanhosier/Desktop/random/fyp/final_report/main_dark.tex`
  - mirror the same `\input{...}` insertion.
- `/Users/ethanhosier/Desktop/random/fyp/final_report/methodology/methodology.tex`
  - convert the five placeholder `Chapter~4` references to
    `\ref{...}` per "Forward-reference wiring" above.
- `/Users/ethanhosier/Desktop/random/fyp/final_report/bibs/sample.bib`
  - add any citations returned by Prompts A1 / A2 / A3 (see
    below). Likely candidates:
    - Negara et al. 2012/2014 CodingTracker (IDE-event capture).
    - Vakilian & Johnson 2014 use-disuse study.
    - Murphy-Hill, Parnin & Black 2012 "How we refactor"
      (already cited in methodology; may resurface here for
      plugin-design rationale).
    - Watchdog or Spelman 2013 / 2015 if relevant.
    - Tsantalis 2018/2020/2022 RefactoringMiner (already cited).
- Reference (read-only): all code files in the source-material
  map; `ARCHITECTURE.md`; `HONEST_REVIEW_2.md`;
  `PLAN-thesis-writeup.md`; the four `explained_results/*.md`
  files for cross-reference numbers (esp. sensitivity.md's 2.6 s
  / 4860 runs figure).

## Research-agent prompts

Three prompts. **A1 is load-bearing** for §4.7 (wrap-and-patch).
**A2 is load-bearing** for §4.3 (plugin design). **A3 is
low-priority** for §4.5 (headless JDT / OSGi).

### Prompt A1 - IntelliJ vs. Eclipse JDT semantic divergence on refactorings (load-bearing)

> "Architecture-chapter citation hunt. I'm justifying a wrap-and-
> patch layer that closes systematic terminal-AST divergences
> between IntelliJ's refactoring engine and Eclipse JDT's
> refactoring engine (the two largest production Java
> refactoring engines). Specifically, the two engines disagree
> on: (a) whether an extracted method should carry the `static`
> modifier in certain contexts; (b) whether Extract Method on a
> region containing a `return` produces a value-returning method
> with an assignment at the call site, or a void-returning
> method with an inlined return. I want citations.
>
> Specifically:
> (a) Empirical studies comparing the behaviour of IDE
>     refactoring engines on the same input - any pair across
>     {IntelliJ, Eclipse JDT, NetBeans} qualifies. Quantitative
>     measurements of divergence rates would be ideal but
>     anecdotal documented cases are useful.
> (b) Theoretical work on refactoring engine soundness /
>     completeness, especially showing where two
>     formally-correct engines can still diverge.
> (c) Refactoring testing / property-based testing literature
>     that has surfaced these divergences as bugs against either
>     engine.
> (d) Survey papers on Java refactoring tool ecosystems that
>     name the inconsistency between engines as an open issue.
>
> Venues: ICSE, FSE, ASE, TSE, EMSE, MSR, SANER, ICSME, ISSTA,
> JSS, arXiv. 2010-2025. Up to 3 papers, ranked by load-bearing
> strength. If literature is thin, say so confidently - a
> documented 'no peer-reviewed work has compared these engines'
> is itself useful for an honest disclosure paragraph."

### Prompt A2 - IDE event-capture plugins for developer-process research (load-bearing)

> "Architecture-chapter citation hunt. I'm describing an
> IntelliJ plugin that captures editor / file / refactoring /
> build / test / git events into a `events.jsonl` stream for
> downstream analysis. I want prior-art citations on
> instrumented IDE plugins designed for developer-process
> research.
>
> Specifically:
> (a) Plugins / approaches that capture fine-grained IDE-event
>     streams (CodingTracker / CodingSpectator from Negara,
>     Vakilian, Johnson; Watchdog from Beller, Spelman; CodeAware
>     from related lines; recent 2020-2025 successors).
> (b) Studies that *use* such captured event streams to analyse
>     refactoring behaviour, debugging behaviour, or general
>     developer process - so I can position my capture design
>     against theirs.
> (c) Design rationale for events.jsonl-style append-only
>     streaming versus snapshot-based approaches.
>
> Venues: ICSE, FSE, ASE, TSE, EMSE, MSR, SANER, ICSME, IST,
> JSS, arXiv. 2010-2025. Up to 3 papers, ranked by
> load-bearing strength. I already have Murphy-Hill, Parnin &
> Black 2012 'How we refactor and how we know it' cited
> elsewhere - this prompt is about the *plugin engineering* not
> the refactoring observations they produced."

### Prompt A3 - Headless Eclipse JDT / Equinox OSGi for refactoring tooling (low-priority)

> "Quick check. I run Eclipse JDT refactorings headlessly in
> the analysis pipeline by hosting an embedded Equinox OSGi
> framework. This is the standard approach for using JDT
> outside an Eclipse instance, but I'd like one or two
> citations confirming it.
>
> Possibilities:
> (a) Papers / technical reports / documented projects that use
>     JDT headlessly for refactoring (RefactoringMiner uses JDT
>     for diff analysis but not for *applying* refactorings -
>     I want the latter).
> (b) Studies / surveys discussing the OSGi/Equinox bundle
>     approach for embedding JDT.
> (c) Eclipse Foundation documentation that names the OSGi
>     approach as the supported integration path.
>
> Venues: ICSE, FSE, ASE, MSR, SANER, ICSME, plus Eclipse
> Foundation technical reports / blog posts / wiki pages -
> grey literature is acceptable here as JDT-headless is more
> of an engineering practice than an academic topic. Up to 2
> sources. If the answer is 'nobody has written this up',
> that's a useful answer."

## Tone preservation notes

The architecture chapter sits between methodology (formal, with
notation) and results (empirical, with numbers). Defaults:

- Topic-sentence-first paragraphs throughout. Each section's
  opening sentence states the architectural claim or question it
  answers (not just "this section describes...").
- "This thesis's tool" / "the pipeline" / occasional "we" are
  fine; avoid "the author" / "our paper".
- `\texttt{}` for class names and file paths. Use the short form
  (`RefactoringClient`) on first reference, then unqualified
  thereafter.
- Cite specific files using path snippets - readers can jump in
  the IDE. Match methodology style: `\texttt{ReorderSynthesiser.kt}`
  rather than full paths.
- Display math is rare in this chapter; one or two formulas at
  most. Pseudocode listings: prefer compact `algorithm2e` blocks
  only if necessary for §4.6 (the prefix-trie DFS). Most algorithms
  are better as prose with cross-refs to the code.
- One TikZ figure is the chapter's load-bearing visual. Don't add
  more figures unless one is strictly necessary; the chapter
  earns its pages on dense, claim-anchored prose.
- Acknowledge each load-bearing engineering choice's discarded
  alternative honestly (per-Change JDT undo failure in §4.6;
  reflog-watcher failure in §4.3). Transparency is
  rubric-rewarded.

## Verification

1. **Compile cleanly.** `cd final_report && bash build.sh`
   produces both `main.pdf` and `main_dark.pdf` with no new
   undefined-citation or unresolved-reference warnings.
2. **Page count.** Architecture chapter is $\le 10$ pages
   strictly. Target 8.
3. **All forward-ref labels exist.** `\label{ch:architecture}`,
   `\label{sec:arch-phases}`, `\label{sec:arch-plugin}`,
   `\label{sec:arch-pipeline}`, `\label{sec:arch-bundle}`,
   `\label{sec:arch-reorder-engine}`, `\label{sec:arch-wrap-patch}`,
   `\label{sec:arch-dashboard}`, `\label{fig:arch-overview}`.
4. **Methodology wiring.** All five `Chapter~4` placeholder
   strings in `methodology.tex` converted to `\ref{...}` per
   the table above.
5. **TikZ figure renders.** Renders cleanly in *both* light
   and dark builds. Inspect `main_dark.pdf` for legibility.
6. **No dead-module mentions.** Chapter does not reference
   `metrics-core` or `metrics-lambda` (deleted).
7. **No cross-domain duplication.** No methodology content
   leaks into architecture and vice versa. The split test: if
   a paragraph could plausibly be argued either way, it
   belongs in methodology.
8. **Cross-references resolve.** §4.2 cites Chapter~5
   (results); §4.6 reciprocally cited *from* methodology
   §3.6, §3.10. Use placeholder `Chapter~\ref{ch:results}`
   until that chapter is drafted - the warning is acceptable
   if labelled-but-undefined; convert when the results chapter
   ships.
9. **Read aloud.** Every section's opening sentence states an
   engineering claim, not a section preamble.

## What this pass deliberately doesn't do

- **Doesn't repeat methodology content.** Correctness arguments
  (why the algorithms work) stay in methodology; this chapter
  is about *how* the algorithms are made fast.
- **Doesn't preview Chapter 5 experiments.** Only forward-refs
  to the sensitivity / ablation results - no anticipation of
  per-kind precision/recall or saturation findings.
- **Doesn't cover `metrics-core` / `metrics-lambda`.** Those
  modules are deleted; the dead-module section of
  ARCHITECTURE.md is omitted entirely.
- **Doesn't deep-dive into dashboard internals.** §4.8 is half
  a page. The view-model layer, the Zustand store details, the
  trajectory-chart sub-component decomposition all live in
  `ARCHITECTURE.md` and on the dashboard side - they don't
  belong in the thesis.
- **Doesn't include the full end-to-end walkthrough.**
  ARCHITECTURE.md §8 (lines 763-835) is a useful dev-onboarding
  artefact but doesn't earn its space in the thesis. The
  pipeline-stages table in §4.4 plus the dataflow figure in
  §4.1 cover the same ground compactly.
- **Doesn't cover Build & run commands.** ARCHITECTURE.md §9
  is project-onboarding doc, not thesis content.
- **No future-work / deferred-feature material.** Goes to
  Conclusion chapter (item #7 in master plan).

## Estimated effort

2 days of focused writeup once Prompts A1 / A2 / A3 have
returned. Faster than methodology because:

- The source-of-truth doc (ARCHITECTURE.md) is already
  populated and currently accurate (modulo the deleted
  metrics-core/metrics-lambda block).
- Less new prose - several sections are condensations of
  existing ARCHITECTURE.md sections rather than from-scratch
  writeups.
- Fewer citations to verify - methodology had ~17 new bib
  entries; this chapter likely lands 3-5.
- The TikZ figure is the one piece of new visual work; budget
  half a day for it.

---

## Research-agent results: Prompt A1 (completed 2026-05-18)

Web-search agent run for the JDT↔IntelliJ refactoring-engine
divergence citation hunt. Verdict: literature is thin but **three
solid load-bearing citations exist**, plus a documented negative
result that is itself useful for the honest-disclosure paragraph
in §4.7.

### Executive summary

There is a small but growing peer-reviewed literature on Java
refactoring-engine bugs and inter-engine divergence (Wang/Tan
group 2024-2025, Kim et al. IWoR 2019, Soares et al. TSE 2013,
Mongiovi et al. TSE 2018). **No peer-reviewed paper directly
tabulates Extract Method divergences between Eclipse JDT and
IntelliJ IDEA in the specific shapes observed in this codebase**
(static-modifier coin-flip; return-vs-void shape). The closest
analogues are (i) a documented Make-Static parameter-finality
divergence between JDT and IntelliJ in Wang/Xu/Tan FORGE 2025;
(ii) a four-IDE precondition/transformation divergence table for
Move-Instance-Method in Kim et al. IWoR 2019. Both are strong
enough to support the general claim "this kind of inter-engine
divergence is a known phenomenon."

### Citation 1 (STRONG) - Wang, Xu & Tan, FORGE 2025

**Citation.** Haibo Wang, Zhuolin Xu, and Shin Hwei Tan.
"Testing Refactoring Engine via Historical Bug Report driven
LLM." *Proceedings of the 2nd ACM International Conference on AI
Foundation Models and Software Engineering (FORGE 2025)*, 2025.
arXiv:2501.09879.

**Relevance.** Applies **differential testing** between Eclipse
JDT and IntelliJ IDEA refactoring engines on the same generated
input variants and reports 18 new bugs (15 Eclipse, 3 IntelliJ;
7 confirmed, 3 fixed). Documents at least one concrete
inter-engine behavioural divergence: "when performing 'Make
static' refactoring for a method having parameters, IntelliJ
IDEA would declare the parameters as 'final' during refactoring
while Eclipse would not." That is the same-input/different-output
phenomenon the wrap-and-patch layer normalises. The methodology
section frames cross-engine output difference itself as the bug
oracle.

**Category fit.** (a) primary; (c) secondary.

**Load-bearing strength.** **Strong.** The single most directly
applicable citation: it establishes (in a peer-reviewed venue,
FORGE is ACM-sponsored) that JDT↔IntelliJ differential testing
is a legitimate research methodology and that systematic
same-input/different-output divergences exist between exactly
the two engines this thesis straddles. The cited Make-Static
example is closely adjacent to the `static`-modifier finding in
§4.7.

**URL.** https://arxiv.org/abs/2501.09879

### Citation 2 (STRONG) - Wang, Xu, Zhang, Tsantalis & Tan, TOSEM 2025

**Citation.** Haibo Wang, Zhuolin Xu, Huaien Zhang, Nikolaos
Tsantalis, and Shin Hwei Tan. "Towards Understanding Refactoring
Engine Bugs." *ACM Transactions on Software Engineering and
Methodology (TOSEM)*, 2025. DOI: 10.1145/3747289. Preprint:
arXiv:2409.14610 ("An Empirical Study of Refactoring Engine
Bugs").

**Relevance.** First systematic study of refactoring-engine
bugs spanning all three relevant engines - Eclipse JDT (18 years
of bug history), IntelliJ IDEA (8 years), and NetBeans (12
years) - analysing 518 bugs, identifying six root causes, nine
symptoms, and eight input characteristics. Key findings for this
chapter: "Extract" operations account for **28.76% (149 of 518)
of bugs studied** - Extract-family refactorings are the most
error-prone category - and **"Incorrect Transformations" is the
most common root cause for both Eclipse and IntelliJ IDEA**.
That establishes producing wrong/divergent output (as opposed to
crashes) is the dominant failure mode. A transferability
sub-study found 130 new bugs in the latest engine versions,
including bugs originally reported against one engine that
reproduced on another - direct evidence that engines share
input-pattern triggers but diverge in how they handle them.

**Category fit.** (a) strong; (d) strong.

**Load-bearing strength.** **Strong.** The TOSEM venue is
exactly what the thesis needs - a top SE journal stamping the
claim that "refactoring engines disagree, and Extract-family
operations are the worst offenders." Does not give a direct
JDT-vs-IntelliJ tabular comparison of Extract Method outputs
(categorises bugs per engine rather than running same-input
differential tests), so cite it for the macro claim and pair
with Citation 1 for the differential-testing methodology claim.

**URLs.**
- Preprint: https://arxiv.org/abs/2409.14610
- Journal: https://dl.acm.org/doi/10.1145/3747289
- Author copy: https://www.shinhwei.com/tosem25.pdf

### Citation 3 (MODERATE-STRONG) - Kim, Batory & Gligoric, IWoR 2019

**Citation.** Jongwook Kim, Don Batory, and Milos Gligoric.
"Code Transformation Issues in Move-Instance-Method
Refactorings." *Proceedings of the 3rd International Workshop on
Refactoring (IWoR 2019)*, co-located with ICSE 2019, IEEE, 2019.
DOI: 10.1109/IWoR.2019.00012.

**Relevance.** Surveys **four** Java IDE refactoring engines -
Eclipse JDT, IntelliJ IDEA, Apache NetBeans, and Oracle
JDeveloper - applying the same Move-Instance-Method refactoring
across all four and showing that "all of these IDEs implement
different sets of preconditions for MIMR" and "all of these IDEs
may change program behavior due to incorrect code transformation
rules." Includes a comparative table and concrete code-level
examples of same-input/different-output behaviour, with several
Eclipse bug IDs (495899-495907, 424388) referenced as outcomes
of the comparison.

**Category fit.** (a) primary; (c) secondary.

**Load-bearing strength.** **Moderate-to-strong.** The only
peer-reviewed paper found that does a head-to-head, same-input
multi-engine comparison and concludes with a "they all disagree"
table. Catch: it studies *Move Instance Method*, not Extract
Method. So cite it for **structural precedent** - "engines have
been formally shown to diverge on identical inputs even on
classical refactorings; this work is in the same vein for the
divergences observed on Extract Method here." Don't claim it
covers the specific Extract-Method shape; do cite it for the
principle.

**URL.** https://users.ece.utexas.edu/~gligoric/papers/KimETAL19MoveInstanceMethodRefactoring.pdf
(IEEE Xplore: https://ieeexplore.ieee.org/document/8844413/)

### Honest negative result (for §4.7 disclosure paragraph)

To be explicit about what does NOT exist:

- **No peer-reviewed paper documents the specific Extract-Method
  `static`-modifier coin-flip between JDT and IntelliJ** observed
  in this codebase. The closest is the Make-Static
  `final`-parameter divergence in Wang/Xu/Tan FORGE 2025, which
  is a different but kinematically similar phenomenon.
- **No peer-reviewed paper documents the return-vs-void shape
  divergence on Extract Method** specifically. It is treated
  implicitly as part of the "Extract bugs dominate at 28.76%"
  finding in the TOSEM 2025 paper, but no paper isolates it as
  a named divergence.
- The older Soares et al. line of work ("Automated Behavioral
  Testing of Refactoring Engines," *IEEE TSE* 38(2), 2012/2013;
  Mongiovi et al. "Detecting Overly Strong Preconditions in
  Refactoring Engines," *IEEE TSE* 2018) studies Eclipse,
  NetBeans, and JRRT (not IntelliJ), and uses
  behaviour-preservation oracles rather than same-input output
  comparison. Useful as background on "refactoring engines are
  buggy" but **not** load-bearing for inter-engine output
  divergence specifically - don't stretch them for the §4.7
  claim.
- The Brett Daniel et al. WRT 2007 paper ("Automated Testing of
  Eclipse and NetBeans Refactoring Tools") and ASTGen line is a
  workshop paper from 2007 that pre-dates IntelliJ's relevance
  in academic refactoring testing; cite only if historical
  grounding is needed.

### Recommended lean bibliography for §4.7

For §4.7's "wrap-and-patch is justified" paragraph, the
three-citation lean bibliography:

1. **Wang/Xu/Tan FORGE 2025** - "differential testing between
   JDT and IntelliJ is a recognised methodology and produces
   same-input/different-output bugs."
2. **Wang/Xu/Zhang/Tsantalis/Tan TOSEM 2025** - "Extract-family
   refactorings are the dominant bug class and incorrect
   transformations (not crashes) are the dominant symptom across
   both JDT and IntelliJ."
3. **Kim/Batory/Gligoric IWoR 2019** - "multi-IDE same-input
   comparison reveals systematic precondition/transformation
   divergence" (analogical precedent, not the exact refactoring).

The disclosure paragraph in §4.7 can then truthfully state that
no published peer-reviewed paper documents the two specific
Extract-Method divergences in tabular form, motivating the
wrap-and-patch layer as both engineering work and a small
empirical contribution.

### Bib entries to add to `bibs/sample.bib`

Three entries needed:
- `wang2025forge` - Wang/Xu/Tan FORGE 2025 (Make-Static
  `final`-parameter JDT↔IntelliJ divergence; differential
  testing methodology).
- `wang2025tosem` - Wang/Xu/Zhang/Tsantalis/Tan TOSEM 2025
  (518-bug empirical study; Extract dominates at 28.76%;
  Incorrect-Transformations is the top root cause).
- `kim2019iwor` - Kim/Batory/Gligoric IWoR 2019
  (four-IDE Move-Instance-Method comparison; precondition
  divergence table).

Verify each DOI / URL before committing; cross-check the FORGE
2025 paper exists in ACM DL by the time the bibliography audit
(item #8 in `PLAN-thesis-writeup.md`) runs.

---

## Research-agent results: Prompt A2 (completed 2026-05-18)

Web-search agent run for the IDE event-capture plugin prior-art
citation hunt. Verdict: literature is **dominated by a 2014-2018
stratum** (CodingTracker, WatchDog, FeedBaG/KaVE). Three solid
load-bearing citations exist, plus a confident negative result
for 2020-2025: no genuinely new plugin-engineering paper has
landed since FeedBaG; modern work uses VS Code / JetBrains
built-in telemetry and analyses the captured streams rather than
engineering new capture plugins.

### Executive summary

The plugin-engineering question peaked in the **2014-2018
Eclipse/Visual Studio era**. The three strongest citations are
**Negara et al. ICSE 2014** (CodingTracker - fine-grained event
streams used for refactoring-trajectory analysis), **Beller et
al. TSE 2019** (WatchDog - multi-IDE Eclipse+IntelliJ plugin,
the closest engineering analogue to this thesis's plugin), and
**Amann/Proksch/Nadi ICPC 2016 + Proksch et al. MSR 2018**
(FeedBaG/KaVE - the explicit "enriched event stream with code
snapshots" design that directly justifies the JSONL stream +
initial-tree-snapshot hybrid).

The 2014-2018 stratum is dominant. Modern work
(CodeWatcher ICSME 2025) replicates the same architecture on VS
Code/LSP for LLM-interaction studies - useful but weaker as a
citation. Honest framing: lean on the 2014-2018 stratum without
apology; modern industrial telemetry (VS Code built-in, JetBrains
FUS) has effectively absorbed the plugin-engineering question
out of the academic literature.

### Citation 1 (STRONG) - Negara, Codoban, Dig & Johnson, ICSE 2014

**Citation.** Stas Negara, Mihai Codoban, Danny Dig, and Ralph
E. Johnson. "Mining Fine-Grained Code Changes to Detect Unknown
Change Patterns." *Proceedings of the 36th International
Conference on Software Engineering (ICSE '14)*, ACM, 2014,
pp. 803-813. DOI: 10.1145/2568225.2568317.

**Relevance.** Canonical reference for the CodingTracker line
of work. The Eclipse plugin "non-intrusively records
fine-grained and diverse data during code development" -
AST-level edits, automated refactoring invocations, VCS
interactions, and test runs - across 1,652 hours of real
development by 24 developers. The precise prior art for
"instrumented IDE plugin captures a fine-grained, multi-channel
event stream and uses it for refactoring-trajectory analysis."
If only one paper is cited for (a) and (b) together, this is
it. Note: this paper is what Murphy-Hill et al. 2012 built
toward - citing Negara 2014 (the plugin paper) instead of
re-citing Murphy-Hill 2012 (the refactoring observations)
exactly satisfies the "cite the plugin papers, not the
refactoring observations" constraint.

**Category fit.** (a) primary; (b) primary.

**Load-bearing strength.** **Strong** for both (a) plugin
engineering and (b) studies using such streams.

**URLs.**
- ACM DL: https://dl.acm.org/doi/10.1145/2568225.2568317
- Free PDF: http://dig.cs.illinois.edu/papers/ICSE14_Stas.pdf

### Citation 2 (STRONG) - Beller, Gousios, Panichella, Proksch, Amann & Zaidman, TSE 2019

**Citation.** Moritz Beller, Georgios Gousios, Annibale
Panichella, Sebastian Proksch, Sven Amann, and Andy Zaidman.
"Developer Testing in the IDE: Patterns, Beliefs, and
Behavior." *IEEE Transactions on Software Engineering*,
vol. 45, no. 3, 2019, pp. 261-284. DOI: 10.1109/TSE.2017.2776152.

**Relevance.** The most complete write-up of the WatchDog
plugin and the strongest engineering analogue to this thesis's
tool. WatchDog is "a multi-IDE infrastructure that assesses
developer testing activities" implemented as **both an Eclipse
and an IntelliJ plugin**, with 2,443 developers monitored over
2.5 years. Covers exactly the same event categories captured
here (editor activity, file events, test events, build events),
and is multi-IDE, addressing the "why a plugin per IDE
platform" question. For one citation that lets the chapter say
"previous instrumented IDE plugins for developer-behaviour
research" without further justification, this is the strongest.

Companion / antecedent paper if the earlier short version is
preferred: Beller, Gousios, Zaidman. "How (Much) Do Developers
Test?" *ICSE 2015* (poster track, pp. 179-180). The TSE paper
subsumes it.

**Category fit.** (a) primary; (b) secondary.

**Load-bearing strength.** **Strong** for (a) - direct
engineering precedent for an IntelliJ-Platform-SDK + Eclipse
plugin recording a developer-activity stream. **Moderate** for
(b) - analyses test activity, not refactoring, so cite
Negara 2014 for trajectory-analysis prior art and Beller 2019
for the plugin architecture.

**URLs.**
- IEEE Xplore: https://ieeexplore.ieee.org/document/8116886/
- Free PDF: https://inventitech.com/assets/publications/2017_beller_gousios_panichella_amann_proksch_zaidman_developer_testing_in_the_ide_patterns_beliefs_and_behavior.pdf
- Source artefact: https://github.com/TestRoots/watchdog

### Citation 3 (STRONG for (c), MODERATE for (a)) - Amann/Proksch/Nadi ICPC 2016 + Proksch/Amann/Nadi MSR 2018

**Citation (tool paper).** Sven Amann, Sebastian Proksch, and
Sarah Nadi. "FeedBaG: An Interaction Tracker for Visual
Studio." *Proceedings of the 24th International Conference on
Program Comprehension (ICPC '16)*, IEEE, 2016. DOI:
10.1109/ICPC.2016.7503741.

**Citation (dataset paper).** Sebastian Proksch, Sven Amann,
and Sarah Nadi. "Enriched Event Streams: A General Dataset for
Empirical Studies on In-IDE Activities of Software
Developers." *Proceedings of the 15th International Conference
on Mining Software Repositories (MSR '18 - Mining Challenge)*,
ACM, 2018.

**Relevance.** The cleanest citation in the literature for the
**events.jsonl-style stream design plus an initial source-tree
snapshot**. The KaVE/FeedBaG architecture is explicitly
described as "enriched event streams with code snapshots" -
i.e., a continuous timestamped event log enriched by periodic
structural snapshots so the stream can be replayed without
unbounded state reconstruction. That is literally this thesis's
design (live `events.jsonl` appended per event, plus an
`initial-src/` snapshot written once at session start, plus a
final flush to `session.json`). The MSR 2018 dataset paper
documents this for 81 developers, ~11M events, ~15K hours,
providing both a design rationale and a comparison point for
this thesis's event volume.

**Category fit.** (c) primary; (a) secondary.

**Load-bearing strength.** **Strong** specifically for (c)
design rationale - the load-bearing citation for the
"append-only event stream plus snapshot" hybrid choice.
**Moderate** for (a) - Visual Studio rather than IntelliJ, but
the architectural pattern is exactly transferable.

**URLs.**
- ICPC 2016: https://ieeexplore.ieee.org/document/7503741/
  (author copy: http://sven-amann.de/publications/2016-05-ICPC-FeedBaG/)
- MSR 2018: https://2018.msrconf.org/details/msr-2018-Mining-Challenge/3/Enriched-Event-Streams-A-General-Dataset-For-Empirical-Studies-On-In-IDE-Activities-
- Project: https://www.kave.cc/feedbag

### Honest negative result (for §4.3 disclosure paragraph)

The 2014-2018 stratum is dominant. The honest finding for 2020-2025:

- The only genuinely recent paper that fits the
  "plugin engineering for fine-grained event capture" frame is
  **CodeWatcher (Basha et al., ICSME 2025 Tool Demo)** - a VS
  Code extension capturing insertions, deletions, copy-paste,
  and focus events for LLM-interaction studies
  (https://arxiv.org/abs/2510.11536). Architecturally identical
  to WatchDog/FeedBaG but applied to LLM-coding behaviour.
  **Weak-to-moderate** as a citation - useful only as a "the
  architecture continues to be used in 2025" footnote; the
  engineering claims are not novel relative to WatchDog/FeedBaG.
- Beyond CodeWatcher, post-2020 work in this area is
  overwhelmingly **using captured event streams** (e.g.,
  Sergeyuk et al. "Evolving with AI" 2025 longitudinal analysis
  on enterprise IDE telemetry) rather than **engineering new
  plugins for capture**. The academic plugin-engineering
  question genuinely peaked in 2014-2018; modern industrial
  telemetry (VS Code built-in, JetBrains FUS) has eaten the
  engineering question.
- **Defensible chapter framing.** Cite Negara 2014, Beller
  2019, and Proksch/Amann/Nadi 2016+2018 as the
  plugin-engineering prior art; optionally note CodeWatcher
  2025 as confirmation the pattern still holds. Do not pretend
  there is a 2023-2025 IntelliJ-plugin-engineering paper that
  doesn't exist.

### Useful secondary references (optional, if citation room allows)

- **Snipes, Murphy-Hill, Fritz, Vakilian, Damevski, Nair,
  Shepherd.** "A Practical Guide to Analyzing IDE Usage Data."
  Chapter 5 of *The Art and Science of Analyzing Software
  Data*, Morgan Kaufmann, 2015. Methodological reference for
  the Mylyn-style "interaction events written sequentially into
  a log file" pattern with later versions
  compressing/aggregating events - directly relevant if
  discussing the debouncing of keystrokes into `EDIT_BURST`.
  URL: https://www.sciencedirect.com/science/article/pii/B9780124115194000057
- **Negara, Vakilian, Chen, Johnson & Dig.** "Is It Dangerous
  to Use Version Control Histories to Study Source Code
  Evolution?" *ECOOP 2012*. Explicit defence of stream-based
  capture over snapshot/VCS reconstruction; the seminal
  "snapshots lose intermediate state" citation for (c). If
  there's room, add this as a secondary (c) cite alongside
  FeedBaG.
  URL: https://link.springer.com/chapter/10.1007/978-3-642-31057-7_5
- **Negara, Chen, Vakilian, Johnson & Dig.** "A Comparative
  Study of Manual and Automated Refactorings." *ECOOP 2013*,
  pp. 552-576. The companion paper using CodingTracker streams
  specifically for refactoring behaviour - fits (b) if a
  refactoring-specific cite distinct from the ICSE 2014
  pattern-mining paper is wanted.
  URL: https://link.springer.com/chapter/10.1007/978-3-642-39038-8_23
- **Mik Kersten and Gail Murphy.** "Mylyn / Task-Focused
  Interface" (Kersten's UBC thesis, 2007 and subsequent
  papers). Origin point of the "Mylyn Monitor" interaction-
  event stream pattern that everything above descends from.
  Cite only for historical root.

### Recommended §4.3 paragraph structure

Three-citation structure mapping to three sub-claims:

> Instrumented IDE plugins have a well-established prior art
> for capturing fine-grained developer events: notably
> **CodingTracker on Eclipse [Negara et al., ICSE 2014]** and
> **WatchDog as a multi-IDE Eclipse + IntelliJ plugin [Beller
> et al., TSE 2019]** - the latter being the direct
> engineering precedent for our IntelliJ-Platform-SDK design.
> Our choice of an append-only `events.jsonl` stream augmented
> by an initial source-tree snapshot follows the
> **enriched-event-stream architecture established by FeedBaG /
> KaVE for Visual Studio [Amann et al., ICPC 2016; Proksch et
> al., MSR 2018]**, which combines a continuous timestamped
> event log with periodic structural snapshots to enable
> post-hoc session reconstruction without unbounded state
> replay. Recent VS Code work [Basha et al., ICSME 2025]
> confirms the pattern remains standard for fine-grained
> developer-behaviour capture.

This distinguishes the recording side from Murphy-Hill 2012's
observations, lets the chapter mention but not lean on the 2025
work, and gives three load-bearing citations in three clean
positions.

### Bib entries to add to `bibs/sample.bib`

Three primary entries needed:
- `negara2014icse` - Negara/Codoban/Dig/Johnson ICSE 2014
  (CodingTracker; fine-grained event capture; plugin
  engineering prior art).
- `beller2019tse` - Beller/Gousios/Panichella/Proksch/Amann/Zaidman
  TSE 2019 (WatchDog; multi-IDE Eclipse + IntelliJ plugin;
  closest engineering analogue).
- `amann2016icpc` and `proksch2018msr` - the FeedBaG / KaVE pair
  (Visual Studio; enriched-event-stream + initial-snapshot
  design rationale).

Optional secondary entries:
- `basha2025icsme` - CodeWatcher (recent VS Code analogue;
  weak-to-moderate; cite as confirmation the pattern persists).
- `negara2012ecoop` - "Is It Dangerous to Use VCS Histories"
  (explicit snapshot-vs-stream rationale).
- `snipes2015chapter` - Snipes et al. *Art and Science of
  Analyzing Software Data* chapter 5 (methodological reference
  for IDE-usage log analysis; relevant if discussing
  `EDIT_BURST` debouncing).

Verify each DOI / URL before committing; cross-check the ICSME
2025 paper status during the bibliography audit (item #8 in
`PLAN-thesis-writeup.md`).

---

## Research-agent results: Prompt A3 (completed 2026-05-18)

Web-search agent run for the headless Eclipse JDT / Equinox OSGi
citation hunt. Verdict: **confident negative-leaning result**.
The engineering pattern is essentially undocumented in
peer-reviewed venues - treated as engineering plumbing rather
than a research contribution. Two grey-literature citations are
available; the strongest of them is a reference-implementation
existence proof rather than a published paper.

### Executive summary

Embedding Equinox OSGi to host JDT bundles in a non-Eclipse JVM
**for the purpose of applying refactorings** is essentially
undocumented in peer-reviewed venues. The available citations
are **Eclipse Foundation documentation** plus the **Eclipse JDT
Language Server (jdt.ls) reference implementation** maintained
by the Eclipse Foundation itself. The closest peer-reviewed
neighbours (SafeRefactor, JRRT, Schäfer & de Moor OOPSLA'10) all
run inside Eclipse as plugins or build separate refactoring
engines, and explicitly avoid the embedded-Equinox question.

The §4.5 disclosure paragraph can frame the engineering choice
defensively without claiming novelty.

### Citation 1 (MODERATE) - Eclipse JDT FAQ (Eclipse Foundation wiki)

**Citation.** Eclipse Foundation. *JDT/FAQ - Eclipsepedia.*
Eclipse Wiki. (Continuously updated; consulted 2026-05-18.)

**Relevance.** The canonical Eclipse-Foundation statement of
headless JDT usage. Direct quote: *"JDT Core has no dependency
on UI side, however it requires a runtime-workbench. Hence you
can use it in an Eclipse headless application or include all
the dependent jar files in the class path of your application."*
This distinguishes the two supported headless paths: (i)
bare-classpath usage for AST/parser/formatter, or (ii) running
inside an Eclipse-style (i.e. OSGi/Equinox) runtime-workbench
for anything workspace-dependent. Refactorings, which depend on
`IJavaProject`/workspace state and the LTK refactoring
framework, fall in the second bucket.

**Category fit.** (c) primary; (b) partial.

**Load-bearing strength.** **Moderate.** Names "runtime-
workbench" (i.e. Equinox-hosted) as the path for
workspace-dependent features but does not spell out "embed
Equinox in your JVM via `EclipseStarter`/`FrameworkFactory`" in
those exact words.

**URL.** https://wiki.eclipse.org/JDT/FAQ

### Citation 2 (STRONG, but grey literature) - Eclipse JDT Language Server (jdt.ls)

**Citation (newsletter article).** Gorkem Ercan et al. *Eclipse
JDT Language Server Project.* Eclipse Newsletter, May 2017.

**Citation (reference implementation).**
`eclipse-jdtls/eclipse.jdt.ls`. GitHub. Eclipse Foundation
project, ongoing.

**Relevance.** The newsletter article explicitly describes
jdt.ls as *"a small, headless Eclipse JDT distribution,
providing comprehensive Java support to a wide array of
clients"* that *"will initialize an Eclipse IDE workspace under
the hood."* The GitHub launch instructions show the canonical
invocation pattern using `org.eclipse.equinox.launcher`,
`-Dosgi.bundles.defaultStartLevel=4`, `eclipse.application`,
and `eclipse.product` system properties - i.e. the standard
embedded-Equinox bootstrap. The README's feature list now
includes *"Code actions (quick fixes, source actions &
refactorings)"*, so jdt.ls is a working,
Eclipse-Foundation-blessed example of "host Equinox in your
JVM, load JDT bundles, expose refactorings to an external
client" - precisely this thesis's architecture pattern. (The
2017 newsletter article describes refactoring as "future"; the
present-day README confirms refactorings are now exposed.)

**Category fit.** (a) primary; (b) primary.

**Load-bearing strength.** **Strong** (within grey literature).
The de facto reference for the pattern. If a reviewer pushes
back on "standard / supported path," jdt.ls is the existence
proof maintained by the Eclipse Foundation itself.

**URLs.**
- Newsletter: https://www.eclipse.org/community/eclipse_newsletter/2017/may/article4.php
- Reference implementation: https://github.com/eclipse-jdtls/eclipse.jdt.ls

### Honest negative result (for §4.5 disclosure paragraph)

To be explicit:

- **No peer-reviewed paper** (ICSE/FSE/ASE/MSR/SANER/ICSME,
  2010-2025) documents embedding Equinox to run JDT
  refactorings in a non-Eclipse JVM. The pattern is treated as
  engineering plumbing.
- Refactoring-correctness work (SafeRefactor by Soares et al.
  SBES'09 / TSE'13, JRRT by Schäfer & de Moor OOPSLA'10,
  Systematic Testing of Refactoring Engines by Gligoric et al.)
  all runs inside Eclipse as plugins or builds a separate
  refactoring engine - none documents the embedded-Equinox-in-a-
  non-Eclipse-JVM pattern. Do not cite them for the §4.5 claim.
- Personal blog posts and small GitHub projects exist (jmini's
  "JDT without Eclipse" 2020-01-17; `kaluchi/jdtbridge` 2024-25)
  but neither is load-bearing for the architecture chapter.

### Honourable mentions (do not cite; context only)

- **jmini, "JDT without Eclipse" (personal blog, 2020-01-17).**
  Useful corroboration of the FAQ's two-mode framing but does
  not cover refactoring application.
  URL: https://jmini.github.io/blog/2020/2020-01-17_jdt-without-eclipse.html
- **`kaluchi/jdtbridge` (GitHub, 2024-2025).** Eclipse plugin
  exposing JDT SearchEngine, refactoring, and diagnostics over
  HTTP. Recent micro-example of the same pattern, but as a
  plugin rather than embedded Equinox. Weak as a citation.

### Optional grey-literature footnote (third anchor if needed)

The Equinox Launcher documentation page
(https://equinox.eclipseprojects.io/launcher/equinox_launcher.html)
and the Eclipse Equinox project page
(https://projects.eclipse.org/projects/eclipse.equinox)
together formally name `org.eclipse.equinox.launcher` as the
supported bootstrap for hosting Eclipse bundles in a JVM. Cite
as a footnote only if the FAQ + jdt.ls pair isn't enough.

### Recommended §4.5 paragraph framing

Defensive phrasing that grounds the engineering choice without
claiming novelty:

> "Hosting Eclipse JDT outside an Eclipse IDE for workspace-
> dependent operations such as refactoring requires an OSGi
> runtime-workbench [Eclipse JDT FAQ]; the embedded-Equinox
> approach used here mirrors the architecture of the Eclipse
> JDT Language Server, which is itself characterised by its
> maintainers as 'a small, headless Eclipse JDT distribution'
> [Eclipse Newsletter 2017; eclipse-jdtls/eclipse.jdt.ls].
> The pattern is documented in Eclipse Foundation grey
> literature and reference implementations but not, to the
> author's knowledge, in any peer-reviewed venue."

The final sentence (honest disclosure of the no-peer-reviewed-
coverage result) is rubric-rewarded.

### Bib entries to add to `bibs/sample.bib`

Two grey-literature entries:
- `eclipse_jdt_faq` - Eclipse Foundation JDT/FAQ wiki page
  (use `@misc` with `howpublished = {Eclipse Wiki}` and
  consulted-date).
- `eclipse_jdtls` - jdt.ls (combine the 2017 newsletter article
  + the GitHub project as one `@misc` entry, or split into two
  entries if the citation style prefers).

Optional footnote anchors (`equinox_launcher_docs`,
`equinox_project_page`) only if FAQ + jdt.ls aren't sufficient.

Verify URLs before committing; the old Eclipse forum thread on
"JDT refactoring from command line" now returns a
service-shutdown page (verified 2026-05-18) - do not cite it.

---

## All three citation hunts complete

Status of the three research-agent prompts:

- **A1** (JDT↔IntelliJ refactoring divergence) - **complete**;
  3 STRONG citations + documented negative result. See
  `wang2025forge`, `wang2025tosem`, `kim2019iwor`.
- **A2** (IDE event-capture plugin prior art) - **complete**;
  3 STRONG citations + confident "2014-2018 stratum dominant"
  negative result. See `negara2014icse`, `beller2019tse`,
  `amann2016icpc`/`proksch2018msr`.
- **A3** (headless JDT / Equinox OSGi) - **complete**;
  confident negative-leaning result; 2 grey-literature anchors
  (`eclipse_jdt_faq`, `eclipse_jdtls`); no peer-reviewed
  coverage exists.

Ready for the writeup phase. Total of 8 primary bib entries to
add when the chapter is drafted (3 + 3 + 2), plus a small number
of optional secondaries.
