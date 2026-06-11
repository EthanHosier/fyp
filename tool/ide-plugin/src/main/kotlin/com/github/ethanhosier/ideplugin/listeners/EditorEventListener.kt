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

class EditorEventListener(private val project: Project) : DocumentListener {

    override fun documentChanged(event: DocumentEvent) {
        val vFile = FileDocumentManager.getInstance().getFile(event.document) ?: return
        if (!vFile.shouldCapture()) return
        // Multicaster is application-wide; only act on documents that
        // belong to our project so each event is handled once per project.
        if (project !in ProjectLocator.getInstance().getProjectsForFile(vFile)) return
        if (project.service<RefactoringBurstCoordinator>().onDocumentChanged(vFile, event)) return
        project.service<EditBurstTracker>().onDocumentChanged(vFile, event)
    }
}
