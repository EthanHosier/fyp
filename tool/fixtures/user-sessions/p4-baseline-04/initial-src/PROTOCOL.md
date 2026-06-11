# Participant protocol — `user-study-fixture`

## Welcome

We're studying how developers work on existing Java code in IntelliJ. Thank you for taking part. This document describes what you'll do; if anything below is unclear at any point, ask the facilitator.

## What you'll do

You'll do **six work sessions** of about **25 minutes each** on a small order-processing codebase. Each session is independent — the code resets to a clean baseline at the start of every session. At the end of each session you'll briefly look at a report produced by a plugin that has been recording your IDE activity, and answer two short questions.

The whole thing is roughly 4 hours including a short interview at the end (scheduled separately).

## One-time setup

Done with the facilitator before your first session:

1. Open `tool/fixtures/user-study-fixture/` as a Gradle project in IntelliJ. Let Gradle finish importing.
2. From a terminal in that directory: `./gradlew test`. Confirm the suite is green.
3. The facilitator will give you a packaged plugin JAR. In IntelliJ, install it via **Settings → Plugins → Install Plugin from Disk…** Restart IntelliJ if prompted.
4. Open the playbook directory `tool/fixtures/user-study-playbook/` in your file manager so you can find each session's prompt file when needed.

## Starting a session

Each session begins from a clean baseline:

1. In a terminal, from `tool/fixtures/`: `./reset-for-user-study-session.sh`. This restores the codebase to its starting state and deletes anything left over from the last session.
2. In IntelliJ, click the **Reload from disk** toolbar button (or wait a few seconds — the IDE picks up the on-disk reset automatically).
3. In the plugin, click **Start recording**.
4. Open the session's prompt file in your editor: `tool/fixtures/user-study-playbook/SXX-<theme>.md` (the facilitator will tell you which `XX` to use).
5. Read the three prompts and begin.

## During a session

- Work the prompts in any order.
- Keep tests green between prompts. Run `./gradlew test` whenever you want.
- Java documentation, IntelliJ help, and Stack Overflow are all fine to consult.
- Thinking aloud is welcome but not required.
- If you're stuck on a prompt, **skip it and move on** to the next one. You can come back if there's time.
- If a prompt is unclear, ask the facilitator. They will rephrase it but won't tell you what to do.

## Ending a session

1. In the plugin, click **Stop recording**.
2. The facilitator will open your session report on a separate screen. Take a couple of minutes to look at it.
3. Answer two short questions (verbal is fine; the facilitator will write down your answers):
   - **Which feedback item, if any, was most useful?**
   - **Did anything in the report make you want to do something differently in the next session?**

That's the whole session. The facilitator will tell you when to start the next one.

## After all six sessions

You'll have a short interview (~25 minutes) about the experience. The facilitator will schedule this separately.

## Consent and recording

Your sessions, the IDE recordings, and any verbal answers are anonymised in the writeup. The detailed consent form covers data retention and use; please ask the facilitator if you'd like to re-read it at any point.
