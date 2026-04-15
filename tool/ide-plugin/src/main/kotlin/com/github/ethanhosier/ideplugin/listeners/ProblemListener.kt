package com.github.ethanhosier.ideplugin.listeners

import com.github.ethanhosier.ideplugin.model.EventType
import com.github.ethanhosier.ideplugin.model.FileChangeType
import com.github.ethanhosier.ideplugin.model.FileSnapshot
import com.github.ethanhosier.ideplugin.services.SessionService
import com.github.ethanhosier.ideplugin.util.shouldCapture
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.problems.ProblemListener
import java.util.concurrent.ConcurrentHashMap

/**
 * Emits FILE_ERRORS_CHANGED the moment the platform's WolfTheProblemSolver
 * reports a change, gated by per-file transition dedup: only emit when the
 * file's `hasErrors` differs from the last value we emitted for that file.
 *
 * The daemon already rate-limits these callbacks to once per re-analysis
 * cycle (a few per second at most), so a time-based debounce on top just
 * added lag. Transition dedup keeps the stream noise-free while staying in
 * sync with the highlighter — important for correlating error state with
 * REFACTORING_FINISHED and EDIT_BURST events in the analysis stage.
 */
class ProblemListener(private val project: Project) : ProblemListener {

    // Keyed by VirtualFile URL for stable identity across renames. Persists for
    // the lifetime of this listener instance (one per project subscription).
    private val lastEmittedHasErrors: ConcurrentHashMap<String, Boolean> = ConcurrentHashMap()

    override fun problemsAppeared(file: VirtualFile) = dispatch(file, hasErrors = true)
    override fun problemsChanged(file: VirtualFile) = dispatch(file, hasErrors = true)
    override fun problemsDisappeared(file: VirtualFile) = dispatch(file, hasErrors = false)

    private fun dispatch(file: VirtualFile, hasErrors: Boolean) {
        if (!file.shouldCapture()) return
        if (lastEmittedHasErrors.put(file.url, hasErrors) == hasErrors) return

        val snapshot = FileSnapshot(
            path = file.path,
            contents = readContents(file),
            changeType = FileChangeType.MODIFIED,
        )
        project.service<SessionService>().addEvent(
            type = EventType.FILE_ERRORS_CHANGED,
            changedFiles = listOf(snapshot),
            payload = mapOf("hasErrors" to hasErrors.toString()),
        )
    }

    private fun readContents(file: VirtualFile): String? {
        FileDocumentManager.getInstance().getDocument(file)?.let { return it.text }
        return try { String(file.contentsToByteArray()) } catch (e: Exception) { null }
    }
}
