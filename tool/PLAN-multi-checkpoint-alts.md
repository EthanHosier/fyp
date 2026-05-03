# Multi-checkpoint alternative trajectories — thesis-worthy design options

## Context

**What we already have.** The pipeline currently produces *single-step* alternatives:
`AlternativeTrajectoryRunner` (`analysis/.../alternative/AlternativeTrajectoryRunner.kt`)
takes each user-performed refactoring step that maps to a typed
`RefactoringSpec`, replays its `fromSha` in a worktree, dispatches one
`JdtRefactorer` op, and commits the result as a single `altSha` on
`alt/{stepIndex}`. Each alt is a single (`fromSha`, `spec`) → `altSha`
pair, surfaced as one `AlternativeTrajectory` entry in the report.
Process-quality scoring (`DerivedMetricsRunner`,
`process-score.ts`) is per-checkpoint, with a prefix-cumulative walk
on the *main* trajectory only — alts get a single snapshot score.

**What's missing for the thesis.** The thesis question (interim
report §1.3) is: *given a known start and end state, evaluate the
quality of the developer's refactoring process and identify points
where a better process was available*. The "better process" must be
a **higher-scoring reference trajectory between the same endpoints**
(introduction §1.4). Single-step IDE-substitution alts only cover the
trivial case where one manual edit could have been one IDE op — they
do not produce a *trajectory-level* counterfactual that diverges,
threads through several intermediate states, and rejoins.

**Constraints.** Solutions must (i) span ≥2 checkpoints per alt, (ii)
end at a state equivalent (or metric-equivalent) to a user
checkpoint so divergence/rejoin is well-defined, (iii) be amenable
to scoring by the existing process-quality metric (cleanliness
composite + cumulative process score), and (iv) be tractable in the
May–June window of the project plan.

---

## Framing: alternatives as a constrained search problem

Pick a contiguous window of user checkpoints `[c_a … c_b]` (a few
steps long). Let `τ_obs` be the user's sub-trajectory through that
window. Define an **alternative sub-trajectory** `τ_alt` as

```
c_a = s_0 →[op_1] s_1 →[op_2] … →[op_k] s_k ≈ c_b
```

where each `op_i` is a JDT refactoring our executor can apply. A
process-quality metric `J(τ)` then induces an objective: find
`τ_alt` maximising `J(τ_alt)` subject to the end-state-equivalence
constraint `s_k ≈ c_b`.

The three approaches below differ in **how the search space of
candidate `τ_alt` is generated**, not in the scoring function or the
end-state-equivalence relation, which they share.

---

## Approach A — Topologically-constrained step reordering

**Idea.** Keep the *set* of refactorings the user performed inside
the window unchanged, but search over valid permutations of their
order. Two permutations of an independent op set produce the same
final AST (closure under commutation), so end-state equivalence is
free. Different orderings produce very different intermediate states
— some keep build/tests green throughout, others go through broken
or smell-heavy intermediates.

**Search space.** Specs `[r_1, …, r_n]` mined inside the window via
`RefactoringMinerRunner`. Build a dependency DAG: edge `r_i → r_j`
when `r_j` reads/writes an entity that `r_i` writes (e.g. Rename
Method *m* → *m'* must precede any Move Method on *m'*). Valid
candidates are topological orderings of the DAG. For ≤ ~7 steps this
is exhaustively enumerable; beyond that, beam search keyed on
running `J(τ_partial)`.

**End-state check.** AST equivalence between `s_k` and `c_b` modulo
whitespace/comments — reuse the GumTree/JDT machinery already in the
executor. Mismatches mean the dependency analysis was incomplete;
treat as a result, not a bug.

**What's novel for the thesis.** Search-based refactoring (Harman &
Tratt, Mohan et al., cited in §2.5 of background) optimises *which*
refactorings to apply. ReSynth synthesises a sequence from partial
edits but fixes the ordering implied by the developer's edits.
Refactoring Navigator picks the next step toward a goal. **No prior
work, to our knowledge, treats the *ordering* of a fixed
refactoring set as a first-class lever for process quality** —
exactly the gap §1.3 stakes out.

**Why it fits.** Maximally leverages existing infrastructure: 31
typed `RefactoringSpec` subtypes, 32 JDT ops, the per-checkpoint
metric layer. New code is the dependency analyser, the permutation
enumerator, and a multi-step variant of `synthesiseOne()` that
commits between ops. Risk surface is low — every individual op is
already known to work.

**Risk.** Some manual user refactorings have no typed spec
(`RefactoringSpec.Other`); a window must be filtered to all-typed
before reordering. RM under-detection inside the window is a real
threat to validity (call out in evaluation).

---

## Approach B — Spec coalescing / macro-substitution

**Idea.** Some sequences of small specs are equivalent to one
larger op the executor already supports. Examples:

- `RenameMethod(m → m')` + `MoveMethod(C.m' → D.m')` ≡
  `MoveAndRenameMethod(C.m → D.m')`.
- `ExtractMethod` + `RenameMethod` on the freshly extracted method ≡
  a single `ExtractMethod` with the right name.
- `MoveField` + `RenameField` → `MoveAndRenameAttribute`.

Symmetrically: split a coarse user step into a finer sequence (e.g.
`MoveAndRenameMethod` → `RenameMethod` then `MoveMethod`) to
compare granularity choices.

**Search space.** Pattern-rewrite system over the user's spec
sequence. Rules are hand-authored from the JDT op catalogue +
RM-derived equivalences (the `MoveAndRename*` family already encodes
several of these implicitly).

**End-state check.** By construction the macro op's outcome equals
the composed sub-sequence's outcome. Sanity-checked with AST diff.

**What's novel for the thesis.** Frames *episode granularity* as a
process-quality lever. The literature flags step granularity as
under-specified (§2.6 of the background, plus the explicit
"granularity, segmentation, and semantic interpretation" subsection)
but no prior system *generates* granularity-varied alternatives and
scores them. Directly addresses the "how many steps" question raised
in the metric-candidates table.

**Why it fits.** Dovetails with Approach A — a coalesced macro lifts
constraints on neighbouring ops in the dependency DAG, opening up
more reorderings. Together they form a complete sequence-space
search (compose × reorder).

**Risk.** Rewrite rules are hand-authored, so coverage is partial.
Ambitious version: learn the rewrite system from RM detections
across a corpus (REFAZER-style, cited in §2.5) — out of scope here,
clean future work.

---

## Approach C — Bounded operator planning (A* / beam search)

**Idea.** Don't restrict the alternative to operators the user
performed. Treat the window as a planning problem: from `c_a`,
search the full 32-op JDT catalogue using a heuristic derived from
`J` and a goal test of "reaches a state equivalent to `c_b`".
Returns a genuinely *different* refactoring sequence the user could
have taken.

**Search space.** State = AST + metric vector. Operator = JDT op
parameterised by entity selectors (which method, which class).
Pruning by metric heuristic (only expand frontier nodes that don't
underperform the user's running `J`); depth-limited at the window
size; beam-width capped.

**End-state check.** Either AST-exact (likely too strict) or
*metric-vector-equivalent* (the alt reaches a snapshot scoring
within ε on each cleanliness sub-metric of `c_b`). The latter is
more flexible and more aligned with the thesis question — the user
cares about reaching an *equally-clean* state, not necessarily the
*same* tokens.

**What's novel for the thesis.** Most directly realises Figure
\ref{fig:divergence}: a true reference trajectory generated under
the process-quality objective, diverging at `c_a` and rejoining at
~`c_b`. Closes the gap with Refactoring Navigator's goal-directed
sequencing while explicitly evaluating against a developer trace
(the precise hole §2.5.3 of the background calls out).

**Why it fits.** Highest research novelty. Strongest evaluation
story: shows the system can construct *reference trajectories* in
the strongest sense the thesis claims.

**Risk.** Highest implementation cost and biggest correctness
surface. Branching factor of "32 ops × every entity in the
codebase" is enormous; aggressive pruning is essential. Defining a
useful metric-equivalence relation for the goal test needs care — a
loose definition can let the search rejoin at "a different state
that happens to have similar numbers", which is a poor reference
trajectory. Could absorb the entire May–June window without
finishing.

---

## Cross-cutting design questions (apply to all three approaches)

1. **Window selection.** Which contiguous `[c_a, c_b]` to alt-ify?
   Heuristics: regions of high churn, regions where `J` regresses,
   regions where many manual refactorings were performed.

2. **Schema extension.** `AlternativeTrajectory` currently encodes a
   single alt SHA. A multi-checkpoint alt needs
   `intermediateCheckpoints: List<CheckpointReport>` plus the
   ordered spec list. Backwards-compat with the single-step case via
   a default-empty list.

3. **Process-score adaptation.** The cumulative walk lives only on
   the main trajectory. To score `J(τ_alt)` we need either to run
   the same prefix-cumulative computation over the alt's own
   intermediate snapshots (anchored at `c_a`'s prefix), or to define
   an "interval contribution" version of the process score that's
   locally additive. The latter is cleaner and reusable.

4. **Frontend.** The chart already renders single dashed branches
   (`chart-alternative-paths.tsx`). A multi-checkpoint alt becomes a
   dashed *poly-line* through the alt's intermediate metric values,
   rejoining at `c_b`. Detail panel needs a new alt-trajectory body
   listing per-step diffs and the process-score gap.

5. **End-state equivalence relation.** Three options, picked once
   and shared:
   - **AST-exact** (modulo whitespace/comments)
   - **Refactoring-equivalent** (same RM-detected operations applied)
   - **Metric-equivalent** (cleanliness vector within ε)

   AST-exact is the least argumentative for **A** and **B** because
   they preserve ops; **C** likely needs metric-equivalent.

---

## Recommendation

Build **A first (reordering)**, then **B (coalescing)** if time
allows, and **frame C (planning) as future work** in the thesis.

Justification:

- **A alone is a coherent, novel contribution.** Directly
  instantiates "the same start and end states, a different
  trajectory" with strong end-state guarantees, leverages everything
  already built, and produces the kind of multi-checkpoint alts the
  thesis needs. Permutation-as-process-quality-lever is, on its own,
  a thesis-worthy claim.

- **A + B together** form a complete operator-sequence search:
  reordering explores permutations, coalescing explores
  granularity. Together they cover the full sequence space of *what
  the user actually did, restructured*, without venturing into
  full-blown operator planning.

- **C as future work.** Lets the thesis claim a clean research
  arc — start with constrained search, gesture at full planning as
  the natural extension — without taking on the implementation risk
  of bounded planning during the writing window. Prevents the
  classic FYP failure mode of over-scoping the most ambitious
  variant.

Evaluation story for **A + B** maps directly onto §4 of the interim
report: inject known-bad orderings/granularities into collected
sessions (the synthetic ground-truth strategy already specified),
confirm the system reorders/recoalesces back to something with
higher `J`. Real episodes provide the qualitative
"divergence-point + explanation" demos.

---

## Out of scope (explicit)

- **Cross-window / globally optimal alts.** All three approaches
  operate on a single contiguous window. `c_0 → c_n` planning is
  out of scope; the thesis does not need it and the background
  explicitly identifies it as intractable.

- **New operators in the JDT executor.** No new refactoring ops
  added. If a window contains a typed spec the executor doesn't yet
  support, the window is excluded.

- **Behavioural verification.** End-state equivalence is structural
  (AST or metric); no test execution during search. Test/build
  status is captured per-checkpoint and folded into `J` as today.

- **Cross-trajectory metric aggregation.** Beyond `J(τ_obs)` vs
  `J(τ_alt)` per window, no aggregation across alt trajectories.
  Each alt is scored and presented standalone.
