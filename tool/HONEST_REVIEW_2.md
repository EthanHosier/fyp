# Honest evaluation against the MEng rubric — v2

A week ago I wrote `HONEST_REVIEW.md` putting the project at:

- **Optimistic ~75, realistic ~70, pessimistic ~64.**
- Evaluation dimension (20% weight) flagged as **40–59% — the dominant
  downward pressure on the final mark.**

This is a re-read of the same rubric after a week of substantial
work: the process-score methodology overhaul (PR #56 and lead-up
work), the first-class DivergencePoint model (PR #53), Hygiene
divergence (PR #54), the Phase A/B split + injectable
`ScoringConfig` (PR #57), the three experiment drivers (PR #58),
the 45-session hand-recorded corpus (PR #59), and the four
explained-results writeups (commit `2ba423f`).

Calibration disclaimer: same as v1. I'm not your marker. These are
estimates; treat them as directional.

---

## Headline

**Realistic overall estimate: ~75.5% — solid first class.**
**Optimistic: ~78.5%. Pessimistic: ~72.5%.**

The floor moved from "borderline first" to "solid first." The
ceiling moved from "solid first" to "comfortable first." Almost
all of that delta is the Evaluation dimension: it has shifted from
40–59 % (high risk) to 68–78 % (solid contribution).

The project's final mark is now overwhelmingly determined by
**writeup quality**, not by additional experimental work. You have
the evidence base for a first; the question is whether the chapter
defends it.

---

## Rubric scores, side-by-side

| Dimension                     | Weight | v1 band       | v2 band       | Δ           |
|-------------------------------|:------:|:-------------:|:-------------:|:-----------:|
| 1. Framing                    | 15 %   | 70–84 % mid   | 72–78 %       | +2–4        |
| 2. Execution                  | 50 %   | 70–84 % mid   | 78–84 %       | +3–5        |
| 3. **Evaluation**             | 20 %   | **40–59 %**   | **68–78 %**   | **+15–25**  |
| 4. Communication              | 15 %   | 60–69 / ≤75   | 65–78 %       | mostly held |

---

## 1. Framing (15 %)

**Estimated band: 72–78 % (was 70–84 % mid, realistic ~72).**

What moved:

- The **"reordering rarely produces a better refactoring"** finding
  from divergence.md is the kind of empirical claim that
  elevates a tool-building thesis into a research thesis. 225 alts
  → 29 surfaced DPs → 7 beat user; 0/5 SuboptimalOrdering
  injections produced a positive-magnitude reorder alt; mechanism
  (commuting refactors → same terminal AST) is articulated and
  defensible. This is publishable as a workshop note on its own.
- The gap statement can now be hardened. The interim hedged
  ("comparatively little work"); the chapter can now claim more
  precisely: *no prior system both (a) scores a developer trace on
  a process-quality metric and (b) produces counterfactual
  divergence points with measured per-kind precision/recall against
  ground-truth labels.* The 45-session corpus + explained_results
  back this up.

What hasn't moved:

- No 2023–2025 citations added yet. The interim background section
  positions you against ReSynth, Refactoring Navigator, REFAZER —
  all 2010s work. A handful of recent citations on
  developer-process logging or refactoring-trajectory analysis
  would close that gap and is cheap (1 day of search + paragraph
  writing).
- The novelty framing in the interim is gentle. Sharpen it.

Net: realistic 73–76. Reaching 80+ here still needs the
positioning rewrite + a stronger "this is new" claim.

## 2. Execution (50 %)

**Estimated band: 78–84 % (was 70–84 % mid, realistic ~75–78).**

What moved (this is half the rubric so I'll be specific):

- Three named contributions now exist:
  1. **Process score formula** — 6 weights, each grounded in
     literature, each ablation-validated (`broken` strongest
     solo, `commitGap` strongest ordering effect, `gain` honestly
     disclosed as solo-weak).
  2. **First-class DivergencePoint detector** — per-kind logic
     with explicit `MIN_*` thresholds, typed entity flowing
     through analysis report → dashboard → experiment driver.
  3. **Per-kind counterfactual synthesisers** — reorder (with the
     JDT `withBatchSession` + AST-hash terminal audit + DFS
     prefix-trie engineering story), IDE_REPLAY, REWORK,
     HYGIENE. Each is a distinct algorithm with tests.
- **Phase A / Phase B split + injectable ScoringConfig** is genuine
  research infrastructure. Phase A is a deterministic dump of
  refactoring steps + checkpoints + per-step metrics; Phase B
  applies a `ScoringConfig` on top. This is what makes the
  sensitivity + ablation experiments possible at ~5s wall-clock
  total across 45 sessions. Most MEng codebases don't have
  experiment infrastructure this clean.
- **45-session hand-recorded corpus** with multi-label ground truth
  is a quantum of empirical work above the typical "we ran the
  tool on 3 toy projects" MEng output. The fact that you took
  honest negative findings (the reordering result, the
  plugin-misclassification audit) and folded them into the writeup
  is exactly what markers reward.
- **The v1 review's specific advice items 1 (methodology), 2
  (experiment), 3 (baseline) are mostly done.** Item 4 (human
  study) and item 5 (threats to validity) are partially done — TtV
  is per-experiment in the explained_results.md files, awaiting a
  unifying section.

What still constrains:

- **No multi-language.** Java only, JDT-only. This is a hard
  ceiling on "advances state of the art."
- **No external baseline comparison.** Internal baselines exist
  (endpoint-only `cleanliness_delta`, per-step regression list)
  but no comparison row to a published recommender. Adding
  Refactoring Navigator or ReSynth as a baseline is large work;
  framing your internal baselines as the comparison is cheap and
  would help. Currently they're columns in the CSV that need
  surfacing in the chapter.
- **Two named gaps in detection.** ORDERING recall 0.39 (validator
  splits manual-edit windows); IDE_REPLAY 4 FPs (plugin
  in-place-template capture). Both are documented as deferred with
  per-session root causes — that's the right way to handle them —
  but an examiner could ask "why didn't you fix them?" The answer
  ("the headline finding is already negative on ordering, fixing
  recall surfaces more zero-magnitude DPs; IDE_REPLAY fix is in a
  different module") is defensible but you should rehearse it.

Net: realistic 78–82. This is now an unambiguous upper-2nd / low-1st
on execution alone, with the experimental work doing genuine
heavy lifting. To reach 85+ here you'd need either the
multi-language story or a real third-party baseline integration —
both probably out of scope for the remaining timeline.

## 3. Evaluation (20 %)

**Estimated band: 68–78 % (was 40–59 % — the dominant risk in v1).**

This is the dimension that moved most. What's now in place:

- **Three experiments completed end-to-end with results
  documented:**
  - Sensitivity: 4860 perturbations, 99.1 % τ = 1.0, 100 %
    top-5 hit rate, saturation analysed at 15.6 % of rows.
  - Ablation: full 2^6 = 64 power-set, 2880 rows, monotone
    magnitude recovery 0.49 → 1.0, every term contributes,
    `commitGap` carries ordering, `broken` carries magnitude.
  - Divergence: 45 sessions, multi-label confusion matrix per
    kind, precision/recall/beats-fraction/top-1-ranking all
    reported.
- **A documented negative finding** (reordering rarely helps),
  framed as a sharpened chapter claim rather than a tool flaw.
  Markers reward this kind of intellectual honesty heavily.
- **Per-experiment caveats** in each explained_results.md file —
  saturation, τ-b small-DP brittleness, REWORK weight-independent
  magnitudes, cleanliness sub-weights not ablated, plugin
  misclassifications as upstream not downstream.
- The v1 review specifically called out "weight justification +
  sensitivity analysis" as missing. Both are now done.

What still constrains:

- **No third-party baseline comparison.** Internal baselines exist
  in the CSV (`cleanliness_delta`, `perstep_regression_steps`) but
  haven't been surfaced as a "naive endpoint-only would have
  caught X% of injections vs. our per-kind detector catches Y%"
  framing in the writeup. This is cheap to add — 1 day, mostly
  prose — and pushes the chapter higher.
- **No human study.** Your supervisor has now suggested one. Even
  n=5 directly answers "are these alts actually useful?" which
  examiners reach for. Whether to do this — see Supervisor
  suggestions section below.
- **All experiments are internal-consistency / fixture-based.** A
  fair examiner will note that the corpus is one investigator
  recording one fixture in one language. Threats-to-validity
  needs an explicit "single-investigator, single-fixture,
  Java-only, no longitudinal study" paragraph.

Net: realistic 70–75. To push into 75+ you need either the
baseline-comparison framing (cheap) or the n=5 human study
(more expensive). Either alone moves this 3–5 points.

## 4. Communication (15 %)

**Estimated band: 65–78 % (was 65–75 % final-achievable).**

What's helpful for the writeup:

- The four explained_results.md files are very well-structured
  (methodology / headline numbers / interpretation / caveats).
  They port to the methodology + results chapters near-verbatim
  once LaTeX-ified, with figures cropped from the dashboard or
  generated from the CSVs.
- ARCHITECTURE.md exists as scaffolding for the tool-architecture
  chapter.
- The Phase 3 PROTOCOL.md + manifest-v2.csv together document the
  experimental protocol at a level most evaluation chapters miss.

What still constrains:

- The interim text still has the typos flagged in v1.
- Conclusion file still empty.
- No GenAI disclosure section — this is rubric-mandated, so do it.
- The actual chapters (methodology, results, discussion) haven't
  been written yet.

This dimension is entirely a function of the writeup execution.
If you spend the time on it, you land at 72–78. If the last week
is rushed, you drop to 60–65 and the whole project mark slides
~1 point.

---

## What's still load-bearing risk

In rough priority order:

1. **Writeup execution.** The single biggest swing factor now.
   Methodology chapter > results chapter > everything else.
2. **Threats-to-validity section.** The per-experiment caveats are
   in the markdown but they're scattered. Consolidate into one
   section. The rubric explicitly rewards this at 70+.
3. **A few recent citations.** ~1 day of work. Lifts framing.
4. **Conclusion + GenAI disclosure + appendix.** Required by the
   rubric. ~½ day each.
5. **Baseline framing in the results chapter.** ~1 day to surface
   the internal-baseline columns as a comparison table. Lifts
   evaluation.

Everything else is optional. Specifically the two supervisor
suggestions:

---

## Supervisor's suggestions

### A. Human-vs-AI-agent trace review

Idea: run an AI agent through the same playbooks the human used,
record traces, compare what the detector flags.

**My honest take: don't do this unless writeup is finished and you
have 2+ weeks of slack.** Reasons:

- It's a 1–2 week piece of work (agent setup, prompt design,
  trace recording, analysis, writeup).
- It doesn't fill any of the named gaps (no third-party baseline,
  no human study, no recent-citation framing).
- The current corpus + experiments already tell a coherent story.
  Adding an AI-agent angle without first finishing the writeup
  risks ending up with two half-written chapters.

If you somehow have ~3 weeks of slack after writeup, this is more
interesting than a conventional human study: (a) it's genuinely
novel, (b) it's contemporary-relevant, (c) recruitment effort is
zero.

### B. Small human study (n=5)

Idea: 5–8 participants view a few analysis-report outputs in the
dashboard, rate usefulness, you write up.

**My honest take: do this only if it's cheap and won't displace
writeup time.** Reasons:

- Higher leverage on Evaluation than the AI-agent study because
  it answers "are these alts *useful*?" which examiners reach
  for.
- Rubric rewards human evaluation at 70+.
- But: even at n=5 it's ~1 week if smooth, 2 if rocky (consent
  forms, protocol, analysis, writeup).
- The current evaluation is already sufficient for a first
  **without** it.

If you have 1 spare week after writeup: do this. Otherwise skip
and flag as future work in the conclusion.

### C. If forced to pick

**Neither, unless writeup is on track and you genuinely have
slack.** The mark is now overwhelmingly writeup-quality-bound. The
experiments you have are sufficient for a first.

If supervisor pressures one or the other: pick the human study.
Cheaper, higher rubric leverage, more conventional examiner
expectation.

---

## Thesis structure recommendation

Your stated plan — background (trim ReSynth weight) → methodology
+ justification → tool architecture → experiments and results — is
the right structure. Specific notes:

1. **Background trim.** Drop ReSynth to ~½ page, keep the
   comparative framing (it positions the divergence detector). Add
   1–2 paragraphs of 2023–2025 work on developer-process logging
   or refactoring-trajectory analysis. Even one or two new
   citations help the framing dimension noticeably.

2. **Methodology chapter** (highest-leverage). Order:
   - Formal state/action/trace framing (carry from interim).
   - Process score: each of the 6 terms with cited weight
     justification + the ablation single-term recovery number for
     that term.
   - Cleanliness sub-score: per-metric methodology, PMD/CPD +
     readability sources.
   - Divergence-point detector: per-kind detection logic, the
     `MIN_*` thresholds, the magnitude-floor decision documented
     in commit `b03a7ba`.
   - Counterfactual synthesisers, kind by kind. The reorder
     synthesiser's JDT-engineering paragraph
     (`withBatchSession`, AST-hash terminal audit, DFS prefix-trie
     with git-checkout backtracking) is its own engineering
     contribution and should be flagged as such.

3. **Tool architecture chapter.** Keep brief; reuse ARCHITECTURE.md.
   The Phase A / Phase B split is the key engineering claim to
   surface here, with one diagram.

4. **Experiments and results chapter.** Three subsections, one per
   experiment. The explained_results.md files port verbatim. End
   the chapter with:
   - The reordering-rarely-helps headline finding (strongest
     positive claim of the chapter, lead with it in the
     discussion).
   - The scoped-out fixes section (transparency builds
     credibility).

5. **Threats to validity.** New section pulling together
   per-experiment caveats. Add: 45-session corpus, single
   fixture, single investigator, Java/JDT-only, no human study,
   plugin-side capture bugs as upstream noise.

6. **Conclusion + future work.** Bullet the deferred items: human
   study, ORDERING gate relaxation, plugin template-event capture,
   multi-language, AI-vs-human comparison.

7. **GenAI disclosure appendix.** Rubric-mandated. One paragraph
   per category of use (writing assistance, code assistance,
   ideation), with what you reviewed and what manual changes you
   made.

---

## Updated arithmetic

Using rubric weights (15 / 50 / 20 / 15):

| Scenario       | F  | E  | V  | C  | Weighted   | Band                     |
|----------------|---:|---:|---:|---:|-----------:|--------------------------|
| Optimistic     | 76 | 82 | 75 | 76 | **78.5 %** | comfortable first        |
| Realistic      | 74 | 80 | 72 | 72 | **75.5 %** | solid first              |
| Pessimistic    | 72 | 78 | 68 | 65 | **72.5 %** | low first                |
| v1 realistic   | 72 | 75 | 60 | 67 | 70.0 %     | borderline first         |
| v1 pessimistic | 70 | 72 | 48 | 60 | 64.0 %     | upper second             |

The pessimistic scenario in v2 is still a first class. The v1
pessimistic was an upper-second. That's the size of the
shift.

---

## Things I'm uncertain about

Same disclaimers as v1, plus:

- I haven't seen the written methodology chapter (it doesn't exist
  yet as LaTeX). When it does, my estimates will move — possibly
  significantly in either direction. The explained_results.md
  files are good drafts but markdown ≠ thesis prose.
- The 45-session corpus is hand-recorded by one investigator on
  one fixture; an examiner who reads research methodology
  textbooks may dock evaluation for small-n + single-investigator
  + same-author labelling. I haven't priced that in heavily —
  could be +/- 3 points.
- The "reordering rarely helps" finding cuts both ways. It's a
  publishable result *and* it's an admission that one of the four
  detector kinds doesn't surface useful alts on the planted
  injections. A careful examiner could call this either way; the
  framing in the writeup determines which read they take.
- I'm taking the supervisor's mention of "human-vs-AI agent" at
  face value as a suggestion. If the supervisor flagged it
  strongly, the optionality calculus changes — supervisor approval
  matters in marker calibration.

But the *direction* of v2 vs v1 — Evaluation has moved from
high-risk to low-risk, the project is now writeup-bound rather
than experiment-bound, and the realistic mark is a solid first
rather than a borderline first — I'm fairly confident in.
