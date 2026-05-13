package com.github.ethanhosier.analysis.alternative.rework

import com.github.ethanhosier.analysis.alternative.rework.ReworkDetector.Direction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReworkDetectorTest {

    /**
     * Build a `Foo.java` source with the given lines inserted at the
     * top of `bar()`'s body. Produces:
     *   line 1: package com.example;
     *   line 2: public class Foo {
     *   line 3:     public void bar() {
     *   line 4..:        <bodyLines>
     *   ...:          }
     *   ...:      }
     */
    private fun fooWithBarBody(vararg bodyLines: String): String =
        buildString {
            appendLine("package com.example;")
            appendLine("public class Foo {")
            appendLine("    public void bar() {")
            for (line in bodyLines) appendLine("        $line")
            appendLine("    }")
            appendLine("}")
        }.trimEnd('\n')

    @Test
    fun `add_then_delete_same_chunk emits one finding`() {
        val step1Pre = fooWithBarBody()
        val step1Post = fooWithBarBody("int a = 1;", "int b = 2;", "int c = 3;")
        val step3Pre = step1Post
        val step3Post = step1Pre

        val step1Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -3,0 +4,3 @@\n" +
            "+int a = 1;\n" +
            "+int b = 2;\n" +
            "+int c = 3;\n"

        val step3Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -4,3 +3,0 @@\n" +
            "-int a = 1;\n" +
            "-int b = 2;\n" +
            "-int c = 3;\n"

        val steps = listOf(
            ReworkDetector.StepInput(
                stepIndex = 1,
                patch = step1Patch,
                preFileContent = mapOf("Foo.java" to step1Pre),
                postFileContent = mapOf("Foo.java" to step1Post),
            ),
            ReworkDetector.StepInput(
                stepIndex = 3,
                patch = step3Patch,
                preFileContent = mapOf("Foo.java" to step3Pre),
                postFileContent = mapOf("Foo.java" to step3Post),
            ),
        )

        val findings = ReworkDetector.detect(steps, minNormalizedLineCount = 1)
        assertEquals(1, findings.size)
        val f = findings[0]
        assertEquals(1, f.originatingStep)
        assertEquals(3, f.terminalStep)
        assertEquals("Foo.java", f.file)
        assertEquals("com.example.Foo#bar()", f.scopeId)
        assertEquals(Direction.ADD_THEN_REMOVE, f.direction)
        assertEquals(3, f.lineCount)
    }

    @Test
    fun `delete_then_add_back emits one finding with REMOVE_THEN_ADD direction`() {
        val withChunk = fooWithBarBody("int a = 1;", "int b = 2;")
        val empty = fooWithBarBody()

        val step1Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -4,2 +3,0 @@\n" +
            "-int a = 1;\n" +
            "-int b = 2;\n"

        val step3Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -3,0 +4,2 @@\n" +
            "+int a = 1;\n" +
            "+int b = 2;\n"

        val steps = listOf(
            ReworkDetector.StepInput(1, step1Patch,
                preFileContent = mapOf("Foo.java" to withChunk),
                postFileContent = mapOf("Foo.java" to empty)),
            ReworkDetector.StepInput(3, step3Patch,
                preFileContent = mapOf("Foo.java" to empty),
                postFileContent = mapOf("Foo.java" to withChunk)),
        )

        val findings = ReworkDetector.detect(steps, minNormalizedLineCount = 1)
        assertEquals(1, findings.size)
        assertEquals(Direction.REMOVE_THEN_ADD, findings[0].direction)
        assertEquals(2, findings[0].lineCount)
    }

    @Test
    fun `partial_chunk_revert_not_detected_v1`() {
        // Step 1 adds [a, b, c]; step 3 deletes [a, c]. The deleted
        // chunk differs from the added chunk, so no match.
        val empty = fooWithBarBody()
        val abc = fooWithBarBody("int a = 1;", "int b = 2;", "int c = 3;")
        val onlyB = fooWithBarBody("int b = 2;")

        val step1Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -3,0 +4,3 @@\n" +
            "+int a = 1;\n" +
            "+int b = 2;\n" +
            "+int c = 3;\n"

        // Two separate single-line removals — not one matchable chunk.
        val step3Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -4,1 +3,0 @@\n" +
            "-int a = 1;\n" +
            "@@ -6,1 +4,0 @@\n" +
            "-int c = 3;\n"

        val steps = listOf(
            ReworkDetector.StepInput(1, step1Patch,
                preFileContent = mapOf("Foo.java" to empty),
                postFileContent = mapOf("Foo.java" to abc)),
            ReworkDetector.StepInput(3, step3Patch,
                preFileContent = mapOf("Foo.java" to abc),
                postFileContent = mapOf("Foo.java" to onlyB)),
        )

        assertTrue(ReworkDetector.detect(steps, minNormalizedLineCount = 1).isEmpty())
    }

    @Test
    fun `same_chunk_different_methods_no_match`() {
        // Step 1 adds `int x = 1;` to bar(); step 3 removes
        // `int x = 1;` from baz(). Different scopes → no match.
        val empty = """
            package com.example;
            public class Foo {
                public void bar() {
                }
                public void baz() {
                }
            }
        """.trimIndent()
        val xInBar = """
            package com.example;
            public class Foo {
                public void bar() {
                    int x = 1;
                }
                public void baz() {
                }
            }
        """.trimIndent()
        val xInBaz = """
            package com.example;
            public class Foo {
                public void bar() {
                }
                public void baz() {
                    int x = 1;
                }
            }
        """.trimIndent()

        val step1Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -3,0 +4,1 @@\n" +
            "+int x = 1;\n"

        // step 3: tree goes xInBaz → xInBar (i.e. remove line from baz);
        // we just need a -line in baz()'s body for scope to resolve there.
        val step3Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -7,1 +6,0 @@\n" +
            "-int x = 1;\n"

        val steps = listOf(
            ReworkDetector.StepInput(1, step1Patch,
                preFileContent = mapOf("Foo.java" to empty),
                postFileContent = mapOf("Foo.java" to xInBar)),
            ReworkDetector.StepInput(3, step3Patch,
                preFileContent = mapOf("Foo.java" to xInBaz),
                postFileContent = mapOf("Foo.java" to empty)),
        )

        assertTrue(ReworkDetector.detect(steps, minNormalizedLineCount = 1).isEmpty())
    }

    @Test
    fun `same_chunk_different_files_no_match`() {
        val fooEmpty = fooWithBarBody()
        val fooWith = fooWithBarBody("int x = 1;")
        val barEmpty = fooEmpty.replace("class Foo", "class Bar")
        val barWith = fooWith.replace("class Foo", "class Bar")

        val step1Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -3,0 +4,1 @@\n" +
            "+int x = 1;\n"

        val step3Patch =
            "diff --git a/Bar.java b/Bar.java\n" +
            "--- a/Bar.java\n" +
            "+++ b/Bar.java\n" +
            "@@ -4,1 +3,0 @@\n" +
            "-int x = 1;\n"

        val steps = listOf(
            ReworkDetector.StepInput(1, step1Patch,
                preFileContent = mapOf("Foo.java" to fooEmpty),
                postFileContent = mapOf("Foo.java" to fooWith)),
            ReworkDetector.StepInput(3, step3Patch,
                preFileContent = mapOf("Bar.java" to barWith),
                postFileContent = mapOf("Bar.java" to barEmpty)),
        )

        assertTrue(ReworkDetector.detect(steps, minNormalizedLineCount = 1).isEmpty())
    }

    @Test
    fun `whitespace lines do not perturb chunk hash`() {
        // Step 1's added chunk has a blank padding line in the
        // middle; step 3's removed chunk doesn't. After normalisation
        // the blank line is dropped → both hash identically.
        val empty = fooWithBarBody()
        val withBlankPad = fooWithBarBody("int a = 1;", "", "int b = 2;")
        val tight = fooWithBarBody("int a = 1;", "int b = 2;")

        val step1Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -3,0 +4,3 @@\n" +
            "+int a = 1;\n" +
            "+\n" +
            "+int b = 2;\n"

        val step3Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -4,2 +3,0 @@\n" +
            "-int a = 1;\n" +
            "-int b = 2;\n"

        val steps = listOf(
            ReworkDetector.StepInput(1, step1Patch,
                preFileContent = mapOf("Foo.java" to empty),
                postFileContent = mapOf("Foo.java" to withBlankPad)),
            ReworkDetector.StepInput(3, step3Patch,
                preFileContent = mapOf("Foo.java" to tight),
                postFileContent = mapOf("Foo.java" to empty)),
        )

        val findings = ReworkDetector.detect(steps, minNormalizedLineCount = 1)
        assertEquals(1, findings.size)
        // Normalized line count = 2 (blank dropped).
        assertEquals(2, findings[0].lineCount)
    }

    @Test
    fun `unparseable file falls back to file scope and still matches`() {
        val badSrc = "this is not java !!!"

        val step1Patch =
            "diff --git a/X.java b/X.java\n" +
            "--- a/X.java\n" +
            "+++ b/X.java\n" +
            "@@ -1,0 +2,1 @@\n" +
            "+totally unparseable thing\n"

        val step3Patch =
            "diff --git a/X.java b/X.java\n" +
            "--- a/X.java\n" +
            "+++ b/X.java\n" +
            "@@ -2,1 +1,0 @@\n" +
            "-totally unparseable thing\n"

        val steps = listOf(
            ReworkDetector.StepInput(1, step1Patch,
                preFileContent = mapOf("X.java" to ""),
                postFileContent = mapOf("X.java" to badSrc)),
            ReworkDetector.StepInput(3, step3Patch,
                preFileContent = mapOf("X.java" to badSrc),
                postFileContent = mapOf("X.java" to "")),
        )

        val findings = ReworkDetector.detect(steps, minNormalizedLineCount = 1)
        assertEquals(1, findings.size)
        assertEquals("X.java#<file>", findings[0].scopeId)
    }

    @Test
    fun `multiple round trips paired greedily`() {
        // Add X at step 1, add X at step 3, remove X at step 5,
        // remove X at step 7. Expect two pairs: (1, 5) and (3, 7).
        val empty = fooWithBarBody()
        val withX = fooWithBarBody("int x = 1;")

        val addPatch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -3,0 +4,1 @@\n" +
            "+int x = 1;\n"

        val removePatch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -4,1 +3,0 @@\n" +
            "-int x = 1;\n"

        val steps = listOf(
            ReworkDetector.StepInput(1, addPatch,
                preFileContent = mapOf("Foo.java" to empty),
                postFileContent = mapOf("Foo.java" to withX)),
            ReworkDetector.StepInput(3, addPatch,
                preFileContent = mapOf("Foo.java" to empty),
                postFileContent = mapOf("Foo.java" to withX)),
            ReworkDetector.StepInput(5, removePatch,
                preFileContent = mapOf("Foo.java" to withX),
                postFileContent = mapOf("Foo.java" to empty)),
            ReworkDetector.StepInput(7, removePatch,
                preFileContent = mapOf("Foo.java" to withX),
                postFileContent = mapOf("Foo.java" to empty)),
        )

        val findings = ReworkDetector.detect(steps, minNormalizedLineCount = 1)
        val pairs = findings.map { it.originatingStep to it.terminalStep }.toSet()
        assertEquals(setOf(1 to 5, 3 to 7), pairs)
    }

    @Test
    fun `intervening edit to same method does not block match`() {
        val empty = fooWithBarBody()
        val withX = fooWithBarBody("int x = 1;")
        val withXY = fooWithBarBody("int x = 1;", "int y = 2;")
        val withY = fooWithBarBody("int y = 2;")

        val step1Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -3,0 +4,1 @@\n" +
            "+int x = 1;\n"

        val step2Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -4,0 +5,1 @@\n" +
            "+int y = 2;\n"

        val step3Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -4,1 +3,0 @@\n" +
            "-int x = 1;\n"

        val steps = listOf(
            ReworkDetector.StepInput(1, step1Patch,
                preFileContent = mapOf("Foo.java" to empty),
                postFileContent = mapOf("Foo.java" to withX)),
            ReworkDetector.StepInput(2, step2Patch,
                preFileContent = mapOf("Foo.java" to withX),
                postFileContent = mapOf("Foo.java" to withXY)),
            ReworkDetector.StepInput(3, step3Patch,
                preFileContent = mapOf("Foo.java" to withXY),
                postFileContent = mapOf("Foo.java" to withY)),
        )

        val findings = ReworkDetector.detect(steps, minNormalizedLineCount = 1)
        assertEquals(1, findings.size)
        assertEquals(1, findings[0].originatingStep)
        assertEquals(3, findings[0].terminalStep)
    }

    @Test
    fun `aggregation across content hashes sums line counts`() {
        // Two different content chunks added at step 1 in bar(),
        // both removed at step 3. Aggregated into one finding with
        // summed lineCount.
        val empty = fooWithBarBody()
        val ab = fooWithBarBody("int a = 1;", "int b = 2;")

        val step1Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -3,0 +4,1 @@\n" +
            "+int a = 1;\n" +
            "@@ -3,0 +5,1 @@\n" +
            "+int b = 2;\n"

        val step3Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -4,1 +3,0 @@\n" +
            "-int a = 1;\n" +
            "@@ -5,1 +3,0 @@\n" +
            "-int b = 2;\n"

        val steps = listOf(
            ReworkDetector.StepInput(1, step1Patch,
                preFileContent = mapOf("Foo.java" to empty),
                postFileContent = mapOf("Foo.java" to ab)),
            ReworkDetector.StepInput(3, step3Patch,
                preFileContent = mapOf("Foo.java" to ab),
                postFileContent = mapOf("Foo.java" to empty)),
        )

        val findings = ReworkDetector.detect(steps, minNormalizedLineCount = 1)
        assertEquals(1, findings.size)
        assertEquals(2, findings[0].lineCount)
    }

    @Test
    fun `detectChunkPairs exposes raw startLine and run length`() {
        // Step 1 adds 2 lines (blank + meaningful) at newLine 4.
        // Normalized count = 1 (blank dropped); raw run length = 2.
        val empty = fooWithBarBody()
        val withChunk = fooWithBarBody("", "int a = 1;")

        val step1Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -3,0 +4,2 @@\n" +
            "+\n" +
            "+int a = 1;\n"

        val step3Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -4,2 +3,0 @@\n" +
            "-\n" +
            "-int a = 1;\n"

        val steps = listOf(
            ReworkDetector.StepInput(1, step1Patch,
                preFileContent = mapOf("Foo.java" to empty),
                postFileContent = mapOf("Foo.java" to withChunk)),
            ReworkDetector.StepInput(3, step3Patch,
                preFileContent = mapOf("Foo.java" to withChunk),
                postFileContent = mapOf("Foo.java" to empty)),
        )

        val pairs = ReworkDetector.detectChunkPairs(steps, minNormalizedLineCount = 1)
        assertEquals(1, pairs.size)
        val p = pairs[0]
        assertEquals(1, p.originatingStep)
        assertEquals(3, p.terminalStep)
        assertEquals(Direction.ADD_THEN_REMOVE, p.direction)
        assertEquals(4, p.originatingRunStartLine)
        assertEquals(4, p.terminalRunStartLine)
        assertEquals(2, p.rawLineCount)         // includes the blank
        assertEquals(1, p.normalizedLineCount)  // blank dropped
    }

    @Test
    fun `default threshold filters out 1-line false positives`() {
        // 1-line add at step 1, 1-line remove at step 3. With default
        // threshold (>= 2), the chunks never enter the matching pool
        // → no findings, no chunk pairs.
        val empty = fooWithBarBody()
        val withX = fooWithBarBody("int x = 1;")

        val step1Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -3,0 +4,1 @@\n" +
            "+int x = 1;\n"

        val step3Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -4,1 +3,0 @@\n" +
            "-int x = 1;\n"

        val steps = listOf(
            ReworkDetector.StepInput(1, step1Patch,
                preFileContent = mapOf("Foo.java" to empty),
                postFileContent = mapOf("Foo.java" to withX)),
            ReworkDetector.StepInput(3, step3Patch,
                preFileContent = mapOf("Foo.java" to withX),
                postFileContent = mapOf("Foo.java" to empty)),
        )

        assertTrue(ReworkDetector.detect(steps).isEmpty())
        assertTrue(ReworkDetector.detectChunkPairs(steps).isEmpty())

        // Same input with threshold = 1 → finding emerges.
        assertEquals(1, ReworkDetector.detect(steps, minNormalizedLineCount = 1).size)
    }

    @Test
    fun `default threshold keeps multi-line reworks`() {
        // 3-line chunk should survive the default >= 2 threshold.
        val empty = fooWithBarBody()
        val withAbc = fooWithBarBody("int a = 1;", "int b = 2;", "int c = 3;")

        val step1Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -3,0 +4,3 @@\n" +
            "+int a = 1;\n" +
            "+int b = 2;\n" +
            "+int c = 3;\n"

        val step3Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -4,3 +3,0 @@\n" +
            "-int a = 1;\n" +
            "-int b = 2;\n" +
            "-int c = 3;\n"

        val steps = listOf(
            ReworkDetector.StepInput(1, step1Patch,
                preFileContent = mapOf("Foo.java" to empty),
                postFileContent = mapOf("Foo.java" to withAbc)),
            ReworkDetector.StepInput(3, step3Patch,
                preFileContent = mapOf("Foo.java" to withAbc),
                postFileContent = mapOf("Foo.java" to empty)),
        )

        assertEquals(1, ReworkDetector.detect(steps).size)
    }

    @Test
    fun `no rework on unrelated trace`() {
        val empty = fooWithBarBody()
        val withX = fooWithBarBody("int x = 1;")

        val step1Patch =
            "diff --git a/Foo.java b/Foo.java\n" +
            "--- a/Foo.java\n" +
            "+++ b/Foo.java\n" +
            "@@ -3,0 +4,1 @@\n" +
            "+int x = 1;\n"

        val steps = listOf(
            ReworkDetector.StepInput(1, step1Patch,
                preFileContent = mapOf("Foo.java" to empty),
                postFileContent = mapOf("Foo.java" to withX)),
        )
        assertTrue(ReworkDetector.detect(steps, minNormalizedLineCount = 1).isEmpty())
    }
}
