# Plan: Background chapter pass

## Context

The interim Background chapter
(`interim_report/background/background.tex`, ~230 lines / ~9 pages,
7 sections) was the strongest single piece of the interim
submission. Per HONEST_REVIEW v1 it was at the 70–84 % band on
framing, with the only weaknesses being (i) too much weight on
ReSynth in §2.5, (ii) no 2023–2025 citations, (iii) a gentle gap
statement, and (iv) a handful of typos.

The intro pass already added 7 new bib entries (RefactorBench,
Bouzenia-Pradel, AlOmar SLR, SWE-Gym, Eilertsen, the
RefactoringMiner 3.0 reference, the GumTree 2024 reference). Most
of these belong in Background as their primary home — the intro
just dropped two cites to set up the gap; the actual landscape
discussion lives here.

This pass is the 2-day item #2 from `PLAN-thesis-writeup.md`.
Goal: trim, modernise, sharpen, and clean — without rewriting from
scratch. The interim's section structure is good and the prose is
mostly fine. This is a surgical pass.

All edits land on `final_report/background/background.tex`.
Interim is frozen.

## Changes to make

### 1. Update chapter intro paragraph (line 4)

The current chapter intro only signposts §2.1–§2.4. It silently
drops §2.5 (recommendation / synthesis), §2.6 (process-aware
logging), and §2.7 (summary).

Add a short clause covering all three. Something like:

> *"...and finally surveys refactoring recommendation and
> sequence synthesis (\S\ref{sec:recommendation-synthesis}), prior
> work on capturing developer process traces
> (\S\ref{sec:process-aware-logging}), and the resulting
> positioning of this thesis
> (\S\ref{sec:background-summary})."*

### 1b. Tighten the formal framing in §2.1.3 (state/action/trace formalism)

**Why:** HONEST_REVIEW_2 §1 (Framing) notes that reaching the higher
band needs *"more recent-citation work + a tighter formal framing"*.
The intro-pass research (Prompt 2) found direct formal precedent in
recent SE work — useful for linking the thesis's formalism to a
recognised research lineage rather than presenting it in isolation.

Currently §2.1.3 defines $\tau = (s_0, a_0, s_1, a_1, \dots,
a_{T-1}, s_T)$ in isolation. The plan is to add 1–2 sentences at
the end of §2.1.3 linking this formalism to recent SE precedent:

> *"This state/action/trace framing is consistent with recent
> software-engineering work that models agent and developer
> behaviour as a partially-observable Markov decision process,
> where a trajectory is a sequence of (action, observation) pairs
> \cite{gautam2025refactorbench,bouzenia2025trajectories}. We
> retain the explicit $s_t$ notation in addition to actions because
> intermediate states — rather than only observations — are the
> primary object scored by the process-quality metric defined in
> Chapter~3."*

The second sentence is load-bearing: it explains *why* this
thesis's formalism diverges from the agent-literature convention
(the thesis scores states; the agent literature observes them).
Making the difference explicit defends the formalism choice
against an examiner who knows the POMDP literature.

### 2. Trim ReSynth in §2.5.1 (lines 154–160)

The current §2.5.1 spends ~6 lines on ReSynth alone, plus another
~3 on REFAZER. Compress ReSynth to ~3 lines covering only the
load-bearing facts:

- It synthesises a sequence of IDE refactorings consistent with a
  developer's partial edits.
- Search space is constrained by edited entities and operator
  pre/postconditions.
- Objective is *reachability and consistency*, not process-quality
  evaluation.

Drop the technical detail about "transforming the original program
into a version that matches the developer's partial edits" — the
methodology chapter doesn't need this level of operator-mechanics
exposition.

Net target: §2.5.1 goes from ~½ page to ~⅓ page.

### 3. Replace stale RefactoringMiner / GumTree citations

Per the Prompt-3 currency check on PLAN-thesis-intro.md, the
canonical refs have moved. Sweep:

- Line 122 `\cite{falleri2014gumtree}` → `\cite{falleri2024gumtree}`.
- Line 124 `\cite{tsantalis2020refactoringminer}` → `\cite{alikhanifard2025rminer}`.
- Line 140 `\cite{tsantalis2020refactoringminer,silva2017refdiff}` →
  `\cite{alikhanifard2025rminer,silva2017refdiff}`.
- Line 226 `\cite{tsantalis2020refactoringminer,falleri2014gumtree}` →
  `\cite{alikhanifard2025rminer,falleri2024gumtree}`.

Background doesn't discuss the 2.0 → 3.0 evolution, so no need to
cite both old and new. The 2018/2020 RefactoringMiner refs and the
2014 GumTree ref stay in the bib for any chapter that wants them.

### 4. Fix undefined-citation warnings on line 217

`poncin2011rabbiteclipse` and `damevski2014visualstudio` don't
resolve — they trigger LaTeX warnings on every compile. Replace
with the already-cited papers from the same authors that *do*
exist in the bib:

- `poncin2011rabbiteclipse` → `poncin2011processmining`
- `damevski2014visualstudio` → `damevski2017usagesmells`

Same intent (segmentation of IDE event streams into coherent
activities), cleaner bib.

### 5. Insert a new paragraph in §2.5 (or new §2.5.7) citing 2023–2025 trajectory work

After §2.5.6 ("Synthesis: what sequence-generation work enables,
and what remains missing"), add a short subsection or paragraph:

> *"Recent work has begun to formalise software-engineering agent
> behaviour as a trajectory of (action, observation) pairs in a
> POMDP-shaped environment [Gautam 2025; Bouzenia & Pradel 2025;
> Pan et al. 2025], and a recent systematic literature review of
> LLM-based refactoring research identifies process-aware
> evaluation as an open gap in the field [AlOmar 2025]. These
> developments confirm that the trajectory framing is gaining
> traction in concurrent SE research, but the work to date targets
> agent behaviour and benchmark task success rather than human
> developer process quality. Empirical observational studies
> independently confirm that real-world refactoring proceeds as
> ordered sequences of operations interleaved with other change
> activities [Eilertsen 2024], motivating the trajectory-level
> framing adopted in this thesis."*

Cites used: `gautam2025refactorbench`, `bouzenia2025trajectories`,
`pan2025swegym`, `alomar2025slr`, `eilertsen2024refactorings`. All
already in the bib from the intro pass.

### 6. Refresh §2.6 with recent IDE-logging work (depends on Research-agent Prompt B1 below)

The current §2.6 is anchored on Poncin 2011 and Damevski 2017.
Both are ≥7 years old. The intro pass didn't surface a fresh
IDE-logging citation, so Background needs its own research-agent
pass for this. Slot in 1–2 paragraphs once the agent returns.

**Contingency if Prompt B1 returns nothing strong.** If the
literature on recent IDE-event logging for refactoring trajectories
is genuinely thin (which is plausible — the area has been quiet
since the mid-2010s), then:

- Leave §2.6 anchored on Poncin/Damevski/Foutsekh as is — these
  are still the canonical references.
- Add a single honest sentence in §2.6 noting that recent
  IDE-telemetry work has shifted toward LLM-agent traces rather
  than human-developer traces (cross-reference the Change #5
  paragraph for the agent-trajectory work that *did* appear).
- Flag "modern IDE-telemetry studies of refactoring-specific
  human-developer traces" as an open area in the Future Work
  section of the conclusion chapter.

This contingency matches the treatment given to Prompt B2 (search-
based refactoring) — no padding with weak fits, transparency about
where the literature is thin is itself a finding for the chapter.

### 7. Optionally refresh §2.5.2 with 2023–2025 search-based work (Prompt B2)

§2.5.2 cites Harman 2007 + Mohan 2019 + Paixão 2017 — all >5 years
old. If the agent surfaces fresh multi-objective refactoring
search work (2023–2025), add a single sentence noting the line of
work continues. If nothing strong returns, leave as is — the
classics are still the canonical refs and not citing recent search
work is a smaller methodological miss than not citing recent
trajectory work.

### 8. Sharpen the gap claim in §2.7 (lines 228–230)

Currently reads as: *"...there is comparatively little work that
explicitly evaluates the quality of a developer's refactoring
trajectory between a known start and end state."*

Strengthen to mirror the intro's harder formulation:

> *"As far as we are aware, no prior system both (i) scores a
> developer's refactoring trace against an explicit
> process-quality metric and (ii) produces counterfactual reference
> trajectories tied to measurable divergence points. The closest
> existing work — sequence synthesis, search-based recommendation,
> and the recent trajectory-formalism studies surveyed above —
> generates or analyses sequences as the primary object, treating
> the algorithm's output as the solution rather than evaluating a
> developer's chosen path against alternatives. This thesis closes
> that gap by..."*

Then keep the existing thesis-question sentence.

### 9. Typo + formality pass

Specific known typos:

- Line 20 "may be a mixed of the two" → "may be a mix of the two"
- Line 49 "DECOR, wdefines smells" → "DECOR, which defines smells"
- Line 54 "granularity of classs" → "granularity of classes"
- Line 66 "the the choice" → "the choice"
- Line 66 "[will be defined]" → "(defined in the methodology
  chapter)" or "(formally introduced in
  \S\ref{sec:methodology-process-score})"
- Line 66 "comprise of" → "comprise" (it's the verb form; no "of")
- Line 120 "A lot of work focuses on" → "Much of this work focuses
  on"
- Line 120 "you usually need to either" → "one usually needs to
  either"
- Line 124 "Building on these foundations" — fine but redundant
  with the previous paragraph; consider tightening
- Line 214 "and then apply process mining techniques to infer
  developer workflows \cite{poncin2011processmining}. This gives a
  clear example of turning low-level interaction events into
  higher-level activity models, such   as sequences" — fix double
  space "such   as" → "such as"
- Line 214 "interpretation.It" → "interpretation. It" (missing
  space after full stop)
- Line 224 "strong foundations" → "a strong foundation"
- Line 226 "primarly" → "primarily"
- Line 226 "metrics. \cite{paiva..." → "metrics
  \cite{paiva..." (stray full stop before cite)
- Line 228 "research on can \emph{detecting} refactorings" →
  "research on \emph{detecting} refactorings"
- Line 228 "refactorig trajectory" → "refactoring trajectory"

Plus a general pass for double-spaces, comma placement, en/em-dash
consistency. Smart-quote → LaTeX-quote conversion if needed (the
file already mostly uses LaTeX-style \`\`...\'\').

### 10. Cross-citation prep for the methodology chapter (no edits — just a comment)

Note for methodology chapter: when justifying time-weighted
`W_BROKEN`, cite RefactorBench's failure-mode #2 (Gautam 2025)
which observes that strictly rejecting intermediate broken states
hurts agent performance because broken intermediate states are
sometimes necessary. Drop a `% TODO methodology cross-cite` next
to the gautam2025refactorbench citation in Background §2.5.

## Tone preservation notes

The Background voice is slightly more academic than the intro —
longer sentences, more nominalisations, more "this thesis", fewer
"we"s. Stay consistent with that. Specifically:

- Keep: "A representative example is...", "Building on these
  foundations,...", "This is relevant because..." — these are the
  chapter's existing rhythm.
- Avoid: "We argue that...", "Our approach is...". Background is
  third-person-leaning; first-person plural is sparing.
- The chapter uses `\textbf{...}` for thesis-distinguishing terms
  and `\emph{...}` for technical terms-of-art. Preserve the
  pattern.
- Topic-sentence-first paragraphs throughout. New paragraphs
  should follow this pattern.

## Research-agent prompts

Two prompts needed. Both are scoped tighter than the intro
prompts — Background already has a strong foundation; we're
patching specific subsections, not rebuilding the survey.

### Prompt B1 — Recent IDE-event logging / developer-trace capture (load-bearing)

> "I'm writing the related-work chapter of an MEng thesis on
> evaluating developer refactoring trajectories. I have a
> subsection on IDE event logging and developer interaction
> mining that is currently anchored on Poncin et al. 2011 (Eclipse
> event logs + process mining) and Damevski et al. 2017 (Visual
> Studio usage smell mining). Both are 7+ years old.
>
> Please find up to 3 papers from 2023–2025 (any of ICSE, FSE, ASE,
> TSE, EMSE, MSR, SANER, IST, JSS, CHASE, arXiv) on:
> (a) Large-scale IDE telemetry / event-stream collection for
>     developer-process analysis.
> (b) Segmenting IDE interaction streams into semantic activities
>     or workflow steps.
> (c) Privacy-preserving developer-trace capture (e.g. on-device
>     summarisation, federated logging).
> (d) Recent extensions of process-mining-in-SE applied to
>     developer activities (not CI/CD pipelines, which are
>     out-of-scope).
>
> Return up to 3 papers. For each: full citation, one-sentence
> relevance, and which of (a)/(b)/(c)/(d) it touches. I'd rather
> have 1 strong paper than 3 weak ones. If the literature is thin
> on (c) and (d), say so explicitly — that itself is useful
> information for the chapter."

### Prompt B2 — Recent search-based / multi-objective refactoring (low-priority, optional)

> "Background-chapter freshness check. My §2.5.2 cites Harman &
> Tratt 2007 and Mohan & Greer 2019 on multi-objective search-based
> refactoring, and Paixão 2017 on the optimisation-vs-disruption
> trade-off. None are recent.
>
> Has there been notable 2023–2025 work on multi-objective or
> many-objective search-based refactoring, or on the
> metric-improvement vs disruption trade-off, that I should cite
> as 'work in this line continues'? I don't need a full survey —
> 1–2 representative papers is enough, ideally from ICSE/FSE/ASE/
> GECCO/SSBSE/TSE/EMSE. If the line is dormant in this period,
> say so — that's fine.
>
> Return up to 2 papers (or 0). Citation + one sentence each."

## Critical files

- `/Users/ethanhosier/Desktop/random/fyp/final_report/background/background.tex`
  — only file to edit substantively.
- `/Users/ethanhosier/Desktop/random/fyp/final_report/bibs/sample.bib`
  — add any new entries from Prompts B1 / B2 (most of the
  trajectory-and-LLM cites already added from intro pass).
- Reference (read-only):
  - `HONEST_REVIEW_2.md` for framing critique.
  - `PLAN-thesis-intro.md` for the sharpened gap-claim language
    (so Background's §2.7 stays consistent with intro §1.2).
  - `analysis/.../experiment/explained_results/divergence.md`
    for the reordering-finding language Background should align
    with.

## Verification

1. **Compile cleanly.** `cd final_report && bash build.sh`. The two
   pre-existing undefined-citation warnings
   (`poncin2011rabbiteclipse`, `damevski2014visualstudio`) should
   disappear after Change #4. No new warnings should appear.
2. **Word-count delta:** rough net change should be neutral or
   slightly positive — Change #2 removes ~½ page (ReSynth trim),
   Change #5 adds ~⅓ page (trajectory paragraph), Change #6 adds
   ~½ page (recent IDE-logging work). Net ~+⅓ page.
3. **Cross-reference consistency:** the intro's gap claim and the
   Background §2.7 gap claim should be near-identical in wording
   — examiners reading both should not feel they are reading two
   different gap statements.
4. **Citation sweep:** `grep -n 'tsantalis2018\|tsantalis2020\|falleri2014' final_report/background/background.tex`
   should return zero hits after Change #3.
5. **Typo sweep:** spot-check the typo list against the file after
   the pass — each known typo should be fixed.
6. **Read top-to-bottom once.** The chapter should still flow as a
   single argument culminating in the gap statement. New
   paragraphs should not stick out as stylistic outliers.

## What this pass deliberately doesn't do

- **Rewrite §2.3 metric-candidates table.** The table is good; no
  changes needed.
- **Restructure §2.5's six subsections.** Tempting to merge but
  risky for a 2-day pass. Leave the structure as is.
- **Add a "threats-to-validity preview" paragraph.** That belongs
  in the threats-to-validity chapter (#6 in master plan), not
  Background.
- **Re-do the state/action/trace formalism subsection.** The
  methodology chapter will define it formally; Background's brief
  intro is sufficient and is already cross-referenced from §2.6
  via the segmentation-granularity discussion.

---

## Research-agent results: Prompt B1 (IDE-event logging, 2023–2025)

### Executive summary

**The literature is unexpectedly thin.** One STRONG fit emerged
(CodeWatcher, ICSME 2025). Bullets (b) segmentation, (c)
privacy-preserving capture, and (d) process-mining-in-SE for
developer activities returned **nothing strong** in 2023–2025 —
the community has pivoted hard toward LLM-assisted-coding
telemetry and CI/CD-flavoured process mining, leaving classical
IDE-side activity mining largely untouched since the late 2010s.

This is **itself a citable observation** for the chapter — it
strengthens the §2.7 gap claim rather than weakening it.

### Single fit

**[BASHA-2025]** Basha, M., Ribeiro, A.~M., Javahar, J., de
Souza, C.~R.~B., Rodr{\'i}guez-P{\'e}rez, G. *CodeWatcher: IDE
Telemetry Data Extraction Tool for Understanding Coding
Interactions with LLMs.* ICSME 2025, Tool Demonstration Track.
arXiv:2510.11536.

- **What it does:** Lightweight client-server VS Code extension
  that captures fine-grained, semantically labelled IDE events
  (LLM-tool insertions, deletions, copy-paste, focus shifts) with
  timestamps for post-hoc reconstruction of coding sessions.
- **Touches:** (a) primarily; brushes (b) via post-hoc session
  reconstruction (no automated activity-classification algorithm
  presented).
- **Why it matters here:** Direct successor in spirit to Damevski
  2017 / Poncin 2011. The most recent infrastructure paper for
  IDE event capture aimed at behavioural analysis, motivated by
  studying developer-LLM interaction patterns — exactly the 2025
  framing §2.6 needs. Cite to argue *"modern IDE-telemetry
  infrastructure still ships as research-only tooling and still
  leaves the segmentation/activity-inference problem open."*
- **Strength of fit:** STRONG.

### Calibration finding (use this in the chapter)

Bullets (b), (c), and (d) are **genuinely thin in 2023–2025**:

- **(b) Segmentation of IDE interaction streams.** No 2023–2025
  paper meaningfully advances on Burattin 2018 or Damevski 2017.
  Recent "developer activity recognition" hits are
  computer-vision/HAR work, not SE.
- **(c) Privacy-preserving developer-trace capture.** Lives in the
  systems/security community (Apple, Microsoft, Mozilla, Brave,
  Nebula) and does not target developer events.
- **(d) Process-mining-in-SE for developer activities.** Recent
  work (e.g. Dorado et al. arXiv:2510.25935 CodeSight; recent
  ScienceDirect agile-process-mining) consistently operates on
  GitHub PR / CI logs rather than IDE event streams.

### How to use this in §2.6

**Plan-of-record update for Change #6:**

Cite **CodeWatcher 2025** as the single 2023–2025 anchor for §2.6,
paired with the existing Poncin 2011 + Damevski 2017 +
Foutsekh 2018. Add a positioning sentence that turns the gap
finding into an asset for the chapter, e.g.:

> *"The 2023–2025 IDE-telemetry literature has pivoted toward
> capturing developer-LLM interactions
> \cite{basha2025codewatcher} rather than refactoring-specific
> developer process traces, and process-mining-in-SE has migrated
> to repository / pipeline logs rather than IDE event streams.
> Classical IDE-side activity mining for refactoring trajectories
> therefore remains an area where canonical references are over
> seven years old — a gap this thesis addresses through its own
> custom IDE plugin."*

This is **better than padding with weak fits**: it explicitly
claims an empirical gap the thesis fills, and the gap claim is
defensible because the lit search supports it.

### Action items for the writeup

- **Add `basha2025codewatcher` to `final_report/bibs/sample.bib`.**
  Suggested entry (verify on arxiv before committing):
  ```bibtex
  @inproceedings{basha2025codewatcher,
    title     = {{CodeWatcher}: {IDE} Telemetry Data Extraction Tool for Understanding Coding Interactions with {LLMs}},
    author    = {Basha, Manaal and Ribeiro, Aim{\^e}e M. and Javahar, Jeena and de Souza, Cleidson R. B. and Rodr{\'i}guez-P{\'e}rez, Gema},
    booktitle = {Proceedings of the 41st IEEE International Conference on Software Maintenance and Evolution (ICSME), Tool Demonstration Track},
    year      = {2025},
    eprint    = {2510.11536},
    archivePrefix = {arXiv},
    primaryClass  = {cs.SE}
  }
  ```
- **In §2.6:** add the positioning sentence above (or a slight
  rewording) plus a single sentence stating that CodeWatcher is
  the most recent infrastructure paper of its kind, then return
  to the existing Poncin/Damevski/Foutsekh discussion.
- **Drop the "contingency" path** in Change #6 — Prompt B1 found
  a fit, so we do not need to fall back to the no-recent-citation
  story. (The gap finding for (b)/(c)/(d) is folded into the
  positioning sentence instead.)
- **Update §2.7 gap claim** (Change #8) to also reference the
  IDE-telemetry gap: a phrase like *"... and IDE-telemetry
  infrastructure for refactoring-specific developer traces has
  itself stagnated since the late 2010s \cite{basha2025codewatcher
  is the lone recent infrastructure paper}"* — actually just leave
  the §2.7 wording as drafted; the §2.6 positioning sentence
  already makes the point and §2.7 doesn't need to repeat.

### Sources

1. https://arxiv.org/abs/2510.11536 — CodeWatcher arXiv abstract
2. https://conf.researchr.org/details/icsme-2025/icsme-2025-tool-demonstration/9/CodeWatcher-IDE-Telemetry-Data-Extraction-Tool-for-Understanding-Coding-Interactions
   — ICSME 2025 listing
3. https://arxiv.org/pdf/2510.11536 — full PDF
4. https://arxiv.org/abs/2510.25935 — CodeSight (excluded: CI/CD)
5. https://arxiv.org/abs/2506.11019 — Mind the Metrics
   (excluded: LLM-tooling, not developer-event)
6. https://2025.msrconf.org/track/msr-2025-technical-papers —
   MSR 2025 (surveyed, nothing matches)

---

## Research-agent results: Prompt B2 (search-based refactoring, 2023–2025)

### Executive summary

**The line is not dormant.** Two clear extensions in 2023–2024,
both explicit NSGA/Pareto formulations in the Harman/Mohan lineage.
One STRONG and one MEDIUM fit. Recommend citing the STRONG one as
the single "work in this line continues" anchor in §2.5.2; the
MEDIUM one is optional flavour.

This upgrades Change #7 in this plan from "optional" to
"recommended": add a single sentence and one citation. ~30
seconds of writing.

### Fits

**[CORTELLESSA-2023]** Cortellessa, V., Di Pompeo, D., Stoico, V.,
Tucci, M. *Many-Objective Optimization of Non-Functional
Attributes based on Refactoring of Software Models.* Information
and Software Technology, 2023. DOI: 10.1016/j.infsof.2023.107159.
arXiv:2301.09531.

- **What it does:** Uses NSGA-II to search Pareto frontiers of
  model-level refactoring sequences across performance,
  reliability, performance-antipattern count, and an
  *"architectural distance"* objective quantifying refactoring
  effort.
- **Why I'd cite it:** Direct continuation of the Mohan/Greer
  many-objective formulation *and* Paixão's optimisation-vs-
  disruption trade-off — *"architectural distance"* is essentially
  a disruption metric treated as a first-class Pareto objective.
- **Strength of fit:** **STRONG**.

**[CHEN-2024]** Chen, L., Hayashi, S. *MORCoRA: Multi-Objective
Refactoring Recommendation Considering Review Availability.*
International Journal of Software Engineering and Knowledge
Engineering, 34(12):1919–1947, 2024. DOI:
10.1142/S0218194024500438. arXiv:2408.06568.

- **What it does:** Multi-objective search over refactoring
  sequences jointly optimising code-quality improvement, semantic
  preservation, and reviewer availability/expertise.
- **Why I'd cite it:** Extends the Harman-lineage formulation with
  a socio-technical disruption proxy (reviewer cost), echoing
  Paixão's *"cost of metric-improvement"* framing in a 2024
  venue.
- **Strength of fit:** MEDIUM (journal is mid-tier; objective set
  drifts from pure structural disruption toward review-process
  cost).

### How to use this in §2.5.2

**Plan-of-record update for Change #7:** add a single sentence
near the end of §2.5.2 (currently anchored on Harman 2007,
Mohan 2019, Paixão 2017):

> *"This line of work continues into the present: recent
> many-objective formulations explicitly treat architectural
> distance — a direct disruption proxy — as a first-class Pareto
> objective alongside structural quality
> \cite{cortellessa2023manyobjective}, and related work has
> extended the framework with socio-technical cost proxies such
> as reviewer availability \cite{chen2024morcora}."*

The Cortellessa cite is the load-bearing one. The Chen cite is
optional flavour; if word budget is tight, drop it.

### Action items

- **Add `cortellessa2023manyobjective` to
  `final_report/bibs/sample.bib`.** Suggested entry:
  ```bibtex
  @article{cortellessa2023manyobjective,
    title     = {Many-Objective Optimization of Non-Functional Attributes based on Refactoring of Software Models},
    author    = {Cortellessa, Vittorio and Di Pompeo, Daniele and Stoico, Vincenzo and Tucci, Michele},
    journal   = {Information and Software Technology},
    year      = {2023},
    doi       = {10.1016/j.infsof.2023.107159},
    eprint    = {2301.09531},
    archivePrefix = {arXiv},
    primaryClass  = {cs.SE}
  }
  ```
- **Optionally add `chen2024morcora`:**
  ```bibtex
  @article{chen2024morcora,
    title     = {{MORCoRA}: Multi-Objective Refactoring Recommendation Considering Review Availability},
    author    = {Chen, Lerina and Hayashi, Shinpei},
    journal   = {International Journal of Software Engineering and Knowledge Engineering},
    volume    = {34},
    number    = {12},
    pages     = {1919--1947},
    year      = {2024},
    doi       = {10.1142/S0218194024500438},
    eprint    = {2408.06568},
    archivePrefix = {arXiv},
    primaryClass  = {cs.SE}
  }
  ```
- **Verify the Cortellessa author/affiliation list** before
  committing the bib entry (the agent surfaced names from a few
  different listings).

### Sources

1. https://arxiv.org/abs/2301.09531 — Cortellessa et al. 2023
   arXiv abstract
2. https://www.sciencedirect.com/science/article/pii/S0950584923000137
   — IST 2023 (paywalled)
3. https://dblp.org/rec/journals/infsof/CortellessaPST23.html —
   dblp record
4. https://arxiv.org/abs/2408.06568 — MORCoRA arXiv abstract
5. https://www.worldscientific.com/doi/10.1142/S0218194024500438
   — IJSEKE
6. https://arxiv.org/abs/2308.15179 — Di Pompeo et al. 2025
   (same lineage but methodology-focused, not recommended as a
   §2.5.2 anchor)
