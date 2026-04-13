package com.github.ethanhosier.ideplugin.listeners

import com.github.ethanhosier.ideplugin.model.EventType
import com.github.ethanhosier.ideplugin.model.FileChangeType
import com.github.ethanhosier.ideplugin.services.CheckpointService
import com.github.ethanhosier.ideplugin.services.FileStateTracker
import com.github.ethanhosier.ideplugin.services.SessionService
import com.intellij.openapi.components.service
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
                    triggerCheckpoint = true,
                )

                is VFilePropertyChangeEvent if event.propertyName == "name" -> handle(
                    file = event.file,
                    eventType = EventType.FILE_RENAMED,
                    changeType = FileChangeType.RENAMED,
                    previousPath = event.file.parent?.path + "/" + event.oldValue,
                    triggerCheckpoint = true,
                )

                else -> { /* content changes handled via DocumentListener */ }
            }
        }
    }

    private fun handle(
        file: VirtualFile,
        eventType: EventType,
        changeType: FileChangeType,
        previousPath: String? = null,
        triggerCheckpoint: Boolean = false,
    ) {
        // Skip directories — we only track file-level changes.
        if (file.isDirectory) return

        // Skip our own output directory to avoid feedback loops.
        if (file.path.contains("/.refactoring-traces/")) return

        // Only track files that belong to an open project.
        val project = ProjectLocator.getInstance().getProjectsForFile(file).firstOrNull() ?: return

        val path = file.path
        val sessionService = project.service<SessionService>()
        if (sessionService.getSessionId() == null) return

        project.service<FileStateTracker>().markDirty(path, changeType, previousPath)
        sessionService.addEvent(
            type = eventType,
            relatedFiles = listOfNotNull(path, previousPath),
            payload = buildMap {
                if (previousPath != null) put("previousPath", previousPath)
            },
        )

        if (triggerCheckpoint) {
            project.service<CheckpointService>().createCheckpoint(
                triggerType = eventType.name,
                activeFilePath = path,
            )
        }
    }
}
