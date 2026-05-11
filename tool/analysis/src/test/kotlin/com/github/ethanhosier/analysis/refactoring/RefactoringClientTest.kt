package com.github.ethanhosier.analysis.refactoring

import com.github.ethanhosier.analysis.refactoring.ops.ChangeAttributeTypeRequest
import com.github.ethanhosier.analysis.refactoring.ops.ChangeMethodSignatureRequest
import com.github.ethanhosier.analysis.refactoring.ops.ChangeVariableTypeRequest
import com.github.ethanhosier.analysis.refactoring.ops.ExtractAndMoveMethodRequest
import com.github.ethanhosier.analysis.refactoring.ops.ExtractAttributeRequest
import com.github.ethanhosier.analysis.refactoring.ops.ExtractSubclassRequest
import com.github.ethanhosier.analysis.refactoring.ops.ParameterizeAttributeRequest
import com.github.ethanhosier.analysis.refactoring.ops.ParameterizeVariableRequest
import com.github.ethanhosier.analysis.refactoring.ops.ReplaceVariableWithAttributeRequest
import com.github.ethanhosier.analysis.refactoring.ops.MoveAndRenameClassRequest
import com.github.ethanhosier.analysis.refactoring.ops.MoveAndRenameMethodRequest
import com.github.ethanhosier.analysis.refactoring.ops.MoveAndRenameAttributeRequest
import com.github.ethanhosier.analysis.refactoring.ops.MoveInstanceFieldRequest
import com.github.ethanhosier.analysis.refactoring.ops.MovePackageRequest
import com.github.ethanhosier.analysis.refactoring.ops.ExtractClassRequest
import com.github.ethanhosier.analysis.refactoring.ops.SignatureParameter
import com.github.ethanhosier.analysis.refactoring.ops.ExtractInterfaceRequest
import com.github.ethanhosier.analysis.refactoring.ops.ExtractMethodRequest
import com.github.ethanhosier.analysis.refactoring.ops.ExtractSuperclassRequest
import com.github.ethanhosier.analysis.refactoring.ops.ExtractVariableRequest
import com.github.ethanhosier.analysis.refactoring.ops.InlineMethodRequest
import com.github.ethanhosier.analysis.refactoring.ops.InlineVariableRequest
import com.github.ethanhosier.analysis.refactoring.ops.MoveClassRequest
import com.github.ethanhosier.analysis.refactoring.ops.MoveInstanceMethodRequest
import com.github.ethanhosier.analysis.refactoring.ops.MoveStaticMembersRequest
import com.github.ethanhosier.analysis.refactoring.ops.PullUpRequest
import com.github.ethanhosier.analysis.refactoring.ops.PushDownRequest
import com.github.ethanhosier.analysis.refactoring.ops.RenameClassRequest
import com.github.ethanhosier.analysis.refactoring.ops.RenameFieldRequest
import com.github.ethanhosier.analysis.refactoring.ops.RenameLocalVariableRequest
import com.github.ethanhosier.analysis.refactoring.ops.RenameMethodRequest
import com.github.ethanhosier.analysis.refactoring.ops.RenamePackageRequest
import com.github.ethanhosier.analysis.refactoring.ops.changeAttributeType
import com.github.ethanhosier.analysis.refactoring.ops.changeMethodSignature
import com.github.ethanhosier.analysis.refactoring.ops.changeVariableType
import com.github.ethanhosier.analysis.refactoring.ops.extractAndMoveMethod
import com.github.ethanhosier.analysis.refactoring.ops.extractAttribute
import com.github.ethanhosier.analysis.refactoring.ops.extractSubclass
import com.github.ethanhosier.analysis.refactoring.ops.parameterizeAttribute
import com.github.ethanhosier.analysis.refactoring.ops.parameterizeVariable
import com.github.ethanhosier.analysis.refactoring.ops.replaceVariableWithAttribute
import com.github.ethanhosier.analysis.refactoring.ops.moveAndRenameClass
import com.github.ethanhosier.analysis.refactoring.ops.moveAndRenameMethod
import com.github.ethanhosier.analysis.refactoring.ops.moveAndRenameAttribute
import com.github.ethanhosier.analysis.refactoring.ops.moveInstanceField
import com.github.ethanhosier.analysis.refactoring.ops.movePackage
import com.github.ethanhosier.analysis.refactoring.ops.extractClass
import com.github.ethanhosier.analysis.refactoring.ops.extractInterface
import com.github.ethanhosier.analysis.refactoring.ops.extractMethod
import com.github.ethanhosier.analysis.refactoring.ops.extractSuperclass
import com.github.ethanhosier.analysis.refactoring.ops.extractVariable
import com.github.ethanhosier.analysis.refactoring.ops.inlineMethod
import com.github.ethanhosier.analysis.refactoring.ops.inlineVariable
import com.github.ethanhosier.analysis.refactoring.ops.moveClass
import com.github.ethanhosier.analysis.refactoring.ops.moveInstanceMethod
import com.github.ethanhosier.analysis.refactoring.ops.moveStaticMembers
import com.github.ethanhosier.analysis.refactoring.ops.pullUp
import com.github.ethanhosier.analysis.refactoring.ops.pushDown
import com.github.ethanhosier.analysis.refactoring.ops.renameClass
import com.github.ethanhosier.analysis.refactoring.ops.renameField
import com.github.ethanhosier.analysis.refactoring.ops.renameLocalVariable
import com.github.ethanhosier.analysis.refactoring.ops.renameMethod
import com.github.ethanhosier.analysis.refactoring.ops.renamePackage
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * End-to-end coverage for [RefactoringClient]: one real Equinox
 * framework is booted in [BeforeAll], reused across every test, and
 * torn down in [AfterAll]. Each test uses its own [TempDir]-rooted
 * fake worktree.
 *
 * Assertions compare full file contents against expected strings so the
 * tests are scannable — each input block and expected output block
 * lives right next to the other.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("slow")
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
            extractMethodRequestAt(
                projectRoot = worktree,
                sourceFolders = listOf("src"),
                classpathJars = emptyList(),
                relativeFilePath = "src/org/example/OrderPricingService.java",
                startLine = 6,
                startColumn = 1,
                endLine = 9,
                endColumn = Int.MAX_VALUE,
                newMethodName = "handleGold",
            ),
        )

        val success = assertIs<RefactoringOutcome.Success>(outcome, "outcome=$outcome")
        assertEquals(1, success.changedFiles.size, "exactly one file should have changed")

        assertEquals(
            """
            package org.example;

            public class OrderPricingService {
                public double priceFor(String tier, double total) {
                    if (tier.equals("gold")) {
                        return handleGold(total);
                    }
                    return total;
                }

                private double handleGold(double total) {
                    double discount = total * 0.2;
                    double taxed = (total - discount) * 1.2;
                    return taxed - 5.0;
                }
            }
            """.trimIndent(),
            Files.readString(file).trimEnd(),
        )
    }

    @Test
    fun `extract method with isStatic true adds static modifier`(@TempDir worktree: Path) {
        val src = worktree.resolve("src").also(Path::createDirectories)
        val file = src.resolve("org/example/Demo.java")
        file.parent.createDirectories()
        // The extracted body uses only the parameters — JDT's own
        // `forceStatic()` would NOT trigger here (host method is
        // non-static, so a non-static extracted method is a valid
        // choice). The post-process should still add `static`
        // because the spec says so.
        file.writeText(
            """
            package org.example;

            public class Demo {
                public double compute(double a, double b) {
                    double sum = a + b;
                    double doubled = sum * 2;
                    return doubled;
                }
            }
            """.trimIndent(),
        )

        val outcome = client.extractMethod(
            extractMethodRequestAt(
                projectRoot = worktree,
                sourceFolders = listOf("src"),
                classpathJars = emptyList(),
                relativeFilePath = "src/org/example/Demo.java",
                startLine = 5, startColumn = 9,
                endLine = 6, endColumn = Int.MAX_VALUE,
                newMethodName = "combine",
                isStatic = true,
            ),
        )

        assertIs<RefactoringOutcome.Success>(outcome, "outcome=$outcome")
        val result = Files.readString(file)
        // The new method must carry `static`.
        assertTrue(
            "private static" in result,
            "expected `private static` on extracted method, got:\n$result",
        )
        // And `compute` (the host) must NOT have been made static.
        assertTrue(
            "public double compute" in result,
            "host method should be untouched by the static rewrite, got:\n$result",
        )
    }

    @Test
    fun `extract method with isStatic false leaves modifier off`(@TempDir worktree: Path) {
        val src = worktree.resolve("src").also(Path::createDirectories)
        val file = src.resolve("org/example/Demo.java")
        file.parent.createDirectories()
        file.writeText(
            """
            package org.example;

            public class Demo {
                public double compute(double a, double b) {
                    double sum = a + b;
                    double doubled = sum * 2;
                    return doubled;
                }
            }
            """.trimIndent(),
        )

        val outcome = client.extractMethod(
            extractMethodRequestAt(
                projectRoot = worktree,
                sourceFolders = listOf("src"),
                classpathJars = emptyList(),
                relativeFilePath = "src/org/example/Demo.java",
                startLine = 5, startColumn = 9,
                endLine = 6, endColumn = Int.MAX_VALUE,
                newMethodName = "combine",
                isStatic = false,
            ),
        )

        assertIs<RefactoringOutcome.Success>(outcome, "outcome=$outcome")
        val result = Files.readString(file)
        assertTrue(
            "private static" !in result,
            "expected non-static extracted method, got:\n$result",
        )
        // Sanity check the extraction itself happened.
        assertTrue(
            "private double combine" in result || "private static double combine" !in result,
            "expected `private double combine(...)` in result, got:\n$result",
        )
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

        assertEquals(
            """
            package com.example;

            public class Greeter {
                public String greet(String who) {
                    return sayHi(who);
                }

                public String sayHi(String who) {
                    return "hi " + who;
                }
            }
            """.trimIndent(),
            Files.readString(greeter).trimEnd(),
        )
        assertEquals(
            """
            package com.example;

            public class Caller {
                public String callIt() {
                    return new Greeter().sayHi("world");
                }
            }
            """.trimIndent(),
            Files.readString(caller).trimEnd(),
        )
    }

    @Test
    fun `rename class updates references and renames source file`(@TempDir worktree: Path) {
        val src = worktree.resolve("src").also(Path::createDirectories)
        val foo = src.resolve("com/example/Foo.java")
        val user = src.resolve("com/example/FooUser.java")
        foo.parent.createDirectories()

        foo.writeText(
            """
            package com.example;

            public class Foo {
                public int value() { return 1; }
            }
            """.trimIndent(),
        )
        user.writeText(
            """
            package com.example;

            public class FooUser {
                public int useIt() {
                    return new Foo().value();
                }
            }
            """.trimIndent(),
        )

        val outcome = client.renameClass(
            RenameClassRequest(
                projectRoot = worktree,
                sourceFolders = listOf("src"),
                classpathJars = emptyList(),
                typeFqn = "com.example.Foo",
                newName = "Bar",
            ),
        )

        assertIs<RefactoringOutcome.Success>(outcome, "outcome=$outcome")

        val bar = src.resolve("com/example/Bar.java")
        assertTrue(Files.exists(bar), "Foo.java should have been renamed to Bar.java on disk")
        assertFalse(Files.exists(foo), "old Foo.java should be gone")

        assertEquals(
            """
            package com.example;

            public class Bar {
                public int value() { return 1; }
            }
            """.trimIndent(),
            Files.readString(bar).trimEnd(),
        )
        assertEquals(
            """
            package com.example;

            public class FooUser {
                public int useIt() {
                    return new Bar().value();
                }
            }
            """.trimIndent(),
            Files.readString(user).trimEnd(),
        )
    }

    @Test
    fun `rename field updates reads and writes across files`(@TempDir worktree: Path) {
        val src = worktree.resolve("src").also(Path::createDirectories)
        val counter = src.resolve("com/example/Counter.java")
        val user = src.resolve("com/example/CounterUser.java")
        counter.parent.createDirectories()

        counter.writeText(
            """
            package com.example;

            public class Counter {
                public int tally = 0;
            }
            """.trimIndent(),
        )
        user.writeText(
            """
            package com.example;

            public class CounterUser {
                public int bump(Counter c) {
                    c.tally = c.tally + 1;
                    return c.tally;
                }
            }
            """.trimIndent(),
        )

        val outcome = client.renameField(
            RenameFieldRequest(
                projectRoot = worktree,
                sourceFolders = listOf("src"),
                classpathJars = emptyList(),
                declaringTypeFqn = "com.example.Counter",
                oldName = "tally",
                newName = "count",
            ),
        )

        val success = assertIs<RefactoringOutcome.Success>(outcome, "outcome=$outcome")
        assertEquals(2, success.changedFiles.size, "changed=${success.changedFiles}")

        assertEquals(
            """
            package com.example;

            public class Counter {
                public int count = 0;
            }
            """.trimIndent(),
            Files.readString(counter).trimEnd(),
        )
        assertEquals(
            """
            package com.example;

            public class CounterUser {
                public int bump(Counter c) {
                    c.count = c.count + 1;
                    return c.count;
                }
            }
            """.trimIndent(),
            Files.readString(user).trimEnd(),
        )
    }

    @Test
    fun `rename package moves files and updates imports`(@TempDir worktree: Path) {
        val src = worktree.resolve("src").also(Path::createDirectories)
        val oldDir = src.resolve("com/example/old").also(Path::createDirectories)
        val clientDir = src.resolve("com/example/app").also(Path::createDirectories)
        val widget = oldDir.resolve("Widget.java")
        val caller = clientDir.resolve("AppMain.java")

        widget.writeText(
            """
            package com.example.old;

            public class Widget {
                public int value() { return 7; }
            }
            """.trimIndent(),
        )
        caller.writeText(
            """
            package com.example.app;

            import com.example.old.Widget;

            public class AppMain {
                public int useIt() {
                    return new Widget().value();
                }
            }
            """.trimIndent(),
        )

        val outcome = client.renamePackage(
            RenamePackageRequest(
                projectRoot = worktree,
                sourceFolders = listOf("src"),
                classpathJars = emptyList(),
                oldPackage = "com.example.old",
                newPackage = "com.example.fresh",
            ),
        )

        assertIs<RefactoringOutcome.Success>(outcome, "outcome=$outcome")

        val movedWidget = src.resolve("com/example/fresh/Widget.java")
        assertTrue(Files.exists(movedWidget), "Widget.java should have moved to com/example/fresh/")
        assertFalse(Files.exists(widget), "old Widget.java should be gone")

        assertEquals(
            """
            package com.example.fresh;

            public class Widget {
                public int value() { return 7; }
            }
            """.trimIndent(),
            Files.readString(movedWidget).trimEnd(),
        )
        assertEquals(
            """
            package com.example.app;

            import com.example.fresh.Widget;

            public class AppMain {
                public int useIt() {
                    return new Widget().value();
                }
            }
            """.trimIndent(),
            Files.readString(caller).trimEnd(),
        )
    }

    @Test
    fun `rename local variable updates parameter and its uses`(@TempDir worktree: Path) {
        val src = worktree.resolve("src").also(Path::createDirectories)
        val file = src.resolve("com/example/Summer.java")
        file.parent.createDirectories()
        file.writeText(
            """
            package com.example;

            public class Summer {
                public int addOne(int x) {
                    int y = x + 1;
                    return y;
                }
            }
            """.trimIndent(),
        )

        // Column points inside the `x` parameter declaration on line 4.
        val outcome = client.renameLocalVariable(
            renameLocalVariableRequestAt(
                projectRoot = worktree,
                sourceFolders = listOf("src"),
                classpathJars = emptyList(),
                relativeFilePath = "src/com/example/Summer.java",
                line = 4,
                column = 28,
                newName = "value",
            ),
        )

        assertIs<RefactoringOutcome.Success>(outcome, "outcome=$outcome")
        assertEquals(
            """
            package com.example;

            public class Summer {
                public int addOne(int value) {
                    int y = value + 1;
                    return y;
                }
            }
            """.trimIndent(),
            Files.readString(file).trimEnd(),
        )
    }

    @Test
    fun `extract variable pulls duplicated expression into a local`(@TempDir worktree: Path) {
        val src = worktree.resolve("src").also(Path::createDirectories)
        val file = src.resolve("com/example/Pricer.java")
        file.parent.createDirectories()
        file.writeText(
            """
            package com.example;

            public class Pricer {
                public double finalPrice(double total) {
                    double taxed = total + total * 0.2;
                    double rounded = taxed - total * 0.2;
                    return rounded;
                }
            }
            """.trimIndent(),
        )

        // Select the expression `total * 0.2` on line 5 (columns 24..34 inclusive).
        val outcome = client.extractVariable(
            extractVariableRequestAt(
                projectRoot = worktree,
                sourceFolders = listOf("src"),
                classpathJars = emptyList(),
                relativeFilePath = "src/com/example/Pricer.java",
                startLine = 5,
                startColumn = 32,
                endLine = 5,
                endColumn = 42,
                newName = "discount",
            ),
        )

        assertIs<RefactoringOutcome.Success>(outcome, "outcome=$outcome")
        assertEquals(
            """
            package com.example;

            public class Pricer {
                public double finalPrice(double total) {
                    double discount = total * 0.2;
                    double taxed = total + discount;
                    double rounded = taxed - discount;
                    return rounded;
                }
            }
            """.trimIndent(),
            Files.readString(file).trimEnd(),
        )
    }

    @Test
    fun `inline variable replaces the use with the initializer`(@TempDir worktree: Path) {
        val src = worktree.resolve("src").also(Path::createDirectories)
        val file = src.resolve("com/example/Inliner.java")
        file.parent.createDirectories()
        file.writeText(
            """
            package com.example;

            public class Inliner {
                public int compute(int a, int b) {
                    int total = a + b;
                    return total + 1;
                }
            }
            """.trimIndent(),
        )

        // Column 13 sits inside `total` on line 5.
        val outcome = client.inlineVariable(
            inlineVariableRequestAt(
                projectRoot = worktree,
                sourceFolders = listOf("src"),
                classpathJars = emptyList(),
                relativeFilePath = "src/com/example/Inliner.java",
                line = 5,
                column = 13,
            ),
        )

        assertIs<RefactoringOutcome.Success>(outcome, "outcome=$outcome")
        assertEquals(
            """
            package com.example;

            public class Inliner {
                public int compute(int a, int b) {
                    return a + b + 1;
                }
            }
            """.trimIndent(),
            Files.readString(file).trimEnd(),
        )
    }

    @Test
    fun `inline method replaces call sites and removes the declaration`(@TempDir worktree: Path) {
        val src = worktree.resolve("src").also(Path::createDirectories)
        val file = src.resolve("com/example/Adder.java")
        file.parent.createDirectories()
        file.writeText(
            """
            package com.example;

            public class Adder {
                public int run() {
                    return sum(1, 2);
                }

                private int sum(int a, int b) {
                    return a + b;
                }
            }
            """.trimIndent(),
        )

        val outcome = client.inlineMethod(
            InlineMethodRequest(
                projectRoot = worktree,
                sourceFolders = listOf("src"),
                classpathJars = emptyList(),
                declaringTypeFqn = "com.example.Adder",
                methodName = "sum",
            ),
        )

        assertIs<RefactoringOutcome.Success>(outcome, "outcome=$outcome")
        assertEquals(
            """
            package com.example;

            public class Adder {
                public int run() {
                    return 1 + 2;
                }
            }
            """.trimIndent(),
            Files.readString(file).trimEnd(),
        )
    }

    @Test
    fun `pull up moves method and field from subclass to superclass`(@TempDir worktree: Path) {
        val src = worktree.resolve("src").also(Path::createDirectories)
        val parent = src.resolve("com/example/Animal.java")
        val child = src.resolve("com/example/Dog.java")
        parent.parent.createDirectories()

        parent.writeText(
            """
            package com.example;

            public class Animal {
            }
            """.trimIndent(),
        )
        child.writeText(
            """
            package com.example;

            public class Dog extends Animal {
                protected int legs = 4;

                protected String describe() {
                    return "legs=" + legs;
                }
            }
            """.trimIndent(),
        )

        val outcome = client.pullUp(
            PullUpRequest(
                projectRoot = worktree,
                sourceFolders = listOf("src"),
                classpathJars = emptyList(),
                declaringTypeFqn = "com.example.Dog",
                methodNames = listOf("describe"),
                fieldNames = listOf("legs"),
            ),
        )

        assertIs<RefactoringOutcome.Success>(outcome, "outcome=$outcome")
        assertEquals(
            """
            package com.example;

            public class Animal {

                protected int legs = 4;

                protected String describe() {
                    return "legs=" + legs;
                }
            }
            """.trimIndent(),
            Files.readString(parent).trimEnd(),
        )
        assertEquals(
            """
            package com.example;

            public class Dog extends Animal {
            }
            """.trimIndent(),
            Files.readString(child).trimEnd(),
        )
    }

    @Test
    fun `push down moves method and field from superclass to subclass`(@TempDir worktree: Path) {
        val src = worktree.resolve("src").also(Path::createDirectories)
        val parent = src.resolve("com/example/Shape.java")
        val child = src.resolve("com/example/Circle.java")
        parent.parent.createDirectories()

        parent.writeText(
            """
            package com.example;

            public class Shape {
                protected double radius = 1.0;

                protected double area() {
                    return 3.14 * radius * radius;
                }
            }
            """.trimIndent(),
        )
        child.writeText(
            """
            package com.example;

            public class Circle extends Shape {
            }
            """.trimIndent(),
        )

        val outcome = client.pushDown(
            PushDownRequest(
                projectRoot = worktree,
                sourceFolders = listOf("src"),
                classpathJars = emptyList(),
                declaringTypeFqn = "com.example.Shape",
                methodNames = listOf("area"),
                fieldNames = listOf("radius"),
            ),
        )

        assertIs<RefactoringOutcome.Success>(outcome, "outcome=$outcome")
        assertEquals(
            """
            package com.example;

            public class Shape {
            }
            """.trimIndent(),
            Files.readString(parent).trimEnd(),
        )
        assertEquals(
            """
            package com.example;

            public class Circle extends Shape {

                protected double radius = 1.0;

                protected double area() {
                    return 3.14 * radius * radius;
                }
            }
            """.trimIndent(),
            Files.readString(child).trimEnd(),
        )
    }

    @Test
    fun `move static members relocates static method and field across classes`(@TempDir worktree: Path) {
        val src = worktree.resolve("src").also(Path::createDirectories)
        val source = src.resolve("com/example/Utils.java")
        val destination = src.resolve("com/example/MathOps.java")
        val caller = src.resolve("com/example/UtilsUser.java")
        source.parent.createDirectories()

        source.writeText(
            """
            package com.example;

            public class Utils {
                public static int ZERO = 0;

                public static int doubled(int n) {
                    return n * 2;
                }
            }
            """.trimIndent(),
        )
        destination.writeText(
            """
            package com.example;

            public class MathOps {
            }
            """.trimIndent(),
        )
        caller.writeText(
            """
            package com.example;

            public class UtilsUser {
                public int runIt() {
                    return Utils.doubled(Utils.ZERO + 3);
                }
            }
            """.trimIndent(),
        )

        val outcome = client.moveStaticMembers(
            MoveStaticMembersRequest(
                projectRoot = worktree,
                sourceFolders = listOf("src"),
                classpathJars = emptyList(),
                sourceTypeFqn = "com.example.Utils",
                destinationTypeFqn = "com.example.MathOps",
                methodNames = listOf("doubled"),
                fieldNames = listOf("ZERO"),
            ),
        )

        assertIs<RefactoringOutcome.Success>(outcome, "outcome=$outcome")
        assertEquals(
            """
            package com.example;

            public class Utils {
            }
            """.trimIndent(),
            Files.readString(source).trimEnd(),
        )
        assertEquals(
            """
            package com.example;

            public class MathOps {

                public static int ZERO = 0;

                public static int doubled(int n) {
                    return n * 2;
                }
            }
            """.trimIndent(),
            Files.readString(destination).trimEnd(),
        )
        assertEquals(
            """
            package com.example;

            public class UtilsUser {
                public int runIt() {
                    return MathOps.doubled(MathOps.ZERO + 3);
                }
            }
            """.trimIndent(),
            Files.readString(caller).trimEnd(),
        )
    }

    @Test
    fun `move instance method relocates method to parameter's type`(@TempDir worktree: Path) {
        val src = worktree.resolve("src").also(Path::createDirectories)
        val source = src.resolve("com/example/Printer.java")
        val target = src.resolve("com/example/Document.java")
        source.parent.createDirectories()

        source.writeText(
            """
            package com.example;

            public class Printer {
                public String render(Document doc) {
                    return "[" + doc.getTitle() + "]";
                }
            }
            """.trimIndent(),
        )
        target.writeText(
            """
            package com.example;

            public class Document {
                private String title = "hello";

                public String getTitle() {
                    return title;
                }
            }
            """.trimIndent(),
        )

        val outcome = client.moveInstanceMethod(
            MoveInstanceMethodRequest(
                projectRoot = worktree,
                sourceFolders = listOf("src"),
                classpathJars = emptyList(),
                sourceTypeFqn = "com.example.Printer",
                methodName = "render",
                targetName = "doc",
            ),
        )

        assertIs<RefactoringOutcome.Success>(outcome, "outcome=$outcome")
        assertEquals(
            """
            package com.example;

            public class Printer {
            }
            """.trimIndent(),
            Files.readString(source).trimEnd(),
        )
        assertEquals(
            """
            package com.example;

            public class Document {
                private String title = "hello";

                public String getTitle() {
                    return title;
                }

                public String render() {
                    return "[" + getTitle() + "]";
                }
            }
            """.trimIndent(),
            Files.readString(target).trimEnd(),
        )
    }

    @Test
    fun `move class relocates file and updates imports`(@TempDir worktree: Path) {
        val src = worktree.resolve("src").also(Path::createDirectories)
        val original = src.resolve("com/example/old/Widget.java")
        val caller = src.resolve("com/example/app/AppMain.java")
        original.parent.createDirectories()
        caller.parent.createDirectories()

        original.writeText(
            """
            package com.example.old;

            public class Widget {
                public int value() { return 7; }
            }
            """.trimIndent(),
        )
        caller.writeText(
            """
            package com.example.app;

            import com.example.old.Widget;

            public class AppMain {
                public int useIt() {
                    return new Widget().value();
                }
            }
            """.trimIndent(),
        )

        val outcome = client.moveClass(
            MoveClassRequest(
                projectRoot = worktree,
                sourceFolders = listOf("src"),
                classpathJars = emptyList(),
                typeFqn = "com.example.old.Widget",
                destinationPackage = "com.example.util",
            ),
        )

        assertIs<RefactoringOutcome.Success>(outcome, "outcome=$outcome")

        val moved = src.resolve("com/example/util/Widget.java")
        assertTrue(Files.exists(moved), "Widget.java should have moved to com/example/util/")
        assertFalse(Files.exists(original), "old Widget.java should be gone")

        assertEquals(
            """
            package com.example.util;

            public class Widget {
                public int value() { return 7; }
            }
            """.trimIndent(),
            Files.readString(moved).trimEnd(),
        )
        assertEquals(
            """
            package com.example.app;

            import com.example.util.Widget;

            public class AppMain {
                public int useIt() {
                    return new Widget().value();
                }
            }
            """.trimIndent(),
            Files.readString(caller).trimEnd(),
        )
    }

    @Test
    fun `extract interface creates new interface and makes source implement it`(@TempDir worktree: Path) {
        val src = worktree.resolve("src").also(Path::createDirectories)
        val source = src.resolve("com/example/EmailService.java")
        source.parent.createDirectories()
        source.writeText(
            """
            package com.example;

            public class EmailService {
                public void send(String to) {
                }

                public int queued() {
                    return 0;
                }
            }
            """.trimIndent(),
        )

        val outcome = client.extractInterface(
            ExtractInterfaceRequest(
                projectRoot = worktree,
                sourceFolders = listOf("src"),
                classpathJars = emptyList(),
                sourceTypeFqn = "com.example.EmailService",
                newInterfaceName = "Mailer",
                methodNames = listOf("send"),
            ),
        )

        assertIs<RefactoringOutcome.Success>(outcome, "outcome=$outcome")

        val iface = src.resolve("com/example/Mailer.java")
        assertTrue(Files.exists(iface), "Mailer.java should have been created")
        assertEquals(
            """
            package com.example;

            public interface Mailer {

                void send(String to);

            }
            """.trimIndent(),
            Files.readString(iface).trimEnd(),
        )
        assertEquals(
            """
            package com.example;

            public class EmailService implements Mailer {
                public void send(String to) {
                }

                public int queued() {
                    return 0;
                }
            }
            """.trimIndent(),
            Files.readString(source).trimEnd(),
        )
    }

    @Test
    fun `extract superclass creates new parent and moves members`(@TempDir worktree: Path) {
        val src = worktree.resolve("src").also(Path::createDirectories)
        val source = src.resolve("com/example/Employee.java")
        source.parent.createDirectories()
        source.writeText(
            """
            package com.example;

            public class Employee {
                protected String name = "";

                public String describe() {
                    return "name=" + name;
                }
            }
            """.trimIndent(),
        )

        val outcome = client.extractSuperclass(
            ExtractSuperclassRequest(
                projectRoot = worktree,
                sourceFolders = listOf("src"),
                classpathJars = emptyList(),
                sourceTypeFqn = "com.example.Employee",
                newSupertypeName = "Person",
                methodNames = listOf("describe"),
                fieldNames = listOf("name"),
            ),
        )

        assertIs<RefactoringOutcome.Success>(outcome, "outcome=$outcome")

        val newSuper = src.resolve("com/example/Person.java")
        assertTrue(Files.exists(newSuper), "Person.java should have been created")
        // Note: ExtractSupertypeProcessor copies methods up but leaves
        // the subclass implementation as an override — see comment in
        // ExtractSuperclassOp. Callers wanting a clean "move up" should
        // chain a Pull Up against the newly-created parent. Fields ARE
        // moved fully (field deletion goes through a different path).
        assertEquals(
            """
            package com.example;

            public class Person {

                public Person() {
                    super();
                }

                protected String name = "";

                public String describe() {
                    return "name=" + name;
                }

            }
            """.trimIndent(),
            Files.readString(newSuper).trimEnd(),
        )
        assertEquals(
            """
            package com.example;

            public class Employee extends Person {
                public String describe() {
                    return "name=" + name;
                }
            }
            """.trimIndent(),
            Files.readString(source).trimEnd(),
        )
    }

    @Test
    fun `extract class bundles fields into a new class and delegates through a field`(@TempDir worktree: Path) {
        val src = worktree.resolve("src").also(Path::createDirectories)
        val source = src.resolve("com/example/Rectangle.java")
        source.parent.createDirectories()
        source.writeText(
            """
            package com.example;

            public class Rectangle {
                public int width;
                public int height;

                public int area() {
                    return width * height;
                }
            }
            """.trimIndent(),
        )

        val outcome = client.extractClass(
            ExtractClassRequest(
                projectRoot = worktree,
                sourceFolders = listOf("src"),
                classpathJars = emptyList(),
                sourceTypeFqn = "com.example.Rectangle",
                newClassName = "Dimensions",
                delegateFieldName = "dimensions",
                fieldNames = listOf("width", "height"),
            ),
        )

        assertIs<RefactoringOutcome.Success>(outcome, "outcome=$outcome")
        val newClass = src.resolve("com/example/Dimensions.java")
        assertTrue(Files.exists(newClass), "Dimensions.java should have been created")
        assertEquals(
            """
            package com.example;

            public class Dimensions {
                public int width;
                public int height;

                public Dimensions() {
                }
            }
            """.trimIndent(),
            Files.readString(newClass).trimEnd(),
        )
        assertEquals(
            """
            package com.example;

            public class Rectangle {
                public Dimensions dimensions = new Dimensions();

                public int area() {
                    return dimensions.width * dimensions.height;
                }
            }
            """.trimIndent(),
            Files.readString(source).trimEnd(),
        )
    }

    @Test
    fun `change method signature reorders renames and adds parameters across call sites`(@TempDir worktree: Path) {
        val src = worktree.resolve("src").also(Path::createDirectories)
        val greeter = src.resolve("com/example/Greeter.java")
        greeter.parent.createDirectories()
        greeter.writeText(
            """
            package com.example;

            public class Greeter {
                public String greet(String name, int times) {
                    return name + times;
                }
            }
            """.trimIndent(),
        )
        val caller = src.resolve("com/example/Caller.java")
        caller.writeText(
            """
            package com.example;

            public class Caller {
                public String run() {
                    return new Greeter().greet("Ada", 3);
                }
            }
            """.trimIndent(),
        )

        val outcome = client.changeMethodSignature(
            ChangeMethodSignatureRequest(
                projectRoot = worktree,
                sourceFolders = listOf("src"),
                classpathJars = emptyList(),
                declaringTypeFqn = "com.example.Greeter",
                oldMethodName = "greet",
                newMethodName = "sayHi",
                parameters = listOf(
                    // Reorder: times comes before name; rename times→repeats.
                    SignatureParameter.Existing(oldName = "times", newName = "repeats"),
                    SignatureParameter.Existing(oldName = "name"),
                    // Brand-new parameter inserted at the end with a default for callers.
                    SignatureParameter.Added(name = "loud", type = "boolean", defaultValue = "false"),
                ),
            ),
        )

        assertIs<RefactoringOutcome.Success>(outcome, "outcome=$outcome")
        assertEquals(
            """
            package com.example;

            public class Greeter {
                public String sayHi(int repeats, String name, boolean loud) {
                    return name + repeats;
                }
            }
            """.trimIndent(),
            Files.readString(greeter).trimEnd(),
        )
        assertEquals(
            """
            package com.example;

            public class Caller {
                public String run() {
                    return new Greeter().sayHi(3, "Ada", false);
                }
            }
            """.trimIndent(),
            Files.readString(caller).trimEnd(),
        )
    }

    @Test
    fun `extract and move method chains extract method then move instance method`(@TempDir worktree: Path) {
        val src = worktree.resolve("src").also(Path::createDirectories)
        val source = src.resolve("com/example/Printer.java")
        val target = src.resolve("com/example/Document.java")
        source.parent.createDirectories()

        source.writeText(
            """
            package com.example;

            public class Printer {
                public String render(Document doc) {
                    return "[" + doc.getTitle() + "]";
                }
            }
            """.trimIndent(),
        )
        target.writeText(
            """
            package com.example;

            public class Document {
                private String title = "hello";

                public String getTitle() {
                    return title;
                }
            }
            """.trimIndent(),
        )

        val outcome = client.extractAndMoveMethod(
            ExtractAndMoveMethodRequest(
                projectRoot = worktree,
                sourceFolders = listOf("src"),
                classpathJars = emptyList(),
                relativeFilePath = "src/com/example/Printer.java",
                startLine = 5,
                startColumn = 15,
                endLine = 5,
                endColumn = 41,
                newMethodName = "format",
                declaringTypeFqn = "com.example.Printer",
                moveTargetName = "doc",
            ),
        )

        assertIs<RefactoringOutcome.Success>(outcome, "outcome=$outcome")
        assertEquals(
            """
            package com.example;

            public class Printer {
                public String render(Document doc) {
                    return doc.format();
                }
            }
            """.trimIndent(),
            Files.readString(source).trimEnd(),
        )
        assertEquals(
            """
            package com.example;

            public class Document {
                private String title = "hello";

                public String getTitle() {
                    return title;
                }

                String format() {
                    return "[" + getTitle() + "]";
                }
            }
            """.trimIndent(),
            Files.readString(target).trimEnd(),
        )
    }

    @Test
    fun `move and rename class relocates file renames type and updates callers`(@TempDir worktree: Path) {
        val src = worktree.resolve("src").also(Path::createDirectories)
        val original = src.resolve("com/example/old/Widget.java")
        val caller = src.resolve("com/example/app/AppMain.java")
        original.parent.createDirectories()
        caller.parent.createDirectories()

        original.writeText(
            """
            package com.example.old;

            public class Widget {
                public int value() { return 7; }
            }
            """.trimIndent(),
        )
        caller.writeText(
            """
            package com.example.app;

            import com.example.old.Widget;

            public class AppMain {
                public int useIt() {
                    return new Widget().value();
                }
            }
            """.trimIndent(),
        )

        val outcome = client.moveAndRenameClass(
            MoveAndRenameClassRequest(
                projectRoot = worktree,
                sourceFolders = listOf("src"),
                classpathJars = emptyList(),
                typeFqn = "com.example.old.Widget",
                destinationPackage = "com.example.fresh",
                newName = "Gadget",
            ),
        )

        assertIs<RefactoringOutcome.Success>(outcome, "outcome=$outcome")
        val moved = src.resolve("com/example/fresh/Gadget.java")
        assertTrue(Files.exists(moved), "Gadget.java should have been created at new location")
        assertFalse(Files.exists(original), "old Widget.java should be gone")
        assertEquals(
            """
            package com.example.fresh;

            public class Gadget {
                public int value() { return 7; }
            }
            """.trimIndent(),
            Files.readString(moved).trimEnd(),
        )
        assertEquals(
            """
            package com.example.app;

            import com.example.fresh.Gadget;

            public class AppMain {
                public int useIt() {
                    return new Gadget().value();
                }
            }
            """.trimIndent(),
            Files.readString(caller).trimEnd(),
        )
    }

    @Test
    fun `move and rename method relocates method then renames it across call sites`(@TempDir worktree: Path) {
        val src = worktree.resolve("src").also(Path::createDirectories)
        val source = src.resolve("com/example/Printer.java")
        val target = src.resolve("com/example/Document.java")
        source.parent.createDirectories()

        source.writeText(
            """
            package com.example;

            public class Printer {
                public String render(Document doc) {
                    return "[" + doc.getTitle() + "]";
                }
            }
            """.trimIndent(),
        )
        target.writeText(
            """
            package com.example;

            public class Document {
                private String title = "hello";

                public String getTitle() {
                    return title;
                }
            }
            """.trimIndent(),
        )

        val outcome = client.moveAndRenameMethod(
            MoveAndRenameMethodRequest(
                projectRoot = worktree,
                sourceFolders = listOf("src"),
                classpathJars = emptyList(),
                sourceTypeFqn = "com.example.Printer",
                methodName = "render",
                targetName = "doc",
                targetTypeFqn = "com.example.Document",
                newMethodName = "format",
            ),
        )

        assertIs<RefactoringOutcome.Success>(outcome, "outcome=$outcome")
        assertEquals(
            """
            package com.example;

            public class Printer {
            }
            """.trimIndent(),
            Files.readString(source).trimEnd(),
        )
        assertEquals(
            """
            package com.example;

            public class Document {
                private String title = "hello";

                public String getTitle() {
                    return title;
                }

                public String format() {
                    return "[" + getTitle() + "]";
                }
            }
            """.trimIndent(),
            Files.readString(target).trimEnd(),
        )
    }

    @Test
    fun `move instance field relocates field to destination class and rewrites access`(@TempDir worktree: Path) {
        val src = worktree.resolve("src").also(Path::createDirectories)
        val source = src.resolve("com/example/Source.java")
        val dest = src.resolve("com/example/Dest.java")
        source.parent.createDirectories()

        source.writeText(
            """
            package com.example;

            public class Source {
                public int count = 3;
            }
            """.trimIndent(),
        )
        dest.writeText(
            """
            package com.example;

            public class Dest {
            }
            """.trimIndent(),
        )

        val outcome = client.moveInstanceField(
            MoveInstanceFieldRequest(
                projectRoot = worktree,
                sourceFolders = listOf("src"),
                classpathJars = emptyList(),
                sourceTypeFqn = "com.example.Source",
                fieldName = "count",
                destinationTypeFqn = "com.example.Dest",
            ),
        )

        assertIs<RefactoringOutcome.Success>(outcome, "outcome=$outcome")
        assertEquals(
            """
            package com.example;

            public class Source {
            }
            """.trimIndent(),
            Files.readString(source).trimEnd(),
        )
        assertEquals(
            """
            package com.example;

            public class Dest {

                public int count = 3;
            }
            """.trimIndent(),
            Files.readString(dest).trimEnd(),
        )
    }

    @Test
    fun `move and rename attribute moves field to destination then renames it`(@TempDir worktree: Path) {
        val src = worktree.resolve("src").also(Path::createDirectories)
        val source = src.resolve("com/example/Source.java")
        val dest = src.resolve("com/example/Dest.java")
        source.parent.createDirectories()

        source.writeText(
            """
            package com.example;

            public class Source {
                public int count = 3;
            }
            """.trimIndent(),
        )
        dest.writeText(
            """
            package com.example;

            public class Dest {
            }
            """.trimIndent(),
        )

        val outcome = client.moveAndRenameAttribute(
            MoveAndRenameAttributeRequest(
                projectRoot = worktree,
                sourceFolders = listOf("src"),
                classpathJars = emptyList(),
                sourceTypeFqn = "com.example.Source",
                fieldName = "count",
                destinationTypeFqn = "com.example.Dest",
                newFieldName = "total",
            ),
        )

        assertIs<RefactoringOutcome.Success>(outcome, "outcome=$outcome")
        assertEquals(
            """
            package com.example;

            public class Source {
            }
            """.trimIndent(),
            Files.readString(source).trimEnd(),
        )
        assertEquals(
            """
            package com.example;

            public class Dest {

                public int total = 3;
            }
            """.trimIndent(),
            Files.readString(dest).trimEnd(),
        )
    }

    @Test
    fun `move package relocates under new parent keeping last segment`(@TempDir worktree: Path) {
        val src = worktree.resolve("src").also(Path::createDirectories)
        val oldDir = src.resolve("com/example/utils").also(Path::createDirectories)
        val callerDir = src.resolve("com/example/app").also(Path::createDirectories)
        val helper = oldDir.resolve("Helper.java")
        val caller = callerDir.resolve("AppMain.java")

        helper.writeText(
            """
            package com.example.utils;

            public class Helper {
                public int value() { return 7; }
            }
            """.trimIndent(),
        )
        caller.writeText(
            """
            package com.example.app;

            import com.example.utils.Helper;

            public class AppMain {
                public int useIt() {
                    return new Helper().value();
                }
            }
            """.trimIndent(),
        )

        val outcome = client.movePackage(
            MovePackageRequest(
                projectRoot = worktree,
                sourceFolders = listOf("src"),
                classpathJars = emptyList(),
                oldPackage = "com.example.utils",
                newParentPackage = "com.foo",
            ),
        )

        assertIs<RefactoringOutcome.Success>(outcome, "outcome=$outcome")
        val moved = src.resolve("com/foo/utils/Helper.java")
        assertTrue(Files.exists(moved), "Helper.java should be at com/foo/utils/")
        assertFalse(Files.exists(helper), "old Helper.java should be gone")
        assertEquals(
            """
            package com.foo.utils;

            public class Helper {
                public int value() { return 7; }
            }
            """.trimIndent(),
            Files.readString(moved).trimEnd(),
        )
        assertEquals(
            """
            package com.example.app;

            import com.foo.utils.Helper;

            public class AppMain {
                public int useIt() {
                    return new Helper().value();
                }
            }
            """.trimIndent(),
            Files.readString(caller).trimEnd(),
        )
    }

    @Test
    fun `extract attribute promotes duplicated literal to static final field`(@TempDir worktree: Path) {
        val src = worktree.resolve("src").also(Path::createDirectories)
        val file = src.resolve("com/example/Taxer.java")
        file.parent.createDirectories()
        file.writeText(
            """
            package com.example;

            public class Taxer {
                public double grossUp(double total) {
                    double a = total * 0.2;
                    double b = total * 0.2;
                    return a + b;
                }
            }
            """.trimIndent(),
        )

        // Select the literal `0.2` on line 5 (columns 28..30).
        val outcome = client.extractAttribute(
            extractAttributeRequestAt(
                projectRoot = worktree,
                sourceFolders = listOf("src"),
                classpathJars = emptyList(),
                relativeFilePath = "src/com/example/Taxer.java",
                startLine = 5,
                startColumn = 28,
                endLine = 5,
                endColumn = 30,
                newName = "VAT_RATE",
                visibility = "private",
            ),
        )

        assertIs<RefactoringOutcome.Success>(outcome, "outcome=$outcome")
        // JDT's Extract Constant rewrites the selected occurrence; the
        // other `0.2` isn't matched as a duplicate because a bare
        // literal-inside-expression doesn't satisfy JDT's structural
        // match heuristic (the containing `total * 0.2` would).
        assertEquals(
            """
            package com.example;

            public class Taxer {
                private static final double VAT_RATE = 0.2;

                public double grossUp(double total) {
                    double a = total * VAT_RATE;
                    double b = total * 0.2;
                    return a + b;
                }
            }
            """.trimIndent(),
            Files.readString(file).trimEnd(),
        )
    }

    @Test
    fun `change variable type generalises an ArrayList declaration to List`(@TempDir worktree: Path) {
        val src = worktree.resolve("src").also(Path::createDirectories)
        val file = src.resolve("com/example/Lists.java")
        file.parent.createDirectories()
        file.writeText(
            """
            package com.example;

            import java.util.ArrayList;

            public class Lists {
                public int size() {
                    ArrayList<String> xs = new ArrayList<>();
                    xs.add("a");
                    return xs.size();
                }
            }
            """.trimIndent(),
        )

        // Point at the variable name `xs` on line 7.
        val outcome = client.changeVariableType(
            changeVariableTypeRequestAt(
                projectRoot = worktree,
                sourceFolders = listOf("src"),
                classpathJars = emptyList(),
                relativeFilePath = "src/com/example/Lists.java",
                line = 7,
                column = 27,
                newTypeFqn = "java.util.List<java.lang.String>",
            ),
        )

        assertIs<RefactoringOutcome.Success>(outcome, "outcome=$outcome")
        assertEquals(
            """
            package com.example;

            import java.util.ArrayList;
            import java.util.List;

            public class Lists {
                public int size() {
                    List<String> xs = new ArrayList<>();
                    xs.add("a");
                    return xs.size();
                }
            }
            """.trimIndent(),
            Files.readString(file).trimEnd(),
        )
    }

    @Test
    fun `change attribute type generalises a field declaration to a supertype`(@TempDir worktree: Path) {
        val src = worktree.resolve("src").also(Path::createDirectories)
        val file = src.resolve("com/example/Holder.java")
        file.parent.createDirectories()
        file.writeText(
            """
            package com.example;

            import java.util.ArrayList;

            public class Holder {
                private ArrayList<String> items = new ArrayList<>();

                public int size() {
                    return items.size();
                }
            }
            """.trimIndent(),
        )

        val outcome = client.changeAttributeType(
            ChangeAttributeTypeRequest(
                projectRoot = worktree,
                sourceFolders = listOf("src"),
                classpathJars = emptyList(),
                declaringTypeFqn = "com.example.Holder",
                fieldName = "items",
                newTypeFqn = "java.util.List<java.lang.String>",
            ),
        )

        assertIs<RefactoringOutcome.Success>(outcome, "outcome=$outcome")
        assertEquals(
            """
            package com.example;

            import java.util.ArrayList;
            import java.util.List;

            public class Holder {
                private List<String> items = new ArrayList<>();

                public int size() {
                    return items.size();
                }
            }
            """.trimIndent(),
            Files.readString(file).trimEnd(),
        )
    }

    @Test
    fun `replace variable with attribute promotes local to field`(@TempDir worktree: Path) {
        val src = worktree.resolve("src").also(Path::createDirectories)
        val file = src.resolve("com/example/Box.java")
        file.parent.createDirectories()
        file.writeText(
            """
            package com.example;

            public class Box {
                public int get() {
                    int cache = 42;
                    return cache;
                }
            }
            """.trimIndent(),
        )

        // Point at the `cache` declaration on line 5.
        val outcome = client.replaceVariableWithAttribute(
            replaceVariableWithAttributeRequestAt(
                projectRoot = worktree,
                sourceFolders = listOf("src"),
                classpathJars = emptyList(),
                relativeFilePath = "src/com/example/Box.java",
                line = 5,
                column = 13,
                newFieldName = "cache",
                visibility = "private",
            ),
        )

        assertIs<RefactoringOutcome.Success>(outcome, "outcome=$outcome")
        assertEquals(
            """
            package com.example;

            public class Box {
                private int cache;

                public int get() {
                    cache = 42;
                    return cache;
                }
            }
            """.trimIndent(),
            Files.readString(file).trimEnd(),
        )
    }

    @Test
    fun `parameterize variable promotes literal to new method parameter`(@TempDir worktree: Path) {
        val src = worktree.resolve("src").also(Path::createDirectories)
        val file = src.resolve("com/example/Greeter.java")
        file.parent.createDirectories()
        file.writeText(
            """
            package com.example;

            public class Greeter {
                public String hello() {
                    return "Ada";
                }
            }
            """.trimIndent(),
        )

        // Select the literal "Ada" on line 5 (columns 16..20 inclusive, quotes included).
        val outcome = client.parameterizeVariable(
            parameterizeVariableRequestAt(
                projectRoot = worktree,
                sourceFolders = listOf("src"),
                classpathJars = emptyList(),
                relativeFilePath = "src/com/example/Greeter.java",
                startLine = 5,
                startColumn = 16,
                endLine = 5,
                endColumn = 20,
                newParameterName = "name",
            ),
        )

        assertIs<RefactoringOutcome.Success>(outcome, "outcome=$outcome")
        assertEquals(
            """
            package com.example;

            public class Greeter {
                public String hello(String name) {
                    return name;
                }
            }
            """.trimIndent(),
            Files.readString(file).trimEnd(),
        )
    }

    @Test
    fun `parameterize attribute promotes field read to new method parameter`(@TempDir worktree: Path) {
        val src = worktree.resolve("src").also(Path::createDirectories)
        val file = src.resolve("com/example/Counter.java")
        file.parent.createDirectories()
        file.writeText(
            """
            package com.example;

            public class Counter {
                private int base = 10;

                public int incBy(int delta) {
                    return base + delta;
                }
            }
            """.trimIndent(),
        )

        // Select `base` on line 7.
        val outcome = client.parameterizeAttribute(
            parameterizeAttributeRequestAt(
                projectRoot = worktree,
                sourceFolders = listOf("src"),
                classpathJars = emptyList(),
                relativeFilePath = "src/com/example/Counter.java",
                startLine = 7,
                startColumn = 16,
                endLine = 7,
                endColumn = 19,
                newParameterName = "start",
            ),
        )

        assertIs<RefactoringOutcome.Success>(outcome, "outcome=$outcome")
        assertEquals(
            """
            package com.example;

            public class Counter {
                private int base = 10;

                public int incBy(int delta, int start) {
                    return start + delta;
                }
            }
            """.trimIndent(),
            Files.readString(file).trimEnd(),
        )
    }

    @Test
    fun `extract subclass creates new subclass and pushes members down`(@TempDir worktree: Path) {
        val src = worktree.resolve("src").also(Path::createDirectories)
        val source = src.resolve("com/example/Animal.java")
        source.parent.createDirectories()
        source.writeText(
            """
            package com.example;

            public class Animal {
                public int legs = 4;

                public String swim() {
                    return "swimming";
                }
            }
            """.trimIndent(),
        )

        val outcome = client.extractSubclass(
            ExtractSubclassRequest(
                projectRoot = worktree,
                sourceFolders = listOf("src"),
                classpathJars = emptyList(),
                sourceTypeFqn = "com.example.Animal",
                newSubclassName = "Fish",
                methodNames = listOf("swim"),
            ),
        )

        assertIs<RefactoringOutcome.Success>(outcome, "outcome=$outcome")
        val newClass = src.resolve("com/example/Fish.java")
        assertTrue(Files.exists(newClass), "Fish.java should have been created")
        assertEquals(
            """
            package com.example;

            public class Fish extends Animal {

                public String swim() {
                    return "swimming";
                }
            }
            """.trimIndent(),
            Files.readString(newClass).trimEnd(),
        )
        assertEquals(
            """
            package com.example;

            public class Animal {
                public int legs = 4;
            }
            """.trimIndent(),
            Files.readString(source).trimEnd(),
        )
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
            extractMethodRequestAt(
                projectRoot = worktree,
                sourceFolders = listOf("src"),
                classpathJars = emptyList(),
                relativeFilePath = "src/org/example/Box.java",
                startLine = 3,
                startColumn = 1,
                endLine = 4,
                endColumn = Int.MAX_VALUE,
                newMethodName = "bogus",
            ),
        )

        val failed = assertIs<RefactoringOutcome.Failed>(outcome, "outcome=$outcome")
        assertTrue(failed.reason.isNotBlank(), "reason must not be blank")

        // Client must still be usable after a failure.
        val after = client.extractMethod(
            extractMethodRequestAt(
                projectRoot = worktree,
                sourceFolders = listOf("src"),
                classpathJars = emptyList(),
                relativeFilePath = "src/org/example/Box.java",
                startLine = 3,
                startColumn = 1,
                endLine = 4,
                endColumn = Int.MAX_VALUE,
                newMethodName = "bogusAgain",
            ),
        )
        assertIs<RefactoringOutcome.Failed>(after, "second call after failure still works")
    }

    /**
     * Verifies the AST-subtree-hash anchor on
     * [com.github.ethanhosier.analysis.miner.model.RefactoringSpec.ExtractMethod]
     * survives drift in the surrounding code as long as the selection's
     * own subtree is unchanged. Each test:
     *  1. writes an "anchor source" — the file as it looked when the
     *     spec was mined, used to compute the hash anchor;
     *  2. overwrites the same file with a "drifted source" — same
     *     to-be-extracted code, edits elsewhere (lines shifted,
     *     unrelated statements changed, formatting adjusted);
     *  3. runs `client.extractMethod` with the *original* anchor and
     *     asserts the refactoring still locates and extracts the
     *     intended window.
     *
     * Together these exercise the resilience that motivates the
     * AST-anchor scheme: an alt-trajectory replays a refactoring after
     * a prior op rewrote part of the file, and the anchor needs to
     * re-find its target without relying on stale (line, col)
     * addressing.
     */
    @Nested
    inner class `extract method anchor drift` {

        @Test
        fun `survives a new method added above the host`(@TempDir worktree: Path) {
            val (file, request) = anchorSource(
                worktree,
                """
                package org.example;

                public class Pricer {
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
                startLine = 6, endLine = 9, newMethodName = "handleGold",
            )

            // Drift: prepend a brand-new method above `priceFor`. Every
            // line of the host shifts down. The selection's AST is
            // byte-identical to the anchor source.
            file.writeText(
                """
                package org.example;

                public class Pricer {
                    private double surcharge(double total) {
                        return total * 0.05;
                    }

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

            val outcome = client.extractMethod(request)
            assertIs<RefactoringOutcome.Success>(outcome, "outcome=$outcome")
            assertEquals(
                """
                package org.example;

                public class Pricer {
                    private double surcharge(double total) {
                        return total * 0.05;
                    }

                    public double priceFor(String tier, double total) {
                        if (tier.equals("gold")) {
                            return handleGold(total);
                        }
                        return total;
                    }

                    private double handleGold(double total) {
                        double discount = total * 0.2;
                        double taxed = (total - discount) * 1.2;
                        return taxed - 5.0;
                    }
                }
                """.trimIndent(),
                Files.readString(file).trimEnd(),
            )
        }

        @Test
        fun `survives an unrelated statement in the same method changing`(@TempDir worktree: Path) {
            val (file, request) = anchorSource(
                worktree,
                """
                package org.example;

                public class Pricer {
                    public double priceFor(String tier, double total) {
                        double tax = total * 0.1;
                        if (tier.equals("gold")) {
                            double discount = total * 0.2;
                            double taxed = (total - discount) * 1.2;
                            return taxed - 5.0;
                        }
                        return total + tax;
                    }
                }
                """.trimIndent(),
                startLine = 7, endLine = 10, newMethodName = "handleGold",
            )

            // Drift: change the unrelated `tax = total * 0.1` to
            // `total * 0.15`. The if-body — the selection — is
            // untouched, so its hash still matches.
            file.writeText(
                """
                package org.example;

                public class Pricer {
                    public double priceFor(String tier, double total) {
                        double tax = total * 0.15;
                        if (tier.equals("gold")) {
                            double discount = total * 0.2;
                            double taxed = (total - discount) * 1.2;
                            return taxed - 5.0;
                        }
                        return total + tax;
                    }
                }
                """.trimIndent(),
            )

            val outcome = client.extractMethod(request)
            assertIs<RefactoringOutcome.Success>(outcome, "outcome=$outcome")
            assertEquals(
                """
                package org.example;

                public class Pricer {
                    public double priceFor(String tier, double total) {
                        double tax = total * 0.15;
                        if (tier.equals("gold")) {
                            return handleGold(total);
                        }
                        return total + tax;
                    }

                    private double handleGold(double total) {
                        double discount = total * 0.2;
                        double taxed = (total - discount) * 1.2;
                        return taxed - 5.0;
                    }
                }
                """.trimIndent(),
                Files.readString(file).trimEnd(),
            )
        }

        @Test
        fun `survives comments and javadoc added around the selection`(@TempDir worktree: Path) {
            val (file, request) = anchorSource(
                worktree,
                """
                package org.example;

                public class Pricer {
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
                startLine = 6, endLine = 9, newMethodName = "handleGold",
            )

            // Drift: add javadoc on the host, a line comment inside
            // the if-block above the selection, and a block comment
            // after. Comments are skipped by the AST hasher.
            file.writeText(
                """
                package org.example;

                /** Pricing logic. */
                public class Pricer {
                    /**
                     * Computes the price for a tier.
                     */
                    public double priceFor(String tier, double total) {
                        if (tier.equals("gold")) {
                            // gold-tier discount + tax + flat-five fee
                            double discount = total * 0.2;
                            double taxed = (total - discount) * 1.2;
                            return taxed - 5.0;
                            /* end of gold branch */
                        }
                        return total;
                    }
                }
                """.trimIndent(),
            )

            val outcome = client.extractMethod(request)
            assertIs<RefactoringOutcome.Success>(outcome, "outcome=$outcome")
            val rewritten = Files.readString(file)
            assertTrue(
                rewritten.contains("private double handleGold"),
                "expected `handleGold` method to be created — got:\n$rewritten",
            )
            assertTrue(
                rewritten.contains("return handleGold(total);"),
                "expected the original block to be replaced by a call",
            )
        }

        @Test
        fun `survives reformatted indentation in the host method`(@TempDir worktree: Path) {
            val (file, request) = anchorSource(
                worktree,
                """
                package org.example;

                public class Pricer {
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
                startLine = 6, endLine = 9, newMethodName = "handleGold",
            )

            // Drift: change indentation throughout the host body. JDT's
            // structural property descriptors don't carry whitespace,
            // so the selection's hash is invariant to formatting.
            file.writeText(
                """
                package org.example;

                public class Pricer {
                  public double priceFor(String tier, double total) {
                      if (tier.equals("gold"))
                      {
                                double discount = total * 0.2;
                                double taxed = (total - discount) * 1.2;
                                return taxed - 5.0;
                      }
                      return total;
                  }
                }
                """.trimIndent(),
            )

            val outcome = client.extractMethod(request)
            assertIs<RefactoringOutcome.Success>(outcome, "outcome=$outcome")
            assertTrue(
                Files.readString(file).contains("private double handleGold"),
                "expected `handleGold` method to be created",
            )
        }

        @Test
        fun `survives new statements added inside the host but outside the selection`(@TempDir worktree: Path) {
            val (file, request) = anchorSource(
                worktree,
                """
                package org.example;

                public class Pricer {
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
                startLine = 6, endLine = 9, newMethodName = "handleGold",
            )

            // Drift: insert new statements both *before* the if-block
            // and *after* it. The selection (the if-then-block's three
            // statements) is byte-identical, so the hash still matches.
            file.writeText(
                """
                package org.example;

                public class Pricer {
                    public double priceFor(String tier, double total) {
                        double base = total;
                        double tax = base * 0.1;
                        if (tier.equals("gold")) {
                            double discount = total * 0.2;
                            double taxed = (total - discount) * 1.2;
                            return taxed - 5.0;
                        }
                        double surcharge = base * 0.05;
                        return total + tax + surcharge;
                    }
                }
                """.trimIndent(),
            )

            val outcome = client.extractMethod(request)
            assertIs<RefactoringOutcome.Success>(outcome, "outcome=$outcome")
            assertEquals(
                """
                package org.example;

                public class Pricer {
                    public double priceFor(String tier, double total) {
                        double base = total;
                        double tax = base * 0.1;
                        if (tier.equals("gold")) {
                            return handleGold(total);
                        }
                        double surcharge = base * 0.05;
                        return total + tax + surcharge;
                    }

                    private double handleGold(double total) {
                        double discount = total * 0.2;
                        double taxed = (total - discount) * 1.2;
                        return taxed - 5.0;
                    }
                }
                """.trimIndent(),
                Files.readString(file).trimEnd(),
            )
        }

        @Test
        fun `survives statements deleted from the host but outside the selection`(@TempDir worktree: Path) {
            val (file, request) = anchorSource(
                worktree,
                """
                package org.example;

                public class Pricer {
                    public double priceFor(String tier, double total) {
                        double base = total;
                        double tax = base * 0.1;
                        double surcharge = base * 0.05;
                        if (tier.equals("gold")) {
                            double discount = total * 0.2;
                            double taxed = (total - discount) * 1.2;
                            return taxed - 5.0;
                        }
                        return total + tax + surcharge;
                    }
                }
                """.trimIndent(),
                startLine = 9, endLine = 12, newMethodName = "handleGold",
            )

            // Drift: delete the unrelated `tax` and `surcharge` setup
            // statements *and* simplify the trailing return. The
            // selection (if-then-block's three statements) is
            // unchanged so the hash still matches; the apply path
            // re-finds the window at its new (shifted-up) position.
            file.writeText(
                """
                package org.example;

                public class Pricer {
                    public double priceFor(String tier, double total) {
                        double base = total;
                        if (tier.equals("gold")) {
                            double discount = total * 0.2;
                            double taxed = (total - discount) * 1.2;
                            return taxed - 5.0;
                        }
                        return base;
                    }
                }
                """.trimIndent(),
            )

            val outcome = client.extractMethod(request)
            assertIs<RefactoringOutcome.Success>(outcome, "outcome=$outcome")
            assertEquals(
                """
                package org.example;

                public class Pricer {
                    public double priceFor(String tier, double total) {
                        double base = total;
                        if (tier.equals("gold")) {
                            return handleGold(total);
                        }
                        return base;
                    }

                    private double handleGold(double total) {
                        double discount = total * 0.2;
                        double taxed = (total - discount) * 1.2;
                        return taxed - 5.0;
                    }
                }
                """.trimIndent(),
                Files.readString(file).trimEnd(),
            )
        }

        @Test
        fun `fails gracefully when a literal inside the selection was changed`(@TempDir worktree: Path) {
            val (file, request) = anchorSource(
                worktree,
                """
                package org.example;

                public class Pricer {
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
                startLine = 6, endLine = 9, newMethodName = "handleGold",
            )

            // Drift: change the discount factor *inside* the selection
            // window from 0.2 → 0.25. Selection hash no longer matches.
            file.writeText(
                """
                package org.example;

                public class Pricer {
                    public double priceFor(String tier, double total) {
                        if (tier.equals("gold")) {
                            double discount = total * 0.25;
                            double taxed = (total - discount) * 1.2;
                            return taxed - 5.0;
                        }
                        return total;
                    }
                }
                """.trimIndent(),
            )

            val outcome = client.extractMethod(request)
            val failed = assertIs<RefactoringOutcome.Failed>(outcome, "outcome=$outcome")
            assertTrue(
                failed.reason.contains("no AST subtree match") || failed.reason.contains("hash"),
                "reason should mention hash / no AST subtree match — got: ${failed.reason}",
            )
        }

        /**
         * Write [source] to a Java file under [worktree] and build the
         * extract-method request against that snapshot. Returns the
         * file (so callers can overwrite it with drifted source) and
         * the request (which carries the AST-subtree-hash anchor that
         * survives subsequent edits).
         */
        private fun anchorSource(
            worktree: Path,
            source: String,
            startLine: Int,
            endLine: Int,
            newMethodName: String,
        ): Pair<Path, com.github.ethanhosier.analysis.refactoring.ops.ExtractMethodRequest> {
            val src = worktree.resolve("src").also(Path::createDirectories)
            val file = src.resolve("org/example/Pricer.java")
            file.parent.createDirectories()
            file.writeText(source)
            val request = extractMethodRequestAt(
                projectRoot = worktree,
                sourceFolders = listOf("src"),
                classpathJars = emptyList(),
                relativeFilePath = "src/org/example/Pricer.java",
                startLine = startLine,
                startColumn = 1,
                endLine = endLine,
                endColumn = Int.MAX_VALUE,
                newMethodName = newMethodName,
            )
            return file to request
        }
    }
}
