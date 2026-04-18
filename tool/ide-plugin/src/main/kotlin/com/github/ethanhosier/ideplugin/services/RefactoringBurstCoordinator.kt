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
 * Coordinates REFACTORING_FINISHED emission around the IntelliJ command
 * boundary so the emitted snapshot reflects the final, post-auto-format
 * state of every touched file.
 *
 * Every refactoring runs inside a `CommandProcessor` command. Inside that
 * command IntelliJ first runs the refactoring processor's PSI mutations
 * (at the end of which `RefactoringEventListener.refactoringDone` fires),
 * then runs the trailing "reformat at end of command" pass, then commits.
 * `CommandListener.commandFinished` fires after the whole command commits.
 *
 * The coordinator ignores `refactoringDone` as an emission trigger — it
 * stashes the platform's `afterData` for later. Emission happens when
 * `CommandListener` fires `commandFinished` (routed here via
 * [RefactoringCommandListener]). Reading file contents at that moment
 * gives the post-format text directly, so we no longer need any
 * analysis-side heuristic to reconcile the refactoring snapshot with a
 * trailing EDIT_BURST or a lagging VFS FILE_CREATED.
 *
 * In-place / preview refactorings terminated via [RefactoringTemplateListener]
 * still call [endRefactoringNow] because templates dismissed with Esc don't
 * fire `refactoringDone` and may not sit inside a command we can wait on.
 * Same for conflict/undo paths.
 */
@Service(Service.Level.PROJECT)
class RefactoringBurstCoordinator(private val project: Project) {

    private val lock = Any()
    private var activeRefactoringId: String? = null
    private var activeBeforeData: RefactoringEventData? = null
    private var pendingAfterData: RefactoringEventData? = null
    private var awaitingCommandFinish: Boolean = false
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
                emitFinishedLocked(outcome = "superseded", refactoringId = activeRefactoringId!!, afterData = pendingAfterData)
            }
            // Flush any pending edit burst so the pre-refactoring state is emitted as
            // its own EDIT_BURST before we start intercepting further changes.
            project.service<EditBurstTracker>().flushAllPending()
            activeRefactoringId = refactoringId
            activeBeforeData = beforeData
            pendingAfterData = null
            awaitingCommandFinish = false
            accumulator.clear()
        }
        project.service<SessionService>().addEvent(
            EventType.REFACTORING_STARTED,
            payload = mapOf("refactoringId" to refactoringId),
        )
    }

    /**
     * Called from [com.github.ethanhosier.ideplugin.listeners.RefactoringListener]
     * at `refactoringDone`. Stashes [afterData] and marks the refactoring as
     * waiting for its enclosing command to finish — actual emission happens in
     * [emitOnCommandFinish].
     */
    fun markRefactoringDone(refactoringId: String, afterData: RefactoringEventData?) {
        synchronized(lock) {
            val active = activeRefactoringId ?: return
            if (refactoringId != active) {
                thisLogger().warn(
                    "RefactoringTracer: markRefactoringDone($refactoringId) but active is $active — ignoring"
                )
                return
            }
            pendingAfterData = afterData
            awaitingCommandFinish = true
        }
    }

    /**
     * Called from [com.github.ethanhosier.ideplugin.listeners.RefactoringCommandListener]
     * on every `commandFinished`. Emits REFACTORING_FINISHED iff the coordinator is
     * waiting on a command boundary. A no-op otherwise (commands fire for ordinary
     * typing too).
     */
    fun emitOnCommandFinish() {
        synchronized(lock) {
            if (!awaitingCommandFinish) return
            val active = activeRefactoringId ?: return
            emitFinishedLocked(outcome = "done", refactoringId = active, afterData = pendingAfterData)
        }
    }

    /**
     * Immediate-emit path for terminators that don't sit inside a command we
     * can wait on — conflicts, undo, template cancel. [afterData] is whatever
     * the terminator had (often null for templates). No-op if no refactoring
     * is active.
     */
    fun endRefactoringNow(
        refactoringId: String?,
        outcome: String,
        afterData: RefactoringEventData? = null,
    ) {
        synchronized(lock) {
            val active = activeRefactoringId ?: return
            if (refactoringId != null && refactoringId != active) {
                thisLogger().warn(
                    "RefactoringTracer: endRefactoringNow($refactoringId) but active is $active — ending anyway"
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
        pendingAfterData = null
        awaitingCommandFinish = false
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
     *
     * Stays active through the whole command including the auto-format pass, so
     * the DocumentEvents the formatter fires for open files get folded in here
     * rather than escaping as a trailing EDIT_BURST.
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
        endRefactoringNow(refactoringId = null, outcome = outcome)
    }
}
