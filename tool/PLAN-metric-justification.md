ALSO: make general code heigeine weights + add process step cost (for relative process score between trajectories)
---
# Metric justification: two-layer methodology

Addresses §2 / §3 of `HONEST_REVIEW.md` ("metrics fully backed by
research") without faking citations for a contribution that is, in
fact, novel.

## The split

Methodology chapter is two clearly-labelled layers:

### Layer 1 — Cleanliness (literature-backed)

Every sub-signal is selected on literature grounds; per-signal raw
extraction, trajectory-touched-set aggregation, citations, and
limitations live in
[`RESEARCH-cleanliness-metrics.md`](RESEARCH-cleanliness-metrics.md).
Sub-signals covered: cohesion (Bieman & Kang 1995's TCC), coupling
(Chidamber & Kemerer 1994's CBO), smells (Marinescu 2004 detection
strategies via PMD), duplication (Heitlager et al. 2007 / CPD),
readability (Buse & Weimer 2010 feature taxonomy), and cognitive
complexity (Campbell 2018).

The *composite* (range-normalised weighted average over six
sub-signals × 100) is a design choice grounded in the Quamoco
(Wagner et al. 2015) and QMOOD (Bansiya & Davis 2002) precedents.
Within-composite weights are uniform (`1/6` each) under Laplace's
principle of insufficient reason in the absence of an in-domain
calibration corpus.

### Layer 2 — Process score (novel contribution)

Frame explicitly: "no prior metric evaluates a *trajectory* of
refactorings against process quality; we propose one." Lit review
already supports this gap (endpoint-only / single-step metrics
dominate: Harman/Tratt, ReSynth, Refactoring Navigator, etc.).

Each weighted term justified by a stated **axiom**, not a citation:

- **W_BROKEN (28)** — must dominate any one-step gain so users can't
  profit from leaving the build broken. *Anti-gaming axiom.*
- **W_SKIP_TESTS (14)** — half W_BROKEN; skipping tests is weaker
  evidence of bad process than failing them. *Severity-ordering axiom.*
- **W_COMMIT_GAP (7)** — half W_SKIP_TESTS; failing to commit at
  reasonable intervals is process-hygiene ergonomics (review and
  rollback cost), weaker than skipping tests but still bad practice.
  Completes the 4:2:1 hygiene ratio (W_BROKEN:W_SKIP_TESTS:W_COMMIT_GAP
  = 28:14:7). *Severity-ordering axiom (extended).*
- **W_GAIN (50) vs W_DEGRADATION (21)** — asymmetric. State the
  axiom explicitly (e.g. "reward sustained gains; tolerate transient
  dips that later refactoring absorbs"). Optional: gesture at
  Paixão's disruption-vs-improvement framing as analogous, not
  derivative.
- **W_SMELL (21)** — bounded by W_DEGRADATION so the smell ledger
  can't dominate the static cleanliness signal it derives from.
  *Non-double-counting axiom.*

## Sensitivity analysis (closes the loop)

For each weight $w \in \{W_\text{BROKEN}, W_\text{SKIP\_TESTS},
W_\text{COMMIT\_GAP}, W_\text{GAIN}, W_\text{DEGRADATION}, W_\text{SMELL}\}$:

1. Vary $w$ by $\pm 50\%$ across N fixture trajectories.
2. Measure: does the top-K divergence-point ranking change?
3. Report: per-weight rank-stability under perturbation.

If top-K is stable under $\pm 50\%$ on most weights → defensible.
If unstable on one weight → either re-pick that weight from an
explicit principle, or call it out as a known limitation.

## Why this is a stronger position than "every weight has a citation"

The marker reads:

> "Novel composite metric for trajectory evaluation. Each constituent
> signal is literature-grounded. Weights derived from explicitly
> stated axioms (anti-gaming, severity ordering, non-double-counting).
> Robustness demonstrated empirically via sensitivity analysis."

That's textbook research-grade methodology. "Fully backed by research"
in the rubric sense doesn't mean "every number copied from a paper";
it means "no arbitrary choices left unjustified". Axioms + sensitivity
counts as justification.

## What to avoid

- **Faking citations** for Layer 2. Don't cite Paixão *as the source*
  of the W_GAIN/W_DEGRADATION asymmetry — cite as analogous prior
  framing, then state the axiom.
- **Overselling novelty.** "To our knowledge no prior metric evaluates
  refactoring *trajectories* in this way" — measured, supported by the
  lit review's existing gap argument.
- **Leaving weights unstated.** Every magnitude in `ProcessScore`
  must appear in the methodology chapter alongside its axiom.

## Files touched (writeup only)

- `paper/chapters/methodology.tex` — new chapter, two-layer structure.
- `paper/chapters/evaluation.tex` — sensitivity-analysis subsection.
- `paper/refs.bib` — add Campbell 2018, Buse & Weimer 2010 if missing
  (Chidamber-Kemerer, Scalabrino, Arcelli-Fontana, Paiva, Paixão,
  Harman/Tratt already present).

## Out of scope

- Re-deriving weights from a large-corpus empirical study. Time
  doesn't allow; sensitivity analysis on fixtures is the affordable
  substitute.
- Replacing the composite with a learned model. The axiomatic
  formulation is the *contribution*; a learned model would obscure
  it.
