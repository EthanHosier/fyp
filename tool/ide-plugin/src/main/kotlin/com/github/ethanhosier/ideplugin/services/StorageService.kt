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

/**
 * Handles all disk I/O for a session.
 *
 * Output layout:
 *   <projectDir>/.refactoring-traces/<sessionId>/
 *     ├── session.json    ← written on session end
 *     └── events.jsonl   ← appended live (one JSON object per line, crash-safe)
 *
 * Call [init] once after the session ID is known (from SessionService.startSession).
 */
@Service(Service.Level.PROJECT)
class StorageService {

    private val json = Json { prettyPrint = false; encodeDefaults = true }
    private val prettyJson = Json { prettyPrint = true; encodeDefaults = true }

    private val lock = Any()

    @Volatile private var sessionDir: File? = null
    private var eventsWriter: BufferedWriter? = null

    fun init(sessionId: String, projectBasePath: String) = synchronized(lock) {
        val base = File(projectBasePath, ".refactoring-traces/$sessionId")
        base.mkdirs()

        sessionDir = base
        eventsWriter = BufferedWriter(
            OutputStreamWriter(FileOutputStream(File(base, "events.jsonl"), /* append = */ true), Charsets.UTF_8)
        )

        ensureGitignoreEntry(File(projectBasePath))

        thisLogger().info("RefactoringTracer: storage initialised at ${base.absolutePath}")
    }

    /**
     * If the project is a git repo, append `.refactoring-traces/` to `.gitignore` (creating the
     * file if missing) so captured trace data doesn't sneak into commits via `git add .`.
     * No-op for non-git projects.
     */
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

    /** Appends a single event as one JSON line to events.jsonl. */
    fun flushEvent(event: TraceEvent) = synchronized(lock) {
        val writer = eventsWriter ?: return@synchronized
        try {
            val line = json.encodeToString(event)
            thisLogger().info("RefactoringTracer: $line")
            writer.write(line)
            writer.write("\n")
            writer.flush()
        } catch (e: Exception) {
            thisLogger().warn("RefactoringTracer: failed to flush event id=${event.id}: ${e.message}")
        }
    }

    /** Writes the full session summary. Called once on session end. */
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
}
