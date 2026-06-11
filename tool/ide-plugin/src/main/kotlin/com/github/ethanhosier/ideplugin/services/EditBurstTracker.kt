package com.github.ethanhosier.ideplugin.services

import com.github.ethanhosier.ideplugin.model.EventType
import com.github.ethanhosier.ideplugin.model.FileChangeType
import com.github.ethanhosier.ideplugin.model.FileSnapshot
import com.github.ethanhosier.ideplugin.model.TouchedMember
import com.github.ethanhosier.ideplugin.model.TraceEvent
import com.github.ethanhosier.ideplugin.refactoring.TouchedMemberResolver
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@Service(Service.Level.PROJECT)
class EditBurstTracker(private val project: Project) : Disposable {

    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "RefactoringTracer-EditDebounce").also { it.isDaemon = true }
    }

    private val accumulators: ConcurrentHashMap<String, BurstAccumulator> = ConcurrentHashMap()

    private val futureLock = Any()
    private var future: ScheduledFuture<*>? = null

    private data class BurstAccumulator(
        val fileUrl: String,
        val filePath: String,
        var charsInserted: Int = 0,
        var charsDeleted: Int = 0,
        var regionStart: Int = Int.MAX_VALUE,
        var regionEnd: Int = 0,
        // Captured from event.document.text on every change — reflects the exact in-memory
        // state at the time of the last edit to this file in this burst.
        var latestContents: String? = null,
        // Timestamp of the last edit to this file — used to derive the event timestamp
        // (max across files) so it reflects when the activity actually happened.
        var latestTimestamp: Long = System.currentTimeMillis(),
        val touchedMembers: LinkedHashSet<TouchedMember> = LinkedHashSet(),
    )

    fun onDocumentChanged(vFile: VirtualFile, event: DocumentEvent) {
        val key = vFile.url
        val inserted = maxOf(0, event.newLength - event.oldLength)
        val deleted = maxOf(0, event.oldLength - event.newLength)
        val regionEnd = event.offset + maxOf(event.oldLength, event.newLength)
        val currentText = event.document.text
        val document = event.document
        val members = ReadAction.compute<List<TouchedMember>, RuntimeException> {
            val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
                ?: return@compute emptyList()
            TouchedMemberResolver.fromRange(psiFile, event.offset, regionEnd)
        }

        accumulators.compute(key) { _, existing ->
            val acc = existing ?: BurstAccumulator(fileUrl = key, filePath = vFile.path)
            acc.charsInserted += inserted
            acc.charsDeleted += deleted
            acc.regionStart = minOf(acc.regionStart, event.offset)
            acc.regionEnd = maxOf(acc.regionEnd, regionEnd)
            acc.latestContents = currentText
            acc.latestTimestamp = System.currentTimeMillis()
            acc.touchedMembers.addAll(members)
            acc
        }

        synchronized(futureLock) {
            future?.cancel(false)
            future = if (scheduler.isShutdown) {
                null
            } else {
                scheduler.schedule({ flushAll() }, DEBOUNCE_MS, TimeUnit.MILLISECONDS)
            }
        }
    }

    private fun flushAll() {
        val accs: List<BurstAccumulator>
        synchronized(futureLock) {
            future = null
            accs = accumulators.values.toList()
            accumulators.clear()
        }

        if (accs.isEmpty()) return
        if (project.isDisposed) return

        val changedFiles = accs
            .sortedBy { it.filePath }
            .map { acc ->
                FileSnapshot(
                    path = acc.filePath,
                    contents = acc.latestContents,
                    changeType = FileChangeType.MODIFIED,
                    touchedMembers = acc.touchedMembers.toList(),
                )
            }

        val totalIns = accs.sumOf { it.charsInserted }
        val totalDel = accs.sumOf { it.charsDeleted }
        val latestTs = accs.maxOf { it.latestTimestamp }

        val sessionId = project.service<SessionService>().getSessionId() ?: return
        project.service<SessionService>().addEvent(
            TraceEvent(
                id = UUID.randomUUID().toString(),
                type = EventType.EDIT_BURST,
                timestamp = latestTs,
                sessionId = sessionId,
                changedFiles = changedFiles,
                payload = mapOf(
                    "charsInserted" to totalIns.toString(),
                    "charsDeleted" to totalDel.toString(),
                    "fileCount" to changedFiles.size.toString(),
                ),
            )
        )
        thisLogger().info(
            "RefactoringTracer: EDIT_BURST files=${changedFiles.size} " +
                "+$totalIns/-$totalDel paths=${changedFiles.joinToString(",") { it.path.substringAfterLast('/') }}"
        )
    }

    fun flushAllPending() {
        synchronized(futureLock) {
            future?.cancel(false)
            future = null
        }
        flushAll()
    }

    override fun dispose() {
        flushAllPending()
        scheduler.shutdownNow()
    }

    companion object {
        private const val DEBOUNCE_MS = 2000L
    }
}
