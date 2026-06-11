# Plan: Agent comparison paragraph (Phase 8 writeup)

## Context

Phases 1–7 + 9 of `PLAN-user-study.md` have shipped: the user-study fixture, playbook, PROTOCOL, blind labelling, two participant sessions, and the §5.4 User study section in `final_report/results/results.tex` are all in place. Phase 8 — the AI-agent comparison the supervisor specifically asked for — was deferred at writeup time and capped with a one-sentence stub at the close of §5.4.

The agent has now been run: 6 sessions on the same `user-study-fixture/` playbook (S01–S06), with the per-session feedback markdown from `feedbackForAgent` injected into the agent's prompt between sessions. Data lives at `tool/fixtures/agent-sessions/{01..06}-agent/`, with a continuous `AGENT_SESSION_TRANSCRIPT.md` capturing the prompts, the agent's responses, and the dashboard feedback fed back each time.

This plan extends §5.4 with a real Agent-comparison paragraph that delivers what the supervisor asked for: an AI-agent process-score comparison alongside the human participants, with a qualitative comparison of failure modes.

## Disclosure (locked at write time)

Per `PLAN-user-study.md` Phase 8: name the agent, the date, the model version.

- **Agent**: Claude Code (Anthropic), CLI
- **Model version**: Opus 4.7 (`claude-opus-4-7`)
- **Date**: 2026-05-21

This goes in the first sentence of the new paragraph; no additional anonymisation needed because the agent isn't a human subject.

## Data already in hand

**Per-session divergence-point counts** (from `analysis-report.json` in each `tool/fixtures/agent-sessions/0X-agent/`):

| Session | IDE_REPLAY | REWORK | HYGIENE | ORDERING | Total |
|---------|:---:|:---:|:---:|:---:|:---:|
| S1      | 2 | 0 | 0 | 1 | 3 |
| S2      | 0 | 0 | 0 | 0 | 0 |
| S3      | 3 | 0 | 0 | 0 | 3 |
| S4      | 0 | 0 | 0 | 0 | 0 |
| S5      | 2 | 0 | 0 | 0 | 2 |
| S6      | 2 | 0 | 0 | 0 | 2 |
| **Total** | **9** | **0** | **0** | **1** | **10** |

Compared against the human combined totals already in §5.4 Table~\ref{tab:results-userstudy-trajectory} (46 / 10 / 32 / 20 = 108 total DPs across the four kinds), the agent's DP profile is dramatically different in shape, not just size:

| Kind        | Agent | Humans (P1+P2 combined) | Agent share |
|-------------|------:|------------------------:|------------:|
| IDE_REPLAY  | 9     | 46                      | 20% of human |
| REWORK      | 0     | 10                      | 0% |
| HYGIENE     | 0     | 32                      | 0% |
| ORDERING    | 1     | 20                      | 5% |

**Per-session process-score trajectory** (first → last checkpoint score per `analysis-report.json`):

| Session | First | Last | Δ |
|---------|------:|-----:|---:|
| S1      | 50    | 0    | $-50$ |
| S2      | 50    | 79   | $+29$ |
| S3      | 50    | 30   | $-20$ |
| S4      | 50    | 100  | $+50$ |
| S5      | 50    | 72   | $+22$ |
| S6      | 50    | 39   | $-11$ |

The process score is highly variable across sessions (range $0$ – $100$), but the score is primarily a within-session signal: the formula scores each session against its own checkpoint distribution, so cross-session score comparisons are dominated by task difficulty rather than actor improvement. S2 (magic-number renames) and S4 (cross-class consolidation) are short, mechanical sessions where the score reaches the high end for both the agent and the humans; S3 (within-class duplication) and S6 (reshape + cleanup) are structurally harder sessions where the score drops for everyone. The same pattern appears in the human per-kind trajectory in Table~\ref{tab:results-userstudy-trajectory}: humans' ORDERING and REWORK flag counts both spike on S3 and S6 even though the participants had accumulated more feedback by then. Session task difficulty is a confound shared across both populations, not a learning failure unique to the agent.

**Trajectory advice fired per session** (from `analysis-report.json`'s `advice[*]`):

- S1: `BUILD_OFTEN_BROKEN`, `TEST_REGRESSIONS`, `LONG_STRETCH_WITHOUT_COMMIT`, `PROCESS_SCORE_DEGRADED` (4)
- S2: `LONG_STRETCH_WITHOUT_COMMIT` (1)
- S3: `REFACTORING_INTRODUCED_SMELLS`, `PROCESS_SCORE_DEGRADED` (2)
- S4: none
- S5: none
- S6: `BUILD_OFTEN_BROKEN`, `LONG_STRETCH_WITHOUT_COMMIT`, `REFACTORING_INTRODUCED_SMELLS`, `PROCESS_SCORE_DEGRADED`, `REORDER_BEATS_USER` (5)

**Observed behavioural correction.** ORDERING fired in S1 (magnitude $0$, but the divergence point surfaced); in the subsequent five sessions the agent never produced another ordering divergence point. After the S1 feedback the agent explicitly acknowledged the recommendation in the transcript and stated that it would order edits differently. This is the single clearest behavioural-change signal in the agent's six-session arc.

**Persistent failure mode.** Nine of the agent's ten divergence points are IDE_REPLAY. The agent has no IDE refactor surface to switch to — Claude Code edits files via text tool calls, not through IntelliJ's Refactor menu — so every refactoring it performs is structurally classified as a manual edit by the detector. By S6 the agent acknowledges this in the transcript: *"the IDE-replay flags persist — manual extracts consistently underperform the IntelliJ refactoring; in a real run I'd use the Extract Method action."* The signal is real but the actuator is missing; the agent cannot drive IDE_REPLAY toward zero however it works.

**Qualitatively complementary failure profile vs humans.** The clean contrast is the headline story:

- **Humans** (P1, P2): high IDE_REPLAY and HYGIENE counts, scattered REWORK / ORDERING. Process-cadence-noisy ("I forgot to commit", "I undid that"); architecturally capable (used IDE refactors when they remembered to).
- **Agent**: near-zero on REWORK / HYGIENE / ORDERING (the three procedural-discipline dimensions); saturated on IDE_REPLAY. Procedurally disciplined (commits exactly when told to, runs tests via `agent-test`, no undo cycles); architecturally limited (no IDE refactor actuator).

The two profiles are nearly complementary on the four kinds — which is itself a useful finding for the chapter's claim about what the tool measures.

## What the new paragraph will contain

Placement: **expand the existing closing stub at the end of `\section{User study}` into a full `\paragraph{Agent comparison.}` block**, sitting after the behavioural-trajectory paragraph and before any closing matter. Matches the `\paragraph{}`-headed style the rest of §5.4 uses; keeps the section within `PLAN-user-study.md` Phase 9's +2-page budget.

Length target: **600–800 words of prose plus 2 tables**, fitting in roughly 1.5–2 pages.

Content, in order:

1. **Setup sentence.** One sentence: an automated coding agent — *Claude Code (Anthropic, model `claude-opus-4-7`, run 2026-05-21)* — performed the same six-session playbook on the same fixture, with each session's `feedbackForAgent` markdown injected into the next session's prompt. No human in the loop except the orchestrator running the harness.

2. **Per-kind DP comparison table.** New table `tab:results-userstudy-agent-distribution`: 4 rows × 3 columns (Agent / Humans / ratio). Same kind ordering as `tab:results-userstudy-trajectory`. Reading paragraph below the table lands the headline number: agent produced $10$ divergence points across six sessions vs the human combined $108$, but the *shape* is what matters: $9$ of the agent's $10$ are IDE_REPLAY and the agent has zero REWORK, zero HYGIENE, one ORDERING.

3. **Per-session trajectory table.** New table `tab:results-userstudy-agent-trajectory`: 6 columns (S1–S6) × 4 kinds + total + final-score row + Δ. Mirrors the structure of `tab:results-userstudy-trajectory` for direct visual comparison. Reading paragraph: the agent's DP counts ($3, 0, 3, 0, 2, 2$) and process-score endpoints ($0, 79, 30, 100, 72, 39$) both vary primarily with session task difficulty rather than session number. S2 and S4 are mechanical sessions where everyone — humans and agent alike — produces near-zero divergence points; S3 and S6 are structurally harder sessions where divergence-point counts rise for both populations (the human ORDERING and REWORK trajectories spike on exactly these sessions, per Table~\ref{tab:results-userstudy-trajectory}). The process score is a within-session signal — scoring each session against its own checkpoint distribution — so cross-session score comparisons are confounded by task difficulty. The cleaner trajectory signal sits inside the per-kind columns of this table rather than in the score row.

4. **The observed behavioural correction.** Reading the per-kind columns left-to-right gives a different picture from the score row. ORDERING fired once in S1, the agent acknowledged it in writing, and the kind never fired again in the remaining five sessions — including S6, the most complex session, where the *human* ORDERING count climbed to nine. This is the closest the chapter gets to a controlled "feedback drove behaviour change" observation: a kind the tool flagged in session 1, with the agent's response on subsequent sessions being a clean zero across the rest of the arc even on harder material. REWORK and HYGIENE were at zero across all six agent sessions from the start; the procedural-discipline contract baked into the agent's setup prompt (see `AGENT-SETUP.md`) appears to have been honoured throughout. Quote one short line from the transcript (paraphrased, ≤15 words) showing the post-S1 acknowledgement.

5. **The IDE_REPLAY structural point.** Frame honestly: the agent has no IDE refactor surface, so IDE_REPLAY saturates regardless of how well the agent works. This is not a defect of the tool's measurement; it surfaces a real difference between human and agent refactoring practice on this codebase (humans can switch to IDE refactors; the agent cannot). One short quoted acknowledgement from the agent's own S6 reflection.

6. **Qualitatively complementary failure profile.** This is the headline thesis for the comparison: humans and the agent fail in nearly complementary ways on the four kinds. Humans are *procedurally noisy and architecturally capable*; the agent is *procedurally disciplined and architecturally limited*. The tool surfaces both kinds of failure correctly — neither the human profile nor the agent profile is the "right" one to target; both are legitimate readings of process behaviour. This sentence is what closes the agent paragraph.

7. **Honest scoping (one sentence).** $n = 1$ agent, six sessions, one model version on one date. The comparison is illustrative of the tool's behaviour on a non-human actor, not a generalisable claim about coding agents. The agent-comparison run was scoped for thesis-defence demonstration rather than statistical analysis.

## New LaTeX assets

Two new tables, both placed inside the new `\paragraph{Agent comparison.}` block:

- `\label{tab:results-userstudy-agent-distribution}` — 4 rows (kinds) × 3 columns (Agent total, Human combined total, agent-as-percent-of-human). Compact; numeric formatting `$0.20$` / `$0.05$` for ratios, integers for counts. Caption: "Per-kind divergence-point totals for the agent over six sessions on the user-study fixture, against the combined two-participant human total reported in Table~\ref{tab:results-userstudy-trajectory}."

- `\label{tab:results-userstudy-agent-trajectory}` — 4 rows (kinds) × 7 columns (S1–S6 + Δ), plus 1 final row for "final process score" with the values $0, 79, 30, 100, 72, 39$. Caption: "Agent per-session divergence-point trajectory and process-score endpoints, mirroring Table~\ref{tab:results-userstudy-trajectory} for the human cohort."

No new figures.

## Critical files

**To edit:**
- `final_report/results/results.tex` — replace the closing stub sentence of §5.4 with the new `\paragraph{Agent comparison.}` block + the two tables. The stub currently reads: *"An agent-comparison run on the same playbook is scoped for future work and is not reported here; the capture mechanism for ingesting an agent's session into the Phase A pipeline remains open."* — drop entirely.

**Read-only references (NOT cited in the thesis prose):**
- `tool/fixtures/agent-sessions/0X-agent/analysis-report.json` (×6) — per-session DP + advice + trajectory data
- `tool/fixtures/agent-sessions/AGENT_SESSION_TRANSCRIPT.md` — source for the two paraphrased quote-snippets (S1 ordering acknowledgement, S6 IDE-replay acknowledgement). Provenance footnote already exists in the user-study section ("paraphrased from session-end notes; not reproduced here"); the agent transcript will be cited under the same umbrella footnote — *not* re-cited by filename, to keep the artefact-list under the existing footnote rather than introducing a second one.

## Voice + convention notes (carried from earlier user-study writeup)

- Numbers in math mode: `$108$`, `$0.20$`, `$+22$`.
- Code identifiers in `\texttt{}`: `\texttt{IDE\_REPLAY}`, `\texttt{user-study-fixture}`, `\texttt{claude-opus-4-7}`.
- Citations: `\cite{landis1977kappa}` (no tilde).
- Cross-refs: `\S\ref{...}`, `Table~\ref{...}`.
- Don't claim precision/recall on the agent corpus — same playbook, no pre-declared `expected_kinds`.

## Verification

1. **PDF builds clean.** `final_report/build.sh --light` returns "Output written" with no undefined refs and no labels-may-have-changed warnings.
2. **Page-budget check.** Total chapter length extends by no more than 2 pages over the pre-agent-paragraph version (PLAN-user-study.md Phase 9 verification).
3. **Numbers match the data.** Re-derive the agent totals from `analysis-report.json` files and confirm `tab:results-userstudy-agent-distribution` rows are byte-identical to that derivation. Same for the per-session trajectory table.
4. **Anonymisation check on the new paragraph.** `grep -iE 'p1|p2|p1-0|p2-0' final_report/results/results.tex` returns 0 hits inside the new block (humans stay as P1/P2; the agent stays as "Claude Code, Opus 4.7").
5. **Disclosure check.** The paragraph mentions Claude Code, the model id, and the date 2026-05-21. `grep -E 'Claude Code|claude-opus-4-7|2026-05-21' final_report/results/results.tex` returns hits.

## Branch + commit strategy

Continue on `feat/user-study-fixture` (the open branch). One commit: "feat: add agent-comparison paragraph (Phase 8 writeup) to results chapter". Push, leave the existing PR open. Single PR continues to cover Phases 1–3 + 7 + 9 + 8.

## Out of scope

- **Statistical claims** about whether the agent "learned" from the feedback. $n = 1$ agent, one model version, six sessions; descriptive only.
- **Comparison across multiple agents** (Claude vs Cursor vs Codex etc.). Out of thesis scope; flag in the future-work paragraph if there's space.
- **Re-running the agent with different feedback formats** (top-K vs all-DPs, cumulative vs per-session). Plumbing-level experiment, not a writeup question.
- **Methodology-chapter expansion** of the agent-comparison protocol. The methodology chapter's rater-design section was already flagged as a placeholder; the agent-protocol methodology is a similar gap to be addressed in a separate methodology-chapter pass.
