package com.github.ethanhosier.ideplugin.toolWindow

import com.github.ethanhosier.ideplugin.dashboard.DashboardWindow
import com.github.ethanhosier.ideplugin.services.AnalysisClient
import com.github.ethanhosier.ideplugin.services.SessionService
import com.github.ethanhosier.ideplugin.services.StorageService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.UIUtil
import java.awt.CardLayout
import java.awt.Color
import java.awt.Desktop
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.datatransfer.StringSelection
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Date
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.Timer

class TracerToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = TracerStatusPanel(project, toolWindow)
        val content = ContentFactory.getInstance().createContent(panel, null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true
}

private class TracerStatusPanel(private val project: Project, toolWindow: ToolWindow) : JBPanel<TracerStatusPanel>(CardLayout()) {

    private val sessionService = project.service<SessionService>()
    private val storageService = project.service<StorageService>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    // --- Active-session widgets ---
    private val sessionLabelLabel = JBLabel()
    private val sessionIdLabel = JBLabel()
    private val startTimeLabel = JBLabel()
    private val eventCountLabel = JBLabel()
    private val outputDirLabel = JBLabel()

    private val openFolderButton = iconButton(AllIcons.Actions.OpenNewTab, "Open session folder")
    private val copyPathButton = iconButton(AllIcons.Actions.Copy, "Copy session folder path")

    private fun iconButton(icon: javax.swing.Icon, tooltip: String) = JButton(icon).apply {
        isBorderPainted = false
        isContentAreaFilled = false
        isOpaque = false
        toolTipText = tooltip
        preferredSize = java.awt.Dimension(22, 22)
    }

    private val startSessionButton = JButton("Start Session").apply {
        background = JBColor(Color(75, 110, 175), Color(75, 110, 175))
        foreground = Color.WHITE
        isBorderPainted = false
        isOpaque = true
        font = font.deriveFont(Font.BOLD)
    }
    private val endSessionButton = JButton("End Session")

    @Volatile private var analysing = false

    private companion object {
        const val CARD_IDLE = "idle"
        const val CARD_ACTIVE = "active"
    }

    init {
        add(buildIdlePanel(), CARD_IDLE)
        add(buildActivePanel(), CARD_ACTIVE)

        wireButtons()
        refresh()
        val timer = Timer(2000) { refresh() }.apply { isRepeats = true; start() }
        Disposer.register(toolWindow.disposable) { timer.stop() }
    }

    private fun buildIdlePanel(): JBPanel<*> =
        JBPanel<JBPanel<*>>(GridBagLayout()).apply {
            val gbc = GridBagConstraints().apply {
                gridx = 0; gridy = 0
                fill = GridBagConstraints.HORIZONTAL
                weightx = 1.0
                insets = Insets(8, 10, 4, 10)
            }
            add(startSessionButton, gbc)

            // Push everything to the top
            gbc.gridy = 1; gbc.weighty = 1.0; gbc.fill = GridBagConstraints.BOTH
            add(Box.createGlue(), gbc)
        }

    private fun buildActivePanel(): JBPanel<*> {
        val mutedColor = UIUtil.getContextHelpForeground().let {
            JBColor(
                Color(
                    minOf(255, it.red + 40),
                    minOf(255, it.green + 40),
                    minOf(255, it.blue + 40)
                ),
                Color(it.red, it.green, it.blue, 160)
            )
        }

        fun headingLabel(text: String) = JBLabel(text).apply {
            font = font.deriveFont(Font.PLAIN, font.size - 1f)
            foreground = mutedColor
        }

        return JBPanel<JBPanel<*>>(GridBagLayout()).apply {
            val gbc = GridBagConstraints().apply {
                gridx = 0; fill = GridBagConstraints.HORIZONTAL; weightx = 1.0
            }
            var row = 0

            fun addField(heading: String, value: JBLabel) {
                gbc.gridy = row++; gbc.gridwidth = 2
                gbc.insets = Insets(10, 10, 0, 10)
                add(headingLabel(heading), gbc)
                gbc.gridy = row++
                gbc.insets = Insets(2, 10, 0, 10)
                add(value, gbc)
            }

            fun addFieldWithActions(heading: String, value: JBLabel, actions: JComponent) {
                gbc.gridy = row++; gbc.gridx = 0; gbc.gridwidth = 1; gbc.weightx = 1.0
                gbc.insets = Insets(10, 10, 0, 4)
                add(headingLabel(heading), gbc)
                gbc.gridx = 1; gbc.weightx = 0.0
                gbc.insets = Insets(10, 0, 0, 6)
                add(actions, gbc)
                gbc.gridy = row++; gbc.gridx = 0; gbc.gridwidth = 2; gbc.weightx = 1.0
                gbc.insets = Insets(2, 10, 0, 10)
                add(value, gbc)
            }

            val outputActions = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                isOpaque = false
                add(openFolderButton)
                add(copyPathButton)
            }

            addField("Session name", sessionLabelLabel)
            addField("Session ID", sessionIdLabel)
            addField("Started", startTimeLabel)
            addField("Events", eventCountLabel)
            addFieldWithActions("Output", outputDirLabel, outputActions)

            // Separator
            gbc.gridy = row++; gbc.gridx = 0; gbc.gridwidth = 2; gbc.weightx = 1.0
            gbc.insets = Insets(12, 8, 0, 8)
            add(JSeparator(), gbc)

            // End button
            gbc.gridy = row++
            gbc.insets = Insets(8, 10, 4, 10)
            add(endSessionButton, gbc)

            // Push to top
            gbc.gridy = row; gbc.weighty = 1.0; gbc.fill = GridBagConstraints.BOTH
            add(Box.createGlue(), gbc)
        }
    }

    private fun wireButtons() {
        openFolderButton.addActionListener {
            storageService.getSessionDir()?.let { dir ->
                if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(dir)
            }
        }
        copyPathButton.addActionListener {
            storageService.getSessionDir()?.absolutePath?.let { path ->
                CopyPasteManager.getInstance().setContents(StringSelection(path))
            }
        }
        startSessionButton.addActionListener {
            val label = Messages.showInputDialog(
                this,
                "Session name:",
                "Start Session",
                Messages.getQuestionIcon(),
                null,
                object : InputValidator {
                    override fun checkInput(input: String?) = !input.isNullOrBlank()
                    override fun canClose(input: String?) = checkInput(input)
                },
            ) ?: return@addActionListener
            sessionService.startSession(label)
            refresh()
        }
        endSessionButton.addActionListener {
            val sessionDir = storageService.getSessionDir()?.toPath()
            if (sessionDir != null) {
                analysing = true
                setEndButtonAnalysing()
            }
            sessionService.endSession()
            refresh()
            if (sessionDir != null) {
                runAnalysisUpload(sessionDir)
            }
        }
    }

    private fun setEndButtonAnalysing() {
        endSessionButton.text = "Analysing…"
        endSessionButton.icon = AnimatedIcon.Default()
        endSessionButton.isEnabled = false
    }

    private fun resetEndButton() {
        endSessionButton.text = "End Session"
        endSessionButton.icon = null
        endSessionButton.isEnabled = true
    }

    private fun runAnalysisUpload(sessionDir: java.nio.file.Path) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Analysing session…", /* canBeCancelled = */ false) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "Uploading session to analysis server…"
                val reportPath = sessionDir.resolve("analysis-report.json")
                try {
                    val reportBytes = project.service<AnalysisClient>().upload(sessionDir)
                    Files.write(reportPath, reportBytes)
                    notifyUser(
                        NotificationType.INFORMATION,
                        "Session analysed",
                        "Report written to ${reportPath.toAbsolutePath()}",
                    )
                    openDashboard(reportPath)
                } catch (e: Exception) {
                    thisLogger().warn("RefactoringTracer: analysis upload failed", e)
                    notifyUser(
                        NotificationType.ERROR,
                        "Analysis failed",
                        e.message ?: e.toString(),
                    )
                }
            }

            override fun onFinished() {
                analysing = false
                resetEndButton()
                refresh()
            }
        })
    }

    private fun openDashboard(reportPath: java.nio.file.Path) {
        if (System.getenv("REFDASH_DISABLED") == "1") return
        if (!DashboardWindow.isSupported()) {
            thisLogger().warn("RefactoringTracer: JCEF unsupported, skipping dashboard")
            return
        }
        ApplicationManager.getApplication().invokeLater {
            try {
                DashboardWindow(project, reportPath).show()
            } catch (e: Exception) {
                thisLogger().warn("RefactoringTracer: failed to open dashboard", e)
            }
        }
    }

    private fun notifyUser(type: NotificationType, title: String, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("RefactoringTracer.Analysis")
            .createNotification(title, content, type)
            .notify(project)
    }

    private fun refresh() {
        val active = sessionService.isSessionActive() || analysing
        sessionLabelLabel.text = sessionService.getSessionName() ?: ""
        sessionIdLabel.text = sessionService.getSessionId()?.take(8)?.let { "$it…" } ?: ""
        startTimeLabel.text = sessionService.getStartTime()?.let { dateFormat.format(Date(it)) } ?: ""
        eventCountLabel.text = sessionService.getEventCount().toString()
        outputDirLabel.text = storageService.getSessionDir()?.absolutePath ?: ""

        val cardLayout = layout as CardLayout
        cardLayout.show(this, if (active) CARD_ACTIVE else CARD_IDLE)
    }
}
