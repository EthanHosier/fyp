package com.github.ethanhosier.ideplugin.services

import com.github.ethanhosier.ideplugin.model.Session
import com.github.ethanhosier.ideplugin.model.TraceEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter

@Service(Service.Level.PROJECT)
class StorageService {

    private val json = Json { prettyPrint = false; encodeDefaults = true }
    private val prettyJson = Json { prettyPrint = true; encodeDefaults = true }

    private val lock = Any()

    @Volatile private var sessionDir: File? = null
    private var eventsWriter: BufferedWriter? = null

    fun init(sessionId: String, projectBasePath: String) = synchronized(lock) {
        // Close any writer left over from a previous session that wasn't flushed
        // (e.g. if startSession was called twice without endSession in between).
        eventsWriter?.runCatching { close() }
        eventsWriter = null

        val base = File(projectBasePath, ".refactoring-traces/$sessionId")
        base.mkdirs()

        sessionDir = base
        eventsWriter = BufferedWriter(
            OutputStreamWriter(FileOutputStream(File(base, "events.jsonl"), /* append = */ true), Charsets.UTF_8)
        )

        ensureGitignoreEntry(File(projectBasePath))

        thisLogger().info("RefactoringTracer: storage initialised at ${base.absolutePath}")
    }

    private fun ensureGitignoreEntry(projectRoot: File) {
        if (!File(projectRoot, ".git").exists()) return
        try {
            val gitignore = File(projectRoot, ".gitignore")
            val entry = ".refactoring-traces/"
            val existing = if (gitignore.exists()) gitignore.readLines() else emptyList()
            if (existing.any { it.trim() == entry }) return
            val prefix = if (existing.isNotEmpty() && existing.last().isNotBlank()) "\n" else ""
            gitignore.appendText("$prefix$entry\n")
        } catch (e: Exception) {
            thisLogger().warn("RefactoringTracer: could not update .gitignore: ${e.message}")
        }
    }

    fun flushEvent(event: TraceEvent) = synchronized(lock) {
        val writer = eventsWriter ?: return@synchronized
        try {
            val line = json.encodeToString(event)
            // Log a summary only — the event JSON can include full file contents, which would
            // echo source code into the IDE log and blow up log size over a long session.
            thisLogger().info(
                "RefactoringTracer: flushed ${event.type} id=${event.id} bytes=${line.length}"
            )
            writer.write(line)
            writer.write("\n")
            writer.flush()
        } catch (e: Exception) {
            thisLogger().warn("RefactoringTracer: failed to flush event id=${event.id}: ${e.message}")
        }
    }

    fun flushSession(session: Session) = synchronized(lock) {
        val dir = sessionDir ?: return@synchronized
        try {
            eventsWriter?.close()
            eventsWriter = null
            File(dir, "session.json").writeText(prettyJson.encodeToString(session))
        } catch (e: Exception) {
            thisLogger().warn("RefactoringTracer: failed to flush session: ${e.message}")
        }
    }

    fun getSessionDir(): File? = sessionDir

    fun writeSessionFile(relativePath: String, bytes: ByteArray, executable: Boolean = false) = synchronized(lock) {
        val dir = sessionDir ?: return@synchronized
        try {
            val target = File(dir, relativePath)
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
            if (executable) target.setExecutable(true, false)
        } catch (e: Exception) {
            thisLogger().warn("RefactoringTracer: failed to write session file $relativePath: ${e.message}")
        }
    }
}
