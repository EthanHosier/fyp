package org.orders;

import java.util.List;

public class OrderValidator {

    public void validate(Cart cart, String customerEmail, String shippingStreet,
                         String shippingCity, String shippingPostcode) {
        if (cart == null || cart.isEmpty()) {
            throw new IllegalStateException("cart is empty");
        }
        List<LineItem> items = cart.snapshot();
        for (LineItem li : items) {
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
