package com.github.ethanhosier.ideplugin.listeners

import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsAdapter
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.diagnostic.thisLogger

class TestRunListener : SMTRunnerEventsAdapter() {

    override fun onTestingStarted(testsRoot: SMTestProxy.SMRootTestProxy) {
        thisLogger().info("[STUB] Test run started: ${testsRoot.name}")
    }

    override fun onTestingFinished(testsRoot: SMTestProxy.SMRootTestProxy) {
        thisLogger().info("[STUB] Test run finished: ${testsRoot.name} passed=${testsRoot.isPassed}")
    }

    override fun onTestFailed(test: SMTestProxy) {
        thisLogger().info("[STUB] Test failed: ${test.name}")
    }
}
