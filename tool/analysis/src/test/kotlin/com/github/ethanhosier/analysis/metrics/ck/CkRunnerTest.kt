package com.github.ethanhosier.analysis.metrics.ck

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CkRunnerTest {

    @Test
    fun `runs CK on a tiny java fixture and returns one class entry`() {
        val fixture = Path.of("src/test/resources/ck-fixture")
        val result = CkRunner().run(fixture)

        println(result);
        assertTrue(result.parseErrors.isEmpty(), "unexpected parse errors: ${result.parseErrors}")
        assertEquals(1, result.perClass.size, "expected exactly one class entry, got ${result.perClass}")

        val greeter = result.perClass.single()
        assertEquals("sample.Greeter", greeter.className)
        assertEquals("class", greeter.type)
        // Greeter has 5 public methods (constructor + greet + sum + safe + getCallCount).
        // CK counts the constructor as a method, so expect 5.
        assertEquals(5, greeter.numberOfMethods)
        assertEquals(3, greeter.numberOfFields)
        assertTrue(greeter.loc > 10, "loc should be > 10, was ${greeter.loc}")
        assertTrue(greeter.loopQty >= 1, "loopQty should count the for-each loop, was ${greeter.loopQty}")
        assertTrue(greeter.tryCatchQty >= 1, "tryCatchQty should count the try/catch, was ${greeter.tryCatchQty}")
        // file path is relativized to the fixture root.
        assertEquals("src/main/java/sample/Greeter.java", greeter.file)
    }
}
