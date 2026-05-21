# Session S04 — Cross-class duplication

Target time: ~25 minutes.

## Before you start

1. From `tool/fixtures/`, run `./reset-for-user-study-session.sh`.
2. In IntelliJ, click the "Reload from disk" toolbar button (or wait a few seconds).
3. Start plugin recording.
4. Confirm the test suite is green: `./gradlew test`.

## Prompts

1. The same validation rules appear in `Cart`, `OrderValidator`, and `OrderService`. Make them live in one place.
2. After consolidating, decide whether the unified validation has a sensible home (cart-side, validator-side, or elsewhere) and move it if needed.
3. Check that no caller is now bypassing validation that previously fired.

Work through them in any order. Keep tests green as you go.

## When you're done

1. Stop plugin recording.
2. The facilitator will open your analysis report — take a look.
3. Answer two short questions (verbal is fine):
   - Which feedback item, if any, was most useful?
   - Did anything in the report make you want to do something differently in the next session?
