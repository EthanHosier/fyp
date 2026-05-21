# Session S03 — Within-class duplication

Target time: ~25 minutes.

## Before you start

1. From `tool/fixtures/`, run `./reset-for-user-study-session.sh`.
2. In IntelliJ, click the "Reload from disk" toolbar button (or wait a few seconds).
3. Start plugin recording.
4. Confirm the test suite is green: `./gradlew test`.

## Prompts

1. Two methods in `Order` produce strings in nearly the same way. Make them share the work.
2. `PaymentProcessor.charge` handles several payment types in one method body. Make the top-level shape easier to read.
3. Run the test suite and confirm behaviour hasn't drifted.

Work through them in any order. Keep tests green as you go.

## When you're done

1. Stop plugin recording.
2. The facilitator will open your analysis report — take a look.
3. Answer two short questions (verbal is fine):
   - Which feedback item, if any, was most useful?
   - Did anything in the report make you want to do something differently in the next session?
