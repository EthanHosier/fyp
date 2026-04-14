package com.github.ethanhosier.ideplugin.listeners

import com.github.ethanhosier.ideplugin.model.EventType
import com.github.ethanhosier.ideplugin.model.FileChangeType
import com.github.ethanhosier.ideplugin.model.FileSnapshot
import com.github.ethanhosier.ideplugin.services.SessionService
import com.github.ethanhosier.ideplugin.util.shouldCapture
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.*

class VfsListener : BulkFileListener {

    override fun after(events: List<VFileEvent>) {
        for (event in events) {
            when (event) {
                is VFileCreateEvent -> handle(
                    file = event.file ?: continue,
                    eventType = EventType.FILE_CREATED,
                    changeType = FileChangeType.CREATED,
                )

                is VFileDeleteEvent -> handle(
                    file = event.file,
                    eventType = EventType.FILE_DELETED,
                    changeType = FileChangeType.DELETED,
                )

                is VFileMoveEvent -> handle(
                    file = event.file,
                    eventType = EventType.FILE_MOVED,
                    changeType = FileChangeType.MOVED,
                    previousPath = event.oldPath,
                )

                is VFilePropertyChangeEvent if event.propertyName == "name" -> handle(
                    file = event.file,
                    eventType = EventType.FILE_RENAMED,
                    changeType = FileChangeType.RENAMED,
                    previousPath = event.oldPath,
                )

                // Content changes made inside the IDE editor are captured by EditBurstTracker
                // and then re-emerge here with isFromSave=true when the document flushes to
                // disk. Skip those to avoid double-counting. Anything else (git checkout,
                // external editor, codemod run from terminal) goes through.
                is VFileContentChangeEvent if !event.isFromSave -> handle(
                    file = event.file,
                    eventType = EventType.FILE_MODIFIED_EXTERNAL,
                    changeType = FileChangeType.MODIFIED,
                )

                else -> { /* ignored */ }
            }
        }
    }

    private fun handle(
        file: VirtualFile,
        eventType: EventType,
        changeType: FileChangeType,
        previousPath: String? = null,
    ) {
        // Only capture files inside a src/ folder (excludes directories,
        // our own output dir, .git, .idea, build output, config, etc.)
        if (!file.shouldCapture()) return

        // Only track files that belong to an open project.
        val project = ProjectLocator.getInstance().getProjectsForFile(file).firstOrNull() ?: return

        val sessionService = project.service<SessionService>()
        if (sessionService.getSessionId() == null) return

        val contents = if (changeType == FileChangeType.DELETED) {
            // File is already gone by the time after() fires — record null contents.
            null
        } else {
            readContents(file)
        }

        val snapshot = FileSnapshot(
            path = file.path,
            contents = contents,
            changeType = changeType,
            previousPath = previousPath,
        )

        sessionService.addEvent(
            type = eventType,
            changedFiles = listOf(snapshot),
            payload = buildMap {
                if (previousPath != null) put("previousPath", previousPath)
            },
        )
    }

    /**
     * Reads file contents preferring the in-memory document (open editor state) over
     * the VFS cache, so we get the exact text the user sees before any disk flush.
     */
    private fun readContents(file: VirtualFile): String? {
        FileDocumentManager.getInstance().getDocument(file)?.let { return it.text }
        return try { String(file.contentsToByteArray()) } catch (e: Exception) { null }
    }
}
