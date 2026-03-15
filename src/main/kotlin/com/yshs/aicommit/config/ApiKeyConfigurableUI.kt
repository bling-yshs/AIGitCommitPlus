package com.yshs.aicommit.config

import com.yshs.aicommit.constant.Constants
import com.yshs.aicommit.service.AIService
import com.yshs.aicommit.service.CommitMessageService
import com.yshs.aicommit.service.LastPromptService
import com.yshs.aicommit.service.ModelDiscoveryService
import com.yshs.aicommit.util.DialogUtil
import com.yshs.aicommit.util.PromptDialogUIUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Desktop
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.net.URI
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.Timer
import javax.swing.UIManager
import javax.swing.border.Border
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.DefaultTableModel

class ApiKeyConfigurableUI {
    lateinit var mainPanel: JPanel
        private set
    private lateinit var tabbedPane: JBTabbedPane
    private val settings: ApiKeySettings = ApiKeySettings.getInstance()
    private val modelDiscoveryService = ModelDiscoveryService()

    lateinit var clientComboBox: ComboBox<String>
        private set
    lateinit var moduleComboBox: ComboBox<String>
        private set
    lateinit var languageComboBox: ComboBox<String>
        private set
    private lateinit var apiUrlField: JTextField
    private lateinit var apiUrlPreviewLabel: JBLabel
    private lateinit var apiKeyField: JBPasswordField
    private lateinit var checkConfigButton: JButton
    private lateinit var refreshModelsButton: JButton
    private lateinit var addModelButton: JButton
    private lateinit var clientPanel: JPanel
    private lateinit var modelRowsPanel: JPanel
    private var apiKeyVisible = false

    lateinit var promptTypeComboBox: ComboBox<String>
        private set
    lateinit var customPromptsTable: JBTable
        private set
    lateinit var customPromptsTableModel: DefaultTableModel
        private set
    private lateinit var customPromptPanel: JPanel
    private lateinit var projectPromptPanel: JPanel

    lateinit var enableFileExclusionCheckBox: JBCheckBox
        private set
    lateinit var excludePatternsTextArea: JBTextArea
        private set
    private lateinit var resetToDefaultButton: JButton
    private lateinit var fileExclusionPanel: JPanel

    private lateinit var recentPromptTextArea: JBTextArea
    private lateinit var copyPromptButton: JButton
    private lateinit var refreshPromptButton: JButton
    private lateinit var promptStatusLabel: JBLabel

    var SELECTED_ROW: Int = 0
        private set
    private var activeClient: String = Constants.OpenAI

    init {
        initComponents()
        layoutComponents()
        setupListeners()
    }

    private fun initComponents() {
        clientPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        clientComboBox = ComboBox(Constants.LLM_CLIENTS)
        clientComboBox.selectedItem = settings.selectedClient
        if (clientComboBox.selectedItem == null) {
            clientComboBox.selectedItem = Constants.OpenAI
        }
        activeClient = clientComboBox.selectedItem as? String ?: Constants.OpenAI
        clientPanel.add(clientComboBox)

        moduleComboBox = ComboBox()
        moduleComboBox.isEditable = false
        apiUrlField = JTextField()
        apiUrlPreviewLabel = JBLabel().apply {
            foreground = JBColor.GRAY
            font = Font(Font.MONOSPACED, Font.PLAIN, 11)
        }
        apiKeyField = JBPasswordField()
        checkConfigButton = JButton("Check").apply {
            toolTipText = "Validate the current URL, API key and model"
        }
        refreshModelsButton = JButton(AllIcons.Actions.Refresh).apply {
            toolTipText = "Fetch models from the provider, then choose which ones to add"
        }
        addModelButton = JButton("Add Model").apply {
            toolTipText = "Add a custom model name for the current protocol"
        }
        modelRowsPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }
        loadClientConfig(activeClient)
        reloadModelComboBox(activeClient)

        languageComboBox = ComboBox(Constants.languages).apply {
            isEditable = true
            selectedItem = settings.commitLanguage
        }

        promptTypeComboBox = ComboBox(Constants.getAllPromptTypes())
        customPromptsTableModel = DefaultTableModel(arrayOf("Description", "Prompt"), 0)
        customPromptsTable = JBTable(customPromptsTableModel)
        customPromptsTable.columnModel.getColumn(0).preferredWidth = 150
        customPromptsTable.columnModel.getColumn(0).maxWidth = 200
        customPromptsTable.columnModel.getColumn(1).preferredWidth = 400

        customPromptPanel = createCustomPromptPanel()
        projectPromptPanel = createProjectPromptPanel()

        enableFileExclusionCheckBox = JBCheckBox("Enable File Filtering")
        excludePatternsTextArea = JBTextArea(8, 50).apply {
            lineWrap = true
            wrapStyleWord = true
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        }
        resetToDefaultButton = JButton("Reset to Default").apply {
            toolTipText = "Reset exclusion patterns to default values"
        }
        fileExclusionPanel = createFileExclusionPanel()

        recentPromptTextArea = JBTextArea(15, 60).apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            background = UIManager.getColor("Panel.background")
        }
        copyPromptButton = JButton("Copy Prompt").apply {
            toolTipText = "Copy the recent prompt to clipboard"
        }
        refreshPromptButton = JButton("Refresh").apply {
            toolTipText = "Refresh the recent prompt display"
        }
        promptStatusLabel = JBLabel("No recent prompt available").apply {
            foreground = JBColor.GRAY
        }
    }

    private fun layoutComponents() {
        mainPanel = JPanel(BorderLayout())
        tabbedPane = JBTabbedPane()
        tabbedPane.addTab("Basic Settings", createBasicSettingsPanel())
        tabbedPane.addTab("Prompt Settings", createPromptSettingsPanel())
        tabbedPane.addTab("File Filtering", createFileFilterPanel())
        tabbedPane.addTab("Recent Prompt", createRecentPromptPanel())
        mainPanel.add(tabbedPane, BorderLayout.CENTER)
    }

    private fun createCustomPromptPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        val labelPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(0, 0, 5, 0)
        }
        labelPanel.add(
            JBLabel("Select a prompt from the table below to use it as your commit message template.").apply {
                font = font.deriveFont(Font.PLAIN, 12f)
                foreground = JBColor.GRAY
            },
            BorderLayout.WEST,
        )
        labelPanel.add(
            JLabel("<html><a href='https://github.com/HMYDK/AIGitCommit/discussions/23'>More Prompts ↗</a></html>").apply {
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) {
                        try {
                            Desktop.getDesktop().browse(URI("https://github.com/HMYDK/AIGitCommit/discussions/23"))
                        } catch (_: Exception) {
                        }
                    }
                })
            },
            BorderLayout.EAST,
        )
        panel.add(labelPanel, BorderLayout.NORTH)

        val customPromptsPanel = ToolbarDecorator.createDecorator(customPromptsTable)
            .setAddAction { addCustomPrompt() }
            .setRemoveAction { removeCustomPrompt() }
            .setEditAction { editCustomPrompt(customPromptsTable.selectedRow) }
            .createPanel()
        panel.add(customPromptsPanel, BorderLayout.CENTER)

        customPromptsTable.selectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                SELECTED_ROW = maxOf(customPromptsTable.selectedRow, 0)
            }
        }
        return panel
    }

    private fun createProjectPromptPanel(): JPanel {
        val panel = JPanel(BorderLayout()).apply {
            preferredSize = Dimension(-1, 200)
        }
        val labelPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(0, 0, 5, 0)
        }
        labelPanel.add(
            JLabel("Using project-specific prompt from '${Constants.PROJECT_PROMPT_FILE_NAME}' in the project root.").apply {
                font = font.deriveFont(Font.PLAIN, 12f)
                foreground = JBColor.GRAY
            },
            BorderLayout.CENTER,
        )
        panel.add(labelPanel, BorderLayout.NORTH)
        panel.add(JPanel().apply { background = UIManager.getColor("Panel.background") }, BorderLayout.CENTER)
        return panel
    }

    private fun addCustomPrompt() {
        val promptDialogUI = PromptDialogUIUtil.showPromptDialog(true, null, null)
        javax.swing.SwingUtilities.invokeLater {
            UIManager.put("OptionPane.okButtonText", "OK")
            UIManager.put("OptionPane.cancelButtonText", "Cancel")
            val optionPane = JOptionPane(promptDialogUI.panel, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION)
            val dialog: JDialog = optionPane.createDialog(mainPanel, "Add Prompt")
            dialog.isVisible = true
            val result = optionPane.value as Int
            if (result == JOptionPane.OK_OPTION) {
                val description = promptDialogUI.descriptionField!!.text.trim()
                val content = promptDialogUI.contentArea!!.text.trim()
                if (description.isNotEmpty() && content.isNotEmpty()) {
                    customPromptsTableModel.addRow(arrayOf(description, content))
                }
            }
        }
    }

    private fun removeCustomPrompt() {
        ApplicationManager.getApplication().invokeLater {
            val selectedRow = customPromptsTable.selectedRow
            if (selectedRow != -1) {
                val result = Messages.showYesNoDialog(
                    "Are you sure you want to delete this custom prompt?",
                    "Confirm Deletion",
                    Messages.getQuestionIcon(),
                )
                if (result == Messages.YES) {
                    customPromptsTableModel.removeRow(selectedRow)
                }
            }
        }
    }

    private fun editCustomPrompt(row: Int) {
        javax.swing.SwingUtilities.invokeLater {
            val description = customPromptsTableModel.getValueAt(row, 0) as String
            val content = customPromptsTableModel.getValueAt(row, 1) as String
            ApplicationManager.getApplication().invokeAndWait({
                val promptDialogUI = PromptDialogUIUtil.showPromptDialog(false, description, content)
                UIManager.put("OptionPane.okButtonText", "OK")
                UIManager.put("OptionPane.cancelButtonText", "Cancel")
                val result = JOptionPane.showConfirmDialog(
                    mainPanel,
                    promptDialogUI.panel,
                    "Update Your Prompt",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE,
                )
                if (result == JOptionPane.OK_OPTION) {
                    val newDescription = promptDialogUI.descriptionField!!.text.trim()
                    val newContent = promptDialogUI.contentArea!!.text.trim()
                    if (newDescription.isNotEmpty() && newContent.isNotEmpty()) {
                        customPromptsTableModel.setValueAt(newDescription, row, 0)
                        customPromptsTableModel.setValueAt(newContent, row, 1)
                    }
                }
            }, ModalityState.defaultModalityState())
        }
    }

    private fun setupListeners() {
        clientComboBox.addActionListener {
            persistCurrentProviderConfig(activeClient)
            val selectedClient = clientComboBox.selectedItem as String
            activeClient = selectedClient
            loadClientConfig(selectedClient)
            reloadModelComboBox(selectedClient)
            updateApiUrlPreview()
        }
        apiUrlField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = updateApiUrlPreview()
            override fun removeUpdate(e: DocumentEvent) = updateApiUrlPreview()
            override fun changedUpdate(e: DocumentEvent) = updateApiUrlPreview()
        })
        checkConfigButton.addActionListener { checkCurrentConfig() }
        refreshModelsButton.addActionListener { refreshModels() }
        addModelButton.addActionListener { addCustomModel() }
        moduleComboBox.addActionListener { reloadModelList(activeClient) }
        copyPromptButton.addActionListener { copyRecentPromptToClipboard() }
        refreshPromptButton.addActionListener { refreshRecentPrompt() }
        tabbedPane.addChangeListener {
            if (tabbedPane.selectedIndex == 3) {
                refreshRecentPrompt()
            }
        }
    }

    private fun createBasicSettingsPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = JBUI.insets(10)
            fill = GridBagConstraints.HORIZONTAL
        }

        val topPanel = JPanel(BorderLayout())
        topPanel.add(
            JLabel("<html><a href='https://github.com/HMYDK/AIGitCommit/issues'>Report Bug ↗</a></html>").apply {
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) {
                        try {
                            Desktop.getDesktop().browse(URI("https://github.com/HMYDK/AIGitCommit/issues"))
                        } catch (_: Exception) {
                        }
                    }
                })
            },
            BorderLayout.EAST,
        )
        topPanel.add(
            JLabel("<html><a href='#'>Manage HTTP proxy settings (requires restart)</a></html>").apply {
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                foreground = JBColor.GRAY
                addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) {
                        ShowSettingsUtil.getInstance().showSettingsDialog(null, "HTTP Proxy")
                    }
                })
            },
            BorderLayout.WEST,
        )

        gbc.gridwidth = 2
        addComponent(panel, topPanel, gbc, 0, 0, 1.0)
        gbc.gridwidth = 1

        addComponent(panel, JBLabel("Provider Type:"), gbc, 0, 1, 0.0)
        addComponent(panel, clientPanel, gbc, 1, 1, 1.0)
        addComponent(panel, JBLabel("API URL:"), gbc, 0, 2, 0.0)
        addComponent(panel, createApiUrlPanel(), gbc, 1, 2, 1.0)
        addComponent(panel, JBLabel("API Key:"), gbc, 0, 3, 0.0)
        addComponent(panel, createApiKeyPanel(), gbc, 1, 3, 1.0)

        val modulePanel = JPanel(BorderLayout(5, 0))
        modulePanel.add(moduleComboBox, BorderLayout.CENTER)
        modulePanel.add(JPanel(FlowLayout(FlowLayout.RIGHT, 5, 0)).apply {
            add(refreshModelsButton)
            add(checkConfigButton)
        }, BorderLayout.EAST)

        val moduleLabelPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            add(JBLabel("Model: "))
            add(JBLabel(AllIcons.General.ContextHelp).apply {
                toolTipText = "Select a model from the list below. Use 'Add Model' or fetch models from the API."
            })
        }
        addComponent(panel, moduleLabelPanel, gbc, 0, 4, 0.0)
        addComponent(panel, modulePanel, gbc, 1, 4, 1.0)

        val modelListPanel = createModelListPanel()
        gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.BOTH
        gbc.weighty = 1.0
        addComponent(panel, modelListPanel, gbc, 0, 5, 1.0)
        gbc.gridwidth = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weighty = 0.0

        val languagePanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            add(JBLabel("Language: "))
            add(JBLabel(AllIcons.General.ContextHelp).apply {
                toolTipText = "The language of the generated commit message. Note that the actual output language depends on the LLM model's language capabilities."
            })
        }
        addComponent(panel, languagePanel, gbc, 0, 6, 0.0)
        addComponent(panel, languageComboBox, gbc, 1, 6, 1.0)

        gbc.gridwidth = 2
        gbc.weighty = 1.0
        gbc.fill = GridBagConstraints.BOTH
        addComponent(panel, JPanel(), gbc, 0, 7, 1.0)
        return panel
    }

    private fun createPromptSettingsPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = JBUI.insets(10)
            fill = GridBagConstraints.HORIZONTAL
        }
        addComponent(panel, JBLabel("Prompt type:"), gbc, 0, 0, 0.0)
        addComponent(panel, promptTypeComboBox, gbc, 1, 0, 1.0)

        val contentPanel = JPanel(java.awt.CardLayout()).apply {
            preferredSize = Dimension(-1, 300)
            add(customPromptPanel, "CUSTOM_PROMPT")
            add(projectPromptPanel, "PROJECT_PROMPT")
        }
        gbc.gridwidth = 2
        gbc.weighty = 1.0
        gbc.fill = GridBagConstraints.BOTH
        addComponent(panel, contentPanel, gbc, 0, 1, 1.0)

        promptTypeComboBox.addActionListener {
            val cardLayout = contentPanel.layout as java.awt.CardLayout
            if (Constants.CUSTOM_PROMPT == promptTypeComboBox.selectedItem) {
                cardLayout.show(contentPanel, "CUSTOM_PROMPT")
            } else {
                cardLayout.show(contentPanel, "PROJECT_PROMPT")
            }
        }
        return panel
    }

    private fun createFileFilterPanel(): JPanel = createFileExclusionPanel()

    private fun addComponent(parent: JPanel, component: Component, gbc: GridBagConstraints, gridx: Int, gridy: Int, weightx: Double) {
        gbc.gridx = gridx
        gbc.gridy = gridy
        gbc.weightx = weightx
        parent.add(component, gbc)
    }

    fun reloadModelComboBox(selectedClient: String?) {
        if (selectedClient.isNullOrBlank()) return
        moduleComboBox.removeAllItems()
        val modules = settings.getAvailableModels(selectedClient)
        modules.forEach { module ->
            if (module.isNotBlank()) {
                moduleComboBox.addItem(module)
            }
        }

        val selectedModel = settings.getSelectedModel(selectedClient)
        if (selectedModel.isNotBlank() && modules.contains(selectedModel)) {
            moduleComboBox.selectedItem = selectedModel
        } else if (modules.isNotEmpty()) {
            val firstModel = modules[0]
            settings.setSelectedModel(selectedClient, firstModel)
            moduleComboBox.selectedItem = firstModel
        } else {
            settings.setSelectedModel(selectedClient, "")
            moduleComboBox.selectedItem = ""
        }
        reloadModelList(selectedClient)
    }

    private fun refreshModels() {
        val selectedClient = clientComboBox.selectedItem as String
        persistCurrentProviderConfig(selectedClient)

        ProgressManager.getInstance().run(object : Task.Modal(null, "Refreshing Models", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.isIndeterminate = true
                    indicator.text = "Fetching models from $selectedClient..."
                    val models = modelDiscoveryService.fetchModels(selectedClient)
                    ApplicationManager.getApplication().invokeLater {
                        importFetchedModels(selectedClient, models)
                    }
                } catch (ex: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog("Failed to refresh models: ${ex.message}", "Refresh Models")
                    }
                }
            }
        })
    }

    fun persistCurrentProviderConfig(client: String?) {
        if (client.isNullOrBlank()) return
        val moduleConfig = settings.getModuleConfig(client)
        moduleConfig.url = apiUrlField.text.trim()
        moduleConfig.apiKey = String(apiKeyField.password)
        settings.setSelectedModel(client, currentModuleValue)
    }

    fun loadClientConfig(client: String?) {
        if (client.isNullOrBlank()) return
        val moduleConfig = settings.getModuleConfig(client)
        apiUrlField.text = moduleConfig.url.ifBlank { "" }
        apiKeyField.text = moduleConfig.apiKey.ifBlank { "" }
        updateApiUrlPreview()
    }

    private fun createApiUrlPanel(): JPanel =
        JPanel(BorderLayout(0, 4)).apply {
            add(apiUrlField, BorderLayout.NORTH)
            add(apiUrlPreviewLabel, BorderLayout.SOUTH)
            updateApiUrlPreview()
        }

    private fun updateApiUrlPreview() {
        val selectedClient = clientComboBox.selectedItem as? String ?: Constants.OpenAI
        val rawUrl = apiUrlField.text.trim()
        if (rawUrl.isEmpty()) {
            apiUrlPreviewLabel.text = "Preview: "
            apiUrlPreviewLabel.toolTipText = null
            return
        }

        try {
            val previewUrl = ModelDiscoveryService.normalizeRequestUrl(selectedClient, rawUrl)
            apiUrlPreviewLabel.text = "Preview: $previewUrl"
            apiUrlPreviewLabel.toolTipText = previewUrl
        } catch (ex: Exception) {
            apiUrlPreviewLabel.text = "Preview: Invalid URL"
            apiUrlPreviewLabel.toolTipText = ex.message
        }
    }

    private fun createApiKeyPanel(): JPanel =
        JPanel(BorderLayout()).apply {
            add(apiKeyField, BorderLayout.CENTER)
            add(
                JButton(AllIcons.Actions.Show).apply {
                    isFocusable = false
                    preferredSize = Dimension(32, 28)
                    addActionListener { toggleApiKeyVisibility(this) }
                },
                BorderLayout.EAST,
            )
        }

    private fun toggleApiKeyVisibility(toggleButton: JButton) {
        apiKeyVisible = !apiKeyVisible
        if (apiKeyVisible) {
            apiKeyField.echoChar = 0.toChar()
            toggleButton.icon = AllIcons.Actions.ToggleVisibility
        } else {
            apiKeyField.echoChar = '•'
            toggleButton.icon = AllIcons.Actions.Show
        }
        apiKeyField.revalidate()
        apiKeyField.repaint()
    }

    private fun checkCurrentConfig() {
        val selectedClient = clientComboBox.selectedItem as String
        persistCurrentProviderConfig(selectedClient)
        ProgressManager.getInstance().run(object : Task.Modal(null, "Validating Configuration", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.isIndeterminate = true
                    indicator.text = "Validating configuration..."
                    val aiService: AIService = CommitMessageService.getAIService(selectedClient)
                    val validateResPair = aiService.validateConfig(
                        mapOf(
                            "url" to apiUrlField.text.trim(),
                            "module" to currentModuleValue.trim(),
                            "apiKey" to String(apiKeyField.password),
                        ),
                    )
                    ApplicationManager.getApplication().invokeLater {
                        if (validateResPair.left) {
                            Messages.showInfoMessage(mainPanel, "Configuration validation successful.", "Success")
                        } else {
                            DialogUtil.showErrorDialog(mainPanel, validateResPair.right, DialogUtil.CONFIGURATION_ERROR_TITLE)
                        }
                    }
                } catch (ex: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(mainPanel, "Validation error occurred: ${ex.message}", "Error")
                    }
                }
            }
        })
    }

    private fun createModelListPanel(): JPanel =
        JPanel(BorderLayout(0, 8)).apply {
            add(
                JPanel(BorderLayout()).apply {
                    add(JBLabel("Model List").apply { font = font.deriveFont(Font.BOLD, 12f) }, BorderLayout.WEST)
                    add(JPanel(FlowLayout(FlowLayout.RIGHT, 5, 0)).apply { add(addModelButton) }, BorderLayout.EAST)
                },
                BorderLayout.NORTH,
            )
            add(JBScrollPane(modelRowsPanel).apply { preferredSize = Dimension(-1, 140) }, BorderLayout.CENTER)
            add(
                JBLabel("Each row is one model. Use 'Add Model' for manual input, or fetch and choose models from the API.")
                    .apply { foreground = JBColor.GRAY },
                BorderLayout.SOUTH,
            )
        }

    private fun reloadModelList(selectedClient: String?) {
        if (selectedClient.isNullOrBlank()) return
        modelRowsPanel.removeAll()
        val models = settings.getAvailableModels(selectedClient)
        val selectedModel = settings.getSelectedModel(selectedClient)
        if (models.isEmpty()) {
            modelRowsPanel.add(
                JBLabel("No models in the list yet. Use 'Add Model' or fetch from API.").apply {
                    foreground = JBColor.GRAY
                    border = JBUI.Borders.empty(8)
                },
            )
        } else {
            models.forEach { model ->
                modelRowsPanel.add(createModelRow(selectedClient, model, model == selectedModel))
            }
        }
        modelRowsPanel.revalidate()
        modelRowsPanel.repaint()
    }

    private fun addCustomModel() {
        val selectedClient = clientComboBox.selectedItem as String
        val input = Messages.showInputDialog(
            mainPanel,
            "Enter a model name for $selectedClient:",
            "Add Custom Model",
            Messages.getQuestionIcon(),
            currentModuleValue,
            null,
        ) ?: return

        val model = input.trim()
        if (model.isEmpty()) {
            Messages.showErrorDialog(mainPanel, "Model name cannot be empty.", "Add Custom Model")
            return
        }

        settings.addAvailableModel(selectedClient, model)
        settings.setSelectedModel(selectedClient, model)
        reloadModelComboBox(selectedClient)
        moduleComboBox.selectedItem = model
    }

    private fun createModelRow(client: String, model: String, selected: Boolean): JPanel {
        val row = JPanel(BorderLayout(8, 0)).apply {
            border = createModelRowBorder(selected)
            maximumSize = Dimension(Int.MAX_VALUE, 36)
            isOpaque = true
            background = getModelRowBackground(selected)
        }

        val label = JBLabel(model).apply {
            border = JBUI.Borders.emptyLeft(2)
            if (selected) {
                font = font.deriveFont(Font.BOLD)
            }
        }

        val actionsPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 5, 0)).apply {
            isOpaque = false
            add(createModelRowActionButton(AllIcons.Actions.Edit, "Edit model").apply {
                addActionListener { editModel(client, model) }
            })
            add(createModelRowActionButton(AllIcons.General.Remove, "Delete model").apply {
                addActionListener { removeModel(client, model) }
            })
        }

        val selectListener = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                settings.setSelectedModel(client, model)
                moduleComboBox.selectedItem = model
                reloadModelList(client)
            }
        }
        row.addMouseListener(selectListener)
        label.addMouseListener(selectListener)
        row.add(label, BorderLayout.CENTER)
        row.add(actionsPanel, BorderLayout.EAST)
        return row
    }

    private fun createModelRowActionButton(icon: Icon, toolTipText: String): JButton =
        JButton(icon).apply {
            this.toolTipText = toolTipText
            isFocusable = false
            border = JBUI.Borders.empty(4)
            isContentAreaFilled = false
            isBorderPainted = false
            isOpaque = false
            preferredSize = Dimension(24, 24)
        }

    private fun createModelRowBorder(selected: Boolean): Border {
        val outerBorder = if (selected) getModelRowAccentColor() else JBColor.border()
        return requireNotNull(JBUI.Borders.compound(
            JBUI.Borders.customLine(outerBorder, 1),
            JBUI.Borders.empty(6, 8),
        ))
    }

    private fun getModelRowBackground(selected: Boolean): Color {
        if (!selected) {
            return UIManager.getColor("Panel.background")
        }
        val base = UIManager.getColor("Panel.background")
        val fallback = if (JBColor.isBright()) Color(235, 244, 255) else Color(60, 71, 89)
        return if (base == null) fallback else blendColors(base, getModelRowAccentColor(), 0.08f)
    }

    private fun getModelRowAccentColor(): Color {
        val focused = UIManager.getColor("Component.focusColor")
        return focused ?: JBColor.namedColor(
            "Component.focusColor",
            JBColor(Color(64, 128, 255), Color(104, 151, 187)),
        )
    }

    private fun blendColors(base: Color, accent: Color, accentRatio: Float): Color {
        val clampedRatio = accentRatio.coerceIn(0f, 1f)
        val baseRatio = 1f - clampedRatio
        val red = kotlin.math.round(base.red * baseRatio + accent.red * clampedRatio).toInt()
        val green = kotlin.math.round(base.green * baseRatio + accent.green * clampedRatio).toInt()
        val blue = kotlin.math.round(base.blue * baseRatio + accent.blue * clampedRatio).toInt()
        return Color(red, green, blue)
    }

    private fun editModel(client: String, oldModel: String) {
        val input = Messages.showInputDialog(
            mainPanel,
            "Edit model name:",
            "Edit Model",
            Messages.getQuestionIcon(),
            oldModel,
            null,
        ) ?: return

        val newModel = input.trim()
        if (newModel.isEmpty()) {
            Messages.showErrorDialog(mainPanel, "Model name cannot be empty.", "Edit Model")
            return
        }
        if (newModel == oldModel) {
            return
        }

        val existingModels = settings.getAvailableModels(client)
        if (existingModels.contains(newModel)) {
            Messages.showErrorDialog(mainPanel, "Model already exists in the list.", "Edit Model")
            return
        }

        val selectedModel = settings.getSelectedModel(client)
        settings.removeAvailableModel(client, oldModel)
        settings.addAvailableModel(client, newModel)
        if (oldModel == selectedModel) {
            settings.setSelectedModel(client, newModel)
            moduleComboBox.selectedItem = newModel
        }
        reloadModelComboBox(client)
        if (oldModel == selectedModel) {
            moduleComboBox.selectedItem = newModel
        }
    }

    private fun removeModel(client: String, model: String) {
        settings.removeAvailableModel(client, model)
        val selectedModel = settings.getSelectedModel(client)
        if (model == selectedModel) {
            val remaining = settings.getAvailableModels(client)
            val nextModel = if (remaining.isEmpty()) "" else remaining[0]
            settings.setSelectedModel(client, nextModel)
            moduleComboBox.selectedItem = nextModel
        }
        reloadModelComboBox(client)
    }

    private fun importFetchedModels(client: String, models: List<String>) {
        if (models.isEmpty()) {
            Messages.showInfoMessage(
                "No models were returned. You can still add models manually.",
                "Fetch Models",
            )
            return
        }

        ModelImportDialog(mainPanel, client, models, settings, modelDiscoveryService, Runnable {
            reloadModelComboBox(client)
        }).show()
    }

    val currentModuleValue: String
        get() = moduleComboBox.selectedItem?.toString()?.trim().orEmpty()

    fun setPromptTypeComboBox(promptTypeComboBox: ComboBox<String>) {
        this.promptTypeComboBox = promptTypeComboBox
    }

    fun setModuleComboBox(moduleComboBox: ComboBox<String>) {
        this.moduleComboBox = moduleComboBox
    }

    private fun createFileExclusionPanel(): JPanel {
        val panel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.compound(
                JBUI.Borders.empty(10, 0, 5, 0),
                JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
            )
        }

        val titlePanel = JPanel(BorderLayout()).apply {
            add(JBLabel("File Filtering Settings").apply {
                font = font.deriveFont(Font.BOLD, 14f)
            }, BorderLayout.WEST)
            add(enableFileExclusionCheckBox, BorderLayout.EAST)
        }
        panel.add(titlePanel, BorderLayout.NORTH)

        val contentPanel = JPanel(BorderLayout(0, 5)).apply {
            border = JBUI.Borders.empty(10, 0, 0, 0)
            add(JBLabel(Constants.EXCLUDE_PATTERNS_HELP_TEXT).apply {
                font = font.deriveFont(Font.PLAIN, 11f)
                foreground = JBColor.GRAY
            }, BorderLayout.NORTH)
        }

        val textAreaPanel = JPanel(BorderLayout(0, 5))
        textAreaPanel.add(JBScrollPane(excludePatternsTextArea).apply {
            preferredSize = Dimension(-1, 120)
        }, BorderLayout.CENTER)
        textAreaPanel.add(JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
            add(resetToDefaultButton)
        }, BorderLayout.SOUTH)

        contentPanel.add(textAreaPanel, BorderLayout.CENTER)
        panel.add(contentPanel, BorderLayout.CENTER)

        enableFileExclusionCheckBox.addActionListener {
            val enabled = enableFileExclusionCheckBox.isSelected
            excludePatternsTextArea.isEnabled = enabled
            resetToDefaultButton.isEnabled = enabled
        }
        resetToDefaultButton.addActionListener {
            excludePatternsTextArea.text = Constants.DEFAULT_EXCLUDE_PATTERNS_TEXT
        }
        return panel
    }

    private fun createRecentPromptPanel(): JPanel {
        val panel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(10)
        }

        val headerPanel = JPanel(BorderLayout())
        headerPanel.add(JBLabel("Most Recent AI Prompt").apply {
            font = font.deriveFont(Font.BOLD, 14f)
        }, BorderLayout.WEST)
        headerPanel.add(JPanel(FlowLayout(FlowLayout.RIGHT, 5, 0)).apply {
            add(refreshPromptButton)
            add(copyPromptButton)
        }, BorderLayout.EAST)

        val statusPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 5)).apply {
            add(promptStatusLabel)
        }

        val northPanel = JPanel(BorderLayout()).apply {
            add(headerPanel, BorderLayout.NORTH)
            add(statusPanel, BorderLayout.SOUTH)
        }
        panel.add(northPanel, BorderLayout.NORTH)

        panel.add(
            JBScrollPane(recentPromptTextArea).apply {
                verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
                horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
                border = JBUI.Borders.compound(
                    JBUI.Borders.customLine(JBColor.GRAY, 1),
                    JBUI.Borders.empty(5),
                )
            },
            BorderLayout.CENTER,
        )

        panel.add(
            JPanel(FlowLayout(FlowLayout.LEFT, 0, 10)).apply {
                add(JBLabel("This shows the most recent prompt sent to the AI for the current project.").apply {
                    font = font.deriveFont(Font.PLAIN, 11f)
                    foreground = JBColor.GRAY
                })
            },
            BorderLayout.SOUTH,
        )
        return panel
    }

    private fun refreshRecentPrompt() {
        val currentProject = getCurrentProject()
        if (currentProject != null) {
            val recentPrompt = LastPromptService.getLastPrompt(currentProject)
            if (!recentPrompt.isNullOrBlank()) {
                recentPromptTextArea.text = recentPrompt
                promptStatusLabel.text =
                    "Last updated: " + java.time.LocalDateTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                    )
                promptStatusLabel.foreground = JBColor.GRAY
                copyPromptButton.isEnabled = true
            } else {
                recentPromptTextArea.text = ""
                promptStatusLabel.text = "No recent prompt available for current project"
                promptStatusLabel.foreground = JBColor.GRAY
                copyPromptButton.isEnabled = false
            }
        } else {
            recentPromptTextArea.text = ""
            promptStatusLabel.text = "No project currently open"
            promptStatusLabel.foreground = JBColor.GRAY
            copyPromptButton.isEnabled = false
        }
        recentPromptTextArea.caretPosition = 0
    }

    private fun copyRecentPromptToClipboard() {
        val promptText = recentPromptTextArea.text
        if (promptText.isNotBlank()) {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(promptText), null)
            promptStatusLabel.text = "Prompt copied to clipboard!"
            promptStatusLabel.foreground = JBColor.GREEN
            Timer(2000) { refreshRecentPrompt() }.apply {
                isRepeats = false
                start()
            }
        }
    }

    private fun getCurrentProject(): Project? {
        val openProjects = ProjectManager.getInstance().openProjects
        return if (openProjects.isNotEmpty()) openProjects[0] else null
    }
}
