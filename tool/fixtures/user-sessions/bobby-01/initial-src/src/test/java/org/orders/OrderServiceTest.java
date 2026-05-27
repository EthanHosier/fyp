package org.orders;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class OrderServiceTest {

    private InventoryManager inventory;
    private PaymentProcessor payments;
    private ShippingCalculator shipping;
    private Notifier notifier;
    private OrderValidator validator;
    private OrderService service;

    @BeforeEach
    void setup() {
        inventory = new InventoryManager();
        inventory.seed("A", 10);
        inventory.seed("B", 5);
        payments = new PaymentProcessor();
        shipping = new ShippingCalculator();
        notifier = new Notifier();
        validator = new OrderValidator();
        service = new OrderService(inventory, payments, shipping, notifier, validator);
    }

    private OrderService.CheckoutRequest baseRequest() {
        Cart cart = new Cart();
        cart.add(new LineItem("A", 2, 1000, 300));
        OrderService.CheckoutRequest req = new OrderService.CheckoutRequest();
        req.cart = cart;
        req.customerEmail = "user@example.com";
        req.customerName = "User";
        req.shippingStreet = "1 Test St";
        req.shippingCity = "London";
        req.shippingPostcode = "W1A 1AA";
        req.paymentMethod = "CARD";
        return req;
    }

    @Test
    void happyPathProducesPaidOrderAndSendsOneNotification() {
        Order order = service.processOrder(baseRequest());
        assertEquals(OrderStatus.PAID, order.getStatus());
        assertTrue(order.getTotalCents() > 0);
        assertEquals(1, notifier.sentCount());
    }

    @Test
    void inventoryIsDecrementedAfterCheckout() {
        service.processOrder(baseRequest());
        assertEquals(8, inventory.available("A"));
    }

    @Test
    void emptyCartIsRejected() {
        OrderService.CheckoutRequest req = baseRequest();
        req.cart = new Cart();
        assertThrows(IllegalStateException.class, () -> service.processOrder(req));
    }

    @Test
    void negativeQuantityIsRejected() {
        OrderService.CheckoutRequest req = baseRequest();
        Cart cart = new Cart();
        cart.add(new LineItem("A", -1, 1000, 300));
        req.cart = cart;
        assertThrows(IllegalStateException.class, () -> service.processOrder(req));
    }

    @Test
    void missingShippingPostcodeIsRejected() {
        OrderService.CheckoutRequest req = baseRequest();
        req.shippingPostcode = "";
        assertThrows(IllegalStateException.class, () -> service.processOrder(req));
    }

    @Test
    void paymentFailureReleasesReservation() {
        OrderService.CheckoutRequest req = baseRequest();
        Cart big = new Cart();
        big.add(new LineItem("A", 1, 100_000, 300));
        req.cart = big;
        req.paymentMethod = "VOUCHER";
        assertThrows(IllegalStateException.class, () -> service.processOrder(req));
        assertEquals(10, inventory.available("A"));
    }

    @Test
    void voucherPathChargesZero() {
        OrderService.CheckoutRequest req = baseRequest();
        Cart cart = new Cart();
        cart.add(new LineItem("A", 1, 1000, 300));
        req.cart = cart;
        req.paymentMethod = "VOUCHER";
        Order order = service.processOrder(req);
        assertEquals(0, order.getTotalCents());
    }

    @Test
    void duplicateIdempotencyKeyIsRejected() {
        OrderService.CheckoutRequest req = baseRequest();
        req.idempotencyKey = "key-1";
        service.processOrder(req);

        OrderService.CheckoutRequest req2 = baseRequest();
        req2.idempotencyKey = "key-1";
        assertThrows(IllegalStateException.class, () -> service.processOrder(req2));
    }
}
