# Plan: Introduction chapter rewrite

## Context

The interim intro
(`interim_report/introduction/introduction.tex`, ~32 LaTeX lines,
4 sections + diagram) was written before the empirical
experiments existed. It states the thesis question and sketches
the approach but is currently weak on:

- Gap claim ("They do not directly address...") — too gentle now
  that the thesis has measured precision/recall against ground
  truth.
- No explicit contributions list — examiners expect one.
- No roadmap paragraph at the end (the interim's own `thesis_plan.md`
  called for one).
- Several typos previously flagged ("differnet", "this is matters",
  "before arriving to a final outcome").
- ReSynth gets ~3 sentences in Research Objectives — slight trim
  consistent with the planned Background trim.

**The interim report is preserved as-is.** All edits for the final
thesis happen on a copy at
`/Users/ethanhosier/Desktop/random/fyp/final_report/` (one
directory above `tool/`). The interim continues to compile from
its own directory without interference.

Per `PLAN-thesis-writeup.md` this is a ½-day pass. The intro
should preview the empirical findings without spoiling them, and
ground the gap statement in what the rest of the thesis actually
demonstrates.

## Pre-step (one-time setup before chapter work begins)

- `cp -R /Users/ethanhosier/Desktop/random/fyp/interim_report \
        /Users/ethanhosier/Desktop/random/fyp/final_report`
- Confirm `final_report/main.tex` compiles unchanged before
  starting any edits, so any later breakage is unambiguously
  due to the intro rewrite, not the copy.

## Changes to make

### 1. Sharpen the gap statement (Motivation section)

Current closing-paragraph of Motivation says, roughly:

> "These perspectives largely evaluate refactoring as a mapping
> from initial to final state. They do not directly address
> whether the trajectory taken... was efficient, low-risk, or
> 'good' under any explicit criteria."

Strengthen to something like:

> "These perspectives evaluate refactoring as a mapping from
> initial to final state, leaving the trajectory between those
> states largely outside the picture. As far as we are aware, no
> prior system both (i) scores a developer's refactoring trace
> against an explicit process-quality metric and (ii) produces
> counterfactual reference trajectories tied to measurable
> divergence points. The closest work either synthesises a path
> to reach an end state (without judging the developer's path)
> or recommends refactorings on an architectural target (without
> reference to an observed process)."

Keep the voice the user already uses — "as far as we are aware",
"the closest work either... or...", not "to the best of our
knowledge no prior approach simultaneously addresses...". The
sharpened claim must survive into the Background gap section.

### 2. Add a brief empirical-preview sentence (Motivation section)

Without spoiling Chapter 5 results, signpost that the thesis
includes a measured evaluation. One sentence near the end of
Motivation:

> "We instantiate this framing in a tool, evaluate it on a
> 45-session corpus of recorded refactoring sessions with
> hand-labelled ground truth, and report per-kind precision and
> recall plus a sensitivity/ablation analysis of the underlying
> score formula."

This is honest signposting, not result-quoting. The reordering
finding stays in Chapter 5.

### 3. Add an explicit Contributions subsection

After Research Objectives, before Approach and Scope, insert a
short `\section{Contributions}` with a 4-item list. Keep the
language plain — not "we contribute the following novel
algorithmic advances" but more like:

- A process-quality metric for refactoring trajectories with each
  term grounded in cited prior work, validated by sensitivity
  and ablation analysis.
- A first-class divergence-point detector that classifies
  divergence into four kinds (ORDERING, IDE_REPLAY, REWORK,
  HYGIENE) and produces a counterfactual reference trajectory
  for each.
- A 45-session hand-recorded corpus on a purpose-built Java
  fixture, multi-label ground-truth annotated, plus the
  experimental drivers (sensitivity, ablation, divergence)
  needed to evaluate the detector on it.
- An empirical evaluation that establishes per-kind precision
  and recall, surfaces what the score formula measurably
  captures, and identifies which categories of
  process-improvement the formula is structurally insensitive to.

The fourth point is where the reordering finding lives in
spirit — phrased generally, not specifically. The Chapter 5
discussion does the specific empirical work.

### 4. Trim ReSynth in Research Objectives

The current ReSynth/Refactoring Navigator paragraph is ~6 lines
and gives ReSynth too much weight. Trim ReSynth to one sentence
("ReSynth synthesises a path to reach a target end state
[cite]") and leave Navigator as it is. Keep the search-based
refactoring citation. The full critical treatment lives in
Background.

### 5. Add a roadmap paragraph at the end

After the divergence diagram, add ~4 sentences:

> "The remainder of this thesis is structured as follows.
> Chapter 2 surveys related work in refactoring evaluation,
> mining, recommendation and synthesis, and positions this work
> against that literature. Chapter 3 defines the process-quality
> metric and the divergence-point detector. Chapter 4 describes
> the implementation. Chapter 5 reports experimental results on
> a 45-session corpus and analyses threats to validity.
> Chapter 6 concludes and identifies future work."

(The exact chapter numbering will need to match whatever the
final structure ends up being — adjust on the final pass.)

### 6. Typo + formality pass

Pass the whole 32-line file for:
- "this is matters because" → "this matters because"
- "differnet" → "different"
- "before arriving to a final outcome" → "before arriving at a
  final outcome"
- Double-spaces, comma placement, en/em-dash consistency.
- "Two developers can arrive at" — fine, but check if any of the
  other slightly-informal phrasings ("In practice",
  "Meanwhile") should soften or stay. Most should stay; they're
  voice.

### 7. Optionally cite one recent (2023–2025) paper

If the research agent surfaces a recent paper on process-aware
refactoring evaluation or IDE-trace mining that fits the
Motivation section's "process matters" argument, drop it in as a
single citation. Most recent-citation effort belongs in
Background; the intro just needs the gap statement to land.

## Tone preservation notes

The current voice features:
- Mid-length sentences, not very long. Em-dashes used sparingly.
- "In practice", "Meanwhile", "Typical strands of work include"
  — slightly informal academic, not journal-stiff.
- The user uses *italics* and **bold** for emphasis on key terms
  (`\textit{trajectory}`, `\textbf{process-quality metric}`).
- Avoid: "we contribute the following novel algorithmic
  advances", "to the best of our knowledge", "extensive
  evaluation demonstrates". These are journal-jargon and would
  clash.
- Prefer: "as far as we are aware", "the closest work
  either... or...", "we instantiate this framing in a tool".
- First-person plural ("we") is fine — the interim already uses
  "this project" / "the project". Either pattern is OK; keep
  consistent within a paragraph.

## Research-agent prompts (for the user to fire off in parallel)

Three short, targeted prompts the user can give to a research
agent (the user has access to one — I cannot reach the open
web). Each is scoped to return ≤5 candidate papers with one-line
relevance summaries, so the user can pick the strongest 1–2 to
cite in the intro and use the rest in Background.

### Prompt 1 — process-aware refactoring evaluation (most important)

> "I'm writing an MEng thesis on evaluating the *process* a
> developer takes while refactoring — not just the endpoint
> result. I want to make the gap claim that no prior system both
> (i) scores a refactoring trace against a process-quality
> metric and (ii) produces counterfactual reference trajectories
> with measurable divergence points.
>
> Please find any 2023–2025 papers (ICSE, FSE, ASE, TSE, EMSE,
> MSR, SANER, journals are all fine) that come close to either
> half of that claim. Specifically:
> (a) Work that scores or evaluates refactoring quality on
>     intermediate states or trajectories, not just endpoints.
> (b) Work that mines developer process or IDE-event traces and
>     produces actionable feedback (not just descriptive
>     statistics).
> (c) Anything LLM-based that critiques refactoring sequences or
>     suggests better orderings.
>
> Return up to 5 papers. For each: full citation, one-sentence
> relevance, and whether it strengthens or threatens my gap
> claim. I'd rather hear about a paper that threatens the claim
> than miss it."

### Prompt 2 — counterfactual / trajectory framing (background)

> "I'm framing refactoring as a state/action/trace problem where
> 'states' are code snapshots and 'actions' are IDE refactorings
> or manual edits. I claim divergence between an observed
> trajectory and a higher-scoring alternative.
>
> Find 2022–2025 papers that use a similar formalism for
> *anything* in software engineering — e.g. trajectory analysis
> in debugging, sequential decision-making in code edits,
> counterfactual reasoning over edit sequences, behavioural
> cloning of developer workflows, or MDP/policy framings of
> programmer actions.
>
> Up to 4 papers. I want one that supports framing my work in
> 'process evaluation' or 'behaviour analysis' terms — even an
> analogue from robotics or RL if a software-engineering paper
> rides that analogy. Brief one-line relevance each."

### Prompt 3 — quick sanity on RefactoringMiner / GumTree currency

> "Quick check: are tsantalis2018refactoringminer and
> tsantalis2020refactoringminer still the canonical references
> in 2025, or has RefactoringMiner had a major update I should
> cite instead? Same question for falleri2014gumtree. One
> sentence each."

The user pastes these into their research-agent of choice and
returns the citations to fold into the intro + Background.

## Critical files

- `/Users/ethanhosier/Desktop/random/fyp/final_report/introduction/introduction.tex`
  — only file to edit for the intro itself.
- `/Users/ethanhosier/Desktop/random/fyp/final_report/bibs/sample.bib`
  — add any new citations the research agent surfaces.
  (Consider renaming to `bibs/main.bib` later — out of scope for
  this pass; leave the path alone for now.)
- `/Users/ethanhosier/Desktop/random/fyp/tool/PLAN-thesis-writeup.md`
  — small follow-up edit: change the "Source-material map" entry
  for Background from `interim_report/background/background.tex`
  to `final_report/background/background.tex`. One-line tweak;
  doesn't change the plan's substance.
- Reference (read-only): `HONEST_REVIEW_2.md` (framing critique),
  `analysis/.../experiment/explained_results/divergence.md` (for
  signposting language), `PLAN-thesis-writeup.md` (for chapter
  numbering when writing the roadmap).

## Verification

1. Re-read the new intro top-to-bottom; tone should feel
   continuous with the rest of the interim (no journal-jargon
   slipping in).
2. The gap statement should be sharp enough that someone reading
   only the intro could predict what Chapter 5 evaluates.
3. The contributions list should have exactly 4 items, each
   tied to a later chapter.
4. Compile `final_report/main.tex` and confirm no LaTeX errors,
   no broken citations. (The interim's `main.pdf` is the
   reference for "before" — leave it untouched as a baseline.)
5. Sanity-check: word count of the new intro is roughly +30 %
   over the interim (4 contributions + roadmap + sharpened gap
   ≈ 200–300 additional words).

---

## Research-agent results: Prompt 1 (process-aware refactoring evaluation, 2023–2025)

### Executive summary

**The gap claim survives the search.** No paper found simultaneously
scores a developer's refactoring trajectory against an explicit
process-quality metric *and* generates counterfactual reference
trajectories tied to divergence points. The two closest threats are
(i) RefactorBench's qualitative agent-trajectory failure-mode
analysis and (ii) process-reward models for code-edit sequences,
but both fall short on one of the two prongs.

Two papers worth citing in the intro (or Background): **[1]
RefactorBench (ICLR 2025)** as the closest adjacent agent-trajectory
work, and **[4] AlOmar et al. (IST/JSS 2025)** as a survey anchor
that itself flags process-aware evaluation as a gap. The intro only
needs one new citation; the rest belong in Background.

### Findings

**[1] RefactorBench: Evaluating Stateful Reasoning in Language
Agents Through Code.** Gautam, D., Garg, S., Jang, J., Sundaresan,
N., Zilouchian Moghaddam, R. *ICLR 2025.* arXiv:2503.07832.
- **Relevance:** Multi-file refactoring benchmark for LM agents;
  manual trajectory analysis classifies ~58% of failed agent
  trajectories into three failure modes. Touches (a) and (c).
- **Verdict:** STRENGTHENS gap claim.
- **Reason:** Trajectory analysis is qualitative failure-mode coding
  of *agent* (not developer) traces against task success — no
  explicit process-quality metric and no counterfactual reference
  trajectory generation tied to divergence points.

**[2] From Expert Trajectories to Step-wise Reasoning.**
arXiv:2510.25992, 2025.
- **Relevance:** Step-level reward model trained on 134k expert
  trajectory step instances for code-patch generation.
  Touches (c) and tangentially (a).
- **Verdict:** STRENGTHENS gap claim.
- **Reason:** Scores intermediate steps of agent trajectories, but
  the target is patch correctness, not refactoring process quality,
  and produces no counterfactual reference trajectory anchored to
  divergence points.

**[3] From Restructuring to Stabilization: A Large-Scale Experiment
on Iterative Code Readability Refactoring with LLMs.** Cordeiro,
J. et al. *arXiv 2025 (preprint).* arXiv:2602.21833.
- **Relevance:** Iterative (multi-round) LLM refactoring of 230 Java
  snippets across five iterations × three prompting strategies,
  measuring readability across iterations. Touches (a) and (c).
- **Verdict:** STRENGTHENS gap claim.
- **Reason:** Evaluates quality across the LLM's iterations
  (intermediate states), but does not score a *developer's*
  trajectory and produces no counterfactual reference paths tied to
  identified divergence points.
- **Caveat:** the arXiv ID is `2602.21833` which is unusually
  formatted; verify the canonical bibrec before citing.

**[4] Software Refactoring Research with Large Language Models:
A Systematic Literature Review.** AlOmar, E. et al. *Information &
Software Technology / J. Systems & Software, 2025.* (50 primary
studies; S0164121225004315).
- **Relevance:** SLR anchoring the LLM-refactoring landscape;
  explicitly notes gaps around process-aware evaluation, IDE
  integration, and reliability. Touches (a) and (c) as a survey.
- **Verdict:** TANGENTIAL (survey anchor).
- **Reason:** Useful to *support* the gap framing — the SLR's
  identified gaps overlap with the thesis claim and no surveyed
  primary study combines process-metric scoring with counterfactual
  trajectories.

**[5] Investigating Student Reasoning in Method-Level Code
Refactoring: A Think-Aloud Study.** Krasniqi, R., Buse, R. *Koli
Calling 2024.* doi:10.1145/3699538.3699550. arXiv:2410.20875.
- **Relevance:** Qualitative think-aloud on student refactoring
  reasoning; categorises eight refactoring reasons and frequently-
  overlooked issues. Touches (a) descriptively.
- **Verdict:** TANGENTIAL.
- **Reason:** Descriptive process study with no automated
  process-quality metric and no counterfactual trajectory
  generation — useful as motivation, not a threat.

### Explicitly excluded (checked, found weaker than above)

- *IDE Native, Foundation Model Based Agents for Software
  Refactoring* (Bellur & Batole, ICSE IDE workshop 2025) — position
  paper, no scoring or counterfactuals.
- MSR 2024 ChatGPT-refactoring conversation mining
  (Cassano et al.) — descriptive only.
- *Refactoring Loops in the Era of LLMs* (MDPI Future Internet
  2025) — endpoint metric deltas only.

### How to use these in the intro

- **Cite [1] RefactorBench** in the Motivation paragraph as the
  closest adjacent work: *"Concurrent work analyses LM-agent
  refactoring trajectories qualitatively against task success
  [Gautam 2025]; we instead score developer trajectories against
  an explicit process-quality metric and surface counterfactual
  reference paths."*
- **Cite [4] AlOmar et al. 2025 SLR** as a survey anchor in the
  same paragraph: *"A recent SLR of LLM-based refactoring research
  [AlOmar 2025] identifies process-aware evaluation as one of the
  open gaps in the field."*
- **Leave [2], [3], [5]** for the Background chapter (they fit the
  related-work survey better than the intro).

### Watch-list (12-month horizon, not yet papers)

- ICSE 2025 IDE workshop track on "IDE-Native FM-Based Agents for
  Software Refactoring" — position papers only so far.
- Process-reward-model literature spilling into code
  (ThinkPRM, Spark) — none target refactoring process quality
  yet. Worth flagging in *Future work* as convergence risk over
  the next 12 months.

### Sources

1. https://arxiv.org/abs/2503.07832 — RefactorBench
2. https://arxiv.org/pdf/2510.25992 — Expert Trajectories to
   Step-wise Reasoning
3. https://arxiv.org/html/2602.21833v1 — From Restructuring to
   Stabilization
4. https://www.sciencedirect.com/science/article/abs/pii/S0164121225004315
   — AlOmar et al. SLR
5. https://arxiv.org/abs/2410.20875 — Krasniqi & Buse Koli Calling
   2024

### Deep-read verdict on [1] RefactorBench (after reading full paper)

**Verdict: adjacent, not similar where it matters. Gap claim is
safe.**

RefactorBench is a *benchmark for LM agents* on multi-file
refactoring (Python, 100 hand-crafted tasks, AST unit-test
scoring). Their "trajectory analysis" is **manual + GPT-4-assisted
qualitative coding** of failed agent trajectories into 3
categorical buckets (localisation failure, erroneous-intermediate-
state failure, context-flooding failure), classifying ~58 % of
failed runs. They have no continuous process-quality metric and no
counterfactual reference trajectories — their proposed fix is a
*state-aware prompting interface* for prevention, not a
retrospective comparison tool.

The load-bearing differences (use these to differentiate in the
chapter):

| Dimension     | RefactorBench                       | This thesis                        |
|---------------|-------------------------------------|------------------------------------|
| Subject       | LM agents                           | Human developers                   |
| Evaluation    | Binary pass/fail of AST tests       | Multi-term continuous process score |
| Analysis      | Qualitative 3-bucket failure coding | Quantitative DP magnitudes per kind |
| Counterfactual | None                               | Reorder, IDE_REPLAY, REWORK, HYGIENE alts |
| Output        | "Did the agent solve the task?"     | "Where did your trajectory diverge from a better one, and by how much?" |
| Per-kind P/R  | Not present                         | Multi-label confusion matrix, 45 sessions |

Their failure-mode #2 ("erroneous intermediate states are
sometimes necessary; strict linting hurts performance") is a
**kindred-spirit observation** to the time-weighted `W_BROKEN`
methodology choice. Worth cross-citing in the methodology chapter
when justifying continuous-time penalty over binary
disqualification. Useful supporting citation.

The shared POMDP formalism (`τ_N = (a₁, ω₁, …, a_N, ω_N)`) is
also worth pointing to in the methodology when introducing the
state/action/trace framing — RefactorBench is recent precedent
for using the same formalism in the same domain.

**Pre-drafted Motivation paragraph for the intro:**

> *"Concurrent work has begun to analyse LM-agent refactoring
> trajectories qualitatively, classifying agent failures into
> categorical buckets and proposing prompt-level interventions
> [Gautam 2025]. This work shares our trajectory framing —
> modelling refactoring as a sequence of actions over observable
> code states — but evaluates only LM agents on a pass/fail
> benchmark, with no explicit process-quality metric and no
> counterfactual reference trajectories tied to measurable
> divergence points. The present thesis instead scores developer
> trajectories on a multi-term continuous process metric and
> surfaces counterfactual reference paths anchored to per-step
> divergence points."*

This paragraph (i) honestly cites the most adjacent recent work,
(ii) positions precisely against it, (iii) uses it to *sharpen*
the gap claim rather than threaten it.

**Action items for the intro pass:**

- Cite [1] RefactorBench (Gautam 2025) in the Motivation
  paragraph using the pre-drafted text above.
- Cite [4] AlOmar et al. SLR 2025 in the same Motivation
  paragraph as survey support that the gap is unsurveyed.
- Leave [2], [3], [5] for the Background chapter related-work
  survey — they don't belong in the intro.
- **Cross-citation note for methodology chapter:** when justifying
  time-weighted `W_BROKEN`, cite RefactorBench failure-mode #2
  as independent observation that strict broken-state penalties
  are over-eager.

**Cross-citation note for future work / risks:**

- ICSE 2025 IDE workshop on FM-based refactoring agents — position
  papers only so far; flag as 12-month convergence risk in *Future
  work* alongside AI-vs-human comparison.
- Process-reward-model literature on code (ThinkPRM, Spark) —
  could converge on this niche within 12 months; worth a sentence
  in *Threats to validity* or *Future work*.

---

## Research-agent results: Prompt 2 (state/action/trace formalism, 2022–2025)

### Executive summary

The literature is **moderately thin but high-quality for our
purposes.** One load-bearing citation that formalises SE-agent
behaviour as a POMDP with a state/action/observation trajectory
(Bouzenia & Pradel, ASE 2025) — the natural anchor for the
related-work paragraph alongside RefactorBench. Two secondary
papers (SWE-Gym; Eilertsen JSEP 2024) round out the picture.
Honest negative: **no 2022–2025 paper performs counterfactual
reasoning over developer edit trajectories** in the formal sense —
the thesis is novel on that axis and can claim it explicitly.

### Findings

**[BOUZENIA-PRADEL-2025]** Islem Bouzenia and Michael Pradel.
*Understanding Software Engineering Agents: A Study of
Thought-Action-Result Trajectories.* ASE 2025. arXiv:2506.18824.

- **What it does:** Empirical study of 120 trajectories / 2,822
  interactions from RepairAgent, AutoCodeRover, OpenHands;
  categorises thought-action-result sequences and identifies
  recurring patterns (Explore / Locate / Search / Reproduce /
  Generate-fix / Run-tests) distinguishing successful from failed
  trajectories.
- **Why it matters:** Hits (a), (b), (d). Recent precedent that
  treats SE work as a trajectory of (thought, action, observation)
  triples and analyses divergence between successful and
  unsuccessful paths — directly your framing, complementary to
  RefactorBench's POMDP formalism.
- **Strength of fit:** **STRONG**.

**[GAUTAM-2025]** RefactorBench (already covered above under
Prompt 1).

- Notable detail this prompt surfaced: their trajectory definition
  `τ_N = (a₁, ω₁, …, a_N, ω_N)` is augmented with an externally
  computed state `σ` representing "divergence from initialisation
  state" — directly analogous to the thesis's divergence-point
  identification. Cite as direct lineage.
- **Strength of fit:** **STRONG**.

**[PAN-2025]** Jiayi Pan et al. *Training Software Engineering
Agents and Verifiers with SWE-Gym.* ICML 2025. arXiv:2412.21139.

- **What it does:** Frames SWE tasks as an RL environment with
  action-observation turns; trains policies via rejection-sampling
  fine-tuning on successful trajectories.
- **Why it matters:** Hits (b), (d), (e). Lets the thesis claim
  "treating developer/agent edit sequences as policies on an
  MDP-shaped environment is now standard for SE benchmarks."
- **Strength of fit:** **MEDIUM** (RL-on-agents, not
  human-developer focus).

**[EILERTSEN-2024]** Anna Maria Eilertsen. *A study of
refactorings during software change tasks.* J. Software:
Evolution and Process, 2024. doi:10.1002/smr.2378.

- **What it does:** Empirical observational study of refactoring
  operations as ordered sequences interleaved with other change
  activities during real developer change tasks.
- **Why it matters:** Hits (a) and (f) loosely. Provides a
  non-LLM, human-developer citation supporting "refactoring is
  naturally analysed as a sequence of operations within a larger
  trajectory" — keeps the related-work chapter from being
  entirely LM-agent-flavoured.
- **Strength of fit:** **MEDIUM**.

### How to use these in the related-work / Background chapter

Build the paragraph around **Bouzenia-Pradel 2025 + RefactorBench**
as the load-bearing pair, e.g.:

> *"Recent software-engineering work formalises agent and developer
> behaviour as a POMDP-shaped trajectory of (action, observation)
> pairs [Gautam 2025; Bouzenia & Pradel 2025]; this thesis adopts
> the same framing for refactoring trajectories and additionally
> locates divergence points against a higher-scoring alternative."*

Use **SWE-Gym** for the policy/RL-precedent sentence:

> *"Treating edit sequences as policies on an MDP-shaped
> environment is now standard for SE benchmarks [Pan et al.
> 2025]."*

Use **Eilertsen 2024** for the human-developer-grounding sentence:

> *"Empirical observational studies confirm that real-world
> refactoring proceeds as ordered sequences of operations
> interleaved with other change activities [Eilertsen 2024],
> motivating an analysis at the trajectory level rather than the
> endpoint level."*

### Honest negative result (deliberately not padded)

**No 2022–2025 paper performs counterfactual reasoning over
developer edit trajectories** in the formal sense (i.e. "what
would a better trajectory have looked like, and where did this one
diverge from it?"). The closest analogues:

- RefactorBench's σ-divergence representation (but no
  counterfactual reference path).
- Bouzenia-Pradel's successful-vs-failed comparison (but
  qualitative, not synthesised alternatives).

This thesis appears to be **novel on the counterfactual axis**.
Worth claiming explicitly in the intro and Background gap section.

### Action items for the writeup

- **Background chapter:** add `[Bouzenia-Pradel 2025]` and
  `[Pan et al. 2025]` and `[Eilertsen 2024]` to the related-work
  survey. RefactorBench already covered.
- **Intro Motivation paragraph:** the Bouzenia-Pradel /
  RefactorBench load-bearing pair gives a strengthened gap claim:
  *"recent SE work formalises trajectories of (action, observation)
  pairs; this thesis adds an explicit process-quality metric and
  counterfactual reference trajectory generation."*
- **Methodology chapter (state/action/trace formal definition):**
  cite Bouzenia-Pradel + RefactorBench as recent precedent for
  POMDP formalism in SE.
- **Gap-claim sharpening:** the chapter can now claim novelty on
  the counterfactual axis explicitly, since no 2022–2025 paper
  has done counterfactual reasoning over developer edit
  trajectories. Strengthens the intro's "as far as we are aware"
  formulation to something more direct.

### Sources

1. https://arxiv.org/abs/2506.18824 — Bouzenia & Pradel ASE 2025
2. https://software-lab.org/publications/ase2025_trajectories.pdf
   — camera-ready of the above
3. https://arxiv.org/abs/2412.21139 — Pan et al. SWE-Gym ICML 2025
4. https://onlinelibrary.wiley.com/doi/10.1002/smr.2378 —
   Eilertsen JSEP 2024
5. https://arxiv.org/abs/2503.07832 — RefactorBench (already in bib
   from Prompt 1)

---

## Research-agent results: Prompt 3 (citation currency check)

### Q1 — RefactoringMiner

**The 2018 + 2020 pair is no longer the most up-to-date reference.**
Cite as RefactoringMiner 3.0:

> Alikhanifard & Tsantalis. *A Novel Refactoring and Semantic Aware
> Abstract Syntax Tree Differencing Tool and a Benchmark for
> Evaluating the Accuracy of Diff Tools.* ACM TOSEM 34(2),
> Article 40, Feb 2025.

Keep `tsantalis2018refactoringminer` and `tsantalis2020refactoringminer`
in the bib for historical reference if the chapter discusses the
2.0 → 3.0 evolution; otherwise replace both with the 2025 cite.

### Q2 — GumTree

**`falleri2014gumtree` is no longer the latest canonical
reference.** Cite as the up-to-date GumTree reference:

> Falleri & Martinez. *Fine-grained, Accurate, and Scalable Source
> Code Differencing.* ICSE 2024.
> doi:10.1145/3597503.3639148

### Action items for the writeup

- **Update `final_report/bibs/sample.bib`:** add the two new
  citations above. Replace `falleri2014gumtree` references with
  the 2024 entry; replace `tsantalis2018refactoringminer` and
  `tsantalis2020refactoringminer` references with the 2025 entry
  (or keep alongside if the historical evolution is being
  discussed in Background).
- **Sweep usages:** `grep -rn 'tsantalis20\(18\|20\)refactoringminer\|falleri2014gumtree' final_report/`
  to find every cite site. Update consistently.
- **No new framing implications** — purely a freshness upgrade so
  the bib doesn't look two versions behind.

### Sources

1. https://users.encs.concordia.ca/~nikolaos/publications/TOSEM_2024.pdf
   — Alikhanifard & Tsantalis TOSEM 2025 (RefactoringMiner 3.0)
2. https://github.com/tsantalis/RefactoringMiner/releases/tag/3.0.11
   — confirms 3.0 release line
3. https://dl.acm.org/doi/10.1145/3597503.3639148
   — Falleri & Martinez ICSE 2024 (current GumTree)
4. https://hal.science/hal-04855170v1/document
   — author-version PDF of the above


## WRITE RESEARCH HERE:
