package com.github.ethanhosier.ideplugin.listeners

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener

class EditorEventListener : EditorFactoryListener, DocumentListener {

    override fun editorCreated(event: EditorFactoryEvent) {
        event.editor.document.addDocumentListener(this)
        thisLogger().info("[STUB] Editor created: ${event.editor.virtualFile?.path}")
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        event.editor.document.removeDocumentListener(this)
        thisLogger().info("[STUB] Editor released: ${event.editor.virtualFile?.path}")
    }

    override fun documentChanged(event: DocumentEvent) {
        val path = event.document.toString()
        thisLogger().info(
            "[STUB] Document changed — charsInserted=${event.newLength - event.oldLength} " +
                "offset=${event.offset} doc=$path"
        )
    }
}
