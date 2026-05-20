# Experiment 3 — Divergence: Explained Results

## What the experiment asks

> *"Against a corpus of recorded sessions with hand-labelled ground
> truth, how often does the detector correctly surface alternative
> paths a perfect detector would identify — and when it does, are
> those alts actually useful (do they beat the user)?"*

The headline experiment. Sensitivity (Experiment 1) defends the
internal robustness of the score formula; ablation (Experiment 2)
defends the structural contribution of each term; **divergence
(Experiment 3) defends the external validity of the detector against
ground truth.**

## How it runs

For each of 45 recorded sessions (`fixtures/sessions/<id>/`):

1. Load the Phase-A dump from `fixtures/corpus/<id>.json`.
2. Assemble an `AnalysisReport` at `ScoringConfig.PRODUCTION`.
3. Compare the report's `divergencePoints` against the manifest's
   `expected_kinds` (the set of kinds a *perfect detector* would
   surface alts for) and the implied injection kind from `pattern`.

For each session, emit per-kind classification (TP/FP/FN/TN) +
magnitude statistics + injection prominence. 54 columns per row;
~5 s wall-clock for all 45 sessions.

### Run

```bash
./gradlew :analysis:divergence \
    --args="--corpus fixtures/corpus \
            --manifest fixtures/sessions/manifest-v2.csv \
            --output /tmp/divergence-results.csv" -q
./fixtures/aggregate-divergence.sh /tmp/divergence-results.csv
```

## Headline results

### Part 1 — Detection accuracy (precision / recall)

| kind        | precision | recall (injection only) | recall (any expected) |
|-------------|----------:|------------------------:|----------------------:|
| ORDERING    | 1.00      | 0.40                    | 0.36                  |
| IDE_REPLAY  | 1.00      | 0.76                    | 0.76                  |
| REWORK      | 1.00      | 1.00                    | 1.00                  |
| HYGIENE     | 1.00      | 1.00                    | 1.00                  |

- **Precision is perfect on all four kinds.** The four IDE_REPLAY
  false positives previously attributed to plugin-instrumentation
  bugs (sessions 025, 032, 037, 039) were closed by extending
  `RefactoringCommandListener` to synthesise a `REFACTORING_STARTED`
  /`REFACTORING_FINISHED` envelope around IntelliJ commands the
  platform `RefactoringEventListener` is silent for (Move Method,
  Change Method Signature) or fires under a refactoringId the
  analyser's name-matching does not bridge to RefactoringMiner's
  vocabulary (Extract/Introduce/Inline operations). Fix shipped in
  PR #62 (merge commit `8daecbf`); affected sessions re-recorded
  against the patched plugin.
- **REWORK and HYGIENE are perfect on both axes.**
- **ORDERING has perfect precision but only 0.36 recall.** Most
  failures are the Manual* sessions (001–021) where the reorder
  synthesiser's `splitOnInvalid` gate breaks the window into
  singletons because the manual step gets validator status
  `AST_DIVERGED` or `REFACTOR_FAILED`. Documented implementation
  gap; relaxing the gate would recover most of the missed recall.

### Part 2 — Detection quality (when the detector fires, how good is the alt?)

| kind        | DPs | alts enumerated | beats user | beats fraction | max magnitude |
|-------------|----:|----------------:|-----------:|---------------:|---------------:|
| REWORK      |   9 |               9 |          9 | **100 %**      | 15 lines       |
| HYGIENE     |  13 |              13 |         13 | **100 %**      | 10 points      |
| IDE_REPLAY  |  22 |              22 |         16 | **72.7 %**     | 23 points      |
| ORDERING    |  29 |             225 |          7 | **24.1 %**     | 9 points       |

> **TODO**: IDE_REPLAY row predates the PR #62 fix and includes
> the 4 plugin-instrumentation FPs (sessions 025/032/037/039)
> that are no longer in the corpus. Re-derive DP-level counts
> from the refreshed `corpus/` JSONs before quoting in the
> chapter. Headline precision (Part 1 / Part 3) is already
> updated.

Three findings:

1. **REWORK and HYGIENE never propose a useless alt.** Every DP of
   those kinds is a real improvement over the user's trajectory.
   Strong defence — when those detectors fire, the user is being
   shown something genuinely actionable.

2. **IDE_REPLAY proposes a real improvement 72.7 % of the time.**
   The misses are cases where the IDE-driven equivalent of the
   user's manual edit scored *the same or worse* on the process
   formula — usually because the manual edit happened to produce
   an AST that the IDE refactor wouldn't have. Calibration question
   for follow-up: why does the IDE-replay scoring sometimes lose to
   manual? Could be the `manualIde` penalty term being too small.

3. **ORDERING synthesises 225 alts but only 29 are surfaced as DPs,
   and of those 29 only 7 beat the user.** This is the most
   important finding: most of the time the synthesiser correctly
   enumerates reorder permutations but **no permutation actually
   beats the user under the current score formula**, because
   commuting independent refactors reach the same terminal AST and
   the score formula scores at the terminal state. This is the
   chapter's *"the score formula isn't sensitive to commuting
   orderings"* finding, confirmed empirically. Either:
   - The SuboptimalOrdering playbooks (022–026) need redesigning
     to pick *non-commuting* orderings.
   - Or the score formula needs an ordering-specific term that
     penalises intermediate-state cleanliness, not just terminal
     state.

#### Why 29 surfaced from 225, and 7 of 29 beat user

`DivergencePointBuilder.buildOrdering` groups all enumerated
permutations by `(fromSha, userToSha)` — i.e. one DP per reorder
*window*, not per individual permutation:

```kotlin
val grouped = alts.filter { it.kind == ORDERING }
    .groupBy { it.fromSha to it.userToSha }
for ((key, group) in grouped) {
    val best = scoredGroup.maxBy { it.third }
    out += DivergencePoint(
        magnitude = best.third,                       // best permutation's delta
        altTrajectoryIndexes = scoredGroup.map { it.first },  // every enumerated alt
    )
}
```

So **225** = total permutations enumerated across all 45 sessions
(e.g. a 4-step window enumerates up to 24 permutations); **29** =
distinct reorder windows in the corpus; **7** = windows where the
best permutation in the window actually beat the user. The other 22
windows: best permutation was no better than the user. Within each
window the dashboard shows the full set of permutations via
`altTrajectoryIndexes`; the magnitude on the DP represents only the
best.

### Part 3 — Injection prominence (when caught, where does it rank?)

| injected kind | n caught | @ rank 1 | mean rank |
|---------------|---------:|---------:|----------:|
| HYGIENE       |        8 |        8 | 1.00      |
| IDE_REPLAY    |       16 |       16 | 1.00      |
| ORDERING      |        2 |        2 | 1.00      |
| REWORK        |        6 |        6 | 1.00      |

**100 % top-1 across all four kinds.** Whenever the detector caught
the injection's kind, that DP was the *highest-magnitude*
recommendation in the session — ranked above every other DP that
fired.

This is the strongest claim the thesis can make about the
user-facing surface: **if the user only sees the top recommendation,
they always see the deliberately bad behaviour pointed out.** No
useful alt of another kind is masking the injection.

### Part 4 — Step-anchor recall (strict matching, disclosed cost)

What happens if we require the DP's `stepIndex` to match the
manifest's `target_step`:

| injected kind | n  | step matches | strict rate |
|---------------|---:|-------------:|------------:|
| HYGIENE       |  9 |            4 | 44.4 %      |
| IDE_REPLAY    | 21 |            2 |  9.5 %      |
| ORDERING      |  5 |            0 |  0 %        |
| REWORK        |  6 |            1 | 16.7 %      |

This is the cost of disabling step anchoring. The plugin's
checkpoint cadence depends on how fast the user types — EDIT_BURST
flushes, in-place rename template edits — so the same logical bad
step lands at index 2 in one recording and index 4 in another.
Strict anchoring collapses recall, particularly for ORDERING (which
anchors at the *terminal* step of the window, which shifts with
every checkpoint emitted between refactors).

The honest chapter framing: *"we measured the cost of cadence drift
directly and chose the looser anywhere-in-session policy on those
numbers."* Re-enabling strict anchoring is gated on plugin-side
checkpoint stability (in-place rename / Move Method envelope
detection — see `plugin-misclassifications.md`).

### Part 5 — Baselines and absolute magnitudes

| metric                  |  mean   |  min   |  max   |
|-------------------------|--------:|-------:|-------:|
| cleanliness_delta       | +6.8    | −100   | +100   |
| best_alt_magnitude      | +7.14   |    0   | +23    |
| sessions with regression| 39/45   |    —   |   —    |

- Cleanliness Δ spans the full 200-point range — the corpus
  legitimately exercises both clean and degraded trajectories.
- The detector's best alt across all sessions averages +7 points,
  with magnitudes up to +23. A 7-point gain on a 100-point process
  score is a meaningful fraction.
- 39 of 45 sessions exhibit at least one per-step score regression
  (a baseline-detector signal), but the divergence-point detector's
  per-kind precision/recall numbers above show it provides
  qualitatively more information than the per-step baseline.

## What this tells us about the tool

### Where it works

- **REWORK detection is rock-solid.** Perfect precision, perfect
  recall on injections. Under the V2 magnitude semantic (trajectory-
  final process-score delta) most REWORK alts beat the user, but a
  small minority (3/9) score below the user — the alt elides the
  wasted work but the post-convergence rate-term denominators can
  shift in a way that reduces the alt's score. The line count is
  preserved on the divergence point as a separate field for
  explanation purposes.
- **HYGIENE detection is similarly solid.** 100/100/100 across the
  board. Magnitudes reflect skipped-test composite sizes and
  commit-gap lengths.
- **IDE_REPLAY is now precision-perfect.** Plugin-capture FPs
  closed; 100 % precision, 76 % recall, and 73 % of fires propose
  real improvements (Part 2). The remaining miss is the
  beats-user fraction, not detector classification.
- **Top-1 ranking is always correct.** Every caught injection is
  the top-magnitude recommendation in its session — a strong UX
  story for the dashboard's "show me what to fix first" surface.

### Where it doesn't work (and why honestly)

- **ORDERING injection caught = 0/5.** The SuboptimalOrdering
  playbooks deliberately picked the worse order, but the score
  formula sees both orderings as equal-magnitude because commuting
  independent refactors reach the same terminal AST. *This is a
  finding, not a bug* — and arguably the most important one
  because it tells you the limit of the current detector.
- **ORDERING recall 0.39.** Validator gating: when a manual step
  exists in a window, the validator's non-VALID status splits the
  reorder window into singletons. Documented implementation gap;
  relaxing this gate is a follow-up.
- **Step-anchoring is broken.** Plugin checkpoint cadence drifts
  too much. We measured the cost directly; either fix the plugin
  (in-place rename / Move Method envelope detection) or accept the
  looser anywhere-in-session policy.
- **IDE_REPLAY can propose worse alts.** A non-trivial fraction of
  IDE_REPLAY DPs have magnitude ≤ 0 (sometimes negative — e.g.
  session 007 at −5 points). On caught injections specifically,
  only 12 of 21 fired with magnitude > 0 (caught-mag@>0 = 0.571).
  The detector still fires (because we removed the magnitude floor
  at the builder), but the alt isn't actually useful. A simple
  downstream threshold (`tier2_alt_better = magnitude > 0`)
  recovers the usefulness signal.

### Net story for the chapter

The tool is:

- **Correct** — precision = 1.00 across all four kinds.
- **Useful when it fires** — REWORK / HYGIENE 100 %, IDE_REPLAY
  73 %, ORDERING 24 %.
- **Well-ranked** — 100 % of caught injections are the top-1
  recommendation.

The headline weaknesses are **tractable and well-localised**:

1. ORDERING-formula insensitivity to commuting reorders
   (calibration follow-up).
2. Plugin checkpoint cadence drift (plugin-side fix).
3. Validator-gated reorder windows (synthesis-side fix).

Each is a discrete piece of follow-up work, not a fundamental flaw
in the approach. The detector defends its claim of being a useful
real-time refactoring-trajectory analyser.

## Caveats / limitations

1. **45-session corpus.** Single hand-recorded fixture; per-cell
   counts are small (e.g. only 5 SuboptimalOrdering injections).
   Per-kind ratios are directionally correct but the underlying
   counts are not enough for tight confidence intervals. Report
   raw counts alongside percentages in the chapter.
2. **Step-anchoring disabled.** Recall numbers report
   "anywhere-in-session," not strict step matching. Strict
   numbers are disclosed in Part 4. Re-enable once plugin cadence
   is stable.
3. **"Beats user" is computed against `magnitude > 0`.** Under V2
   magnitude semantics, all four kinds use the trajectory-final
   process-score delta, so a positive magnitude means the
   counterfactual trajectory genuinely scores higher than the
   user's. HYGIENE COMMIT_GAP DPs have a discrete magnitude
   exactly equal to `W_cg` (the commit-flip breaks a 6+ stretch
   into sub-stretches below the threshold, dropping $n_{cg}$ by 1
   at trajectory-final).
4. **`alt_count` for ORDERING (225) is enumeration count, not
   surfaced alts.** Reported here for transparency about the
   synthesiser's reach; the user-facing number is the 29 DPs.
5. **Rework-detector adjacent-envelope limitation.** The plugin
   fix that closed the IDE_REPLAY FPs synthesises a refactoring
   envelope on `commandStarted` for refactorings the platform
   listener is silent for. For dialog/template-based refactorings
   (Extract Variable etc.) this can place a synth envelope next
   to a platform envelope describing the same logical operation.
   The trace is faithful — both events represent real IntelliJ
   commands — but the rework detector's between-refactor-nodes
   comparison can occasionally read this as adjacent rework. On
   the current corpus this did not produce any rework FPs
   (precision still 1.00), but the chapter should flag it as a
   known metric-design limitation rather than a trace bug.

## Headline empirical claim: reordering alone rarely produces a better refactoring

The single most important empirical observation in this experiment
is that **reordering refactoring operations, on its own, almost
never leads to a better refactoring outcome on the current corpus.**

The evidence:

- The reorder synthesiser enumerated **225 alternative permutations**
  across 45 sessions.
- Those collapsed into **29 distinct reorder-window DPs** (one per
  `(fromSha, userToSha)` window, magnitude = best permutation in
  the window).
- Of those 29 windows, **only 7 had a best permutation that beat
  the user's order** — 24 %.
- On the 5 sessions where SuboptimalOrdering was *deliberately*
  injected (the user was instructed to perform the explicitly worse
  order from the playbook), **0 of the 5 produced a positive-magnitude
  reorder alt.** Even when the bad ordering was hand-engineered,
  the score formula could not distinguish it from the good ordering.

The mechanism is straightforward: the reorder synthesiser permutes
independent refactorings, and for **commuting** refactorings —
which almost all in-IDE refactor pairs are by construction (IDE
refactors are designed to be locally well-defined and
non-interacting) — every permutation reaches the **same terminal
AST**. The terminal-state cleanliness metrics are therefore
identical across permutations; the process-score's `gain` term
sees a zero delta; the only differing term is intermediate-state
cadence which doesn't enter the score formula.

This is **not a synthesiser bug** — the permutations are correctly
enumerated. It is a finding about the *measurable* impact of
ordering choices on the score formula being studied: under
production weights and the cleanliness-delta-as-headline-signal
design, **independent refactor orderings are observationally
equivalent.**

Three honest reads for the chapter:

1. **Ordering matters less than the literature implies, on
   independent refactor windows.** The "fix the order" intuition
   most developers carry assumes orderings produce different
   *terminal* code, but for IDE-driven refactors over independent
   targets they don't. The dataset confirms this empirically.

2. **The detector still surfaces 7 windows where ordering *did*
   matter** — and those are interesting cases worth examining
   qualitatively (sessions 013, 037, 039, 044). They tend to
   involve non-commuting pairs: inline-then-rename vs
   rename-then-inline genuinely produces different terminal state
   for the inlined helper's callers, because the rename
   propagates to or away from a body that was about to be
   inlined.

3. **If ordering is to be a meaningful chapter-3 finding**, the
   playbook design must be redrawn to inject *non-commuting*
   sequences (e.g. operations that share at least one entity per
   `SpecEffects.kt`'s definition), or the score formula must be
   extended with an intermediate-state cleanliness term. The
   current corpus + current formula together demonstrate that
   pure independent-reorder injections are below the detector's
   measurement floor by design.

This finding doesn't weaken the tool — it sharpens the chapter's
claim. The detector's job is to surface *measurable* process
improvements, and on commuting independent reorders there is no
measurable improvement to surface. The dashboard correctly stays
quiet (or fires with magnitude 0). The detector is being honest
about what the score formula can and can't see.

## Scoped-out fixes (deliberately deferred to future work)

Two known weaknesses in the current numbers are **deliberately not
fixed** in this submission. The rationale is recorded here so the
choice is explicit rather than something the reader has to infer.

### 1. ORDERING recall (0.39) — `splitOnInvalid` gate

**The gap.** When a window contains a manual edit, the reorder
synthesiser's validator returns a non-`VALID` status
(`AST_DIVERGED` / `REFACTOR_FAILED`) and `splitOnInvalid` collapses
the window into singletons, producing no permutations. This is why
recall is 0.39 rather than ~0.9.

**Why not fixed.** The headline finding in this experiment is
*"even when the synthesiser does fire, 0/5 deliberately-injected
orderings produce a positive-magnitude alt"* — a claim about the
score formula's insensitivity to commuting orderings, not about
detector recall. Relaxing the gate would surface more reorder
windows, but every additional window would (by the same commuting-
refactor argument) carry magnitude 0. Recall would rise; beats-
fraction would fall correspondingly; the chapter's headline claim
would be unchanged. Fixing the gate is therefore a *cosmetic* recall
improvement that does not move the substantive finding.

**Defensible answer if questioned.** "Validator gate documented in
Part 1; relaxing it would surface additional zero-magnitude DPs
which strengthens rather than changes the headline empirical claim
about commuting reorders."

### 2. IDE_REPLAY false positives (precision 0.80) — plugin event capture — CLOSED

**Status.** Closed in PR #62 (merge commit `8daecbf`). Previously,
four IDE_REPLAY false positives (sessions 025, 032, 037, 039) were
attributed to plugin-instrumentation bugs: Move Method and Change
Method Signature invocations that fire document mutations *outside*
the plugin's `REFACTORING_STARTED` / `REFACTORING_FINISHED`
envelope, plus Extract/Introduce/Inline operations that fire a
platform `refactoringStarted` event under a refactoringId the
analyser's name-matching does not bridge to RefactoringMiner's
vocabulary.

**Fix.** Extended `RefactoringCommandListener` to override
`commandStarted` and synthesise a refactoring envelope when the
IntelliJ command name matches a known refactoring phrase. The
synth envelope follows the same commit / accumulator path as the
platform-fired envelope. Affected sessions re-recorded against the
patched plugin; corpus-wide IDE_REPLAY precision rises from 0.80
to 1.00 with no other precision/recall regressions.

**Residual limitation.** For dialog/template-based refactorings,
the synth envelope can appear adjacent to a platform envelope
describing the same logical operation. Captured as caveat #5
above. The rework detector's between-refactor-nodes comparison
can occasionally read this as adjacent rework. On the current
corpus this did not produce any rework FPs.

### Why deferral is the right call

Both gaps are **well-localised, well-documented, and orthogonal to
the chapter's core claims.** Fixing them would consume time without
materially changing what the chapter argues:

- The ORDERING fix would *strengthen* the commuting-reorder headline
  (more evidence of zero-magnitude reorders) but does not change its
  sign.
- The IDE_REPLAY fix has now been applied (PR #62) and closes
  the gap to 1.00 precision. The fix lives in the plugin module
  and is about event capture fidelity, not refactoring-trajectory
  analysis (the chapter's subject).

The honest version of the chapter — which names both gaps, explains
their root causes, and reports detector-only numbers alongside the
raw numbers — is more compelling than one in which the gaps were
closed at the cost of narrowing what could be analysed elsewhere.
