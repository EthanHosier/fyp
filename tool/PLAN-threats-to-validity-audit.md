# Plan: audit and extend `THREATS-TO-VALIDITY.md`

## Context

A final scan of the thesis (`final_report/`) and supporting code identified threats to validity that are not yet captured in `tool/THREATS-TO-VALIDITY.md`. Three parallel Explore agents combed: (1) the methodology chapter, (2) the architecture chapter, (3) the analysis code in `tool/analysis/` (advisor, detectors, synthesisers, derived metrics). Their findings have been filtered against the existing 10-axis bullet list to keep only substantive items a reasonable reviewer would actually cite.

The intended outcome is a self-contained list of bullets to add to `THREATS-TO-VALIDITY.md` (and existing bullets to extend), with rationale and target axis for each. No prose drafting is in scope here — the prose pass is task #12 in the master writeup plan and consumes this file as its source.

## Substantive gaps found

### Construct validity — one new bullet

(Originally drafted: "HYGIENE synthesiser presupposes the counterfactual without verifying it." **Withdrawn.** The methodology chapter wording was wrong; the code at `HygieneDetector.kt:261-279` does *not* fabricate a passing result, it only injects a `TEST_RUN_FINISHED` event and leaves the test outcome unchanged. The methodology chapter has been corrected to match. The threat does not exist.)

1. **AST-equivalence audit uses an informal definition.** The reorder synthesiser admits a permutation only if its terminal AST is equivalent to the user's "*modulo syntactic differences that do not change behaviour*" (methodology §3.6). The operationalisation is an AST hash whose definition of "behaviour-preserving" is not formalised in the chapter. A too-loose hash could admit reorders that change behaviour (e.g. comment placement, annotation order); a too-tight one could reject legitimate ones. **Axis:** Construct validity. **Why it matters:** ORDERING-kind correctness depends on this audit being sound, and the chapter currently asserts soundness without proving it.

### Internal validity / calibration — two new bullets, two extensions

(Originally drafted: "Advice-rule thresholds are heuristic and not calibrated." **Withdrawn** — these thresholds only govern the dashboard advice surface, not the score formula or divergence-point detector. They are a UI concern, not a validity threat.)

(Originally drafted: "REWORK pairing is greedy by content hash." **Withdrawn** — the failure mode (multi-round identical-content rework) is purely hypothetical with zero observed cases on the recorded corpora; a reviewer would need to construct a hypothetical to cite this, which doesn't justify a bullet.)

(Originally drafted: "`MIN_REWORK_LINES = 2` is a heuristic floor that drops single-line REWORK as noise." **Withdrawn** — minor calibration constant; not a substantive validity threat.)

1. **The $60$-second composite-window boundary is hardcoded and not sensitivity-tested.** This boundary (Murphy-Hill 2012) determines which refactoring steps are bundled into a single batch for `TESTS_SKIPPED` and ORDERING detection. Typing cadence different from the study's participants' would bundle differently and produce different `TESTS_SKIPPED` counts. **Axis:** Internal validity (calibration). **Why it matters:** worth one sentence alongside the existing `min_commit_gap = 6` bullet so both batching primitives are flagged together.

2. **The reorder dependency analysis is deliberately coarse, and content-addressed anchors are sensitive to in-region identifier renames.** The `SpecDependencyAnalyzer` is optimistic on Extract* specs (two extracts on the same host body are not auto-conflicted, since the AST-equivalence audit downstream filters invalid permutations) and coarse on Rename / Inline / ChangeMethodSignature (each writes the entire declaring class, chaining later ops on the same type by construction). Anchor entities (`Region`, `Declaration`) are content-addressed via AST subtree hashes, so an identifier rename *inside* an anchor's region between two reorderable steps invalidates the second step's anchor — the SSA versioning that handles name-based entities (Type / Method / Field) does not extend to subtree hashes. Both choices contribute to the ORDERING recall gap reported in §5.2.1: the optimistic model over-enumerates and the audit drops the false positives; the coarse model under-enumerates and removes legitimate independence by construction. The §5.1.3 headline ("reordering rarely improves the score") is robust to both — it follows from commuting refactorings producing identical end states under the production formula, which does not depend on the dependency model's precision — but the ORDERING recall number itself is conditional on these choices. **Axis:** Internal validity (calibration). **Why it matters:** a reviewer comparing the ORDERING recall ($0.36$ on any-expected) to a system with a tighter dependency model would see a different number, even though the headline reordering finding would not change.

**Extension to existing bullet** ("**Term-importance varies by corpus**", line 22 of `THREATS-TO-VALIDITY.md`): the existing bullet covers the divergence between injection and user-study term importance. No new content needed here, but the prose pass should tie this to the gain-stripped finding in §5.4 (the new score-trajectory table) where the cleanliness-gain term dominates absolute scale despite being a regularizer on ranking — i.e. the formula has different "modes" for ranking vs. absolute scoring.

### Implementation — two existing extended

**Extension to existing bullet** ("**The wrap-and-patch layer**", line 69 of `THREATS-TO-VALIDITY.md`): the existing bullet says the layer is not exhaustive. The audit found that what counts as "safely patched" is implicit — there is no formal criterion for when a JDT/IntelliJ gap is patchable. Reorder candidates excluded under this implicit criterion are bundled with legitimate `ast_diverged` rejections in the recall numbers, so the contribution of "we don't yet have a patch for X" to ORDERING recall is not separable from "the AST genuinely diverges on X". One extra sentence on the existing bullet.

**Extension to existing bullet** (cleanliness sub-score normalisation, currently only covered indirectly in line 19): alt-trajectory cleanliness sub-signals are min–max normalised against the *main trajectory's* observed range, then clamped to $[0, 1]$. An alt whose cleanliness exceeds the user's range in either direction is masked at the boundary. The chapter mentions this in methodology §3.4 but does not flag it as a threat. **Axis:** Internal validity. One short bullet under "Internal validity — calibration" or a sub-point on the existing cleanliness-weight bullet.

## Items considered and rejected

These came up in the audit but are too niche or too implementation-detailed to warrant a bullet:

- AST-hash collision probability — vanishingly low; not quantified, but a reviewer is unlikely to cite this without evidence of a collision actually occurring.
- Worktree-pool resource exhaustion — engineering concern, not a validity threat.
- Phase B memory bound on very large reports — engineering concern, no failure observed.
- Dashboard rendering fidelity (SVG clipping, etc.) — no evidence of issues; the participants used the dashboard successfully.
- Rework drift-tracker correctness under overlapping edits — niche technical issue, no evidence of failure on the recorded corpora.
- `DivergencePointBuilder` tie-break ordering — already deterministic (we fixed this earlier this session); the deterministic tie-break is a *mitigation*, not a threat. Belongs in the section's mitigation summary rather than the threats list.
- Whitespace-only REWORK invisibility — too niche; whitespace-only rework is rare in practice.
- The choice of *which six* cleanliness sub-signals to include — already implicit in the "kind labels are a methodology choice" bullet (line 12) generalised. Could be added if §6 prose has room, but is borderline.

## Critical file

`tool/THREATS-TO-VALIDITY.md` — add the three new bullets above under their named axes, extend the three flagged existing bullets with the additional content described.

The downstream consumer is task #12 in `PLAN-thesis-writeup.md` (the §6 prose drafting), which expands the updated bullet file into the chapter.

## Verification

1. Open `THREATS-TO-VALIDITY.md` after edits and confirm each of the three new bullets sits under the correct axis header (Construct $\times 1$, Internal calibration $\times 2$ + 1 cleanliness-clamp sub-point, Implementation $\times 1$ extension only).
2. Grep `final_report/results/results.tex` and `final_report/methodology/methodology.tex` for the specific phrases the new bullets reference (AST equivalence, composite-window, reorder dependency analysis / content-addressed anchors, wrap-and-patch safety, cleanliness clamp). For each, confirm the bullet's claim is consistent with what the chapter says.
3. No PDF rebuild required — `THREATS-TO-VALIDITY.md` is not compiled into the report. The downstream §6 prose pass (task #12) will pick it up.

## Out of scope

- Writing the §6 prose itself. That is task #12 and will draw on the updated bullet file as one of several sources (the others being the inline caveats in §5 and the master writeup plan).
- Re-running any experiments. The audit identified threats inherent to the design and code, not to the data.
- Editing the methodology or architecture chapters to address any of these threats. The threats list documents what exists; mitigations live in the existing prose where applicable, and unresolved threats live in §6.
