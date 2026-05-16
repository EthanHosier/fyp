package com.github.ethanhosier.ideplugin.listeners

import com.github.ethanhosier.ideplugin.services.EditBurstTracker
import com.github.ethanhosier.ideplugin.services.RefactoringBurstCoordinator
import com.github.ethanhosier.ideplugin.util.shouldCapture
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator

/**
 * Captures every Document edit across the IDE for one specific project.
 *
 * Registered on `EditorFactory.eventMulticaster` from
 * [com.github.ethanhosier.ideplugin.startup.TracerStartupActivity], which
 * runs per-project. The multicaster is **application-scoped**, so every
 * documentChanged event fans out to every registered listener — including
 * those belonging to other projects. A listener that handled events from
 * any project would route the same event to its project's
 * [EditBurstTracker] twice when two projects are open, breaking the
 * debounce (one accumulator gets flushed by Project-A's listener before
 * Project-B's listener arrives, so the same edit lands in two separate
 * EDIT_BURSTs instead of one).
 *
 * The listener is therefore parameterised on [project] and silently
 * ignores documents that don't belong to it. With N projects open, every
 * edit still fires N callbacks total but only the one matching project
 * acts on it.
 *
 * The multicaster also dispatches once per document change regardless of
 * editor lifecycle, so project-wide Find/Replace — which mutates
 * documents without opening editors for un-visible files — is captured
 * for every affected file.
 */
class EditorEventListener(private val project: Project) : DocumentListener {

    override fun documentChanged(event: DocumentEvent) {
        val vFile = FileDocumentManager.getInstance().getFile(event.document) ?: return
        if (!vFile.shouldCapture()) return
        // Multicaster is application-wide; only act on documents that
        // belong to our project so each event is handled once per project.
        if (project !in ProjectLocator.getInstance().getProjectsForFile(vFile)) return
        // Route through the coordinator first: during a refactoring it absorbs the
        // change into the REFACTORING_FINISHED snapshot instead of letting EBT emit
        // an EDIT_BURST that could bleed across the refactoring boundary.
        if (project.service<RefactoringBurstCoordinator>().onDocumentChanged(vFile, event)) return
        project.service<EditBurstTracker>().onDocumentChanged(vFile, event)
    }
}
