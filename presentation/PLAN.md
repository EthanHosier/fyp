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
| Design space + solution shape | ~2 min | 4–5 |
| Demo part 1 (kick off live recording) | ~1 min | 6 |
| Architecture + Methodology | ~6 min | 7–15 |
| Demo part 2 (live results + prepared alternative) | ~3 min | 16 |
| Evaluation setup + headline results | ~3:15 min | 17–20 |
| Closing: contributions + impact | ~1 min | 21–22 |
| **Total** | **~18:15 min** | |

The demo is **split into two halves**. At Slide 6 the live recording is started in IntelliJ (a short refactoring on a prepared fixture project). The analysis pipeline runs in the background while the audience watches the architecture + methodology slides. By the time we reach Slide 16, the report is ready: walk through the divergence points the tool found, then show a higher-scoring alternative prepared earlier as a backup.

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

### Section 2 — Design space + solution shape (Slides 4–5, ~2 min)

**Slide 4 — Narrowing the design space (~50s)**
- Opening line: *"Evaluating refactoring as a process is a wide, mostly-unexplored design space. Four requirements shaped what we built."*
- Four design requirements, each with a one-clause prior-work nod where the report supports it:
  - **Quantifiable + comparable score** — one number J(τ) per trajectory so sessions sit on a common scale. Builds on composite quality models (Quamoco, QMOOD), adapted from snapshot- to trajectory-level.
  - **Seamless, in-workflow capture** — passive IDE plugin; zero developer effort during the session. IDE-event logging is a known-feasible approach (Damevski et al., CodeWatcher).
  - **Actionable, moment-specific feedback** — point to a specific moment AND a concrete better alternative. *Inverts* prior refactoring recommenders (ReSynth, Refactoring Navigator, search-based refactoring): they output sequences as guidance; here, the synthesised sequence is the *comparison point* against an observed trace.
  - **Efficient alternative synthesis** — the state-action space over a real codebase is too large to enumerate, so we decompose by *kind* of divergence: four targeted synthesisers, not one global search.
- Closing line (looks forward): *"These four requirements fix the shape of the tool — the desired output (next slide), the score formula (Slide 8), the four divergence kinds (Slides 10–14), and what's out of scope (live recommendations, multi-developer collaboration, review-time feedback)."*

**Slide 5 — Desired outcome: a comparable counterfactual (~45s)**
- Reframes the divergence diagram as the *concrete output* the four requirements pin down, not just a concept picture.
- *"Given those four requirements, the concrete thing we wanted the tool to produce, for any recorded session:"*
- Solid line: user's actual trajectory through code states.
- Dashed line: synthesised alternative — same start, same finish, measurably higher J(τ).
- Divergence point marks where the two paths split; ΔJ quantifies the gap.
- Figure: `presentation/images/divergence-point.png` (embedded; the line chart from `fig:divergence` in the report intro).

### Section 3 — Demo, part 1: live capture (Slide 6, ~1 min)

This is the **opening half of the demo**. While the audience is still warm, switch to IntelliJ, start a recording, and do a short refactoring on a prepared fixture project (e.g. extract method + rename + 1–2 manual edits). The plugin keeps recording in the background while we move on to slides 7–15; the analysis pipeline should be ready by the time we reach Slide 16.

**Slide 6 — Demo (part 1): live capture (~1 min)**
- Centered title slide. Tagline: *"I'll start a short live refactoring session. The tool will analyse it in the background while we walk through the methodology."*
- Live moves (rehearsed in advance):
  1. Switch to IntelliJ sandbox.
  2. Click "Start Session" in the Refactoring Tracer tool window.
  3. Perform 2–3 small refactorings (~30–60s of activity).
  4. Click "End Session" — the plugin uploads the event log to the analysis server.
  5. Switch back to slides and keep talking. Analysis runs in the background.
- **Backup if the live capture stalls:** advance to Slide 7 and use a pre-recorded fixture session at Slide 16 instead. Have its `analysis-report.json` pre-loaded in the dashboard.

### Section 4 — Architecture + Methodology (Slides 7–15, ~6 min)

**Slide 7 — What the tool does, end to end (~45s)**
- IntelliJ plugin records every edit, refactoring, build, test, commit during a session.
- Analysis backend reconstructs the trajectory, scores it, and detects divergence points.
- Dashboard surfaces: *"here is where you diverged, here is an alternative path, here is how much better it scores, and here is why."*
- Three-stage architecture diagram with per-component sub-stages and data hand-offs (plugin → analysis → dashboard).

The methodology block deliberately spends time per-divergence-kind. Each kind gets its own slide covering both what it is *and* the engineering behind detection + synthesis, to showcase the algorithmic depth on each.

**Slide 8 — Scoring a trajectory (~1 min)**
- Process score J(τ) on 0–100 scale, baseline 50.
- Seven weighted terms grouped into three buckets:
  - **Positive:** cleanliness gain (W_g=50), step-savings bonus (W_l=11)
  - **Process penalties:** intermediate-lag (11), commit-gap (7)
  - **Safety penalties:** broken build/test (28), skip-tests (14), manual-when-IDE (11)
- Key design choice: broken-state penalty is sized so **no single-step gain can outweigh a broken build** — behaviour preservation is non-negotiable.
- (Optionally show the equation; don't dwell on it.)

**Slide 9 — The cleanliness sub-score (~30s)**
- Six equally-weighted code-quality signals: cognitive complexity, coupling (CBO), duplication (CPD), readability, smells (PMD), cohesion (TCC).
- Normalised against the trajectory's own range so sessions are comparable.
- Equal weights: no trajectory-level calibration data exists, so the least-assumptive choice. *(Trim to ~30s — this is a supporting detail.)*

**Slide 10 — Four kinds of divergence (overview) (~25s)**
- Opening line: *"Four categories of process waste — each fitting the four requirements from Slide 4: detectable from the event stream, actionable for the developer, synthesisable as a concrete alternative."*
  1. **Ordering** — right refactorings, wrong sequence.
  2. **Manual-Refactor** — wrong tool (hand-edit when the IDE could safely have done it).
  3. **Rework** — wrong churn (added then removed).
  4. **Hygiene** — missing safety checkpoints (no tests, no commits).
- One sentence: *each kind gets its own slide next, covering both the detection and the synthesis algorithm.*
- Honest framing note: the report presents the four kinds as a chosen taxonomy, not as deductively-derived. Don't claim "derived"; claim "fitting".

**Slide 11 — Ordering: detect + synthesise (~45s)**
- **What:** developer applied the right refactorings, but in a suboptimal order — final state is fine, intermediate states are worse than they needed to be.
- **Detect:** find a subsequence of refactorings whose reordering preserves the terminal state (matched by canonical AST hash, formatting/comments ignored).
- **Synthesise:** build a dependency DAG over the refactorings in the window; **prefix-trie DFS** over valid topological orderings, sharing work across common prefixes; roll back with `git checkout` on backtrack.
- **Scope limit (named honestly):** skip windows > 7 steps (5040-permutation cap). This is also the source of the Ordering recall gap on slide 18.
- Use figure `methodology-reorder-trie` if it reads at slide size.

**Slide 12 — Manual-Refactor: detect + synthesise (~45s)**
- **What:** developer hand-edited something the IDE could have done safely (and with its precondition checks).
- **Detect:** run **RefactoringMiner** on sliding commit windows; cross-check against the IDE event stream — anything RefactoringMiner finds that the IDE did *not* emit is a manual refactoring.
- **Synthesise:** apply the equivalent IDE refactoring, then **three-way merge** the user's other edits back on top.
- **Wrap-and-patch layer:** reconciles minor JDT ↔ IntelliJ AST differences (e.g. static modifiers, variable liveness) so the synthesised path validates against the user's terminal state.

**Slide 13 — Rework: detect + synthesise (~40s)**
- **What:** code added and later removed (or vice versa) — net-zero churn, but real cost while it was there.
- **Detect:** hash normalised lines; pair add/remove events by `(file, scope, content-hash)`.
- **Synthesise:** strip both halves from the trajectory; replay the rest of the user's edits unchanged; validate terminal state matches.
- Simplest of the four synthesisers, but high recall: precision and recall both 1.00 on the injection set.

**Slide 14 — Hygiene: detect + synthesise (~40s)**
- **What:** long stretches of work without safety checkpoints — no test runs, no commits.
- **Detect:** 60-second windows with no test-run events; commit-gap events when commits are spaced beyond threshold.
- **Synthesise:** model an alternative cadence that intersperses test-run and commit events at the right points; the alternative's J(τ) credits those checkpoints via the skip-tests and commit-gap terms.
- Cheap to compute, but consistently surfaces meaningful divergences in the user study.

**Slide 15 — Comparable divergence magnitudes (~45s)**
- Magnitude = J(τ*_final) − J(τ_final): score gap when the alternative is substituted at the divergence point but the rest of the user's trajectory is left unchanged.
- This makes magnitudes comparable across all four divergence kinds.
- Lets the dashboard surface each divergence point alongside its score impact, so the highest-impact moments are easy to spot in the session summary.
- This is the number the demo will point at.

### Section 5 — Demo, part 2: results (Slide 16, ~3 min)

This is the **payoff half of the demo**. The live session captured on Slide 6 has been analysed by the backend during the methodology slides. We now open the dashboard, look at what the tool flagged in that live session, then contrast it against a higher-scoring alternative prepared earlier.

**Slide 16 — Demo (part 2): results (~3 min)**
- Centered title slide. Tagline: *"Now the live session has been analysed: walk through the divergence points the tool found, then show a higher-scoring alternative I prepared earlier."*

**Demo script (~3 min):**

1. **(~30s) Switch to the dashboard.** The analysis of the Slide-6 live session should now be ready. *"This is the session I started a few minutes ago. The plugin uploaded the event stream when I clicked End Session; the analysis backend reconstructed the trajectory, scored it, and looked for divergence points while we talked through the methodology."*

2. **(~45s) Orient + walk through the live results.** Point at the trajectory chart for the live session. Highlight 1–2 divergence points the tool found. *"For instance, here the tool flagged a [Manual-Refactor / Rework / Hygiene] divergence at this step — magnitude X.X."* Click into one and show what the alternative would have been.

3. **(~1 min) Show the pre-prepared higher-scoring alternative.** Switch to a second pre-loaded session (run by you earlier, doing roughly the same refactoring task but cleanly — IDE refactorings throughout, tests run, commits at sensible points). *"Same starting code, same final state. Look at the score gap — this version scores Y points higher because the broken-state penalty is gone and the manual-when-IDE penalty drops to zero."*

4. **(~30s) Land the close.** *"So that's the loop: record naturally, get back specific moments where there was a measurably better alternative — with the alternative made concrete and the score gap explained."*

**Demo robustness — must do before the day:**
- Pre-rehearse the live Slide-6 refactoring on the actual demo project so it consistently triggers at least one divergence point. Aim for a session with an obvious manual-refactor or rework that the tool will catch.
- Boot the analysis backend and Vite dashboard **before** the talk (`./gradlew runPluginAndServers` in `tool/`, or just the analysis server + dashboard separately). Verify both are reachable.
- Verify ports 8080 (analysis) and 5173 (dashboard) are free.
- Have a **pre-recorded fixture session** loaded in a separate dashboard tab as a backup — if the live capture stalls or analysis fails, switch tabs and use the fixture for Slide 16.
- The pre-prepared "higher-scoring alternative" session must be already analysed and pre-loaded — there's no time on stage to run it.
- Have a static-screenshot fallback queued in case the dashboard fails to render at all.

### Section 6 — Evaluation (Slides 17–20, ~3:15 min)

**Slide 17 — What we wanted to evaluate (~45s)**
- Frames the next three slides around three concrete questions, each tied to its dataset + experiment. Replaces the bare "three datasets" enumeration.
- Each bullet: **bold question** + grey "answered by" clause with forward-reference to the slide where the answer lives.
  - **Is the tool accurate? Does it detect real divergences without false positives?** *45 labelled injection sessions, with deliberately-introduced bad behaviours, scored against per-kind precision and recall (Slide 18).*
  - **Is the process score reliable? Does the ranking hold up when the weights are perturbed?** *Injection set + user-study rankable subset, used for the sensitivity sweep and ablation study (Slide 19).*
  - **Does the tool change developer behaviour? Fewer divergences over time when feedback is shown?** *30-session randomised user study, plus a 48-session agent extension as motivation for future work (Slide 20).*
- Visual pattern matches Slide 4 (bold main + grey reference) for consistency.

**Slide 18 — Detector precision & recall (~1 min)**
- Per-kind table from results chapter:
  - **Precision = 1.00 across all four kinds.** Every detection is valid.
  - **Recall:** Rework 1.00, Hygiene 1.00, Manual-Refactor 0.76, Ordering 0.40.
  - Inter-rater agreement (Cohen's κ): 1.00 for Ordering & Manual-Refactor, 0.86 for Rework, 0.72 for Hygiene.
- Ordering recall gap is honest and explained: synthesiser rejects windows it can't safely reproduce. A scope limit, not a detector flaw.

**Slide 19 — Score robustness (~45s)**
- Single-knob sensitivity sweep: scaling any one weight by {0.1×–10×}, top-1 recommendation preserved in **96.5%** of user-study cases.
- Multi-knob Monte Carlo (200 samples): top-1 stability drops to 84.6%, mean Kendall τ = 0.586. Honest framing: stable *near* chosen weights, not at arbitrary values.
- Ablation confirms each process term contributes real signal — endpoint gain alone doesn't recover the ranking.

**Slide 20 — User study: does feedback change behaviour? (~45s)**
- Headline numbers:
  - With-feedback group: **2.7 divergence points / session**.
  - No-feedback baseline: **6.0 divergence points / session**. (**2.2× difference.**)
  - Gain-stripped process-score slope across the 6-session arc: **+4.47 / session with feedback**, **−0.40 / session without**.
- Honest caveat (one line): n=3 vs n=2 is directional evidence, not a hypothesis test.

### Section 7 — Closing (Slides 21–22, ~1 min)

**Slide 21 — Contributions (~45s)**
- Four bullets, claimed plainly:
  1. A process-quality metric J(τ) that combines endpoint, process, and safety signals in one principled score.
  2. A divergence-point detector with four actionable kinds and a per-kind synthesiser that constructs concrete alternative trajectories.
  3. Three datasets — 45 injection sessions, 30-session user study, 48-session agent extension — and reproducible analysis (Jupyter notebook reproduces every table/figure).
  4. A deployable end-to-end system: IntelliJ plugin + analysis backend + dashboard.

**Slide 22 — Closing line (~15s)**
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

1. **Cold rehearsal:** Run slides end-to-end with a timer. Aim for ≤18:00. If overrunning, the first cuts should be Slide 9 (cleanliness sub-score detail) down to ~20s and the multi-knob detail on Slide 19 (score robustness). If still overrunning, drop one of the prior-work parentheticals on Slide 4 (e.g. "(Damevski et al., CodeWatcher)") or collapse Slide 10 (four-kinds overview) into the first per-kind slide.
2. **Demo rehearsal:** Run the demo at least 3 times on the actual demo machine, with the chosen fixture, with the laptop on battery (in case the room's mains is awkward). Time it; the demo should fit in 4 min comfortably.
3. **Fallback rehearsal:** Practise the demo once *from screenshots only*, in case the live dashboard fails. You should still be able to deliver the "we found this divergence, here's a better path, here's the score gap" story.
4. **Q&A rehearsal:** Have someone (supervisor / lab mate) ask 3–4 of the anticipated questions above; rehearse keeping each answer under a minute.
