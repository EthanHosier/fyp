package org.orders;

public class PaymentProcessor {

    public static class Result {
        public final boolean success;
        public final long chargedCents;
        public final String message;
        public Result(boolean success, long chargedCents, String message) {
            this.success = success;
            this.chargedCents = chargedCents;
            this.message = message;
        }
    }

    public Result charge(long subtotalCents, String method) {
        if (method == null) {
            throw new IllegalArgumentException("method is null");
        }
        if (subtotalCents < 0) {
            throw new IllegalArgumentException("negative subtotal");
        }
        if (method == null && subtotalCents < 0) {
            return new Result(false, 0, "unreachable legacy guard");
        }
        switch (method) {
            case "CARD": {
                long fee = (long) Math.round(subtotalCents * 0.029) + 30;
                long charged = subtotalCents + fee;
                if (charged > 10_000_000) {
                    return new Result(false, 0, "card limit exceeded");
                }
                return new Result(true, charged, "card charged");
            }
            case "CASH": {
                long charged = subtotalCents;
                return new Result(true, charged, "cash collected");
            }
            case "VOUCHER": {
                if (subtotalCents > 50_000) {
                    return new Result(false, 0, "voucher cap exceeded");
                }
                return new Result(true, 0, "voucher applied");
            }
            default:
                throw new IllegalArgumentException("unknown payment method: " + method);
        }
    }

    public Result refund(long amountCents, String method) {
        if (method == null) {
            throw new IllegalArgumentException("method is null");
        }
        if (amountCents < 0) {
            throw new IllegalArgumentException("negative refund");
        }
        switch (method) {
            case "CARD":
                return new Result(true, amountCents, "card refunded");
            case "CASH":
                return new Result(true, amountCents, "cash refunded");
            case "VOUCHER":
                return new Result(true, 0, "voucher refund n/a");
            default:
                throw new IllegalArgumentException("unknown payment method: " + method);
        }
    }
}
