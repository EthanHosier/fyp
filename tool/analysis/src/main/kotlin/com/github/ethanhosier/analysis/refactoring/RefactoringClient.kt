package com.github.ethanhosier.analysis.refactoring

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.osgi.framework.launch.Framework
import java.lang.reflect.Method
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Host-side facade for JDT-backed refactorings. Each refactoring is
 * implemented as a per-op file in this package (e.g. [ExtractMethod],
 * [RenameMethod]) that hangs an extension function off this class and
 * calls [invokeOnBundle].
 *
 * Thread-safety: a coarse [ReentrantLock] serialises every call.
 * Eclipse's workspace has its own locks, but the bundle's project
 * registry mutates shared state and the simplest correct default is to
 * serialise outright — revisit if a profiler says otherwise.
 *
 * Lifecycle: instances are produced by [RefactoringClientFactory] and
 * are expected to live for the duration of the server process. [close]
 * stops the embedded Equinox framework.
 */
class RefactoringClient internal constructor(
    private val framework: Framework,
    private val refactorer: Any,
    private val refactorerClass: Class<*>,
) : AutoCloseable {

    private val lock = ReentrantLock()
    private val methodCache = ConcurrentHashMap<String, Method>()

    /**
     * Resolve and cache the `@JvmStatic` method named [name] on the
     * bundle's `JdtRefactorer`, then invoke it under the client's
     * lock and parse the JSON outcome. Per-op files call this.
     *
     * [paramTypes] must match the bundle-side signature exactly
     * (primitives / String / Array<String>). Using the cache key alone
     * would be ambiguous for overloads, so we recompute only on miss.
     */
    internal fun invokeOnBundle(
        name: String,
        paramTypes: Array<Class<*>>,
        args: Array<Any?>,
    ): RefactoringOutcome = lock.withLock {
        val handle = methodCache.getOrPut(name) {
            refactorerClass.getMethod(name, *paramTypes)
        }
        parse(handle.invoke(refactorer, *args) as String)
    }

    /**
     * Run [block] inside a "batch session" — the bundle keeps the
     * indexed Eclipse project alive across consecutive
     * [invokeOnBundle] calls on the same `projectRoot`, so a sequence
     * of refactorings pays only one full init+index for the whole
     * batch instead of one per call.
     *
     * Lifecycle, in order, on every call:
     * 1. Acquire [lock] (the same lock [invokeOnBundle] uses; it's
     *    reentrant so nested per-op calls inside [block] don't
     *    deadlock).
     * 2. Tell the bundle to keep the project after each call.
     * 3. Run [block]. Per-op calls inside it transparently benefit
     *    from the cache.
     * 4. In a `finally`: tear down whatever's cached, reset the
     *    keep-flag to off. No state survives past this method.
     *
     * Holding the lock for the whole batch serialises out any
     * concurrent caller (`apply`, another `applyAll`, etc.) — without
     * that, a different worktree's `apply()` could race the keep-flag
     * and reuse a project rooted elsewhere. The JDT lock at the
     * bundle layer makes parallel bundle calls impossible regardless,
     * so this isn't a throughput regression.
     */
    fun <T> withBatchSession(block: () -> T): T = lock.withLock {
        setKeepProject(true)
        try {
            block()
        } finally {
            // Order matters: clear the cache while the flag is still
            // truthful about whether a project is held, then reset.
            runCatching { clearProjectCache() }
            runCatching { setKeepProject(false) }
        }
    }

    /**
     * Force the bundle's cached Eclipse project to re-stat every
     * file. Use after rewriting working-tree files behind Eclipse's
     * back — typically after a `git checkout` between sibling
     * subtrees in a DFS walk.
     */
    fun refreshProject(): RefactoringOutcome = invokeOnBundle(
        "refreshProject",
        emptyArray(),
        emptyArray(),
    )

    private fun setKeepProject(keep: Boolean) {
        invokeOnBundle(
            "setKeepProject",
            arrayOf(java.lang.Boolean.TYPE),
            arrayOf<Any?>(keep),
        )
    }

    private fun clearProjectCache() {
        invokeOnBundle("clearProjectCache", emptyArray(), emptyArray())
    }

    /**
     * Test-only: read the bundle's project-init counter. Smoke tests
     * use this to assert that an N-step batch indexed only once.
     */
    internal fun initCount(): Int = lock.withLock {
        val handle = methodCache.getOrPut("initCount") {
            refactorerClass.getMethod("initCount")
        }
        handle.invoke(refactorer) as Int
    }

    override fun close() {
        lock.withLock {
            framework.stop()
            // Block until Equinox releases its storage locks so the
            // next boot (or a tmpdir cleanup) isn't racing it.
            framework.waitForStop(5_000)
        }
    }

    private fun parse(payload: String): RefactoringOutcome {
        val decoded = outcomeJson.decodeFromString<OutcomePayload>(payload)
        return when (decoded.status) {
            "ok" -> RefactoringOutcome.Success(decoded.changedFiles.map(Path::of))
            "failed" -> RefactoringOutcome.Failed(decoded.reason)
            else -> RefactoringOutcome.Failed("unknown status: ${decoded.status}; raw=$payload")
        }
    }

    @Serializable
    private data class OutcomePayload(
        val status: String,
        val changedFiles: List<String> = emptyList(),
        val reason: String = "",
    )

    private companion object {
        private val outcomeJson = Json { ignoreUnknownKeys = true }
    }
}
