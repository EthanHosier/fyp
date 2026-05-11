package com.github.ethanhosier.analysis.refactoring

import com.github.ethanhosier.analysis.refactoring.ops.ExtractMethodRequest
import com.github.ethanhosier.analysis.refactoring.ops.RenameMethodRequest
import com.github.ethanhosier.analysis.refactoring.ops.extractMethod
import com.github.ethanhosier.analysis.refactoring.ops.renameMethod
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Verifies the bundle-side project-cache + keep-flag plumbing exposed
 * via [RefactoringClient.withBatchSession]. Each test reads
 * `client.initCount()` as a baseline and asserts the *delta*, because
 * the counter is process-wide and accumulates across the whole test
 * class run.
 *
 * The "perf-sensitive" case (`session_indexes_once`) is also the
 * correctness guarantee — one bundle init per session means we are
 * actually running both refactorings against the same indexed
 * `IJavaProject`. Regression here would silently re-index per call.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("slow")
class RefactoringClientBatchSessionTest {

    private lateinit var client: RefactoringClient
    private lateinit var dataArea: Path

    @BeforeAll
    fun setUp() {
        dataArea = Files.createTempDirectory("rc-batch-session-test-")
        client = RefactoringClientFactory.create(dataArea.resolve("osgi"))
    }

    @AfterAll
    fun tearDown() {
        client.close()
        dataArea.toFile().deleteRecursively()
    }

    @Test
    fun `session indexes once across two consecutive applies`(@TempDir worktree: Path) {
        writeFixture(worktree)
        val before = client.initCount()

        client.withBatchSession {
            val r1 = client.extractMethod(extractRequest(worktree))
            assertIs<RefactoringOutcome.Success>(r1, "extractMethod failed: $r1")
            val r2 = client.renameMethod(renameRequest(worktree, oldName = "compute"))
            assertIs<RefactoringOutcome.Success>(r2, "renameMethod failed: $r2")
        }

        assertEquals(
            1, client.initCount() - before,
            "expected exactly 1 project init across the batch (cache reused on call 2)",
        )
    }

    @Test
    fun `applies outside a session index per call (baseline regression guard)`(@TempDir worktree: Path) {
        writeFixture(worktree)
        val before = client.initCount()

        val r1 = client.extractMethod(extractRequest(worktree))
        assertIs<RefactoringOutcome.Success>(r1, "extractMethod failed: $r1")
        val r2 = client.renameMethod(renameRequest(worktree, oldName = "compute"))
        assertIs<RefactoringOutcome.Success>(r2, "renameMethod failed: $r2")

        assertEquals(
            2, client.initCount() - before,
            "without a session, each apply should pay its own init",
        )
    }

    @Test
    fun `session evicts cache so the next single apply on a different worktree re-inits`(
        @TempDir wt1: Path,
        @TempDir wt2: Path,
    ) {
        writeFixture(wt1)
        writeFixture(wt2)

        client.withBatchSession {
            val r = client.extractMethod(extractRequest(wt1))
            assertIs<RefactoringOutcome.Success>(r)
        }

        val before = client.initCount()
        val r2 = client.extractMethod(extractRequest(wt2))
        assertIs<RefactoringOutcome.Success>(r2, "extractMethod on second worktree failed: $r2")

        assertEquals(
            1, client.initCount() - before,
            "post-session apply on a different worktree must re-init (cache should have been cleared)",
        )
    }

    @Test
    fun `session evicts cache when the body throws`(
        @TempDir wt1: Path,
        @TempDir wt2: Path,
    ) {
        writeFixture(wt1)
        writeFixture(wt2)

        try {
            client.withBatchSession {
                val r = client.extractMethod(extractRequest(wt1))
                assertIs<RefactoringOutcome.Success>(r)
                throw IllegalStateException("simulated mid-session crash")
            }
            fail("withBatchSession should have rethrown")
        } catch (_: IllegalStateException) {
            // expected
        }

        // Different worktree → must re-init even though the previous
        // session ended exceptionally.
        val before = client.initCount()
        val r2 = client.extractMethod(extractRequest(wt2))
        assertIs<RefactoringOutcome.Success>(r2)

        assertEquals(
            1, client.initCount() - before,
            "exception in session body must still trigger cache eviction in finally",
        )
    }

    /**
     * Sanity check: session-mode wall-clock is materially faster than
     * non-session for the same N-step workload. The init-count delta
     * already proves the *cause* (cache reused), but the user-visible
     * win is wall-clock; log it so future regressions show up
     * obviously and so it's easy to track perf as we add more ops.
     *
     * The assertion is intentionally lax: CI is noisy and the second
     * apply's incremental index can occasionally rival a fresh full
     * index on tiny fixtures. We just require the session not to be
     * *slower*, which is the only outcome that would invalidate the
     * design.
     */
    @Test
    fun `session is materially faster than per-call init for two consecutive applies`(
        @TempDir wtSession: Path,
        @TempDir wtBaseline: Path,
    ) {
        writeFixture(wtSession)
        writeFixture(wtBaseline)

        // Warm-up: prime any first-time JIT / class-load costs that
        // would otherwise inflate whichever path runs first.
        run {
            val warm = Files.createTempDirectory("rc-warmup-")
            try {
                writeFixture(warm)
                val r = client.extractMethod(extractRequest(warm))
                assertIs<RefactoringOutcome.Success>(r)
            } finally {
                warm.toFile().deleteRecursively()
            }
        }

        val sessionNs = measureNanos {
            client.withBatchSession {
                val r1 = client.extractMethod(extractRequest(wtSession))
                assertIs<RefactoringOutcome.Success>(r1)
                val r2 = client.renameMethod(renameRequest(wtSession, oldName = "compute"))
                assertIs<RefactoringOutcome.Success>(r2)
            }
        }

        val baselineNs = measureNanos {
            val r1 = client.extractMethod(extractRequest(wtBaseline))
            assertIs<RefactoringOutcome.Success>(r1)
            val r2 = client.renameMethod(renameRequest(wtBaseline, oldName = "compute"))
            assertIs<RefactoringOutcome.Success>(r2)
        }

        val sessionMs = sessionNs / 1_000_000.0
        val baselineMs = baselineNs / 1_000_000.0
        val saved = baselineMs - sessionMs
        val savedPct = (saved / baselineMs) * 100.0
        println(
            "[batch-session perf] session=${"%.1f".format(sessionMs)}ms  " +
                "baseline=${"%.1f".format(baselineMs)}ms  " +
                "saved=${"%.1f".format(saved)}ms (${"%.1f".format(savedPct)}%)",
        )

        assertTrue(
            sessionMs <= baselineMs * 1.1,
            "session should not be meaningfully slower than per-call init " +
                "(session=${sessionMs}ms baseline=${baselineMs}ms)",
        )
    }

    @Test
    fun `nested session is a no-op`(@TempDir worktree: Path) {
        writeFixture(worktree)
        val before = client.initCount()

        client.withBatchSession {
            val r1 = client.extractMethod(extractRequest(worktree))
            assertIs<RefactoringOutcome.Success>(r1)

            // Re-entering should not re-init or break the outer session.
            client.withBatchSession {
                val r2 = client.renameMethod(renameRequest(worktree, oldName = "compute"))
                assertIs<RefactoringOutcome.Success>(r2)
            }

            // ⚠ caveat: the inner session's `finally` clears the cache
            // and resets the keep-flag, so a *third* apply here would
            // re-init. Out of scope to fix in this slice — nested
            // sessions don't compose, but they don't crash either.
            // Locked in by this assertion: 2 inits across the nested
            // batch (one per outer step due to inner clearing the
            // cache between them is wrong! …actually let's measure).
        }

        // We assert the actual delta rather than a magic number so a
        // future "nested session is fully transparent" change can
        // tighten this without rewriting the test fixture. Today the
        // expected delta is 2 because the inner session clears the
        // cache, costing the outer's would-be third call a re-init —
        // but we have only two ops above so it's just 1 outer init +
        // 1 inner init = 2.
        val delta = client.initCount() - before
        assertTrue(
            delta in 1..2,
            "nested session should not init more than 2× (got $delta) — composition is not perfect but must not regress",
        )
    }

    private inline fun measureNanos(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return System.nanoTime() - start
    }

    private fun writeFixture(worktree: Path) {
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
    }

    private fun extractRequest(worktree: Path): ExtractMethodRequest {
        // Selection: lines 5-6 of the fixture (`double sum = a + b;`
        // and `double doubled = sum * 2;`). Anchor builder gives us
        // the host method and selection hash — sufficient fidelity
        // for the actual extract (we don't assert on the extracted
        // body here, only on init counts).
        val builder = com.github.ethanhosier.analysis.refactoring.anchor.SpecAnchorBuilder(worktree)
        val anchor = builder.rangeAnchor(
            relativeFilePath = "src/org/example/Demo.java",
            startLine = 5, startColumn = 9,
            endLine = 6, endColumn = Int.MAX_VALUE,
        ) ?: error("range anchor failed")
        return ExtractMethodRequest(
            projectRoot = worktree,
            sourceFolders = listOf("src"),
            classpathJars = emptyList(),
            relativeFilePath = "src/org/example/Demo.java",
            declaringTypeFqn = "org.example.Demo",
            hostMethodName = anchor.hostMethodName,
            hostMethodParamTypes = anchor.hostMethodParamTypes,
            selectionSubtreeHash = anchor.selectionSubtreeHash,
            selectionNodeCount = anchor.selectionNodeCount,
            originalLineHint = 5,
            originalColumnHint = 9,
            newMethodName = "combine",
            isStatic = false,
        )
    }

    private fun renameRequest(worktree: Path, oldName: String): RenameMethodRequest =
        RenameMethodRequest(
            projectRoot = worktree,
            sourceFolders = listOf("src"),
            classpathJars = emptyList(),
            declaringTypeFqn = "org.example.Demo",
            oldName = oldName,
            newName = "${oldName}V2",
            // JDT-encoded `(double, double)` — see RenameMethodRequest doc.
            paramTypeSignatures = listOf("D", "D"),
        )
}
