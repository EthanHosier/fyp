package org.orders;

public class ShippingCalculator {

    public long process(Order order) {
        return process(order.totalWeightGrams(), order.getShippingPostcode());
    }

    public long process(int weightGrams, String postcode) {
        long base;
        if (weightGrams < 500) {
            base = 299;
        } else if (weightGrams < 2000) {
            base = 599;
        } else {
            base = 1299;
        }
        long zoneFee = zoneFee(postcode);
        return base + zoneFee;
    }

    private long zoneFee(String postcode) {
        if (postcode == null || postcode.isBlank()) {
            return 0;
        }
        char first = Character.toUpperCase(postcode.charAt(0));
        if (first == 'W' || first == 'E' || first == 'N' || first == 'S') {
            return 0;
        }
        if (first >= 'A' && first <= 'M') {
            return 200;
        }
        return 400;
    }
}
