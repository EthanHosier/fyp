# Session S05 — Misplaced behaviour

Target time: ~25 minutes.

## Before you start

1. From `tool/fixtures/`, run `./reset-for-user-study-session.sh`.
2. In IntelliJ, click the "Reload from disk" toolbar button (or wait a few seconds).
3. Start plugin recording.

## Step 1 — Pull message-building onto `Order`

1. Open `Notifier.java`. Look at `notifyCustomer(Order order)` (lines 10–29). It reads `order.getCustomerEmail()`, `order.getCustomerName()`, `order.getId()`, `order.getItems()`, `order.getTotalCents()`, and `order.getShippingStreet/City/Postcode()` — and computes its own running subtotal from the line items.
2. Open `Order.java` and add a new method `confirmationMessage()` that builds the whole multi-line message string. Move every line of the message-formatting logic (the `sb.append(...)` block currently in `notifyCustomer`) into this new method on `Order`. The running subtotal can use the line items directly — `Order` already has them.

## Step 2 — Simplify `Notifier.notifyCustomer`

1. Back in `Notifier.java`, the body of `notifyCustomer(Order order)` should now be two lines: build the message by calling `order.confirmationMessage()`, then add the resulting string to `sent`.
2. Confirm no other class reaches into `Order` to build a confirmation message itself.

## Step 3 — Same exercise for `Notifier.handle`

1. `handle(Order order)` in `Notifier.java` (lines 31–35) builds a short shipment-notification message the same way: it pulls `order.getCustomerEmail()` and `order.getId()` and assembles a string.
2. Move that string-building onto `Order` as a method (e.g. `shipmentMessage()`). The body of `handle` should then be two lines like `notifyCustomer` ended up.

## When you're done

1. Stop plugin recording.
2. The facilitator will open your analysis report — take a look.
3. Answer two short questions (verbal is fine):
   - Which feedback item, if any, was most useful?
   - Did anything in the report make you want to do something differently in the next session?
