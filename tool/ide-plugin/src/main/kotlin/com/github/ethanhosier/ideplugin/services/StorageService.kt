package com.github.ethanhosier.ideplugin.services

import com.github.ethanhosier.ideplugin.model.Session
import com.github.ethanhosier.ideplugin.model.TraceEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileWriter

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

    private var sessionDir: File? = null
    private var eventsWriter: FileWriter? = null

    fun init(sessionId: String, projectBasePath: String) {
        val base = File(projectBasePath, ".refactoring-traces/$sessionId")
        base.mkdirs()

        sessionDir = base
        eventsWriter = FileWriter(File(base, "events.jsonl"), /* append = */ true)

        thisLogger().info("RefactoringTracer: storage initialised at ${base.absolutePath}")
    }

    /** Appends a single event as one JSON line to events.jsonl. */
    fun flushEvent(event: TraceEvent) {
        val writer = eventsWriter ?: return
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
    fun flushSession(session: Session) {
        val dir = sessionDir ?: return
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
