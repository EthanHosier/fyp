# Phase 7 — Post-study qualitative interview

**Audience: facilitator only.** Do not show this document to the participant during the study. The interview runs after the participant's sixth and final session.

## Logistics

- **Duration:** 20–30 minutes.
- **Format:** Semi-structured. The lead questions below are the spine; follow-ups are optional and used only when a participant's first answer is short or vague. Stay out of the participant's way — a long answer from the participant is the goal.
- **Recording:** Audio (with consent — confirm verbally before pressing record). Transcribed afterwards; 3–5 verbatim quotes extracted per participant for the writeup.
- **Anonymisation:** Participants are referred to as P1 / P2 / P3 in the writeup. Names and identifying details are stripped from transcripts before quotes are extracted.
- **Setting:** After the participant has finished session S06 and looked at the final analysis report. The dashboard from the last session can stay open on a side screen so the participant can point to specific recommendations if helpful.

## Pre-interview script (verbatim)

> Thanks again for taking part. We're now going to have a short conversation about what the six sessions were like for you, and what you thought of the feedback you saw at the end of each one. There are no right or wrong answers — I'm interested in your honest reaction to the tool, including the parts that didn't land or felt wrong. The conversation will take about 25 minutes. I'll be recording the audio if that's okay — the recording is anonymised in the writeup, and you can ask me to pause at any point. Ready to start?

## Warm-up

**Lead:** Take me through the six sessions as a whole — what stood out, what was hard, what was satisfying?

*Purpose: get the participant talking, surface anything top-of-mind before we steer toward the tool. Two to three minutes maximum.*

## Theme 1 — Did the tool's recommendations match the participant's own judgement?

**Lead:** When you looked at the report at the end of a session, how often did the things it flagged line up with what you'd have flagged yourself?

**Follow-ups (use only if the lead answer is brief):**
- Can you think of a specific recommendation where you thought "yes, exactly"?
- Can you think of one where you thought "that's not really a problem"?

*Probe target: get one example of agreement and one of disagreement, with reasoning.*

## Theme 2 — Per-kind actionability

**Lead:** The tool surfaces four flavours of recommendation — IDE replay (where the IDE could have done a refactoring you did by hand), rework (where you did and then undid roughly the same thing), hygiene (skipped tests or long stretches without committing), and ordering (you did refactorings in a suboptimal order). Of those four, which felt most actionable to you, and which felt least actionable?

**Follow-ups:**
- For the most actionable one: what made it click?
- For the least actionable one: was it the framing, the specific examples, or the underlying claim that didn't land?

*Probe target: which kind translates best from "the tool says X" to "I would do Y differently". Note: the kind names are participant-facing in the dashboard, so it's fine to use them by name here.*

## Theme 3 — Behavioural change across the six sessions

**Lead:** Looking back at sessions 1 through 6, did anything the tool showed you change how you approached a later session?

**Follow-ups:**
- Was there a particular recommendation that you carried into the next session?
- Was there anything you wanted to change but couldn't, or didn't get around to?
- Were there things you started doing that the tool DIDN'T flag — i.e. you anticipated it?

*Probe target: capture an episode where the tool's feedback actually nudged the participant's process. If there was none, capture that too — it's a legitimate finding.*

## Theme 4 — Wrong, nit-picky, or annoying

**Lead:** Was there a recommendation that felt outright wrong, or so minor it wasn't worth showing?

**Follow-ups:**
- What about a recommendation you understood but disagreed with on principle?
- Anything the tool missed that you wish it had caught?

*Probe target: the participant's annoyance / disagreement signal is high-value. Don't argue back — record the objection cleanly.*

## Theme 5 — Open

**Lead:** Anything else about the tool, the codebase, the session format, or the study setup that I haven't asked about?

*Purpose: a deliberate open slot in case a strong theme didn't fit the structured questions above.*

## Closing script (verbatim)

> That's everything from my side. Thank you again — this is genuinely useful feedback. The transcript will be anonymised before any quotes go into the writeup. If you think of anything later that you wish you'd mentioned, my contact details are on the consent form. Stopping the recording now.

## Post-interview checklist (facilitator)

- [ ] Audio file saved with anonymised filename (e.g. `P1-interview.m4a`).
- [ ] Note any moments worth flagging for the transcriber (long pauses, references to a specific dashboard view, etc.).
- [ ] Confirm with the participant that they're happy to be contacted with follow-up clarifications (or not).
- [ ] Transcribe within 48 hours while the conversation is fresh — interim notes deteriorate quickly.
- [ ] After transcription, extract 3–5 candidate verbatim quotes per participant. Tag each with the theme it supports.

## Notes on what NOT to ask

- Don't ask about the codebase's intentional smells by name (it primes participants to read seeded smells as the "right answer" retrospectively). Stick to "what you noticed" and "what you'd do differently".
- Don't ask whether the tool's process score went up — this is not a controlled before/after design, and any apparent improvement is confounded.
- Don't push the participant toward a positive framing — disagreement and frustration are equally informative.
- Don't share other participants' answers, even in aggregate.
