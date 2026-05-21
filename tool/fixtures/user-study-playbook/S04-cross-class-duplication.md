# Session S04 — Cross-class duplication

Target time: ~25 minutes.

## Before you start

1. From `tool/fixtures/`, run `./reset-for-user-study-session.sh`.
2. In IntelliJ, click the "Reload from disk" toolbar button (or wait a few seconds).
3. Start plugin recording.

## The duplication

The same validation rules (cart non-empty, each line item has positive qty + non-negative price, customer email non-blank, shipping street / city / postcode non-blank) appear in three places:

- `Cart.validate()` in `Cart.java` (lines 60–73).
- `OrderValidator.validate(...)` in `OrderValidator.java` (lines 7–32).
- The inline block at the top of `OrderService.processOrder` in `OrderService.java` (lines 53–75).

Make these rules live in one place — `OrderValidator.validate(...)` — and have both `Cart` and `OrderService` call it.

## Steps

1. In `OrderService.java`, delete the inline validation block in `processOrder` (lines 53–75) and replace it with a single call to `validator.validate(req.cart, req.customerEmail, req.shippingStreet, req.shippingCity, req.shippingPostcode)`.
2. In `Cart.java`, remove `Cart.validate()` and update `Cart.checkout()` to call `OrderValidator.validate(...)` instead (you'll need a `OrderValidator` reference — either passed in to `Cart`, or constructed inside `checkout()`).
3. In `OrderService.java`, the now-unused private `OrderService.validate(...)` method (lines 128–153) — remove it.

## When you're done

1. Stop plugin recording.
2. The facilitator will open your analysis report — take a look.
3. Answer two short questions (verbal is fine):
   - Which feedback item, if any, was most useful?
   - Did anything in the report make you want to do something differently in the next session?
