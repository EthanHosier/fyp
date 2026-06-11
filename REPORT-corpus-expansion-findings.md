# Chapter 5 findings on the full corpus

**Status.** Numbers extracted from a headless `nbconvert --execute` of
`tool/fixtures/notebooks/experiments.ipynb` plus a side TSV-dump cell, run against
the full corpus on 2026-05-28. The thesis has *not* been edited; this is reading
material before the rewrite tracked in `tool/PLAN-corpora-expansion.md`.

**Corpus.** 45 injection sessions (controlled bad-behaviour playbook on
`library-fixture/`); 30 user-study sessions (5 participants × 6, 3 with-feedback +
2 no-feedback baseline, on `user-study-fixture/`); 48 agent extension sessions (8
agent stacks × 6, 6 with-feedback + 2 no-feedback baseline, same fixture).

## Framing reminder

The thesis is about a tool for evaluating **human** refactoring trajectories. The
agent material (§5.4) is an **extension experiment**, not a co-equal contribution.
The tool was not optimised for agent traces, and three of its detectors are
structurally biased against agent-style edit patterns (see §5.4 below). Treat the
human findings as the thesis's load-bearing claim and the agent findings as
descriptive, with explicit limitations.

## Executive summary

Four things to take away.

1. **§5.1 robustness is solid on the rankable subset.** Rank statistics now use the rankable subset of each session set (sessions with ≥ 2 detected divergence points; sessions with ≤ 1 DP are excluded because τ-b and top-1 are mechanically 1.0 on them). This leaves 15/45 injection, 25/30 user-study, 20/48 agent sessions in scope for rank. Top-1 stability on the user-study rankable subset is 96.5 % single-knob and 84.6 % multi-knob. Magnitude statistics in the ablation tables continue on the full 45 injection sessions.

2. **`gain` is the most disruptive knob.** Under the 30-session user-study set,
   perturbing the cleanliness-gain weight `W_g` disrupts top-1 ranking on 8.9 % of
   single-knob sweeps — more than any other knob. The §5.1 narrative should be
   built around `gain`'s leverage.

3. **The baseline arm validates the human feedback story.** With-feedback users
   gain +21 J (gain-stripped) and drive total divergence points from 12 → 2 over
   six sessions. No-feedback users *lose* 5 J and go from 10 → 15 DPs. This is the
   headline finding of the corpus and is the part the thesis should lead with.
   Direction (n = 3 vs n = 2) is honest evidence; *p*-values are not claimed.

4. **The agent extension shows no feedback effect — but this reads as a tool-scope
   limitation, not a tool failure.** Agents start much more disciplined than humans
   (mean S1 gain-stripped J ≈ 33 vs ≈ 22 for human baselines; agent baseline DP
   rate ≈ 0.5 / session vs 6.0 for human baselines), so there is little room to
   improve. Three structural detector biases compound this floor effect: agents
   can't trigger IDE_REPLAY, rarely trigger the 6-checkpoint `commitGap` window in
   a 6-session arc, and have their edit-burst structure collapsed by the 2 s
   debouncer because tool-applied writes arrive faster than reasoning pauses. The
   qualitative split is worth reporting: **Claude-family cells (1, 2, 3)
   explicitly engage with the feedback in their transcripts; Gemini and GPT-5.5
   cells (4, 5, 6) do not** — but even on Claude cells the engagement does not
   translate into a ΔJ separable from baseline.

The rest of this document walks every table.

---

## §5.1 Robustness

### Table 5.1 — Sensitivity headline (top-1 stability)

| Set (rankable)     | N | sweep rows | τ = 1.0  | top-1 = 1 | τ < 0 | clamp-frozen rate |
|--------------------|--:|-----------:|---------:|----------:|------:|------------------:|
| Injection (15)     | 15 | 1 755      | 30.7 %   | 99.0 %    | 1.3 % | 8.6 %             |
| User study (25)    | 25 | 2 925      | 41.9 %   | 96.5 %    | 1.0 % | 1.8 %             |
| Agent (20)         | 20 | 2 340      | 72.4 %   | 96.3 %    | 3.0 % | 20.8 %            |

**Read.** Top-1 stability ≥ 96.3 % on every rankable subset. The τ = 1.0 share
drops sharply versus the previously-reported corpus-wide figures because the
rankable filter removes the mechanically-saturated ≤ 1-DP sessions; what remains
is genuine perturbation response. User-study set carries the headline because
it has the largest rankable subset (25 of 30) and the longest per-session
rankings; injection (15) and agent (20) are smaller-N supporting evidence.

### Table 5.2 — Per-knob top-1 disruption (user-study set)

Single-knob sweep, 9 perturbation factors × 30 sessions = 270 rows per knob.
Disrupting a knob means top-1 ranking changed under that perturbation.

| Knob       | Top-1 disrupted | Share of 270 |
|------------|----------------:|-------------:|
| gain       | **24**          | **8.9 %**    |
| manualIde  | 17              | 6.3 %        |
| skipTests  | 15              | 5.6 %        |
| commitGap  | 15              | 5.6 %        |
| length     | 14              | 5.2 %        |
| broken     | 12              | 4.4 %        |
| coupling   | 2               | 0.7 %        |
| lag        | 1               | 0.4 %        |
| smells     | 1               | 0.4 %        |
| cohesion   | 1               | 0.4 %        |
| cognitive  | 0               | 0.0 %        |
| duplication| 0               | 0.0 %        |
| readability| 0               | 0.0 %        |

**Read.** `gain` is the most leverage-y knob in the formula on this corpus. Six
of the seven process-quality terms (gain, manualIde, skipTests, commitGap,
length, broken) show measurable disruption between 4.4 % and 8.9 %; `lag` is the
outlier at just 0.4 %, consistent with the cross-set LOO finding that lag is a
magnitude contributor more than a rank-shaper on the user-study set. The six
cleanliness sub-terms contribute almost nothing under independent single-knob
perturbation, consistent with the §3.5 design that they enter the score only
through the cleanliness scalar `C` and its in-arc gain.

### Table 5.3 — Multi-knob Monte Carlo

Every weight scaled by an independent log-normal `exp(σZ)`, σ = ln 2, 200 samples
per fixture, fixed seed.

| Set (rankable)     | Samples | Mean τ | top-1 = 1 | top-3 = 1 | top-5 = 1 |
|--------------------|--------:|-------:|----------:|----------:|----------:|
| Injection (15)     | 3 000   | 0.385  | 95.7 %    | 32.9 %    | 33.3 %    |
| User study (25)    | 5 000   | 0.586  | 84.6 %    | 61.7 %    | 59.5 %    |
| Agent (20)         | 4 000   | 0.645  | 82.5 %    | 78.3 %    | 75.0 %    |

**Read.** The user-study top-1 stability holds at 84.6 % under joint multi-knob
perturbation; mean τ is 0.586 — moderate absolute correlation. The injection
top-3/top-5 drop sharply because injection rankings have at most 5 items, so a
single tail flip propagates through top-3 and top-5 simultaneously.

### Tables 5.4 – 5.6 — Ablation (injection set)

Monotonicity of mean τ and sum-over-sum recovery as active-term count grows from
0 (all seven process weights zeroed) to 7 (full process score):

| Active terms | Mean τ | Sum-over-sum |
|-------------:|-------:|-------------:|
| 0            | 0.744  | **0.000**    |
| 1            | 0.743  | 0.170        |
| 2            | 0.746  | 0.327        |
| 3            | 0.754  | 0.473        |
| 4            | 0.765  | 0.612        |
| 5            | 0.782  | 0.745        |
| 6            | 0.805  | 0.874        |
| 7            | 0.833  | **1.000**    |

Solo recovery (each term active alone, the other six zeroed):

| Term       | Mean τ | Recovery | Sum-over-sum |
|------------|-------:|---------:|-------------:|
| length     | 0.744  | 0.578    | **0.328**    |
| broken     | 0.789  | 0.553    | 0.302        |
| manualIde  | 0.744  | 0.442    | 0.261        |
| lag        | 0.669  | 0.423    | 0.132        |
| skipTests  | 0.718  | 0.398    | 0.089        |
| commitGap  | 0.796  | 0.368    | 0.080        |
| gain       | 0.744  | 0.311    | 0.000        |

Leave-one-out (all seven terms active except `removed`):

| Removed   | Injection τ | User-study τ |
|-----------|------------:|-------------:|
| length    | 0.833       | **0.606**    |
| manualIde | 0.833       | 0.657        |
| commitGap | 0.791       | 0.713        |
| skipTests | 0.815       | 0.719        |
| gain      | 0.826       | 0.736        |
| broken    | 0.731       | 0.689        |
| lag       | 0.803       | 0.820        |

**Read.**
- Sum-over-sum recovery is strictly monotonic across all eight active-term
  counts and drops cleanly to 0.000 when every process term is zeroed: every
  term does real work, including the lag term.
- `length` is the strongest single-term recoverer on the injection set
  (sum/sum 0.328), with `broken` (0.302) and `manualIde` (0.261) close behind.
  `lag` is fourth at 0.132 — measurable single-term contribution without
  dominating any existing term.
- Removing `gain` inflates sum/sum to 1.060: `gain` regularises against the
  penalty terms rather than acting as a positive single-term contributor.
- The cross-set leave-one-out picks out `length` as the term whose removal
  costs the most user-study τ (0.833 → 0.606). The §5.1.2 generalisation
  paragraph should be written around `length` as the load-bearing process
  term across both populations. The lag term has the smallest user-study
  impact of the seven (τ = 0.820 on removal) but a measurable injection-set
  impact (τ = 0.803), so its role is magnitude-contributor on user-study and
  magnitude-plus-rank-shaper on injection.

The ablation sweep now covers all seven process-side terms (gain, broken,
skipTests, manualIde, length, commitGap, lag). This matches the methodology
chapter's "seven process-side weights" framing and removes the residual
all-stripped magnitude that lag's earlier hold-at-production setting carried.

---

## §5.2 Detector tests (45-session injection set)

| Table                       | Result                                                                      |
|-----------------------------|-----------------------------------------------------------------------------|
| 5.8 Cohen's κ               | ORD 1.000 / IDE 1.000 / REW 0.861 / HYG 0.723 – 0.862                       |
| 5.9 precision / recall      | Precision 1.00 every kind; recall ORD 0.368, IDE 0.762, REW 1.00, HYG 1.00  |
| 5.10 beats-user fraction    | DPs 24 / 16 / 13 / 13; beats 10 / 12 / 6 / 13; 41.7 % / 75.0 % / 46.2 % / 100 % |
| 5.11 prominence (rank 1)    | caught 1 / 12 / 5 / 8; mean rank 1.00 / 1.00 / 1.00 / 1.13                  |

The detector validation rests entirely on the controlled injection set and is
unaffected by the user / agent corpora. The κ shows substantial-to-perfect
agreement on the kinds the playbook directly injects. Precision is perfect on
every kind: when the detector fires, it always lines up with at least one
expected injection. Recall is high for the kinds whose detection signature is
deterministic (REW 1.00, HYG 1.00, IDE 0.76) and lower for ORDERING (0.37),
which depends on enumerating refactor permutations and is the detector with the
weakest synthesis at present.

---

## §5.3 User study

### Per-session divergence-point distribution (Table 5.12)

Summary by arm:

| Arm                                 | Sessions | Total DPs | DP / session |
|-------------------------------------|---------:|----------:|-------------:|
| with-feedback (p1, p2, p3)  | 18       | 49        | 2.7          |
| baseline (alex, vlad)               | 12       | 72        | **6.0**      |

The full 30-row table is in §Appendix A. The per-kind shares are comparable
across arms (IDE_REPLAY dominates, HYGIENE next, ORDERING and REWORK sparse),
but **baseline participants generate ~2.2× the DP rate per session**.

### Per-participant trajectory (Table 5.13)

Final-checkpoint gain-stripped J at each session:

| Participant         | Arm           | S1 | S2 | S3 | S4 | S5 | S6 | ΔJ  | slope  |
|---------------------|---------------|---:|---:|---:|---:|---:|---:|----:|-------:|
| p1 (P1)           | with-feedback | 25 | 22 | 41 | 38 | 34 | 48 | +23 | +4.60  |
| p2 (P2)          | with-feedback | 16 | 13 | 29 | 39 | 37 | 47 | +31 | +6.20  |
| p3 (P3)          | with-feedback | 39 | 15 | 42 | 42 | 31 | 48 | +9  | +1.80  |
| p4-baseline (P4)  | baseline      | 22 | 17 |  8 | 22 | 12 | 15 | **−7** | −1.40 |
| p5-baseline (P5)  | baseline      | 23 | 17 |  9 | 36 | 11 | 20 | **−3** | −0.60 |

**Notes on shape.**
- S2 dips for most participants. S2 in the playbook is a refactor-heavy task
  where intermediate cleanliness drops by design; the dip is a property of the
  task, not the participant.
- p3 (P3) starts at J = 39 — much closer to ceiling than the other
  with-feedback participants — so the slope is naturally smaller. The arc is
  still strictly non-decreasing on slope sign.

### §5.3.3 With-feedback vs baseline arm

| Arm                          | Mean S1 J | Mean S6 J | Mean ΔJ | Mean slope |
|------------------------------|----------:|----------:|--------:|-----------:|
| with-feedback (n = 3)        | 26.7      | **47.7**  | **+21.0** | **+4.20** |
| baseline (n = 2)             | 22.5      | **17.5**  | **−5.0**  | **−1.00** |
| Cross-arm Δ of Δ             |           |           | **+26.0** | **+5.20** |

Per-session DP totals by arm:

| Session  | with-feedback (Σ over 3 ppts) | baseline (Σ over 2 ppts) |
|----------|------------------------------:|-------------------------:|
| S1       | 12                            | 10                       |
| S2       | 17                            | 15                       |
| S3       | 11                            | 19                       |
| S4       | 0                             | 0                        |
| S5       | 7                             | 13                       |
| S6       | **2**                         | **15**                   |
| ΔS6 − S1 | **−10**                       | **+5**                   |

**Read.** Both arms start at roughly the same total-DP rate (12 vs 10) but
diverge by S6: the feedback arm has driven divergence points down to noise (2),
while the baseline arm has stayed flat-to-worsening (15). Gain-stripped J climbs
at +4.2 / session under feedback vs −1.0 / session under baseline — a 5.2-point
per-session gap that compounds across the arc.

**Caveat.** n = 3 vs n = 2 does not support a hypothesis test; the direction is
the publishable claim, not a *p*-value. With per-participant variance
comparable to the cross-arm Δ, this is honest descriptive evidence, not
statistical inference.

### Anomalies worth a sentence in the rewrite

- **All five participants score 0 DPs at S4.** S4 is the playbook's no-op
  session by design — it tests the reset gate. The detector correctly says so
  regardless of arm.

---

## §5.4 Agent extension experiment

**Framing.** This section is an extension experiment for a human-focused tool,
not a second contribution. The tool was not designed to monitor agents. Three of
its detectors are structurally biased against agent edit patterns:

- **No IDE-driven refactorings.** Agents apply file edits via tool calls, not via
  IntelliJ's refactor menu. The IDE_REPLAY kind, which fires when a sequence of
  manual edits could have been replaced by a single IDE refactor invocation, is
  structurally moot for agents — there is no manual-vs-IDE choice to flag.
- **Commit-gap threshold rarely triggered.** The `commitGap` detector flags six
  refactor checkpoints elapsed without a commit. Agents batch many file changes
  per checkpoint and arrive at S6 in fewer total checkpoints than a human
  session of equivalent length. In a 6-session arc the threshold is rarely
  reached.
- **Edit-burst debouncer collapses each agent turn into one burst.** The
  edit-burst tracker debounces on `DEBOUNCE_MS = 2000L` (2 s; see
  `tool/ide-plugin/src/main/kotlin/com/github/ethanhosier/ideplugin/services/EditBurstTracker.kt:189`).
  It does fire on tool-applied file writes. But agents emit many file edits in a
  tight burst, then pause for reasoning longer than 2 s; each tool turn
  therefore collapses to one burst event, compressing the burst-vs-checkpoint
  signal that several divergence-point kinds depend on.

Plus a *floor effect*: agents start at much higher baseline discipline than
humans (mean S1 gain-stripped J ≈ 33 for agents vs ≈ 22 for human baselines;
agent baseline DP rate ≈ 0.5 / cell-session vs 6.0 for human baselines), so
there is materially less room for any intervention to move the score.

None of these are tool bugs — the detectors do the right thing on human traces.
But together they imply the agent results should be read as descriptive
feasibility evidence, not as a verdict on the tool's intended use.

### §5.4.1 Per-cell descriptive arc

Gain-stripped J at S1..S6 for every cell:

| # | Cell                                          | Arm           | S1 | S2 | S3 | S4 | S5 | S6 | ΔJ  | slope | Σ DP |
|---|-----------------------------------------------|---------------|---:|---:|---:|---:|---:|---:|----:|------:|-----:|
| 1 | claude-2-opus-4.7-claude-code                 | with-feedback | 35 | 39 | 40 | 45 | 40 | 35 |  0  |  0.00 |   5  |
| 2 | claude-opus-4.7-medium-opencode               | with-feedback | 37 | 36 | 40 | 45 | 37 | 38 | +1  | +0.20 |   4  |
| 3 | claude-opus-4.7-thinking-medium-cursor-cli    | with-feedback | 35 | 39 | 40 | 45 | 39 | 39 | +4  | +0.80 |   6  |
| 4 | gemini-3.5-flash-medium-opencode              | with-feedback | 33 | 37 | 40 | 45 | 39 | 37 | +4  | +0.80 |   9  |
| 5 | gpt-5.5-medium-cursor-cli                     | with-feedback | 23 | 38 | 39 | 45 | 37 | 37 | **+14** | +2.80 |   7  |
| 6 | openai-gpt-5.5-medium-opencode                | with-feedback | 35 | 38 | 40 | 45 | 36 | 36 | +1  | +0.20 |   5  |
| 7 | claude-4.7-opus-medium-claude-code-baseline   | **baseline**  | 36 | 34 | 39 | 45 | 36 | 40 | +4  | +0.80 |   3  |
| 8 | gpt-5.5-medium-opencode-baseline              | **baseline**  | 25 | 34 | 40 | 45 | 36 | 38 | **+13** | +2.60 |   4  |

**Cross-stack observations** (descriptive, single-cell per axis cell so no
inferential weight):

- **Model axis** (OpenCode harness held out): Claude (#2), Gemini (#4), GPT (#6)
  → slopes 0.20 / 0.80 / 0.20, DPs 4 / 9 / 5. Gemini surfaces more DPs but
  climbs J the fastest, consistent with willingness to commit mid-task partial
  work that surfaces as ORDERING / IDE_REPLAY but recovers by S6.
- **Harness axis** (Claude model held out): Claude Code (#1), OpenCode (#2),
  Cursor CLI (#3) → slopes 0.00 / 0.20 / 0.80. Cursor improves fastest at fixed
  model.
- **Cross-stack** (#1 vs #6): Claude / Claude Code (slope 0.00) vs GPT / OpenCode
  (slope 0.20). Roughly tied.

### §5.4.2 With-feedback vs baseline arm

| Arm                       | Mean S1 J | Mean S6 J | Mean ΔJ | Mean slope |
|---------------------------|----------:|----------:|--------:|-----------:|
| with-feedback (n = 6)     | 33.0      | 37.0      | **+4.0** | **+0.80** |
| baseline (n = 2)          | 30.5      | 39.0      | **+8.5** | **+1.70** |
| Cross-arm Δ of Δ          |           |           | **−4.5** | **−0.90** |

DP totals by arm:

| Session  | with-feedback (Σ over 6 cells) | baseline (Σ over 2 cells) |
|----------|-------------------------------:|--------------------------:|
| S1       | 7                              | 1                         |
| S2       | 0                              | 0                         |
| S3       | 10                             | 2                         |
| S4       | 0                              | 0                         |
| S5       | 9                              | 2                         |
| S6       | 10                             | 2                         |
| ΔS6 − S1 | +3                             | +1                        |

**Read.** Under this protocol, the agent baseline does **not** underperform the
feedback arm — it slightly outperforms it on gain-stripped J (+8.5 vs +4.0) and
produces roughly half the DP rate. This is consistent with the structural bias
list above and the floor effect: agents in both arms behave very similarly
because the tool's detectors don't fire on much of what they do, and the room
between their starting J and the ceiling is small either way.

The intent here is **not** to claim the tool's feedback hurts agents — both
slopes are positive, and the n = 2 baseline cells include one cell
(`gpt-5.5-medium-opencode-baseline`) that starts at an anomalously low S1 = 25
and skews the arm mean. The honest framing is: **on this corpus the tool's
feedback shows no measurable causal effect on agent process scores**, and the
construct-validity threats above explain why the experiment is not the right
test for that claim.

### §5.4.3 Feedback engagement varies by model family

Skimming the per-cell `transcript.md` (the full session-1..6 conversation log)
shows a sharp split:

- **Claude family (cells 1, 2, 3) explicitly engaged with the feedback.** Cell 1
  is the strongest case: after S1 warnings about `BUILD_OFTEN_BROKEN`,
  `TEST_REGRESSIONS`, and `PROCESS_SCORE_DEGRADED`, S2 opens with "I'll use the
  atomic-edit-per-file approach … batched each file's constant introductions
  atomically … then ran tests once green before committing." After S3 flagged
  `REFACTORING_INTRODUCED_SMELLS`, S4 planning included "when extracting a
  method, leave the extracted body cleaner … extract and tidy, not extract and
  stop." Cell 2 follows the same pattern: after S1, "BUILD_OFTEN_BROKEN …
  likely caused by intermediate states … I'll land each rename atomically per
  checkpoint." Cell 3 shows quieter engagement but visible adjustment after the
  S5 IDE_REPLAY summary.
- **Non-Claude cells (4 Gemini, 5 GPT-5.5 cursor, 6 GPT-5.5 opencode) showed no
  visible engagement.** They proceeded with consistent (and competent) behaviour
  session-to-session but did not acknowledge or react to the between-session
  feedback in their conversation traces.

This split is interesting on its own — it suggests differential treatment of
reflective critique across model families — but it does **not** rescue a causal
claim. Even on the Claude cells, the engagement does not produce a ΔJ
distinguishable from the baseline arm. Report this honestly: feedback-receiving
is observable in the transcripts; feedback-driven score improvement is not.

### §5.4.4 Qualitative threads from cell 1 (`claude-2-opus-4.7-claude-code`)

The original qualitative threads (commit cadence, smell self-diagnosis,
IDE-replay limitation) are observations about cell 1 specifically. The rewrite
should identify them as cell-1 anecdotes, not as "the agent." They illustrate
what feedback engagement looks like in transcript form, which is itself useful;
they do not generalise across the eight-cell agent extension.

Every agent cell scores J = 45 at S4 — a uniform-across-cells corroboration of
the user-study finding that S4 is genuinely a no-event session under the
playbook.

---

## What this means for the thesis prose

A short punch-list for the Chapter 5 rewrite (the full structural plan is in
`tool/PLAN-corpora-expansion.md`):

- **Framing.** State the thesis goal as a tool for evaluating **human** refactoring
  trajectories. Demote agents to an extension experiment.
- **§5.1.1.** Build the per-knob disruption narrative around `gain` (8.9 %).
- **§5.1.2.** Lead with `length` as the load-bearing process term across both
  populations (cross-set LOO τ 0.833 → 0.606). The ablation sweep now includes
  all seven process-side terms (lag added back), so the active-count table is
  0..7 and the all-stripped row is sum/sum 0.000.
- **§5.1.3.** Report user-study mean τ 0.586 honestly on the rankable subset; lead with the top-1 84.6 % stability number.
- **§5.2.** No changes.
- **§5.3.1, §5.3.2.** Five-participant per-kind and trajectory tables.
- **§5.3.3 (new).** With-feedback vs baseline comparison; honest n = 3 vs n = 2
  caveat; this is the headline finding of the corpus expansion.
- **§5.4.** Reframe as extension experiment. Drop causal language. Add the
  detector-bias and floor-effect limitations. Add the Claude-vs-non-Claude
  engagement split. Demote cell-1 threads to cell-1 anecdotes.
- **Abstract / §6 / §7.2.** Update participant counts; lead with the human
  feedback finding; downgrade the agent claim to "extension experiment shows
  descriptive arcs but no causal feedback effect on this corpus, consistent with
  the detectors' human-edit-pattern scope."

---

## Appendix A — Full per-session table

User study (30 sessions):

```
session              ORD IDE REW HYG  Σ DP  J_prod  J_gain0
p1-01                1   5   0   1    7      25      25
p1-02                0   2   0   3    5      72      22
p1-03                0   4   2   0    6      24      41
p1-04                0   0   0   0    0      88      38
p1-05                0   1   0   1    2      50      34
p1-06                1   0   0   0    1      54      48
p2-01               0   3   0   2    5      16      16
p2-02               0   2   0   4    6      63      13
p2-03               1   1   0   2    4      33      29
p2-04               0   0   0   0    0      89      39
p2-05               0   2   0   0    2      37      37
p2-06               0   1   0   0    1      38      47
p3-01               0   0   0   0    0       0      39
p3-02               0   2   0   4    6      65      15
p3-03               0   1   0   0    1      42      42
p3-04               0   0   0   0    0      92      42
p3-05               0   2   0   1    3       9      31
p3-06               0   0   0   0    0      53      48
p4-baseline-01       0   0   0   0    0       0      22
p4-baseline-02       0   3   1   5    9      26      17
p4-baseline-03       0   8   1   4   13      10       8
p4-baseline-04       0   0   0   0    0      72      22
p4-baseline-05       0   5   0   4    9      41      12
p4-baseline-06       0   5   1   3    9      27      15
p5-baseline-01       1   5   0   4   10      23      23
p5-baseline-02       0   0   0   6    6      14      17
p5-baseline-03       0   3   0   3    6      21       9
p5-baseline-04       0   0   0   0    0      81      36
p5-baseline-05       0   2   0   2    4      48      11
p5-baseline-06       1   3   0   2    6      27      20
```

Agent extension (48 sessions): in `tool/fixtures/notebooks/experiments.executed.ipynb`
under the `### AGENT ###` TSV dump.

---

## Appendix B — Reproducibility

1. `cd tool && python3 fixtures/notebooks/build_notebook.py` regenerates the
   notebook from the canonical Python source.
2. `JAVA_HOME=<JDK-25-home> jupyter-nbconvert --to notebook --execute fixtures/notebooks/experiments.ipynb --output experiments.executed.ipynb --ExecutePreprocessor.kernel_name=kotlin --ExecutePreprocessor.allow_errors=True`
   runs all cells against the full corpus. Numbers in this report come from this
   executed notebook plus the appended TSV-dump cell.
3. Three cells still error on hard-coded P1 / P2 / Agent ID regexes (`p1Idx`,
   `p2Idx`, `agIdx`); the per-participant aggregator rewrite is tracked in
   `tool/PLAN-corpora-expansion.md` §B2. None of the numerics in this report
   depend on those cells.
