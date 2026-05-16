package com.github.ethanhosier.ideplugin.startup

import com.github.ethanhosier.ideplugin.listeners.EditorEventListener
import com.github.ethanhosier.ideplugin.listeners.RefactoringTemplateListener
import com.github.ethanhosier.ideplugin.listeners.TestRunListener
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class TracerStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        // Subscribe to every Document edit in the IDE via the EditorFactory
        // multicaster. The multicaster fires `documentChanged` for any Document
        // change regardless of whether (or how many) editors are open on the
        // document — so project-wide Find/Replace, which mutates documents
        // without opening editors for the un-visible files, is captured for
        // every affected file rather than only the document with editor focus.
        //
        // The 2-arg form auto-removes the listener when `project` is disposed,
        // so no manual Disposer cleanup is needed.
        val editorListener = EditorEventListener(project)
        EditorFactory.getInstance().eventMulticaster
            .addDocumentListener(editorListener, project)

        // SMTRunnerEventsListener requires per-project dynamic subscription. Pass project
        // as the parent Disposable so the connection (and TestRunListener's Project ref)
        // are released on project close.
        val connection = project.messageBus.connect(project)
        connection.subscribe(SMTRunnerEventsListener.TEST_STATUS, TestRunListener(project))

        // Template lifecycle: terminator for in-place refactorings. RefactoringEventListener
        // does not fire refactoringDone / undoRefactoring when the user dismisses an
        // in-place refactoring (e.g. Extract Method preview with Esc), so we rely on the
        // template's finish callback to close the refactoring window cleanly.
        connection.subscribe(TemplateManager.TEMPLATE_STARTED_TOPIC, RefactoringTemplateListener(project))
    }
}
