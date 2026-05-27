package org.orders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Cart {

    private final LinkedHashMap<String, LineItem> items = new LinkedHashMap<>();

    public void add(LineItem item) {
        if (item == null) {
            throw new IllegalArgumentException("item is null");
        }
        LineItem existing = items.get(item.getSku());
        if (existing == null) {
            items.put(item.getSku(), item);
        } else {
            int combined = existing.getQty() + item.getQty();
            items.put(item.getSku(), new LineItem(
                    existing.getSku(), combined, existing.getUnitPriceCents(), existing.getWeightGrams()
            ));
        }
    }

    public void remove(String sku) {
        items.remove(sku);
    }

    public List<LineItem> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(items.values()));
    }

    public long subtotalCents() {
        long total = 0;
        for (LineItem item : items.values()) {
            total += item.lineTotalCents();
        }
        return total;
    }

    public int totalWeightGrams() {
        int total = 0;
        for (LineItem item : items.values()) {
            total += item.totalWeightGrams();
        }
        return total;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public int size() {
        return items.size();
    }

    private void validate() {
        if (items.isEmpty()) {
            throw new IllegalStateException("cart is empty");
        }
        for (Map.Entry<String, LineItem> e : items.entrySet()) {
            LineItem li = e.getValue();
            if (li.getQty() <= 0) {
                throw new IllegalStateException("non-positive qty for sku " + li.getSku());
            }
            if (li.getUnitPriceCents() < 0) {
                throw new IllegalStateException("negative price for sku " + li.getSku());
            }
        }
    }

    public void checkout() {
        validate();
    }
}
