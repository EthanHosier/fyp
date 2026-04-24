package com.github.ethanhosier.analysis.refactoring

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * End-to-end coverage for [RefactoringClient]: one real Equinox
 * framework is booted in [BeforeAll], reused across every test, and
 * torn down in [AfterAll]. Each test uses its own [TempDir]-rooted
 * fake worktree.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RefactoringClientTest {

    private lateinit var client: RefactoringClient
    private lateinit var dataArea: Path

    @BeforeAll
    fun setUp() {
        dataArea = Files.createTempDirectory("refactoring-client-test-")
        client = RefactoringClientFactory.create(dataArea.resolve("osgi"))
    }

    @AfterAll
    fun tearDown() {
        client.close()
        dataArea.toFile().deleteRecursively()
    }

    @Test
    fun `extract method rewrites single file`(@TempDir worktree: Path) {
        val src = worktree.resolve("src").also(Path::createDirectories)
        val file = src.resolve("org/example/OrderPricingService.java")
        file.parent.createDirectories()
        file.writeText(
            """
            package org.example;

            public class OrderPricingService {
                public double priceFor(String tier, double total) {
                    if (tier.equals("gold")) {
                        double discount = total * 0.2;
                        double taxed = (total - discount) * 1.2;
                        return taxed - 5.0;
                    }
                    return total;
                }
            }
            """.trimIndent(),
        )

        val outcome = client.extractMethod(
            ExtractMethodRequest(
                projectRoot = worktree,
                sourceFolders = listOf("src"),
                classpathJars = emptyList(),
                relativeFilePath = "src/org/example/OrderPricingService.java",
                startLine = 6,
                endLine = 8,
                newMethodName = "handleGold",
            ),
        )

        val success = assertIs<RefactoringOutcome.Success>(outcome, "outcome=$outcome")
        assertEquals(1, success.changedFiles.size, "exactly one file should have changed")

        val rewritten = Files.readString(file)
        assertContains(rewritten, "handleGold")
        assertContains(rewritten, "private")
    }

    @Test
    fun `rename method updates call sites across files`(@TempDir worktree: Path) {
        val src = worktree.resolve("src").also(Path::createDirectories)
        val greeter = src.resolve("com/example/Greeter.java")
        val caller = src.resolve("com/example/Caller.java")
        greeter.parent.createDirectories()

        greeter.writeText(
            """
            package com.example;

            public class Greeter {
                public String greet(String who) {
                    return hello(who);
                }

                public String hello(String who) {
                    return "hi " + who;
                }
            }
            """.trimIndent(),
        )
        caller.writeText(
            """
            package com.example;

            public class Caller {
                public String callIt() {
                    return new Greeter().hello("world");
                }
            }
            """.trimIndent(),
        )

        val outcome = client.renameMethod(
            RenameMethodRequest(
                projectRoot = worktree,
                sourceFolders = listOf("src"),
                classpathJars = emptyList(),
                declaringTypeFqn = "com.example.Greeter",
                oldName = "hello",
                newName = "sayHi",
            ),
        )

        val success = assertIs<RefactoringOutcome.Success>(outcome, "outcome=$outcome")
        assertEquals(
            2,
            success.changedFiles.size,
            "rename should touch both Greeter.java and Caller.java; got ${success.changedFiles}",
        )

        val rewrittenGreeter = Files.readString(greeter)
        val rewrittenCaller = Files.readString(caller)
        assertContains(rewrittenGreeter, "sayHi")
        assertFalse("hello" in rewrittenGreeter, "old name gone from Greeter")
        assertContains(rewrittenCaller, "sayHi")
        assertFalse("hello" in rewrittenCaller, "old name gone from Caller")
    }

    @Test
    fun `failed extract returns Failed rather than throwing`(@TempDir worktree: Path) {
        val src = worktree.resolve("src").also(Path::createDirectories)
        val file = src.resolve("org/example/Box.java")
        file.parent.createDirectories()
        file.writeText(
            """
            package org.example;

            public class Box {
                public int value = 42;
            }
            """.trimIndent(),
        )

        // Lines 3..4 straddle the class declaration and a field — not a
        // clean statement selection, so ExtractMethod should refuse.
        val outcome = client.extractMethod(
            ExtractMethodRequest(
                projectRoot = worktree,
                sourceFolders = listOf("src"),
                classpathJars = emptyList(),
                relativeFilePath = "src/org/example/Box.java",
                startLine = 3,
                endLine = 4,
                newMethodName = "bogus",
            ),
        )

        val failed = assertIs<RefactoringOutcome.Failed>(outcome, "outcome=$outcome")
        assertTrue(failed.reason.isNotBlank(), "reason must not be blank")

        // Client must still be usable after a failure.
        val after = client.extractMethod(
            ExtractMethodRequest(
                projectRoot = worktree,
                sourceFolders = listOf("src"),
                classpathJars = emptyList(),
                relativeFilePath = "src/org/example/Box.java",
                startLine = 3,
                endLine = 4,
                newMethodName = "bogusAgain",
            ),
        )
        assertIs<RefactoringOutcome.Failed>(after, "second call after failure still works")
    }
}
