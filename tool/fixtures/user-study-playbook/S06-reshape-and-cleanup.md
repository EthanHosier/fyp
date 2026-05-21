# Session S06 — Reshape and cleanup

Target time: ~25 minutes.

## Before you start

1. From `tool/fixtures/`, run `./reset-for-user-study-session.sh`.
2. In IntelliJ, click the "Reload from disk" toolbar button (or wait a few seconds).
3. Start plugin recording.

## Step 1 — Split `OrderService.processOrder`

1. Open `OrderService.java`. The method `processOrder(CheckoutRequest req)` starts at line 44 and runs to roughly line 124. It does six things in sequence. Pull each into its own private method so the top-level `processOrder` reads as a short sequence of named steps:

   - **Idempotency check** (lines 48–50): the `req.idempotencyKey != null && submittedIds.contains(...)` guard. Pull out into something like `checkIdempotency(req)`.
   - **Inline validation** (lines 52–75): all the cart/email/address null and bounds checks. Pull out into something like `validateRequest(req)`.
   - **Inventory reservation** (lines 77–81): the `inventory.reserve(items)` block. Pull out into something like `reserveStock(items)`.
   - **Total calculation** (lines 83–94): subtotal + shipping + tax. Pull out into something like `computeGrandTotal(req)` (returns `long`).
   - **Payment + rollback** (lines 96–100): `payments.charge(...)` plus the `inventory.release(items)` on failure. Pull out into something like `chargeOrRollback(items, grandTotal, method)` (returns the `PaymentProcessor.Result`).
   - **Order construction + notification** (lines 102–122): build the `Order`, set total/status, notify. Pull out into something like `buildAndNotify(req, items, result)` (returns the `Order`).

2. The end-state of `processOrder` should be roughly: idempotency check → validate → reserve → compute total → charge-or-rollback → finalise stock + build-and-notify → record idempotency key → return order. About a dozen lines.

## Step 2 — Remove the dead branch in `PaymentProcessor.charge`

1. Open `PaymentProcessor.java`. In `charge(long subtotalCents, String method)` (lines 16–47), the block on lines 23–25 is `if (method == null && subtotalCents < 0) { return ... "unreachable legacy guard" ... }`.
2. That block can never run: the guard on line 17 throws when `method == null`, and the guard on line 20 throws when `subtotalCents < 0`. Either condition already exits before line 23. Remove the dead block.

## Step 3 — Remove the dead helper in `OrderService`

1. Still in `OrderService.java`, the private method `calculateDiscountLegacy(long subtotal)` at line 155 has no callers anywhere in the codebase.
2. Remove the method entirely.

## When you're done

1. Stop plugin recording.
2. The facilitator will open your analysis report — take a look.
3. Answer two short questions (verbal is fine):
   - Which feedback item, if any, was most useful?
   - Did anything in the report make you want to do something differently in the next session?
