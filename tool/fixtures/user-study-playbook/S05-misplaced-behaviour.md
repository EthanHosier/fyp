# Session S05 — Misplaced behaviour

Target time: ~25 minutes.

[//]: # (## Before you start)

[//]: # ()
[//]: # (1. From `tool/fixtures/`, run `./reset-for-user-study-session.sh`.)

[//]: # (2. In IntelliJ, click the "Reload from disk" toolbar button &#40;or wait a few seconds&#41;.)

[//]: # (3. Start plugin recording.)

## The problem

`Notifier.notifyCustomer(Order order)` in `Notifier.java` (lines 10–29) reads almost everything off `Order` — `getCustomerEmail`, `getCustomerName`, `getId`, `getItems`, `getTotalCents`, `getShippingStreet/City/Postcode` — and computes its own line-item subtotal. `Notifier.handle(Order order)` (lines 31–35) does the same on a smaller scale for shipment notifications. The string-building belongs on `Order`, not on `Notifier`.

Move it. The externally visible behaviour shouldn't change: calling `notifier.notifyCustomer(order)` or `notifier.handle(order)` should still append the same strings to `sent`.

## Steps

1. In `Order.java`, add a public method `confirmationMessage()` that returns the multi-line confirmation string. Move the entire `sb.append(...)` block from `Notifier.notifyCustomer` into it.
2. In `Notifier.java`, replace the body of `notifyCustomer(Order order)` with two lines — get the string from `order.confirmationMessage()`, then add it to `sent`.
3. Do the same for `handle`: add a public `Order.shipmentMessage()` that returns the short shipment-notification string, then shrink `Notifier.handle(Order order)` to two lines (get message from `order.shipmentMessage()`, add to `sent`).

## When you're done

1. Stop plugin recording.
2. The facilitator will open your analysis report — take a look.

[//]: # (3. Answer two short questions &#40;verbal is fine&#41;:)

[//]: # (   - Which feedback item, if any, was most useful?)

[//]: # (   - Did anything in the report make you want to do something differently in the next session?)
