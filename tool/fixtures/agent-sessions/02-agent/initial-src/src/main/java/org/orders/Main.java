package org.orders;

public class Main {
    public static void main(String[] args) {
        InventoryManager inventory = new InventoryManager();
        inventory.seed("BOOK-A", 10);
        inventory.seed("BOOK-B", 5);

        OrderService service = new OrderService(
                inventory,
                new PaymentProcessor(),
                new ShippingCalculator(),
                new Notifier(),
                new OrderValidator()
        );

        Cart cart = new Cart();
        cart.add(new LineItem("BOOK-A", 2, 1500, 400));
        cart.add(new LineItem("BOOK-B", 1, 800, 300));

        OrderService.CheckoutRequest req = new OrderService.CheckoutRequest();
        req.cart = cart;
        req.customerEmail = "demo@example.com";
        req.customerName = "Demo User";
        req.shippingStreet = "1 Demo Lane";
        req.shippingCity = "London";
        req.shippingPostcode = "W1A 1AA";
        req.paymentMethod = "CARD";

        Order order = service.processOrder(req);
        System.out.println("placed: " + order.getId() + " total=" + order.getTotalCents() + "c");
    }
}
