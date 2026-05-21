# Session S04 — Cross-class duplication

Target time: ~25 minutes.

## Before you start

1. From `tool/fixtures/`, run `./reset-for-user-study-session.sh`.
2. In IntelliJ, click the "Reload from disk" toolbar button (or wait a few seconds).
3. Start plugin recording.

## The duplication

The same set of validation rules — cart non-empty, every line item has positive qty + non-negative price, customer email non-blank, shipping street / city / postcode non-blank — appears in two places:

- `OrderValidator.validate(Cart, String, String, String, String)` in `OrderValidator.java` (lines 7–32).
- The inline block at the top of `OrderService.processOrder` in `OrderService.java` (lines 53–75).

There is also a third copy: the private method `OrderService.validate(Cart, String, String, String, String)` in `OrderService.java` (lines 128–153). It is identical to `OrderValidator.validate(...)` and is not called from anywhere.

Make `OrderValidator.validate(...)` the single home for these rules.

## Steps

1. In `OrderService.java`, delete the inline validation block in `processOrder` (lines 53–75) and replace it with a single call:

   ```java
   validator.validate(req.cart, req.customerEmail, req.shippingStreet, req.shippingCity, req.shippingPostcode);
   ```

2. In the same file, remove the now-unused private method `OrderService.validate(...)` (lines 128–153).

`Cart.validate()` in `Cart.java` is a smaller, separate API (it checks only the cart-internal rules and takes no email/address) — leave it as-is.

## When you're done

1. Stop plugin recording.
2. The facilitator will open your analysis report — take a look.
3. Answer two short questions (verbal is fine):
   - Which feedback item, if any, was most useful?
   - Did anything in the report make you want to do something differently in the next session?
