package com.github.ethanhosier.ideplugin.toolWindow

import com.github.ethanhosier.ideplugin.model.EventType
import com.github.ethanhosier.ideplugin.services.SessionService
import com.github.ethanhosier.ideplugin.services.StorageService
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Desktop
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.text.SimpleDateFormat
import java.util.Date
import javax.swing.JButton
import javax.swing.JOptionPane
import javax.swing.JSeparator
import javax.swing.Timer

class TracerToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = TracerStatusPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true
}

private class TracerStatusPanel(project: Project) : JBPanel<TracerStatusPanel>(GridBagLayout()) {

    private val sessionService = project.service<SessionService>()
    private val storageService = project.service<StorageService>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    private val sessionIdLabel = JBLabel()
    private val startTimeLabel = JBLabel()
    private val eventCountLabel = JBLabel()
    private val outputDirLabel = JBLabel()
    private val activeTaskLabel = JBLabel()
    private val startTaskButton = JButton("Start Task")
    private val endTaskButton = JButton("End Task")

    // Icon button to open the session folder — sits in the heading row, right-aligned
    private val openFolderButton = JButton(AllIcons.Actions.OpenNewTab).apply {
        isBorderPainted = false
        isContentAreaFilled = false
        isOpaque = false
        toolTipText = "Open session folder"
        preferredSize = java.awt.Dimension(22, 22)
    }

    init {
        buildLayout()
        wireButtons()
        refresh()
        Timer(2000) { refresh() }.also { it.isRepeats = true; it.start() }
    }

    private fun buildLayout() {
        // Two columns: col 0 = label/value (stretches), col 1 = optional icon (fixed)
        val gbc = GridBagConstraints().apply { fill = GridBagConstraints.HORIZONTAL }

        val mutedColor = UIUtil.getContextHelpForeground().let {
            JBColor(
                Color(it.red + 40, it.green + 40, it.blue + 40).let { c ->
                    Color(minOf(255, c.red), minOf(255, c.green), minOf(255, c.blue))
                },
                Color(it.red, it.green, it.blue, 160)
            )
        }

        fun headingLabel(text: String) = JBLabel(text).apply {
            font = font.deriveFont(Font.PLAIN, font.size - 1f)
            foreground = mutedColor
        }

        /** Plain field: heading spans both columns, value spans both columns. */
        fun addField(heading: String, value: JBLabel, row: Int) {
            gbc.gridy = row * 2; gbc.gridx = 0; gbc.gridwidth = 2; gbc.weightx = 1.0
            gbc.insets = Insets(10, 10, 0, 10)
            add(headingLabel(heading), gbc)

            gbc.gridy = row * 2 + 1; gbc.gridwidth = 2
            gbc.insets = Insets(2, 10, 0, 10)
            add(value, gbc)
        }

        /** Field with an icon action in the heading row, right-aligned. */
        fun addFieldWithAction(heading: String, value: JBLabel, action: JButton, row: Int) {
            // Heading in col 0
            gbc.gridy = row * 2; gbc.gridx = 0; gbc.gridwidth = 1; gbc.weightx = 1.0
            gbc.insets = Insets(10, 10, 0, 4)
            add(headingLabel(heading), gbc)
            // Icon in col 1
            gbc.gridx = 1; gbc.weightx = 0.0
            gbc.insets = Insets(10, 0, 0, 6)
            add(action, gbc)
            // Value spans both columns
            gbc.gridy = row * 2 + 1; gbc.gridx = 0; gbc.gridwidth = 2; gbc.weightx = 1.0
            gbc.insets = Insets(2, 10, 0, 10)
            add(value, gbc)
        }

        addField("Session ID", sessionIdLabel, 0)
        addField("Started", startTimeLabel, 1)
        addField("Events", eventCountLabel, 2)
        addFieldWithAction("Output", outputDirLabel, openFolderButton, 3)

        // Separator
        gbc.gridy = 8; gbc.gridx = 0; gbc.gridwidth = 2; gbc.weightx = 1.0
        gbc.insets = Insets(12, 8, 4, 8)
        add(JSeparator(), gbc)

        addField("Active task", activeTaskLabel, 5)  // rows 10 & 11

        // Start Task button (primary blue)
        startTaskButton.apply {
            background = JBColor(Color(75, 110, 175), Color(75, 110, 175))
            foreground = Color.WHITE
            isBorderPainted = false
            isOpaque = true
            font = font.deriveFont(Font.BOLD)
        }

        gbc.gridy = 12; gbc.gridx = 0; gbc.gridwidth = 2; gbc.weightx = 1.0
        gbc.insets = Insets(12, 10, 4, 10)
        add(startTaskButton, gbc)

        gbc.gridy = 13
        gbc.insets = Insets(0, 10, 10, 10)
        add(endTaskButton, gbc)

        // Push content to top
        gbc.gridy = 14; gbc.weighty = 1.0; gbc.fill = GridBagConstraints.BOTH
        gbc.insets = Insets(0, 0, 0, 0)
        add(JBPanel<JBPanel<*>>(), gbc)
    }

    private fun wireButtons() {
        openFolderButton.addActionListener {
            storageService.getSessionDir()?.let { dir ->
                if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(dir)
            }
        }

        startTaskButton.addActionListener {
            val label = JOptionPane.showInputDialog(
                this, "Task label:", "Start Task", JOptionPane.QUESTION_MESSAGE
            ) ?: return@addActionListener
            sessionService.addEvent(
                type = EventType.TASK_STARTED,
                payload = mapOf("label" to label),
            )
            refresh()
        }

        endTaskButton.addActionListener {
            val label = sessionService.getActiveTaskLabel() ?: return@addActionListener
            sessionService.addEvent(
                type = EventType.TASK_ENDED,
                payload = mapOf("label" to label),
            )
            refresh()
        }
    }

    private fun refresh() {
        val taskLabel = sessionService.getActiveTaskLabel()
        sessionIdLabel.text = sessionService.getSessionId()?.take(8)?.let { "$it…" } ?: "—"
        startTimeLabel.text = sessionService.getStartTime()?.let { dateFormat.format(Date(it)) } ?: "—"
        eventCountLabel.text = sessionService.getEventCount().toString()
        outputDirLabel.text = storageService.getSessionDir()?.absolutePath ?: "—"
        activeTaskLabel.text = taskLabel ?: "none"
        startTaskButton.isEnabled = taskLabel == null
        endTaskButton.isEnabled = taskLabel != null
    }
}
