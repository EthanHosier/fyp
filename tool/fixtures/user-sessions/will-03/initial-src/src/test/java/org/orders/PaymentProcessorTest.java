package org.orders;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PaymentProcessorTest {

    @Test
    void cardChargesSubtotalPlusFee() {
        PaymentProcessor.Result r = new PaymentProcessor().charge(10_000, "CARD");
        assertTrue(r.success);
        // 10000 + round(10000 * 0.029) + 30 = 10000 + 290 + 30 = 10320
        assertEquals(10_320, r.chargedCents);
    }

    @Test
    void cashChargesNoFee() {
        PaymentProcessor.Result r = new PaymentProcessor().charge(5_000, "CASH");
        assertTrue(r.success);
        assertEquals(5_000, r.chargedCents);
    }

    @Test
    void voucherChargesZeroBelowCap() {
        PaymentProcessor.Result r = new PaymentProcessor().charge(1_000, "VOUCHER");
        assertTrue(r.success);
        assertEquals(0, r.chargedCents);
    }

    @Test
    void negativeSubtotalRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new PaymentProcessor().charge(-1, "CARD"));
    }

    @Test
    void unknownMethodRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new PaymentProcessor().charge(100, "BTC"));
    }
}
