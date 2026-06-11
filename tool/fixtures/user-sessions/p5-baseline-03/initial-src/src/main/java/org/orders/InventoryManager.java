package org.orders;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InventoryManager {

    private final Map<String, Integer> stock = new HashMap<>();
    private final Map<String, Integer> reserved = new HashMap<>();

    public void seed(String sku, int qty) {
        stock.put(sku, qty);
    }

    public int available(String sku) {
        int total = stock.getOrDefault(sku, 0);
        int held = reserved.getOrDefault(sku, 0);
        return total - held;
    }

    public boolean reserve(List<LineItem> items) {
        LinkedHashMap<String, Integer> staged = new LinkedHashMap<>();
        for (LineItem li : items) {
            int need = staged.getOrDefault(li.getSku(), 0) + li.getQty();
            if (available(li.getSku()) < need) {
                return false;
            }
            staged.put(li.getSku(), need);
        }
        for (Map.Entry<String, Integer> e : staged.entrySet()) {
            doIt(e.getKey(), e.getValue());
        }
        return true;
    }

    public void release(List<LineItem> items) {
        for (LineItem li : items) {
            int held = reserved.getOrDefault(li.getSku(), 0);
            int next = Math.max(0, held - li.getQty());
            if (next == 0) {
                reserved.remove(li.getSku());
            } else {
                reserved.put(li.getSku(), next);
            }
        }
    }

    public void commit(List<LineItem> items) {
        for (LineItem li : items) {
            int held = reserved.getOrDefault(li.getSku(), 0);
            int next = Math.max(0, held - li.getQty());
            if (next == 0) {
                reserved.remove(li.getSku());
            } else {
                reserved.put(li.getSku(), next);
            }
            int onHand = stock.getOrDefault(li.getSku(), 0);
            stock.put(li.getSku(), Math.max(0, onHand - li.getQty()));
        }
    }

    private void doIt(String sku, int n) {
        int held = reserved.getOrDefault(sku, 0);
        reserved.put(sku, held + n);
    }
}
