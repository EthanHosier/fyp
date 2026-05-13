package com.github.ethanhosier.analysis.alternative.rework

import kotlin.test.Test
import kotlin.test.assertEquals

class EnclosingScopeResolverTest {

    private fun resolve(source: String, line: Int, path: String = "Foo.java"): String =
        EnclosingScopeResolver.resolve(source, path, line)

    @Test
    fun `line inside method body resolves to method scope`() {
        val src = """
            package com.example;
            public class Foo {
                public int bar(int x, String y) {
                    int z = x + 1;
                    return z;
                }
            }
        """.trimIndent()
        // Line 4 is `int z = x + 1;` inside bar(int, String)
        assertEquals("com.example.Foo#bar(int,String)", resolve(src, 4))
    }

    @Test
    fun `method with no parameters has empty parens`() {
        val src = """
            package com.example;
            public class Foo {
                public void run() {
                    System.out.println("hi");
                }
            }
        """.trimIndent()
        assertEquals("com.example.Foo#run()", resolve(src, 4))
    }

    @Test
    fun `nested class method resolves to nested type`() {
        val src = """
            package com.example;
            public class Outer {
                static class Inner {
                    void hidden() {
                        int q = 1;
                    }
                }
            }
        """.trimIndent()
        assertEquals("com.example.Outer.Inner#hidden()", resolve(src, 5))
    }

    @Test
    fun `field declaration resolves to field scope`() {
        val src = """
            package com.example;
            public class Foo {
                private int counter = 0;
                public void run() {}
            }
        """.trimIndent()
        assertEquals("com.example.Foo::counter", resolve(src, 3))
    }

    @Test
    fun `static initializer resolves to clinit`() {
        val src = """
            package com.example;
            public class Foo {
                static {
                    int x = 1;
                }
            }
        """.trimIndent()
        assertEquals("com.example.Foo#<clinit>", resolve(src, 4))
    }

    @Test
    fun `instance initializer resolves to init`() {
        val src = """
            package com.example;
            public class Foo {
                {
                    int x = 1;
                }
            }
        """.trimIndent()
        assertEquals("com.example.Foo#<init>", resolve(src, 4))
    }

    @Test
    fun `line in imports falls back to file scope`() {
        val src = """
            package com.example;
            import java.util.List;
            public class Foo {
                void run() {}
            }
        """.trimIndent()
        assertEquals("Foo.java#<file>", resolve(src, 2))
    }

    @Test
    fun `line between methods falls back to file scope`() {
        val src = """
            package com.example;
            public class Foo {
                void a() {}

                void b() {}
            }
        """.trimIndent()
        // Blank line between methods.
        assertEquals("Foo.java#<file>", resolve(src, 4))
    }

    @Test
    fun `unparseable source falls back to file scope`() {
        val src = "this is not valid java !!! @#$"
        assertEquals("Bad.java#<file>", resolve(src, 1, path = "Bad.java"))
    }

    @Test
    fun `default package method has no package prefix`() {
        val src = """
            public class Foo {
                void run() {
                    int x = 1;
                }
            }
        """.trimIndent()
        assertEquals("Foo#run()", resolve(src, 3))
    }

    @Test
    fun `overloaded methods distinguished by param types`() {
        val src = """
            package com.example;
            public class Foo {
                void run(int x) { int a = x; }
                void run(String s) { int b = s.length(); }
            }
        """.trimIndent()
        assertEquals("com.example.Foo#run(int)", resolve(src, 3))
        assertEquals("com.example.Foo#run(String)", resolve(src, 4))
    }

    @Test
    fun `line on method signature resolves to that method`() {
        val src = """
            package com.example;
            public class Foo {
                public void doIt() {
                    int x = 1;
                }
            }
        """.trimIndent()
        // Line 3 is the `public void doIt() {` line.
        assertEquals("com.example.Foo#doIt()", resolve(src, 3))
    }

    @Test
    fun `generic parameter type rendered as written`() {
        val src = """
            package com.example;
            import java.util.List;
            public class Foo {
                void take(List<String> xs) {
                    int n = xs.size();
                }
            }
        """.trimIndent()
        assertEquals("com.example.Foo#take(List<String>)", resolve(src, 5))
    }
}
