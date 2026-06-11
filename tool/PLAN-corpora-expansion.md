# Plan: incorporate the expanded user + agent corpora into Chapter 5 + the notebook

## Context

The user study has grown from 2 → 5 participants (6 sessions each = 30 sessions). The agent comparison has grown from 1 → 8 agent stacks (6 sessions each = 48 sessions). Two participants and two agent stacks are no-feedback baselines — they ran the same playbook + reset cycle but were never shown the tool's feedback between sessions. This adds a *causal* arm to the chapter that wasn't possible with the original observational design.

Naming convention (verified):
- User participants ending in `-baseline`: `p4-baseline`, `p5-baseline` (no-feedback).
- User participants without suffix: `p1`, `p2`, `p3` (with-feedback).
- Agent stacks ending in `-baseline`: `claude-4.7-opus-medium-claude-code-baseline`, `gpt-5.5-medium-opencode-baseline`.
- Agent stacks without suffix: 6 cells covering Claude / GPT / Gemini × Claude Code / OpenCode / Cursor.

Per-cell session count is always 6.

The headline structural change Chapter 5 needs: §5.3 (user study) and §5.4 (agent comparison) both grow a new subsection on the with-feedback vs no-feedback comparison. §5.1 (robustness experiments) gets its numbers refreshed under the larger N.

## A. Thesis updates (final_report/results/results.tex + abstract/conclusion/threats)

### A1. §5.3 user study — restructure

Existing section is built around P1 and P2. Restructure into:

1. **Intro** — five participants × six sessions = 30 sessions; 3 with-feedback (P1, P2, P3) + 2 no-feedback baseline (P4, P5). Anonymisation P1..P5 kept.
2. **§5.3.1 Per-kind distribution** — extend `tab:results-userstudy-distribution` from 12 to 30 rows; add `condition` column (`with-feedback` / `baseline`).
3. **§5.3.2 Per-participant trajectory** — extend `tab:results-userstudy-trajectory` and the gain-stripped trajectory plot to 5 lines (3 solid + 2 dashed).
4. **§5.3.3 (new) With-feedback vs no-feedback comparison** — per-participant gain-stripped slope (J_S6 − J_S1) / 5, grouped by arm. Honest claim: 3 vs 2 doesn't support hypothesis testing, but the direction and magnitude are reportable. Threats subsection updated to reflect this.
5. **§5.3.4 Step-UI interview observations** — keep existing P1+P2 notes; extend with P3, P4, P5 if interview material exists (verify before writing).

### A2. §5.4 agent comparison — restructure

**Framing (this is the headline framing change for the whole agent section).**
The thesis is about a tool for evaluating **human** refactoring behaviour. The agent
comparison is an *extension experiment*, not a second contribution. The tool was not
optimised for agent traces, and several of its detectors are structurally biased
against agents (see "DP-detection limitations on agent traces" below). The whole §5.4
should be reframed accordingly:

- Stop calling it "the agent comparison study" — call it the **agent extension** or
  **agent feasibility experiment**.
- Drop any sentence that implies the tool was *designed to* monitor agents.
- Remove causal language about feedback driving agent course-correction. The data
  doesn't carry that claim (the baseline arm matches or beats the feedback arm on
  ΔJ), and even the qualitative engagement is split by model family.
- Make explicit that what §5.4 tests is "does a tool built for humans produce any
  signal on agent traces at all" — and the answer is mostly "yes for descriptive
  arcs, less so for cross-arm comparison."

Existing section is one Claude Code run. Restructure into:

1. **Intro** — reframe as extension experiment per the framing block above. 8 agent
   cells × 6 sessions = 48 sessions; 6 with-feedback + 2 no-feedback baselines. State
   upfront that agents start much more disciplined than humans (mean S1 gain-stripped
   J ≈ 33 vs 23 for humans; baseline DP rate ≈ 0.5 / session vs 6.0 for human
   baseline), so the ceiling for improvement is lower regardless of intervention.
2. **§5.4.1 Per-cell descriptive arc** — table of (cell, S1 J, S6 J, ΔJ, slope) across 8 rows. Descriptive only.
3. **§5.4.2 Cross-stack effects** — three subtables/paragraphs:
   - Model axis (held-out OpenCode harness): cells 2 / 3 / 4.
   - Harness axis (held-out Claude model): cells 1 / 2 / 5.
   - Cross-stack: cell 1 vs cell 6.
4. **§5.4.3 With-feedback vs no-feedback** — report the numbers (mean ΔJ +4.0 with
   feedback vs +8.5 baseline) honestly. Frame as a **null-to-inverted result**: under
   this protocol, agent process quality is not improved by exposing the tool's
   between-session feedback summary. List the candidate reasons (next bullet) so the
   reader can locate the result in context rather than treating it as a defeat.
5. **§5.4.4 DP-detection limitations on agent traces** (new subsection). Three
   structural reasons the tool under-detects agent divergences, which together
   contribute to a *floor effect* on what the agent ΔJ could be:
   - **No IDE-driven refactorings.** Agents apply file edits via tool calls, not via
     the IntelliJ refactoring menu. The IDE_REPLAY kind, which fires when a sequence
     of manual edits could have been replaced by a single IDE refactor invocation, is
     structurally moot for agents — there is no manual-vs-IDE choice to flag.
   - **Commit-gap threshold rarely triggered.** The `commitGap` detector flags
     six refactor checkpoints elapsed without a commit. Agent sessions tend to batch
     many file changes into each checkpoint and arrive at S6 in fewer total
     checkpoints than a human session of equivalent calendar length; combined with
     the short ~6-session arc used here, the six-checkpoint threshold is rarely
     reached.
   - **Edit-burst debouncer is too short to matter for agents.** The edit-burst
     tracker debounces on a 2 s idle timeout
     (`tool/ide-plugin/src/main/kotlin/com/github/ethanhosier/ideplugin/services/EditBurstTracker.kt:189`,
     `DEBOUNCE_MS = 2000L`); it does fire on tool-applied file writes, but because
     agents apply many file edits in a tight burst followed by a longer pause for
     reasoning, all the edits of a single agent "turn" tend to coalesce into one
     burst event. The signal that the burst-vs-checkpoint kinds depend on (many
     interleaved bursts between successive checkpoints) is therefore compressed.
     The user's original intuition was that the debouncer was 60 s; flag in §5.4 that
     the actual timeout is 2 s but the structural effect on agent traces is similar
     — agent reasoning latency exceeds 2 s, so each "tool turn" still collapses to
     one burst.

   Note clearly: these are not bugs. The tool was scoped for humans, and these
   detectors do the right thing on human traces. The point of §5.4.4 is to tell the
   reader why the agent results should be read as descriptive, not as a verdict on
   the tool's utility for agents.
6. **§5.4.5 Qualitative threads — feedback engagement varies by model family.**
   The transcript skim shows a sharp split:
   - **Claude family (cells 1, 2, 3) explicitly engaged with the feedback.**
     Cell 1 (`claude-2-opus-4.7-claude-code`) is the strongest case: after S1
     warnings about `BUILD_OFTEN_BROKEN`, `TEST_REGRESSIONS`, and
     `PROCESS_SCORE_DEGRADED`, S2 opens with "I'll use the atomic-edit-per-file
     approach … batched each file's constant introductions atomically … then ran
     tests once green before committing." After S3 flagged
     `REFACTORING_INTRODUCED_SMELLS`, the agent's S4 plan included "when extracting
     a method, leave the extracted body cleaner … extract and tidy, not extract and
     stop." Similar pattern in cell 2 (`claude-opus-4.7-medium-opencode`): after S1,
     "BUILD_OFTEN_BROKEN … likely caused by intermediate states … I'll land each
     rename atomically per checkpoint." Cell 3 shows quieter engagement but still
     visible adjustment after the S5 IDE_REPLAY summary.
   - **Non-Claude cells (4 Gemini, 5 GPT-5.5 cursor, 6 GPT-5.5 opencode) showed no
     visible engagement.** They proceeded with consistent (and competent) behaviour
     session-to-session but did not acknowledge or react to the between-session
     feedback in their conversation traces.
   - The Claude Code threads from the old §5.4 (commit cadence, smell self-diagnosis,
     IDE-replay limitation) stay as observations about cell 1 specifically — make
     sure the rewrite identifies them as cell-1 anecdotes, not "the agent."
   This split is interesting on its own (it suggests differential treatment of
   reflective critique across model families), but it does **not** rescue a causal
   claim — even on the Claude cells, the engagement did not produce a statistically
   distinguishable ΔJ vs the baseline. Report this honestly.

### A3. §5.1 sensitivity / ablation / multi-knob MC — refresh numbers

The existing tables use the *old* 12-user + 6-agent corpora. With the larger N (30 + 48), every numeric in §5.1 shifts.

**Recommendation: include baselines in the corpora (Option A).** The sensitivity/ablation experiments test the *score formula*, not the *feedback effect*. Both arms are valid input to robustness testing, and the larger N strengthens that claim. (Option B = exclude baselines → 18 user + 36 agent → kept as fallback if the baselines materially distort robustness numbers; document the choice in §5.1 design notes either way.)

### A4. Abstract / conclusion / threats refresh

- **Abstract / introduction framing.** State the thesis goal as a tool for evaluating
  **human** refactoring trajectories. The agent material is an extension experiment,
  not a co-equal contribution. Concretely:
  - Replace "two participants" with "five participants split across with-feedback
    (n=3) and no-feedback (n=2) arms."
  - One sentence on the **human** feedback-causation directional finding (ΔJ +21 vs
    −5, n=3 vs n=2, no *p*-value claimed).
  - One sentence on the agent extension: "as an extension experiment, the same
    protocol was applied to eight agent configurations; the tool's human-facing
    detectors produce descriptive arcs on agent traces but do not show feedback-
    driven course-correction, consistent with the structural biases of those
    detectors against agent-style edit patterns."
- **§7.2 headline findings**: lead with the human finding. Demote the original
  Claude-Code course-correction paragraph to a single bullet citing it as a
  qualitative engagement example from cell 1, not as the headline.
- **§6 construct / external validity**: update "n=2, observational" caveat to "n=5
  split 3/2"; note the with-vs-without comparison is observational (not randomised)
  and that 2 baseline participants gives a directional signal but not statistical
  power. Add a new construct-validity item: **the tool's detectors are optimised for
  human edit patterns** (manual-IDE replay, edit-burst structure, six-checkpoint
  commit-gap window), so agent results are a feasibility extension, not a measure of
  the tool's intended evaluative target.

## B. Notebook + analysis-script updates

`loadCorpus` rewrite already landed (walks for `phase-a.json`). Remaining changes in `tool/fixtures/notebooks/build_notebook.py` (then `python3 build_notebook.py` regenerates `experiments.ipynb`).

### B1. Arm-classification helpers (after `loadCorpus`)

```kotlin
// `-baseline` suffix on the participant (user-sessions) or stack name
// (agent-sessions) marks the no-feedback control arm.
fun isBaseline(sid: String): Boolean {
    val stem = sid.substringBeforeLast('-')   // drop the trailing -NN
    return stem.endsWith("-baseline")
}
fun participantOf(sid: String): String = sid.substringBeforeLast('-')
fun cellOf(sid: String): String = sid.substringBeforeLast('-')
```

### B2. Per-participant / per-cell aggregator helpers

Group loaded sessions by participant / cell, compute per-session gain-stripped J (zero out W_g and re-run `ReportAssembler.assemble`), per-arc slope (S1 → S6).

### B3. New plots / tables

- Per-participant trajectory plot — gain-stripped J vs session index; 5 lines (3 solid + 2 dashed).
- Per-cell trajectory plot for agents — 8 lines (6 solid + 2 dashed).
- Arm-aggregate trajectories — mean over arm members; 2 lines per study (with-feedback / baseline).
- Slope summary table — `(name, arm, S1_J, S6_J, ΔJ, slope)`.
- Cross-stack comparison table for agents — three subtables structured around the model / harness / cross-stack axes.

### B4. Existing tables auto-refresh

Per-kind distribution, sensitivity/ablation/MC all flow through `loadCorpus`, so they automatically include the larger N once the notebook regenerates. Numbers in the thesis tables get re-cited from the new run.

### B5. Kotlin experiment classes — deferred follow-up

`AblationExperiment.kt` / `SensitivityExperiment.kt` / `MultiKnobMonteCarloExperiment.kt` / `DivergenceExperiment.kt` / `UserSessionStats.kt` still hard-code the old `--corpus <flat-dir>` interface. The notebook is the source of truth per the README, so these get updated later (parallel walk for `phase-a.json`, same fixture-id derivation). Tracked as follow-up, does not block this round.

## C. Decisions to make before any prose / code lands

1. **Anonymisation** of the three new participants: extend the P1, P2 scheme to P3, P4, P5 (recommended), or use real first names since the participants consented? Thesis currently anonymises.
2. **§5.3.4 interviews**: did P3 / P4 / P5 do post-session interviews, or only P1+P2? Affects whether §5.3.4 stays single-section or expands.
3. **§5.1 corpus inclusion** (Option A vs B): include baselines in the robustness corpora, or exclude them?
4. **Order of cells in §5.4 tables**: by cell number (1..8 from the original planning table), or grouped by arm (with-feedback first, baselines together)?

## Out of scope for this plan

- Migrating the 5 Kotlin experiment classes to the new session-tree layout (deferred per §B5).
- Re-running phase A on any sessions — phase-a.json files already regenerated by `tool/scripts/regenerate-phase-a-dumps.sh`.
- LFS migration commands — covered in earlier work; the `.gitattributes` patterns already route `phase-a.json` files through LFS.

## Verification

1. `cd tool && python3 fixtures/notebooks/build_notebook.py` regenerates `experiments.ipynb`; the corpus-load cell reports `injection=45, user-study=30, agent=48`.
2. New trajectory plot cells render without errors when the kernel runs end-to-end.
3. Slope-summary table cells produce 5 rows (user) + 8 rows (agent) with `arm` column populated correctly via `isBaseline()`.
4. `cd final_report && ./build.sh --light` succeeds after each thesis-rewrite increment; spot-check that updated tables in the rendered PDF show 30 user rows / 8 agent rows / refreshed §5.1 numerics.
5. The abstract on the rendered PDF reads with the new participant counts.
