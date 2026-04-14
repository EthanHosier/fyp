package com.github.ethanhosier.ideplugin.listeners

import com.github.ethanhosier.ideplugin.services.EditBurstTracker
import com.github.ethanhosier.ideplugin.util.shouldCapture
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.ProjectLocator

class EditorEventListener : EditorFactoryListener, DocumentListener {

    override fun editorCreated(event: EditorFactoryEvent) {
        event.editor.document.addDocumentListener(this)
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        event.editor.document.removeDocumentListener(this)
    }

    override fun documentChanged(event: DocumentEvent) {
        val vFile = FileDocumentManager.getInstance().getFile(event.document) ?: return
        if (!vFile.shouldCapture()) return
        val project = ProjectLocator.getInstance().getProjectsForFile(vFile).firstOrNull() ?: return
        project.service<EditBurstTracker>().onDocumentChanged(vFile, event)
    }
}
