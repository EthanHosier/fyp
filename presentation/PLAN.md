in t# Presentation Plan — FYP Final Presentation (18 min)

## Context

This is the final presentation for a 4th-year Imperial Computing project on a tool that **evaluates refactoring as a process, not just as an endpoint**. The audience is the assessment team (supervisor, second marker, additional assessors), most of whom will not know the technical area. The presentation is recorded and capped at 18 minutes, followed by up to 10 minutes of Q&A.

The talk needs to:
- Land the core idea quickly (refactoring quality is about the *path*, not just the final code)
- Spend most slide time on the "behind the hood" work — the process-score formula, the four divergence kinds, the synthesizers, and the evaluation across three datasets — since this is where the contribution lives and where slides do most of the explanatory work
- Use a tight ~4–5 min demo whose **only job is to make one thing tangible**: we identified a specific moment where the user went wrong, and we constructed an alternative that is measurably better
- End on contributions and impact, not limitations (per supervisor guidance email)

Target total runtime: **~17 min** (1 min buffer per Imperial advice).

---

## Time Allocation

| Section | Time | Slides |
|---|---|---|
| Opening / problem framing | ~2.5 min | 1–3 |
| Solution overview | ~1.5 min | 4–5 |
| Methodology (process score + divergences) | ~5.5 min | 6–13 |
| Evaluation setup + headline results | ~3 min | 14–17 |
| Demo (live dashboard, fixture session) | ~4 min | 18 (transition) |
| Closing: contributions + impact | ~1 min | 19–20 |
| **Total** | **~17 min** | |

Demo is deliberately short. The slides carry the "how it works" and "why it matters" weight; the demo's only job is to make one concrete divergence + alternative tangible for the audience.

---

## Slide-by-Slide Breakdown

### Section 1 — Hook & Problem (Slides 1–3, ~2.5 min)

**Slide 1 — Title (~15s)**
- Project title + one-line tagline: *"Scoring the path a developer takes through a refactoring session, and pointing to where a better path existed."*
- Name, supervisor, second marker, Imperial College London.

**Slide 2 — The hook: same destination, different journeys (~1 min)**
- Two developers refactor the same code, arrive at the same end state. Visually identical outcome.
- But along the way: one ran tests, committed checkpoints, used IDE refactorings. The other broke the build twice, added then removed 80 lines, hand-edited what the IDE could do safely.
- Ask the audience: *which one would you want on your team?*
- Punchline: **endpoint-only evaluation can't tell them apart.**

**Slide 3 — What's missing in prior work (~1 min)**
- Two camps of prior work, briefly:
  - Endpoint quality metrics (CK, smells, readability): compare before/after, ignore the path.
  - Refactoring recommenders/sequence generators: produce *new* sequences, but don't evaluate the one the developer actually walked.
- Gap: **no system scores an arbitrary observed refactoring session against an explicit process-quality metric, and shows where a better path was available.**
- This is the gap the project fills.

### Section 2 — Solution Overview (Slides 4–5, ~1.5 min)

**Slide 4 — The core idea, visualised (~45s)**
- Use the divergence diagram from `introduction/divergence_diagram.png`.
- Solid line: user's trajectory. Dashed line: synthesised alternative. Both end at the same final state.
- Mark the divergence point and the score gap ΔJ between final scores.
- This single picture should communicate the whole thesis to a non-expert.

**Slide 5 — What the tool does, end to end (~45s)**
- IntelliJ plugin records every edit, refactoring, build, test, commit during a session.
- Analysis backend reconstructs the trajectory, scores it, and looks for divergence points.
- Dashboard surfaces: *"here is where you diverged, here is an alternative path, here is how much better it scores, and here is why."*
- One-line architecture diagram (plugin → analysis → dashboard).

### Section 3 — Methodology (Slides 6–13, ~5.5 min)

The methodology block deliberately spends time per-divergence-kind. Each kind gets its own slide covering both what it is *and* the engineering behind detection + synthesis, to showcase the algorithmic depth on each.

**Slide 6 — Scoring a trajectory (~1 min)**
- Process score J(τ) on 0–100 scale, baseline 50.
- Seven weighted terms grouped into three buckets:
  - **Positive:** cleanliness gain (W_g=50), step-savings bonus (W_l=11)
  - **Process penalties:** intermediate-lag (11), commit-gap (7)
  - **Safety penalties:** broken build/test (28), skip-tests (14), manual-when-IDE (11)
- Key design choice: broken-state penalty is sized so **no single-step gain can outweigh a broken build** — behaviour preservation is non-negotiable.
- (Optionally show the equation; don't dwell on it.)

**Slide 7 — The cleanliness sub-score (~30s)**
- Six equally-weighted code-quality signals: cognitive complexity, coupling (CBO), duplication (CPD), readability, smells (PMD), cohesion (TCC).
- Normalised against the trajectory's own range so sessions are comparable.
- Equal weights: no trajectory-level calibration data exists, so the least-assumptive choice. *(Trim to ~30s — this is a supporting detail.)*

**Slide 8 — Four kinds of divergence (overview) (~20s)**
- A roadmap slide: name the four kinds, one line each, no engineering yet.
  1. **Ordering** — right refactorings, suboptimal order.
  2. **Manual-Refactor** — hand-edited what the IDE could do safely.
  3. **Rework** — added code then removed it (or vice versa); net-zero churn.
  4. **Hygiene** — long stretches without tests or commits.
- One sentence: *each is actionable, and each has its own detector + synthesiser, which the next four slides walk through.*
- Keep this slide on screen briefly — it primes the audience for what's coming.

**Slide 9 — Ordering: detect + synthesise (~45s)**
- **What:** developer applied the right refactorings, but in a suboptimal order — final state is fine, intermediate states are worse than they needed to be.
- **Detect:** find a subsequence of refactorings whose reordering preserves the terminal state (matched by canonical AST hash, formatting/comments ignored).
- **Synthesise:** build a dependency DAG over the refactorings in the window; **prefix-trie DFS** over valid topological orderings, sharing work across common prefixes; roll back with `git checkout` on backtrack.
- **Scope limit (named honestly):** skip windows > 7 steps (5040-permutation cap). This is also the source of the Ordering recall gap on slide 15.
- Use figure `methodology-reorder-trie` if it reads at slide size.

**Slide 10 — Manual-Refactor: detect + synthesise (~45s)**
- **What:** developer hand-edited something the IDE could have done safely (and with its precondition checks).
- **Detect:** run **RefactoringMiner** on sliding commit windows; cross-check against the IDE event stream — anything RefactoringMiner finds that the IDE did *not* emit is a manual refactoring.
- **Synthesise:** apply the equivalent IDE refactoring, then **three-way merge** the user's other edits back on top.
- **Wrap-and-patch layer:** reconciles minor JDT ↔ IntelliJ AST differences (e.g. static modifiers, variable liveness) so the synthesised path validates against the user's terminal state.

**Slide 11 — Rework: detect + synthesise (~40s)**
- **What:** code added and later removed (or vice versa) — net-zero churn, but real cost while it was there.
- **Detect:** hash normalised lines; pair add/remove events by `(file, scope, content-hash)`.
- **Synthesise:** strip both halves from the trajectory; replay the rest of the user's edits unchanged; validate terminal state matches.
- Simplest of the four synthesisers, but high recall: precision and recall both 1.00 on the injection set.

**Slide 12 — Hygiene: detect + synthesise (~40s)**
- **What:** long stretches of work without safety checkpoints — no test runs, no commits.
- **Detect:** 60-second windows with no test-run events; commit-gap events when commits are spaced beyond threshold.
- **Synthesise:** model an alternative cadence that intersperses test-run and commit events at the right points; the alternative's J(τ) credits those checkpoints via the skip-tests and commit-gap terms.
- Cheap to compute, but consistently surfaces meaningful divergences in the user study.

**Slide 13 — Comparable divergence magnitudes (~45s)**
- Magnitude = J(τ*_final) − J(τ_final): score gap when the alternative is substituted at the divergence point but the rest of the user's trajectory is left unchanged.
- This makes magnitudes comparable across all four divergence kinds.
- Lets the dashboard rank divergence points and surface the most impactful first.
- This is the number the demo will point at.

### Section 4 — Evaluation (Slides 14–17, ~3 min)

**Slide 14 — Three datasets (~30s)**
- 45 hand-recorded **injection sessions** with deliberately-injected bad behaviours (labelled ground truth).
- 30-session **randomised user study**, 5 participants, 3 with-feedback vs 2 no-feedback baseline.
- 48-session **agent extension** across 8 LLM agent stacks. *(Mention only — frame as motivation for future work.)*

**Slide 15 — Detector precision & recall (~1 min)**
- Per-kind table from results chapter:
  - **Precision = 1.00 across all four kinds.** Every detection is valid.
  - **Recall:** Rework 1.00, Hygiene 1.00, Manual-Refactor 0.76, Ordering 0.40.
  - Inter-rater agreement (Cohen's κ): 1.00 for Ordering & Manual-Refactor, 0.86 for Rework, 0.72 for Hygiene.
- Ordering recall gap is honest and explained: synthesiser rejects windows it can't safely reproduce. A scope limit, not a detector flaw.

**Slide 16 — Score robustness (~45s)**
- Single-knob sensitivity sweep: scaling any one weight by {0.1×–10×}, top-1 recommendation preserved in **96.5%** of user-study cases.
- Multi-knob Monte Carlo (200 samples): top-1 stability drops to 84.6%, mean Kendall τ = 0.586. Honest framing: stable *near* chosen weights, not at arbitrary values.
- Ablation confirms each process term contributes real signal — endpoint gain alone doesn't recover the ranking.

**Slide 17 — User study: does feedback change behaviour? (~45s)**
- Headline numbers:
  - With-feedback group: **2.7 divergence points / session**.
  - No-feedback baseline: **6.0 divergence points / session**. (**2.2× difference.**)
  - Gain-stripped process-score slope across the 6-session arc: **+4.47 / session with feedback**, **−0.40 / session without**.
- Honest caveat (one line): n=3 vs n=2 is directional evidence, not a hypothesis test.

### Section 5 — Demo (~4 min)

**Slide 18 — Demo transition**
- A single slide that says "Demo" with the one-sentence goal of the demo written on it, so the audience knows what to watch for:
  - *"I'll show one identified divergence point and the alternative path the tool constructed — and how much better it scores."*

**Demo script (~4 min, replay of fixture session — e.g. `fixtures/user-sessions/p1-01/`):**

> **Replay-only, dashboard-only.** On the day you are **not** recording a fresh session or running the analysis pipeline live. You are loading a pre-computed `analysis-report.json` into the **dashboard in standalone mode** (no plugin, no analysis server, no IntelliJ sandbox, no JCEF/Equinox boot). The dashboard's `useReport` hook supports a dev-server replay path: start Vite with the report path as an env var and it serves it via the `/__dev_report.json` middleware. See `tool/dashboard/src/hooks/useReport.ts` for the contract.
>
> Launch command (run before the talk, leave the tab open):
> ```bash
> cd tool/dashboard
> REFDASH_REPORT=/absolute/path/to/fixtures/user-sessions/p1-01/analysis-report.json npm run dev
> ```
> Then open the printed Vite URL (typically `http://localhost:5173/`) in a regular browser tab.

1. **(~30s) Set the scene.** "This is a real session from the user study — a participant performing a refactoring task on the order-processing fixture project. The plugin recorded their session passively while they worked normally in IntelliJ. The dashboard you're seeing is the analysis output."

2. **(~30s) Orient on the trajectory chart.** Point at the time axis, point at the cleanliness curve, point at refactoring glyphs / commit markers. *"At a glance you can already see where the session degraded code quality and where it recovered."*

3. **(~1 min) Identify the divergence point.** Open the divergence panel, sorted by magnitude. Pick the highest-magnitude one. *"The tool flagged this specific moment as the biggest divergence point in the session — magnitude X.X."* Click into it. Show the user's actions at that point (e.g. "they hand-edited an extract-method that the IDE could have done safely").

4. **(~1.5 min) Show the alternative.** Switch to the alternative-path view. *"Here's the path our synthesiser constructed instead — same starting state, same final state, but using the IDE refactoring."* Show the side-by-side score breakdown: user's path scored Y, alternative scored Y+ΔJ. Walk the audience through *which terms* improved — e.g. "manual-when-IDE penalty dropped by N, step count fell by M".

5. **(~30s) Cycle to a second example briefly** (only if comfortable on time) — ideally a different *kind* of divergence to show the taxonomy in action. Otherwise skip.

6. **(~30s) Close the demo.** *"So that's the loop: record naturally, get back a ranked list of specific moments where there was a measurably better alternative, with the alternative made concrete."*

**Demo robustness — must do before the day:**
- Pre-select 1–2 fixture sessions with at least one large, easily-explained divergence. Vet that each renders cleanly in the standalone dashboard on the actual demo laptop.
- Run `npm install` in `tool/dashboard/` on the demo machine ahead of time so `node_modules` is warm.
- Boot the dashboard once the day before via the launch command above to warm the Vite cache; confirm the fixture report loads end-to-end.
- Verify port 5173 is free immediately before going on stage (`lsof -i :5173`).
- Have the Vite tab already open and the fixture report already loaded **before** you walk on; do not boot the dev server live.
- Have a static screenshot fallback on the next slide in case the browser tab dies.

### Section 6 — Closing (Slides 19–20, ~1 min)

**Slide 19 — Contributions (~45s)**
- Four bullets, claimed plainly:
  1. A process-quality metric J(τ) that combines endpoint, process, and safety signals in one principled score.
  2. A divergence-point detector with four actionable kinds and a per-kind synthesiser that constructs concrete alternative trajectories.
  3. Three datasets — 45 injection sessions, 30-session user study, 48-session agent extension — and reproducible analysis (Jupyter notebook reproduces every table/figure).
  4. A deployable end-to-end system: IntelliJ plugin + analysis backend + dashboard.

**Slide 20 — Closing line (~15s)**
- End on the through-line, strongly. Suggested wording:
  - *"Refactoring quality isn't just about where you end up — it's about the path you walked to get there. This work makes that path measurable, comparable, and improvable."*
- Then: *"Happy to take questions."*

**Deliberately not the final slide:** limitations. If they come up in Q&A, address honestly (no external ground truth for "refactoring quality", weights not fitted to outcome data, small user-study n, agent extension shows poor transfer because detectors are human-IDE-shaped). But do not put them last.

---

## Q&A Preparation (anticipated questions)

Worth preparing a short answer (~1 min each) for:

- *"Why these seven terms and not others?"* — Drawn from prior-work signal families (Table 2.1 in background); weights ordered by literature severity. Sensitivity sweep shows ranking is robust *near* these weights.
- *"How do you know the alternative is actually better?"* — Definitionally better by J(τ), with both endpoints fixed. Magnitude is the J gap. We're not claiming it's "better" in some absolute external sense — we're claiming it scores higher under an explicit, defensible metric.
- *"Why didn't the agent extension work?"* — Detectors are shaped around human-IDE patterns: agents have no IDE refactoring surface (so everything looks "Manual-Refactor"), batch edits differently (debouncer collapses them), rarely cross commit-gap thresholds. Honest answer: tool is scoped to human sessions; agents are future work.
- *"User-study sample size?"* — n=5 (3 with-feedback, 2 baseline) over 6 sessions each = 30 sessions. Acknowledge upfront this is directional evidence; the value is in the consistent direction (and slope sign difference), not statistical significance.
- *"Why a custom score instead of an existing metric?"* — No existing metric scores trajectories; existing metrics score states. Combining state metrics with process and safety signals is the contribution.

---

## Pre-Presentation Checklist

**Room / equipment (per the supervisor's email):**
- Visit the allocated room in advance; verify projector cable (HDMI/USB-C), screen mirroring, microphone.
- Confirm Panopto recording will run; bring own backup recorder if you want a personal copy.
- Have a visible clock or phone timer set to 17:00 countdown.

**Demo machine (dashboard-only, replay-only — no plugin, no analysis server):**
- `npm install` in `tool/dashboard/` ahead of time so dependencies are resolved.
- Day before: boot the dashboard standalone via `cd tool/dashboard && REFDASH_REPORT=/absolute/path/to/analysis-report.json npm run dev` and confirm the chosen fixture renders end-to-end.
- On the day: launch the same command before walking on stage; leave the Vite tab open and the report already loaded.
- Confirm port 5173 is free (`lsof -i :5173`) immediately before going on.
- Static screenshot fallback queued on the next slide in case the browser tab dies.

**Slides:**
- Upload to Scientia (per email instruction).
- Export a PDF copy as backup in case the live deck fails to open.
- Bring on USB stick as a last-resort fallback.

---

## Verification

There is nothing to "build" here — this is a presentation plan. Verification = rehearsal:

1. **Cold rehearsal:** Run slides end-to-end with a timer. Aim for 17:00 ± 30s. If overrunning, the first cuts should be Slide 7 (cleanliness sub-score detail) and the multi-knob detail on Slide 16 (score robustness). If still overrunning, collapse Slide 8 (four-kinds overview) into the first per-kind slide.
2. **Demo rehearsal:** Run the demo at least 3 times on the actual demo machine, with the chosen fixture, with the laptop on battery (in case the room's mains is awkward). Time it; the demo should fit in 4 min comfortably.
3. **Fallback rehearsal:** Practise the demo once *from screenshots only*, in case the live dashboard fails. You should still be able to deliver the "we found this divergence, here's a better path, here's the score gap" story.
4. **Q&A rehearsal:** Have someone (supervisor / lab mate) ask 3–4 of the anticipated questions above; rehearse keeping each answer under a minute.
