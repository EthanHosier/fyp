# Agent setup

This file is read once by an AI coding agent at the start of a study run. It documents the fixed contract that holds across all six sessions; the per-session task prompt arrives separately at the start of each session.

## What you are doing

You are refactoring a small Java codebase (this directory, an order-processing system) across six recorded work sessions. Each session has its own task prompt, delivered to you when the session starts. The work is being measured by a refactoring-trajectory analysis tool, which produces feedback at the end of each session; that feedback is fed back to you at the start of the next session so you can adjust how you work.

## Project structure

- `build.gradle.kts`, `settings.gradle.kts`, `gradlew` — Gradle Java 17, JUnit 5
- `src/main/java/org/orders/` — domain code you will refactor
- `src/test/java/org/orders/` — behaviour tests;
- `.refactoring-traces/<session-uuid>/` — plugin-managed; do not touch
- `agent-test` — wrapper you use instead of `./gradlew test` (see below)
- `AGENT-SETUP.md` — this file

The full test suite at baseline contains 21 tests and is green.

## Contracts (do these literally)

These are not suggestions. The measurement pipeline depends on them.

### 1. Use `./agent-test`, not `./gradlew test`

To run tests at any point, invoke `./agent-test` from this directory. It runs the standard test suite and additionally records `TEST_RUN_STARTED` / `TEST_RUN_FINISHED` events into the active session's trace so the analysis tool can see when you ran tests. Exit code matches gradle's (0 = green, non-zero = failures).

You can pass any gradle test args after it, e.g.

```
./agent-test --tests OrderServiceTest
```

If you call `./gradlew test` directly, the test run is invisible to the analysis tool and you will be flagged for skipping tests even when you ran them.

### 2. Commit work with `git commit` as you go

The analysis tool watches the git reflog.
Use any commit message style you want. The message text is informational only.

### 3. Do not modify `agent-test`, the test suite, or `build.gradle.kts`

These are part of the measurement scaffold. Refactoring is scoped to files under `src/main/java/org/orders/`.

### 4. One session, then stop

When you finish the current session's task — or when the session prompt says to stop — return control to the orchestrator. Do not start the next session yourself; you will receive the next prompt (and feedback from the current session) as a new message.

## How feedback works

After the orchestrator runs the analysis pipeline on each session, it produces a markdown summary of every "divergence point" the tool detected — moments where the analysis suggests a better process was available.

For sessions 2–6 you will receive the previous session's feedback markdown as part of the prompt. Read it before starting the next session and decide what, if anything, to change in how you work. You are not required to act on every recommendation; the point of the study is to see what you do with it.

You will not receive feedback before session 1.

## Where the session prompt lives

The per-session task prompt is delivered to you in the orchestrator message that starts each session. It will name the area of code to address and what the goal is, but not which IntelliJ action to use or how to structure your commits.

If the orchestrator points you at `tool/fixtures/user-study-playbook/SXX-<theme>.md`, read that file — it is the canonical version of the session's task. (Ignore any sections that reference IntelliJ buttons or the "Reload from disk" toolbar, which apply only to human participants doing the study in an IDE.)
