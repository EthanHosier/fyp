# Session S05 — Misplaced behaviour

Target time: ~25 minutes.

## Before you start

1. From `tool/fixtures/`, run `./reset-for-user-study-session.sh`.
2. In IntelliJ, click the "Reload from disk" toolbar button (or wait a few seconds).
3. Start plugin recording.

## Prompts

1. `Notifier.notifyCustomer(Order order)` reads a lot of fields from `Order` — `getCustomerEmail`, `getCustomerName`, `getItems`, `getTotalCents`, `getShippingStreet/City/Postcode` — and computes the line-item subtotal itself. Move the message-building work onto `Order` (e.g. a `confirmationMessage()` method) so `Notifier.notifyCustomer` just asks `Order` for the formatted message and delivers it.
2. After the move, the original `Notifier.notifyCustomer` should be a few lines at most. If it still looks like it could be inlined into its callers, decide whether to leave it as a thin wrapper or fold it in.
3. If time remains: do the same exercise for `Notifier.handle(Order)`.

Work through them in any order.

## When you're done

1. Stop plugin recording.
2. The facilitator will open your analysis report — take a look.
3. Answer two short questions (verbal is fine):
   - Which feedback item, if any, was most useful?
   - Did anything in the report make you want to do something differently in the next session?
