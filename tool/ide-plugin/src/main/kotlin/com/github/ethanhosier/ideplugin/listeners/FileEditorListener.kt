package com.github.ethanhosier.ideplugin.listeners

import com.github.ethanhosier.ideplugin.model.EventType
import com.github.ethanhosier.ideplugin.services.SessionService
import com.github.ethanhosier.ideplugin.util.isTracesFile
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class FileEditorListener(private val project: Project) : FileEditorManagerListener {

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        if (file.isTracesFile()) return
        project.service<SessionService>().addEvent(EventType.FILE_OPENED, relatedFiles = listOf(file.path))
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        if (file.isTracesFile()) return
        project.service<SessionService>().addEvent(EventType.FILE_CLOSED, relatedFiles = listOf(file.path))
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
        val sessionService = project.service<SessionService>()
        event.oldFile?.takeUnless { it.isTracesFile() }
            ?.let { sessionService.addEvent(EventType.FILE_UNFOCUSED, relatedFiles = listOf(it.path)) }
        event.newFile?.takeUnless { it.isTracesFile() }
            ?.let { sessionService.addEvent(EventType.FILE_FOCUSED, relatedFiles = listOf(it.path)) }
    }
}
