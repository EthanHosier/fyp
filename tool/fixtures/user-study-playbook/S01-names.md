# Session S01 — Names

Target time: ~25 minutes.

## Before you start

1. From `tool/fixtures/`, run `./reset-for-user-study-session.sh`.
2. In IntelliJ, click the "Reload from disk" toolbar button (or wait a few seconds).
3. Start plugin recording.
4. Confirm the test suite is green: `./gradlew test`.

## Prompts

1. Some methods in `InventoryManager`, `Notifier`, and `ShippingCalculator` are named in ways that hide what they do. Give them names that describe what they actually do.
2. Pick one of the methods you've just changed and check that every call site still reads naturally.
3. Look briefly through the rest of the codebase for any other identifiers whose names don't match what they do.

Work through them in any order. Keep tests green as you go.

## When you're done

1. Stop plugin recording.
2. The facilitator will open your analysis report — take a look.
3. Answer two short questions (verbal is fine):
   - Which feedback item, if any, was most useful?
   - Did anything in the report make you want to do something differently in the next session?
