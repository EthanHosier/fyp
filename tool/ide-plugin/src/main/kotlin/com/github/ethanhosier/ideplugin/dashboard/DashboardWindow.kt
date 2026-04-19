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

/**
 * Floating non-modal window hosting a JCEF browser that loads the Vite dev
 * server (default `http://localhost:5173`). Once the page finishes loading
 * we push the analysis report into the page as `window.__REPORT__` and fire
 * a `refdash:report-loaded` event; the dashboard's `useReport()` hook reads
 * from there.
 *
 * This is a dev-iteration setup — production bundling of the React app into
 * the plugin jar is deferred.
 */
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
        // The file is already valid JSON, which is a valid JavaScript
        // expression for plain data. The only sequence that would break
        // script parsing (if this were HTML-embedded) is `</`, which can't
        // occur inside pure JSON — no escaping needed for executeJavaScript.
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
