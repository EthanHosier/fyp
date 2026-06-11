package org.orders;

public class LineItem {
    private final String sku;
    private final int qty;
    private final long unitPriceCents;
    private final int weightGrams;

    public LineItem(String sku, int qty, long unitPriceCents, int weightGrams) {
        this.sku = sku;
        this.qty = qty;
        this.unitPriceCents = unitPriceCents;
        this.weightGrams = weightGrams;
    }

    public String getSku() { return sku; }
    public int getQty() { return qty; }
    public long getUnitPriceCents() { return unitPriceCents; }
    public int getWeightGrams() { return weightGrams; }

    public long lineTotalCents() {
        return unitPriceCents * qty;
    }

    public int totalWeightGrams() {
        return weightGrams * qty;
    }
}
