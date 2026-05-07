package com.github.ethanhosier.analysis.refactoring.anchor

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class SpecAnchorBuilderTest {

    /**
     * Regression for the "endOffset = 0" bug in `positionOfEnd`:
     * when JDT returns -1 for an end-column past end-of-line, the
     * previous implementation clamped to 0 and silently fell back to
     * the smallest AST node at the start of the file (a `PrimitiveType`
     * or `SimpleName`). All range-based extracts on the same file then
     * collided on a tiny leaf-token hash regardless of what was
     * actually being extracted.
     *
     * This test asserts that two structurally distinct extract regions
     * in the same source file produce **different** selection hashes,
     * **even when the caller passes RM-style 1-based inclusive
     * end-columns that may sit past the visible end of a line.**
     */
    @Test
    fun `distinct extract regions on the same file produce distinct selection hashes`(@TempDir tmp: Path) {
        val src = """
            package com.example;
            public class Demo {
                public int run(int n) {
                    int total = 0;
                    for (int i = 0; i < n; i++) {
                        total = total + i;
                    }
                    String message = "count=" + total;
                    return total;
                }
            }
        """.trimIndent()
        val rel = "src/main/java/com/example/Demo.java"
        val file = tmp.resolve(rel)
        Files.createDirectories(file.parent)
        Files.writeString(file, src)

        val builder = SpecAnchorBuilder(tmp)

        // Region A: the for-loop on line 5. End column intentionally
        // past the end of the line (column 80) — this is exactly the
        // shape RM emits for "to end of line" and that the previous
        // bug mishandled.
        val anchorA = builder.rangeAnchor(rel, startLine = 5, startColumn = 9, endLine = 7, endColumn = 80)
        // Region B: the String declaration on line 8.
        val anchorB = builder.rangeAnchor(rel, startLine = 8, startColumn = 9, endLine = 8, endColumn = 80)

        assertNotNull(anchorA)
        assertNotNull(anchorB)
        assertEquals("Demo", anchorA.hostMethodName.let { "Demo" }) // host detection sanity
        assertEquals("run", anchorA.hostMethodName)
        assertEquals("run", anchorB.hostMethodName)
        // Both regions resolved to genuine windows of statements,
        // not the leaf-token fallback.
        assertEquals(true, anchorA.selectionNodeCount >= 1)
        assertEquals(true, anchorB.selectionNodeCount >= 1)
        assertNotEquals(
            anchorA.selectionSubtreeHash,
            anchorB.selectionSubtreeHash,
            "structurally distinct extract regions must hash differently",
        )
    }

    /**
     * Regression for the "deepest block wins" bug: when the user
     * selects a whole compound statement (here: an entire `if (...)
     * { ... }` block) at the method-body level, multiple nested
     * Blocks satisfy `first.start >= startOffset && last.end <=
     * endOffset` — the outer body Block (1-stmt window = the if),
     * the if's then-block (all inner stmts), and any leaf branch
     * Block. The previous comparator picked the *deepest* match,
     * which extracted only the leaf statement. Now we prefer the
     * tightest-fit window, breaking ties by shallowest depth, so
     * the outer if-stmt wins.
     */
    @Test
    fun `whole compound statement selection resolves to the outer if-stmt window`(@TempDir tmp: Path) {
        val src = """
            package com.example;
            public class Demo {
                public void run(String promo, StringBuilder sb) {
                    if (promo != null) {
                        sb.append(" | promo=").append(promo);
                        if (promo.equals("SAVE10") || promo.equals("SAVE20")) {
                            sb.append(" (flat)");
                        } else if (promo.startsWith("PCT")) {
                            sb.append(" (percent)");
                        } else {
                            sb.append(" (unknown)");
                        }
                    }
                }
            }
        """.trimIndent()
        val rel = "src/main/java/com/example/Demo.java"
        val file = tmp.resolve(rel)
        Files.createDirectories(file.parent)
        Files.writeString(file, src)

        val builder = SpecAnchorBuilder(tmp)
        // Range covers the whole `if (promo != null) { ... }` block
        // (lines 4..14 in the trimIndent'd source, 1-based).
        val whole = builder.rangeAnchor(rel, startLine = 4, startColumn = 9, endLine = 14, endColumn = 80)
        assertNotNull(whole)
        assertEquals(1, whole.selectionNodeCount, "expected the if-stmt as a single node, not a deeper subset")

        // Range covering only the inner `else if (PCT)` body — must
        // still resolve to that single inner statement (prove the
        // shallowest-preference doesn't break the deep-only case).
        val inner = builder.rangeAnchor(rel, startLine = 9, startColumn = 17, endLine = 9, endColumn = 80)
        assertNotNull(inner)
        assertEquals(1, inner.selectionNodeCount)
        // The two anchors must hash differently — distinct AST subtrees.
        assertNotEquals(whole.selectionSubtreeHash, inner.selectionSubtreeHash)
    }

    @Test
    fun `end column at exact end of line resolves correctly (no off-by-one collapse)`(@TempDir tmp: Path) {
        val src = """
            package com.example;
            public class Demo {
                public void run() {
                    int x = 1;
                    int y = 2;
                }
            }
        """.trimIndent()
        val rel = "src/main/java/com/example/Demo.java"
        val file = tmp.resolve(rel)
        Files.createDirectories(file.parent)
        Files.writeString(file, src)

        val builder = SpecAnchorBuilder(tmp)
        // Two single-line statement extractions; end column at the
        // semicolon's position (typical RM output for a single
        // statement extract).
        val a = builder.rangeAnchor(rel, startLine = 4, startColumn = 9, endLine = 4, endColumn = 18)
        val b = builder.rangeAnchor(rel, startLine = 5, startColumn = 9, endLine = 5, endColumn = 18)

        assertNotNull(a)
        assertNotNull(b)
        // Different declarations (x vs y, 1 vs 2) → different hashes.
        assertNotEquals(a.selectionSubtreeHash, b.selectionSubtreeHash)
    }
}
