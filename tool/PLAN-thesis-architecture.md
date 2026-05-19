# Plan: Tool Architecture chapter pass (v2 - novel-content-first)

## Context

The Tool Architecture chapter is item #4 in `PLAN-thesis-writeup.md`
(estimated 2 days). Per `HONEST_REVIEW_2.md` §"Tool architecture
chapter":

> *"Keep brief; reuse ARCHITECTURE.md. The Phase A / Phase B split
> is the key engineering claim to surface here, with one diagram."*

**Plan revision (v2) - focus on novel content.** The original v1
plan gave equal weight to every module / pipeline stage, producing
a balanced-tour chapter where Phase A / Phase B and the JDT
engineering sat alongside ~3 pages of routine architecture prose.
This pass reweights toward the algorithmically novel pieces and
compresses the routine sections. Four "most interesting" pieces
get the spotlight:

1. **Refactor synthesis via JDT** - embedded Equinox, batch
   sessions, wrap-and-patch layer.
2. **Reorder synthesis via dependency-DAG + prefix-trie + DFS** -
   the chapter's headline algorithmic section.
3. **Rework detection: content hashing + line-number translation
   across non-adjacent SHAs**.
4. **Shadow git repo reconstruction + worktree pool** (brief).

Phase A / Phase B stays - HONEST_REVIEW_2 specifically named it -
but compresses from 1.5 pages to 1 page. Plugin and dashboard
compress to ~1 page combined.

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

**Page budget: 8 target, 10 hard cap** (unchanged from v1 - the
reweighting moves pages from §§4.1/4.7/4.8 into §§4.3/4.4/4.5,
but the total budget stands).

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

## Chapter outline (v2 - with page budget)

| § | Section                                                        | Pages | Novel? |
|---|----------------------------------------------------------------|------:|:------:|
| 4.1 | Architecture at a glance (diagram + compressed module roles)  | 0.75  | -      |
| 4.2 | The Phase A / Phase B split                                   | 1     | -      |
| 4.3 | **Reorder synthesis: spec-effects DAG, SSA versioning, prefix-trie DFS, git-checkout backtracking** | **2** | ★      |
| 4.4 | **Refactoring application: embedded Equinox, JDT batch sessions, wrap-and-patch** | **1.5** | ★      |
| 4.5 | **Rework detection and synthesis: content hashing + line-number translation across non-adjacent SHAs** | **1.25** | ★      |
| 4.6 | Shadow repo reconstruction and worktree pool                  | 0.5   | ★      |
| 4.7 | Plugin event capture and pipeline overview (compressed)       | 0.75  | -      |
| 4.8 | Dashboard (paragraph)                                         | 0.25  | -      |
| **Total** |                                                          | **~8** |        |

Sections marked ★ are the algorithmically novel content the
chapter focuses on. §§4.3-4.6 combined = ~5.25 pages (versus
~3.5 in v1); §§4.1/4.7/4.8 combined = ~1.75 pages (versus ~3.5
in v1).

## Per-section detail (v2)

### §4.1 Architecture at a glance (0.75 page)

`\label{ch:architecture}` at the chapter top.

Compressed from v1's 1.5-page module overview. Single
topic-sentence paragraph identifying the four JVM modules
(`:ide-plugin`, `:analysis`, `:refactoring-bundle`, `:shared`)
plus the standalone React app (`dashboard/`), followed by the
**TikZ dataflow figure** (see "TikZ diagram approach" further
below). The figure carries most of the orientation weight;
per-module prose drops to one-sentence bullets:

- **`ide-plugin/`** - IntelliJ plugin recording the session.
- **`analysis/`** - Kotlin pipeline; ingests sessions, builds a
  shadow git repo, mines and synthesises trajectories, emits an
  `AnalysisReport`.
- **`refactoring-bundle/`** - headless Eclipse JDT in an embedded
  Equinox framework; details in §4.4.
- **`dashboard/`** - React/TypeScript presentation surface;
  details in §4.8.
- **`shared/`** - cross-wire types (`Session`, `TraceEvent`,
  `SessionMetadata`, `FileSnapshot`, `EventType`).

One closing sentence on schema codegen: Kotlin `@Serializable`
types are source-of-truth, TypeScript types are generated via the
`:analysis:generateDashboardTypes` Gradle task.

### §4.2 The Phase A / Phase B split (1 page)

`\label{sec:arch-phases}`.

Compressed from v1's 1.5 pages. Still a load-bearing claim per
`HONEST_REVIEW_2.md`, but the page weight is now equal to (rather
than dominant over) the novel-algorithm sections.

Topic sentence: the analysis pipeline is structured as two phases
- an **expensive Phase A** that produces a serialisable
`PhaseAResult`, and a **cheap Phase B** that assembles an
`AnalysisReport` from the `PhaseAResult` plus a `ScoringConfig`.

Body:
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
  caches the *raw* checkpoint metrics, not the scored output;
  Phase B is where weights become process-score numbers. So
  perturbing weights cannot invalidate any Phase A artefact.

Close with a forward-reference to the Chapter~5 experiments that
exploit this property. Cut the v1 closing dashboard-iteration
paragraph; that material moves to §4.8.

### §4.3 Reorder synthesis: spec-effects DAG, SSA versioning, prefix-trie DFS, git-checkout backtracking (2 pages)

`\label{sec:arch-reorder-engine}`.

**The chapter's headline algorithmic section.** Carries methodology
§3.6, §3 preamble, and §3.10 forward-refs. Topic sentence: a
reorder window of $k$ refactoring steps has up to $k!$ candidate
permutations, and each ordering must be applied against the same
project state. Four engineering choices turn the search from
exponential into a worst-case product traversal.

Structure as a numbered exposition with one paragraph per stage:

1. **Spec-effects dependency DAG**
   (`SpecDependencyAnalyzer.kt`). Each refactoring step declares
   the `Entity`s it reads, writes, produces, or consumes (full
   collision table in `alternative/reorder/README.md`). The
   analyser builds pairwise must-come-before edges; symmetric
   collisions (produces/produces, consumes/consumes) become
   *invalidations* rather than edges, because those orderings are
   unreachable regardless of position. The DAG defines the
   permutation lattice the search must respect.

2. **SSA versioning across renames**
   (`SpecVersioner.kt`). Pre-passes the window and threads a
   version id through every named entity so that a method renamed
   from `foo` to `bar` at step 2 is recognised as the *same*
   logical entity in steps that reference `bar` later. Without
   SSA, a rename + later-rename-of-bar would look like two
   independent operations on the same name and produce wrong
   dependency edges.

3. **Topological enumeration with budget**
   (`TopologicalEnumerator.kt`). Backtracks all valid orderings
   under an `EnumerationBudget`. The user's actual ordering is
   filtered out (it's already the reference trace). The budget
   prevents pathological windows from running away.

4. **Prefix-trie DFS with git-checkout backtracking**
   (`PrefixTrie.kt`). Every alternative ordering is folded into a
   trie; the DFS walks it so each unique prefix is materialised
   exactly once. The two key engineering plays are below.

After the four-paragraph algorithmic exposition, drop into the
**three engineering choices** that make the trie walk fast (these
are the bits methodology §3.6 + §3.10 forward-ref):

- **Single borrowed worktree.** A single worktree is borrowed at
  `windowFromSha` via `WorktreePool.borrow()` for the entire
  reorder window. Forward edges and back edges reuse the same
  on-disk checkout.
- **Single JDT batch session.** One `client.withBatchSession`
  is opened for the entire window. The batch session keeps the
  indexed `IJavaProject` alive across every `invokeOnBundle`
  call inside the window, so each subsequent refactoring sees a
  warm project model. The amortisation is significant:
  cold-start JDT init/index per refactoring would dominate the
  search wall-clock.
- **Git-checkout backtracking.** Back edges in the DFS use
  `worktreeGit.checkoutDetach(parentSha)` + `client.refreshProject()`
  to roll the worktree back. The `refreshProject()` call goes
  through Eclipse's `IResource.refreshLocal(...)` path - the same
  "files changed under us" pathway IDE users exercise constantly
  - so JDT re-stats the on-disk files cleanly.

Then the **discarded-alternative paragraph** for the back-edge
mechanism: an earlier attempt used JDT's per-`Change` undo. It
didn't work - per-file JDT caches (working-copy buffers +
JavaModel element info) didn't reliably invalidate, producing
corrupt commits on the next forward apply. Git-checkout +
`refreshProject()` is the standard pathway and works.

Close with the **terminal AST-equivalence audit**
(`JavaFileAstHasher`): at every leaf of the trie, the synthesiser
hashes the user-changed `.java` set in the worktree and compares
to the precomputed `windowToSha` hashes. Divergent terminals are
filtered out of the report (the commits + refs persist in the
shadow repo for offline forensics). One sentence noting the
validator (`RefactoringStepValidator`) reuses these three
engineering choices for bracket replay - that lands methodology
§3.10's forward-ref - and that the validator computes the user's
reference AST hashes via `git show` piped into the hasher rather
than maintaining a second worktree.

### §4.4 Refactoring application: embedded Equinox, JDT batch sessions, wrap-and-patch (1.5 pages)

`\label{sec:arch-jdt}` (umbrella). Inside this section,
`\label{sec:arch-wrap-patch}` for the wrap-and-patch subsection
so methodology §3.5.2 and §3.6 window-split forward-refs land
precisely.

Topic sentence: applying real Java refactorings outside Eclipse
requires three engineering pieces - hosting JDT in an embedded
OSGi runtime, amortising its project model across calls, and
patching over the systematic differences between JDT and
IntelliJ's terminal output.

**§4.4.1 Embedded Equinox boundary** (~0.5 page).
- Why headless JDT: JDT is the only Java refactoring engine with
  the breadth (~25 operations: Extract / Inline / Move / Rename
  / Pull-Up / Push-Down etc.) and the maturity to serve as
  ground truth. JDT was designed to run inside Eclipse; the
  standard headless solution is to host an embedded Equinox OSGi
  framework. (Note: A3 research confirmed this pattern has no
  peer-reviewed documentation - cite Eclipse JDT FAQ + jdt.ls
  reference implementation.)
- The OSGi boundary: `refactoring/RefactoringClient.kt` boots an
  Equinox `Framework` at startup, loads the bundle JAR, resolves
  `JdtRefactorer` via reflection, holds the instance for process
  lifetime. Per refactoring, `invokeOnBundle(name, paramTypes,
  args)` invokes the bundle method under a `ReentrantLock` and
  parses a JSON-serialised outcome. **JSON-as-wire-format** keeps
  Eclipse classes from crossing the classloader boundary - every
  bundle-internal type stays inside OSGi.
- Anchor resolution: source-text-based selection is brittle.
  JDT-side ops resolve the target element from a `(line, column,
  subtreeHash)` triple. `JavaFileAstHasher` computes the same
  hash on the host side so spec construction and bundle-side
  resolution agree.

**§4.4.2 Batch-session amortisation** (~0.25 page).
`RefactoringHost.withBatchSession` keeps the indexed
`IJavaProject` alive across consecutive `invokeOnBundle` calls
on the same `projectRoot`. The host-side `client.withBatchSession
{ body() }` acquires the client lock reentrantly, tells the
bundle to keep the project cache after each call, runs `body`
(every inner call sees a warm `IJavaProject`), and tears down
the cache in `finally`. This is what §4.3 leans on for the
reorder DFS - one batch session for the whole window means k
applies pay k × per-call cost instead of k × (init + per-call).

**§4.4.3 The wrap-and-patch layer** (~0.5 page).
`\label{sec:arch-wrap-patch}`. Even when IntelliJ and Eclipse
JDT agree on the *semantics* of a refactoring, they sometimes
produce different terminal ASTs. Two real divergences (verified
against codebase + git history; cite A1 research findings):

- **`static` modifier divergence.** JDT and IntelliJ disagree on
  whether the post-extract method should carry the `static`
  modifier in certain contexts. Captured in
  `RefactoringSpec.kt`'s `isStatic` comment. The wrap layer
  inspects the user's terminal AST and post-patches the bundle's
  output to align. (Cite Wang/Xu/Tan FORGE 2025 for the
  analogous Make-Static `final`-parameter divergence between
  the same two engines.)
- **Return-vs-void shape on Extract Method.** When the extracted
  region contains a `return`, JDT and IntelliJ disagree on
  whether the new method should return a value (call site
  assigns to a local) or return void (call site bears an inlined
  return). Captured by commit `6005505 ExtractMethod: post-extract
  rewrite for void-return + inlined-return shapes`. A small
  manual git patch closes the delta after the JDT operation
  completes.

Honest disclosure (load-bearing for §3.6 window-split): where
the residual gap cannot be patched safely, the bracket gets the
`AST_DIVERGED` verdict and is not reordered. The wrap-and-patch
layer expands the set of `VALID` brackets but doesn't fully
bridge the two engines. Cite Wang/Xu/Zhang/Tsantalis/Tan TOSEM
2025 for the macro claim (Extract-family is 28.76% of
refactoring-engine bugs; "Incorrect Transformations" is the top
root cause) and note no peer-reviewed paper documents the
specific Extract-Method divergences this thesis observes.

### §4.5 Rework detection and synthesis: content hashing + line-number translation (1.25 pages)

`\label{sec:arch-rework}` (new section, not in v1 plan).

Topic sentence: REWORK detection identifies user steps where a
chunk of code is added at one step and removed at a later step
(or vice versa) in the *same* logical scope - but those two
steps may be separated by many intervening edits that have
shifted line numbers and rewritten neighbouring content. Two
engineering pieces make this tractable: content-addressed chunk
hashing for detection, and a per-file drift tracker for
synthesis.

**§4.5.1 Content-addressed chunk detection** (~0.5 page).
`ReworkDetector.kt` is a pure function over a list of `StepInput`
records (each step's unified diff + the touched files'
pre/post contents). The algorithm:

1. **Hunk extraction.** `HunkExtractor.kt` parses each step's
   unified-diff text into added-runs and removed-runs per file,
   per hunk.
2. **Scope resolution.** `EnclosingScopeResolver.kt` walks the
   pre- or post-state Java source and identifies the
   method-level (or class-level) scope that contains each run.
   The scope id (`"file.java#class.method"`) becomes part of the
   chunk's identity.
3. **Normalisation and hashing.** Each run's lines are
   normalised (trim trailing whitespace, drop pure-whitespace
   lines) and hashed (SHA-256) to a content-addressed chunk key.
   Normalisation matters: it absorbs cosmetic reformatting
   between the two appearances of the chunk.
4. **Greedy pairing within `(file, scopeId, contentHash)`.**
   Each chunk participates in at most one pair. Earliest
   originating ↔ earliest later opposite-side. Single-line
   chunks are filtered out by default
   (`DEFAULT_MIN_NORMALIZED_LINE_COUNT = 2`) because two identical
   one-line statements in the same scope are
   content-indistinguishable and pairing would attribute the
   wrong instances.

The detection step is **purely pairwise across content hashes** -
no temporal-locality assumption between originating and terminal
steps. Two steps separated by 30 intervening edits can pair if
their hashes match.

**§4.5.2 Drift tracking for surgical synthesis** (~0.5 page).
Once a `ChunkPair` is identified, the rework alt-trajectory has
to apply the surgical patch at the originating step and have it
"translate forward" through all intermediate user edits to land
correctly at the terminal step. `ReworkDriftTracker.kt` does
this translation.

The tracker represents per-file divergence between the user's
tree and the surgically-replayed (alt) tree as a list of
**zones**:

- **ADDED zone.** `K` user lines starting at `userStartLine`
  that have no synth equivalent (surgery dropped them at the
  originating step). User lines past the zone shift by $-K$ in
  synth coords.
- **REMOVED zone.** `K` synth lines that have no user equivalent
  (the user removed them at the originating step but surgery
  preserved them). User lines at or past the zone shift by $+K$
  in synth coords.

Zones are registered when surgery happens at step $k'$ and
cleared when surgery reverses them at the terminal step $k$ (the
two trees converge again). Between $k'$ and $k$, each
intermediate step applies hunks to the user tree, and the
tracker shifts zones whose `userStartLine` is past the applied
hunk by the hunk's net line delta. This keeps each zone anchored
at the same *logical content* even as the user tree grows or
shrinks around it.

**§4.5.3 Closing point** (~0.25 page).
Pure-Kotlin state machinery, no git or filesystem - which is what
makes both the detector and the drift tracker easy to unit-test
exhaustively. The same `ChunkPair` records flow into
`ReworkAlternativeBuilder` for the actual patch synthesis (cited
in methodology §3.8) and into `DivergencePoint` records for the
report.

The reverted-line count surfaced on the dashboard
(`reworkLineCount`) comes from this section's
`normalizedLineCount` field, summed per
`(originatingStep, terminalStep, file, scopeId)`.

### §4.6 Shadow repo reconstruction and worktree pool (0.5 page)

`\label{sec:arch-shadow}` (new section, brief).

Topic sentence: every algorithm in §§4.3-4.5 needs to check out
historical project states at will. The shadow repo + worktree
pool are the infrastructure that makes that cheap.

Body:
- **Shadow repo reconstruction.** `ShadowRepoBuilder.kt` replays
  the normalised event stream into a fresh git repo under
  `<sessionDir>/shadow-repo/`, one commit per event.
  No-op events (e.g. `EDIT_BURST` with zero net diff against the
  previous snapshot) alias the previous SHA, so the unique-SHA
  count is typically much smaller than the event count. Output:
  `ReconstructionResult` carrying the repo path and `eventCommits`
  (event id → SHA). This is what gives every downstream stage a
  stable `(fromSha, toSha)` pair to check out.
- **`WorktreePool.kt`**. A small pool of linked worktrees off the
  shadow repo. Borrowers (validator, reorder synthesiser,
  IDE-replay runner) `borrow()` a worktree at a target SHA, run
  their operation, and return it. Multiple concurrent borrowers
  use distinct worktrees; the JDT lock in
  `RefactoringClient.invokeOnBundle` serialises actual bundle
  calls, so pool parallelism mostly overlaps cheap local work
  (AST hashing, git diff invocation).
- The MetricsRunner deliberately bypasses the pool: it pins a
  single persistent worktree and `git checkout`s sequentially
  through every SHA, because Gradle daemon caches amortise far
  better in one worktree than across N. This is the only
  sequential stage in the pipeline.

### §4.7 Plugin event capture and pipeline overview (0.75 page)

`\label{sec:arch-plugin}`. Compressed from v1's 1 page + the
analysis pipeline 1-page tour, now combined.

Topic sentence: the recording side and the pipeline-stage tour
both fit here because neither is the chapter's novel content.

**Plugin event capture** (~0.4 page). One bullet list of the
listeners (`EditorEventListener`, `FileEditorListener`,
`VfsListener`, `FileSaveListener`, `RefactoringListener` +
command/template variants, `BuildListener`, `TestRunListener`,
`ProjectCloseListener`). Then two short paragraphs on the two
genuinely interesting engineering choices, with research
backing:

- **Edit burst tracking.** `EditBurstTracker.kt` debounces
  keystroke `DocumentEvent`s into one `EDIT_BURST` per file per
  quiet window. **The flush reads the in-memory document text,
  not the on-disk file** - so the recorded snapshot reflects
  what the user typed regardless of auto-save / on-save
  formatters. (Cite Negara et al. ICSE 2014 for fine-grained
  event capture; Beller et al. TSE 2019 for multi-IDE plugin
  architecture; Amann et al. ICPC 2016 for enriched-event-stream
  design rationale.)
- **Git commit watching.** `GitCommitWatcher.kt` is a polling
  thread (one second cadence), not a reflog watcher. The reflog
  approach was abandoned because fresh `git init` repos may
  disable it. Honest disclosure of the design rationale.

**Pipeline-stage tour** (~0.35 page). A compact 12-row table
(same as v1's §4.4):

| # | Stage | Output |
|---|-------|--------|
| 1 | Load + normalise (`TraceLoader`, `TraceNormalizer`) | ordered event stream |
| 2 | Shadow repo reconstruction (`ShadowRepoBuilder`) | shadow-repo + event→SHA map |
| 3 | Refactoring mining (`RefactoringMinerRunner` + `BracketSpecReorderer`) | typed `RefactoringSpec` list |
| 4 | Validation (`RefactoringStepValidator`) | per-step verdicts |
| 5 | Reorder synthesis (`ReorderSynthesiser`) | alt trajectories |
| 6 | Single-step IDE replay (`AlternativeTrajectoryRunner`) | single-step alts |
| 7 | Metrics (`MetricsRunner`) | per-SHA checkpoint metrics |
| 8 | Diffs (`DiffsRunner` + `PatchFilter`) | unified-diff patches |
| 9 | PMD violation tracking (`PmdTrackingRunner`) | first/resolved-at-SHA |
| 10 | CPD duplication tracking (`CpdTrackingRunner`) | clone-group tracking |
| 11 | Derived metrics (`DerivedMetricsRunner`) | cleanliness + process score |
| 12 | Report assembly (`buildAnalysisReport()`) | `AnalysisReport` |

Two-sentence closing paragraph naming entry points (`cli/Main.kt`
local; `server/Main.kt` + `AnalyzeRoute.kt` HTTP).

### §4.8 Dashboard (0.25 page)

`\label{sec:arch-dashboard}`. Compressed from v1's 0.5 page to a
single paragraph + a closing sentence.

The dashboard is a single-page React/TypeScript app
(React 18 + TypeScript + Vite + Tailwind + Zustand) that consumes
the `AnalysisReport`. Two report-loading paths: plugin (the
IntelliJ tool window assigns `window.__REPORT__` and dispatches
a `refdash:report-loaded` event) and dev (a bundled
`analysis-report.json` imported at build time, used by
`useReport.ts`). Schema codegen described in §4.1; no
hand-written TypeScript types.

One closing sentence: the dashboard is a presentation surface,
not an engineering contribution - the load-bearing engineering
of this chapter is §§4.3-4.5.

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

Once `architecture.tex` defines its labels, convert the five
methodology forward-refs in `methodology/methodology.tex`. All
label targets are defined in v2 - just at different sections than
v1:

| Line | Current text | Target (v2) |
|------|--------------|-------------|
| 6 | `... is covered in Chapter~4 (Tool Architecture).` | `... is covered in Chapter~\ref{ch:architecture} (\S\ref{sec:arch-reorder-engine}).` |
| 181 | `... are described in Chapter~4 (Tool Architecture).` | `... are described in \S\ref{sec:arch-wrap-patch}.` |
| 196 | `... are described in Chapter~4.` | `... are described in \S\ref{sec:arch-reorder-engine}.` |
| 199 | `The wrap-and-patch layer described in Chapter~4 ...` | `The wrap-and-patch layer described in \S\ref{sec:arch-wrap-patch} ...` |
| 249 | `... are described in Chapter~4.` | `... are described in \S\ref{sec:arch-reorder-engine}.` |

The `sec:arch-reorder-engine` and `sec:arch-wrap-patch` labels
now live in §4.3 and §4.4.3 (was §4.6 / §4.7 in v1) - the
forward-refs still resolve cleanly.

Verify the LaTeX compile produces no unresolved references and
the rendered chapter cross-refs read naturally.

## Source-material map (v2)

| Architecture section | Primary ARCHITECTURE.md source | Primary code |
|---------------------|--------------------------------|--------------|
| §4.1 Architecture at a glance | §1 (lines 21-101)         | `settings.gradle.kts`; module `build.gradle.kts` files |
| §4.2 Phase A / Phase B     | (not in ARCHITECTURE.md; derive from code) | `pipeline/AnalysisPipeline.kt`; `pipeline/PhaseAResult.kt`; `pipeline/ReportAssembler.kt`; `cli/PhaseACli.kt`; `cli/PhaseBCli.kt` |
| §4.3 Reorder synthesis      | §3.A (lines 344-444)      | `alternative/synthesise/ReorderSynthesiser.kt`, `PrefixTrie.kt`; `alternative/reorder/SpecDependencyAnalyzer.kt`, `SpecVersioner.kt`, `TopologicalEnumerator.kt`; `alternative/validate/RefactoringStepValidator.kt`; `JavaFileAstHasher.kt`; `metrics/WorktreePool.kt` |
| §4.4 JDT integration + wrap-and-patch | §5 (lines 625-707) + (derive from code + git log for wrap-and-patch) | `refactoring/RefactoringClient.kt`, `refactoring/RefactoringClientFactory.kt`; `refactoring-bundle/.../JdtRefactorer.kt` + `RefactoringHost.kt`; `RefactoringSpec.kt` (isStatic); commit `6005505` (Extract Method post-extract rewrite) |
| §4.5 Rework detection + drift | (not in ARCHITECTURE.md; derive from code) | `alternative/rework/ReworkDetector.kt`, `ReworkDriftTracker.kt`, `HunkExtractor.kt`, `EnclosingScopeResolver.kt`, `ReworkAlternativeBuilder.kt` |
| §4.6 Shadow repo + worktree pool | §3 (lines 247-280)    | `reconstruct/ShadowRepoBuilder.kt`; `metrics/WorktreePool.kt`; `metrics/MetricsRunner.kt` |
| §4.7 Plugin + pipeline tour | §2 (lines 105-224) + §3 (lines 247-343) | `ide-plugin/.../services/`, `.../listeners/`, `.../actions/`; `pipeline/AnalysisPipeline.kt` |
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

## Verification (v2)

1. **Compile cleanly.** `cd final_report && bash build.sh`
   produces both `main.pdf` and `main_dark.pdf` with no new
   undefined-citation or unresolved-reference warnings.
2. **Page count.** Architecture chapter is $\le 10$ pages
   strictly. Target 8.
3. **All forward-ref labels exist.** Updated for v2:
   `\label{ch:architecture}` (§4.1),
   `\label{sec:arch-phases}` (§4.2),
   `\label{sec:arch-reorder-engine}` (§4.3),
   `\label{sec:arch-jdt}` (§4.4),
   `\label{sec:arch-wrap-patch}` (§4.4.3 subsection),
   `\label{sec:arch-rework}` (§4.5),
   `\label{sec:arch-shadow}` (§4.6),
   `\label{sec:arch-plugin}` (§4.7),
   `\label{sec:arch-dashboard}` (§4.8),
   `\label{fig:arch-overview}` (figure).
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
   (results); §4.3 reciprocally cited *from* methodology
   §3.6, §3.10. Use placeholder `Chapter~\ref{ch:results}`
   until that chapter is drafted - the warning is acceptable
   if labelled-but-undefined; convert when the results chapter
   ships.
9. **Read aloud.** Every section's opening sentence states an
   engineering claim, not a section preamble.
10. **Novel-content weight check.** §§4.3-4.5 (the three
    ★-marked sections) account for ≥ 4.5 of the 8 pages.
    §§4.1/4.7/4.8 combined should not exceed 2 pages. If the
    routine sections balloon during writing, compress them
    before letting the chapter creep past 10.

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

## Estimated effort (v2)

2-2.5 days of focused writeup; A1/A2/A3 prompts already returned
(see results blocks further down). Faster than methodology
because:

- The source-of-truth doc (ARCHITECTURE.md) is already
  populated and currently accurate (modulo the deleted
  metrics-core/metrics-lambda block).
- Two of the three ★ sections (§4.3 reorder, §4.4 JDT) port
  from existing ARCHITECTURE.md material with the new emphasis
  woven in; only §4.5 (rework) is from-scratch writeup against
  `ReworkDetector.kt` + `ReworkDriftTracker.kt`.
- Citations already inventoried (8 primary entries: 3 from A1
  + 3 from A2 + 2 from A3).
- The TikZ figure is the one piece of new visual work; budget
  half a day for it.

The reweighting from v1 doesn't cost extra time - it's the same
total prose budget, just redistributed.

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
