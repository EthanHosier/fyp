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

/**
 * Owns all debounce state for edit burst detection.
 *
 * Edits across **any** open document accumulate into one global burst
 * window — a single shared debounce timer is rescheduled on every
 * change, so a sequence of edits separated by less than [DEBOUNCE_MS]
 * (even if they touch different files) flushes as one EDIT_BURST event
 * with a `changedFiles` list containing every file that was touched in
 * the window. This is the right semantics for multi-file operations
 * like project-wide Find/Replace, IDE Rename across call sites, and
 * any human-driven sequence of related edits across files. Per-file
 * counters and content snapshots are still kept (in [accumulators]) so
 * the emitted event carries one [FileSnapshot] per touched file.
 *
 * Lifecycle is tied to the project: the scheduler is shut down cleanly
 * on dispose().
 */
@Service(Service.Level.PROJECT)
class EditBurstTracker(private val project: Project) : Disposable {

    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "RefactoringTracer-EditDebounce").also { it.isDaemon = true }
    }

    // Per-file content/region/touched-members snapshots. Keyed by VirtualFile URL
    // for stable identity across renames. Multiple keys can be active at once if
    // the user touches multiple files inside one burst window — every entry here
    // becomes one FileSnapshot inside the next emitted EDIT_BURST.
    private val accumulators: ConcurrentHashMap<String, BurstAccumulator> = ConcurrentHashMap()

    // One shared debounce timer. Rescheduled on every onDocumentChanged so the
    // flush only fires after DEBOUNCE_MS of inactivity across *all* files. Mutated
    // only under [futureLock] to keep cancel-and-reschedule atomic relative to
    // flushAll().
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
        // Class/method members touched by any DocumentEvent in this burst. Resolved
        // per-event because merging regionStart/regionEnd across events would falsely
        // include members that sit between two actually-edited regions.
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

        // Reset the single shared debounce timer. Held under a lock so a concurrent
        // flushAll() can't slip between cancel and schedule — that would leave the
        // accumulator alive with no pending flush and lose the burst until the next
        // edit.
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
            // Snapshot + clear together so a concurrent onDocumentChanged that arrives
            // *after* we read can't lose its update — it'll see an empty map and
            // start a fresh burst.
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

    /**
     * Synchronously flushes the pending burst (if any), cancelling the scheduled
     * future first so the debounce timer can't race with the final flush. Called
     * from SessionService.endSession so the last ~2 s of typing before a session
     * ends is preserved instead of silently dropped.
     */
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
