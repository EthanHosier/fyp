# Session S06 — Reshape and cleanup

Target time: ~25 minutes.

## Before you start

1. From `tool/fixtures/`, run `./reset-for-user-study-session.sh`.
2. In IntelliJ, click the "Reload from disk" toolbar button (or wait a few seconds).
3. Start plugin recording.
4. Confirm the test suite is green: `./gradlew test`.

## Prompts

1. `OrderService.processOrder` is doing too many things in one method (validate, reserve stock, compute totals, charge, notify). Make its top-level shape easier to read.
2. There is logic in `PaymentProcessor` and `OrderService` that either never runs or is no longer called. Remove what isn't needed.
3. Run the full test suite and confirm everything still passes.

Work through them in any order. Keep tests green as you go.

## When you're done

1. Stop plugin recording.
2. The facilitator will open your analysis report — take a look.
3. Answer two short questions (verbal is fine):
   - Which feedback item, if any, was most useful?
   - Did anything in the report make you want to do something differently in the next session?
