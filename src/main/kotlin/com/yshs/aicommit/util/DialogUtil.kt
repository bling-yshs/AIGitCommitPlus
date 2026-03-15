package com.yshs.aicommit.util

import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextPane

object DialogUtil {
    const val CONFIGURATION_ERROR_TITLE = "Configuration Error"
    const val GENERATE_COMMIT_MESSAGE_ERROR_TITLE = "Generate Commit Message Error"

    @JvmStatic
    fun showErrorDialog(parentComponent: Component?, errorDetails: String?, title: String) {
        ErrorDialog(parentComponent, errorDetails.orEmpty(), title).show()
    }

    private class ErrorDialog(
        parent: Component?,
        private val errorDetails: String,
        private val dialogTitle: String,
    ) : DialogWrapper(true) {

        init {
            title = dialogTitle
            init()
        }

        override fun createCenterPanel(): JComponent {
            val tabbedPane = JBTabbedPane()
            tabbedPane.addTab("Overview", AllIcons.Actions.Help, buildOverviewPanel())
            tabbedPane.addTab("Error Details", AllIcons.Debugger.Console, buildDetailsPanel())
            return JPanel(BorderLayout()).apply {
                preferredSize = JBUI.size(520, 320)
                add(tabbedPane, BorderLayout.CENTER)
            }
        }

        override fun createActions(): Array<Action> = arrayOf(okAction)

        override fun createDefaultActions() {
            super.createDefaultActions()
            okAction.putValue(Action.NAME, "Close")
        }

        private fun buildOverviewPanel(): JPanel =
            JPanel(BorderLayout(0, 10)).apply {
                border = JBUI.Borders.empty(12)
                add(
                    JPanel(BorderLayout(10, 0)).apply {
                        add(JBLabel(AllIcons.General.Error), BorderLayout.WEST)
                        add(JBLabel(dialogTitle), BorderLayout.CENTER)
                    },
                    BorderLayout.NORTH,
                )
                add(
                    JTextPane().apply {
                        contentType = "text/html"
                        text =
                            "<html><body>" +
                                "Possible causes:<br><br>" +
                                "• API Key or URL configuration error<br>" +
                                "• Network connection issues (check your proxy)<br>" +
                                "• The selected model is temporarily unavailable<br><br>" +
                                "Please review the configuration and retry." +
                                "</body></html>"
                        isEditable = false
                        border = null
                        background = null
                    },
                    BorderLayout.CENTER,
                )
            }

        private fun buildDetailsPanel(): JPanel =
            JPanel(BorderLayout(0, 8)).apply {
                border = JBUI.Borders.empty(12)
                add(JBLabel("Provider response:"), BorderLayout.NORTH)
                add(
                    JBScrollPane(
                        JBTextArea(errorDetails.ifBlank { "No error details available" }).apply {
                            isEditable = false
                            lineWrap = true
                            wrapStyleWord = true
                            caretPosition = 0
                        },
                    ),
                    BorderLayout.CENTER,
                )
            }
    }
}
