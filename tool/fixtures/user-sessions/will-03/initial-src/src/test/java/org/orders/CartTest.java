package org.orders;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CartTest {

    @Test
    void subtotalAddsLineTotals() {
        Cart cart = new Cart();
        cart.add(new LineItem("A", 2, 500, 100));
        cart.add(new LineItem("B", 3, 200, 50));
        assertEquals(1_000 + 600, cart.subtotalCents());
    }

    @Test
    void duplicateSkuMergesQuantities() {
        Cart cart = new Cart();
        cart.add(new LineItem("A", 1, 500, 100));
        cart.add(new LineItem("A", 2, 500, 100));
        assertEquals(1, cart.size());
        assertEquals(1_500, cart.subtotalCents());
    }

    @Test
    void removeBySku() {
        Cart cart = new Cart();
        cart.add(new LineItem("A", 1, 500, 100));
        cart.remove("A");
        assertTrue(cart.isEmpty());
    }

    @Test
    void checkoutOnEmptyCartFails() {
        Cart cart = new Cart();
        assertThrows(IllegalStateException.class, cart::checkout);
    }
}
