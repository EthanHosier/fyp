package com.github.ethanhosier.analysis.metrics.remote

import com.github.ethanhosier.analysis.metrics.GradleConfig
import com.github.ethanhosier.analysis.metrics.dto.BundleRef
import com.github.ethanhosier.analysis.metrics.dto.ComputeRequest
import com.github.ethanhosier.analysis.metrics.dto.ComputeResponse
import com.github.ethanhosier.analysis.metrics.gradlebuild.BuildResult
import com.github.ethanhosier.analysis.metrics.ck.CkResult
import com.github.ethanhosier.analysis.metrics.cpd.CpdResult
import com.github.ethanhosier.analysis.metrics.model.CheckpointMetrics
import com.github.ethanhosier.analysis.metrics.pmd.PmdResult
import com.github.ethanhosier.analysis.metrics.readability.ReadabilityResult
import com.github.ethanhosier.analysis.metrics.tests.TestResult
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/**
 * Verifies the executor's contract: build a [ComputeRequest] per submitted
 * SHA, hand it to the [LambdaInvoker], unwrap the response back into a
 * [CheckpointMetrics] (or a thrown exception). Uses a hand-rolled fake so
 * no AWS SDK is touched.
 */
class RemoteCheckpointExecutorTest {

    private val bundle = BundleRef("test-bucket", "shadow-bundles/abcdef.bundle")

    @Test
    fun `submits a request per sha and returns its metrics`() {
        val seenRequests = ConcurrentHashMap.newKeySet<ComputeRequest>()
        val invoker = LambdaInvoker { request ->
            seenRequests.add(request)
            ComputeResponse(sha = request.sha, metrics = stubMetrics(request.sha))
        }

        val shas = listOf("aaa1111", "bbb2222", "ccc3333")
        val results = RemoteCheckpointExecutor(invoker, bundle, parallelism = 4).use { exec ->
            shas.map { exec.submit(it) }.map { it.get() }
        }

        assertEquals(shas, results.map { it.sha })
        assertEquals(shas.toSet(), seenRequests.map { it.sha }.toSet())
        assertTrue(seenRequests.all { it.bundle == bundle })
        assertTrue(seenRequests.all { it.gradleConfig == GradleConfig.DEFAULT })
    }

    @Test
    fun `throws when invoker reports a handler-side error`() {
        val invoker = LambdaInvoker { request ->
            ComputeResponse(sha = request.sha, error = "boom")
        }

        val ex = assertFails {
            RemoteCheckpointExecutor(invoker, bundle).use { exec ->
                exec.submit("deadbeef").get()
            }
        }
        // Future.get wraps in ExecutionException; the cause is the error
        // message we surfaced from the response.
        val message = (ex.cause?.message ?: ex.message).orEmpty()
        assertTrue("boom" in message, "expected 'boom' in: $message")
    }

    private fun stubMetrics(sha: String) = CheckpointMetrics(
        sha = sha,
        ck = CkResult(perClass = emptyList()),
        pmd = PmdResult(violations = emptyList()),
        cpd = CpdResult.EMPTY,
        readability = ReadabilityResult.EMPTY,
        build = BuildResult(success = true, exitCode = 0, durationMs = 0, timedOut = false, stderrTail = ""),
        tests = TestResult.skipped("stub"),
    )
}
