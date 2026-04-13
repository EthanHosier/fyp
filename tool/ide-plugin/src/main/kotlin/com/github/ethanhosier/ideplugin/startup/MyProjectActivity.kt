package com.github.ethanhosier.ideplugin.startup

import com.github.ethanhosier.ideplugin.listeners.EditorEventListener
import com.github.ethanhosier.ideplugin.listeners.TestRunListener
import com.github.ethanhosier.ideplugin.services.SessionService
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class MyProjectActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        project.service<SessionService>().startSession()

        // EditorEventListener is wired here rather than via plugin.xml because
        // IntelliJ restores previously open editors through an internal path that
        // bypasses EditorFactory events — so a plugin.xml EditorFactoryListener
        // never sees editorCreated for those editors. MyProjectActivity runs after
        // restoration is complete, so we can attach to existing editors AND register
        // for future ones in a single step with one listener instance.
        val editorListener = EditorEventListener()
        EditorFactory.getInstance().allEditors
            .filter { it.project == project }
            .forEach { it.document.addDocumentListener(editorListener) }
        EditorFactory.getInstance().addEditorFactoryListener(editorListener, project)

        // SMTRunnerEventsListener requires per-project dynamic subscription.
        project.messageBus.connect().subscribe(SMTRunnerEventsListener.TEST_STATUS, TestRunListener(project))
    }
}
