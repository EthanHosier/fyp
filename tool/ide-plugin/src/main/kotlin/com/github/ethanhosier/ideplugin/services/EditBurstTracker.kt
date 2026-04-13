package com.github.ethanhosier.ideplugin.services

import com.github.ethanhosier.ideplugin.model.EventType
import com.github.ethanhosier.ideplugin.model.FileChangeType
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
 * For each file being edited, it accumulates character-level change stats and
 * schedules a flush after [DEBOUNCE_MS] of inactivity. On flush, it emits an
 * EDIT_BURST TraceEvent and marks the file dirty for the next checkpoint.
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
    )

    fun onDocumentChanged(vFile: VirtualFile, event: DocumentEvent) {
        val key = vFile.url
        val inserted = maxOf(0, event.newLength - event.oldLength)
        val deleted = maxOf(0, event.oldLength - event.newLength)
        val regionEnd = event.offset + maxOf(event.oldLength, event.newLength)

        accumulators.compute(key) { _, existing ->
            val acc = existing ?: BurstAccumulator(fileUrl = key, filePath = vFile.path)
            acc.charsInserted += inserted
            acc.charsDeleted += deleted
            acc.regionStart = minOf(acc.regionStart, event.offset)
            acc.regionEnd = maxOf(acc.regionEnd, regionEnd)
            acc
        }

        // Reset the debounce timer.
        futures.remove(key)?.cancel(false)
        if (!scheduler.isShutdown) {
            futures[key] = scheduler.schedule({ flush(key) }, DEBOUNCE_MS, TimeUnit.MILLISECONDS)
        }

        thisLogger().info("EditBurstTracker EditBurstTracker $key!!!!")
        // Mark dirty immediately so FileStateTracker is up-to-date even before the burst flushes.
        project.service<FileStateTracker>().markDirty(vFile.path, FileChangeType.MODIFIED)
    }

    private fun flush(key: String) {
        val acc = accumulators.remove(key) ?: return
        futures.remove(key)

        if (project.isDisposed) return

        project.service<SessionService>().addEvent(
            EventType.EDIT_BURST,
            relatedFiles = listOf(acc.filePath),
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
