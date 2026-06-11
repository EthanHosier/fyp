package org.orders;

import java.util.ArrayList;
import java.util.List;

public class Notifier {

    private final List<String> sent = new ArrayList<>();

    public void notifyCustomer(Order order) {
        StringBuilder sb = new StringBuilder();
        sb.append("To: ").append(order.getCustomerEmail()).append("\n");
        sb.append("Hi ").append(order.getCustomerName()).append(",\n");
        sb.append("Your order ").append(order.getId()).append(" is confirmed.\n");
        sb.append("Items:\n");
        long subtotal = 0;
        for (LineItem li : order.getItems()) {
            sb.append(" - ").append(li.getSku())
              .append(" x ").append(li.getQty())
              .append(" @ ").append(li.getUnitPriceCents()).append("c\n");
            subtotal += li.lineTotalCents();
        }
        sb.append("Subtotal: ").append(subtotal).append("c\n");
        sb.append("Total charged: ").append(order.getTotalCents()).append("c\n");
        sb.append("Shipping to: ").append(order.getShippingStreet())
          .append(", ").append(order.getShippingCity())
          .append(" ").append(order.getShippingPostcode()).append("\n");
        sent.add(sb.toString());
    }

    public void handle(Order order) {
        StringBuilder sb = new StringBuilder();
        sb.append("To: ").append(order.getCustomerEmail()).append("\n");
        sb.append("Order ").append(order.getId()).append(" has shipped.\n");
        sent.add(sb.toString());
    }

    public List<String> sentMessages() {
        return List.copyOf(sent);
    }

    public int sentCount() {
        return sent.size();
    }
}
