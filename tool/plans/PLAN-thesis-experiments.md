# Plan: Experiments + Results chapter pass

## Context

The Experiments + Results chapter is Chapter 5 of the thesis, item
#5 in `PLAN-thesis-writeup.md`. It is the chapter that lands the
three named empirical claims of the thesis: (i) the process-score
ranking is robust to plausible weight perturbations, (ii) the
score formula's six process-side terms are non-redundant, and
(iii) the divergence-point detector recovers ground-truth
injections at strong precision/recall with one well-localised
empirical limitation (the commuting-reorder finding).

The chapter ports near-verbatim from three `explained_results/*.md`
files that were re-generated under the V2 magnitude semantic
(trajectory-final $J$ delta, uniform across all four divergence
kinds). The V2 semantic is the current production semantic and the
methodology chapter (`final_report/methodology/methodology.tex`)
defines magnitude in those terms; **every number reported in this
chapter must be the V2 number**, never the legacy V1 number that
remains documented as a footnote in the source files.

The chapter's headline finding — *"reordering rarely helps"* — is
the strongest standalone empirical observation in the thesis. The
chapter ends by landing it explicitly, alongside a scoped-out
fixes section that frames the two known precision/recall gaps as
deliberate deferrals rather than oversights.

The user study (`PLAN-user-study.md`) is in flight. One paragraph
in this chapter is study-blocked: the expanded-corpus paragraph
at the end of the divergence subsection. A `% TODO study-blocked`
comment marks the slot; the rest of the chapter ships without it.

**Page budget: 11 target, 14 hard cap.** Lower-bound is set by the
three subsections each earning ~2.5 pages of dense numbers plus a
~1-page headline-claim ending plus ~½ page of chapter
introduction. Upper-bound matches the methodology chapter's cap
and respects the master-plan rhythm (12/14, 8/10, 11/14).

Sources are all in place:

- **Primary source files (port near-verbatim):**
  - `analysis/src/main/kotlin/com/github/ethanhosier/analysis/experiment/explained_results/sensitivity.md`
  - `analysis/src/main/kotlin/com/github/ethanhosier/analysis/experiment/explained_results/ablation.md`
  - `analysis/src/main/kotlin/com/github/ethanhosier/analysis/experiment/explained_results/divergence.md`
- **Supporting source:**
  - `analysis/src/main/kotlin/com/github/ethanhosier/analysis/experiment/explained_results/plugin-misclassifications.md`
    (per-session catalogue cited from the IDE_REPLAY FP discussion).
- **Forward-ref anchors:** `final_report/methodology/methodology.tex`
  has nine forward references to Chapter 5 that this chapter must
  resolve via the labels listed under "Critical files" below.
- **Master plan:** `PLAN-thesis-writeup.md` item #5 sets the scope.
- **Reviewer-style guidance:** `HONEST_REVIEW_2.md` lines 335–342
  set the closing structure (reordering-rarely-helps headline +
  scoped-out fixes).

**Pre-step:** create `final_report/results/results.tex` and wire it
into `main.tex` immediately after the architecture chapter input.
The methodology chapter already references `Chapter~5` in nine
places via the unresolved string; this chapter must define the
labels `ch:results`, `sec:results-sensitivity`,
`sec:results-ablation`, `sec:results-divergence`,
`sec:results-headline`, `sec:results-scoped-out` so those
forward-refs resolve cleanly.

## Chapter outline (with page budget)

| §   | Section                                                        | Pages |
|-----|----------------------------------------------------------------|------:|
| 5.0 | Chapter introduction (3-experiment-defence framing)            | 0.5   |
| 5.1 | Sensitivity sweep (τ robustness across weight perturbations)   | 2.5   |
| 5.2 | Ablation sweep (power-set non-redundancy + per-term roles)     | 2.5   |
| 5.3 | Divergence experiment (precision/recall/quality vs. ground truth) | 3.5   |
| 5.4 | Headline finding: reordering rarely helps on commuting windows | 1     |
| 5.5 | Scoped-out fixes (ORDERING gate + IDE_REPLAY plugin capture, CLOSED) | 1 |
| **Total** |                                                          | **~11** |

A 14-page hard cap leaves ~3 pages of contingency, used in this
order if needed: (i) expanded ground-truth discussion in 5.3,
(ii) the expanded-corpus paragraph when the user study lands,
(iii) additional figure space.

## Per-section detail

### §5.0 Chapter introduction (0.5 page)

Opens with the chapter's tripartite-defence framing taken from the
`ablation.md` introduction: sensitivity defends robustness to
*plausible* weight choices; ablation defends *non-redundancy* of
the terms; divergence defends *external validity* against ground
truth. State that **all three defences must hold simultaneously**
for the score formula to be credible: a formula that survives
sensitivity but not ablation is decorative, a formula that survives
ablation but not sensitivity is over-tuned, and a formula that
passes both internal defences but fails against ground truth is
mathematically clean but empirically meaningless. This chapter
shows all three hold.

The intro also sets two pieces of running scaffolding the rest of
the chapter relies on:

- **The corpus.** 45 hand-recorded sessions in `fixtures/sessions/`,
  labelled against `manifest-v2.csv` with multi-label
  `expected_kinds`. State the corpus size honestly: the chapter
  reports raw counts alongside percentages throughout, because
  per-cell counts are small.
- **The V2 magnitude semantic.** A one-sentence reminder linking
  back to methodology §3.5: magnitude is the trajectory-final $J$
  delta uniformly across all four kinds. Every number in this
  chapter is reported under V2.

Forward-reference to §5.4 (headline finding) and §5.5 (scoped-out
fixes) in the closing sentence of this intro, so the reader knows
the chapter ends with an explicit landing rather than petering out.

### §5.1 Sensitivity sweep (2.5 pages)

The opening claim sentence: *"Across 4,860 single-knob
perturbations spanning ×0.1 to ×10, the divergence-point ranking is
preserved (Kendall τ-b = 1.0) on 99.1 % of perturbations, and the
top-5 hit rate is 1.0 on 100 %."*

Subsection structure (4 paragraphs + 2 tables + 1 figure):

1. **What the experiment asks and how it runs.** ~⅓ page. Port
   the *"How it runs"* block from `sensitivity.md` near-verbatim —
   the 12-knob × 9-factor × 45-fixture design (4,860 rows; ~2.6 s
   wall-clock) is concrete enough to earn its space. State that
   τ-b and top-5 hit rate are the two ranking-stability measures
   reported, with τ-b being the more brittle one on small DP
   counts (see caveats).

2. **τ distribution table** (Table 5.1). Port from `sensitivity.md`:
   τ = 1.00 on 4818 rows (99.1 %), τ ∈ [0.25, 1.00) on 22 rows
   (0.5 %), τ ∈ [0, 0.25) on 2 rows (0.0 %), τ < 0 on 18 rows
   (0.4 %). Top-5 hit rate = 1.0 on every single row. Caption
   should land that **the user-facing top of the ranking is fully
   robust**, which is the practical claim the dashboard relies on.

3. **Histogram figure** (Figure 5.1). Density histogram of τ-b
   across the 4,860 perturbations, log-scaled y-axis, with vertical
   lines at τ = 1.0 (the production mass) and τ = 0 (the
   ranking-collapse threshold). The mass at τ = 1.0 should
   dominate visually; the leftmost tail (τ < 0) should be visible
   but small. Generated from `/tmp/sensitivity-results.csv`.

4. **Which knobs reshuffle when pushed hard.** ~½ page + Table 5.2.
   Port from `sensitivity.md`: the 8 knob × factor combinations
   with mean τ < 0.97. State the two patterns explicitly:
   (i) extreme factors dominate — every entry lives at ×4, ×10, or
   ×0.1; (ii) process knobs reshuffle 10× more often than
   cleanliness knobs (38/2430 process rows vs. 4/2430 cleanliness
   rows). The cleanliness-doesn't-matter finding is its own claim:
   *"the divergence-point ranking is driven by process-score
   alt − user deltas rather than absolute cleanliness levels."*

5. **Saturation check.** ~⅓ page. Port the saturation paragraph
   from `sensitivity.md`: 15.6 % of rows have at least one
   saturated DP; 12.3 % of all baseline DPs (972 / 7884) are
   saturated; concentrated in 7 fixtures (003, 004, 006, 008, 009,
   026 all-DP-saturated + 044 partially). Land the headline claim:
   *"even removing saturated rows from the analysis, the remaining
   84.4 % of rows still show ~99 % τ = 1.0; the robustness story
   is not an artefact of clipping."*

6. **Caveats** (~⅓ page). Port the four caveats from
   `sensitivity.md` near-verbatim:
   - Single-knob perturbations only; interaction effects are out
     of scope (covered by §5.2 ablation for the cumulative-zero
     case).
   - τ-b on small DP counts is brittle; top-5 hit rate is the
     more stable surface signal.
   - HYGIENE COMMIT_GAP DPs have a discrete magnitude exactly
     equal to $W_{cg}$; perturbing $W_{cg}$ moves them by a fixed
     proportion under V2.
   - REWORK magnitudes are now trajectory-final process-score
     deltas under V2 (not the V1 line-count), so REWORK DPs
     respond to weight changes. The τ stability is a property of
     the score formula, not of a weight-independent magnitude.

7. **Closing claim sentence** (verbatim from `sensitivity.md`):
   the "What I claim in the chapter" block.

**Topic-sentence intent for each paragraph above:** every paragraph
opens with the empirical result it establishes, not with
"This section reports...". The reader skimming first sentences
should be able to lift the chapter's full story.

### §5.2 Ablation sweep (2.5 pages)

The opening claim sentence: *"Full power-set ablation across the
six process-score weights (2^6 = 64 variants per fixture, 2,880
rows total) shows that no single term dominates the
divergence-point result, and every term has a measurable empirical
role."*

Subsection structure (4 paragraphs + 3 tables + 1 figure):

1. **What the experiment asks and how it runs.** ~⅓ page. Port
   the design rationale block from `ablation.md`: the
   sensitivity-holds + ablation-breaks defensible position; the
   power-set scope decision (process side only, cleanliness side
   skipped because §5.1 already shows cleanliness is near-inert).
   2,880 rows; ~1.7 s wall-clock.

2. **Sanity row + monotonic-by-active-count table** (Table 5.3).
   Port from `ablation.md`: sanity row passes (the 6-term variant
   reproduces production). Then the monotone-by-active-count
   table:

   | active terms | mean τ | mean recovery | sum/sum | n   |
   |-------------:|-------:|--------------:|--------:|----:|
   | 0            | 0.830  | 0.333         | **0.000** | 45  |
   | 1            | 0.863  | 0.476         | 0.211   | 270 |
   | 2            | 0.892  | 0.597         | 0.400   | 675 |
   | 3            | 0.919  | 0.706         | 0.570   | 900 |
   | 4            | 0.945  | 0.808         | 0.726   | 675 |
   | 5            | 0.972  | 0.907         | 0.869   | 270 |
   | 6 (full)     | 1.000  | 1.000         | 1.000   | 45  |

   Headline claim: **monotone rise in both τ and sum/sum
   recovery; no discontinuities**. The sum/sum column is the
   honest "fraction of total corpus magnitude that survives"
   measure (avoids the 0/0 = 1.0 inflation that mean-of-fractions
   has on no-DP fixtures); the mean-recovery column is shown
   alongside for continuity with earlier reports but explicitly
   flagged as the weaker measure.

3. **Per-term single-knob table** (Table 5.4). Port from
   `ablation.md`:

   | term       | mean τ | mean recovery | sum/sum |
   |------------|-------:|--------------:|--------:|
   | `manualIde` | 0.830 | 0.457 | **0.357** |
   | `broken`    | 0.896 | 0.615 | 0.348     |
   | `length`    | 0.830 | 0.632 | 0.342     |
   | `commitGap` | 0.904 | 0.387 | 0.126     |
   | `skipTests` | 0.889 | 0.431 | 0.093     |
   | `gain`      | 0.830 | 0.333 | **0.000** |

   Prose covers: (i) `manualIde`, `broken`, and `length` cluster
   as strongest solo contributors (sum/sum ≈ 0.34–0.36 each);
   (ii) `gain` alone recovers 0.000 because $\Delta C \approx 0$
   on most fixtures (user and alt reach the same end state), so
   the term has nothing to multiply on its own — visible in
   combination but solo-empty; (iii) the gap between `commitGap`
   (0.126) and `manualIde` (0.357) shows recovery scales with
   weight × signal variability, not weight alone. This is the
   "Δ recovery (solo)" column previewed in methodology §3.3
   Table 3.1 — explicit back-reference here so the
   methodology-to-results loop is closed.

4. **Per-term leave-one-out table** (Table 5.5). Port from
   `ablation.md`:

   | removed term | mean τ | mean recovery | sum/sum |
   |--------------|-------:|--------------:|--------:|
   | `length`     | 1.000  | 0.814         | 0.736   |
   | `manualIde`  | 1.000  | 0.887         | 0.772   |
   | `broken`     | 0.933  | 0.945         | 0.826   |
   | `skipTests`  | 0.970  | 0.898         | 0.901   |
   | `commitGap`  | 0.926  | 0.952         | 0.892   |
   | `gain`       | 1.000  | 0.944         | **1.090** |

   Prose covers the three observations from `ablation.md`:
   (i) removing `length` causes the largest sum/sum drop (W_L is
   the dominant magnitude contributor); (ii) removing `gain` causes
   sum/sum to exceed 1.0 because `gain · ΔC` is a small *positive*
   term for alts that regularises against the penalty terms —
   remove it and the penalties operate unchecked, producing larger
   absolute magnitudes. This is the chapter's "gain as
   regulariser" finding. Flag it honestly; (iii) removing `broken`,
   `skipTests`, or `commitGap` reshuffles ranking (τ ≤ 0.97) but
   barely moves sum/sum — these terms primarily affect *which* DP
   is biggest, not *how* big.

5. **Power-set heatmap figure** (Figure 5.2). 64 × 1 sum/sum
   recovery heatmap, rows ordered by active-term count then
   lexicographic on the 6-bit active mask. Colour scale: 0
   (white) to 1.09 (deep blue, with the `gain`-removed row
   visibly above 1.0). Generated from
   `/tmp/ablation-results.csv`. Caption lands the monotonicity
   claim visually.

6. **Caveats** (~⅓ page). Port the five caveats from
   `ablation.md`:
   - Cleanliness sub-weights not ablated (would extend 64→4096
     for negligible new information per §5.1).
   - Saturation impact on ablation is low (mean
     `perturbed_saturated_dp_count` ≈ 0.20–0.29 across variants).
   - τ-b on small DP counts is brittle; many fixtures have 1–2
     DPs and τ is structurally 1.0 on those.
   - `gain` alone equals empty baseline in sum/sum (both 0.000)
     — honest disclosure, defended by the regulariser-role
     finding above.
   - V2 magnitude note: under V1 the active_count = 0 floor was
     0.119 (REWORK line-count survived); under V2 it collapses
     to 0.000.

7. **Closing claim sentence** (verbatim from `ablation.md`):
   the "What I claim in the chapter" block.

### §5.3 Divergence experiment (3.5 pages)

The opening claim sentence: *"Against 45 recorded sessions with
hand-labelled ground truth, the divergence-point detector achieves
perfect precision on all four kinds (REWORK 1.00, HYGIENE 1.00,
ORDERING 1.00, IDE_REPLAY 1.00), and ranks every caught injection
at top-1 in its session."* (The IDE_REPLAY precision figure
previously sat at 0.80 with four plugin-instrumentation false
positives; this gap was closed in PR #62 by extending the plugin's
`RefactoringCommandListener` to synthesise envelopes around
IntelliJ commands the platform `RefactoringEventListener` is
silent for. See §5.5 for the deferred-fixes history.)

Subsection structure (6 paragraphs + 4 tables + 1 figure):

1. **What the experiment asks and how it runs.** ~⅓ page. Port
   from `divergence.md`. The chapter sets up that divergence is
   the headline experiment — sensitivity defends internal
   robustness, ablation defends structural contribution,
   divergence defends external validity. 45 sessions; 54
   columns per row; ~5 s wall-clock.

2. **Part 1 — Detection accuracy table** (Table 5.6). Port
   verbatim:

   | kind        | precision | recall (injection only) | recall (any expected) |
   |-------------|----------:|------------------------:|----------------------:|
   | ORDERING    | 1.00      | 0.40                    | 0.36                  |
   | IDE_REPLAY  | 1.00      | 0.76                    | 0.76                  |
   | REWORK      | 1.00      | 1.00                    | 1.00                  |
   | HYGIENE     | 1.00      | 1.00                    | 1.00                  |

   Land the findings from `divergence.md`: precision is perfect
   on all four kinds; REWORK + HYGIENE perfect on both axes;
   IDE_REPLAY's previously-documented four plugin-instrumentation
   FPs on sessions 025/032/037/039 have been closed by the
   plugin envelope synthesis fix (cross-ref to §5.5 for the
   deferral-then-closure history); ORDERING perfect precision +
   0.36 recall driven by the `splitOnInvalid` validator gate
   (forward-ref to §5.5 for the deferral rationale).

3. **Part 2 — Detection quality table** (Table 5.7). Port
   verbatim:

   | kind        | DPs | alts enumerated | beats user | beats fraction | max magnitude |
   |-------------|----:|----------------:|-----------:|---------------:|--------------:|
   | REWORK      |   9 |               9 |          9 | **100 %**      | 15 lines      |
   | HYGIENE     |  13 |              13 |         13 | **100 %**      | 10 points     |
   | IDE_REPLAY  |  22 |              22 |         16 | **72.7 %**     | 23 points     |
   | ORDERING    |  29 |             225 |          7 | **24.1 %**     | 9 points      |

   > **TODO**: IDE_REPLAY row predates the PR #62 envelope fix
   > and includes the 4 closed plugin-instrumentation FPs.
   > Re-derive DP-level counts from the refreshed `corpus/` JSONs
   > before quoting in the chapter.

   Three findings (verbatim from `divergence.md`): REWORK and
   HYGIENE never propose a useless alt — every DP is real
   improvement; IDE_REPLAY proposes a real improvement 72.7 % of
   the time (calibration question for `manualIde` weight as
   follow-up); ORDERING synthesises 225 alts but only 7 beat the
   user. The 225/29/7 breakdown — total permutations / distinct
   reorder windows / windows where best permutation beats user —
   is the empirical anchor for the §5.4 headline finding.

4. **Part 3 — Injection prominence table** (Table 5.8). Port
   verbatim:

   | injected kind | n caught | @ rank 1 | mean rank |
   |---------------|---------:|---------:|----------:|
   | HYGIENE       |        8 |        8 | 1.00      |
   | IDE_REPLAY    |       16 |       16 | 1.00      |
   | ORDERING      |        2 |        2 | 1.00      |
   | REWORK        |        6 |        6 | 1.00      |

   **100 % top-1 across all four kinds.** This is the strongest
   user-facing claim in the thesis. If the user only sees the
   top recommendation, they always see the deliberately bad
   behaviour. Cross-reference to architecture §4.8 (dashboard's
   "show me what to fix first" surface) where the practical
   relevance lands.

5. **Part 4 — Step-anchor recall (strict matching, disclosed
   cost)** (Table 5.9). Port verbatim:

   | injected kind | n  | step matches | strict rate |
   |---------------|---:|-------------:|------------:|
   | HYGIENE       |  9 |            4 | 44.4 %      |
   | IDE_REPLAY    | 21 |            2 |  9.5 %      |
   | ORDERING      |  5 |            0 |  0 %        |
   | REWORK        |  6 |            1 | 16.7 %      |

   Honest framing: *"we measured the cost of cadence drift
   directly and chose the looser anywhere-in-session policy on
   those numbers."* The cause is plugin checkpoint cadence
   (broader than the IDE_REPLAY envelope fix in PR #62, which
   only closed the per-operation envelope gap, not the
   per-step checkpoint cadence). Re-enabling strict anchoring is
   plugin-side future work.

6. **Per-kind precision/recall bar chart figure** (Figure 5.3).
   Grouped bar chart: 4 kinds × {precision, recall, beats-fraction}
   = 12 bars. Generated from `/tmp/divergence-results.csv` via
   `fixtures/aggregate-divergence.sh`. The visual lands the
   per-kind asymmetry — REWORK/HYGIENE/IDE_REPLAY wall-of-100
   on precision, ORDERING precision-without-recall.

7. **Part 5 — Baselines and absolute magnitudes** (Table 5.10).
   Port verbatim:

   | metric                  |  mean   |  min   |  max   |
   |-------------------------|--------:|-------:|-------:|
   | cleanliness_delta       | +6.8    | −100   | +100   |
   | best_alt_magnitude      | +7.14   |    0   | +23    |
   | sessions with regression| 39/45   |    —   |   —    |

   Lands that the corpus legitimately exercises both clean and
   degraded trajectories (full 200-point cleanliness range), and
   that the detector's best alt averages +7 process-score points
   (a meaningful fraction of the 100-point scale).

8. **Caveats** (~½ page). Port the five caveats from
   `divergence.md`:
   - 45-session corpus; per-cell counts are small; raw counts
     reported alongside percentages.
   - Step-anchoring disabled; strict numbers in Part 4.
   - "Beats user" computed against magnitude > 0; HYGIENE
     COMMIT_GAP DPs have discrete magnitude exactly $W_{cg}$.
   - alt_count = 225 for ORDERING is enumeration count, not
     surfaced alts (29 DPs).
   - Rework-detector adjacent-envelope limitation. The plugin
     envelope fix (PR #62) synthesises an envelope for
     refactoring commands the platform listener is silent for;
     for dialog/template-based refactorings (Extract Variable
     etc.) this can place a synth envelope next to a platform
     envelope describing the same logical operation. The rework
     detector's between-refactor-nodes comparison can read this
     as adjacent rework. On the current corpus this did not
     produce any rework FPs (precision still 1.00) but the
     chapter should flag it as a metric-design limitation.

9. **Expanded-corpus paragraph (STUDY-BLOCKED TODO SLOT)** (~½
   page if it lands). Per `PLAN-user-study.md` Phase 1 and Phase
   2: Cohen's κ per kind from the two independent raters,
   per-kind precision/recall delta on the expanded 51-session
   corpus (45 + 6 = 51), and 2–3 verbatim quotes from Phase 3
   feedback sessions. **Leave a `% TODO study-blocked` comment
   in the LaTeX source** flagging the slot; ship the rest of
   the chapter without it.

### §5.4 Headline finding: reordering rarely helps on commuting windows (1 page)

The chapter's strongest standalone empirical claim, given its own
section so it can't be missed.

Opening sentence (verbatim-style from `divergence.md`):
*"The single most important empirical observation in this experiment
is that reordering refactoring operations, on its own, almost never
leads to a better refactoring outcome on the current corpus."*

Structure (3 paragraphs):

1. **The evidence.** Port verbatim from `divergence.md` "Headline
   empirical claim" section: 225 alternative permutations across
   45 sessions → 29 distinct reorder-window DPs → 7 windows where
   the best permutation beats the user (24 %). On the 5
   SuboptimalOrdering injection sessions, **0 of 5** produced a
   positive-magnitude reorder alt — even hand-engineered bad
   orderings could not be distinguished by the score formula.

2. **The mechanism.** Port the commuting-refactor argument
   verbatim: the reorder synthesiser correctly enumerates
   permutations, but for **commuting** refactorings (which almost
   all in-IDE refactor pairs are by construction — IDE refactors
   are designed to be locally well-defined and non-interacting),
   every permutation reaches the **same terminal AST**. Terminal
   cleanliness is identical across permutations; the `gain` term
   sees zero delta; intermediate-state cadence doesn't enter the
   score formula. This is **not a synthesiser bug** — it's a
   finding about the *measurable* impact of ordering choices on
   the score formula being studied.

3. **Three honest reads.** Port the three reads verbatim:
   (i) ordering matters less than the literature implies, on
   independent refactor windows; (ii) the detector still surfaces
   7 windows where ordering *did* matter — non-commuting pairs
   like inline-then-rename vs rename-then-inline (worth a one-line
   reference to sessions 013, 037, 039, 044); (iii) if ordering is
   to be a meaningful finding, either the playbook design must
   inject non-commuting sequences, or the score formula must be
   extended with an intermediate-state cleanliness term.

Closing sentence: *"This finding does not weaken the tool — it
sharpens the chapter's claim. The detector's job is to surface
*measurable* process improvements, and on commuting independent
reorders there is no measurable improvement to surface."*

**Why this is given its own section** (not buried in §5.3): per
`HONEST_REVIEW_2.md` lines 335–342 and `PLAN-thesis-writeup.md`
item #5, this is the chapter's most defensible standalone empirical
contribution. It's also the finding the Introduction chapter
references when tightening the gap statement, and the finding the
Conclusion chapter recapitulates. Putting it in a labelled
sub-section (`sec:results-headline`) lets every other chapter
cite it cleanly.

### §5.5 Scoped-out fixes (1 page)

Two known limitations of the divergence numbers are **deliberately
not fixed** in this submission. Port both from the "Scoped-out
fixes" section of `divergence.md`. The chapter explicitly defends
the deferrals rather than letting the reader infer them — this is
the "transparency builds credibility" frame from
`HONEST_REVIEW_2.md` lines 335–342.

1. **ORDERING recall 0.39 — `splitOnInvalid` gate.** Port
   verbatim. The gap: validator-gated reorder windows collapse to
   singletons when a manual edit is present. Why not fixed:
   relaxing the gate would surface additional zero-magnitude DPs,
   strengthening the §5.4 commuting-reorder claim, not changing
   it. The headline finding is about *score-formula insensitivity*
   to commuting orderings, not about detector recall — fixing the
   gate is a cosmetic improvement that doesn't move the
   substantive finding.

2. **IDE_REPLAY precision 0.80 — plugin event capture — CLOSED.**
   Port the closure story rather than the original deferral.
   Previously 4 FPs (sessions 025, 032, 037, 039), root-caused to
   IntelliJ commands whose document mutations either bypassed the
   platform `RefactoringEventListener` (Move Method, Change Method
   Signature) or fired under a refactoringId the analyser's
   name-matching did not bridge to RefactoringMiner's vocabulary
   (Extract/Introduce/Inline). Fix shipped in PR #62 (merge
   commit `8daecbf`): `RefactoringCommandListener` now overrides
   `commandStarted` and synthesises a refactoring envelope when
   the IntelliJ command name matches a known refactoring phrase;
   affected sessions re-recorded against the patched plugin.
   Corpus-wide IDE_REPLAY precision rises from 0.80 to 1.00 with
   no other precision/recall regressions. Residual limitation:
   for dialog/template-based refactorings a synth envelope can
   appear adjacent to a platform envelope describing the same
   logical operation; the rework detector's between-node
   comparison can read this as adjacent rework, but did not
   produce any rework FPs on the current corpus.

Closing paragraph: *"Both gaps are well-localised, well-documented,
and orthogonal to the chapter's core claims. The honest version of
the chapter — which names both gaps, explains their root causes,
and reports detector-only numbers alongside the raw numbers — is
more compelling than one in which the gaps were closed at the cost
of narrowing what could be analysed elsewhere."*

## Figure list

Five figures are recommended, all generatable from existing
experiment CSV outputs without re-running anything:

| Fig.   | Section | Content                                                                  | Source CSV                          |
|--------|---------|--------------------------------------------------------------------------|-------------------------------------|
| 5.1    | §5.1    | τ-b density histogram across 4,860 perturbations (log y-axis)            | `/tmp/sensitivity-results.csv`      |
| 5.2    | §5.2    | Power-set ablation sum/sum-recovery heatmap (64 × 1, ordered by active-count) | `/tmp/ablation-results.csv`         |
| 5.3    | §5.3    | Per-kind precision/recall/beats-fraction grouped bar chart (4 kinds × 3 bars) | `/tmp/divergence-results.csv`       |
| 5.4    | §5.3    | Injection-prominence dot plot (rank distribution per kind; 100% top-1 visual) | `/tmp/divergence-results.csv`       |
| 5.5    | §5.4    | Reorder-window funnel (225 perms → 29 windows → 7 wins, bar/sankey)      | `/tmp/divergence-results.csv`       |

Figures 5.1 and 5.5 are load-bearing for the chapter's two
headline visuals. Figures 5.2, 5.3, 5.4 are supporting. If the
14-page cap forces cuts, 5.4 is the first to merge into 5.3's
caption (the 100 %-top-1 finding is the table's job; the figure is
gilding). 5.2 can be deferred to an appendix if 5.5 ends up
double-column.

Generation note: all five can be rendered in pgfplots / TikZ for
consistency with the architecture chapter figure, or in matplotlib
exported to PDF and `\includegraphics` if pgfplots becomes
expensive. Match whichever convention the architecture chapter
settled on.

## Tables list

Eight tables across the three subsections:

| Table | Section | Content                                                  |
|-------|---------|----------------------------------------------------------|
| 5.1   | §5.1    | τ distribution across all 4,860 perturbations            |
| 5.2   | §5.1    | Mean τ by knob × factor, ascending (8 most-sensitive rows) |
| 5.3   | §5.2    | Monotone-by-active-term-count summary                    |
| 5.4   | §5.2    | Per-term single-knob recovery                            |
| 5.5   | §5.2    | Per-term leave-one-out recovery                          |
| 5.6   | §5.3    | Detection accuracy (precision / recall) per kind         |
| 5.7   | §5.3    | Detection quality (DPs, alts, beats-fraction) per kind   |
| 5.8   | §5.3    | Injection prominence (top-1 rate, mean rank) per kind    |
| 5.9   | §5.3    | Step-anchor strict matching cost per kind                |
| 5.10  | §5.3    | Baselines and absolute magnitudes                        |

Tables are the load-bearing artefacts of this chapter — they earn
their pages. The chapter's argumentation strategy is
table-and-caption-driven, with prose explaining what the
numbers *mean* rather than restating what they *say*.

## Source-material map

| Results section | Primary source                                          | Supporting source                              |
|-----------------|----------------------------------------------------------|------------------------------------------------|
| §5.0 intro      | `ablation.md` (tripartite-defence framing)               | `divergence.md`, `sensitivity.md`              |
| §5.1            | `sensitivity.md` (port near-verbatim)                    | `ScoringConfig.kt` for production weights      |
| §5.2            | `ablation.md` (port near-verbatim)                       | Methodology §3.3 Table 3.1 for back-ref        |
| §5.3            | `divergence.md` (port near-verbatim)                     | `manifest-v2.csv` for label semantics          |
| §5.4            | `divergence.md` "Headline empirical claim" section       | —                                              |
| §5.5            | `divergence.md` "Scoped-out fixes" section               | `plugin-misclassifications.md`                 |

## Forward-/back-reference resolution

The methodology chapter has **nine references to Chapter 5** that
this chapter must resolve via labels. The label scheme:

| Methodology reference line                                    | Resolves via                  |
|---------------------------------------------------------------|-------------------------------|
| Intro (line 4): "Chapter~5" rater study reference             | `\ref{sec:results-divergence}` |
| §3.2 (line 71): "sensitivity experiment in Chapter~5"         | `\ref{sec:results-sensitivity}` |
| §3.3 (line 76): "ablation results in Chapter~5"               | `\ref{sec:results-ablation}`  |
| §3.3 (line 93): "full power-set ablation; see Chapter~5"      | `\ref{sec:results-ablation}`  |
| §3.3 (line 109): "Chapter~5's sensitivity and ablation"       | `\ref{sec:results-sensitivity}`, `\ref{sec:results-ablation}` |
| §3.3 (line 115): "validated empirically in Chapter~5"         | `\ref{sec:results-sensitivity}`, `\ref{sec:results-ablation}` |
| §3.4 (line 145): "sensitivity experiment in Chapter~5"        | `\ref{sec:results-sensitivity}` |
| §3.10 (line 256): "ORDERING recall gap reported in Chapter~5" | `\ref{sec:results-divergence}` |
| §3.11 (line 270): "used in Chapter~5 to bound empirical claims" | `\ref{sec:results-divergence}` |

**Methodology §3.3's "Δ recovery (solo)" column is the load-bearing
forward-ref**: it previews this chapter's §5.2 single-knob table
(Table 5.4) with the exact `manualIde 0.357 / broken 0.348 /
length 0.342` numbers in the same column. The §5.2 prose must
explicitly close this loop with a back-reference: *"the single-knob
recoveries reported here are the same numbers previewed in
Table~3.1 of Chapter~3."*

The Introduction chapter (per master plan item #1) tightens its
gap statement using the §5.4 headline finding; the Conclusion
chapter (item #7) recapitulates it. Both will cite
`\ref{sec:results-headline}` once they're drafted; the label must
exist by the time the chapter ships.

## Critical files

- `/Users/ethanhosier/Desktop/random/fyp/final_report/results/results.tex`
  — **new file** to create.
- `/Users/ethanhosier/Desktop/random/fyp/final_report/main.tex`
  — add `\input{results/results.tex}` after the architecture
    chapter input and before `project_plan`.
- `/Users/ethanhosier/Desktop/random/fyp/final_report/methodology/methodology.tex`
  — convert the nine `Chapter~5` placeholder strings (lines 4,
    71, 76, 93, 109, 115, 145, 256, 270) to `\ref{...}` calls per
    the resolution table above. **This is the load-bearing
    backward-compatibility task** for this chapter pass.
- `/Users/ethanhosier/Desktop/random/fyp/final_report/bibs/sample.bib`
  — verify entries for Kendall 1948 (τ statistic), Cohen 1960
    (κ statistic), and any power-set / Shapley-value ablation
    citation returned by Prompt R2 below. Most other citations
    needed by this chapter (Negara 2013, Vakilian 2012, Murphy-Hill
    2009, etc.) are already in the bib from the methodology pass.
- Reference (read-only): the four `explained_results/*.md` files,
  the relevant CSV outputs in `/tmp/`, and `HONEST_REVIEW_2.md`.

Required labels for cross-chapter resolution:

- `\label{ch:results}` — chapter
- `\label{sec:results-intro}` — §5.0
- `\label{sec:results-sensitivity}` — §5.1
- `\label{sec:results-ablation}` — §5.2
- `\label{sec:results-divergence}` — §5.3
- `\label{sec:results-headline}` — §5.4 (cited from Intro + Conclusion)
- `\label{sec:results-scoped-out}` — §5.5
- `\label{tab:results-sensitivity-tau}` — Table 5.1
- `\label{tab:results-ablation-monotone}` — Table 5.3
- `\label{tab:results-divergence-accuracy}` — Table 5.6
- `\label{fig:results-tau-histogram}` — Figure 5.1
- `\label{fig:results-prf}` — Figure 5.3
- `\label{fig:results-reorder-funnel}` — Figure 5.5

## Research-agent prompts

Five prompts. **R1, R2, R3 are load-bearing** for §5.1, §5.2, §5.4
respectively. **R4 is study-blocked-supporting** (only needed once
the user study lands). **R5 is low-priority** (whether to position
the headline finding as novel or against prior work).

### Prompt R1 — Kendall τ-b methodology for ranking stability (load-bearing)

> "Results-chapter citation hunt. The Sensitivity experiment in
> my MEng thesis uses Kendall τ-b as the primary ranking-stability
> measure when perturbing each weight of a process-quality score
> formula. I need citations for the *methodological origin* of
> τ-b plus any recent empirical-SE work that uses τ-b for
> ranking-stability under metric or weight perturbation.
>
> Specifically:
> (a) The methodological origin: Kendall 1948 'Rank Correlation
>     Methods' (the canonical citation for τ-b with ties) and/or
>     a more recent methodology textbook that I can cite for the
>     tied-pair handling formula (Agresti 'Analysis of Ordinal
>     Categorical Data' is one candidate).
> (b) Empirical SE papers in the last decade that have used
>     Kendall τ (or τ-b specifically) to defend that a metric
>     ranking is robust to design choices — particularly in
>     ranking-of-defects, ranking-of-refactoring-candidates,
>     or ranking-of-quality-issues contexts.
> (c) Methodological discussions of when τ-b is preferable to
>     Spearman ρ or top-k overlap — especially on small N
>     (my ranking lengths range from 1 to ~20 DPs per fixture).
>
> Venues: ICSE, FSE, ASE, TSE, EMSE, MSR, SANER, ICSME, IST, JSS,
> arXiv. Up to 3 papers, ranked by load-bearing strength. The
> Kendall 1948 / Agresti citation is the most important — the
> recent SE work is a nice-to-have. If the recent SE work is
> thin (or all uses Spearman ρ instead), say so confidently —
> a 'τ-b is preferred for tied ranks but Spearman dominates the
> empirical-SE literature' answer is itself useful for an honest
> framing paragraph."

### Prompt R2 — Power-set / Shapley-value ablation in metric-evaluation contexts (load-bearing)

> "Results-chapter citation hunt. The Ablation experiment in my
> thesis runs a full $2^6 = 64$-config power set across the six
> process-score weights, computing magnitude recovery and
> ranking-stability per config. I want methodological precedent
> for *power-set ablation studies* in the context of evaluating
> composite metric formulas — analogous to (but pre-dating /
> distinct from) the Shapley-value attribution machinery used
> in modern explainability literature.
>
> Specifically:
> (a) Empirical SE papers that ablate weighted metric formulas
>     by zeroing subsets of terms and measuring the result —
>     particularly Verdecchia et al. 2022's critique of composite
>     score weights (already in my bib) or any follow-up work in
>     the same line.
> (b) The conceptual link between power-set ablation and
>     Shapley-value attribution: any paper that uses full
>     power-set enumeration as a brute-force Shapley computation
>     for a small number of terms, in a metric / quality / model
>     context.
> (c) Alternative ablation designs (leave-one-out only,
>     forward-selection only, random subsets) and rationale for
>     when full power-set is justified vs. when LOO suffices.
>
> Venues: ICSE, FSE, ASE, TSE, EMSE, MSR, SANER, ICSME, IST, JSS,
> ICML/NeurIPS for the Shapley link, arXiv. Up to 3 papers,
> ranked by load-bearing strength. If the literature on
> power-set ablation in metric evaluation is thin (or all of it
> is in ML model-interpretability rather than SE metric design),
> say so confidently — a 'this is more common in ML
> interpretability than in SE metric design' answer lets me
> position the experiment honestly as a methodological port from
> the ML side."

### Prompt R3 — Refactoring step reordering and effect on outcome metrics (load-bearing)

> "Results-chapter citation hunt. The Divergence experiment in
> my thesis surfaces that reordering refactoring operations on
> commuting independent windows almost never produces a better
> outcome under the process-quality score formula being studied
> — 0/5 deliberately-injected SuboptimalOrdering sessions
> produced a positive-magnitude reorder alt, and only 7 of 29
> distinct reorder windows had a best permutation that beat the
> user's order. I want to know whether any prior empirical study
> has shown — or claimed — that refactoring step reordering has
> limited impact on outcome metrics, so I can position my
> finding against them.
>
> Specifically:
> (a) Empirical studies on the *effect* of refactoring order /
>     sequence on outcome metrics (test pass rate, structural
>     quality measures, defect introduction, build stability).
> (b) Search-based refactoring literature (Harman & Tratt 2007;
>     Mkaouer 2016; Mohan & Greer 2019 — already in my bib) on
>     whether the *order* of operations matters at all once the
>     terminal Pareto-optimal end state is fixed. If the
>     consensus is 'order matters only when refactorings
>     interact', that supports my commuting-reorder finding.
> (c) Theoretical work on refactoring commutativity / confluence:
>     papers that formally characterise when two refactoring
>     operations commute. Mens & Tourwé 2004's survey (already in
>     my bib) treats this lightly; I want anything that goes
>     deeper.
> (d) Any null results — papers that *tried* to show ordering
>     matters and *failed* to find an effect. These are
>     particularly valuable.
>
> Venues: ICSE, FSE, ASE, TSE, EMSE, MSR, SANER, ICSME, JSS,
> SoSym, arXiv. Up to 3 papers, ranked by load-bearing strength.
> If the literature is thin (which I suspect — most refactoring
> work assumes order matters without measuring it), say so
> confidently. A 'no prior empirical work has measured this
> directly' answer lets me frame the §5.4 headline finding as
> genuinely novel rather than as a contribution-to-an-ongoing-
> debate."

### Prompt R4 — Cohen's κ for inter-rater agreement in SE-empirical work (study-blocked-supporting)

> "Results-chapter citation hunt. The user-study expansion of
> my thesis reports Cohen's κ per kind for two independent
> raters labelling refactoring trajectories against a 4-kind
> multi-label schema (ORDERING, MANUAL_REFACTOR, REWORK,
> HYGIENE). I need the methodological origin citation for κ
> plus guidance on κ-band interpretation for small-n / multi-
> label settings.
>
> Specifically:
> (a) Cohen 1960 'A coefficient of agreement for nominal scales'
>     — the canonical citation.
> (b) Landis & Koch 1977 — the canonical κ-band interpretation
>     (poor / fair / moderate / substantial / almost perfect).
>     Or any more recent SE-empirical paper that adapts these
>     bands for software-artefact labelling.
> (c) Empirical SE papers in the last 5 years that report κ on
>     multi-label refactoring / smell / quality-issue
>     classification, ideally with n < 100 sessions (matching
>     the small-n constraint of my user study).
>
> Venues: ICSE, FSE, ASE, TSE, EMSE, MSR, SANER, ICSME, IST, JSS,
> EASE, arXiv. Up to 2 papers (Cohen 1960 + one SE-empirical),
> ranked by load-bearing strength. If the SE-empirical κ
> literature on multi-label refactoring labelling specifically
> is thin, say so — a 'κ on single-label smell classification
> is well-trodden but multi-label is less common' answer is
> useful for the threats-to-validity chapter as well as this
> one.
>
> **This prompt is only needed if Phase 1 of the user study
> lands within the writeup window.** Defer running it until the
> κ numbers exist."

### Prompt R5 — Top-N hit rate as a ranking-stability measure on small N (low-priority)

> "Quick check. The Sensitivity experiment reports both Kendall
> τ-b and top-5 hit rate as ranking-stability measures, because
> τ-b is brittle on small N (some fixtures have only 1–3 DPs)
> and top-5 hit rate is the more stable signal for the
> user-facing dashboard surface. I'd like one or two citations
> defending top-N hit rate / top-k overlap as the preferred
> ranking-stability measure when N is small or the user only
> cares about the top of the ranking.
>
> Possibilities:
> (a) Information-retrieval literature on top-k overlap measures
>     (Webber et al. 2010 'A similarity measure for indefinite
>     rankings' is one candidate).
> (b) Empirical SE papers that report top-N hit rate alongside
>     τ-b or Spearman ρ as a robustness argument.
> (c) Methodological work on when top-N overlap is preferable to
>     full-ranking correlations.
>
> Venues: SIGIR / TOIS / IR&D for the IR literature; ICSE / FSE
> / TSE / EMSE / IR&D for the SE side. Up to 2 sources. If the
> SE side is thin, the IR citation alone is sufficient for the
> §5.1 caveat paragraph."

## Tone preservation notes

The results chapter sits at the empirical core of the thesis. It
should read as confident-but-honest reporting, not as a marketing
brochure. Defaults:

- **Topic-sentence-first.** Every paragraph opens with the
  empirical result it establishes, not with "this section
  reports...". A reader skimming first sentences should lift the
  chapter's full story.
- **Numbers earn their pages.** Tables are the chapter's
  load-bearing artefacts; prose explains what numbers mean rather
  than restating what they say. Avoid restating a table value in
  the surrounding sentence — cross-reference the table cell and
  interpret it.
- **Raw counts alongside percentages.** Per `divergence.md`
  caveats, per-cell counts in the 45-session corpus are small
  enough that ratios need context. Write "9 of 9 (100%)" not just
  "100%"; write "4818 of 4860 (99.1%)" not just "99.1%".
- **Honest disclosure of limitations.** Each subsection ends with
  its caveats paragraph; the chapter does not bury caveats in
  footnotes. The four `explained_results/*.md` files all model
  this — port the caveat blocks verbatim.
- **V2 magnitude semantic enforced.** Every magnitude number is
  the trajectory-final $J$ delta. The V1 line-count REWORK
  magnitude appears only as historical footnote context where
  the source `.md` does so (e.g. ablation's "under V1, the
  active_count = 0 floor was 0.119"). No production claims rest
  on V1 numbers.
- **Topic-sentence intent.** Each section's opening sentence
  states the empirical claim, not the section preamble. §5.1
  opens "Across 4,860 perturbations..."; §5.2 opens "Full power-set
  ablation..."; §5.3 opens "Against 45 sessions..."; §5.4 opens
  "The single most important empirical observation...".
- **"This thesis"/occasional "we" is fine.** Avoid "the author" /
  "our paper". Match the methodology chapter's voice.
- **Pseudo-formulas where they help, prose otherwise.** Kendall
  τ-b's formula is worth a single display equation in §5.1; the
  sum/sum recovery formula is worth a one-line definition in
  §5.2. Most computations stay in prose.
- **No emojis. No fluff. No hedging on robust findings.** The
  three internal-defence claims (sensitivity holds, ablation
  breaks things, divergence catches injections) are robust; state
  them confidently. The two known gaps (ORDERING recall,
  IDE_REPLAY plugin FPs) are well-localised; state the deferrals
  confidently.

## Verification

1. **Compile cleanly.** `cd final_report && bash build.sh` produces
   both `main.pdf` and `main_dark.pdf` with no new
   undefined-citation or unresolved-reference warnings. The nine
   `Chapter~5` references in methodology should now resolve to
   numbered references.
2. **Page count.** ≤ 14 pages strictly. Target 11.
3. **All forward-ref labels exist.** `\label{ch:results}`,
   `\label{sec:results-sensitivity}`,
   `\label{sec:results-ablation}`,
   `\label{sec:results-divergence}`,
   `\label{sec:results-headline}`,
   `\label{sec:results-scoped-out}`, plus the table and figure
   labels listed under Critical files.
4. **Methodology wiring.** All nine `Chapter~5` placeholder
   strings in `methodology.tex` converted to `\ref{...}` per the
   resolution table.
5. **Numbers cross-verify against source `.md` files.** Each
   table is a verbatim port; spot-check 10 random cells against
   the corresponding source `.md` to confirm no V1/V2 mix-up.
   The methodology §3.3 "Δ recovery (solo)" column must match
   §5.2 Table 5.4 exactly (`manualIde 0.357`, `broken 0.348`,
   `length 0.342`, `commitGap 0.126`, `skipTests 0.093`,
   `gain 0.000`).
6. **TODO slot present.** `% TODO study-blocked: expanded-corpus
   paragraph (κ + multi-recorder + verbatim quotes)` comment
   present at the end of §5.3.
7. **Figures render.** All five figures render cleanly in *both*
   light and dark builds. Inspect `main_dark.pdf` for
   axis-label legibility.
8. **Read aloud.** Each subsection's topic sentence states the
   empirical claim. The chapter ends on §5.4's "this finding
   doesn't weaken the tool, it sharpens the claim" beat.
9. **Cross-chapter cite-back.** Introduction chapter's gap
   statement and Conclusion chapter's recapitulation both cite
   `\ref{sec:results-headline}` once they're drafted (verification
   deferred until those chapters land).
10. **Plugin-misclassifications path resolves.** §5.3 caveat 5
    and §5.5 deferral 2 both cite `plugin-misclassifications.md`
    or its in-thesis description. The catalogue itself does not
    need to be reproduced in the chapter — it lives in the
    appendix or stays in the source tree as a developer
    artefact.

## What this pass deliberately doesn't do

- **Doesn't re-derive the methodology.** Magnitude semantics,
  weight definitions, detector logic, synthesiser logic — all
  defined in methodology Chapter 3. The results chapter cites
  back, never re-derives.
- **Doesn't re-litigate the V1 → V2 transition.** V1 numbers
  appear only as honest-disclosure footnotes where the source
  `.md` includes them; no main-text claims rest on V1.
- **Doesn't run experiments.** All numbers are read from existing
  CSV outputs and the four `explained_results/*.md` files. If a
  number needs verification, read the source `.md` — do not
  re-run.
- **Doesn't preview threats-to-validity** beyond the per-section
  caveats already in the source `.md` files. The dedicated
  threats chapter (item #6 in master plan) consolidates
  caveats across experiments; this chapter is the per-experiment
  honest disclosure, not the cross-cutting analysis.
- **Doesn't anticipate the user study's findings.** The
  expanded-corpus paragraph at the end of §5.3 is study-blocked;
  the `% TODO study-blocked` comment marks the slot. Everything
  else in the chapter ships independently.
- **Doesn't speculate on future-work fixes** beyond what the
  source `.md` files already discuss. The §5.5 scoped-out fixes
  are the only future-work mentions in this chapter; the broader
  future-work bullet list (multi-language, AI-vs-human, etc.)
  lives in Conclusion (item #7).
- **Doesn't introduce new metric definitions.** Kendall τ-b,
  Cohen's κ, top-N hit rate are defined in the relevant subsection
  introductions with one-sentence definitions plus a citation;
  formal definitions live in their citation sources, not in this
  chapter.
- **Doesn't replicate the plugin-misclassifications session
  catalogue.** §5.5 deferral 2 cites the artefact and summarises;
  the per-session details (025/032/037/039 root causes) are in
  the source `.md` and optionally an appendix.

## Estimated effort

3 days of focused writeup, given:

- The three source `.md` files are already drafts of the
  chapter's three subsections; this is a port-and-edit pass, not
  a from-scratch writeup. Methodology was 5 days because the code
  had to be re-explained at thesis-formal level; the
  `explained_results/*.md` files are already at near-thesis prose
  quality.
- Figure generation is the largest discretionary time sink. The
  five recommended figures are all generated from existing CSV
  outputs (no re-running experiments), but rendering through
  pgfplots or matplotlib-to-PDF takes ~½ day per figure if styling
  must match the architecture chapter convention. Budget 1 day
  total for figures.
- Citation-hunt prompts (R1, R2, R3) should be run in parallel at
  the start of the writeup pass; they return within hours and
  inform the §5.1 / §5.2 / §5.4 framing paragraphs. R4 is
  study-blocked and deferred. R5 is low-priority and may be
  skipped.
- The nine `Chapter~5` placeholder conversions in
  `methodology.tex` are a ~½ hour mechanical task once the
  results labels exist.

This is **less than the master plan's 1-week estimate**, because
the source material was kept honest in `explained_results/` as the
experiments ran — that investment now pays off in writeup speed.
The contingency budget against the master plan's 1-week estimate
absorbs (i) figure styling friction, (ii) the study-blocked TODO
slot landing mid-writeup if the user study completes early, and
(iii) any citation-hunt back-and-forth from prompts R1–R3.

---

## Research-agent results: Prompt R1 (completed 2026-05-19)

Web-search agent run for the Kendall τ-b methodology citation
hunt. Verdict: **canonical citations strong**; recent empirical-SE
literature does not contain a clean precedent that uses τ-b
specifically to defend ranking-stability under formula-weight
perturbation. Three load-bearing citations + a confident "the
methodology is from statistics, not from SE convention" framing.

### Executive summary

For the §5.1 framing paragraph, the load-bearing citation is
**Kendall (1948), *Rank Correlation Methods*** — the canonical
origin of τ-b's tied-pair normalisation. **Agresti (2010),
*Analysis of Ordinal Categorical Data*, 2nd ed., Wiley** is the
modern textbook companion that confirms the formula is still the
textbook-standard form. **Croux & Dehon (2010), "Influence
functions of the Spearman and Kendall correlation measures"**
(Statistical Methods & Applications) is the cleanest single-paper
modern statistical defence of τ over Spearman ρ on small N - the
right cite for the "why τ-b rather than ρ" framing.

**Honest negative result for recent empirical-SE work:** searches
across ICSE/FSE/ASE/TSE/EMSE/MSR/SANER/ICSME for 2015-2025 work
using τ-b specifically to defend ranking robustness under
parameter perturbation returned tangential hits only. Test-case
prioritisation uses τ-distance as an evaluation metric (not as a
robustness measure); ranking-oriented defect prediction reports
Spearman ρ more often than τ-b. The honest framing is "τ-b is
methodologically preferred for tied small-N rankings;
Spearman ρ dominates the empirical-SE convention; our choice is
defensible on methodology, not convention."

### Citation 1 (STRONG, mandatory) - Kendall 1948

**Citation.** Kendall, M. G. (1948). *Rank Correlation Methods*.
1st ed. London: Charles Griffin & Co. Ltd. vii + 160 pp.

**Relevance.** The canonical first-edition treatment of rank
correlation. Chapter 3 introduces τ and the tied-pair-corrected
variant (now universally called τ-b) with the normalisation
$\tau_b = (C - D) / \sqrt{(n_0 - n_1)(n_0 - n_2)}$ where
$n_0 = n(n-1)/2$, $n_1 = \sum t_i(t_i-1)/2$ for x-ties,
$n_2 = \sum u_j(u_j-1)/2$ for y-ties. This is the citation
reviewers expect when "τ-b" appears in a methods section.

**Category fit.** (a) primary.

**Load-bearing strength.** **Strong** - mandatory cite.

**Note on page number.** The 1948 first edition is the canonical
origin but is not freely paginated online. Most modern methods
sections cite "Kendall 1948" but use the 4th edition (1970,
pp. 34-38) as the practical pointer to the τ-with-ties
exposition. The thesis can cite either - "(Kendall, 1948)" with
chapter granularity is the safest, or "(Kendall, 1970, pp. 34-38)"
if a precise page is required.

**URLs.**
- Internet Archive scan: https://archive.org/details/rankcorrelationm0000kend
- Contemporaneous Journal of the Institute of Actuaries review
  (confirms vii + 160 pp., publisher, year):
  https://www.cambridge.org/core/journals/journal-of-the-institute-of-actuaries/article/abs/rank-correlation-methods-by-maurice-g-kendall-ma-pp-vii-160-london-charles-griffin-and-co-ltd-42-drury-lane-1948-18s/335FD9F4DDCBDF8A7167B4B66EE4DFB9
- Later editions (Kendall & Gibbons): https://www.amazon.com/Rank-Correlation-Methods-Charles-Griffin/dp/0195208375

### Citation 2 (STRONG companion) - Agresti 2010

**Citation.** Agresti, A. (2010). *Analysis of Ordinal
Categorical Data*, 2nd Edition. Wiley Series in Probability and
Statistics. Hoboken, NJ: John Wiley & Sons. ISBN
978-0-470-08289-8. DOI 10.1002/9780470594001.

**Relevance.** §2.4 explicitly derives γ, Kendall's τ-b,
Kendall's τ, and Somers' d from concordant/discordant-pair
counts. Gives the τ-b formula
$\tau_b = (C - D) / \sqrt{(C + D + T_x)(C + D + T_y)}$ and the
standard-error derivation - exactly the formula behind
`scipy.stats.kendalltau`. The modern textbook companion to
Kendall (1948); pair them in one footnote for canonical-origin +
textbook-standard coverage.

**Category fit.** (a) primary.

**Load-bearing strength.** **Strong** - paired-companion cite.

**URLs.**
- Wiley canonical: https://onlinelibrary.wiley.com/doi/book/10.1002/9780470594001
- Open-access PDF: https://ndl.ethernet.edu.et/bitstream/123456789/38587/1/Alan%20Agresti.pdf
- Wiley StatsRef "Ordinal Data" chapter by Agresti:
  https://onlinelibrary.wiley.com/doi/abs/10.1002/9781118445112.stat00372.pub2

### Citation 3 (MODERATE-STRONG) - Croux & Dehon 2010

**Citation.** Croux, C. & Dehon, C. (2010). "Influence functions
of the Spearman and Kendall correlation measures." *Statistical
Methods & Applications* 19(4):497-515. DOI
10.1007/s10260-010-0142-z.

**Relevance.** Derives the influence functions and gross-error
sensitivities of both Spearman ρ and Kendall τ; computes
statistical efficiencies at the bivariate-normal model. Headline
result: both estimators combine a bounded smooth influence
function with high efficiency, but **τ's influence function is
strictly smaller in absolute value than ρ's** - the formal
underpinning of the textbook claim that τ is more robust to
outliers and more accurate in small samples. Includes simulation
evidence comparing the estimators against robust-covariance
alternatives.

**Category fit.** (c) primary.

**Load-bearing strength.** **Moderate-to-strong.** The cleanest
single-paper modern statistical defence of τ's small-sample
behaviour. Cite in the same sentence that justifies τ-b over
Spearman ρ.

**URLs.**
- Springer canonical: https://link.springer.com/article/10.1007/s10260-010-0142-z
- TSE preprint PDF: https://www.tse-fr.eu/sites/default/files/medias/stories/SEMIN_09_10/STATISTIQUE/croux.pdf

### Honest negative result (for §5.1 framing paragraph)

Searches across ICSE/FSE/ASE/TSE/EMSE/MSR/SANER/ICSME for
2015-2025 work using τ-b specifically to defend ranking
robustness under parameter perturbation returned tangential hits
only:

- Test-case prioritisation papers use τ-distance as an evaluation
  metric against ground-truth orderings, **not** as a
  robustness-to-design-choice measure.
- Ranking-oriented defect prediction (Yang et al.'s LTR work, Yan
  et al.'s effort-aware ranking) reports Spearman ρ more often
  than τ-b.
- The cleanest single neighbour is broader **feature-ranking-
  stability literature** outside SE (Khoshgoftaar/Wald/Dittman's
  noise-stability study), which uses Kendall τ to quantify
  ranking agreement under noise - methodologically the same
  shape as the perturbation study here, but applied to feature
  importances rather than formula weights. WEAK as a direct
  precedent.

Honest framing for the thesis: "this experimental design is
novel for refactoring-trajectory analysis; methodology follows
Kendall 1948 / Agresti 2010 / Croux & Dehon 2010 from the
statistics literature rather than an SE precedent."

### Optional secondary - Webber, Moffat & Zobel 2010 (RBO)

**Citation.** Webber, W., Moffat, A., & Zobel, J. (2010). "A
similarity measure for indefinite rankings." *ACM Transactions
on Information Systems* 28(4), Article 20, 1-38. DOI
10.1145/1852102.1852106.

**Relevance.** The canonical RBO (rank-biased overlap) citation.
Useful as a "considered alternatives" footnote in §5.1: the
thesis explicitly does **not** use RBO because per-fixture
rankings are *definite* (every fixture has a finite,
fully-enumerated DP list), so the top-weighting / indefinite-
ranking motivation for RBO does not apply.

**Load-bearing strength.** **Weak as positive justification;
useful as a "rejected alternative" footnote.**

**URLs.**
- ACM canonical: https://dl.acm.org/doi/10.1145/1852102.1852106
- Author copy: http://blog.mobile.codalism.com/research/papers/wmz10_tois.pdf

### Recommended §5.1 framing sentence

A defensible three-citation sentence:

> "We use Kendall's τ-b (Kendall, 1948; Agresti, 2010) as the
> primary ranking-stability statistic because, on the small
> (N = 1-20) per-fixture rankings produced by our divergence-
> point detector, τ-b's bounded influence function and superior
> small-sample efficiency relative to Spearman ρ (Croux & Dehon,
> 2010) make it the methodologically appropriate choice for
> tied ordinal data. We report top-5 hit rate alongside τ-b
> because τ-b on N ≤ 5 rankings is brittle, and we explicitly
> do not use rank-biased overlap (Webber et al., 2010) because
> our per-fixture rankings are definite rather than indefinite."

Honest, methodologically airtight, side-steps the absence of a
direct SE precedent.

### Bib entries to add to `bibs/sample.bib`

Three primary entries needed:
- `kendall1948rank` - Kendall, M.G., *Rank Correlation Methods*,
  Charles Griffin & Co., London, 1948 (`@book`).
- `agresti2010ordinal` - Agresti, A., *Analysis of Ordinal
  Categorical Data*, 2nd ed., Wiley, 2010 (`@book`, DOI
  10.1002/9780470594001).
- `croux2010influence` - Croux & Dehon, "Influence functions of
  the Spearman and Kendall correlation measures", *Statistical
  Methods & Applications* 19(4):497-515, 2010 (`@article`, DOI
  10.1007/s10260-010-0142-z).

Optional secondary entry:
- `webber2010rbo` - Webber, Moffat & Zobel, "A similarity measure
  for indefinite rankings", ACM TOIS 28(4), Article 20, 2010
  (`@article`, DOI 10.1145/1852102.1852106) - cite only if §5.1
  includes a considered-alternatives footnote.

Verify DOIs and URLs before committing during the bibliography
audit (item #8 in `PLAN-thesis-writeup.md`).

---

## Research-agent results: Prompt R2 (completed 2026-05-19)

Web-search agent run for the power-set / Shapley-value ablation
in metric-evaluation contexts. Verdict: **no direct empirical-SE
precedent**; the methodological lineage runs through (1)
composite-indicator sensitivity analysis (Saisana / Saltelli,
econometrics + OECD/JRC) and (2) ML interpretability (Lundberg
& Lee 2017; Štrumbelj & Kononenko 2014). The §5.2 framing is an
honest port from those two adjacent communities.

**!! Important bib correction surfaced by the agent.** The
Verdecchia 2022 cite assumed in earlier planning may be
mis-attributed - see "Bib correction" block below.

### Executive summary

Power-set ablation of composite-metric weights has **no direct
empirical-SE precedent** at ICSE/FSE/TSE/EMSE. The two adjacent
communities with established methodology are:

1. **Composite-indicator sensitivity analysis** -
   Saisana/Saltelli (2005, JRSS-A) is the canonical reference;
   the OECD/JRC handbook codifies it. Established discipline for
   "vary weights, report ranking stability" on composite indices
   (UN Technology Achievement Index etc.).
2. **ML interpretability** - Lundberg & Lee (2017, NeurIPS) and
   Štrumbelj & Kononenko (2014, K&IS / 2010, JMLR) define the
   $2^n$-subset enumeration as the *definitional* operation of
   the Shapley value. With $n = 6$ terms the full power set is
   tractable; brute-force is *exact*, not approximate.

Honest framing for §5.2: "This experimental design is a
methodological port from composite-indicator sensitivity analysis
(Saisana & Saltelli 2005) and Shapley-value feature attribution
(Lundberg & Lee 2017), applied to refactoring-trajectory metrics."

### Citation 1 (STRONG, fits a + c) - Saisana, Saltelli & Tarantola 2005

**Citation.** Saisana, M., Saltelli, A., & Tarantola, S. (2005).
"Uncertainty and sensitivity analysis techniques as tools for
the quality assessment of composite indicators." *Journal of the
Royal Statistical Society: Series A (Statistics in Society)*,
168(2), 307-323.

**Relevance.** Canonical methodological reference for systematic
uncertainty / sensitivity analysis on composite-indicator
construction choices - including weight schemes, aggregation
rules, and normalisation. Jointly perturbs weights and
aggregation choices, computes Sobol-style variance-based indices
to attribute output-ranking variance to each input choice,
demonstrates on the UN Technology Achievement Index. **This is
the precedent for "vary the weights of a composite, see how
rankings move"** - the same move §5.2 makes, with subset-zeroing
instead of distributional perturbation.

**Category fit.** (a) primary; (c) secondary.

**Load-bearing strength.** **Strong.** Lets the chapter write
"subjecting weight choices to systematic sensitivity analysis is
established practice for composite indicators (Saisana et al.
2005); we port this discipline to a refactoring-process
composite." Justifies the design without inventing the framing.

**URL.** https://rss.onlinelibrary.wiley.com/doi/abs/10.1111/j.1467-985X.2005.00350.x

### Citation 2 (STRONG, fits b) - Lundberg & Lee 2017 (SHAP)

**Citation.** Lundberg, S. M., & Lee, S.-I. (2017). "A Unified
Approach to Interpreting Model Predictions." *Advances in Neural
Information Processing Systems 30* (NeurIPS 2017), 4765-4774.

**Relevance.** Defines SHAP, axiomatically grounded in the
Shapley value
$\phi_i = \sum_{S \subseteq N \setminus \{i\}} \tfrac{|S|!(|N|-|S|-1)!}{|N|!}[v(S \cup \{i\}) - v(S)]$
- i.e. the *definitional* form is enumeration over $2^{n-1}$
subsets. The paper introduces KernelSHAP precisely because exact
enumeration is infeasible in the high-dimensional ML setting.
The contrapositive is exactly this thesis's situation: with $n=6$
terms, $2^6 = 64$ evaluations is tractable, so brute-force is
*correct* and sampling approximations are *unnecessary*.

**Category fit.** (b) primary.

**Load-bearing strength.** **Strong.** Lets the chapter write:
"with six terms, the $2^6 = 64$-config power set is small enough
that brute-force enumeration computes exact Shapley-style
contributions (Lundberg & Lee 2017); sampling approximations
(KernelSHAP) become necessary only when $n$ pushes enumeration
intractable."

**URLs.**
- arXiv: https://arxiv.org/abs/1705.07874
- NeurIPS proceedings: https://papers.nips.cc/paper/7062-a-unified-approach-to-interpreting-model-predictions

### Citation 3 (MODERATE, fits b) - Štrumbelj & Kononenko 2014

**Citation.** Štrumbelj, E., & Kononenko, I. (2014). "Explaining
prediction models and individual predictions with feature
contributions." *Knowledge and Information Systems*, 41(3),
647-665. (Predecessor: *JMLR* 11, 2010, "An Efficient Explanation
of Individual Classifications using Game Theory.")

**Relevance.** Independently re-introduced Shapley-value feature
attribution to ML three years before Lundberg & Lee. Explicitly
discusses the $2^n$-subset enumeration cost and proposes
Monte-Carlo sampling. The 2010 JMLR paper is the cleaner
methodological reference for "sampling approximation when exact
enumeration is infeasible"; the 2014 K&IS paper is more widely
cited in interpretability surveys.

**Category fit.** (b) secondary.

**Load-bearing strength.** **Moderate.** Useful companion to
Lundberg & Lee for "the lineage of Shapley-attribution methods
in ML" but doesn't add load over (2) alone. Cite both for
belt-and-braces; cite only Lundberg & Lee for one citation.

**URLs.**
- K&IS 2014: https://link.springer.com/article/10.1007/s10115-013-0679-x
- JMLR 2010 predecessor: https://www.jmlr.org/papers/v11/strumbelj10a.html

### Bib correction surfaced by the agent

**This must be resolved before any chapter ships citing
Verdecchia.** The cite of *"Verdecchia et al. 'On the impact of
metric weighting in code quality assessment'"* (or similar
critique-of-composite-weighting framing) does **not match any
published paper** in Verdecchia's publication list.

The closest match is:
> Verdecchia, R., Malavolta, I., Lago, P., & Ozkaya, I. (2022).
> "Empirical evaluation of an architectural technical debt index
> in the context of the Apache and ONAP ecosystems." *PeerJ
> Computer Science*, 8, e833.
> https://peerj.com/articles/cs-833/

The agent directly verified this paper's methodology section: it
**averages dimension scores with equal weights**, states
"different weights to the different ATDDT dimensions" could be
assigned in *future work*, and performs **no** weight ablation
or sensitivity analysis. It is an evaluation of a composite, not
a critique of its weighting.

**Two options for the chapter (and the bib):**

- **(a) Cite Verdecchia 2022 honestly.** Position it as a
  composite-metric *evaluation* that *omits* weight ablation;
  §5.2's ablation experiment fills exactly the gap that paper
  defers to future work. This actually *strengthens* the
  contribution claim.
- **(b) Drop the Verdecchia cite from §5.2.** Replace with
  Saisana & Saltelli 2005 as the composite-indicator-sensitivity
  precedent. Keep Verdecchia elsewhere if load-bearing for
  composite-metric *background*, not for weight critique.

**Action item before chapter ships:** double-check the exact
title and DOI of the existing Verdecchia entry in
`final_report/bibs/sample.bib`; if it says "On the impact of
metric weighting..." or anything similar, it is mis-cited. The
methodology chapter's §3.3 weight-justification footnote
referencing Verdecchia 2022 should be re-read against the actual
PeerJ CS 2022 paper.

### Useful secondary references (optional)

- **OECD/JRC Handbook on Constructing Composite Indicators
  (2008)**, Nardo, Saisana et al. The practitioner reference
  codifying Saisana & Saltelli's sensitivity methodology. Cite
  if §5.2 needs handbook-level rather than journal-level
  reference.
  URL: https://www.oecd.org/content/dam/oecd/en/publications/reports/2005/08/handbook-on-constructing-composite-indicators_g17a16e3/533411815016.pdf
- **Borg, Mones, Tornhill & Pruvost (2024).** "Ghost Echoes
  Revealed: Benchmarking Maintainability Metrics..." ICSME 2024
  Industry Track. Benchmarks SonarQube, MS-MI, CodeScene Code
  Health against human judgment but does **not** ablate
  composite weights. Useful as §5.2 background on "composite
  maintainability metrics have validity concerns" but not for
  the ablation-design precedent.
  URL: https://arxiv.org/abs/2408.10754
- **Ulan, Löwe, Ericsson & Wingkvist (2021).** "Weighted
  software metrics aggregation and its application to defect
  prediction." *EMSE* 26(86). Defines a probability-theoretic
  *automatic* weighting scheme; doesn't ablate, but is the
  closest EMSE paper to the topic and worth one citation as
  "the alternative direction the SE literature has actually
  taken on weighted metric aggregation."
  URL: https://link.springer.com/article/10.1007/s10664-021-09984-2
- **Li & Janson (2024).** "Optimal ablation for interpretability"
  + the factorial-vs-LOO interpretability literature. Explicitly
  argues LOO misses interaction effects that factorial / full-
  subset designs catch - exactly the rationale for §5.2's
  power-set vs. LOO choice. Citable for (c).
  URL: https://arxiv.org/abs/2409.09951

### Recommended §5.2 framing paragraph

A three-citation framing paragraph:

> "This experimental design is a methodological port: from
> composite-indicator sensitivity analysis (Saisana et al. 2005),
> where systematically perturbing weights and reporting ranking
> stability is established discipline; and from Shapley-value
> feature attribution (Lundberg & Lee 2017), where enumeration
> over $2^n$ feature subsets is the definitional operation and
> is tractable when $n$ is small. With six terms ($2^6 = 64$
> configurations, ~1.7s wall-clock against the 45-fixture
> corpus), brute-force enumeration is exact, not approximate -
> sampling-based attribution methods like KernelSHAP become
> necessary only when $n$ pushes enumeration intractable. The
> full power-set design also avoids the well-known leave-one-out
> blind spot to interaction effects (Li & Janson 2024): with six
> interacting weights, only joint perturbation can distinguish
> weights that are individually redundant but jointly
> indispensable."

**Reviewer-objection defence ("just do LOO"):**
1. Methodological precedent for full subsets (Saisana/Saltelli,
   Lundberg/Lee, Štrumbelj/Kononenko).
2. Computational justification ($n=6$, ~1.7s - LOO offers no
   real cost saving).
3. Statistical justification (LOO misses interaction effects;
   factorial / power-set is the only way to detect them - Li &
   Janson 2024).

**Honest contribution claim:** "Novel application of established
interpretability / sensitivity-analysis ablation methodology to
refactoring-trajectory metrics." Don't claim methodological
invention.

### Caveat on Shapley framing

Cite Lundberg & Lee as the canonical link between power-set
enumeration and interpretability, but **do not claim the §5.2
ablation *is* a Shapley computation** unless the subset
contributions are explicitly weighted by the Shapley kernel
$\tfrac{|S|!(|N|-|S|-1)!}{|N|!}$. The current setup computes
mean recovery per active-count level and mean τ-b per term, i.e.
marginal-contribution-style summaries, not formal Shapley
values. Honest framing: "power-set enumeration in the
Shapley-attribution tradition" rather than "Shapley-value
attribution."

### Bib entries to add to `bibs/sample.bib`

Three primary entries needed:
- `saisana2005composite` - Saisana, Saltelli & Tarantola,
  "Uncertainty and sensitivity analysis techniques as tools for
  the quality assessment of composite indicators", JRSS-A
  168(2):307-323, 2005 (`@article`).
- `lundberg2017shap` - Lundberg & Lee, "A Unified Approach to
  Interpreting Model Predictions", NeurIPS 2017 (`@inproceedings`).
- `strumbelj2014explaining` - Štrumbelj & Kononenko, "Explaining
  prediction models and individual predictions with feature
  contributions", K&IS 41(3):647-665, 2014 (`@article`, DOI
  10.1007/s10115-013-0679-x). Optional alternate: cite the 2010
  JMLR predecessor for sampling-approximation framing.

Optional secondary entries:
- `oecd2008handbook` - OECD/JRC Handbook on Constructing
  Composite Indicators, 2008 (`@techreport`).
- `li2024optimalablation` - Li & Janson, "Optimal ablation for
  interpretability", arXiv:2409.09951, 2024 (`@misc` or
  `@unpublished`) - cite if §5.2 includes LOO-defence paragraph.
- `ulan2022weighted` - Ulan, Löwe, Ericsson & Wingkvist,
  "Weighted software metrics aggregation and its application to
  defect prediction", EMSE 26(86), 2022 (`@article`) - cite for
  "alternative direction SE literature took on weighted
  aggregation."

**Bib correction (must do before chapter ships):** re-read the
current Verdecchia entry against the actual PeerJ CS 2022 paper.
If the title in the bib doesn't match, fix it. Then re-read the
methodology chapter's Verdecchia reference and adjust the
surrounding prose to match what the paper actually claims (it
does NOT critique composite weighting; it evaluates a composite
metric with equal weights and defers weight-sensitivity to
future work).

Verify DOIs and URLs before committing during the bibliography
audit (item #8 in `PLAN-thesis-writeup.md`).

---

## Research-agent results: Prompt R3 (completed 2026-05-19)

Web-search agent run for the refactoring-step-reordering
literature. Verdict: **literature is genuinely thin and largely
assumes ordering matters without measuring it**. Three load-
bearing citations identified - one formal-theory (the deeper
commutation paper from the Mens line), one foil (smell-resolution
ordering, which is premised on non-commuting interactions), and
one direct empirical predecessor (toy-fixture scale). **No
published null result exists; §5.4 stands as a novel empirical
observation, not a contribution to an ongoing debate.**

### Executive summary

The literature on refactoring-step ordering effects is sparse:

- **Formal-theory side.** Mens, Taentzer & Runge (2007, SoSyM)
  formally established parallel independence (commutation) for
  refactorings via critical-pair analysis on graph-transformation
  specifications. Theory is well-developed but rarely connected
  to empirical outcome metrics on real refactor logs.
- **Search-based refactoring.** Harman & Tratt 2007, Mkaouer et
  al. 2016, Mohan & Greer 2019 (all already in the bib) optimise
  a *terminal Pareto front*. Path through search space is treated
  as a means, not as an outcome of interest. The closest the SBR
  line gets to ordering is search-space pruning via
  commutative-path elimination (Liu et al. COMPSAC 2008).
- **Direct empirical side.** Khrishe & Alshayeb 2016 is the only
  paper that asks the question head-on - and it's a toy-fixture
  study (one nine-method C# class, 3 refactorings, 6 orderings,
  no AST-equivalence check on terminal states).
- **No published null result.** No paper of the form "we tried to
  show refactoring ordering matters for outcome metrics and
  failed." §5.4 is the first such empirical observation.

### Citation 1 (STRONG, fits c with implications for b) - Mens, Taentzer & Runge 2007

**Citation.** Mens, T., Taentzer, G., and Runge, O. "Analysing
refactoring dependencies using graph transformation." *Software
and Systems Modeling (SoSyM)*, vol. 6, no. 3, pp. 269-285, 2007.
DOI: 10.1007/s10270-006-0044-6.

**Relevance.** The foundational formal-theory paper on this
question. Specifies refactorings as graph transformation rules
and uses **critical pair analysis** and **sequential dependency
analysis** to identify, at an abstract specification level, when
two refactorings are *parallel independent* (commute, producing
the same terminal graph regardless of order) versus when they
are in conflict or sequentially dependent. Defines the
conflict-essence-empty condition as parallel independence;
discusses preferred orderings *only* when refactorings interact.
**Contains no empirical measurement of metric outcomes across
orderings on real codebases** - which is the gap §5.4 fills.

**Category fit.** (c) primary; (b) implications.

**Load-bearing strength.** **Strong.** The citation that grounds
the §5.4 mechanism claim. Chapter framing: "Mens, Taentzer & Runge
formally established the conditions under which two refactorings
are parallel-independent (commute) using critical pair analysis
on graph-transformation specifications; the standard IDE refactor
catalogue is, by design, dominated by such parallel-independent
pairs." §5.4 then becomes the first empirical confirmation that
when the commutation precondition is satisfied (as it is in
IDE-recorded refactor sessions), ordering produces no measurable
score-formula delta - exactly what the theory predicts but which
no one had previously verified empirically on real refactor traces.

**Important.** This is **distinct from Mens & Tourwé 2004** (the
survey, already in the bib, which mentions commutativity only in
passing). Cite the 2007 paper, not the 2004 survey, for the
commutation claim.

**URLs.**
- SoSyM: https://link.springer.com/article/10.1007/s10270-006-0044-6
- Author preprint PDF: https://orbi.umons.ac.be/bitstream/20.500.12907/19487/1/Mens-2007-02-SOSYM.pdf

### Citation 2 (STRONG as foil, fits a + b) - Liu, Yang, Niu, Ma & Shao 2009

**Citation.** Liu, H., Yang, L., Niu, Z., Ma, Z., and Shao, W.
"Facilitating software refactoring with appropriate resolution
order of bad smells." *Proceedings of the 7th Joint Meeting of
the European Software Engineering Conference and the ACM SIGSOFT
Symposium on the Foundations of Software Engineering (ESEC/FSE
'09)*, pp. 265-268, 2009. DOI: 10.1145/1595696.1595738.

**Relevance.** The canonical "ordering matters" assertion in the
search-based / smell-driven refactoring line. Argues that
"different resolution orders of the same set of bad smells may
require different effort, and/or lead to different quality
improvement" and recommends a preferred ordering. The argument
is **interaction-driven**: resolving one smell changes which other
smells are present, i.e. it is fundamentally a **non-commuting**
setup. Companion: Liu, Li, Ma & Shao, "Conflict-aware schedule of
software refactoring," *IET Software* 2(5):446-460, 2008, which
models the problem as multi-objective scheduling where the
conflict matrix between refactorings drives the gain from
ordering.

**Category fit.** (a) primary; (b) secondary.

**Load-bearing strength.** **Strong as a foil.** The textbook
"ordering matters" claim §5.4 positions *against*. The framing
move: Liu et al. argue ordering matters *because*
refactoring-bad-smell pairs interact (one resolution mutates the
smell graph for the next). §5.4's commuting-window setting is
exactly the *complement* of theirs - it measures precisely the
regime they exclude by construction. Citing them as the
"consensus that ordering matters" reference and then noting that
the consensus is *premised on non-commuting interactions* lets
§5.4 frame the finding as: *"In the commuting regime - the
dominant regime in IDE refactor logs - the Liu et al. effect
vanishes, exactly as the Mens et al. theory predicts."*

**URLs.**
- ACM DL: https://dl.acm.org/doi/10.1145/1595696.1595738
- Companion IET Software 2008: https://digital-library.theiet.org/doi/10.1049/iet-sen%3A20070033

### Citation 3 (MODERATE, fits a; close to d) - Khrishe & Alshayeb 2016

**Citation.** Khrishe, Y., and Alshayeb, M. "An empirical study
on the effect of the order of applying software refactoring."
*7th International Conference on Computer Science and Information
Technology (CSIT 2016)*, Amman, Jordan, IEEE, 2016. DOI:
10.1109/CSIT.2016.7549471.

**Relevance.** **The single existing empirical paper that
directly asks the question "does the order of refactorings
change the resulting quality metrics?"** Tiny study (one
nine-method C# class, three refactoring techniques, six
orderings, six standard size/cohesion/coupling metrics). Reports
that different orderings produce different metric values. **Does
not separate commuting from non-commuting orderings, does not
use AST equivalence to check whether terminal states actually
differ, does not enumerate the full permutation set, magnitudes
are not characterised to permit effect-size extrapolation.**

**Category fit.** (a) primary; very close to (d) once its design
limitations are exposed.

**Load-bearing strength.** **Moderate, but uniquely valuable for
§5.4.** The only prior paper that empirically tests the exact
question. The §5.4 framing critiques its design: (i) doesn't
check whether the refactorings under permutation actually commute
on the AST, (ii) one-class toy fixture rather than recorded
developer sessions, (iii) doesn't enumerate the full permutation
set or report effect magnitudes that permit comparison. §5.4 is
the first to do the experiment at scale (225 enumerated
permutations across 45 sessions; 29 distinct reorder windows;
terminal-AST checks). The defensible framing: *"the only prior
empirical attempt found 'an effect' on a single nine-method class
without checking AST equivalence; at session scale with commuting
windows the score-formula delta is zero."*

**Note on venue.** CSIT is a smaller venue than the ICSE/FSE/etc.
constraint list. Keep this citation specifically *because* its
small scale is part of the contribution narrative.

**URL.** https://ieeexplore.ieee.org/document/7549471/

### Honest negative result (for §5.4 framing paragraph)

The honest finding:

- **Formal-theory side.** Mens, Taentzer, Runge (2007) and the
  broader graph-transformation / critical-pair line (Bottoni,
  Parisi-Presicce, Taentzer; Biermann et al. on parallel
  independence of amalgamated graph transformations) do
  characterise when two refactorings commute. **That theory is
  well-developed but is almost never connected to empirical
  outcome metrics on real refactor logs** - the formal community
  certifies the commutation property, the empirical community
  measures aggregate refactoring quality impact, and the two
  literatures rarely meet.
- **Search-based-refactoring side.** Harman & Tratt 2007, Mkaouer
  et al. 2016, Mohan & Greer 2019, Ouni & Kessentini and
  successors all optimise a *terminal Pareto front*. The path
  through that space is treated as a means, not an outcome of
  interest. The closest the SBR line gets to ordering is
  search-space pruning via commutative-path elimination - e.g.
  **Liu et al., "Searching for opportunities of refactoring
  sequences: reducing the search space" (IEEE COMPSAC 2008)
  explicitly removes commutative paths like Rename Method + Pull
  Up Method *because they produce identical terminal results*** -
  which is itself supporting evidence for the §5.4 framing, but
  it is used there as a *search-space optimisation* rather than
  as an empirical finding about real developer sessions.
- **Direct empirical side.** Khrishe & Alshayeb 2016 (above) is
  the only paper that asks the question head-on; it's a
  toy-fixture study. Large-scale refactoring-impact literature
  (Bavota et al., Bibiano et al., AbuHassan 2024 "Software
  refactoring side effects", Hamdi et al. 2021, Chávez et al. on
  refactoring impact on smells) studies the *what* (which
  refactorings are applied) and the *aggregate* metric delta,
  but does not enumerate alternative orderings on the same
  window and re-score - so no direct null result exists in the
  literature.
- **No published null result.** No paper of the form "we tried
  to show refactoring ordering matters for outcome metrics and
  failed." **§5.4 can honestly stand as the first such
  observation.**

### Useful supporting context (optional)

- **Liu et al. COMPSAC 2008.** "Searching for opportunities of
  refactoring sequences: reducing the search space." Removes
  commutative paths because they produce identical terminal
  results - exact corroborating evidence for §5.4's mechanism
  claim, but used as a *search-space optimisation* rather than
  as an empirical finding about real sessions. Cite as
  supporting context if §5.4 wants the corroboration.
  URL: https://ieeexplore.ieee.org/document/4591575/
- **Bibiano et al. ICSME 2021.** "Look Ahead! Revealing Complete
  Composite Refactorings and their Smelliness Effects." Composite-
  refactoring framing; relevant background but not directly on
  ordering. Optional related-work mention.
- **AbuHassan 2024.** "Software refactoring side effects." J.
  Softw. Evol. Proc. General background on mixed-effect
  refactoring outcomes. Optional.

### Recommended §5.4 framing paragraph

The strongest paragraph that this literature supports:

> "The mechanism of the §5.4 result is well-established in theory:
> Mens, Taentzer & Runge (2007) formally characterise refactorings
> as graph transformations whose parallel independence
> (critical-pair-empty) implies that any permutation reaches the
> same terminal graph. IDE-emitted refactorings are, by
> construction, dominated by parallel-independent pairs - a fact
> already exploited as a search-space pruning rule by Liu et al.
> (2008, COMPSAC). Empirical work on ordering, however, has
> either argued for an order effect on the basis of
> *non-commuting* smell interactions (Liu et al., ESEC/FSE 2009;
> Liu et al., IET Software 2008) or has tested the question only
> on a single nine-method fixture without an AST-equivalence
> check on the terminal state (Khrishe & Alshayeb, CSIT 2016).
> The 45-session, 225-permutation result reported here is, to our
> knowledge, the first systematic empirical evidence that on real
> IDE-recorded refactor sessions the commuting regime dominates
> and the resulting score-formula gain from reordering is, in
> practice, zero."

Supportable, accurate, and does not overclaim. Establishes
novelty without claiming contribution-to-debate.

### Bib entries to add to `bibs/sample.bib`

Three primary entries:
- `mens2007graph` - Mens, Taentzer & Runge, "Analysing
  refactoring dependencies using graph transformation", SoSyM
  6(3):269-285, 2007 (`@article`, DOI 10.1007/s10270-006-0044-6).
  **Important: distinct from Mens & Tourwé 2004 survey already
  in the bib.**
- `liu2009smell` - Liu, Yang, Niu, Ma & Shao, "Facilitating
  software refactoring with appropriate resolution order of bad
  smells", ESEC/FSE 2009, pp. 265-268 (`@inproceedings`, DOI
  10.1145/1595696.1595738).
- `khrishe2016empirical` - Khrishe & Alshayeb, "An empirical
  study on the effect of the order of applying software
  refactoring", CSIT 2016, IEEE (`@inproceedings`, DOI
  10.1109/CSIT.2016.7549471).

Optional supporting entries:
- `liu2008scheduling` - Liu, Li, Ma & Shao, "Conflict-aware
  schedule of software refactoring", *IET Software* 2(5):446-460,
  2008 (`@article`) - companion to liu2009smell; cite if §5.4
  wants both the smell-ordering claim and the scheduling
  formulation.
- `liu2008compsac` - Liu et al., "Searching for opportunities of
  refactoring sequences: reducing the search space", IEEE
  COMPSAC 2008 (`@inproceedings`) - SBR commutative-path pruning;
  the supporting-corroboration cite for §5.4's mechanism.

Verify DOIs and URLs during the bibliography audit (item #8 in
`PLAN-thesis-writeup.md`).

---

## All three results-chapter citation hunts complete

Status of the three load-bearing research-agent prompts for the
results chapter:

- **R1** (Kendall τ-b methodology) - **complete**;
  3 citations (Kendall 1948 mandatory, Agresti 2010 companion,
  Croux & Dehon 2010 for τ-over-ρ defence) +
  confident SE-negative-result framing.
- **R2** (Power-set / Shapley ablation) - **complete**;
  3 citations (Saisana 2005 sensitivity-analysis precedent,
  Lundberg & Lee 2017 SHAP, Štrumbelj & Kononenko 2014) +
  important Verdecchia bib correction to resolve before ship.
- **R3** (Refactoring-step reordering) - **complete**;
  3 citations (Mens/Taentzer/Runge 2007 formal commutation theory,
  Liu et al. ESEC/FSE 2009 as foil, Khrishe & Alshayeb 2016 as
  toy-fixture empirical predecessor) + confirmed §5.4 stands as
  first systematic empirical observation.

**R4** (Cohen's κ) is study-blocked - run only when Phase 1 of
the user study lands. **R5** (top-N hit rate) is low-priority
and can be skipped if §5.1's τ-b/Spearman framing is sufficient.

Total new primary bib entries inventoried across all three
prompts: **9** (3 + 3 + 3). Plus the **Verdecchia 2022 bib
correction** which is its own action item.

---

## Research-agent results: Prompt R4 (completed 2026-05-19)

Web-search agent run for Cohen's κ in SE inter-rater agreement.
Verdict: **canonical citations confirmed; multi-label κ on
refactoring labelling specifically is methodologically uncommon
in empirical SE**. Two primary citations + a bonus threats-to-
validity reference + an important small-n caveat to surface in
the chapter.

Note: this prompt was originally flagged as "study-blocked - run
only when Phase 1 of the user study lands." Running it now lets
the bib entries land in the audit pass before the chapter ships;
the κ numbers themselves remain study-blocked.

### Executive summary

The two methodological references are both canonical and
load-bearing exactly as nominated:

- **Cohen (1960)** is the origin of κ.
- **Landis & Koch (1977)** is the source of the qualitative
  interpretation bands (poor / slight / fair / moderate /
  substantial / almost perfect) that essentially every empirical
  SE paper cites verbatim.

**Honest negative result for SE multi-label κ:** the SE
community routinely applies κ to *single-label* refactoring
intent / code-smell classification, but **per-kind κ across
independent human raters on a multi-label refactoring schema is
uncommon**. The recent multi-label code-smell line (Nature
Scientific Data 2025 "SmellyCode++"; SN Computer Science 2025)
builds *datasets* via multi-label annotation but does not
foreground per-kind κ as the reliability measure.

The closest SE-side methodological reference for the small-n /
agreement-reliability concern is **Gonzalez-Prieto et al.
(JSS 2023)** - useful for threats-to-validity but not for the
results chapter itself.

### Citation 1 (STRONG, fits a) - Cohen 1960

**Citation.** Cohen, J. (1960). "A coefficient of agreement for
nominal scales." *Educational and Psychological Measurement*,
20(1), 37-46. DOI: 10.1177/001316446002000104.

**Relevance.** Defines κ as the chance-corrected agreement
coefficient for two raters labelling items into nominal
categories. The foundational paper everyone cites when
introducing κ. Does NOT define the qualitative bands - those
come from Landis & Koch.

**Category fit.** (a) primary.

**Load-bearing strength.** **Strong.** The citation; there is no
substitute. Cite as-is.

**URLs.**
- SAGE: https://journals.sagepub.com/doi/10.1177/001316446002000104
- Semantic Scholar: https://www.semanticscholar.org/paper/A-Coefficient-of-Agreement-for-Nominal-Scales-Cohen/9e463eefadbcd336c69270a299666e4104d50159

### Citation 2 (STRONG, fits b) - Landis & Koch 1977

**Citation.** Landis, J. R., & Koch, G. G. (1977). "The
measurement of observer agreement for categorical data."
*Biometrics*, 33(1), 159-174. DOI: 10.2307/2529310.

**Relevance.** Introduces the qualitative interpretation table
that is essentially universal in empirical work: <0.00 poor;
0.00-0.20 slight; 0.21-0.40 fair; 0.41-0.60 moderate;
0.61-0.80 substantial; 0.81-1.00 almost perfect. Also
generalises κ-type statistics for multivariate categorical
data, which is technically the closest classical precedent for
a "per-kind κ across a multi-label schema" treatment - though
the L&K formulation is multi-category, not multi-label in the
modern ML sense.

**Category fit.** (b) primary.

**Load-bearing strength.** **Strong.** Cite as-is. The bands are
the de-facto standard SE empirical convention; no evidence the
SE community has adopted a different qualitative band
convention. SE papers vary in where they place the "acceptable"
floor (some take >0.6 substantial as the floor, others take
>0.4 moderate), but they all cite L&K.

**URLs.**
- PubMed: https://pubmed.ncbi.nlm.nih.gov/843571/
- Semantic Scholar: https://www.semanticscholar.org/paper/The-measurement-of-observer-agreement-for-data.-Landis-Koch/7e7343a5608fff1c68c5259db0c77b9193f1546d

### Bonus citation (MODERATE, for threats-to-validity only) - Gonzalez-Prieto et al. 2023

**Citation.** Gonzalez-Prieto, A., Perez, J., Diaz, J., &
Lopez-Fernandez, D. (2023). "Reliability in software engineering
qualitative research through Inter-Coder Agreement." *Journal of
Systems and Software*, 202, 111707. DOI:
10.1016/j.jss.2023.111707.

**Relevance.** An SE-community guide on inter-coder agreement
for qualitative coding. Argues that **Krippendorff's α is in
many cases more appropriate than Cohen's κ** for SE qualitative
research, particularly when categories are not equiprobable and
when data are sparse - both of which apply to a 6-session,
multi-label, kind-imbalanced setup.

**Category fit.** (c) primary - methodological, not refactoring-
specific.

**Load-bearing strength.** **Moderate for threats-to-validity.**
The honest disclosure move: *"We report κ per kind following
Cohen (1960) / Landis & Koch (1977); Gonzalez-Prieto et al.
(2023) note that Krippendorff's α would be a stronger choice in
our setting, which is a limitation."*

**URLs.**
- JSS / ScienceDirect: https://www.sciencedirect.com/science/article/pii/S0164121223001024
- arXiv preprint: https://arxiv.org/abs/2008.00977

### Honest negative result (for threats-to-validity framing)

**Multi-label κ on refactoring labels specifically is uncommon
in empirical SE.** What exists:

- **Multi-label code-smell line (informational only).** Nature
  Scientific Data 2025 "SmellyCode++" and SN Computer Science
  2025 do multi-label *classification* of code smells but
  evaluate via classifier F1 against pre-existing labels, not
  via per-kind κ across two independent human raters. They
  confirm that multi-label labelling is a recognised paradigm
  for class-level smell detection, but do NOT exemplify
  "per-kind κ on multi-label refactoring trajectories."
- **AlOmar / Mkaouer refactoring-classification line
  (informational only).** AlOmar et al. arXiv:2009.09279 on
  Self-Affirmed Refactoring classifies refactoring commit
  messages, typically *single-label* per artefact, validated by
  manual inspection. The natural SE precedent for "refactoring
  classification with human-rater validation" but does not
  match the multi-label structure.

**Honest framing:** "The combination 'per-kind Cohen's κ across
two raters on a multi-label refactoring schema' is
methodologically uncommon in SE. The SE community typically does
(i) single-label κ on refactoring classification, or (ii)
classifier-F1 evaluation on multi-label code-smell datasets.
Treating each kind as an independent binary κ is statistically
defensible and well-understood, but the literature does not
provide a directly comparable precedent."

State this explicitly in threats-to-validity - it strengthens,
rather than weakens, the chapter.

### Small-n caveat (IMPORTANT - surface in chapter)

**With n=6 sessions, κ has very wide confidence intervals.** If
the chapter reports only point estimates without CIs, this is a
more important threat than the κ-vs-α choice. The threats-to-
validity discussion should note:

- κ point estimates on n=6 are highly unstable; a single
  rater-disagreement shifts the per-kind κ substantially.
- Report bootstrap CIs alongside point estimates if computable,
  or explicitly flag the small-n instability if not.
- Combine with the Gonzalez-Prieto citation for the
  acknowledged-better-alternative (Krippendorff α) note.

### Floor-convention guidance

No single accepted κ floor in SE. Different empirical papers
declare >0.4 (moderate, the L&K floor), >0.6 (substantial), or
>0.8 (almost perfect) as their acceptance threshold. **The most
defensible choice for a small-n exploratory study is >0.4
(moderate) per kind as a minimum, with substantial (>0.6)
flagged as the target.** Cite Landis & Koch for the band labels,
not for the floor itself.

### Recommended chapter framing

- **In §5.3 (or wherever the user-study κ numbers are
  reported):** cite Cohen 1960 when first introducing κ; cite
  Landis & Koch 1977 when reporting the qualitative band that
  the per-kind κ falls into.
- **In threats-to-validity:** add Gonzalez-Prieto 2023 as a
  footnote acknowledging Krippendorff α would be the stronger
  choice. Pair with the small-n CI caveat.

### Bib entries to add to `bibs/sample.bib`

Two primary entries:
- `cohen1960kappa` - Cohen, J., "A coefficient of agreement for
  nominal scales", *Educational and Psychological Measurement*
  20(1):37-46, 1960 (`@article`, DOI 10.1177/001316446002000104).
- `landis1977kappa` - Landis & Koch, "The measurement of
  observer agreement for categorical data", *Biometrics*
  33(1):159-174, 1977 (`@article`, DOI 10.2307/2529310).

Optional secondary entry (for threats-to-validity chapter):
- `gonzalez2023intercoder` - Gonzalez-Prieto, Perez, Diaz &
  Lopez-Fernandez, "Reliability in software engineering
  qualitative research through Inter-Coder Agreement", JSS 202,
  111707, 2023 (`@article`, DOI 10.1016/j.jss.2023.111707) -
  cite only if threats-to-validity acknowledges the κ-vs-α
  debate.

Verify DOIs and URLs during the bibliography audit (item #8 in
`PLAN-thesis-writeup.md`).

---

## Research-agent results: Prompt R5 (completed 2026-05-19)

Web-search agent run for the top-N hit rate / top-k overlap
citation hunt. Verdict: **one primary citation (Webber 2010)
dual-purposes cleanly with R1**, plus an optional secondary
structural-analogue citation; SE-empirical defence of top-N over
τ is sparse and not needed.

### Executive summary

**Webber, Moffat & Zobel (2010) is the right primary citation
for R5** - and the framing pivot is legitimate. R1 already
flagged this paper as a "considered alternative" cite (RBO not
used because rankings are definite). For R5 the angle is
different: the paper's foundational argument is that **top-k
overlap measures exist *because* full-ranking correlations (τ,
ρ) are inappropriate when high ranks should be weighted more
heavily than low ranks, when only a prefix is observable, or
when rankings are short**. Same paper, different load-bearing
claim - both citations are defensible and not redundant.

**On the SE-empirical side: sparse, as expected.** Defect-
prediction and test-prioritisation work uses top-N hit rate and
uses τ/ρ, but I found no ICSE/FSE/TSE/EMSE paper that explicitly
defends top-N over τ on small-N grounds. The closest structural
match is **Oh et al. (CIKM 2022) "Rank List Sensitivity..."**,
a recommender-systems sensitivity-analysis paper that uses top-k
overlap as the stability measure under input perturbations -
near-perfect methodology match but wrong venue.

**Recommendation: Webber 2010 alone is sufficient.** The IR
convention is well-established; SE precedent isn't needed.

### Citation 1 (STRONG primary, fits a + c) - Webber, Moffat & Zobel 2010

**Citation.** Webber, W., Moffat, A., & Zobel, J. (2010). "A
Similarity Measure for Indefinite Rankings." *ACM Transactions
on Information Systems*, 28(4), Article 20, pp. 20:1-20:38.
DOI: 10.1145/1852102.1852106.

**Relevance.** Foundational argument: a similarity measure for
rankings should (i) handle non-conjointness, (ii) weight high
ranks more heavily than low ranks, and (iii) be monotonic with
increasing evaluation depth - and **Kendall τ and Spearman ρ
fail all three** because they treat misalignments at every
depth equally and require fully-conjoint domains. The canonical
published defence of top-weighted ranking similarity over
full-ranking correlation coefficients.

**Category fit.** (a) primary; (c) secondary.

**Load-bearing strength.** **Strong.** ~3000+ citations on
Google Scholar; the standard IR/ML/recsys reference.

**R1 vs R5 framing - same paper, different load-bearing
claims:**
- **R1 cite framing:** "We considered RBO but our per-fixture
  rankings are definite (every fixture has a finite, fully-
  enumerated DP list), so full-ranking τ-b suffices."
- **R5 cite framing:** "Even with definite rankings, when N is
  small or the user only sees the top, full-ranking τ-b is the
  wrong stability signal - top-N overlap is what the IR
  literature uses for top-weighted stability measures."

The paper supports both claims directly; the framing distinction
must be explicit in the surrounding prose so a reader doesn't
read it as a copy-paste cite.

**URLs.**
- ACM canonical: https://dl.acm.org/doi/10.1145/1852102.1852106
- Open PDF: https://www.williamwebber.com/research/papers/wmz10_tois.pdf
- Moffat author page: https://people.eng.unimelb.edu.au/ammoffat/abstracts/wmz10acmtois.html

### Citation 2 (MODERATE, optional) - Oh, Ustun, McAuley & Kumar 2022

**Citation.** Oh, S., Ustun, B., McAuley, J., & Kumar, S. (2022).
"Rank List Sensitivity of Recommender Systems to Interaction
Perturbations." *Proceedings of the 31st ACM International
Conference on Information & Knowledge Management (CIKM '22)*,
pp. 1584-1594. DOI: 10.1145/3511808.3557425.

**Relevance.** Defines Rank List Sensitivity (RLS) as a
recommender-system stability measure under input perturbations,
and operationalises it using **top-k overlap and RBO** rather
than full-ranking τ - on essentially the same methodological
grounds (top-of-list is what the user sees; full-ranking
correlations dilute the signal). **Structurally near-identical
to the sensitivity sweep:** perturb inputs (here: perturb
weights; there: perturb interactions), measure top-k stability
of the output ranking.

**Category fit.** (b) marginally (recsys, not SE); (c) more
strongly (post-RBO methodological precedent for choosing top-k
overlap over τ in a sensitivity-analysis context).

**Load-bearing strength.** **Moderate.** Right structural
analogue but wrong venue (CIKM, not ICSE/FSE/TSE) and applied
domain (consumer recsys, not SE). Cite only if the chapter wants
to demonstrate that "perturb input, measure top-k stability" is
an established methodology with peer-reviewed precedent. Drop
for a clean one-cite paragraph.

**URLs.**
- arXiv: https://arxiv.org/pdf/2201.12686
- ACM canonical: https://dl.acm.org/doi/10.1145/3511808.3557425
- UCSD author copy: https://cseweb.ucsd.edu/~jmcauley/pdfs/cikm22a.pdf

### Honest negative result (SE-empirical defence of top-N over τ)

I did not find a defect-prediction or test-prioritisation paper
that explicitly defends top-N hit-rate as a τ-replacement on
small-N stability grounds. Defect-prediction ranking literature
(Yang et al., D'Ambros et al. EMSE 2012, the effort-aware
sub-literature) reports τ/ρ *and* top-N, but does not argue
methodologically that one is preferable to the other when N is
small. The IR convention is well-established; SE precedent isn't
needed for the §5.1 caveat paragraph.

### Useful supporting context (not recommended as citation)

- **Fagin, Kumar & Sivakumar (2003).** "Comparing Top k Lists,"
  *SIAM J. Discrete Math* 17(1):134-160. The pre-RBO foundational
  reference for top-k distance measures. **Not recommended as
  the R5 primary** - it's a mathematics paper about metric
  properties of distance functions on top-k lists, not a
  methodological defence of why top-N is preferable to τ in
  evaluation. Webber 2010 is strictly the better R5 cite.
  URL: https://epubs.siam.org/doi/10.1137/S0895480102412856
- **Vigna (2015).** "A Weighted Correlation Index for Rankings
  with Ties," WWW 2015, DOI 10.1145/2736277.2741088.
  Weighted-τ variant. Not relevant to R5 since the chapter
  proposes top-N hit-rate, not a weighted τ.

### Recommended §5.1 framing

A single-citation caveat sentence that handles both R1 and R5
correctly:

> "We report top-5 hit rate alongside Kendall τ-b because τ-b on
> N ≤ 5 rankings is brittle, and because the user-facing
> dashboard surface only shows the top of the ranking: when only
> a prefix of the ranking is observed, top-weighted overlap
> measures are methodologically preferred to full-ranking
> correlations [Webber, Moffat & Zobel 2010]. We do not use rank-
> biased overlap itself because our per-fixture rankings are
> definite rather than indefinite."

This single sentence carries both the R1 framing ("RBO
considered, rankings are definite") and the R5 framing ("top-N
preferred over τ-b for small N / top-of-list contexts") using
the same citation, with the two claims kept distinct in the
prose.

### Bib entry to add to `bibs/sample.bib`

If `webber2010rbo` was deferred from R1 as optional, **promote
it to mandatory** for R5. Single shared entry:
- `webber2010rbo` - Webber, Moffat & Zobel, "A similarity
  measure for indefinite rankings", ACM TOIS 28(4), Article 20,
  2010 (`@article`, DOI 10.1145/1852102.1852106).

Optional secondary entry:
- `oh2022ranksensitivity` - Oh, Ustun, McAuley & Kumar, "Rank
  List Sensitivity of Recommender Systems to Interaction
  Perturbations", CIKM 2022, pp. 1584-1594 (`@inproceedings`,
  DOI 10.1145/3511808.3557425). Cite only if the §5.1 prose
  wants a peer-reviewed precedent for the
  "perturb-input-measure-top-k-stability" methodology shape.

---

## All five citation hunts complete

Final status:

- **R1** (Kendall τ-b methodology) - **complete**.
- **R2** (Power-set / Shapley ablation) - **complete**;
  Verdecchia bib correction outstanding.
- **R3** (Refactoring-step reordering) - **complete**; §5.4
  framing supports first-empirical-observation novelty claim.
- **R4** (Cohen's κ) - **complete**; small-n CI caveat
  surfaced for threats-to-validity.
- **R5** (top-N hit rate) - **complete**; Webber 2010
  dual-purposes with R1.

Total new primary bib entries across the five completed
prompts: **11**, since the Webber 2010 entry from R5 is shared
with R1's optional-secondary slot. Specifically:

| Prompt | Primary entries |
|--------|----------------:|
| R1 τ-b | 3 (Kendall, Agresti, Croux & Dehon) |
| R2 ablation | 3 (Saisana, Lundberg & Lee, Štrumbelj) |
| R3 reordering | 3 (Mens 2007, Liu 2009, Khrishe 2016) |
| R4 κ | 2 (Cohen, Landis & Koch) |
| R5 top-N | 1 shared (Webber 2010 - shared with R1's optional) |
| **Total distinct primary** | **11** |

Plus optional secondaries from R2 (OECD handbook, Li & Janson
2024, Ulan 2022), R3 (Liu 2008 IET, Liu 2008 COMPSAC), R4
(Gonzalez-Prieto 2023), and R5 (Oh 2022).

Plus the **Verdecchia 2022 bib correction** action item: re-read
the existing bib entry against the actual PeerJ CS 2022 paper;
adjust methodology §3.3 prose if the surrounding framing implies
a critique of composite weighting that the actual paper does not
support.

The experiments chapter is now ready for the writeup phase
whenever you want to fire that agent.
