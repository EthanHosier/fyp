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
        val editorListener = EditorEventListener(project)
        EditorFactory.getInstance().eventMulticaster
            .addDocumentListener(editorListener, project)

        val connection = project.messageBus.connect(project)
        connection.subscribe(SMTRunnerEventsListener.TEST_STATUS, TestRunListener(project))

        connection.subscribe(TemplateManager.TEMPLATE_STARTED_TOPIC, RefactoringTemplateListener(project))
    }
}
