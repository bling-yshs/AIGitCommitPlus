package com.yshs.aicommit.config

import com.yshs.aicommit.constant.Constants
import com.yshs.aicommit.service.AIService
import com.yshs.aicommit.service.CommitMessageService
import com.yshs.aicommit.util.DialogUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPasswordField
import com.intellij.util.ui.JBUI
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.tuple.Pair
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.net.URI
import javax.swing.Action
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

class ModuleConfigDialog(
    parent: Component,
    private val client: String,
    private val module: String?,
) : DialogWrapper(parent, true) {
    private lateinit var urlField: JTextField
    private lateinit var apiKeyField: JBPasswordField
    private lateinit var helpLabel: JLabel
    private var isPasswordVisible = false

    init {
        title = "$client Settings"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout()).apply {
            preferredSize = Dimension(700, 240)
        }
        val gbc = GridBagConstraints().apply {
            insets = JBUI.insets(5, 10, 5, 10)
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
        }

        urlField = JTextField()
        apiKeyField = JBPasswordField()
        helpLabel = JLabel().apply { foreground = JBColor.GRAY }

        gbc.gridx = 0
        gbc.gridy = 0
        gbc.weightx = 0.0
        panel.add(JLabel("URL:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        panel.add(urlField, gbc)

        gbc.gridx = 0
        gbc.gridy = 1
        gbc.weightx = 0.0
        panel.add(JLabel("API Key:"), gbc)

        val apiKeyPanel = JPanel(BorderLayout()).apply {
            add(apiKeyField, BorderLayout.CENTER)
            add(
                JButton().apply {
                    icon = AllIcons.Actions.Show
                    preferredSize = Dimension(28, 28)
                    border = BorderFactory.createEmptyBorder()
                    isFocusable = false
                    addActionListener { togglePasswordVisibility(apiKeyField, this) }
                },
                BorderLayout.EAST,
            )
        }

        gbc.gridx = 1
        gbc.weightx = 1.0
        panel.add(apiKeyPanel, gbc)

        gbc.gridx = 1
        gbc.gridy = 2
        gbc.gridwidth = 2
        gbc.insets = Insets(0, 10, 5, 10)
        updateHelpText()
        panel.add(helpLabel, gbc)
        return panel
    }

    private fun updateHelpText() {
        helpLabel.text = Constants.getHelpText(client)
        val url = Constants.CLIENT_HELP_URLS[client]
        if (url != null) {
            helpLabel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            helpLabel.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    try {
                        java.awt.Desktop.getDesktop().browse(URI(url))
                    } catch (_: Exception) {
                    }
                }
            })
        }
    }

    override fun createActions(): Array<Action> =
        arrayOf(
            okAction,
            cancelAction,
            object : DialogWrapperAction("Reset") {
                override fun doAction(e: ActionEvent) = resetFields()
            },
            object : DialogWrapperAction("Check Config") {
                override fun doAction(e: ActionEvent) = checkConfig()
            },
        )

    private fun checkConfig() {
        ProgressManager.getInstance().run(object : Task.Modal(null, "Validating Configuration", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.isIndeterminate = true
                    indicator.text = "Validating configuration..."
                    val aiService: AIService = CommitMessageService.getAIService(client)
                    val checkConfig = mapOf(
                        "url" to urlField.text,
                        "module" to (module?.trim().orEmpty()),
                        "apiKey" to String(apiKeyField.password),
                    )
                    val validateResPair: Pair<Boolean, String> = aiService.validateConfig(checkConfig)
                    ApplicationManager.getApplication().invokeLater {
                        if (validateResPair.left) {
                            Messages.showInfoMessage("Configuration validation successful! 👏", "Success")
                        } else {
                            DialogUtil.showErrorDialog(contentPanel, validateResPair.right, DialogUtil.CONFIGURATION_ERROR_TITLE)
                        }
                    }
                } catch (e: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog("Validation error occurred: ${e.message}", "Error")
                    }
                }
            }
        })
    }

    override fun init() {
        super.init()
        val moduleConfig = ApiKeySettings.getInstance().getModuleConfig(client)
        urlField.text = moduleConfig.url
        apiKeyField.text = moduleConfig.apiKey
    }

    override fun doOKAction() {
        val moduleConfig = ApiKeySettings.getInstance().getModuleConfig(client)
        val url = urlField.text.trim()
        val apiKey = String(apiKeyField.password)
        if (StringUtils.isEmpty(url)) {
            Messages.showErrorDialog("URL cannot be empty", "Error")
            return
        }
        if (StringUtils.isEmpty(apiKey)) {
            Messages.showErrorDialog("API Key cannot be empty", "Error")
            return
        }
        moduleConfig.url = url
        moduleConfig.apiKey = apiKey
        super.doOKAction()
    }

    private fun resetFields() {
        Constants.moduleConfigs[client]?.let {
            urlField.text = it.url
            apiKeyField.text = it.apiKey
        }
    }

    private fun togglePasswordVisibility(passwordField: JBPasswordField, toggleButton: JButton) {
        isPasswordVisible = !isPasswordVisible
        if (isPasswordVisible) {
            passwordField.echoChar = 0.toChar()
            toggleButton.icon = AllIcons.Actions.ToggleVisibility
        } else {
            passwordField.echoChar = '•'
            toggleButton.icon = AllIcons.Actions.Show
        }
        passwordField.revalidate()
        passwordField.repaint()
    }
}
