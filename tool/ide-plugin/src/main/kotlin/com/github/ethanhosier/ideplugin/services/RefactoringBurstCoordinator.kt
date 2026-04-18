package com.github.ethanhosier.ideplugin.services

import com.github.ethanhosier.ideplugin.model.EventType
import com.github.ethanhosier.ideplugin.model.FileChangeType
import com.github.ethanhosier.ideplugin.model.FileSnapshot
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Coordinates edit-burst routing around refactoring boundaries.
 *
 * When no refactoring is active, document changes fall through to [EditBurstTracker]
 * which emits normal EDIT_BURST events via its 2 s debounce.
 *
 * When a refactoring is active, document changes are intercepted: we record only
 * the latest in-memory contents per touched file (no debounce, no EDIT_BURST), and
 * at refactoring end we emit a single REFACTORING_FINISHED event carrying those
 * contents as its changedFiles. This gives a clean per-refactoring state capture
 * that a post-refactoring keystroke cannot contaminate.
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
    // path -> latest in-memory document text observed during the refactoring.
    // LinkedHashMap so the emitted changedFiles preserve first-touched order.
    private val accumulator: LinkedHashMap<String, String> = LinkedHashMap()

    fun isActive(): Boolean = synchronized(lock) { activeRefactoringId != null }

    fun beginRefactoring(refactoringId: String) {
        synchronized(lock) {
            if (activeRefactoringId != null) {
                // Unexpected nesting. Close the outer one defensively so we don't lose
                // its accumulated state, then start fresh. Logged so we notice if
                // IntelliJ actually produces this in practice.
                thisLogger().warn(
                    "RefactoringTracer: beginRefactoring($refactoringId) while " +
                    "$activeRefactoringId already active — closing previous as 'superseded'"
                )
                emitFinishedLocked(outcome = "superseded", refactoringId = activeRefactoringId!!)
            }
            // Flush any pending edit burst so the pre-refactoring state is emitted as
            // its own EDIT_BURST before we start intercepting further changes.
            project.service<EditBurstTracker>().flushAllPending()
            activeRefactoringId = refactoringId
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
     * id or we log and proceed (the terminator wins regardless).
     */
    fun endRefactoring(
        refactoringId: String?,
        outcome: String,
        extraSnapshots: Map<String, String> = emptyMap(),
    ) {
        synchronized(lock) {
            val active = activeRefactoringId ?: return
            if (refactoringId != null && refactoringId != active) {
                thisLogger().warn(
                    "RefactoringTracer: endRefactoring($refactoringId) but active is $active — ending anyway"
                )
            }
            // DocumentEvent-observed edits (for files the user had open) win over
            // afterData-derived snapshots, so keystroke-accurate text isn't
            // overwritten by a post-refactor PSI read. afterData only fills in
            // files that had no editor — notably new files created by the
            // refactoring itself, which the document listener never sees.
            extraSnapshots.forEach { (path, contents) -> accumulator.putIfAbsent(path, contents) }
            emitFinishedLocked(outcome = outcome, refactoringId = active)
        }
    }

    /** Caller must hold [lock]. */
    private fun emitFinishedLocked(outcome: String, refactoringId: String) {
        val snapshots = accumulator.map { (path, contents) ->
            FileSnapshot(path = path, contents = contents, changeType = FileChangeType.MODIFIED)
        }
        activeRefactoringId = null
        accumulator.clear()
        project.service<SessionService>().addEvent(
            EventType.REFACTORING_FINISHED,
            changedFiles = snapshots,
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
