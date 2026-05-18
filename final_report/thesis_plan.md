## 1) Introduction (1–2 pages)

Goal: succinctly set up the problem + why it matters + your core approach, without overcommitting to final contributions.

What to cover (in this order):

1. **Motivation + problem statement (½ page)**

   * Refactoring is common; tooling/evaluation focuses on *outcome* (before/after).
   * But process matters: intermediate states, churn, risk, detours, maintainability while changing.
   * State the thesis question: *given start + end, was the developer’s process good, and where could it be improved?*

2. **Gap in the literature (½ page)**

   * Prior: outcome-based metrics, smell deltas, refactoring detection/mining, synthesis tools that reach end state.
   * Missing: **evaluation of the trajectory** / process quality from start to end.

3. **Main idea + approach sketch (½–1 page)**

   * Model code snapshots as **states**, developer edits/refactorings as **actions**.
   * Define a **process-quality metric** (you’ll specify later) that can score a path and/or intermediate steps.
   * Generate **counterfactual alternatives** at selected points (local alternatives or short horizons), score them, and highlight “you could have done X here”.
   * Mention expected output: an offline analysis tool (trace → score + suggestions) and possibly an IDE integration later.

4. **Scope + assumptions (short paragraph)**

   * Language(s) you’ll support initially (e.g., Java only to start).
   * What counts as a “step” in your interim thinking (commits/checkpoints/IDE events).
   * What you won’t solve yet (global optimal path search, all refactoring operators).

5. **Roadmap paragraph**

   * “The remainder of this report covers related work, then plan, evaluation, ethics.”

Suggested figure (tiny, optional): one diagram showing Start → (step1, step2, …) → End, with a branch at step k to an alternative.

---

## 2) Background / Related Work (aim 9–10 pages)

This is the bulk. Structure it so it naturally funnels into “therefore my work evaluates process quality”.

### 2.1 Definitions and framing (½–1 page)

* Definitions: refactoring, atomic refactorings, code smells, refactoring sequences/trajectories.
* Your formalism: **state/action/trace**, and what “process” means (intermediate snapshots, not just endpoints).

### 2.2 Outcome-based refactoring evaluation (1–2 pages)

Purpose: show the dominant paradigm you’re improving upon.
Include:

* Approaches that score refactoring by changes in quality metrics (smells, complexity, cohesion/coupling, maintainability indices).
* Typical workflow: detect smells → simulate/apply refactorings → observe metric improvement.
  Critical discussion bullets:
* Good for “is the end better than the start?”
* **Blind to the route**: can’t penalize long detours, temporary degradation, broken intermediate states, rework.

### 2.3 Code smell detection and quality metrics (1–2 pages)

Purpose: justify metric ingredients.
Include:

* How smells are detected (rule-based / metric-based / learned).
* Challenges: false positives, context sensitivity, language/tool dependence, severity weighting, correlations between smells.
  Critical discussion:
* Smells are useful signals but imperfect; hence your metric likely needs multiple terms (smell deltas + churn + safety).

Include a table (good for marks): **Metric candidates** with columns: “signal”, “pros”, “cons”, “available tooling”.

### 2.4 Refactoring detection / mining from code changes (1–2 pages)

Purpose: steps extraction and trace interpretation.
Include:

* Tools that infer refactorings from diffs/AST changes (e.g., RefactoringMiner category).
* How they represent changes (refactoring type taxonomy).
* Their limits: ambiguity, mixed edits, incomplete detection, requires clean commit boundaries, etc.
  Tie-in:
* You’ll likely use these for labeling steps or characterising actions, but your contribution isn’t “detect refactorings”.

### 2.5 Refactoring recommendation and search/synthesis (2–3 pages)

Purpose: you need “counterfactual” alternatives.
Include two buckets:

1. **Synthesis / path completion** (e.g., ReSynth)

   * Given start + partially edited target, synthesize a sequence of IDE refactorings that preserves edits.
   * Strengths: generates plausible operator sequences.
   * Limits: not designed to judge developer process quality; objective is reachability/consistency.

2. **Navigation / guided refactoring** (e.g., Refactoring Navigator)

   * Suggest next refactorings to move architecture/code toward a goal.
   * Strengths: “next-step suggestion” vibe aligns with your local counterfactual generation.
   * Limits: they optimize toward end-state, not necessarily evaluate *human-chosen* sequences.

Critical synthesis paragraph:

* These works generate or recommend sequences, but they don’t directly answer:

  * *Was the human’s path good given a metric?*
  * *Where did they diverge from a better path?*
    This is your gap.

### 2.6 Process-aware / human-in-the-loop / IDE logging adjacent work (½–1 page)

Purpose: justify feasibility of capturing traces and analyzing developer workflows.
Include:

* Studies/tools that log IDE events or developer actions (even if not refactoring-specific).
* Limits: privacy, noise, mapping actions to semantics.

### 2.7 Summary: research gap + your positioning (½ page)

* One tight summary that sets up your project:

  * “Outcome eval exists; step mining exists; synthesis exists; **process-quality evaluation** is underexplored.”
* End with 2–3 concrete research questions you’ll pursue (good for later evaluation), e.g.:

  * RQ1: Can we define a metric that correlates with expert judgement of refactoring process quality?
  * RQ2: Can we automatically generate better local alternatives at key points?
  * RQ3: Does the tool identify divergence points that developers agree were suboptimal?

---

## 3) Project Plan (1–2 pages)

Make this a deliverable-driven plan with fallbacks.

### 3.1 Work completed so far (¼–½ page)

* Current state: papers surveyed, initial formalization, initial metric ideas, initial tooling choice (language/parser), prototype design.

### 3.2 Core milestones (table is best)

Example milestones (adjust dates yourself):

* M1: Dataset/traces format defined (commits vs snapshots vs IDE events)
* M2: Metric v1 implemented (smell/churn/safety terms)
* M3: Trace ingestion + step segmentation
* M4: Alternative generation v1 (local, limited operators)
* M5: Scoring + divergence detection + reporting output
* M6: Evaluation experiments run
* M7: Final report writing + polishing

### 3.3 Fallback positions (explicit)

* If alternative generation is too hard: evaluate process without counterfactuals (score + highlight metric regressions per step).
* If IDE plugin too hard: use git commit history snapshots only.
* If smell tooling weak: focus more on churn + compilation/tests + structural metrics.

### 3.4 Extensions (if time permits)

* IDE plugin UI
* Multi-language support
* Larger refactoring operator set
* Longer-horizon planning vs local suggestions
* User study

---

## 4) Evaluation Plan (1–2 pages)

This section should read like a mini-experiment design doc.

### 4.1 What you will demonstrate (functionality)

* Input: repo + start/end + trace (snapshots)
* Output:

  * process-quality score for the developer path
  * per-step score breakdown
  * identified “divergence points”
  * suggested alternatives (top-k) with higher metric score

### 4.2 Datasets / traces

Pick at least two:

1. **Synthetic** (scripted traces or tool-generated sequences; also your supervisor’s ReSynth-as-test-data angle)
2. **Real** (open-source PRs with multiple commits; or student traces if available)

### 4.3 Experiments

* E1: Metric sanity — does it rank “clean” synthetic routes above “detour-heavy” ones?
* E2: Counterfactual usefulness — at divergence points, do suggested alternatives improve the metric and remain feasible (build/test pass)?
* E3: Human agreement (small) — experts/devs rank traces or divergence points; compare to your metric.

### 4.4 Success criteria (make them measurable)

* % cases where metric agrees with human preference above some threshold
* Reduction in churn / broken states / smell spikes in “better” alternatives
* Runtime performance on medium repos
* Qualitative: developer feedback that divergence points are sensible

Include one paragraph on threats to validity (metric bias, smell detector noise, dataset representativeness).

---

## 5) Ethical Issues (½–1 page, maybe 1–2 if you do IDE logging)

Cover what applies; don’t overdo.

* **If using IDE traces / human participants:**

  * privacy: code may be proprietary; logs may reveal behavior
  * consent, anonymization, data minimization
  * secure storage and retention policy
* **If using open-source only:**

  * licensing/attribution
  * avoid deanonymizing developers by “naming and shaming” bad processes
* **Professional considerations:**

  * risk of tool being used for surveillance/employee evaluation
  * framing: tool is for coaching/insight, not punishment

---

## Page budget suggestion (to stay near 15)

* Intro: 1.5
* Background: 10
* Plan: 1.5
* Eval: 1.5
* Ethics: 0.5–1
  Total: ~15–16 (you can trim Background a bit if needed)

---

If you paste your current list of papers (even just titles/links) I can map each one into the right Background subsection and propose the exact paragraph-by-paragraph flow so you can write it fast.
