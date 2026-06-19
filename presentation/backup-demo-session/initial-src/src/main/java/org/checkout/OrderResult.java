package org.checkout;

import java.util.ArrayList;
import java.util.List;

public class OrderResult {

    private boolean success;
    private double total;
    private final List<String> messages = new ArrayList<>();

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public double getTotal() { return total; }
    public void setTotal(double total) { this.total = total; }

    public List<String> getMessages() { return messages; }
}
