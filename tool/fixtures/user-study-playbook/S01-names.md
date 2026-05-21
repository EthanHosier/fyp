# Session S01 — Names

Target time: ~25 minutes.

## Before you start

1. From `tool/fixtures/`, run `./reset-for-user-study-session.sh`.
2. In IntelliJ, click the "Reload from disk" toolbar button (or wait a few seconds).
3. Start plugin recording.

## Prompts

1. In `InventoryManager.java`, rename the method `doIt(String sku, int n)` to something that describes what it does — it increments the reserved-quantity entry for a sku.
2. In `Notifier.java`, rename the method `handle(Order order)` — it sends a shipment-notification message to the customer.
3. In `ShippingCalculator.java`, rename the method `process(Order order)` — it computes the shipping fee for an order. (The two-argument `process(int weightGrams, String postcode)` overload should be renamed consistently.)

Work through them in any order.

## When you're done

1. Stop plugin recording.
2. The facilitator will open your analysis report — take a look.
3. Answer two short questions (verbal is fine):
   - Which feedback item, if any, was most useful?
   - Did anything in the report make you want to do something differently in the next session?
