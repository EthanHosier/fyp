package com.github.ethanhosier.ideplugin.dashboard

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.BorderLayout
import java.awt.Dimension
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel

class DashboardWindow(
    project: Project,
    private val reportFile: Path,
) : DialogWrapper(project, /* canBeParent = */ false) {

    private val browser: JBCefBrowser = JBCefBrowser()

    init {
        title = "Refactoring Dashboard"
        isModal = false
        setResizable(true)
        setSize(1280, 860)

        Disposer.register(disposable, browser)

        browser.jbCefClient.addLoadHandler(
            object : CefLoadHandlerAdapter() {
                override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                    if (!frame.isMain) return
                    injectReport(browser, frame.url)
                }
            },
            browser.cefBrowser,
        )

        init()

        val url = System.getenv("REFDASH_DEV_URL")?.takeIf { it.isNotBlank() }
            ?: DEFAULT_DEV_URL
        browser.loadURL(url)
    }

    private fun injectReport(cefBrowser: CefBrowser, frameUrl: String) {
        if (!Files.isRegularFile(reportFile)) return
        val json = Files.readString(reportFile)
        val script = buildString {
            append("window.__REPORT__ = ")
            append(json)
            append(";")
            append("window.dispatchEvent(new CustomEvent('refdash:report-loaded'));")
        }
        cefBrowser.executeJavaScript(script, frameUrl, 0)
    }

    override fun createCenterPanel(): JComponent = JPanel(BorderLayout()).apply {
        preferredSize = Dimension(1280, 860)
        add(browser.component, BorderLayout.CENTER)
    }

    // Viewer window — no OK/Cancel row.
    override fun createActions(): Array<Action> = emptyArray()

    companion object {
        const val DEFAULT_DEV_URL = "http://localhost:5173"
        fun isSupported(): Boolean = JBCefApp.isSupported()
    }
}
