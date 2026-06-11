# Session S02 — Magic numbers

Target time: ~25 minutes.

[//]: # (## Before you start)

[//]: # ()
[//]: # (1. From `tool/fixtures/`, run `./reset-for-user-study-session.sh`.)

[//]: # (2. In IntelliJ, click the "Reload from disk" toolbar button &#40;or wait a few seconds&#41;.)

[//]: # (3. Start plugin recording.)

## Step 1 — `PaymentProcessor` CARD branch

1. Open `PaymentProcessor.java`. In `charge(...)` look at the CARD branch (lines 27–34).
2. The literal `0.029` on line 28 is the percentage card fee. Give it a name (e.g. `CARD_FEE_RATE`).
3. The literal `30` on the same line is a fixed fee in cents added on top of the percentage. Give it a name (e.g. `CARD_FIXED_FEE_CENTS`).
4. The literal `10_000_000` on line 30 is the upper cents limit a single card charge can hit. Give it a name (e.g. `CARD_LIMIT_CENTS`).

## Step 2 — `PaymentProcessor` VOUCHER branch

1. In `charge(...)`'s VOUCHER branch (lines 39–43), the literal `50_000` on line 40 is the cents cap above which a voucher is rejected.
2. Give it a name (e.g. `VOUCHER_CAP_CENTS`).

## Step 3 — `ShippingCalculator`

1. Open `ShippingCalculator.java`. In `process(int weightGrams, String postcode)` (lines 9–20):
   - The weight thresholds `500` and `2000` (lines 11 and 13) separate the small / medium / large weight tiers.
   - The base fees `299`, `599`, `1299` (lines 12, 14, 16) are the per-tier base shipping fees in cents.
2. In `zoneFee(...)` (lines 22–32):
   - The literals `200` (line 29) and `400` (line 31) are the inner-zone and outer-zone fees in cents.
3. Give all of those names that explain what they mean.

## When you're done

1. Stop plugin recording.
2. The facilitator will open your analysis report — take a look.

[//]: # (3. Answer two short questions &#40;verbal is fine&#41;:)

[//]: # (   - Which feedback item, if any, was most useful?)

[//]: # (   - Did anything in the report make you want to do something differently in the next session?)
