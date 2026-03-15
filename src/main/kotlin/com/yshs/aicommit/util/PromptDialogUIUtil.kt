package com.yshs.aicommit.util

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.JTextField

object PromptDialogUIUtil {
    class PromptDialogUI {
        var panel: JPanel? = null
        var descriptionField: JTextField? = null
        var contentArea: JTextArea? = null
    }

    @JvmStatic
    fun showPromptDialog(isAdd: Boolean, description: String?, content: String?): PromptDialogUI {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = JBUI.insets(5)
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
        }

        val descriptionField = JTextField(if (isAdd) "" else description, 30)
        val contentArea = JTextArea(if (isAdd) "" else content, 15, 70).apply {
            lineWrap = true
            wrapStyleWord = true
        }

        gbc.gridx = 0
        gbc.gridy = 0
        gbc.weightx = 0.0
        panel.add(JLabel("Description:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        panel.add(descriptionField, gbc)

        gbc.gridx = 0
        gbc.gridy = 1
        gbc.weightx = 0.0
        gbc.weighty = 0.0
        gbc.anchor = GridBagConstraints.NORTHWEST
        panel.add(JLabel("Prompt:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        gbc.weighty = 1.0
        gbc.fill = GridBagConstraints.BOTH
        panel.add(JBScrollPane(contentArea).apply { preferredSize = Dimension(800, 600) }, gbc)

        gbc.gridx = 1
        gbc.gridy = 2
        gbc.weightx = 1.0
        gbc.weighty = 0.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        panel.add(
            JLabel(
                "<html>Supported placeholders:<br>" +
                    "• {diff} - The code changes in diff format.<br>" +
                    "• {language} - The language of the generated commit message.<br>" +
                    "Note: If you want to output the branch name in the final result, just write your requirements in the prompt.</html>",
            ).apply {
                foreground = JBColor.GRAY
                font = font.deriveFont(Font.ITALIC)
            },
            gbc,
        )

        return PromptDialogUI().also {
            it.panel = panel
            it.descriptionField = descriptionField
            it.contentArea = contentArea
        }
    }
}
