package com.github.ethanhosier.ideplugin.startup

import com.github.ethanhosier.ideplugin.listeners.TestRunListener
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class MyProjectActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        thisLogger().info("RefactoringTracer: plugin starting for project=${project.name}")

        // SMTRunnerEventsListener must be subscribed dynamically per project.
        project.messageBus.connect().subscribe(SMTRunnerEventsListener.TEST_STATUS, TestRunListener())
    }
}