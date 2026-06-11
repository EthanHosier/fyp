package sample;

import java.util.ArrayList;
import java.util.List;

public class Greeter {
    private final String name;
    private final List<String> log = new ArrayList<>();
    private int callCount;

    public Greeter(String name) {
        this.name = name;
    }

    public String greet() {
        callCount++;
        log.add("greet");
        return "hello " + name;
    }

    public int sum(int[] xs) {
        int total = 0;
        for (int x : xs) {
            total += x;
        }
        return total;
    }

    public String safe(String raw) {
        try {
            return raw.trim();
        } catch (NullPointerException e) {
            return "";
        }
    }

    public int getCallCount() {
        return callCount;
    }
}
