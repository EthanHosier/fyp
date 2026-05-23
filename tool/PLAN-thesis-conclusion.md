# Plan: draft §7 Conclusion + future work chapter

## Context

The §6 threats chapter is done, the §5 results chapter is substantively complete, and the report's final-flow chapter slot is empty (`final_report/conclusion/conclusion.tex` is $0$ lines and not included in `main.tex`). This leaves the thesis ending on §6's list of caveats and out-of-scope items, which is a poor final impression. A reviewer scanning intro-then-conclusion (a common skim pattern, especially during initial marking passes) sees no contribution restatement, no positioning, no future-work pointers consolidated into one place.

The intended outcome is a $2$–$3$ page chapter `final_report/conclusion/conclusion.tex` that:
1. Restates what was built and what was found, in a single readable digest.
2. States clearly what the work supports and what it does not.
3. Pulls every scattered future-work pointer into one prioritised list.
4. Closes with a brief positioning paragraph that returns to the gap the introduction opened.

Drafting §7 also resolves the only outstanding undefined cross-reference in the PDF build (the `\ref{ch:conclusion}` cited from `threats.tex`).

## Target structure

Five subsections, total $\sim 2.5$ pages. Order chosen so a reader who only skims the chapter still sees the load-bearing items first.

| # | Subsection | Budget | Source |
|---|---|---|---|
| 7.1 | Summary of contributions | $\sim ½$ pg | Methodology §3 contribution list + intro contributions list |
| 7.2 | Headline findings | $\sim ¾$ pg | §5.1.3 reordering result + §5.2 precision/recall + §5.4 gain-stripped curve + §5.4.2 agent threads |
| 7.3 | What the work supports and does not | $\sim ¼$ pg | §6 distilled; this is a statement of position, not a re-derivation of threats |
| 7.4 | Future work | $\sim ¾$ pg | §6.8 out-of-scope + the seven scattered future-work pointers across the chapters |
| 7.5 | Closing | $\sim ¼$ pg | Return to the intro's gap framing |

Chapter heading: **"Conclusion"**. Label: `ch:conclusion`. File: `final_report/conclusion/conclusion.tex` (already exists, $0$ lines). Wire into `main.tex` between `threats/threats.tex` and `project_plan/project_plan.tex`.

## Per-subsection content sketches

### 7.1 Summary of contributions ($\sim ½$ page)

Single paragraph restating what was built, then a short list of the supporting deliverables.

The three primary contributions, named in methodology §3:
1. **The process-quality score** $J$ — a weighted six-term formula over a refactoring trajectory that combines the cleanliness gain across endpoints with five literature-grounded process penalties (broken time, skipped tests, manual-when-IDE rate, commit-gap count, and a length bonus for alternative trajectories).
2. **The divergence-point detector** — classifies points in a trajectory where an alternative path exists into one of four kinds (`IDE_REPLAY`, `REWORK`, `HYGIENE`, `ORDERING`), each with a concrete synthesised counterfactual trajectory.
3. **Per-kind synthesisers** — four reference-trajectory generators that produce concrete, audit-verified alternatives (reorder permutations, IDE-replayed manual edits, rework-stripped paths, metadata-flipped hygiene alts).

Supporting deliverables to mention briefly: the $45$-session hand-recorded corpus (with the $12$-session user study and $6$-session agent comparison on a disjoint fixture), the IntelliJ plugin for event capture, the JCEF dashboard, the analysis pipeline split into Phase A (expensive) / Phase B (cheap re-scoring), and the reproducible notebook covering every reported number.

### 7.2 Headline findings ($\sim ¾$ page)

Five paragraphs, each one sentence-to-three of the substantive findings that drive the thesis's claims.

1. **Reordering rarely improves the score.** The reorder synthesiser tested $194$ permutations across $45$ sessions; $24$ distinct windows; $4$ produced any positive-magnitude alternative ($16.7\%$). On the five sessions where the playbook author intended an ordering that was "worse than its alternatives", zero produced a strictly better permutation. This is a cross-cutting observation about what the score formula can detect: commuting refactorings under independent-refactoring assumptions produce identical end states, and the formula correctly sees them as indistinguishable.

2. **The kind classifier is precise on the controlled-injection corpus.** Per-kind precision $= 1.00$ across all four kinds against the inter-rater-agreed `expected_kinds` labels (with $\kappa = 1.00$ for ORDERING and IDE_REPLAY across all three rater pairs, $\kappa = 0.72$–$1.00$ for REWORK and HYGIENE). Every injection caught with a positive-magnitude alternative ranked at top-$1$ in its session.

3. **The score formula sits in a locally stable region around its production weights.** Single-knob top-$1$ stability on the canonical user-study sessions is $95.6\%$ ($1{,}239$ of $1{,}296$ rows preserve the highest-magnitude divergence point). The multi-knob Monte Carlo (every weight perturbed simultaneously from $\exp(\sigma \mathcal{N}(0,1))$, $\sigma = \ln 2$) lowers top-$1$ stability to $81.2\%$; the formula is robust to plausibly-sized perturbations and degrades smoothly under larger ones.

4. **Two human participants showed near-monotonic process-discipline improvement across the user-study arc.** Decomposing the process score by zeroing the cleanliness-gain weight isolates terms that do not depend on codebase familiarity. P1's gain-stripped score rises from $25$ at S1 to $50$ at S6 (the baseline ceiling) against a difficulty headwind; P2 trends the same direction less consistently. The agent's gain-stripped score shows no arc-position trend, varying with task structure rather than session number.

5. **The agent demonstrably course-corrected between sessions on procedural-discipline behaviours, but not on its structural limitation.** Three quoted threads from the agent transcript (Appendix~\ref{app:agent-transcript}): commit cadence revised twice (under-committing in S1, over-correcting in S3, resolved by S6 atomic-edit synthesis); smell-introduction self-diagnosis after S3 prevented recurrence in S4–S5; IDE-replay flags persisted across the arc because the agent has no IDE refactor surface to switch to (acknowledged in its own S6 reflection).

### 7.3 What the work supports and does not ($\sim ¼$ page)

Compact paragraph (or two). Avoids re-deriving the threats chapter; just states the position.

**Supports**: a kind classifier whose precision and rank-1 prominence are corpus-conditional but reproducible; a score formula whose ranking is locally robust to weight choices and whose terms have measurable per-term influence; a descriptive observation that humans receiving the tool's feedback showed process-discipline improvement across a six-session arc; an observation that a coding agent can interpret the same feedback in writing and adjust between sessions. **Does not support**: causation between feedback exposure and observed improvement (no control condition, $n = 2$ humans, $n = 1$ agent); generalisation to team-PR-driven workflows or to other IDEs and languages (the plugin is IntelliJ-specific and the synthesisers are JDT-specific); calibration of the production weights against an external outcome dataset (no such dataset exists in this problem space). The threats chapter at §\ref{ch:threats} discusses each in detail.

### 7.4 Future work ($\sim ¾$ page)

Itemised list, ordered by what would most strengthen the chapter's load-bearing claims.

1. **Multi-agent comparison.** The agent-comparison chapter rests on one agent run with one model version on one date. A multi-agent extension (different models, multiple dates) would separate model-specific behaviour from agent-class behaviour and would let the qualitative threads in §\ref{sec:results-userstudy-agent} become quantitative.

2. **IDE-refactor-as-agent-tool deployment.** The IDE-replay structural limitation reported in §\ref{sec:results-userstudy-agent} is the cleanest example in the chapter of feedback being correctly interpreted but inactionable: the agent acknowledges the IDE refactoring would score better but has no way to invoke it. Exposing IntelliJ refactor actions as agent tool calls would close that gap and let the IDE_REPLAY count actually respond to feedback across sessions.

3. **Real-time, live-feedback dashboard.** The current dashboard is a session-replay view. A live mode that surfaces divergence points during the session would let the participants self-correct in-trajectory rather than between sessions. The closing paragraph of §\ref{sec:results-userstudy} flags this as the natural next step.

4. **Cross-IDE and cross-language validation.** The plugin's instrumentation uses IntelliJ's PSI and VFS hooks, which have no direct equivalent in VSCode or Eclipse, so a port would require re-implementing the event-capture layer. Cross-language is doubly hard because the refactoring engine is JDT-specific; a TypeScript or Python port would need a different refactoring miner alongside.

5. **Longitudinal validation against external code-quality outcomes.** No dataset linking refactoring-trajectory signals to downstream maintenance cost exists, but constructing one (e.g.\ from a multi-year industry repository with linked defect-tracking data) would let the production weights be refit against an outcome variable rather than against the literature-supported severity orderings used here.

6. **Rater-rank validation.** The Cohen's $\kappa$ study validates kind-presence agreement; a Kendall $\tau$-b study on per-session divergence-point rankings would validate that the score's order of divergence points within a session matches human expert ranking. This is the largest gap between what the chapter claims and what would maximally strengthen the claim.

7. **Reorder enumerator beam search.** The hard $7$-step window cap (§\ref{sec:threats-implementation}) means widest-window reorderings are silently dropped on sessions with long contiguous refactor sequences. Replacing the exhaustive enumerator with a beam-search or pruning heuristic would lift the cap without the factorial cost.

8. **Relaxed validator check.** The ORDERING recall gap ($0.36$) is documented and deliberate (§\ref{sec:results-scoped-out}); relaxing the `splitOnInvalid` gate would surface more windows but, by the same commuting-refactor argument as §5.1.3, those additional windows would mostly carry magnitude zero. Cosmetic recall improvement; named here for completeness.

### 7.5 Closing ($\sim ¼$ page)

One short paragraph that returns to the gap the introduction opened. Frame the thesis's main contribution as a **shift in evaluation primitive**: from snapshot-level "is the code better after the refactoring" to trajectory-level "was the process by which we got there one a careful developer would have followed". The endpoint-only view is what the literature defaulted to; the trajectory-level view is what the tool of this thesis operationalises. The specific numbers in §5 matter, but the conceptual artefact a reader should leave with is that every refactoring trajectory has measurable counterfactuals at concrete points, and the gap between them is measurable in a way snapshot-only evaluation cannot reach.

## Tone and diction constraints

Same as the §6 prose and the §5 amendments:
- Plain vocabulary; no "fascinating" / "crucially" / "it is worth noting".
- Avoid the "we organise" / "we structure" register that breaks fourth wall. State what the chapter does in third person.
- Numbers and counts spelled out; no qualitative hedge words ("substantial", "considerable") in place of figures.
- Avoid repeating §5 prose verbatim — the conclusion's job is to consolidate and position, not to recap.
- The closing paragraph (7.5) is the one place where a slightly more reflective register is acceptable; it is the only paragraph that can step away from the load-bearing-numbers framing. Stay short.
- Reference but do not re-derive §5 numbers — `\S\ref{sec:results-...}` cross-references rather than recomputing.

## Critical files

**To edit**:
- `final_report/conclusion/conclusion.tex` — currently $0$ lines; write the full $2$–$3$ page chapter here.
- `final_report/main.tex` — add `\input{conclusion/conclusion.tex}` between the threats and project_plan inputs (line $51$).

**To reference** (read-only):
- `final_report/results/results.tex` — for §5 cross-references (results-userstudy, results-userstudy-agent, results-headline, results-divergence, results-sensitivity).
- `final_report/threats/threats.tex` — for §6 cross-references (threats-implementation, threats-out-of-scope, ch:threats).
- `final_report/methodology/methodology.tex` — for the contribution-list framing at chapter start.
- `final_report/introduction/introduction.tex` — for the gap framing the closing paragraph returns to.

## Verification

1. **PDF builds clean**: `final_report/build.sh --light` → "Output written", no undefined references after `\input{conclusion/conclusion.tex}` is added. The previously-undefined `\ref{ch:conclusion}` in threats.tex should now resolve.
2. **Length within budget**: chapter renders at $2$–$3$ pages; if over, compress 7.4 future-work (most likely to balloon).
3. **No new claims introduced**: every number in §7 must appear in §5 with the same value. The conclusion consolidates rather than introduces.
4. **Tone match**: grep for AI-tell phrases (`fascinating`, `crucially`, `it is worth noting`, em-dashes-as-bullets, "we structure", "we organise") in the new chapter — should turn up nothing.
5. **TOC entry**: `Chapter 7 Conclusion` appears in the table of contents at the expected position between Chapter 6 and Project Plan.

## Out of scope (this plan, not the chapter)

- Bibliography audit (task 8).
- GenAI disclosure appendix (task 9).
- Porting the agent transcript into appendix (task 9b).
- §5 final read-through (task 5b).
- Updating the master writeup plan to mark §7 done — do that after the chapter lands, not as part of this task.