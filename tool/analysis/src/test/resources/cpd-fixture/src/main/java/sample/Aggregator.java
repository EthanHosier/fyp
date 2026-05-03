package sample;

public class Aggregator {

    public int totalPositive(int[] values) {
        int total = 0;
        for (int x : values) {
            if (x > 0) {
                total += x;
            }
        }
        return total;
    }
}
