package com.github.ethanhosier.ideplugin.services

import com.github.ethanhosier.ideplugin.model.EventType
import com.github.ethanhosier.ideplugin.model.FileChangeType
import com.github.ethanhosier.ideplugin.model.FileSnapshot
import com.github.ethanhosier.ideplugin.model.TraceEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Debounces FILE_ERRORS_CHANGED events per file.
 *
 * The debounce key is the file URL. Rapid successive problem changes on the same file
 * (e.g. multiple inspections firing in quick succession) are collapsed into one event
 * reflecting the final state. The timestamp is captured on the first detection within
 * each debounce window so it reflects when the change actually happened.
 */
@Service(Service.Level.PROJECT)
class ProblemDebouncer(private val project: Project) : Disposable {

    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "RefactoringTracer-ProblemDebounce").also { it.isDaemon = true }
    }

    private data class PendingProblem(val snapshot: FileSnapshot, val hasErrors: Boolean, val timestamp: Long)

    private val pending: ConcurrentHashMap<String, PendingProblem> = ConcurrentHashMap()
    private val futures: ConcurrentHashMap<String, ScheduledFuture<*>> = ConcurrentHashMap()

    fun onProblemsChanged(file: VirtualFile, hasErrors: Boolean) {
        val key = file.url
        val snapshot = FileSnapshot(
            path = file.path,
            contents = readContents(file),
            changeType = FileChangeType.MODIFIED,
        )
        // Always update snapshot and hasErrors to reflect latest state.
        // Only preserve the timestamp from the first detection in this window.
        pending.compute(key) { _, existing ->
            PendingProblem(snapshot, hasErrors, existing?.timestamp ?: System.currentTimeMillis())
        }
        // Known race: if flush(key) is already executing when cancel(false) is called, the
        // cancel returns false and flush proceeds. A concurrent onProblemsChanged call may then
        // write new data into pending and schedule a new future. flush() will subsequently
        // remove that new data via pending.remove(key), causing the new future to fire with
        // nothing to flush — silently dropping one event. The window is the duration of flush()
        // itself (a single ConcurrentHashMap remove + addEvent call), so in practice this is
        // extremely unlikely. Acceptable for thesis data collection; fix by serialising all
        // mutations through the scheduler thread if stronger guarantees are ever needed.
        futures.remove(key)?.cancel(false)
        if (!scheduler.isShutdown) {
            futures[key] = scheduler.schedule({ flush(key) }, DEBOUNCE_MS, TimeUnit.MILLISECONDS)
        }
    }

    private fun flush(key: String) {
        val (snapshot, hasErrors, timestamp) = pending.remove(key) ?: return
        futures.remove(key)
        if (project.isDisposed) return
        val sessionId = project.service<SessionService>().getSessionId() ?: return
        project.service<SessionService>().addEvent(
            TraceEvent(
                id = UUID.randomUUID().toString(),
                type = EventType.FILE_ERRORS_CHANGED,
                timestamp = timestamp,
                sessionId = sessionId,
                changedFiles = listOf(snapshot),
                payload = mapOf("hasErrors" to hasErrors.toString()),
            )
        )
    }

    private fun readContents(file: VirtualFile): String? {
        FileDocumentManager.getInstance().getDocument(file)?.let { return it.text }
        return try { String(file.contentsToByteArray()) } catch (e: Exception) { null }
    }

    override fun dispose() {
        futures.values.forEach { it.cancel(false) }
        futures.clear()
        pending.clear()
        scheduler.shutdownNow()
    }

    companion object {
        private const val DEBOUNCE_MS = 2000L
    }
}
