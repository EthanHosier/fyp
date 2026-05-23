# Plan: Thesis writeup (master organising plan)

DO IN `final_report/`, not `interim_report/`. The latter is frozen.

## Context

The user study has now wrapped (12 human sessions + 6 agent sessions). The results chapter §5 is substantively complete. Remaining work is the three follow-on chapters and the report-wide polish pass. Per `MENG_PROJECT_ASSESSMENT_CRITERIA.md` the rubric splits 15 / 50 / 20 / 15 across Framing / Execution / Evaluation / Communication; the items below are tagged by which band they're aimed at moving.

## Chronological writeup order

| # | Section | Estimate | Status | Rubric criterion |
|---|---|---|---|---|
| 1 | Introduction | ½ day | ✅ **done** | Framing 15% |
| 2 | Background trim + new citations | 2 days | ✅ **done** | Framing 15% |
| 3 | Methodology chapter | 1 week | ✅ **done** | Execution 50% |
| 4 | Tool architecture chapter | 2 days | ✅ **done** | Execution 50% |
| 5 | Experiments + results chapter | 1 week | ✅ **done** (substantive) | Execution 50% + Evaluation 20% |
| 5b | §5 final read-through | ½ day | **pending** | Communication 15% |
| 6 | Threats-to-validity / discussion | 2 days | **pending** | Evaluation 20% (highest leverage) |
| 7 | Conclusion + future work | 1 day | **pending** | Evaluation 20% + Framing 15% |
| 8 | Bibliography audit | ½ day | **pending** | Communication 15% |
| 9 | GenAI disclosure appendix | ½ day | **pending** | Communication 15% (caps band at 50–59% if absent) |

**Total remaining: ~4.5 days of focused writeup.**

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

### 6. Threats-to-validity / discussion (2 days, Evaluation 20% — highest leverage)

Currently: `final_report/evaluation/evaluation.tex` is $11$ lines. The §5 chapter cross-references "the threats-to-validity chapter" multiple times and those refs resolve to an essentially empty section.

Source material is in hand:
- `tool/THREATS-TO-VALIDITY.md` — $80$ lines of bullet content organised by validity axis (construct / internal-calibration / external-corpus / statistical / kind-classifier-specific / agent-specific / user-study-specific / rater-study-specific / implementation / out-of-scope).
- Per-experiment caveats are already inline in §5 — the chapter should consolidate them rather than re-derive.

Target shape: $3$–$5$ pages of structured prose. One subsection per validity axis. Lead with **what was mitigated** (κ on 45 sessions; multi-recorder corpus via P1 + P2; deterministic tie-break; honest score-floor clamping disclosure; relaxed validator-check disclosure). Then **what remains open** — explicit n=2 + no-control-condition for the user study; n=1 agent; structural IDE-refactor-surface limitation for the agent; codebase-familiarity confound; per-session-not-per-step TP classification; ORDERING-recall scope-out.

### 7. Conclusion + future work (1 day, Evaluation 20% + Framing 15%)

Currently: `final_report/conclusion/conclusion.tex` is $0$ lines.

Target shape: $2$–$3$ pages.

- **Contribution restatement**: what was built (score formula + four-kind detector + per-kind alt synthesisers + IntelliJ plugin + JCEF dashboard + analysis pipeline), what was found (the headline numbers: precision $1.00$ × four kinds, top-1 stability ≥ $95\%$, gain-stripped human improvement curve, agent qualitative behavioural threads), what generalises and what does not.
- **Future work**: multi-agent comparison; multi-language / cross-IDE port; ORDERING recall via relaxed validator check; persistent learning across agent sessions; dashboard-mode live feedback (vs current session-replay); IDE-refactor-as-agent-tool-call deployment that would close the IDE_REPLAY gap; longitudinal validation against external code-quality outcomes (named as future work, infeasible at MEng scope).
- **What we cannot claim**: causation from the user-study trajectory ($n=2$, no control); generalisability to team-PR workflows; weight calibration against an external outcome dataset.

### 8. Bibliography audit (½ day, Communication 15%)

`bibs/sample.bib` needs an end-to-end pass. Verify every `\cite{}` resolves. Check for the 2023–2025 entries used in Background. Consistency of style across entries. No stub `key.bib.sample` placeholders.

### 9. GenAI disclosure appendix (½ day, Communication 15%)

Rubric explicitly requires this; without it the Communication band caps at $50$–$59\%$. One paragraph per category of use (writing assistance, code assistance, ideation), with the disclosure being transparent and critically reflective per the 70-84% band descriptor ("noting limitations, checks, or how AI suggestions were adapted").

## Critical files

- `final_report/results/results.tex` — substantively complete; needs the §5 read-through (task 5b).
- `final_report/evaluation/evaluation.tex` — currently stub; needs full §6 draft (task 6).
- `final_report/conclusion/conclusion.tex` — empty; needs full §7 draft (task 7).
- `final_report/appendix/appendix.tex` — currently has agent-transcript label only; add GenAI appendix (task 9).
- `final_report/bibs/sample.bib` — needs audit (task 8).
- `tool/THREATS-TO-VALIDITY.md` — source-of-truth bullets for §6.

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
