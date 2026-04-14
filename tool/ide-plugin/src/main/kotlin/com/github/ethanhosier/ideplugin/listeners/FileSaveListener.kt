package com.github.ethanhosier.ideplugin.listeners

import com.github.ethanhosier.ideplugin.model.EventType
import com.github.ethanhosier.ideplugin.services.SessionService
import com.github.ethanhosier.ideplugin.util.shouldCapture
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.project.ProjectLocator

class FileSaveListener : FileDocumentManagerListener {

    override fun beforeDocumentSaving(document: Document) {
        val vFile = FileDocumentManager.getInstance().getFile(document) ?: return
        if (!vFile.shouldCapture()) return
        val project = ProjectLocator.getInstance().getProjectsForFile(vFile).firstOrNull() ?: return
        project.service<SessionService>().addEvent(EventType.FILE_SAVED, relatedFiles = listOf(vFile.path))
    }
}
