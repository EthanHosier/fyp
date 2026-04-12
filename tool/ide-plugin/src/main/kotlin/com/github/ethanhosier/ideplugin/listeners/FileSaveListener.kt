package com.github.ethanhosier.ideplugin.listeners

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManagerListener

class FileSaveListener : FileDocumentManagerListener {

    override fun beforeDocumentSaving(document: Document) {
        thisLogger().info("[STUB] Document saving: $document")
    }

    override fun beforeAllDocumentsSaving() {
        thisLogger().info("[STUB] All documents saving (e.g. before build)")
    }
}
