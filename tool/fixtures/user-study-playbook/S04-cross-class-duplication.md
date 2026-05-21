# Session S04 — Cross-class duplication

Target time: ~25 minutes.

## Before you start

1. From `tool/fixtures/`, run `./reset-for-user-study-session.sh`.
2. In IntelliJ, click the "Reload from disk" toolbar button (or wait a few seconds).
3. Start plugin recording.

## Step 1 — Find the three copies

There are three near-identical validation blocks in the codebase. Open each file and skim its validation logic so you can see they're the same rules:

- `Cart.validate()` in `Cart.java` (lines 60–73) — private, called from `Cart.checkout()`.
- `OrderValidator.validate(...)` in `OrderValidator.java` (lines 7–32) — public.
- The inline block at the start of `OrderService.processOrder` in `OrderService.java` (lines 53–75) — runs before any other work.

All three check the same things: cart non-empty, each line item has positive qty + non-negative price, customer email non-blank, shipping street / city / postcode non-blank.

## Step 2 — Consolidate

1. Pick `OrderValidator.validate(Cart, String, String, String, String)` as the single home for those rules. Make sure it covers every check that the three current sites cover (it already mostly does).
2. In `Cart.java`, replace the body of `Cart.validate()` with a call into `OrderValidator` (or remove `Cart.validate()` entirely and update `Cart.checkout()` to call `OrderValidator` instead).
3. In `OrderService.java`, delete the inline validation block at the start of `processOrder` (lines 53–75) and replace it with a single call to `validator.validate(req.cart, req.customerEmail, req.shippingStreet, req.shippingCity, req.shippingPostcode)`.

## Step 3 — Sanity-check the call sites

1. The private `OrderService.validate(...)` method (lines 128–153) is now also unused — remove it.
2. Walk through every public entry point that used to validate (`Cart.checkout()`, `OrderService.processOrder`) and confirm the same rules still fire on the same inputs.

## When you're done

1. Stop plugin recording.
2. The facilitator will open your analysis report — take a look.
3. Answer two short questions (verbal is fine):
   - Which feedback item, if any, was most useful?
   - Did anything in the report make you want to do something differently in the next session?
