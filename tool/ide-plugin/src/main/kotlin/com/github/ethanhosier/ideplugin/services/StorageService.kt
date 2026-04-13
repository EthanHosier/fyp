package com.github.ethanhosier.ideplugin.services

import com.github.ethanhosier.ideplugin.model.Checkpoint
import com.github.ethanhosier.ideplugin.model.Session
import com.github.ethanhosier.ideplugin.model.TraceEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileWriter
import java.util.concurrent.atomic.AtomicInteger

/**
 * Handles all disk I/O for a session.
 *
 * Output layout:
 *   <projectDir>/.refactoring-traces/<sessionId>/
 *     ├── session.json          ← written on session end
 *     ├── events.jsonl          ← appended live (one JSON object per line)
 *     └── checkpoints/
 *         ├── 001_<id>.json
 *         └── ...
 *
 * Call [init] once after the session ID is known (i.e. from SessionService.startSession).
 */
@Service(Service.Level.PROJECT)
class StorageService {

    private val json = Json { prettyPrint = false; encodeDefaults = true }
    private val prettyJson = Json { prettyPrint = true; encodeDefaults = true }

    private var sessionDir: File? = null
    private var checkpointDir: File? = null
    private var eventsFile: File? = null
    private var eventsWriter: FileWriter? = null
    private val checkpointSeq = AtomicInteger(0)

    fun init(sessionId: String, projectBasePath: String) {
        val base = File(projectBasePath, ".refactoring-traces/$sessionId")
        base.mkdirs()
        val cpDir = File(base, "checkpoints")
        cpDir.mkdirs()

        sessionDir = base
        checkpointDir = cpDir
        eventsFile = File(base, "events.jsonl")
        eventsWriter = FileWriter(eventsFile!!, /* append = */ true)

        thisLogger().info("RefactoringTracer: storage initialised at ${base.absolutePath}")
    }

    /** Appends a single event as one JSON line to events.jsonl. */
    fun flushEvent(event: TraceEvent) {
        val writer = eventsWriter ?: return
        try {
            writer.write(json.encodeToString(event))
            writer.write("\n")
            writer.flush()
        } catch (e: Exception) {
            thisLogger().warn("RefactoringTracer: failed to flush event id=${event.id}: ${e.message}")
        }
    }

    /** Writes a checkpoint as its own numbered JSON file. */
    fun flushCheckpoint(checkpoint: Checkpoint) {
        val dir = checkpointDir ?: return
        val seq = checkpointSeq.incrementAndGet()
        val filename = "%03d_%s.json".format(seq, checkpoint.id)
        try {
            File(dir, filename).writeText(prettyJson.encodeToString(checkpoint))
        } catch (e: Exception) {
            thisLogger().warn("RefactoringTracer: failed to flush checkpoint id=${checkpoint.id}: ${e.message}")
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
