package com.github.ethanhosier.analysis.refactoring

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.osgi.framework.launch.Framework
import java.lang.reflect.Method
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Host-side facade for JDT-backed refactorings. Each method translates
 * a typed request into a reflective invocation of the corresponding
 * `JdtRefactorer` method inside the OSGi bundle, then parses the
 * returned JSON outcome back into a typed [RefactoringOutcome].
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

// TODO: at some point add a batch mode where can batch together multiple refactorings against same project
//  (= don't have to re index every time)
class RefactoringClient internal constructor(
    private val framework: Framework,
    private val refactorer: Any,
    refactorerClass: Class<*>,
) : AutoCloseable {

    private val lock = ReentrantLock()

    // Reflective method handles cached at construction time. Each
    // signature below must match the JvmStatic method on the bundle's
    // JdtRefactorer exactly.
    private val extractMethodHandle: Method = refactorerClass.getMethod(
        "extractMethod",
        String::class.java,
        Array<String>::class.java,
        Array<String>::class.java,
        String::class.java,
        Int::class.javaPrimitiveType,
        Int::class.javaPrimitiveType,
        String::class.java,
    )

    private val renameMethodHandle: Method = refactorerClass.getMethod(
        "renameMethod",
        String::class.java,
        Array<String>::class.java,
        Array<String>::class.java,
        String::class.java,
        String::class.java,
        String::class.java,
        Array<String>::class.java,
    )

    fun extractMethod(req: ExtractMethodRequest): RefactoringOutcome = call {
        extractMethodHandle.invoke(
            refactorer,
            req.projectRoot.toAbsolutePath().toString(),
            req.sourceFolders.toTypedArray(),
            req.classpathJars.map { it.toAbsolutePath().toString() }.toTypedArray(),
            req.relativeFilePath,
            req.startLine,
            req.endLine,
            req.newMethodName,
        ) as String
    }

    fun renameMethod(req: RenameMethodRequest): RefactoringOutcome = call {
        renameMethodHandle.invoke(
            refactorer,
            req.projectRoot.toAbsolutePath().toString(),
            req.sourceFolders.toTypedArray(),
            req.classpathJars.map { it.toAbsolutePath().toString() }.toTypedArray(),
            req.declaringTypeFqn,
            req.oldName,
            req.newName,
            req.paramTypeSignatures?.toTypedArray(),
        ) as String
    }

    override fun close() {
        lock.withLock {
            framework.stop()
            // Block until Equinox releases its storage locks so the
            // next boot (or a tmpdir cleanup) isn't racing it.
            framework.waitForStop(5_000)
        }
    }

    private inline fun call(block: () -> String): RefactoringOutcome = lock.withLock {
        parse(block())
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
