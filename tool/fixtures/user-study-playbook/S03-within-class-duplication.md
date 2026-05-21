# Session S03 — Within-class duplication

Target time: ~25 minutes.

## Before you start

1. From `tool/fixtures/`, run `./reset-for-user-study-session.sh`.
2. In IntelliJ, click the "Reload from disk" toolbar button (or wait a few seconds).
3. Start plugin recording.

## Prompts

1. In `Order.java`, the methods `getShippingLabel()` and `getBillingLabel()` produce strings in nearly the same way (name + street + city + postcode). Eliminate the duplication so the two methods share a single formatting helper.
2. In `PaymentProcessor.java`, the body of `charge(long subtotalCents, String method)` handles CARD, CASH, and VOUCHER inline. Pull each branch out into its own method so the top-level `charge` reads as a short dispatch.
3. If time remains: do the same exercise on `PaymentProcessor.refund(long amountCents, String method)`.

Work through them in any order.

## When you're done

1. Stop plugin recording.
2. The facilitator will open your analysis report — take a look.
3. Answer two short questions (verbal is fine):
   - Which feedback item, if any, was most useful?
   - Did anything in the report make you want to do something differently in the next session?
