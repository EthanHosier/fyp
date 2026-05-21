# Session S06 — Reshape and cleanup

Target time: ~25 minutes.

## Before you start

1. From `tool/fixtures/`, run `./reset-for-user-study-session.sh`.
2. In IntelliJ, click the "Reload from disk" toolbar button (or wait a few seconds).
3. Start plugin recording.

## Step 1 — Split `OrderService.processOrder`

`OrderService.processOrder(CheckoutRequest req)` (lines 44–124 of `OrderService.java`) runs six things in sequence inline. Pull each into its own private method on `OrderService` so the top-level body reads as a short list of named steps:

| Phase                        | Current lines | Suggested method                                            |
|------------------------------|---------------|-------------------------------------------------------------|
| Idempotency check            | 48–50         | `checkIdempotency(req)`                                     |
| Inline validation            | 52–75         | `validateRequest(req)`                                      |
| Inventory reservation        | 77–81         | `reserveStock(items)`                                       |
| Total calculation            | 83–94         | `computeGrandTotal(req)` → `long`                           |
| Payment + rollback           | 96–100        | `chargeOrRollback(items, grandTotal, method)` → `Result`    |
| Order construction + notify  | 102–122       | `buildAndNotify(req, items, result)` → `Order`              |

The final shape of `processOrder` should be roughly a dozen lines: each helper called in turn, plus the existing `inventory.commit(items)` between charge and build-and-notify and the `submittedIds.add(req.idempotencyKey)` at the end.

## Step 2 — Remove the dead branch in `PaymentProcessor.charge`

In `PaymentProcessor.java`, the block at lines 23–25:

```java
if (method == null && subtotalCents < 0) {
    return new Result(false, 0, "unreachable legacy guard");
}
```

can never run — the guards on lines 17 and 20 already throw when `method == null` or when `subtotalCents < 0`. Remove the block.

## Step 3 — Remove the dead helper in `OrderService`

The private method `calculateDiscountLegacy(long subtotal)` at line 155 of `OrderService.java` has no callers. Remove the whole method.

## When you're done

1. Stop plugin recording.
2. The facilitator will open your analysis report — take a look.
3. Answer two short questions (verbal is fine):
   - Which feedback item, if any, was most useful?
   - Did anything in the report make you want to do something differently in the next session?
