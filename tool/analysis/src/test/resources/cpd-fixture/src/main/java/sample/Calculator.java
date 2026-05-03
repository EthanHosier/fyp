package sample;

public class Calculator {

    public int sumPositives(int[] xs) {
        int total = 0;
        for (int x : xs) {
            if (x > 0) {
                total += x;
            }
        }
        return total;
    }

    public int sumPositivesAgain(int[] xs) {
        int total = 0;
        for (int x : xs) {
            if (x > 0) {
                total += x;
            }
        }
        return total;
    }
}
