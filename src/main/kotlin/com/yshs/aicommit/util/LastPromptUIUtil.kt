package com.yshs.aicommit.util

import com.yshs.aicommit.service.LastPromptService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.JButton
import javax.swing.JPanel

object LastPromptUIUtil {
    @JvmStatic
    fun showLastPromptPopup(project: Project) {
        val prompt = LastPromptService.getLastPrompt(project)
        if (prompt.isNullOrEmpty()) return

        val popup = buildPopup(prompt)
        val anchor = WindowManager.getInstance().getFrame(project)
        if (anchor != null) {
            popup.showInCenterOf(anchor)
        } else {
            popup.showInFocusCenter()
        }
    }

    @JvmStatic
    fun showLastPromptPopup(project: Project, anchorComponent: Component?) {
        val prompt = LastPromptService.getLastPrompt(project)
        if (prompt.isNullOrEmpty()) return

        val popup = buildPopup(prompt)
        if (anchorComponent != null) {
            popup.showUnderneathOf(anchorComponent)
        } else {
            val frame = WindowManager.getInstance().getFrame(project)
            if (frame != null) {
                popup.showInCenterOf(frame)
            } else {
                popup.showInFocusCenter()
            }
        }
    }

    private fun buildPopup(prompt: String) =
        JBPopupFactory.getInstance()
            .createComponentPopupBuilder(buildPanel(prompt), null)
            .setTitle("最近使用的 Prompt")
            .setResizable(true)
            .setMovable(true)
            .setRequestFocus(true)
            .setFocusable(true)
            .setMinSize(Dimension(560, 220))
            .createPopup()

    private fun buildPanel(prompt: String): JPanel {
        val panel = JPanel(BorderLayout())
        val header = JPanel(FlowLayout(FlowLayout.LEFT, 8, 8))
        val title = JBLabel("最近使用的 Prompt").apply { foreground = JBColor.GRAY }
        val copyButton = JButton("复制")
        val toggleButton = JButton("展开")
        header.add(title)
        header.add(copyButton)
        header.add(toggleButton)

        val textArea = JBTextArea(buildPreview(prompt, 160)).apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
        }

        val scrollPane = JBScrollPane(textArea).apply {
            preferredSize = Dimension(520, 180)
        }
        panel.add(header, BorderLayout.NORTH)
        panel.add(scrollPane, BorderLayout.CENTER)

        var expanded = false
        copyButton.addActionListener {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(prompt), null)
        }
        toggleButton.addActionListener {
            expanded = !expanded
            toggleButton.text = if (expanded) "收起" else "展开"
            textArea.text = if (expanded) prompt else buildPreview(prompt, 160)
            textArea.caretPosition = 0
        }
        return panel
    }

    private fun buildPreview(text: String, max: Int): String =
        if (text.length <= max) text else text.substring(0, max) + " ..."
}
