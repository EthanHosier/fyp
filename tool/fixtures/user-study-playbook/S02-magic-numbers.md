# Session S02 — Magic numbers

Target time: ~25 minutes.

## Before you start

1. From `tool/fixtures/`, run `./reset-for-user-study-session.sh`.
2. In IntelliJ, click the "Reload from disk" toolbar button (or wait a few seconds).
3. Start plugin recording.
4. Confirm the test suite is green: `./gradlew test`.

## Prompts

1. There are numeric literals in `PaymentProcessor` (fee multipliers, payment-method caps) whose meaning isn't obvious from the source. Make their meanings visible.
2. Do the same for `ShippingCalculator`'s weight thresholds and zone fees.
3. While you're in those files, see if any other literal values would benefit from being named.

Work through them in any order. Keep tests green as you go.

## When you're done

1. Stop plugin recording.
2. The facilitator will open your analysis report — take a look.
3. Answer two short questions (verbal is fine):
   - Which feedback item, if any, was most useful?
   - Did anything in the report make you want to do something differently in the next session?
