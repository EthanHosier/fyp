package com.github.ethanhosier.ideplugin.startup

import com.github.ethanhosier.ideplugin.listeners.EditorEventListener
import com.github.ethanhosier.ideplugin.listeners.TestRunListener
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer

class TracerStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        // EditorEventListener is wired here rather than via plugin.xml because
        // IntelliJ restores previously open editors through an internal path that
        // bypasses EditorFactory events — so a plugin.xml EditorFactoryListener
        // never sees editorCreated for those editors. This activity runs after
        // restoration is complete, so we can attach to existing editors AND register
        // for future ones in a single step with one listener instance.
        val editorListener = EditorEventListener()
        EditorFactory.getInstance().allEditors
            .filter { it.project == project }
            .forEach { editorListener.attach(it.document) }
        EditorFactory.getInstance().addEditorFactoryListener(editorListener, project)

        // Remove the listener from any editors we attached to directly on project close.
        // addEditorFactoryListener scopes itself to `project`, but the pre-existing editors
        // we looped over above are owned by IntelliJ's document cache and can outlive the
        // project — without this, they'd keep firing documentChanged into a stale listener.
        //
        // Platform inspection flags `Disposer.register(project, ...)` because if the plugin
        // is dynamically unloaded mid-session, the lambda stays registered on the project's
        // disposer tree holding a reference into unloaded plugin classes (classloader leak).
        // The strictly correct fix is to parent under a @Service(PROJECT) that implements
        // Disposable. For this thesis tool (never dynamically reloaded) project scope is fine.
        Disposer.register(project) {
            EditorFactory.getInstance().allEditors
                .filter { it.project == project }
                .forEach { editorListener.detach(it.document) }
        }

        // SMTRunnerEventsListener requires per-project dynamic subscription. Pass project
        // as the parent Disposable so the connection (and TestRunListener's Project ref)
        // are released on project close.
        project.messageBus.connect(project)
            .subscribe(SMTRunnerEventsListener.TEST_STATUS, TestRunListener(project))
    }
}
