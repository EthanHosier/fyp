# Plan: Thesis writeup (master organising plan)

DO IN final_report not interim_report folder, since the latter is now frozen and the former is where the final writeup lives.

## Context

The user study (`PLAN-user-study.md`) is in flight and runs over
the next ~2 weeks. Per `HONEST_REVIEW_2.md`, the project's mark is
now writeup-bound rather than experiment-bound, and only ~1.5 pages
of the ~50-page thesis are actually study-dependent. The remaining
~48 pages can be drafted in parallel with the study.

This is the master plan organising that writeup work in
chronological thesis order, with the study-blocked slots clearly
flagged. **Detailed per-section plans will be written separately**
(`PLAN-thesis-<section>.md`) when work on each section begins.

## Chronological writeup order

| # | Section                          | Estimate | Status        | Study-blocked slot                              |
|---|----------------------------------|---------:|---------------|-------------------------------------------------|
| 1 | Introduction                     | ½ day    | ✅ **done**    | —                                               |
| 2 | Background trim + new citations  | 2 days   | ✅ **done**    | —                                               |
| 3 | Methodology chapter              | 1 week   | ✅ **done**    | ~~TODO slot: rater-design paragraph (~½ pg)~~ — landed |
| 4 | Tool architecture chapter        | 2 days   | ✅ **done**    | —                                               |
| 5 | Experiments + results chapter    | 1 week   | ✅ **done**    | ~~TODO: expanded-corpus subsection (~½ pg)~~ — shipped as full §5.4 User study (4 sub-blocks, ~5 pp incl. agent comparison) |
| 6 | Threats-to-validity / discussion | 2 days   | pending       | TODO: mitigation paragraph (~⅓ pg)              |
| 7 | Conclusion + future work         | 1 day    | pending       | TODO: user-study findings bullet (~¼ pg)        |
| 8 | Bibliography audit               | ½ day    | pending       | —                                               |
| 9 | GenAI disclosure appendix        | ½ day    | pending       | —                                               |

**Total: ~3.5 weeks of focused writeup, study-dependent inserts
total ~1.5 pages across 4 slots.**

**Update (post-user-study):** the expanded-corpus slot landed
larger than the original ½-page budget — `\section{User study}`
came in at ~5 pages with four sub-blocks (inter-rater reliability,
descriptive findings on the participant corpus, behavioural
trajectory across the six-session arc, agent comparison). The
agent-comparison sub-block was Phase 8 of `PLAN-user-study.md` and
was originally a one-sentence stub deferred to future work; it now
ships as a full subsection with two new tables. The methodology
rater-design TODO has now landed too (a ½-page section covering
schema, briefing/blinding protocol, κ as Landis-Koch interpretation,
reconciliation policy, with the empirical numbers carried in the
results-chapter table). The threats-to-validity and conclusion
TODOs are still open.

## Per-section quick-scope notes

1. **Introduction** — tighten the gap statement using the
   reordering-rarely-helps finding from `divergence.md`.

2. **Background** — port `interim_report/background/background.tex`,
   trim ReSynth weight to ~½ page, add 1–2 paragraphs of 2023–2025
   work on developer-process logging / refactoring-trajectory
   analysis, full typo + formality pass.

3. **Methodology chapter** — ✅ **done.** Order:
   state/action/trace framing → process score with cited weights
   + ablation single-term recovery numbers → cleanliness sub-score
   → DivergencePoint detector per kind → per-kind synthesisers
   (incl. JDT `withBatchSession` engineering story) → rater design.
   ~~TODO slot near end: rater-design paragraph.~~ — landed as
   `\section{Rater design}` covering schema, briefing/blinding
   protocol, Cohen's κ + Landis-Koch interpretation, and the
   "don't retro-edit labels" reconciliation policy with sessions
   024 / 043 as the concrete case. Empirical numbers stay in the
   results chapter; methodology section is the framing.

4. **Tool architecture** — ✅ **done.** Ported to LaTeX as 8
   sections (architecture at a glance, event capture, shadow-repo
   reconstruction and worktree pool, metrics calculation, applying
   refactorings via Eclipse JDT, synthesising counterfactual
   trajectories, pipeline phases, dashboard). Phase A / Phase B
   split surfaced as the engineering contribution.

5. **Experiments + results** — ✅ **done.** Three subsections from
   `explained_results/{sensitivity, ablation, divergence}.md` near
   verbatim, the reordering-rarely-helps headline, the scoped-out
   fixes section (now updated for the PR #62 IDE_REPLAY closure),
   and a new `\section{User study}` covering Cohen's κ on the
   45-session corpus, per-session descriptive findings on the
   12-session participant corpus, behavioural trajectory, and the
   agent-comparison subsection. ~~TODO slot at end of divergence
   subsection: expanded-corpus (κ per kind, multi-recorder
   precision/recall delta, verbatim quotes).~~ — landed.

6. **Threats-to-validity / discussion** — consolidate
   per-experiment caveats from the four `explained_results/*.md`
   files. Lead with what was mitigated, then what remains.
   **TODO slot at the top:** mitigation paragraph (κ +
   multi-recorder).

7. **Conclusion + future work** — bullet deferred items: ORDERING
   gate relaxation, plugin template-event capture, multi-language,
   AI-vs-human comparison, multi-fixture, full multi-recorder
   corpus expansion.
   **TODO slot:** user-study findings bullet.

8. **Bibliography audit** — verify all citations resolve; add
   2023–2025 entries used in Background.

9. **GenAI disclosure appendix** — rubric-mandated. One paragraph
   per category of use (writing assistance, code assistance,
   ideation).

## Source-material map

Most chapters port from existing markdown writeups rather than
being written from scratch:

| Thesis chapter         | Existing source(s)                                                                     |
|------------------------|-----------------------------------------------------------------------------------------|
| Background             | `interim_report/background/background.tex` (trim + extend)                              |
| Methodology            | `explained_results/*.md` methodology sections + `ARCHITECTURE.md` (partly)              |
| Tool architecture      | `ARCHITECTURE.md`                                                                       |
| Experiments + results  | `explained_results/sensitivity.md`, `ablation.md`, `divergence.md`, `plugin-misclassifications.md` |
| Threats-to-validity    | Per-experiment Caveats sections in the four `explained_results/*.md` files               |

## What's blocked by the user study (the 4 TODO slots)

1. ~~**Methodology chapter** — rater-design paragraph (Phase 1 of
   `PLAN-user-study.md`).~~ **✅ Done.** Landed as
   `\section{Rater design}` in `methodology.tex` covering schema,
   briefing/blinding protocol, κ + Landis-Koch interpretation,
   reconciliation policy with sessions 024 / 043 as the concrete
   case. Cross-refs from §5.3 detection-accuracy and §5.4
   inter-rater-reliability both resolve cleanly.
2. ~~**Experiments + results chapter** — expanded-corpus subsection
   (κ per kind, multi-recorder precision/recall delta on the new
   6 sessions, verbatim quotes from feedback).~~ **✅ Done.**
   Landed as the full `\section{User study}` with four sub-blocks
   (inter-rater κ on the 45-session corpus, descriptive findings
   on the 12-session participant corpus, behavioural trajectory,
   agent comparison). All three new tables in §5.4 in place; PDF
   builds clean.
3. **Threats-to-validity** — mitigation paragraph leading the
   section. _Still open. κ + multi-recorder data both available._
4. **Conclusion** — user-study findings bullet. _Still open. Data
   available._

Total slots done: 2 of 4. Of the remaining two, all underlying
data is in hand; they are writing-only tasks.

## Note on per-section plans

This file is the master organising plan. Each section gets its own
detailed `PLAN-thesis-<section>.md` (e.g. `PLAN-thesis-methodology.md`,
`PLAN-thesis-results.md`) when work on that section begins —
covering sub-sections, figure lists, page budgets, citation lists,
and exact source-material mappings.
