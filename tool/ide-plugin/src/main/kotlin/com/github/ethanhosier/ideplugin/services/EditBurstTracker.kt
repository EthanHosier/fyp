package com.github.ethanhosier.ideplugin.services

import com.github.ethanhosier.ideplugin.model.EventType
import com.github.ethanhosier.ideplugin.model.FileChangeType
import com.github.ethanhosier.ideplugin.model.FileSnapshot
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Owns all debounce state for edit burst detection.
 *
 * For each file being edited, accumulates character-level change stats and captures
 * the in-memory document text on every change. After [DEBOUNCE_MS] of inactivity,
 * flushes an EDIT_BURST event with the file contents as they were at the last edit —
 * using the in-memory document state (event.document.text) rather than reading from
 * disk, giving accurate pre-save contents regardless of auto-save or formatters.
 *
 * Lifecycle is tied to the project: the scheduler is shut down cleanly on dispose().
 */
@Service(Service.Level.PROJECT)
class EditBurstTracker(private val project: Project) : Disposable {

    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "RefactoringTracer-EditDebounce").also { it.isDaemon = true }
    }

    // Keyed by VirtualFile URL for stable identity across renames.
    private val accumulators: ConcurrentHashMap<String, BurstAccumulator> = ConcurrentHashMap()
    private val futures: ConcurrentHashMap<String, ScheduledFuture<*>> = ConcurrentHashMap()

    private data class BurstAccumulator(
        val fileUrl: String,
        val filePath: String,
        var charsInserted: Int = 0,
        var charsDeleted: Int = 0,
        var regionStart: Int = Int.MAX_VALUE,
        var regionEnd: Int = 0,
        // Captured from event.document.text on every change — reflects the exact in-memory
        // state at the time of the last edit in this burst.
        var latestContents: String? = null,
    )

    fun onDocumentChanged(vFile: VirtualFile, event: DocumentEvent) {
        val key = vFile.url
        val inserted = maxOf(0, event.newLength - event.oldLength)
        val deleted = maxOf(0, event.oldLength - event.newLength)
        val regionEnd = event.offset + maxOf(event.oldLength, event.newLength)
        val currentText = event.document.text

        accumulators.compute(key) { _, existing ->
            val acc = existing ?: BurstAccumulator(fileUrl = key, filePath = vFile.path)
            acc.charsInserted += inserted
            acc.charsDeleted += deleted
            acc.regionStart = minOf(acc.regionStart, event.offset)
            acc.regionEnd = maxOf(acc.regionEnd, regionEnd)
            acc.latestContents = currentText
            acc
        }

        // Reset the debounce timer.
        futures.remove(key)?.cancel(false)
        if (!scheduler.isShutdown) {
            futures[key] = scheduler.schedule({ flush(key) }, DEBOUNCE_MS, TimeUnit.MILLISECONDS)
        }
    }

    private fun flush(key: String) {
        val acc = accumulators.remove(key) ?: return
        futures.remove(key)

        if (project.isDisposed) return

        val changedFiles = listOf(
            FileSnapshot(
                path = acc.filePath,
                contents = acc.latestContents,
                changeType = FileChangeType.MODIFIED,
            )
        )

        project.service<SessionService>().addEvent(
            type = EventType.EDIT_BURST,
            changedFiles = changedFiles,
            payload = mapOf(
                "charsInserted" to acc.charsInserted.toString(),
                "charsDeleted" to acc.charsDeleted.toString(),
                "regionStart" to acc.regionStart.toString(),
                "regionEnd" to acc.regionEnd.toString(),
            ),
        )
        thisLogger().info(
            "RefactoringTracer: EDIT_BURST path=${acc.filePath} " +
            "+${acc.charsInserted}/-${acc.charsDeleted} " +
            "region=[${acc.regionStart},${acc.regionEnd}]"
        )
    }

    override fun dispose() {
        futures.values.forEach { it.cancel(false) }
        futures.clear()
        accumulators.clear()
        scheduler.shutdownNow()
    }

    companion object {
        private const val DEBOUNCE_MS = 2000L
    }
}
