package com.github.ethanhosier.ideplugin.listeners

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener

class FileEditorListener : FileEditorManagerListener {

    override fun fileOpened(source: com.intellij.openapi.fileEditor.FileEditorManager, file: com.intellij.openapi.vfs.VirtualFile) {
        thisLogger().info("[STUB] File opened: ${file.path}")
    }

    override fun fileClosed(source: com.intellij.openapi.fileEditor.FileEditorManager, file: com.intellij.openapi.vfs.VirtualFile) {
        thisLogger().info("[STUB] File closed: ${file.path}")
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
        val old = event.oldFile?.path
        val new = event.newFile?.path
        thisLogger().info("[STUB] File focus changed: $old -> $new")
    }
}
