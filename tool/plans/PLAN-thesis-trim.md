# Plan: thesis trim pass

## Context

Bumping the document class from 10pt to 11pt to match the Imperial distinguished-project style pushed the main content (intro through conclusion, excluding appendix) to ~70 pages. Target is to bring it back toward 58-60 main-content pages, which is where the distinguished example sits, without losing anything load-bearing in the contribution (score formula, detector, four synthesisers, headline empirical findings).

The trim is deliberately conservative on results and methodology core, and aggressive on chapters that grew because every cite-able paper got two sentences (background) or because the chapter documents what was built rather than what advanced the contribution (architecture).

## What is in scope

- Background chapter prose compression.
- Threats-to-validity §6.1 ("Mitigations and design choices") deletion.
- Architecture chapter compression and/or demotion to sit inside methodology.
- Methodology per-weight literature anchoring compression.
- Conclusion future-work list cut.
- Results agent-comparison three-thread prose compression (if needed after the above).

## What is out of scope

- Results chapter substantive content (sensitivity, ablation, κ, precision/recall, user study, gain-stripped table). Those tables and the per-kind findings carry the contribution; do not touch.
- Methodology core (score definition, weight names and ordering, cleanliness sub-score, detector definition, four synthesisers, validator). Cited literature anchoring may be compressed; the technical definitions stay.
- Headline findings in the conclusion (the five paragraphs in §7.2).
- Bibliography. Audit was closed in commit `caf9727`; no further edits.

## Priority order (with estimated page savings)

1. **Background trim** (~3-4 pp; lowest risk).
   - Eight sections is one or two too many for a background chapter of this scope. Most readers need three things: (i) the endpoint-vs-trajectory distinction, (ii) what mining/synthesis prior work does and does not address, (iii) the gap this work fills. Other sections should support those three or be cut.
   - Candidate-signals table + multi-paragraph code-smell commentary is the loosest material. Tighten to one paragraph per signal class with the cited examples named in line rather than written out.
   - Per-paper coverage: collapse two-sentence-per-paper paragraphs to one sentence each. Drop papers that only support framing (already-established background) rather than positioning (where the contribution sits relative to them).

2. **Threats §6.1 deletion** (~1 pp; pure cut, no downstream impact).
   - The eight mitigation bullets all repeat material already disclosed where it mattered: in methodology when the design choice was made, and in results when the mitigation was applied (three-way κ, multi-recorder fixture, deterministic tie-break, multi-knob MC, score-floor disclosure, validator-check disclosure, notebook reproducibility, per-session-not-per-step TP disclosure).
   - Delete the §6.1 block entirely. Threats chapter then leads with §6.2 Construct validity.

3. **Architecture compression / demotion** (~3-4 pp; biggest structural improvement).
   - Keep ~40% of the chapter: the engineering pieces that are load-bearing for the methodology (JDT replay path, validator implementation, reorder enumerator engine notes, shadow-repo justification insofar as it explains how alt trajectories share state with the user trajectory).
   - Cut or compress to a sentence: pipeline-phases overview, dashboard description, event-capture mechanics, persistent-worktree implementation detail.
   - Decision required before applying: full demotion (architecture content becomes sections inside methodology) vs keep-as-chapter-but-trim. Demotion improves readability more but requires updating every `\S\ref{sec:arch-...}` cross-reference in methodology. Keep-as-chapter is the safer move.

4. **Methodology per-weight justification compression** (~2 pp; quick prose pass).
   - Each of the six weights currently has a paragraph anchoring it to literature (Murphy-Hill, Negara, Mens & Tourwé, Vakilian, Tao & Kim, Di Biase, Kudrjavets, Herzig, Buse & Weimer, Muñoz Barón, Lavazza, Campbell, etc.).
   - Rubric demands cited justification but does not demand a paragraph per weight. Replace with a compact two-paragraph block naming each weight, what it maps to behaviourally, and which paper supports it. Inline-cite rather than per-paper exposition.
   - The two existing "no published study directly measures X" disclosures (W_mi, W_cg) stay verbatim — they are load-bearing honesty.

5. **Future-work list cut** (~½ pp; improves focus more than page count).
   - Eight prioritised items in §7.4. Cut to top four or five: larger-and-controlled human study, multi-agent comparison, rater-rank validation, IDE-refactor-as-agent-tool. The cross-IDE/cross-language, reorder beam search, live-feedback dashboard, and relaxed-validator items can be cut or folded into the four above.

6. **Results agent-comparison thread compression** (only if budget still tight; ~1-2 pp).
   - Three behavioural threads (commit cadence, smell-introduction self-critique, IDE-replay structural limitation) currently get substantial prose each. The agent run is supporting evidence for the score-formula and detector contributions, not a co-equal study.
   - Compress each thread to one tight paragraph. Keep the quoted passages because they are the evidence; cut the surrounding interpretation prose.

## Expected total saving

Items 1-5 together: ~10-12 pages. That puts the main content at 58-60 pp, matching the distinguished-project shape. Item 6 is held in reserve in case 1-5 land softer than expected.

## What not to trim (so this list survives later "more trim" passes)

- Score formula derivation and weight table in methodology.
- Detector definition and the four synthesiser specs in methodology.
- Sensitivity, ablation, kind-classifier κ, precision/recall, gain-stripped table, broken-build comparison in results.
- The five headline findings in conclusion §7.2.
- Threats §6.2-§6.5 (four-axis validity, the actual content).
- Bibliography.

## Verification

After each priority step:
- `./build.sh --light` produces zero new "undefined reference" warnings (cross-refs preserved).
- Page count delta is within the estimate ±1 pp.
- No content from the "do not trim" list above has been touched (spot-check by grep'ing for the headline-finding sentences and the score-formula equation labels).

Final target: main content (intro through conclusion, exclusive of appendix) = 58-60 pp.

## Critical files

- `final_report/background/background.tex` — item 1.
- `final_report/threats/threats.tex` — item 2 (§6.1 block).
- `final_report/architecture/architecture.tex` and `final_report/methodology/methodology.tex` — item 3 (decision required first: demote or trim-in-place).
- `final_report/methodology/methodology.tex` — item 4.
- `final_report/conclusion/conclusion.tex` — item 5 (§7.4 block).
- `final_report/results/results.tex` — item 6 (§5.4.2 threads), only if 1-5 underdeliver.

## Out of scope (named so they don't drift into the trim)

- §5b final read-through (separate pending task in `PLAN-thesis-writeup.md`).
- GenAI disclosure appendix (separate pending task).
- Abstract draft (separate pending task).
- Switching to the biblatex/biber/minted template stack from the new Imperial template (separate decision; the 11pt change alone is what triggered this trim).
