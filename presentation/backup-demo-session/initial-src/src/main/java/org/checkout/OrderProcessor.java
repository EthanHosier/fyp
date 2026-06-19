package org.checkout;

import java.util.List;

public class OrderProcessor {

    public OrderResult checkout(Customer customer, List<OrderItem> items) {
        OrderResult result = new OrderResult();

        // Validate the customer and cart.
        String error = null;
        if (customer == null) {
            error = "No customer provided";
        } else if (items == null || items.isEmpty()) {
            error = "Cart is empty";
        } else {
            for (OrderItem it : items) {
                if (it.getQuantity() <= 0) {
                    error = "Invalid quantity for " + it.getSku();
                    break;
                }
                if (it.getUnitPrice() < 0) {
                    error = "Invalid price for " + it.getSku();
                    break;
                }
            }
        }
        if (error != null) {
            result.setSuccess(false);
            result.getMessages().add(error);
            return result;
        }

        // Compute the taxed amount
        double taxed = 0.0;
        for (OrderItem it : items) {
            taxed = taxed + it.getUnitPrice() * it.getQuantity();
        }
        double taxRate = 1.20;
        if (taxed > 100.0) {
            taxRate = 1.15;
        }
        if (customer.isPremium()) {
            taxRate = taxRate - 0.05;
        }
        taxed = taxed * taxRate;

        // Compute the final total from bulk and premium discounts.
        double discount = 1.0;
        int totalQty = 0;
        for (OrderItem it : items) {
            totalQty = totalQty + it.getQuantity();
        }
        if (totalQty >= 10) {
            discount = 0.90;
        } else if (totalQty >= 5) {
            discount = 0.95;
        }
        if (customer.isPremium()) {
            discount = discount * 0.95;
        }
        double finalTotal = Math.round(taxed * discount * 100.0) / 100.0;

        result.setTotal(finalTotal);
        result.setSuccess(true);

        // Append confirmation messages for the receipt.
        result.getMessages().add("Order confirmed for " + customer.getName());
        result.getMessages().add("Items: " + items.size());
        if (customer.isPremium()) {
            result.getMessages().add("Thanks for being a premium member, " + customer.getName());
        }
        result.getMessages().add("Total due: " + finalTotal);

        return result;
    }
}
