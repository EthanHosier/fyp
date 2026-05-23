# Plan: User-study data collection + new codebase fixture

## Context

The results chapter is rigorous on internal-validity questions (sensitivity, ablation) but weak on external validity. Three weaknesses a hostile reviewer will land on:

1. **Self-evaluation problem.** The 45 sessions in `fixtures/sessions/` were recorded, designed (playbooks), and labelled (manifest-v2.csv) by the same person. Cohen's $\kappa$ from an independent rater is the standard fix.
2. **Controlled-injection inflation.** Mean-rank-1.00 prominence is structurally inflated by one-dominant-injection-per-session playbook design. Sessions recorded by independent participants on a different codebase give a real external-validity number.
3. **Two known engineering gaps still in the corpus** (one now closed). IDE_REPLAY precision previously sat at $0.80$ end-to-end, attributed to plugin envelope errors on sessions $025/032/037/039$. This gap has now been closed (PR #62, merge `8daecbf`): end-to-end IDE_REPLAY precision is $1.00$ on the refreshed corpus. The second gap (ORDERING recall via `splitOnInvalid`) remains deliberately deferred.

Additional supervisor ask: AI-agent process-score comparison alongside humans. Free win: behavioural-change signal (do participants reduce flagged behaviours across their 6-session trajectory?) replaces the unworkable pre/post score-improvement claim discussed earlier.

This plan covers everything that needs to come out of the user-study phase plus the new codebase fixture those sessions run against, in the order they must happen.

## Hard ordering constraints (cannot be reshuffled)

1. **Blind labelling MUST precede any participant exposure to the tool.** Once a rater has seen tool output, their labels are contaminated by the tool's framing.
2. **New fixture MUST be different domain from `library-fixture/`.** A participant who happens to know the existing library fixture would have within-codebase transfer; new domain rules that out.
3. **Plugin envelope fix MUST happen before the 4 false-positive sessions are re-recorded.** Otherwise the re-records reproduce the same problem.
4. **Participants MUST be unfamiliar with the project.** Briefing reveals playbook prompts only, never the tool's divergence kinds or recommendation framing.

## Order of operations

### Phase 0 -- Engineering + corpus refresh (1-2 days, can start now)

- [x] Patch the plugin event-capture envelope in `ide-plugin/.../listeners/` so move-method document mutations and other command-only refactorings fire inside `REFACTORING_STARTED` / `REFACTORING_FINISHED`. (Shipped via PR #62, merge commit `8daecbf`. Implementation: `RefactoringCommandListener.commandStarted` synthesises an envelope when the IntelliJ command name matches a known refactoring; the platform listener stays the source of truth where it fires.)
- [x] Re-record sessions $025, 032, 037, 039$ against the patched plugin. Same playbook prompts; same baseline tag. All four now produce $0$ IDE_REPLAY divergence points.
- [x] Re-run Phase A on the 4 re-recorded sessions (other 41 sessions unchanged so reports are still valid).
- [x] Re-run the corpus-wide precision/recall aggregation across all 45 analysis-reports. Headline: precision = 1.00 on all four kinds (ORDERING, IDE_REPLAY, REWORK, HYGIENE).
- [x] Update precision/recall numbers in `final_report/results/results.tex` (IDE_REPLAY precision rises to $1.00$, scoped-out fixes paragraph rewritten as deferred-then-closed). Also updated: `PLAN-thesis-experiments.md`, `divergence.md`, `plugin-misclassifications.md`.
- [x] Document the rework-detector adjacent-envelope limitation in `results.tex` (wide-synth envelope can emit a synth event next to a platform event for the same logical operation; rework's between-node comparison occasionally reads this as adjacent rework — metric-design limitation, not a trace-correctness bug). On the current corpus this produced zero rework FPs (precision still 1.00).

### Phase 1 -- New codebase fixture (1 day)

Path: `tool/fixtures/user-study-fixture/` (parallels `tool/fixtures/library-fixture/`).

- Domain: order-processing system (chosen to be entirely disjoint from a library/loan domain). Suggested top-level classes: `OrderService`, `Cart`, `PaymentProcessor`, `InventoryManager`, `Notifier`, `OrderValidator`, plus DTOs.
- Size: ~600--900 LOC main + ~200--300 LOC tests. Roughly parallel to `library-fixture/` (which is ~10 source files, Gradle Java build).
- Gradle Java project with passing test suite at the baseline tag.
- Intentional refactoring opportunities seeded into the baseline (matching the kinds the tool detects):
  - Duplicate validation logic between `OrderService` and `Cart` (Extract Method / Extract Class)
  - Long `processOrder` method in `OrderService` (Extract Method)
  - Poorly-named methods (`doIt`, `handle`, `process`) for rename
  - `Notifier` reaches deep into `Order` internals (Move Method / feature envy)
  - Dead conditional branch in `PaymentProcessor` (cleanup)
- Freeze at git tag `user-study-baseline-v1` (parallels Phase 3.2 of the existing fixture).

### Phase 2 -- What-not-how playbook (0.5 day)

Path: `tool/fixtures/user-study-playbook/`, one `.md` per session, naming `S01-...md` through `S06-...md`.

- 6 sessions per participant, each ~20--30 minutes target.
- Each session has 3--5 abstract task prompts that state WHAT to change but never HOW:
  - "The validation logic in `Cart` and `OrderService` is duplicated; eliminate the duplication."
  - "The `processOrder` method is doing too much; split it into smaller responsibilities."
  - "Several methods are poorly named; rename them to descriptive equivalents."
- **No** mention of: refactoring tool, commit cadence, test runs, hygiene flags, divergence kinds.
- **No** controlled-injection structure. These sessions do not carry pre-declared `expected_kinds`; they are not used to extend the 45-session precision/recall numbers.
- Difficulty ramp: S01 small/local task, S06 multi-class refactor.

### Phase 3 -- Participant protocol document (0.5 day)

Path: `tool/fixtures/user-study-fixture/PROTOCOL.md` (parallels `tool/fixtures/sessions/PROTOCOL.md`, which is 94 lines).

- Briefing: project domain, IDE setup, plugin install, where to find each session's prompt, where the tool feedback appears, how to start/end a session.
- Explicitly omits: divergence kinds the tool detects, examples of what counts as "bad" process, anything that primes the participant toward the tool's worldview.
- Session-end micro-survey (2 questions): "Which recommendation, if any, did you find most useful?"; "Did you intend to change anything in your next session?"

### Phase 4 -- Recruit (parallel with Phases 1--3)

- Target: 2 confirmed participants, possibly 3, plus 1 separate blind rater.
- **Profile is homogeneous: all 4th-year MEng Imperial Computing students.** This is what's realistically available and worth being explicit about in the writeup.
- Implication for the behavioural-change signal: baseline process quality will be high (these participants will likely already commit reasonably, run tests, use the refactor tool), so the per-kind reduction across sessions $N \to N+1$ may be small or null. The chapter should frame the population honestly and treat a muted signal as a finding, not a failure.
- Implication for the external-validity claim: the writeup should land "independent senior CS students" rather than "real-world professional developers". The thesis's main external-validity gain remains real -- different people, different codebase, no playbook prior -- but should not be oversold.
- Implication for the qualitative interview (Phase 7): technically-informed participants are likely to give richer, more specific feedback. Lean into this.
- The blind rater should NOT also be a participant (because they would need to stay tool-naive after labelling).
- Budget per participant: ~30 min onboarding + 6 \times ~25 min sessions + ~25 min Phase 7 interview = roughly 4 hours total.

### Phase 5 -- Blind labelling of existing 45 sessions (1--2 days, HARD GATE)

This MUST complete before the blind rater or any participant sees the tool's analysis report.

- Brief blind rater on the multi-label schema from `methodology.tex` §\ref{sec:methodology-rater}. Show them the session-recording artefacts (events.jsonl, session.json, screen recording if available) and the schema. Do NOT show them: the playbook prompts, the existing manifest-v2 labels, the tool's analysis report, or any dashboard view.
- They label all 45 sessions against `expected_kinds`.
- Compute Cohen's $\kappa$~\cite{cohen1960kappa, landis1977kappa} per kind against the user's labels. Report $\kappa$ alongside raw agreement counts (per-kind cell counts will be small).
- If $\kappa$ is low on any kind, run a short reconciliation pass and document it honestly; do not retroactively edit labels to inflate $\kappa$.

### Phase 6 -- Participant sessions (1--2 weeks elapsed, depending on participant availability)

- Each participant does 6 sessions on `user-study-fixture/`, with tool feedback shown at the end of every session from session 1.
- Capture per session (automatic via plugin pipeline):
  - `events.jsonl`
  - `session.json`
  - `initial-src.zip`
  - `analysis-report.json` (Phase A + Phase B output)
  - Plus: manual record of session-end micro-survey response.
  - Plus: optional screen recording for Phase 7 review.
- After every session, the participant sees their analysis report (dashboard) and can take whatever they want from it into the next session.
- No control / no-tool condition; no causal "tool improved scores" claim.

### Phase 7 -- Phase 3 qualitative interviews (1 day)

- 20--30 min semi-structured interview per participant after their 6th session.
- Recorded, transcribed, then 3--5 verbatim quotes extracted per participant.
- Question themes:
  - Did the tool's recommendations match your own sense of what was suboptimal?
  - Which kinds (manual-refactor, hygiene, rework, ordering) felt most actionable? Least?
  - Did you change behaviour across the 6 sessions because of a recommendation? Which one?
  - Any recommendation that felt outright wrong or nit-picky?

### Phase 8 -- Agent comparison (deferred; capture mechanism TBD)

In scope for the thesis, but the capture mechanism is deferred -- assumed solvable when the time comes. High-level intent:

- Pick one coding agent and disclose (which agent, which date, which model version).
- Agent executes the same what-not-how playbook prompts on `user-study-fixture/`.
- Capture the agent's session in a form the Phase A pipeline can ingest. Mechanism (driving IntelliJ vs. synthesised event stream vs. something else) decided when this phase is started.
- Run Phase A. Report process-score distribution + per-kind divergence-point counts alongside the human distribution.
- Expectation: agent's failure modes likely differ qualitatively from human failure modes -- the comparison's shape is the finding, not a single number.

### Phase 9 -- Analysis + writeup (1--2 days)

New section in `final_report/results/results.tex`, inserted after §\ref{sec:results-divergence} and before §\ref{sec:results-headline}:

- `\section{User study}` `\label{sec:results-userstudy}` with four subsections:
  - **Inter-rater reliability.** Cohen's $\kappa$ per kind, raw agreement counts, brief disagreement analysis.
  - **External validity on independent participant sessions.** Per-kind precision/recall on the participant corpus; explicit comparison against the 45-session numbers; honest discussion of where the numbers move. Population scoped honestly as "independent senior CS students", with explicit acknowledgement that generalisation to professional developers is out of scope for this thesis.
  - **Behavioural change across the 6-session trajectory.** For each recommendation kind flagged in session $N$, the per-participant rate in session $N+1$. Descriptive trends only; no causal claim. Muted signal (if observed) framed as a baseline-already-strong finding rather than a failed result.
  - **Agent comparison.** Process-score and per-kind divergence-point distribution; qualitative comparison of human vs agent failure modes.
- New short subsection in §\ref{sec:results-divergence} caveats: link to inter-rater $\kappa$.
- `\paragraph{Phase 3 qualitative feedback.}` interleaved into the user-study section: 3--5 verbatim quotes attributed by participant ID, brief reading of each.
- Update injection-prominence caveat to reference real-world (participant) prominence numbers.
- Update §\ref{sec:results-scoped-out}: drop the plugin-instrumentation paragraph (now closed); keep the ordering-recall paragraph (still scoped out, finding-strengthening).

## What gets collected per session

**Participant session (×6 per participant, ×2--3 participants):**
- `events.jsonl`, `session.json`, `initial-src.zip` (auto-captured by plugin)
- `analysis-report.json` (Phase A + B output)
- Session-end 2-question micro-survey (manual)
- Optional: screen recording for Phase 7 review

**Per participant (one-off):**
- Phase 7 interview transcript + extracted quotes
- Engagement-trajectory analysis (derived from the 6 session reports)

**Per blind-rater pass:**
- Per-session labels against the schema (matches manifest-v2.csv column structure)
- Per-kind Cohen's $\kappa$ computation (one number per divergence kind)

**Agent run (one-off):**
- Session artefacts (same format as participant sessions)
- Analysis report
- Disclosure note (agent name, date, model version)

## Critical files

**To create:**
- `tool/fixtures/user-study-fixture/` (Gradle Java project, mirrors `library-fixture/`)
- `tool/fixtures/user-study-fixture/PROTOCOL.md`
- `tool/fixtures/user-study-playbook/S01-...md` ... `S06-...md`
- `tool/fixtures/user-study-sessions/` (output dir; per-participant subdirs)
- `tool/scripts/blind-rater-kappa.kt` or `.py` (Cohen's $\kappa$ computation)
- Agent-capture path: TBD when Phase 8 is started.

**To edit:**
- `tool/ide-plugin/.../listeners/RefactoringListener.kt` (or the move-method / template-manager listener equivalents) -- envelope fix
- `final_report/results/results.tex` -- new §\ref{sec:results-userstudy} section + IDE_REPLAY number updates + scoped-out paragraph rewrite + injection-prominence caveat update
- `final_report/methodology/methodology.tex` -- short mention of inter-rater protocol in §\ref{sec:methodology-rater} if not already there
- `final_report/bibs/sample.bib` -- ensure Cohen 1960 + Landis-Koch 1977 cited

**Read-only references:**
- `tool/fixtures/library-fixture/` (parallel structure for the new fixture)
- `tool/fixtures/sessions/PROTOCOL.md` (parallel structure for the new protocol)
- `tool/fixtures/playbook/` (parallel structure for the new playbook, but content is different: what-not-how rather than scripted-injection)
- `tool/fixtures/sessions/manifest-v2.csv` (column structure for blind-rater labels)
- `final_report/methodology/methodology.tex` §\ref{sec:methodology-rater} (schema definition)

## Verification

- **Phase 0**: Phase A re-run on 45 sessions completes; IDE_REPLAY precision rises to $1.00$; results.tex compiles cleanly with updated numbers.
- **Phase 1--3**: `user-study-fixture/` builds + tests pass at `user-study-baseline-v1` tag; PROTOCOL.md + 6 playbook files exist and read coherently for a project-naive reader.
- **Phase 5**: per-kind Cohen's $\kappa$ computed; target $\kappa > 0.6$ on at least 3 of 4 kinds (rework / hygiene likely strongest; ordering may be lower because per-cell counts are small). Lower $\kappa$ reported honestly.
- **Phase 6**: each participant session produces a valid analysis report; engagement trajectory computable without manual intervention.
- **Phase 7**: interview transcripts captured + at least 3 usable quotes per participant.
- **Phase 8**: agent process-score + per-kind divergence numbers reported alongside human distribution.
- **Phase 9**: `final_report/build.sh` produces both `main.pdf` and `main_dark.pdf` cleanly; all cross-references resolve; new §\ref{sec:results-userstudy} fits within the chapter's existing page budget or extends it by no more than 2 pages.

## Minimum-viable subset (if time/recruitment slips)

In rough order of marginal value, drop from the bottom if time runs out:

1. Blind rater + Cohen's $\kappa$ on existing 45 sessions. (Highest value. Single-rater. ~1--2 days. No new code.)
2. ~~Plugin envelope fix + re-record the 4 false-positive sessions. (Pushes IDE_REPLAY precision $0.80 \to 1.00$. ~1--2 days.)~~ **DONE** — shipped in PR #62.
3. Independent participant sessions on new fixture. (Real external-validity. ~1--2 weeks elapsed.)
4. Phase 7 qualitative interviews. (Cheap once participants are recruited.)
5. Agent comparison. (Supervisor ask; doable in 1--2 days; lower priority than the human-side fixes for thesis defence.)

The thesis can ship a substantially stronger results chapter on items 1+2 alone if items 3--5 prove infeasible.

## Ethics + operational notes (carried forward from earlier draft)

- Submit institutional low-risk ethics form on Day 1; work on plugin packaging while it is pending.
- Consent form covering: recording of feedback session, data retention, anonymisation in writeup.
- Anonymise participants in the thesis (P1, P2, P3).
- Pre-build a packaged plugin JAR / zip distribution before participant onboarding; do not let participants build from source.
- Be on screen-share for each participant's first session; subsequent sessions can run independently.
