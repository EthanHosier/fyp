# Session S05 — Misplaced behaviour

Target time: ~25 minutes.

## Before you start

1. From `tool/fixtures/`, run `./reset-for-user-study-session.sh`.
2. In IntelliJ, click the "Reload from disk" toolbar button (or wait a few seconds).
3. Start plugin recording.

## The problem

`Notifier.notifyCustomer(Order order)` (lines 10–29 of `Notifier.java`) reads almost everything off `Order` to build its message — `getCustomerEmail`, `getCustomerName`, `getId`, `getItems`, `getTotalCents`, `getShippingStreet/City/Postcode` — and computes its own line-item subtotal. The string-building belongs on `Order`, not on `Notifier`.

The same applies to `Notifier.handle(Order order)` (lines 31–35), which builds a short shipment-notification string the same way.

## Steps

1. In `Order.java`, add a new public method `confirmationMessage()` (returns `String`) that builds the multi-line confirmation message. Move the entire `sb.append(...)` block currently in `Notifier.notifyCustomer` into it.
2. In `Notifier.java`, replace the body of `notifyCustomer(Order order)` with two lines: get the message from `order.confirmationMessage()` and add it to `sent`.
3. Do the same for `handle`: add `Order.shipmentMessage()` and shrink `Notifier.handle(Order order)` to two lines (get message, add to `sent`).

## When you're done

1. Stop plugin recording.
2. The facilitator will open your analysis report — take a look.
3. Answer two short questions (verbal is fine):
   - Which feedback item, if any, was most useful?
   - Did anything in the report make you want to do something differently in the next session?
