# Session S01 — Names

Target time: ~25 minutes.

[//]: # (## Before you start)

[//]: # ()
[//]: # (1. From `tool/fixtures/`, run `./reset-for-user-study-session.sh`.)

[//]: # (2. In IntelliJ, click the "Reload from disk" toolbar button &#40;or wait a few seconds&#41;.)

[//]: # (3. Start plugin recording.)

## Step 1 — `InventoryManager.doIt`

1. Open `InventoryManager.java`. The private method `doIt(String sku, int n)` is at line 64 — it adds `n` to the reserved-quantity entry for `sku`.
2. Rename it to something that describes that (e.g. `addReservation`). Make sure the single call site at line 33 (inside `reserve(...)`) ends up consistent.

## Step 2 — `Notifier.handle`

1. Open `Notifier.java`. The public method `handle(Order order)` is at line 31 — it sends a shipment-notification message to the customer.
2. Rename it to something that describes that (e.g. `notifyShipped`).

## Step 3 — `ShippingCalculator.process`

1. Open `ShippingCalculator.java`. There are two overloads of `process` (lines 5 and 9) — they compute the shipping fee.
2. Rename both overloads to something that describes that (e.g. `calculateShippingFee`). The single call site in `OrderService.processOrder` (around line 91, `shipping.process(...)`) should end up consistent.

## When you're done

1. Stop plugin recording.
2. The facilitator will open your analysis report — take a look.

[//]: # (3. Answer two short questions &#40;verbal is fine&#41;:)

[//]: # (   - Which feedback item, if any, was most useful?)

[//]: # (   - Did anything in the report make you want to do something differently in the next session?)
