# Session S03 — Within-class duplication

Target time: ~25 minutes.

[//]: # (## Before you start)

[//]: # ()
[//]: # (1. From `tool/fixtures/`, run `./reset-for-user-study-session.sh`.)

[//]: # (2. In IntelliJ, click the "Reload from disk" toolbar button &#40;or wait a few seconds&#41;.)

[//]: # (3. Start plugin recording.)

## Step 1 — `Order.getShippingLabel` and `Order.getBillingLabel`

1. Open `Order.java`. The methods `getShippingLabel()` (line 62) and `getBillingLabel()` (line 70) build their strings the same way: customer name, then street, then "city + space + postcode".
2. Pull the shared formatting into a single helper on `Order` (e.g. a `formatAddress(name, street, city, postcode)` method) and have both `getShippingLabel` and `getBillingLabel` call it.

## Step 2 — `PaymentProcessor.charge`

1. Open `PaymentProcessor.java`. The body of `charge(long subtotalCents, String method)` (lines 16–47) handles the CARD, CASH, and VOUCHER branches inline.
2. Pull each branch out into its own private method (e.g. `chargeCard(subtotalCents)`, `chargeCash(subtotalCents)`, `chargeVoucher(subtotalCents)`). The top-level `charge` should end up as a short dispatch (guards + a switch / if-else over `method` that returns the appropriate helper's result).

## Step 3 — `PaymentProcessor.refund`

1. In the same file, `refund(long amountCents, String method)` (lines 58–75) has the same shape as `charge` — branches over `method` inline.
2. Pull each branch out the same way you did for `charge` (e.g. `refundCard(amountCents)`, `refundCash(amountCents)`, `refundVoucher(amountCents)`).

## When you're done

1. Stop plugin recording.
2. The facilitator will open your analysis report — take a look.

[//]: # (3. Answer two short questions &#40;verbal is fine&#41;:)

[//]: # (   - Which feedback item, if any, was most useful?)

[//]: # (   - Did anything in the report make you want to do something differently in the next session?)
