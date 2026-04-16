package sample;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HelloTest {

    @Test
    public void passes() {
        assertEquals(2, 1 + 1);
    }

    @Test
    public void fails() {
        assertEquals(99, 1 + 1, "deliberate failure for fixture");
    }

    @Disabled("deliberately skipped for fixture")
    @Test
    public void skipped() {
        assertEquals(1, 1);
    }
}
