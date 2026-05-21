package org.orders;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class OrderService {

    private final InventoryManager inventory;
    private final PaymentProcessor payments;
    private final ShippingCalculator shipping;
    private final Notifier notifier;
    private final OrderValidator validator;
    private final Set<String> submittedIds = new HashSet<>();

    public OrderService(InventoryManager inventory,
                        PaymentProcessor payments,
                        ShippingCalculator shipping,
                        Notifier notifier,
                        OrderValidator validator) {
        this.inventory = inventory;
        this.payments = payments;
        this.shipping = shipping;
        this.notifier = notifier;
        this.validator = validator;
    }

    public static class CheckoutRequest {
        public Cart cart;
        public String customerEmail;
        public String customerName;
        public String shippingStreet;
        public String shippingCity;
        public String shippingPostcode;
        public String billingStreet;
        public String billingCity;
        public String billingPostcode;
        public String paymentMethod;
        public String idempotencyKey;
    }

    public Order processOrder(CheckoutRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("request is null");
        }
        if (req.idempotencyKey != null && submittedIds.contains(req.idempotencyKey)) {
            throw new IllegalStateException("duplicate submission");
        }

        validateRequest(req);

        List<LineItem> items = req.cart.snapshot();
        boolean reserved = inventory.reserve(items);
        if (!reserved) {
            throw new IllegalStateException("insufficient stock");
        }

        long grandTotal = computeGrandTotal(req);

        PaymentProcessor.Result result = payments.charge(grandTotal, req.paymentMethod);
        if (!result.success) {
            inventory.release(items);
            throw new IllegalStateException("payment failed: " + result.message);
        }

        inventory.commit(items);

        Order order = buildAndNotify(req, items, result);

        if (req.idempotencyKey != null) {
            submittedIds.add(req.idempotencyKey);
        }
        return order;
    }

    private Order buildAndNotify(CheckoutRequest req, List<LineItem> items, PaymentProcessor.Result result) {
        String billingStreet = req.billingStreet != null ? req.billingStreet : req.shippingStreet;
        String billingCity = req.billingCity != null ? req.billingCity : req.shippingCity;
        String billingPostcode = req.billingPostcode != null ? req.billingPostcode : req.shippingPostcode;

        Order order = new Order(
                UUID.randomUUID().toString(),
                req.customerEmail,
                req.customerName,
                req.shippingStreet, req.shippingCity, req.shippingPostcode,
                billingStreet, billingCity, billingPostcode,
                items,
                Instant.now()
        );
        order.setTotalCents(result.chargedCents);
        order.setStatus(OrderStatus.PAID);

        notifier.notifyCustomer(order);
        return order;
    }

    private void validateRequest(CheckoutRequest req) {
        if (req.cart == null || req.cart.isEmpty()) {
            throw new IllegalStateException("cart is empty");
        }
        for (LineItem li : req.cart.snapshot()) {
            if (li.getQty() <= 0) {
                throw new IllegalStateException("non-positive qty for sku " + li.getSku());
            }
            if (li.getUnitPriceCents() < 0) {
                throw new IllegalStateException("negative price for sku " + li.getSku());
            }
        }
        if (req.customerEmail == null || req.customerEmail.isBlank()) {
            throw new IllegalStateException("missing customer email");
        }
        if (req.shippingStreet == null || req.shippingStreet.isBlank()) {
            throw new IllegalStateException("missing shipping street");
        }
        if (req.shippingCity == null || req.shippingCity.isBlank()) {
            throw new IllegalStateException("missing shipping city");
        }
        if (req.shippingPostcode == null || req.shippingPostcode.isBlank()) {
            throw new IllegalStateException("missing shipping postcode");
        }
    }

    private long computeGrandTotal(CheckoutRequest req) {
        long subtotal = req.cart.subtotalCents();
        long shippingCents;
        if (req.cart.totalWeightGrams() < 500) {
            shippingCents = 299;
        } else if (req.cart.totalWeightGrams() < 2000) {
            shippingCents = 599;
        } else {
            shippingCents = 1299;
        }
        long shippingFromCalc = shipping.process(req.cart.totalWeightGrams(), req.shippingPostcode);
        long preTax = subtotal + shippingFromCalc;
        long tax = (long) Math.round(preTax * 0.2);
        return preTax + tax;
    }

    private void validate(Cart cart, String customerEmail,
                          String shippingStreet, String shippingCity, String shippingPostcode) {
        if (cart == null || cart.isEmpty()) {
            throw new IllegalStateException("cart is empty");
        }
        for (LineItem li : cart.snapshot()) {
            if (li.getQty() <= 0) {
                throw new IllegalStateException("non-positive qty for sku " + li.getSku());
            }
            if (li.getUnitPriceCents() < 0) {
                throw new IllegalStateException("negative price for sku " + li.getSku());
            }
        }
        if (customerEmail == null || customerEmail.isBlank()) {
            throw new IllegalStateException("missing customer email");
        }
        if (shippingStreet == null || shippingStreet.isBlank()) {
            throw new IllegalStateException("missing shipping street");
        }
        if (shippingCity == null || shippingCity.isBlank()) {
            throw new IllegalStateException("missing shipping city");
        }
        if (shippingPostcode == null || shippingPostcode.isBlank()) {
            throw new IllegalStateException("missing shipping postcode");
        }
    }

}
