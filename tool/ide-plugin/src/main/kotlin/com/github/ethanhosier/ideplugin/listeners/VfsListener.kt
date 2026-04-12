package com.github.ethanhosier.ideplugin.listeners

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent

class VfsListener : BulkFileListener {

    override fun after(events: List<com.intellij.openapi.vfs.newvfs.events.VFileEvent>) {
        for (event in events) {
            when (event) {
                is VFileCreateEvent ->
                    thisLogger().info("[STUB] VFS file created: ${event.path}")

                is VFileDeleteEvent ->
                    thisLogger().info("[STUB] VFS file deleted: ${event.file.path}")

                is VFileMoveEvent ->
                    thisLogger().info("[STUB] VFS file moved: ${event.oldPath} -> ${event.newPath}")

                is VFilePropertyChangeEvent if event.propertyName == "name" ->
                    thisLogger().info("[STUB] VFS file renamed: ${event.oldValue} -> ${event.newValue} (in ${event.file.parent?.path})")

                is VFileCopyEvent ->
                    thisLogger().info("[STUB] VFS file copied: ${event.file.path} -> ${event.newParent.path}/${event.newChildName}")

                is VFileContentChangeEvent ->
                    thisLogger().info("[STUB] VFS content changed: ${event.file.path}")

                else -> { /* not interested */ }
            }
        }
    }
}
