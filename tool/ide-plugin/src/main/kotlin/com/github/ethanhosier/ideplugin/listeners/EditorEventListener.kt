package com.github.ethanhosier.ideplugin.listeners

import com.github.ethanhosier.ideplugin.services.EditBurstTracker
import com.github.ethanhosier.ideplugin.services.RefactoringBurstCoordinator
import com.github.ethanhosier.ideplugin.util.shouldCapture
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.ProjectLocator
import java.util.IdentityHashMap

class EditorEventListener : EditorFactoryListener, DocumentListener {

    // Opening the same file in a split view creates two Editors sharing one Document.
    // Document.addDocumentListener doesn't dedupe, so attaching per-editor would fire
    // documentChanged twice per keystroke. Reference-count attachments per Document and
    // only add/remove at the 0↔1 boundary. EditorFactory callbacks run on the EDT, so
    // no synchronisation is needed.
    private val attachments = IdentityHashMap<Document, Int>()

    /** Attach to a document, incrementing its ref count. Idempotent per (Document, caller) pairing. */
    fun attach(doc: Document) {
        val count = attachments[doc] ?: 0
        if (count == 0) doc.addDocumentListener(this)
        attachments[doc] = count + 1
    }

    /** Detach from a document, decrementing its ref count and removing the listener at zero. */
    fun detach(doc: Document) {
        val count = attachments[doc] ?: return
        if (count <= 1) {
            attachments.remove(doc)
            doc.removeDocumentListener(this)
        } else {
            attachments[doc] = count - 1
        }
    }

    override fun editorCreated(event: EditorFactoryEvent) = attach(event.editor.document)
    override fun editorReleased(event: EditorFactoryEvent) = detach(event.editor.document)

    override fun documentChanged(event: DocumentEvent) {
        val vFile = FileDocumentManager.getInstance().getFile(event.document) ?: return
        if (!vFile.shouldCapture()) return
        val project = ProjectLocator.getInstance().getProjectsForFile(vFile).firstOrNull() ?: return
        // Route through the coordinator first: during a refactoring it absorbs the
        // change into the REFACTORING_FINISHED snapshot instead of letting EBT emit
        // an EDIT_BURST that could bleed across the refactoring boundary.
        if (project.service<RefactoringBurstCoordinator>().onDocumentChanged(vFile, event)) return
        project.service<EditBurstTracker>().onDocumentChanged(vFile, event)
    }
}
