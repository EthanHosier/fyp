# Honest evaluation against the MEng rubric

Based on:

- The interim report (intro, 230-line background, project plan, evaluation plan, thesis_plan.md, 33-entry bib, 65 in-text citations across two chapters)
- The codebase as it stands today (ARCHITECTURE.md, pipeline code, alt-trajectory synth, validator, advisor, task list of 100+ items with 95+ completed)
- The four-criterion rubric in `MENG_PROJECT_ASSESSMENT_CRITERIA.md`

Calibration disclaimer: I'm a code-and-docs reader, not your supervisor — these are estimates, not marks. The actual marker will weigh things differently and may have additional context.

---

## 1. Framing of Research Problem (15%)

**Estimated band: 70–84%, mid-range.**

What's strong:

- **The research question is precise and unusual.** "Given a known start state and end state, can we evaluate the quality of the developer's refactoring process, and identify points where a better process was available?" That's sharper than most FYP framings, and it's a real gap: the literature is overwhelmingly endpoint-focused or recommendation-focused, and you correctly identify that nobody is *evaluating an observed developer trace* against a process-quality objective.
- **The state/action/trace formalism is the right move.** It's a small piece of formal apparatus that signals you've thought about the problem like a CS researcher, not just a software engineer.
- **Literature engagement is wide and well-curated.** 33 bib entries, 65 citations across two chapters, covering: foundational definitions (Opdyke, Fowler, Mens), mining (RefactoringMiner, GumTree, RefDiff), smells/metrics (DECOR, false-positive studies, readability models), search-based recommendation (Harman, Mohan), synthesis (ReSynth, REFAZER), goal-directed (Refactoring Navigator), interactive (Alizadeh), and IDE logging (Damevski, Poncin). That's not "minimum viable lit review" — that's the right shape.
- **The critique of each family is specific.** You don't just describe ReSynth, you say what its objective is (reachability/consistency) and why that doesn't answer the process-evaluation question. Same for navigation and search-based. That kind of pointed comparison is what the rubric rewards at the 70–84% level ("clear critical analysis and synthesis").

What's holding you back from 85%+:

- **The framing reads as "principled and well-justified" rather than "elegantly formulated, publishable".** That's a real gap. To push higher you'd need either (a) sharper, more competitive positioning against very recent (2023–2025) closely-related work, or (b) a stronger argument that process-quality evaluation reshapes how we think about a known concept (e.g., link it explicitly to literature on software process modelling, MDP/policy evaluation framing, or even debug/maintenance ergonomics — there's an analogy to "behaviour evaluation" in robot learning that you could ride).
- **A handful of typos in the interim** ("differnet", "policys", "wdefines", "trajectorig", "primarly") drag the perceived polish down. Easy to fix.
- **The gap is identified rigorously but the *novelty* claim is gentle.** The interim says "comparatively little work" — you're sandbagging. Be more direct: *no prior system* takes a developer trace and produces both (a) a process-quality score and (b) a counterfactual reference trajectory with divergence-point explanations. That's a stronger gap statement and the lit review actually supports it.

Net: framing is solidly first-class. With a polish pass and a sharper "this is new" claim it could reach mid-70s. Reaching 80%+ here is realistic; reaching 85%+ is unlikely without more recent-citation work + a tighter formal framing.

---

## 2. Execution and Technical Quality (50%)

**Estimated band: 70–84%, upper portion if certain things hold.**

This is your strongest dimension by some distance. The implementation is far above typical MEng quality.

What's strong:

- **The systems engineering is serious.** Four-module Gradle build, OSGi-embedded Eclipse JDT bundle, headless RefactoringMiner integration, polyglot Kotlin+TS+LaTeX with codegen across the wire — most MEng projects don't operate at this scale.
- **The reorder-synthesis algorithm is genuinely non-trivial novelty.** The pipeline is: window-split on validator verdicts → dependency DAG from spec effects with SSA versioning for renamed entities → topological enumeration → prefix-trie DFS with **one worktree, one batch session, git-checkout backtracking** to avoid re-indexing → terminal AST-equivalence audit. That last move — abandoning JDT's undo because of per-file cache invalidation issues and going through Eclipse's standard out-of-band-edit pathway — is the kind of design decision a marker will recognise as research-grade engineering judgement.
- **The validator is similarly careful.** Bracket-grouped replay, AST hash audit (`JavaFileAstHasher.hashFileAtSha` reads via `git show`, no second worktree borrow), single-session for the whole call. Verdict statuses are well-typed. This isn't ad-hoc; you've thought about what "correct replay" actually means.
- **Process score + cleanliness sub-score** are present and grounded in literature-cited weights (PMD/CPD/readability).
- **Task list shows systematic incremental delivery.** 95+ completed items. The validator alone took roughly 20 tasks. You're shipping.
- **Code quality is high.** Well-named types, comments explaining *why* not *what*, clear separation of concerns, comprehensive test coverage (TrajectoryAdvisorTest, ReorderSynthesiserTest, RefactoringStepValidatorTest with multi-bracket cases, DiffLineMapper tests, PmdViolationTracker tests).
- **You're not afraid to iterate.** The git log shows repeated refactors as understanding improved (batch session for validator, then for synthesiser; multi-step alts; shared-prefix trim). That's the right way to do research-grade software.

What's at risk:

- **There's a mismatch between effort spent and thesis-question alignment.** The thesis question is *evaluate the developer's process and identify divergence points*. The bulk of your work is on *generating counterfactual orderings*. That's not wrong — counterfactuals are necessary infrastructure — but the *evaluation/divergence* layer is currently the much thinner 7-rule TrajectoryAdvisor and a process score. A marker who reads the thesis question first and then the implementation will ask "where is the divergence-point detector with explanations?" The reorder synth produces alternatives; what's the algorithm that says "your trajectory diverged from a better one at step k because X"?
- **The process-quality metric is the weak link in the technical contribution.** You have a process score (Int 0–100, weighted from cleanliness + churn + safety). I haven't seen rigorous justification of the *weights* — that's the part the interim said would be "defined and justified using existing research". A marker will want the methodology chapter to derive each term from cited work and validate the weight choices.
- **No multi-language story, no large-corpus story.** Scoped down by design — fine. But pushing into 85%+ requires "advances state of the art", which is hard to claim from a single Java fixture session.

Net: this is a genuine 70–84% band of execution, plausibly in the 75–80 range. To push higher:

1. Make the divergence-detection algorithm a first-class named contribution. Right now it's implicit (compare alt process scores to user's). Name it. Describe it. Give it a section.
2. Justify the process-score weights with citations and (ideally) a sensitivity analysis.
3. Add at least one piece of "this beats the obvious baseline" empirical evidence. Even on synthetic data.

---

## 3. Evaluation and Reflection (20%)

**Estimated band as of right now: 40–59%. This is your highest-risk dimension.**

The rubric weight is 20% and this is where the project is most exposed.

What exists:

- The interim *evaluation plan* is sensible: real episodes via the plugin, injected bad-process variants for ground truth, optional open-source mining, small human study with baselines (endpoint-only, per-step regression flag, greedy/local).
- The *infrastructure* to do all of this is built: plugin captures sessions, pipeline produces reports.
- A few fixture sessions exist (the analysis-report.json the dashboard dev-mode reads).

What I don't see:

- A collected dataset of real-world refactoring sessions from human participants.
- An injected-variant test harness with ground-truth divergence points.
- Quantitative results of any kind (precision/recall of divergence detection, score improvement of synthesised references over user trajectories, etc.).
- Any baseline comparisons run.
- A human study — even a small one (n=5 participants).
- A threats-to-validity discussion.

Given today's date is 2026-05-11 and the interim timeline budgeted May–Jun for evaluation + writeup, you're on the timeline you set yourself. But it's a tight timeline and the easiest part to underestimate. Realistically, the *minimum* needed to lift this dimension into the 60s:

1. **Define the process-quality metric formally** (methodology chapter) with each term traced to a citation. You've cited the right papers — Harman/Tratt for multi-objective formulation, Paixão for the disruption-vs-improvement trade-off, Scalabrino for readability, Paiva and Arcelli-Fontana for smell-detector limitations. Use them.
2. **Run at least one quantitative experiment.** The injected-variant story is the cheapest, most credible one. Take a real or synthetic clean trajectory, inject a known bad pattern (e.g., add a noisy detour with churn + temporary smell increase), run your pipeline, measure: did the divergence detector flag the right step? Did the synthesised reference avoid it? Repeat for ≥10 cases. This alone moves you from 40–59 into 60–69.
3. **Pick at least one baseline comparison.** Even just "endpoint-only metric Δ" vs "your process score". Show the endpoint-only view misses something your view catches.
4. **Do a 4–6 participant human evaluation** if at all feasible. Doesn't need to be large. Show participants two outputs (yours vs. a baseline) and ask which is more actionable. Even small-n results that go your way are convincing.
5. **Threats to validity.** The rubric explicitly rewards "anticipates possible objections" at 70%+. Smell-detector noise, weight sensitivity, single-language, small sample, fixture-vs-real gap — write these up honestly.

If items 1–3 happen and 4 is at least partially done, this dimension can reach 65–75%. If none of them happen, it stays in the 40–55% band and drags the whole project down.

**Concrete arithmetic:** every 10 percentage points on this dimension = 2 points on the final mark. Going from 50% to 70% on evaluation is a 4-point swing — the difference between a low first and a comfortable first.

---

## 4. Communication of Ideas (15%)

**Estimated band: 60–69% on interim, 65–75% achievable for final with a polish pass.**

What's strong:

- The interim has a clear logical flow (definitions → outcome eval limits → smells/metrics → mining → recommendation/synthesis → IDE logging → gap → positioning).
- Signposting is good: each subsection has a "relevance / limitations" paragraph that connects back to the thesis.
- The candidate-signals table in the background chapter is *exactly* the kind of artefact that lifts marks — a marker can scan it and see you've thought about pros/cons across signal families.
- The state/action/trace formalism is presented compactly with notation.

What's holding you back:

- **Typos.** Roughly one per page in the interim. "differnet", "policys", "wdefines", "trajectorig", "primarly", "as such" with double space, etc. Each one is invisible alone, but they accumulate and a marker who's read 20 reports that week *will* notice.
- **The conclusion file is empty.** Fine for interim, but if it stays empty it's a critical gap.
- **Bib has 33 entries.** That's solid for an interim but you'll probably want 50–70 for the final, especially in the methodology and evaluation chapters where you'll cite measurement choices.
- **No GenAI disclosure section.** The rubric explicitly mentions this and rewards transparency. Even a one-paragraph appendix saying "I used X for Y, reviewed all outputs, made the following manual changes" goes a long way.
- **Some informal language.** "this is matters because", "two developers can arrive at" (informal phrasing for a thesis), occasional first-person in places where third-person would be more standard. A pass for formality without losing your voice will help.

For the final, a half-day language pass + filling the conclusion + GenAI section + ensuring every figure/table is referenced from text gets you firmly into the 70+ band.

---

## Overall: where you actually are

Using rubric weights (15/50/20/15):

**Optimistic** (evaluation gets done well, report polished, weights justified):
- 75 / 78 / 70 / 72 → **~74.7% — solid first class**

**Realistic** (evaluation done but tight, report decent, some threats discussed):
- 72 / 75 / 60 / 67 → **~70.0% — borderline first**

**Pessimistic** (evaluation not properly executed, report rushed, conclusion thin):
- 70 / 72 / 48 / 60 → **~64.0% — upper second**

The technical work is anchoring this somewhere in the 70s. The evaluation dimension is what swings you up or down within that range. Communication is fixable in a week.

---

## What to actually do next (priority order)

If you have ~6 weeks left (Jan–Jun timeline → ~6–7 weeks):

1. **Lock the process-quality metric and write the methodology chapter.** You already have the literature; you have the score in code. Spend a few days writing the formal definition with cited weight justifications. This unblocks both Execution (clearer contribution) and Evaluation (now you have a thing to evaluate).
2. **Run the injected-variant experiment.** Generate 10–20 synthetic trajectories with known divergence points, run the pipeline, measure detection rate. This is by far the highest-leverage thing for the Evaluation dimension. ~1 week of work.
3. **Run a small human study, even n=5.** Recruit fellow MEng students or your supervisor's group. Show them outputs, get usefulness ratings. ~1 week including analysis.
4. **Make divergence detection a first-class named algorithm.** Not just "process score difference" — define it, name it, describe it formally, evaluate it. This addresses the "thesis-question vs. implementation alignment" gap I flagged in §2.
5. **Don't add more features to the implementation.** The remote-metrics lambda (tasks #61–#63), more refactoring kinds, etc. — drop them. They don't move marks. Polish, evaluation, and writeup do.
6. **Reserve the last 2 weeks for writeup + polish + threats-to-validity + conclusion + GenAI section.** Do not start writeup in the last week.

---

## Things I'm uncertain about

To be honest about the limits of this evaluation:

- I haven't seen the methodology chapter (it doesn't exist yet in the interim). The marker will read that and adjust significantly in either direction.
- I haven't seen any evaluation results — for all I know you've already started running experiments offline. If you have, my Evaluation estimate is too low.
- I haven't seen the ethics chapter, conclusion, or appendix. These are placeholders in the interim.
- I'm not your supervisor. They've seen many more MEng projects than I have and will calibrate differently.
- Marker variance on FYPs is real — same project can get 68 from one marker and 75 from another. Treat the numbers as rough.

But the *direction* of this assessment — technical work is strong, evaluation is the dominant risk, communication is fixable — I'm fairly confident in.
