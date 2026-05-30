# Plan: Thesis writeup (master organising plan)

DO IN `final_report/`, not `interim_report/`. The latter is frozen.

## Context

The user study has wrapped (12 human sessions + 6 agent sessions). The results chapter §5 is substantively complete, the threats-to-validity chapter §6 is done, and the conclusion chapter §7 is now done. Remaining work is the §5 read-through, the appendix wiring, the GenAI disclosure, and the bibliography pass. Per `MENG_PROJECT_ASSESSMENT_CRITERIA.md` the rubric splits 15 / 50 / 20 / 15 across Framing / Execution / Evaluation / Communication; the items below are tagged by which band they're aimed at moving.

## Chronological writeup order

| #  | Section                             | Estimate | Status | Rubric criterion |
|----|-------------------------------------|---|---|---|
| 1  | Introduction                        | ½ day | ✅ **done** | Framing 15% |
| 2  | Background trim + new citations     | 2 days | ✅ **done** | Framing 15% |
| 3  | Methodology chapter                 | 1 week | ✅ **done** | Execution 50% |
| 4  | Tool architecture chapter           | 2 days | ✅ **done** | Execution 50% |
| 5  | Experiments + results chapter       | 1 week | ✅ **done** (substantive) | Execution 50% + Evaluation 20% |
| 5b | §5 final read-through               | ½ day | **pending** | Communication 15% |
| 6  | Threats-to-validity / discussion    | 2 days | ✅ **done** | Evaluation 20% (highest leverage) |
| 7  | Conclusion + future work            | 1 day | ✅ **done** | Evaluation 20% + Framing 15% |
| 8  | Bibliography audit                  | ½ day | ✅ **done** | Communication 15% |
| 9  | GenAI + ethics disclosure appendix  | ½ day | **pending** | Communication 15% (caps band at 50–59% if absent) |
| 9b | Port agent transcript into appendix | ½ day | ✅ **done** | Resolves `app:agent-transcript` cross-references from §5.4.2 |
| 9c | add user interviews into appendix   |       |             |                                                                                                                                  |
| 10 | Abstract                            | ½ day | **pending** | Communication 15% — draft last, after §5b read-through, once headline numbers are stable. ~200 words: gap + method + key result. |

**Total remaining: ~1.5 days of focused writeup.**

## Where §5 landed (recap of this session's work)

§5 substantive content is complete and PDF-clean. Highlights of what changed since the original plan:

- **§5.1 Score formula** — sensitivity sweep (single-knob + multi-knob Monte Carlo), ablation across three session sets (injection / user study / agent), the "reordering rarely helps" headline. Top-1 stability $95.6\%$ user / $97.5\%$ agent / $99.8\%$ injection.
- **§5.2 Detector** — three-way inter-rater κ across $45$ sessions, per-kind precision/recall, per-kind detection quality with REWORK and IDE_REPLAY misses framed honestly (REWORK $46.2\%$ beat rate carries the "process signal sometimes earned, not wasted" reading; IDE_REPLAY $75.0\%$ misses are score-floor clamping not penalty miscalibration). Injection-prominence table with the ORDERING row honestly absent. F1/accuracy explicitly justified against.
- **§5.3 User and agent studies** split into:
  - **§5.4 User study** ($12$ sessions, P1 + P2): per-session per-kind distribution, per-kind behavioural trajectory across the arc, the new **process-score-under-production-and-gain-stripped-weightings** table that surfaces a near-monotonic process-discipline improvement under $W_g = 0$ for both participants against a difficulty headwind, hygiene false-positive observation, per-step inspection usability findings, paraphrased interview quotes.
  - **§5.4.2 Agent comparison** ($6$ sessions, Claude Code, single Claude Code session across all six): per-kind table against P1 and P2 separately, per-session trajectory, three quoted behavioural threads (commit cadence over-corrected then resolved; smell-introduction self-critique; acknowledged IDE-replay structural limitation), broken-build percentage comparison tied to Thread 1.
- **Cross-session corrections shipped this session**:
  - `LONG_STRETCH_WITHOUT_COMMIT` advice rule rewritten to count green-refactor checkpoints only, matching the score-formula commit-gap penalty. All 18 analysis-report JSONs regenerated under the corrected rule. Three stale claims in §5.4.2 Thread 1 fixed accordingly.
  - Table 5.7 (detection quality), 5.8 (prominence), and 5.9 (step-anchor) regenerated against current code with explicit tie-break ordering; baselines paragraph and table dropped entirely as a strawman comparison.
  - Step-anchor recall sub-table removed (was measuring plugin cadence noise, not detector accuracy); replaced with a one-sentence disclosure in the caveats paragraph + a threats bullet flagging the per-session-not-per-step TP classification.
  - "Manifest" implementation leakage replaced with concrete label references (`expected_kinds`, `target_step`, etc.).
  - All ranking sites in code use deterministic `compareByDescending<DivergencePoint>{magnitude}.thenBy{stepIndex}`.
- **Notebook** (`tool/notebooks/experiments.ipynb`, $46$ cells) reproduces every table in §5 that quotes a number. Each notebook cell carries a markdown comment naming the chapter table it reproduces.
- **Appendix `app:agent-transcript`** label exists; the full Claude Code session transcript is referenced from §5.4.2 Threads 1–3.

## Remaining writeup tasks

### 5b. §5 final read-through (½ day)

Single pass focused on three drift sources after the cumulative editing this session:

- **(i) Stale cross-references.** Any `\ref{}` that may have changed names this session (e.g. `sec:results-userstudy` → `sec:results-kappa` for the κ table, `tab:results-divergence-accuracy` → `tab:results-divergence-prec-recall`, `tab:results-divergence-baselines` deleted, `tab:results-divergence-stepanchor` deleted). Already swept once but worth a second pass given how many table labels moved.
- **(ii) Forward-references to threats-to-validity that name a specific bullet.** The §5 text cross-references "the threats-to-validity chapter" multiple times, often paraphrasing a specific bullet. Once §6 is drafted those references should resolve to actual section labels rather than the §6 placeholder.
- **(iii) §5 chapter intro paragraph + §-by-§ summary at the top of §5** must still match what each subsection actually delivers post-rewrite. The intro was written when the chapter had a different shape (e.g. "baselines and absolute magnitudes" paragraph that is now deleted; step-anchor analysis that is now removed; agent comparison framed as one paragraph that is now three quoted threads).

The substantive results work is done; this is polish.

### 6. Threats-to-validity / discussion — ✅ **done**

Landed as `final_report/threats/threats.tex` (new directory; the stale `evaluation/evaluation.tex` interim-plan stub was left orphaned in main.tex). Chapter heading **"Threats to validity"**, label `ch:threats`. Wired into `main.tex` between results and project_plan.

Renders at $\sim 6$ pages with eight subsections:

- **§6.1 Mitigations and design choices** — leads with eight bullets covering what the design already addresses (three-way κ, multi-recorder fixture, deterministic tie-break, multi-knob MC, score-floor disclosure, validator-check disclosure, notebook reproducibility, per-session-not-per-step TP disclosure).
- **§6.2 Construct validity** — five paragraphs: no external ground truth, score-not-maintainability-index, four-kind taxonomy is methodology choice, `SuboptimalOrdering` author-claimed, AST-equivalence audit informal.
- **§6.3 Internal validity** — eight paragraphs: single-knob vs multi-knob, no rater-rank validation, cleanliness sub-weights uniform, two batching primitives heuristic, production weights hand-picked, term-importance varies by session type (connects to gain-stripped finding), reorder dependency analysis coarse (with the audit-found correction that only `RenameMethod`/`ChangeMethodSignature`/`ParameterizeVariable`/`ParameterizeAttribute` are coarse), cleanliness clamp does not affect magnitudes by construction (validator forces same terminal AST).
- **§6.4 External validity** — five paragraphs: scoped exercises not naturalistic, $n=2$ user study, $n=1$ agent, single-author IntelliJ/Gradle Java, refactoring kinds outside synthesiser capability excluded.
- **§6.5 Statistical conclusion validity** — three short paragraphs on κ CIs, τ-b brittleness, no multiple-comparison correction.
- **§6.6 Per-experiment threats** — four paragraphs (kind classifier, user study with Hawthorne + task-style framing, agent comparison, rater study).
- **§6.7 Implementation threats** — wrap-and-patch incompleteness, reorder enumerator size budget (windows >$7$ steps skipped, capped at $7! = 5040$).
- **§6.8 Out of scope** — six items (multi-agent, longitudinal, cross-IDE, weight refitting, live dashboard, rater-rank).

Key cross-chapter corrections shipped during §6 drafting:
- Methodology §3.6 corrected: previously said "DAG sizes are small (most brackets contain 2–8 steps), enumeration terminates quickly" — now discloses the hard $7$-step budget consistent with the code and the threats chapter.
- Architecture §4.6 corrected: enumeration paragraph now mentions the size budget alongside the dependency-DAG pruning.
- §5.4 gain-stripped paragraph reframed: dropped "design doesn't separate feedback from codebase familiarity" framing (the gain-stripped subscore is constructed precisely to be codebase-knowledge-independent), replaced with Hawthorne + task-style-familiarity confounds.
- Wohlin et al. 2012 bib entry added to `bibs/sample.bib` for the four-axis framework citation.

### 7. Conclusion + future work — ✅ **done**

Landed as `final_report/conclusion/conclusion.tex`, wired into `main.tex` between threats and project_plan. Resolves the previously-undefined `\ref{ch:conclusion}` in threats.tex. Five subsections:

- **§7.1 Summary of contributions** — two main contributions (process-quality score $J$ and divergence-point detector, with the four per-kind synthesisers folded in as detector-internal), plus supporting deliverables (corpus, plugin, dashboard, notebook).
- **§7.2 Headline findings** — five paragraphs: commuting-refactoring observation, kind-classifier precision $1.00$, top-1 stability ($95.0\%$ single-knob, $77.0\%$ multi-knob), gain-stripped process-discipline arc for P1/P2, agent course-correction within its tool surface with the IDE-replay limitation correctly attributed to missing tool surface rather than agent failure.
- **§7.3 What the work supports and what it does not** — four-claim supports list; the "does not" paragraph reframed away from a laundry-list of limitations toward "claims left for follow-up work".
- **§7.4 Future work** — eight prioritised items, led by larger-and-controlled human study (the largest single gap), then multi-agent comparison, rater-rank validation, IDE-refactor-as-agent-tool, live-feedback dashboard, cross-IDE/cross-language, reorder beam search, relaxed validator gate.
- **§7.5 Closing** — single paragraph returning to the introduction's gap framing.

### 8. Bibliography audit — ✅ **done**

Closed in commit `caf9727`. The audit applied a six-check rubric (metadata complete, metadata accurate, source obtainable, cite sites enumerated, each claim supported, no internal contradiction) to all $67$ cited entries, log lives at `tool/PLAN-bibliography-audit-log.md`. Outcomes:

- **All 67 cited entries verified** against canonical sources (DOI/arXiv/publisher pages, with secondary cross-checks on Semantic Scholar / DBLP where IEEE Xplore is paywalled).
- **Metadata fixes applied** to $30+$ entries: missing volume/issue/pages/DOI, expanded "and others" author lists, mis-romanised or misattributed authors. Notable corrections include `foutsekh2018interactiontraces` (the citekey reflected a misparse of "Foutse Khomh"; canonical first author is Yamashita), `whydevelopersrefactor2020` (first-author given name was wrong: Valentina → Jevgenija Pantiuchina), `metricsmotivation2024` (entire author list was wrong), `pan2025swegym` (last author misattribution Yuandong → Yizhe Zhang), `chen2024morcora` (Lerina → Lei), `kim2011apilevel` (Danny → Dongxiang).
- **Three load-bearing claim issues** identified and fixed in the thesis prose:
  - `wang2018traceability` was a `.bib` stub; the actual cited work is Nyamawe et al. 2018 IEEE Access (Wang is fourth author). Stub replaced with full entry; all five cite sites land cleanly against the canonical paper.
  - `bansiya2002qmood` was being claimed at two sites to have "regression-fitted" / "tuned against expert assessments" weights — wrong (QMOOD weights are set theoretically and validated via rank-correlation against 13 experts). QMOOD dropped from both sentences (`threats.tex:31`, `methodology.tex:147`); the Maintainability Index cite alone carries the calibrated-snapshot-composite claim.
  - `poncin2011processmining` had wrong title in `.bib` ("Process Mining Software Development Workflows" was never published; canonical is "Process Mining Software Repositories"); cite site framing in `background.tex:221` reworded from "Eclipse plugins to collect detailed event logs" to "combine heterogeneous software-repository logs (version control, bug trackers, mail archives)" to match what the paper actually does.
- **POMDP-claim softening** at `background.tex:28` / `:210` and `introduction.tex:13`: only `gautam2025refactorbench` carries the POMDP-formalism claim; `bouzenia2025trajectories` and `pan2025swegym` are recast as "related work studying agent action patterns / training environments" (neither paper formalises a POMDP).
- **`alomar2025slr` softening**: "identifies process-aware evaluation as an open gap" → "surveys evaluation methodologies and identifies open challenges in this area". The SLR doesn't name process-aware evaluation as a gap category; the new gloss matches what it actually does.
- **16 uncited orphan entries removed** from `sample.bib` (`amann2016icpc`, `bagheri2022goodegg`, `bavota2012induce`, `beller2019tse`, `croux2010influence`, `falleri2014gumtree` [superseded], `kim2011api` [duplicate of `kim2011apilevel`], `kim2019iwor`, `murphyhill2009floss`, `negara2014icse`, `paixao2020intents`, `proksch2018msr`, `tsantalis2018refactoringminer` [superseded by `alikhanifard2025rminer`], `tsantalis2020refactoringminer` [superseded], `wang2025forge`, `wang2025tosem`). Bib now has $67$ entries, every one of them cited.
- **Final build**: `./build.sh --light` produces zero "Citation undefined" warnings and zero "labels may have changed" warnings.

### 9. GenAI disclosure appendix (½ day, Communication 15%)

Rubric explicitly requires this; without it the Communication band caps at $50$–$59\%$. One paragraph per category of use (writing assistance, code assistance, ideation), with the disclosure being transparent and critically reflective per the 70-84% band descriptor ("noting limitations, checks, or how AI suggestions were adapted").

## Critical files

- `final_report/results/results.tex` — substantively complete; needs the §5 read-through (task 5b).
- `final_report/threats/threats.tex` — ✅ done (Chapter 6).
- `final_report/evaluation/evaluation.tex` — stale interim-plan stub; not included in main.tex; safe to ignore or delete.
- `final_report/conclusion/conclusion.tex` — ✅ done (Chapter 7).
- `final_report/appendix/appendix.tex` — currently has agent-transcript label only; needs the transcript content ported in (task 9b) and the GenAI appendix appended (task 9).
- `final_report/bibs/sample.bib` — ✅ audit closed in commit `caf9727`. $67$ cited entries; $0$ orphans; build clean.
- `tool/THREATS-TO-VALIDITY.md` — base bullet source consumed by §6.
- `tool/PLAN-threats-to-validity-audit.md` — audit additions consumed by §6.

## What is in hand

All data, code, and notebook tables are in place:
- All $45 + 12 + 6 = 63$ session analysis-report JSONs regenerated under current code.
- Notebook `tool/notebooks/experiments.ipynb` reproduces every table in §5 with markdown comments cross-referencing the chapter.
- `tool/THREATS-TO-VALIDITY.md` ready to expand into prose for §6.
- Agent session transcript exists at `tool/fixtures/agent-sessions/AGENT_SESSION_TRANSCRIPT.md`; needs porting into `appendix/appendix.tex` for the §5.4.2 cross-references to resolve cleanly.

## Out of scope (named as future work, not done)

- Rater-rank validation (Kendall τ-b across raters on per-session DP ranking, as distinct from per-kind agreement). Noted in threats-to-validity as a known gap.
- Multi-agent agent comparison ($n > 1$ models, multiple dates).
- Refitting production weights against an outcome dataset.
- Cross-IDE / cross-language port.
- Real-time / live-feedback dashboard mode (current is session-replay).
