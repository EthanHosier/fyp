# Session S06 — Reshape and cleanup

Target time: ~25 minutes.

## Before you start

1. From `tool/fixtures/`, run `./reset-for-user-study-session.sh`.
2. In IntelliJ, click the "Reload from disk" toolbar button (or wait a few seconds).
3. Start plugin recording.

## Prompts

1. `OrderService.processOrder(CheckoutRequest req)` is ~85 lines doing six things in sequence: idempotency check, inline validation, inventory reservation, total calculation (subtotal + shipping + tax), payment + rollback, and finally order construction + notification. Pull each phase into its own private method so the top-level `processOrder` reads as a short sequence of named steps.
2. In `PaymentProcessor.charge(long subtotalCents, String method)` there is an `if (method == null && subtotalCents < 0)` block that can never run (the two earlier guards already throw when `method == null` or when `subtotalCents < 0`). Remove it.
3. In `OrderService.java`, the private method `calculateDiscountLegacy(long subtotal)` is never called from anywhere. Remove it.

Work through them in any order.

## When you're done

1. Stop plugin recording.
2. The facilitator will open your analysis report — take a look.
3. Answer two short questions (verbal is fine):
   - Which feedback item, if any, was most useful?
   - Did anything in the report make you want to do something differently in the next session?
