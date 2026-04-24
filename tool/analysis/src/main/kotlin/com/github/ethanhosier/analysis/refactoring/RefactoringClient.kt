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
