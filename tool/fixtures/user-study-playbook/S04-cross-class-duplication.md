# Session S04 — Cross-class duplication

Target time: ~25 minutes.

## Before you start

1. From `tool/fixtures/`, run `./reset-for-user-study-session.sh`.
2. In IntelliJ, click the "Reload from disk" toolbar button (or wait a few seconds).
3. Start plugin recording.

## Prompts

1. The same validation rules — empty cart, non-positive quantity, negative price, missing customer email, missing shipping street / city / postcode — appear in three places:
   - `Cart.validate()` (private, called from `Cart.checkout()`),
   - `OrderValidator.validate(...)`,
   - the long inline block at the top of `OrderService.processOrder(...)`.

   Make those rules live in one place rather than three.
2. Decide whether the single home should be `Cart`, `OrderValidator`, or somewhere else, and update the other two call sites to use it.
3. Walk through each existing caller and confirm that the validation it used to trigger still fires the same way after your change.

Work through them in any order.

## When you're done

1. Stop plugin recording.
2. The facilitator will open your analysis report — take a look.
3. Answer two short questions (verbal is fine):
   - Which feedback item, if any, was most useful?
   - Did anything in the report make you want to do something differently in the next session?
