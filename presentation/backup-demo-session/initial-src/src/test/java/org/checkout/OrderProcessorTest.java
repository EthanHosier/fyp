package org.checkout;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderProcessorTest {

    private final OrderProcessor processor = new OrderProcessor();

    @Test
    void checkoutAppliesTaxAndReturnsTotal() {
        Customer alice = new Customer("c1", "Alice", false);
        OrderResult result = processor.checkout(alice, List.of(new OrderItem("A", 1, 10.0)));

        assertTrue(result.isSuccess());
        assertEquals(12.0, result.getTotal(), 0.001);
    }

    @Test
    void checkoutAppliesBulkDiscount() {
        Customer bob = new Customer("c2", "Bob", false);
        OrderResult result = processor.checkout(bob, List.of(new OrderItem("A", 10, 10.0)));

        assertTrue(result.isSuccess());
        // subtotal=100, taxed=120, bulk 10% off => 108.00
        assertEquals(108.0, result.getTotal(), 0.001);
    }

    @Test
    void checkoutRejectsEmptyCart() {
        Customer carol = new Customer("c3", "Carol", true);
        OrderResult result = processor.checkout(carol, List.of());

        assertFalse(result.isSuccess());
        assertTrue(result.getMessages().stream().anyMatch(m -> m.contains("empty")));
    }
}
