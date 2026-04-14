package com.github.ethanhosier.ideplugin.toolWindow

import com.github.ethanhosier.ideplugin.services.SessionService
import com.github.ethanhosier.ideplugin.services.StorageService
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
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
import java.text.SimpleDateFormat
import java.util.Date
import javax.swing.Box
import javax.swing.JButton
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

private class TracerStatusPanel(project: Project, toolWindow: ToolWindow) : JBPanel<TracerStatusPanel>(CardLayout()) {

    private val sessionService = project.service<SessionService>()
    private val storageService = project.service<StorageService>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    // --- Active-session widgets ---
    private val sessionLabelLabel = JBLabel()
    private val sessionIdLabel = JBLabel()
    private val startTimeLabel = JBLabel()
    private val eventCountLabel = JBLabel()
    private val outputDirLabel = JBLabel()

    private val openFolderButton = JButton(AllIcons.Actions.OpenNewTab).apply {
        isBorderPainted = false
        isContentAreaFilled = false
        isOpaque = false
        toolTipText = "Open session folder"
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

    /** Just the start button, top-aligned. */
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

    /** Info fields + end button, top-aligned. */
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

            fun addFieldWithAction(heading: String, value: JBLabel, action: JButton) {
                gbc.gridy = row++; gbc.gridx = 0; gbc.gridwidth = 1; gbc.weightx = 1.0
                gbc.insets = Insets(10, 10, 0, 4)
                add(headingLabel(heading), gbc)
                gbc.gridx = 1; gbc.weightx = 0.0
                gbc.insets = Insets(10, 0, 0, 6)
                add(action, gbc)
                gbc.gridy = row++; gbc.gridx = 0; gbc.gridwidth = 2; gbc.weightx = 1.0
                gbc.insets = Insets(2, 10, 0, 10)
                add(value, gbc)
            }

            addField("Session name", sessionLabelLabel)
            addField("Session ID", sessionIdLabel)
            addField("Started", startTimeLabel)
            addField("Events", eventCountLabel)
            addFieldWithAction("Output", outputDirLabel, openFolderButton)

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
            sessionService.endSession()
            refresh()
        }
    }

    private fun refresh() {
        val active = sessionService.isSessionActive()
        sessionLabelLabel.text = sessionService.getSessionName() ?: ""
        sessionIdLabel.text = sessionService.getSessionId()?.take(8)?.let { "$it…" } ?: ""
        startTimeLabel.text = sessionService.getStartTime()?.let { dateFormat.format(Date(it)) } ?: ""
        eventCountLabel.text = sessionService.getEventCount().toString()
        outputDirLabel.text = storageService.getSessionDir()?.absolutePath ?: ""

        val cardLayout = layout as CardLayout
        cardLayout.show(this, if (active) CARD_ACTIVE else CARD_IDLE)
    }
}