package com.github.ethanhosier.ideplugin.listeners

import com.github.ethanhosier.ideplugin.model.EventType
import com.github.ethanhosier.ideplugin.services.SessionService
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsAdapter
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

class TestRunListener(private val project: Project) : SMTRunnerEventsAdapter() {

    override fun onTestingStarted(testsRoot: SMTestProxy.SMRootTestProxy) =
        project.service<SessionService>().addEvent(
            EventType.TEST_RUN_STARTED,
            payload = mapOf("suite" to testsRoot.name),
        )

    override fun onTestingFinished(testsRoot: SMTestProxy.SMRootTestProxy) =
        project.service<SessionService>().addEvent(
            EventType.TEST_RUN_FINISHED,
            payload = mapOf("suite" to testsRoot.name, "passed" to testsRoot.isPassed.toString()),
        )

    override fun onTestFailed(test: SMTestProxy) =
        project.service<SessionService>().addEvent(
            EventType.TEST_RUN_FINISHED,
            payload = mapOf("test" to test.name, "passed" to "false"),
        )
}
