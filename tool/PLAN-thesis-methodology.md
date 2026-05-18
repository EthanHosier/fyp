# Plan: Methodology chapter pass

## Context

The methodology chapter is the single highest-leverage chapter per
`HONEST_REVIEW_2.md` — it's where the thesis's three named
contributions (process score, divergence-point detector, per-kind
synthesisers) are formally defined and defended. Per
`PLAN-thesis-writeup.md` this is the 1-week item #3.

**Page budget: 12 target, 14 hard cap.** Verify against course
page limit before commencing.

Algorithm/correctness angle lives here; engineering/performance
angle (JDT batch session, git-checkout backtracking) lives in the
Tool Architecture chapter.

Sources are all in place:
- **Code:** `ScoringConfig.kt`, `DerivedMetricsRunner.kt`,
  `DivergencePointBuilder.kt`, the four detector files
  (`ReworkDetector.kt`, `HygieneDetector.kt`,
  `IdeRefactoringsRunner.kt`, plus reorder-as-implicit-via-synth),
  the four synthesiser files (`ReorderSynthesiser.kt`,
  `IdeRefactoringsRunner.kt`, `ReworkSynthesiser.kt`,
  `HygieneDetector.kt` builders), `RefactoringStepValidator.kt`,
  `JavaFileAstHasher.kt`.
- **Research notes:** `RESEARCH-metrics-weighting.md`,
  `RESEARCH-cleanliness-metrics.md`,
  `plans/PLAN-metric-justification.md`.
- **Empirical anchors:** `explained_results/ablation.md` (per-term
  recovery numbers cross-referenced into §3.3 table).

**Pre-step:** create `final_report/methodology/methodology.tex` and
wire it into `main.tex` between `background` and `project_plan`
inputs.

## Chapter outline (with page budget)

| § | Section                                          | Pages |
|---|--------------------------------------------------|------:|
| 3.1 | Formal definitions and notation                  | 1     |
| 3.2 | The process score: formula and design rationale  | 1     |
| 3.3 | Process-score weights: per-term breakdown (table)| 2     |
| 3.4 | The cleanliness sub-score                        | 1.5   |
| 3.5 | Divergence-point detector (common framework + 4 kinds × ½ pg) | 2 |
| 3.6 | Reorder synthesiser (algorithmic core)           | 2     |
| 3.7 | MANUAL_REFACTOR synthesiser                           | 0.5   |
| 3.8 | REWORK synthesiser                               | 0.5   |
| 3.9 | HYGIENE synthesisers                             | 0.5   |
| 3.10 | Validator and behaviour preservation            | 0.5   |
| 3.11 | Rater design (STUDY-BLOCKED TODO SLOT)          | 0.5–1 |
| **Total** |                                             | **~12** |

## Per-section detail

### §3.1 Formal definitions and notation (1 page)

- One paragraph recapping the state/action/trace formalism (already
  introduced in Background §2.1.3 — don't repeat the POMDP linkage,
  just reference it).
- Then define the analysis-report structure formally: a trajectory
  is paired with per-step DerivedMetrics, a set of DivergencePoints,
  and a set of AlternativeTrajectories.
- Notation table at the end of the section (see "Notation
  conventions" below).

### §3.2 The process score: formula and design rationale (1 page)

The process score $J(\tau)$ is the load-bearing object of the
chapter. From `DerivedMetricsRunner.kt:1491–1496`:

$$J(\tau) = \mathrm{clip}_{[0,100]}\Big(\beta + W_{\textsc{g}}\,\Delta C(\tau) - W_{\textsc{b}}\,r_b(\tau) - W_{\textsc{st}}\,r_{st}(\tau) - W_{\textsc{mi}}\,r_{mi}(\tau) - W_{\textsc{cg}}\,n_{cg}(\tau) + W_{\textsc{l}}\,\sigma_l(\tau)\Big)$$

where $\beta = 50$ is the baseline anchor, $\Delta C$ is cleanliness
gain, $r_*$ are smoothed rate terms, $n_{cg}$ is commit-gap event
count, and $\sigma_l$ is the alt-only step-savings bonus.

Design rationale paragraph covering:
- Anti-gaming axiom: $W_{\textsc{b}}$ must dominate any single-step
  gain (severity ordering 28 : 14 : 11 : 11 : 7 across penalty
  terms).
- Laplace smoothing on rate terms: prevents 1-bad-out-of-1 events
  from triggering full penalty.
- $[0, 100]$ clamp: makes the score comparable across trajectories
  of different length, but introduces saturation (disclose).
- Forward-ref to §3.3 (weights) and §5.X (sensitivity + ablation).

### §3.3 Process-score weights: per-term breakdown (2 pages)

The headline table is a 6-row × 4-column compact reference:

| Term | Default weight | Cited justification | Ablation Δ recovery (solo, 1-term active) |
|------|---------------:|---------------------|------------------------------------------:|
| $W_{\textsc{g}}$ (gain)        | 50 | Cedrim 2017; Paixão 2018           | 0.494 |
| $W_{\textsc{b}}$ (broken)      | 28 | Paixão 2018; Gautam 2025 (RefactorBench failure-mode #2) | **0.725** |
| $W_{\textsc{st}}$ (skipTests)  | 14 | Murphy-Hill, Parnin & Black 2012   | 0.597 |
| $W_{\textsc{mi}}$ (manualIde)  | 11 | *[citation hunt — see Prompt M1]*  | 0.636 |
| $W_{\textsc{l}}$ (length)      | 11 | Mkaouer 2016; Ouni 2017            | 0.680 |
| $W_{\textsc{cg}}$ (commitGap)  | 7  | *[citation hunt — see Prompt M2]*  | 0.533 |

The "Ablation Δ recovery" column previews results §5.X — markers
reward the methodology-to-evaluation cross-reference. Make the
column visually subdued (e.g. grey-tinted) so it doesn't dominate
the reader's eye away from the design rationale.

Approximately ½ page of prose after the table covering:
- Why the severity ratio 28 : 14 : 7 (W_BROKEN : W_SKIP_TESTS :
  W_COMMIT_GAP) — anti-gaming, evidence-strength ordering.
- Why W_BROKEN is time-weighted (brokenMs/elapsedMs) rather than
  binary — connects directly to the RefactorBench failure-mode #2
  cross-cite already prepped in Background §2.5.
- Why $W_{\textsc{l}}$ is alt-only (bonus, not penalty) — the
  step-savings of a synthesised alt over the user is a legitimate
  positive signal; penalising user-trajectory length would
  conflate scope with quality.
- Acknowledge the W_MANUAL_IDE and W_COMMIT_GAP citation gaps
  honestly. Even if Prompts M1/M2 find citations, keep the
  axiomatic defence visible — the chapter's credibility comes from
  transparency about which choices are literature-backed vs which
  are first-principles.

### §3.4 The cleanliness sub-score (1.5 pages)

A per-metric methodology table (6 rows, similar compactness to §3.3):

| Sub-metric | Weight | Cited basis | Computation source |
|------------|------:|-------------|--------------------|
| Cognitive complexity | 1.0 | Campbell 2018 *[Prompt M3 if third-party validation exists]* | Per-method mean |
| Coupling (CBO)        | 1.0 | Chidamber & Kemerer 1994 | CK library, per-class mean |
| Duplication           | 1.0 | Heitlager 2007; Fowler 1999 | CPD-detected cloned lines on touched files |
| Readability           | 1.0 | Buse & Weimer 2010 (taxonomy only — *not* trained model) | Five-feature blend, line-weighted |
| Smells                | 1.0 | Marinescu 2004; Arcelli Fontana 2017; Moha 2010 (DECOR) | PMD violations on touched files |
| Cohesion (TCC)        | 1.0 | Bieman & Kang 1995 | Per-class mean, dropped if <2 methods |

Composition formula:
- Min-max normalisation across the trajectory's range per sub-metric
  (inverted for "lower is better" signals — cognitive, coupling,
  duplication, smells).
- Missing sub-metrics dropped; remaining weights renormalised
  (Laplace's principle of insufficient reason — uniform weighting
  when no calibration data exists, citing Wagner 2015 Quamoco and
  Bansiya & Davis 2002 QMOOD as layered-aggregation precedent).
- Alt-trajectory checkpoint values clamped to [0, 1] against main
  trajectory's range — prevents an extreme alt from shifting
  normalisation of the user's trajectory.
- ¼ page disclosing the readability and cognitive-complexity caveats
  visibly (we use Buse & Weimer's feature taxonomy but not their
  trained model; Campbell 2018 is a white paper, not peer-reviewed).

### §3.5 Divergence-point detector (2 pages)

**§3.5.1 Common framework (~½ page).** Define `DivergencePoint` data
class structure formally (kind, magnitude, stepIndex, per-kind
extras). Define magnitude semantics:
- For ORDERING / MANUAL_REFACTOR / HYGIENE: magnitude = alt's process
  score − user's process score at the anchor checkpoint.
- For REWORK: magnitude = reverted-line count (weight-independent;
  disclose this asymmetry honestly).
- $\mathcal{K} = \{\textsc{ordering}, \textsc{manual\_refactor}, \textsc{rework}, \textsc{hygiene}\}$.

Detection-vs-synthesis split: detection emits a candidate (with
context); synthesis builds the alt trajectory; magnitude is
computed afterwards. The `MIN_*` thresholds gate detection:
- `MIN_REWORK_LINES = 2` (drops line-noise).
- `minCommitGap = 6` (≥6 green-refactor checkpoints without
  commit triggers one COMMIT_GAP penalty event).
- `compositeGapMs = 60_000 L` (Murphy-Hill 2012 batch boundary).

**§3.5.2 ORDERING detection (~½ page).** Per `BracketSpecReorderer`
+ `ReorderSynthesiser`: groups consecutive refactoring steps within
a bracket-validated window, enumerates permutations, computes
process score per permutation, emits a DP if any permutation
strictly beats the user.

**§3.5.3 MANUAL_REFACTOR detection (~½ page).** Per `IdeRefactoringsRunner`:
surfaces refactoring steps where `wasPerformedByIde = false` AND a
non-null IDE-mappable spec exists. Groups by `(fromSha, toSha)`
bracket.

**§3.5.4 REWORK detection (~½ page).** Per `ReworkDetector`:
chunk-level add-then-remove and remove-then-add detection via
unified-diff hunk parsing, scope resolution (method-level + class-
level), content hashing, greedy pairing within `(file, scope, hash)`.

**§3.5.5 HYGIENE detection (~½ page).** Per `HygieneDetector`: two
sub-kinds. TESTS_SKIPPED fires once per untested composite (60s
Murphy-Hill batches); COMMIT_GAP fires once per ≥6 green-refactor
checkpoints without commit. Both emit one DP per finding.

### §3.6 Reorder synthesiser — algorithmic core (2 pages)

This is the chapter's longest single algorithm. Page allocation:

1. **Window-split.** From validator verdicts: only VALID brackets
   become reorder windows. AST_DIVERGED / REFACTOR_FAILED brackets
   are split into singletons (so reordering is impossible).
2. **Dependency DAG construction.** `SpecDependencyAnalyzer` reads
   each step's spec effects (what entities it reads / writes /
   creates / deletes) and builds an edge from $a$ to $b$ if $b$
   depends on $a$'s effects.
3. **SSA versioning.** `SpecVersioner` handles renamed entities by
   threading version IDs across the DAG (a method renamed at step
   2 from `foo` to `bar` becomes the same SSA entity for purposes
   of subsequent steps reading it).
4. **Topological enumeration.** `TopologicalEnumerator` generates
   all topological orderings of the DAG. Each is a candidate
   permutation.
5. **DFS over prefix trie.** Permutations share prefixes; the DFS
   reuses the JDT project state across shared prefixes, only
   re-applying the divergent suffix.
6. **Terminal AST-equivalence audit.** `JavaFileAstHasher` hashes
   every touched .java file at the user's terminal SHA and at the
   permutation's terminal worktree state. Only permutations whose
   terminal hashes match the user's are admitted as legitimate
   reorderings of the same end state.

Forward-reference to §4.X (architecture) for: how the single
borrowed worktree + single `withBatchSession` reuse + git-checkout
backtracking make the DFS tractable. The *correctness* defence
(steps 1–6 above) lives here; the *performance* defence lives in
architecture.

### §3.7 MANUAL_REFACTOR synthesiser (½ page)

Per `IdeRefactoringsRunner` synthesis path: borrow worktree at
`fromSha`, apply each detected spec via `SpecDispatcher.apply` in
miner emission order within one `withBatchSession`. Residual 3-way
merge then layers the user's unrelated edits atop the IDE-replayed
refactoring. Commit all to shadow repo.

Key correctness point: the residual merge means the alt trajectory
preserves the user's non-refactoring edits (so we're comparing
*how the user got there* rather than *whether they got there*).

### §3.8 REWORK synthesiser (½ page)

Per `ReworkSynthesiser`: for each detected (originating, terminal)
chunk pair, `ReworkAlternativeBuilder.plan()` generates per-step
surgical patches over the window. `GitRunner.applyDirect` applies
each, commit, branch-force refs. Atomic per trajectory.

Key correctness point: the surgical replay preserves all
non-reworked content; the magnitude (reverted-line count) measures
exactly the wasted work, weight-independently.

### §3.9 HYGIENE synthesisers (½ page)

Per `HygieneDetector` builders:
- **TESTS_SKIPPED counterfactual.** Flip `tests.wasSkipped = false,
  tests.success = true` on the anchor checkpoint; recompute the
  process score. The alt-trajectory's code state is identical to
  the user's; the magnitude shows what the W_SKIP_TESTS penalty
  was costing.
- **COMMIT_GAP counterfactual.** Flip `isUserCommit = true` on the
  anchor checkpoint; identical code state. **Disclosure: COMMIT_GAP
  magnitude is currently 0** because cadence isn't in the score
  formula directly (it's a counted-event penalty, not a continuous
  signal). Document this honestly — the detector surfaces the
  pattern even when the formula can't measure it.

Defend the metric-flag-flip approach: HYGIENE is fundamentally a
*process* problem (when did you run tests / commit), not a
*code-state* problem. Counterfactual via flag flip is the honest
representation of what would change.

### §3.10 Validator and behaviour preservation (½ page)

Per `RefactoringStepValidator`:
- Groups steps by `(fromSha, toSha)` bracket.
- Replays all specs in bracket order on one borrowed worktree
  inside one `withBatchSession`.
- Computes user's changed `.java` set via `git diff`; hashes user's
  post-state ASTs.
- Verdict: VALID if changed-file-set matches and all AST hashes
  match. AST_DIVERGED if hashes mismatch. REFACTOR_FAILED if
  apply throws.

Forward-ref to §4.X (architecture) for the engineering choices
that make this fast.

### §3.11 Rater design (STUDY-BLOCKED TODO SLOT) (½–1 page)

To be filled when `PLAN-user-study.md` Phase 1 lands. Will cover:
- Schema definition (manifest-v2.csv multi-label format).
- Briefing protocol for the two independent raters.
- Cohen's κ per kind, reported honestly.

Leave a `% TODO study-blocked` comment in the LaTeX source.

## Source-material map

| Methodology section | Primary code | Primary doc |
|--------------------|--------------|-------------|
| §3.1 Notation     | `AnalysisReport.kt` | Background §2.1.3 (recap) |
| §3.2 Formula       | `DerivedMetricsRunner.kt:1491–1496` + `ScoringConfig.kt:6–16` | `RESEARCH-metrics-weighting.md` |
| §3.3 Weight table  | `ScoringConfig.kt:6–16` | `RESEARCH-metrics-weighting.md` + `ablation.md` (recovery numbers) |
| §3.4 Cleanliness   | `DerivedMetricsRunner.kt:560–606` + `ScoringConfig.kt:19–26` | `RESEARCH-cleanliness-metrics.md` |
| §3.5 Detector      | `DivergencePointBuilder.kt` + per-kind detector files | `divergence.md` methodology section |
| §3.6 Reorder synth | `ReorderSynthesiser.kt` + `SpecDependencyAnalyzer` + `SpecVersioner` + `TopologicalEnumerator` + `JavaFileAstHasher` | `divergence.md` |
| §3.7 MANUAL_REFACTOR    | `IdeRefactoringsRunner.kt` | (no doc source — derive from code) |
| §3.8 REWORK        | `ReworkSynthesiser.kt` + `ReworkAlternativeBuilder` | (code) |
| §3.9 HYGIENE       | `HygieneDetector.kt` (builders) | (code) |
| §3.10 Validator    | `RefactoringStepValidator.kt` | (code) |

## Notation conventions (table at end of §3.1)

| Symbol | Meaning |
|--------|---------|
| $\tau = (s_0, a_0, s_1, \ldots, a_{T-1}, s_T)$ | Refactoring trajectory |
| $s_t$ | State (code snapshot) at step $t$ |
| $a_t$ | Action (IDE refactoring or manual edit) at step $t$ |
| $J(\tau)$ | Process-quality score, clamped to $[0, 100]$ |
| $C(s)$ | Cleanliness sub-score of state $s$ |
| $\Delta C(\tau) = C(s_T) - C(s_0)$ | Cleanliness gain across trajectory |
| $\beta = 50$ | Baseline anchor in the process score |
| $W_{\textsc{x}}$ | Process-score weight for term $\textsc{x}$ |
| $r_b, r_{st}, r_{mi}$ | Smoothed rate terms (broken, skipTests, manualIde) |
| $n_{cg}$ | Commit-gap event count |
| $\sigma_l(\tau)$ | Step-savings bonus for alt trajectories |
| $\mathrm{DP}$ | Divergence point |
| $\tau^{\ast}$ | Counterfactual reference trajectory |
| $\mathcal{K}$ | Divergence-kind set $= \{\textsc{ordering}, \textsc{manual\_refactor}, \textsc{rework}, \textsc{hygiene}\}$ |

## Cross-cite to architecture chapter

Methodology covers correctness; architecture covers engineering
performance. Explicit forward-references in the methodology:

- §3.6 reorder synthesiser → §4.X for JDT single-batch-session +
  git-checkout backtracking that makes the DFS tractable.
- §3.10 validator → §4.X for the same engineering choices applied
  to bracket replay.

Both forward-refs use placeholder `Chapter~4` until the
architecture chapter's labels exist; convert to `\ref{...}` at the
end of the writeup.

## Research-agent prompts

Three prompts. M1 and M2 are load-bearing for §3.3. M3 is
low-priority for §3.4.

### Prompt M1 — W_MANUAL_IDE citation hunt (load-bearing)

> "Methodology-chapter citation hunt. I'm justifying a weight in a
> refactoring-trajectory process-quality metric that penalises
> manually-performed transformations relative to IDE-supported
> ones. I need citations on the *reliability/safety* advantage of
> IDE-supported refactoring over manual edits.
>
> Specifically:
> (a) Empirical studies comparing fault rates / breakage rates /
>     defect injection rates between IDE-refactored and
>     hand-refactored code.
> (b) Theoretical work on refactoring tool correctness guarantees
>     (precondition checking, behaviour preservation under static
>     approximation, soundness of refactoring engines).
> (c) Developer-behaviour surveys on why developers use vs avoid
>     IDE refactor menus (and what goes wrong when they don't use
>     them).
>
> Venues: ICSE, FSE, ASE, TSE, EMSE, MSR, SANER, ICSME, IST, JSS,
> arXiv. Up to 3 papers, ranked by load-bearing strength. If the
> literature is thin (or all 2010s+ and the recent work has
> stagnated), say so explicitly — I have a documented axiomatic
> fallback so a confident 'no recent citations' is useful."

### Prompt M2 — W_COMMIT_GAP citation hunt (load-bearing)

> "Methodology-chapter citation hunt. I'm justifying a weight that
> penalises long stretches of green-refactor checkpoints without
> committing. I need citations supporting the value of small /
> frequent / atomic commits, particularly during refactoring.
>
> Keywords: 'atomic commits', 'commit granularity', 'commit
> cadence', 'frequent commits', 'rollback cost', 'review cost
> granularity', 'commit untangling'.
>
> Of particular interest:
> (a) Empirical studies showing review-cost or rollback-cost
>     reductions with smaller / more-frequent commits.
> (b) Refactoring-specific literature on commit segmentation
>     (e.g. tangled-change studies — Herzig 2013 is already in my
>     bib for tangled commits but may not directly support the
>     *frequency* argument).
> (c) Any work tying commit cadence to defect rates / mean-time-
>     to-recovery / review velocity.
>
> Venues: ICSE/FSE/ASE/MSR/TSE/EMSE/IST/JSS/arXiv 2015–2025. Up to
> 3 papers. If thin, say so."

### Prompt M3 — Cognitive complexity third-party validation (low-priority)

> "Quick check. Campbell's 2018 cognitive complexity (SonarSource
> white paper) is not peer-reviewed. Looking for third-party
> empirical validations showing cognitive complexity predicts
> understandability / maintenance effort better than cyclomatic
> complexity. Up to 2 papers. If the only validations are
> SonarSource-affiliated, say so."

## Tone preservation notes

The methodology chapter sits between intro (slightly informal) and
background (formal-academic). Defaults:

- Topic-sentence-first paragraphs throughout.
- "This thesis" / occasional "we" is fine. Avoid "the author" /
  "our paper".
- Pseudo-formulas in display math (`\[...\]` or `equation`
  environment). No inline math beyond simple symbols.
- Algorithmic listings: prefer compact `algorithm2e` blocks over
  prose-buried steps. Keep each algorithm to ≤ ½ page.
- Tables (§3.3 weights, §3.4 cleanliness, §3.1 notation) are the
  load-bearing artefacts. They earn their pages.
- Acknowledge each documented gap explicitly — readability caveat
  (we use Buse & Weimer's taxonomy but not their model), cognitive
  complexity caveat (Campbell 2018 is a white paper), W_MANUAL_IDE
  / W_COMMIT_GAP citation gaps if Prompts M1/M2 return thin.
  Transparency is rubric-rewarded.

## Critical files

- `/Users/ethanhosier/Desktop/random/fyp/final_report/methodology/methodology.tex`
  — **new file** to create.
- `/Users/ethanhosier/Desktop/random/fyp/final_report/main.tex`
  — add `\input{methodology/methodology.tex}` between background
  and project_plan inputs.
- `/Users/ethanhosier/Desktop/random/fyp/final_report/bibs/sample.bib`
  — add the following new citations (most need verification before
  committing):
  - Cedrim et al. 2017 (refactoring outcome empirical study)
  - Paixão et al. 2018 (refactoring disruption)
  - Mkaouer et al. 2016 (many-objective refactoring)
  - Ouni et al. 2017 (refactoring objective formulations)
  - Bieman & Kang 1995 (TCC cohesion)
  - Chidamber & Kemerer 1994 (CK metrics suite)
  - Heitlager, Kuipers & Visser 2007 (SIG maintainability model)
  - Buse & Weimer 2010 (readability)
  - Marinescu 2004 (smell detection)
  - Arcelli Fontana et al. 2017 (smell detector evaluation)
  - Wagner et al. 2015 (Quamoco quality model)
  - Bansiya & Davis 2002 (QMOOD)
  - Campbell 2018 (cognitive complexity — white paper)
  - Verdecchia et al. 2022 (composite-score weight criticism)
  - Plus any new citations from Prompts M1, M2, M3.
- Reference (read-only): all the code files in the source-material
  map; `HONEST_REVIEW_2.md`; `PLAN-thesis-writeup.md`.

## Verification

1. **Compile cleanly.** `cd final_report && bash build.sh` — no new
   undefined-citation warnings.
2. **Page count.** ≤ 14 pages strictly. Target 12.
3. **Citation completeness.** Each weight in §3.3 has either a
   citation OR an explicit "axiomatic, gap disclosed" footnote.
   Each cleanliness signal in §3.4 has either a citation OR an
   explicit caveat.
4. **Cross-reference integrity.** Forward-refs to §4 architecture
   and §5 results compile (placeholder `Chapter~4` /
   `Chapter~5` until labels exist, convert to `\ref` at end).
5. **TODO slot present.** `% TODO study-blocked` comment in §3.11.
6. **Read aloud.** Each section's topic sentence states the
   question it answers (not just "this section describes...").
7. **Tables earn their pages.** §3.3 weight table is the
   chapter's centrepiece; should be referenced from §3.2 and from
   §5.X (results).

## What this pass deliberately doesn't do

- **JDT batch-session engineering details.** Goes to architecture
  chapter.
- **Phase A / Phase B engineering structure.** Goes to architecture
  chapter.
- **Sub-result anticipation.** Don't pre-quote the divergence
  experiment's per-kind precision/recall here; that lives in
  results §5.X. The §3.3 ablation cross-reference column is the
  one allowed exception (it's a forward-reference, not a result
  duplication).
- **Full pseudocode for cleanliness sub-score per-metric
  computation.** A formula and a 1-sentence-per-metric description
  is sufficient; full algorithms go to architecture or appendix.
- **Threats to validity preview.** Goes to the threats-to-validity
  chapter (#6 in master plan).
- **User-study results paragraphs.** Only the rater-design
  paragraph (§3.11) lands here; everything else study-related is
  in the results chapter, threats chapter, or conclusion.

## Estimated effort

5 days of focused writeup once Prompts M1 / M2 / M3 have returned
(plan calls for 1 week per master plan; with the source map
already populated and the structure detailed above, the actual
LaTeX-ification should be faster than for chapters where the
source material was less ready).

---

## Research-agent results: Prompt M1 (W_MANUAL_IDE citation hunt)

### Executive summary

**Three papers found** — two STRONG, one MEDIUM. The literature
went quiet around 2013–2014; the **direct differential claim
"IDE-refactored code has lower fault rates than hand-refactored
code" is NOT directly measured anywhere** in the published
literature. Available citations support the *premise* (manual
refactoring is prevalent in the wild; refactorings induce bugs)
but not the *exact differential*. This means the axiomatic
severity-ordering defence in `RESEARCH-metrics-weighting.md` is
doing real load-bearing work — flag this honestly in §3.3.

### Fits

**[NEGARA-2013]** Negara, S., Chen, N., Vakilian, M., Johnson,
R.E., Dig, D. *A Comparative Study of Manual and Automated
Refactorings.* ECOOP 2013, LNCS 7920, pp. 552–576.
DOI: 10.1007/978-3-642-39038-8_23.

- **What it does:** Mines 5,371 refactorings from 23 developers
  (1,520 hours of IDE telemetry); quantitatively compares manual
  vs. automated refactoring practice.
- **Touches:** (a) + (c). Headline finding: even when an automated
  refactoring is available, developers prefer to do it manually
  — manual refactorings dominate in the wild.
- **Why it matters here:** Justifies the *premise* of the
  W_MANUAL_IDE penalty. Manual refactoring is prevalent enough to
  be a meaningful process-quality signal, not an edge case. Cite
  in §3.3 as the empirical anchor.
- **Strength of fit:** **STRONG**.

**[VAKILIAN-2012]** Vakilian, M., Chen, N., Negara, S., Rajkumar,
B.A., Bailey, B.P., Johnson, R.E. *Use, Disuse, and Misuse of
Automated Refactorings.* ICSE 2012, pp. 233–243.
DOI: 10.1109/ICSE.2012.6227190.

- **What it does:** Mixed-methods study (IDE telemetry + 11
  interviews) on why developers invoke, configure, mis-apply, or
  skip IDE refactorings. Documents *misuse* — cases where
  developers used automation but still introduced
  behaviour-changing or buggy results.
- **Touches:** (c), and (a) via the misuse subset.
- **Why it matters here:** Lets §3.3 say *"even tool-supported
  refactorings have a non-zero correctness cost, but manual ones
  bypass precondition checking entirely"* — justifies a *graded*
  penalty rather than a binary one.
- **Strength of fit:** **STRONG**.

**[BAVOTA-2012]** Bavota, G., De Carluccio, B., De Lucia, A.,
Di Penta, M., Oliveto, R., Strollo, O. *When Does a Refactoring
Induce Bugs? An Empirical Study.* SCAM 2012, pp. 104–113.
DOI: 10.1109/SCAM.2012.20.

- **What it does:** SZZ-based study of 15,008 refactorings across
  Apache Ant, Xerces, ArgoUML; identifies which refactoring kinds
  (especially hierarchy-related) are most fault-inducing.
- **Touches:** (a), partially.
- **Caveat:** does **not** split by manual vs. IDE-applied — treats
  all RefFinder-detected refactorings uniformly.
- **Why it matters here:** Cite as evidence that refactorings
  *can* induce bugs at non-trivial rates, motivating why *any*
  refactoring activity warrants quality tracking — but be explicit
  that the manual/automated split is **not** established by this
  paper.
- **Strength of fit:** MEDIUM.

### Calibration finding (use this in §3.3 prose)

The specific claim "IDE-refactored code has lower fault rates than
hand-refactored code" **is not directly measured** in the published
literature. Closest existing work:

- **Bavota 2012** measures fault-inducing refactorings overall.
- **Negara 2013** measures *frequency* of manual vs. automated.
- **Vakilian 2012** measures *misuse* of automated tools.
- The closest direct differential is the Kim/Gligoric line
  (RefDistiller ICSM 2012, systematic testing of refactoring
  engines), which surprisingly argues the *automated* side is also
  buggy.

The line went quiet around 2013–2014 and has not been revisited
with a head-to-head fault-rate study. **The axiomatic
severity-ordering defence in `RESEARCH-metrics-weighting.md` is
therefore defensible** — the literature supports the premise
(manual is prevalent + refactorings induce bugs) but not the
exact differential claim. Flag this in §3.3 prose: *"the
W_MANUAL_IDE weight rests on the prevalence finding of Negara et
al. 2013 and the precondition-checking argument from Mens & Tourwé
2004 together with the severity-ordering axiom; we are aware of
no published study that has directly measured the fault-rate
differential between manually-performed and IDE-supported
refactorings."*

### How to use this in §3.3

**Plan-of-record update for §3.3 table row 4 (W_MANUAL_IDE):**

| Term | Default weight | Cited justification | Ablation Δ recovery |
|------|---------------:|---------------------|--------------------:|
| $W_{\textsc{mi}}$ (manualIde) | 11 | Negara 2013; Vakilian 2012; (Bavota 2012 for refactoring-induced bug rates generally) | 0.636 |

§3.3 prose addition (~3–4 sentences):

> *"The W_MANUAL_IDE weight penalises steps where a developer
> performed a transformation by hand that the IDE could have
> applied automatically. Negara et al. 2013 establish that this is
> a common case — across 23 developers and 5,371 refactorings,
> developers preferred manual performance even when automated
> equivalents were available [cite Negara 2013]. The reliability
> argument rests on Mens \& Tourwé's framing of refactoring tools
> as enforcing static precondition checking [cite Mens 2004];
> Vakilian et al. 2012 nuance this by showing that even automated
> refactorings can be misused, motivating a graded (rather than
> binary) penalty for the manual case [cite Vakilian 2012]. We
> are aware of no published study that has directly measured the
> fault-rate differential between manually-performed and
> IDE-applied refactorings; the specific weight value is therefore
> axiomatic in the severity-ordering sense (Section 3.3)
> rather than empirically fitted, and is validated by the
> sensitivity and ablation experiments in Chapter 5."*

### Action items

- **Add three new bib entries:** `negara2013manualautomated`,
  `vakilian2012usedisuse`, `bavota2012induce`. Suggested:

  ```bibtex
  @inproceedings{negara2013manualautomated,
    title     = {A Comparative Study of Manual and Automated Refactorings},
    author    = {Negara, Stas and Chen, Nicholas and Vakilian, Mohsen and Johnson, Ralph E. and Dig, Danny},
    booktitle = {Proceedings of the 27th European Conference on Object-Oriented Programming (ECOOP)},
    series    = {LNCS},
    volume    = {7920},
    pages     = {552--576},
    year      = {2013},
    publisher = {Springer},
    doi       = {10.1007/978-3-642-39038-8_23}
  }

  @inproceedings{vakilian2012usedisuse,
    title     = {Use, Disuse, and Misuse of Automated Refactorings},
    author    = {Vakilian, Mohsen and Chen, Nicholas and Negara, Stas and Rajkumar, Balaji Ambresh and Bailey, Brian P. and Johnson, Ralph E.},
    booktitle = {Proceedings of the 34th International Conference on Software Engineering (ICSE)},
    pages     = {233--243},
    year      = {2012},
    publisher = {IEEE},
    doi       = {10.1109/ICSE.2012.6227190}
  }

  @inproceedings{bavota2012induce,
    title     = {When Does a Refactoring Induce Bugs? An Empirical Study},
    author    = {Bavota, Gabriele and De Carluccio, Bernardino and De Lucia, Andrea and Di Penta, Massimiliano and Oliveto, Rocco and Strollo, Orazio},
    booktitle = {Proceedings of the 12th International Working Conference on Source Code Analysis and Manipulation (SCAM)},
    pages     = {104--113},
    year      = {2012},
    publisher = {IEEE},
    doi       = {10.1109/SCAM.2012.20}
  }
  ```

- **Update §3.3 weight table** to swap the `[citation hunt]`
  placeholder for `Negara 2013; Vakilian 2012`.
- **Add the calibration-finding sentence** to the §3.3 prose
  block — *"we are aware of no published study that has directly
  measured the fault-rate differential..."* This is rubric-rewarded
  transparency.

### Sources

1. https://link.springer.com/chapter/10.1007/978-3-642-39038-8_23
   — Negara et al. 2013 (Springer)
2. http://dig.cs.illinois.edu/papers/ECOOP13-NegaraETAL-Inferring.pdf
   — PDF (Negara group)
3. https://dblp.org/rec/conf/icse/VakilianCNRBJ12.html — Vakilian
   2012 (dblp)
4. https://ieeexplore.ieee.org/document/6227190/ — Vakilian 2012
   (IEEE)
5. https://ieeexplore.ieee.org/document/6392107/ — Bavota 2012
   (IEEE)
6. https://people.lu.usi.ch/bavotg/papers/scam2012.pdf — Bavota
   2012 (PDF)
7. https://pdxscholar.library.pdx.edu/compsci_fac/109/ —
   Murphy-Hill & Black "Refactoring Tools: Fitness for Purpose"
   (related context, already in Murphy-Hill line)
8. http://web.cs.ucla.edu/~miryung/Publications/tse2017-refdistiller.pdf
   — RefDistiller TSE 2017 (supporting context only)

---

## Research-agent results: Prompt M2 (W_COMMIT_GAP citation hunt)

### Executive summary

**Three papers found — two STRONG, one MEDIUM (a useful null
result).** The literature **does not directly measure "commit-gap
during refactoring → rollback or review cost"**. The closest
cluster is the *change decomposition / untangling* line (Tao &
Kim, Di Biase, Herzig already in bib). The frequency claim is
supported via decomposition; the "shorter commits merge faster"
intuition turns out to be empirically weaker than expected
(Kudrjavets null result).

This is exactly the same shape of finding as Prompt M1: literature
supports the *premise* (decomposition reduces review noise) but
not the *exact* claim (cadence during refactoring → reduced
rollback / review cost). The axiomatic defence does load-bearing
work; flag it honestly in §3.3.

### Fits

**[DIBIASE-2019]** Di Biase, M., Bruntink, M., van Deursen, A.,
Bacchelli, A. *The Effects of Change Decomposition on Code Review
— A Controlled Experiment.* PeerJ Computer Science, 2019.
arXiv:1805.10978. DOI: 10.7717/peerj-cs.193.

- **What it does:** Controlled experiment with 28 developers
  showing decomposed changesets yield fewer false-positive issues
  and more context-seeking during review (defect count unchanged).
- **Touches:** (a) review-cost reduction, (b) tangled-change
  decomposition (natural follow-up to Herzig 2013, already cited).
- **Why it matters here:** Cleanest empirical anchor for
  W_COMMIT_GAP's review-cost component — *"smaller, coherent
  units lower review noise."*
- **Strength of fit:** **STRONG**.

**[TAO-KIM-2015]** Tao, Y., Kim, S. *Partitioning Composite Code
Changes to Facilitate Code Review.* MSR 2015, IEEE.
DOI: 10.1109/MSR.2015.24.

- **What it does:** User study showing decomposed/untangled
  changesets let reviewers complete tasks more correctly in the
  same time.
- **Touches:** (a) review effectiveness, (b) refactoring-vs-feature
  separation.
- **Why it matters here:** Older but seminal — pairs naturally
  with Herzig (already in bib) to justify *"long uncommitted
  stretches conflate concerns and inflate review cost."*
- **Strength of fit:** **STRONG**.

**[KUDRJAVETS-2022]** Kudrjavets, G., Nagappan, N., Rastogi, A.
*Do Small Code Changes Merge Faster? A Multi-Language Empirical
Investigation.* MSR 2022. arXiv:2203.05045.

- **What it does:** Analyses ~1.25M PRs across 10 languages +
  Gerrit/Phabricator. **Finds PR size does NOT correlate with
  time-to-merge.**
- **Touches:** (c) — counter-evidence on commit cadence vs review
  velocity.
- **Why it matters here:** Cite as a *calibrating null result*.
  Lets §3.3 say *"we do not claim shorter commit gaps merge
  faster; the justified gain is review comprehensibility and
  rollback locality, not throughput."* This is the kind of
  honest-disclosure citation that markers reward — proves
  awareness of the literature's *boundaries*.
- **Strength of fit:** MEDIUM (counter-citation, but high-value
  for thesis defence).

### Calibration finding (use in §3.3 prose)

No paper in 2015–2025 directly measures *"commit-gap during
refactoring → rollback or review cost"*. The closest cluster is
change decomposition / untangling (Tao & Kim, Di Biase,
Herzig). Trunk-based-development quantitative claims live in
DORA/Accelerate reports — practitioner-grade, not peer-reviewed
empirical SE — so unsuitable for a methodology citation. The
"frequency" leap beyond decomposition is genuinely the
**axiomatic step** in the W_COMMIT_GAP weight.

### How to use this in §3.3

**Plan-of-record update for §3.3 table row 6 (W_COMMIT_GAP):**

| Term | Default weight | Cited justification | Ablation Δ recovery |
|------|---------------:|---------------------|--------------------:|
| $W_{\textsc{cg}}$ (commitGap) | 7 | Tao & Kim 2015; Di Biase 2019 (review-cost); Herzig 2013 (tangling); Kudrjavets 2022 (calibrating null) | 0.533 |

§3.3 prose addition (~4 sentences):

> *"The W_COMMIT_GAP weight penalises long stretches of
> green-refactor checkpoints without committing. The reliability
> argument rests on the change-decomposition literature: Tao \&
> Kim 2015 show that partitioning composite changes lets reviewers
> complete review tasks more correctly in the same time [cite
> Tao 2015], and Di Biase et al. 2019 confirm in a controlled
> experiment that decomposed changesets lower false-positive
> review noise [cite Di Biase 2019]. We deliberately do not claim
> a throughput benefit; Kudrjavets et al. 2022 find that PR size
> does not correlate with time-to-merge across 1.25M PRs in 10
> languages [cite Kudrjavets 2022], so the justified gain from
> smaller commit gaps is review *comprehensibility* and
> *rollback locality*, not merge speed. We are aware of no
> published study that has directly measured commit cadence
> during refactoring as a predictor of rollback or review cost;
> the specific weight value is therefore axiomatic in the
> severity-ordering sense and validated by sensitivity and
> ablation in Chapter 5."*

### Action items

- **Add three new bib entries:** `dibiase2019decomposition`,
  `taokim2015partitioning`, `kudrjavets2022small`. Suggested:

  ```bibtex
  @article{dibiase2019decomposition,
    title     = {The Effects of Change Decomposition on Code Review: A Controlled Experiment},
    author    = {Di Biase, Marco and Bruntink, Magiel and van Deursen, Arie and Bacchelli, Alberto},
    journal   = {PeerJ Computer Science},
    volume    = {5},
    pages     = {e193},
    year      = {2019},
    doi       = {10.7717/peerj-cs.193},
    eprint    = {1805.10978},
    archivePrefix = {arXiv},
    primaryClass  = {cs.SE}
  }

  @inproceedings{taokim2015partitioning,
    title     = {Partitioning Composite Code Changes to Facilitate Code Review},
    author    = {Tao, Yida and Kim, Sunghun},
    booktitle = {Proceedings of the 12th IEEE/ACM Working Conference on Mining Software Repositories (MSR)},
    pages     = {180--190},
    year      = {2015},
    publisher = {IEEE},
    doi       = {10.1109/MSR.2015.24}
  }

  @inproceedings{kudrjavets2022small,
    title     = {Do Small Code Changes Merge Faster? {A} Multi-Language Empirical Investigation},
    author    = {Kudrjavets, Gunnar and Nagappan, Nachiappan and Rastogi, Ayushi},
    booktitle = {Proceedings of the 19th International Conference on Mining Software Repositories (MSR)},
    year      = {2022},
    publisher = {IEEE},
    eprint    = {2203.05045},
    archivePrefix = {arXiv},
    primaryClass  = {cs.SE}
  }
  ```

- **Update §3.3 weight table** to swap the `[citation hunt]`
  placeholder for `Tao & Kim 2015; Di Biase 2019; Herzig 2013;
  Kudrjavets 2022`.
- **Cite Kudrjavets as a deliberate calibrating null result** —
  not a weakness to hide. The honest framing ("we do not claim
  throughput, only comprehensibility + rollback locality") is
  rubric-rewarded.

### Sources

1. https://arxiv.org/abs/1805.10978 — Di Biase et al. 2019 arXiv
2. https://peerj.com/articles/cs-193/ — PeerJ CS published version
3. https://arxiv.org/abs/2203.05045 — Kudrjavets et al. 2022 arXiv
4. https://conf.researchr.org/details/msr-2022/msr-2022-technical-papers/21/Do-Small-Code-Changes-Merge-Faster-A-Multi-Language-Empirical-Investigation
   — MSR 2022 listing
5. https://scholar.google.com/scholar?q=Tao+Kim+Partitioning+composite+code+changes+MSR+2015
   — Tao & Kim 2015 (Google Scholar)
6. https://dora.dev/capabilities/trunk-based-development/ —
   DORA trunk-based development (practitioner reference only,
   *not cited* in thesis)

---

## Research-agent results: Prompt M3 (cognitive complexity validation)

### Executive summary

**Two strong peer-reviewed third-party validations exist — and they
disagree.** The "white paper, no peer-reviewed validation" framing
the plan originally assumed is **too strong**. Better framing:
*"empirically contested — partial validation by Muñoz Barón et
al. 2020, contradicted by Lavazza et al. 2023; cyclomatic
complexity remains the more extensively validated baseline."*

Both studies are independent of SonarSource (Stuttgart/Saarland
and Insubria/Zayed respectively). This is genuinely the best
shape of finding for a methodology chapter — citing a *contested*
metric with both validation and contradicting evidence is more
rigorous than citing an unvalidated one.

### Fits

**[MUNOZBARON-2020]** Muñoz Barón, M., Wyrich, M., Wagner, S.
*An Empirical Validation of Cognitive Complexity as a Measure of
Source Code Understandability.* ESEM 2020 (Best Full Paper).
DOI: 10.1145/3382494.3410636. arXiv:2007.12520.

- **What it does:** Systematic re-analysis of 24,400 human
  evaluations across 324 snippets from 10 prior comprehension
  studies; correlates Cognitive Complexity with time, correctness,
  and subjective ratings.
- **Verdict:** **Mixed-to-validating.** Significant positive
  correlation with comprehension *time* and *subjective*
  understandability. Correlation with *correctness* is
  weak/inconsistent (−0.52 to +0.57). Does NOT directly benchmark
  against cyclomatic.
- **Affiliation:** Independent (Univ. Stuttgart / Saarland). No
  SonarSource overlap.
- **Strength of fit:** **STRONG**.

**[LAVAZZA-2023]** Lavazza, L., Abualkishik, A.Z., Liu, G.,
Morasca, S. *An Empirical Evaluation of the "Cognitive
Complexity" Measure as a Predictor of Code Understandability.*
Journal of Systems and Software, 2023.
DOI: 10.1016/j.jss.2022.111561.

- **What it does:** Builds understandability prediction models
  with Cognitive Complexity vs. traditional size/cyclomatic
  measures; head-to-head correlation comparison.
- **Verdict:** **Contradicts.** Cognitive Complexity correlates
  with understandability "approximately as much as traditional
  measures"; adding it to models yields negligible improvement
  over cyclomatic+size. Explicitly concludes it "does not fulfill
  the promise."
- **Affiliation:** Independent (Univ. Insubria / Zayed Univ.).
  No SonarSource overlap.
- **Strength of fit:** **STRONG**.

### Near-miss (worth knowing about)

**[LENARDUZZI-2023]** Lenarduzzi, V., Kilamo, T., Janes, A.
*Cognitive Complexity: An Empirical Study on the Relevance of
Code Smells and Code Understandability.* arXiv:2303.07722.

- Junior-developer study finding both cognitive and cyclomatic
  complexity are only "modest predictors" of understandability.
  Doesn't fundamentally change the picture but reinforces the
  "contested" framing.

### How to use this in §3.4

**Plan-of-record update for §3.4 cleanliness table row 1
(Cognitive complexity):**

| Sub-metric | Weight | Cited basis | Computation source |
|------------|------:|-------------|--------------------|
| Cognitive complexity | 1.0 | Campbell 2018; Muñoz Barón et al. 2020 (partial validation); Lavazza et al. 2023 (contradicting) | Per-method mean |

§3.4 prose addition (~3 sentences, replacing the earlier "white
paper, not peer-reviewed" caveat):

> *"Cognitive complexity is empirically contested. Muñoz Barón
> et al. 2020 partially validate Campbell's claim through a
> re-analysis of 24{,}400 understandability ratings, finding
> significant positive correlation with comprehension time and
> subjective understandability ratings [cite Muñoz Barón 2020].
> Lavazza et al. 2023, however, find that cognitive complexity
> predicts understandability approximately as well as
> traditional size and cyclomatic-complexity measures, with
> negligible additional explanatory power [cite Lavazza 2023].
> We include cognitive complexity as one of six normalised
> cleanliness sub-signals rather than as a dominant predictor,
> consistent with the Laplace-uniform composition justified
> below and the contested empirical status of the measure."*

This framing is **better than the original "no validation"
disclaimer** because it positions cognitive complexity honestly
within an active debate, which is more rigorous than treating it
as unvalidated.

### Action items

- **Add two new bib entries:** `munozbaron2020cognitive`,
  `lavazza2023cognitive`. Suggested:

  ```bibtex
  @inproceedings{munozbaron2020cognitive,
    title     = {An Empirical Validation of Cognitive Complexity as a Measure of Source Code Understandability},
    author    = {Mu{\~n}oz Bar{\'o}n, Marvin and Wyrich, Marvin and Wagner, Stefan},
    booktitle = {Proceedings of the 14th ACM/IEEE International Symposium on Empirical Software Engineering and Measurement (ESEM)},
    year      = {2020},
    publisher = {ACM},
    doi       = {10.1145/3382494.3410636},
    eprint    = {2007.12520},
    archivePrefix = {arXiv},
    primaryClass  = {cs.SE},
    note      = {Best Full Paper award}
  }

  @article{lavazza2023cognitive,
    title     = {An Empirical Evaluation of the ``{Cognitive} {Complexity}'' Measure as a Predictor of Code Understandability},
    author    = {Lavazza, Luigi and Abualkishik, Abdallah Z. and Liu, Geng and Morasca, Sandro},
    journal   = {Journal of Systems and Software},
    volume    = {197},
    pages     = {111561},
    year      = {2023},
    doi       = {10.1016/j.jss.2022.111561}
  }
  ```

- **Update the §3.4 row** to replace the `[Prompt M3 if third-party
  validation exists]` placeholder with the two cites + "contested"
  prose framing above.
- **Drop the original "Campbell 2018 is a white paper, not
  peer-reviewed" caveat** in favour of the contested-empirical-
  status framing — it's more rigorous *and* more defensible.

### Sources

1. https://dl.acm.org/doi/10.1145/3382494.3410636 — Muñoz Barón
   et al. 2020 (ACM DL)
2. https://arxiv.org/pdf/2007.12520 — Muñoz Barón et al. 2020
   (arXiv)
3. https://www.sciencedirect.com/science/article/abs/pii/S0164121222002370
   — Lavazza et al. 2023 (JSS)
4. https://arxiv.org/abs/2303.07722 — Lenarduzzi et al. 2023
   (near-miss)
5. https://www.iste.uni-stuttgart.de/news/Best-Full-Paper-Award-at-The-14th-ACM-IEEE-International-Symposium-on-Empirical-Software-Engineering-and-Measurement-ESEM-2020/
   — ESEM 2020 Best Paper announcement
