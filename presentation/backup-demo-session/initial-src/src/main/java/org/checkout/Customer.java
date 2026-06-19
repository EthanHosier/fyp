package org.checkout;

public class Customer {

    private final String id;
    private final String name;
    private final boolean premium;

    public Customer(String id, String name, boolean premium) {
        this.id = id;
        this.name = name;
        this.premium = premium;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public boolean isPremium() { return premium; }
}
