package com.github.ethanhosier.analysis.refactoring

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.osgi.framework.launch.Framework
import java.lang.reflect.Method
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class RefactoringClient internal constructor(
    private val framework: Framework,
    private val refactorer: Any,
    private val refactorerClass: Class<*>,
) : AutoCloseable {

    private val lock = ReentrantLock()
    private val methodCache = ConcurrentHashMap<String, Method>()

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
