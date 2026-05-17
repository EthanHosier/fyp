# Plan: User study — independent labellers + multi-recorder + feedback

## Context

The current 45-session corpus is hand-recorded and hand-labelled by
a single investigator. Three methodological gaps follow:

1. **Same-author labelling** — no inter-rater agreement number on
   the manifest schema.
2. **Single-investigator recording** — all sessions reflect one
   person's typing speed / commit cadence.
3. **No "is this useful?" data** — rubric rewards human evaluation
   at the 70 %+ band.

This study addresses all three with the same 2 participants, so
onboarding cost amortises. Goal: ship in 2 weeks, integrate
findings into the methodology + threats-to-validity sections of
the thesis. **Hard time-box: 2 weeks then pivot back to writeup.**

## Participants

- **n = 2**, recruited from fellow MEng students or supervisor's
  group. Pick reliability over fit — two people who will turn up
  beats four who might.
- Time commitment per participant: ~10 hours total over 2 weeks.

## Three phases (in this order)

### Phase 1 — Independent labelling pass

Participants label all 45 sessions against the `manifest-v2.csv`
schema, without seeing the author's labels.

- **Brief on schema** (~30 min each): walk through DivergenceKind
  definitions, the multi-label `expected_kinds` semantics, examples
  of each pattern.
- **Materials provided per session:** `events.jsonl`, the
  playbook markdown, dashboard analysis-report viewer access.
- **Independent labelling:** ~5–10 min per session × 45 = 4–8 hrs
  per labeller. Done on their own time.
- **Reconcile** divergent labels. Substantive disagreements
  themselves are findings — note them for the threats-to-validity
  section.
- **Output:** Cohen's κ per kind (ORDERING, IDE_REPLAY, REWORK,
  HYGIENE). Reported honestly even if low.

**Why first:** participants learn the schema before recording, so
their recordings reflect schema understanding rather than
intuition.

### Phase 2 — Multi-recorder mini-corpus

Each participant records 3 sessions against `library-fixture-v1`.

- **Plugin onboarding** (~1 hr each): pre-build a packaged plugin
  JAR / zip distribution before the session — do not let them
  build from source. Block out a 1-hour install call per person.
- **Playbook selection:** 3 sessions per participant covering
  different patterns (one ManualExtractMethod, one
  AddThenRevert, one Control suggested — but pick to maximise
  diversity).
- **Recording:** ~1 hr per participant (with mistakes / restarts).
  You're available on screen-share for the first session.
- **Validation:** `events.jsonl` non-empty, plugin captured the
  intended pattern, run Phase A.
- **Cross-label:** each labeller labels the *other*'s 3 sessions
  (you label them too as third opinion). Compute 3-rater agreement
  on this subset.
- **Re-run** divergence experiment on the expanded 51-session
  corpus. Report whether per-kind precision/recall holds.

### Phase 3 — Qualitative feedback session

45 min structured interview with each participant, recorded with
consent.

**Prepared questions** (same order, both participants):

1. On session X, the tool flagged divergence point Y. Did you
   expect it would? Why or why not?
2. Look at the alternative trajectory for DP Y — would you
   actually use it in a real refactoring session?
3. What's confusing about the dashboard?
4. Is there a kind of bad-process behaviour you'd expect this to
   catch that it doesn't?
5. On a 1–5 scale, how useful is this for a real refactoring
   session?
6. Open: anything else?

**Output:** verbatim quotes (transcribed from recording) +
1–5 usability ratings + any common themes across the two.

## Ethics

- Submit institutional low-risk form on **Day 1**. Work on plugin
  packaging while it's pending.
- Consent form covering: recording of feedback session, data
  retention, anonymisation in writeup.
- Anonymise participants in the thesis (P1, P2).

## Schedule (2-week time-box)

| Day  | What                                                              |
|------|-------------------------------------------------------------------|
| 1    | Submit ethics form. Recruit + confirm 2 participants.             |
| 1–2  | Package plugin as installable JAR/zip.                            |
| 3    | Brief both on schema (~30 min each).                              |
| 4–7  | Participants label 45 sessions on their own time.                 |
| 8    | Compute κ. Reconcile genuinely divergent labels.                  |
| 9    | Plugin onboarding call (1 hr each).                               |
| 10   | Recording session (1 hr each).                                    |
| 11   | Validate sessions + Phase A + cross-label.                        |
| 12   | Re-run divergence experiment on expanded corpus.                  |
| 13   | Qualitative feedback session (45 min each).                       |
| 14   | Write up: methodology + threats-to-validity + results paragraphs. |

## Deliverables (for thesis integration)

- **Methodology chapter addition:** one paragraph on the
  multi-rater design (Phase 1) + the mini multi-recorder corpus
  (Phase 2) + qualitative protocol (Phase 3).
- **Results chapter addition:** Cohen's κ table per kind +
  precision/recall on expanded corpus + qualitative themes +
  verbatim quotes.
- **Threats-to-validity rewrite:** lead with what was mitigated,
  then what remains (still single-fixture, still Java-only,
  still small n on multi-recorder subset).

## Critical files to update post-study

- `/Users/ethanhosier/Desktop/random/fyp/tool/analysis/src/main/kotlin/com/github/ethanhosier/analysis/experiment/explained_results/divergence.md`
  — add multi-recorder validation subsection + κ table.
- `/Users/ethanhosier/Desktop/random/fyp/tool/fixtures/sessions/manifest-v2.csv`
  — extend with new sessions (e.g., 046–051).
- Thesis methodology / results / threats-to-validity sections (not
  yet written).

## What this study deliberately does **not** do

- **No 5-recorder corpus expansion.** Out of scope — time cost
  too high relative to mark return given current writeup pressure.
- **No external baseline integration** (Refactoring Navigator,
  ReSynth). Out of scope; flag as future work.
- **No multi-fixture expansion.** Out of scope; flag as future
  work.

## Verification

- Phase 1: κ computed and reported per kind. Even κ = 0.5
  (moderate agreement) is acceptable and reportable.
- Phase 2: per-kind precision/recall on expanded corpus lies
  within ±10 % of the original 45-session numbers, or differences
  are explained.
- Phase 3: at least 4 of 5 numeric ratings collected + 2+ verbatim
  quotes per question.

## Risks and mitigations

| Risk                                          | Mitigation                                                    |
|-----------------------------------------------|---------------------------------------------------------------|
| Participant flakes mid-study                  | Pick rock-solid recruits. Don't start until both confirm.     |
| Plugin install fails on their machine         | Pre-build a packaged distribution. Don't ship build-from-src. |
| Recording captures wrong pattern              | Be on screen-share for the first session per person.          |
| κ comes back low (e.g. <0.5)                  | Report honestly. Discuss schema ambiguity as a finding.       |
| Time slips past 2 weeks                       | Hard pivot back to writeup. Use what you have.                |
