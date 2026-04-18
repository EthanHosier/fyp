package com.github.ethanhosier.ideplugin.services

import com.github.ethanhosier.ideplugin.model.EventType
import com.github.ethanhosier.ideplugin.model.FileChangeType
import com.github.ethanhosier.ideplugin.model.FileSnapshot
import com.github.ethanhosier.ideplugin.refactoring.RefactoringTypeRegistry
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.refactoring.listeners.RefactoringEventData

/**
 * Coordinates edit-burst routing around refactoring boundaries.
 *
 * When no refactoring is active, document changes fall through to [EditBurstTracker]
 * which emits normal EDIT_BURST events via its 2 s debounce.
 *
 * When a refactoring is active, document changes are intercepted and recorded per
 * touched file (no debounce, no EDIT_BURST). At refactoring end we combine those
 * with a per-refactoring-type snapshot pass driven by [RefactoringTypeRegistry]
 * (which reads the platform's before/after PSI state) and emit a single
 * REFACTORING_FINISHED event carrying accurate `changeType` for each file.
 *
 * Terminators can come from two sources: modal refactorings via
 * RefactoringEventListener (refactoringDone / conflictsDetected / undoRefactoring),
 * and in-place / preview refactorings via TemplateManagerListener (the platform
 * does not fire a RefactoringEventListener terminator when the user dismisses an
 * in-place refactoring with Esc). [endRefactoring] is idempotent — whichever
 * source fires first wins; the second is a no-op.
 */
@Service(Service.Level.PROJECT)
class RefactoringBurstCoordinator(private val project: Project) {

    private val lock = Any()
    private var activeRefactoringId: String? = null
    private var activeBeforeData: RefactoringEventData? = null
    // path -> latest in-memory document text observed during the refactoring.
    // LinkedHashMap so the emitted changedFiles preserve first-touched order.
    private val accumulator: LinkedHashMap<String, String> = LinkedHashMap()

    fun isActive(): Boolean = synchronized(lock) { activeRefactoringId != null }

    fun beginRefactoring(refactoringId: String, beforeData: RefactoringEventData? = null) {
        synchronized(lock) {
            if (activeRefactoringId != null) {
                // Unexpected nesting. Close the outer one defensively so we don't lose
                // its accumulated state, then start fresh. Logged so we notice if
                // IntelliJ actually produces this in practice.
                thisLogger().warn(
                    "RefactoringTracer: beginRefactoring($refactoringId) while " +
                    "$activeRefactoringId already active — closing previous as 'superseded'"
                )
                emitFinishedLocked(outcome = "superseded", refactoringId = activeRefactoringId!!, afterData = null)
            }
            // Flush any pending edit burst so the pre-refactoring state is emitted as
            // its own EDIT_BURST before we start intercepting further changes.
            project.service<EditBurstTracker>().flushAllPending()
            activeRefactoringId = refactoringId
            activeBeforeData = beforeData
            accumulator.clear()
        }
        project.service<SessionService>().addEvent(
            EventType.REFACTORING_STARTED,
            payload = mapOf("refactoringId" to refactoringId),
        )
    }

    /**
     * Ends the active refactoring if any, emitting REFACTORING_FINISHED with the
     * accumulated file snapshots. No-op if no refactoring is active.
     *
     * [refactoringId] is optional: the RefactoringEventListener knows the id; the
     * template-based terminator does not. When supplied it must match the active
     * id or we log and proceed (the terminator wins regardless). [afterData] is
     * passed through to the per-type handler in [RefactoringTypeRegistry]; null
     * when the terminator doesn't have one (e.g. template dismissal).
     */
    fun endRefactoring(
        refactoringId: String?,
        outcome: String,
        afterData: RefactoringEventData? = null,
    ) {
        synchronized(lock) {
            val active = activeRefactoringId ?: return
            if (refactoringId != null && refactoringId != active) {
                thisLogger().warn(
                    "RefactoringTracer: endRefactoring($refactoringId) but active is $active — ending anyway"
                )
            }
            emitFinishedLocked(outcome = outcome, refactoringId = active, afterData = afterData)
        }
    }

    /** Caller must hold [lock]. */
    private fun emitFinishedLocked(
        outcome: String,
        refactoringId: String,
        afterData: RefactoringEventData?,
    ) {
        val handlerSnapshots = ApplicationManager.getApplication().runReadAction<List<FileSnapshot>> {
            RefactoringTypeRegistry.handlerFor(refactoringId)
                .snapshotsFor(project, activeBeforeData, afterData)
        }
        // Start from handler output (has accurate changeType + previousPath) and
        // overlay DocumentEvent-observed text for any path the user actively edited
        // during the refactoring — the keystroke-accurate view wins over the
        // post-refactor PSI read for those specific files.
        val merged = LinkedHashMap<String, FileSnapshot>()
        for (snap in handlerSnapshots) merged[snap.path] = snap
        for ((path, contents) in accumulator) {
            val existing = merged[path]
            merged[path] = if (existing != null) {
                existing.copy(contents = contents)
            } else {
                FileSnapshot(path, contents, FileChangeType.MODIFIED)
            }
        }

        activeRefactoringId = null
        activeBeforeData = null
        accumulator.clear()

        project.service<SessionService>().addEvent(
            EventType.REFACTORING_FINISHED,
            changedFiles = merged.values.toList(),
            payload = mapOf("refactoringId" to refactoringId, "outcome" to outcome),
        )
    }

    /**
     * If a refactoring is active, absorb the document change into the accumulator
     * and return true. Otherwise return false so the caller can route to
     * [EditBurstTracker] as normal.
     */
    fun onDocumentChanged(vFile: VirtualFile, event: DocumentEvent): Boolean {
        synchronized(lock) {
            if (activeRefactoringId == null) return false
            accumulator[vFile.path] = event.document.text
            return true
        }
    }

    /** Called from [SessionService.endSession] to close any in-flight refactoring before EBT flush. */
    fun flushIfActive(outcome: String) {
        endRefactoring(refactoringId = null, outcome = outcome)
    }
}
