package org.orders;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ShippingCalculatorTest {

    @Test
    void belowSmallTier() {
        assertEquals(299, new ShippingCalculator().process(499, "W1A"));
    }

    @Test
    void atSmallTierBoundary() {
        assertEquals(599, new ShippingCalculator().process(500, "W1A"));
    }

    @Test
    void belowLargeTierBoundary() {
        assertEquals(599, new ShippingCalculator().process(1999, "W1A"));
    }

    @Test
    void atLargeTierBoundary() {
        assertEquals(1299, new ShippingCalculator().process(2000, "W1A"));
    }
}
