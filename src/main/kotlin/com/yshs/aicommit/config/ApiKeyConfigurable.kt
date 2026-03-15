package com.yshs.aicommit.config

import com.yshs.aicommit.constant.Constants
import com.yshs.aicommit.pojo.PromptInfo
import com.intellij.openapi.options.Configurable
import javax.swing.JComponent
import javax.swing.table.DefaultTableModel

class ApiKeyConfigurable : Configurable {
    private var ui: ApiKeyConfigurableUI? = null
    private val settings = ApiKeySettings.getInstance()

    override fun getDisplayName(): String = "AI Git Commit Plus"

    override fun createComponent(): JComponent {
        ui = ApiKeyConfigurableUI()
        loadSettings()
        return ui!!.mainPanel
    }

    override fun isModified(): Boolean = true

    override fun apply() {
        val currentUi = ui ?: return
        val selectedClient = currentUi.clientComboBox.selectedItem as String
        currentUi.persistCurrentProviderConfig(selectedClient)
        val selectedModule = currentUi.currentModuleValue
        val commitLanguage = currentUi.languageComboBox.selectedItem as String

        settings.selectedClient = selectedClient
        settings.setSelectedModel(selectedClient, selectedModule)
        settings.selectedModule = selectedModule
        settings.commitLanguage = commitLanguage

        val selectedPromptType = currentUi.promptTypeComboBox.selectedItem as String
        if (Constants.CUSTOM_PROMPT == selectedPromptType) {
            saveCustomPromptsAndChoosedPrompt()
        }
        settings.promptType = selectedPromptType
        saveFileExclusionSettings()
    }

    override fun reset() {
        loadSettings()
    }

    override fun disposeUIResources() {
        ui = null
    }

    private fun loadSettings() {
        val currentUi = ui ?: return
        currentUi.clientComboBox.selectedItem = settings.selectedClient
        currentUi.loadClientConfig(settings.selectedClient)
        currentUi.reloadModelComboBox(settings.selectedClient)
        currentUi.moduleComboBox.selectedItem = settings.getSelectedModel(settings.selectedClient)
        currentUi.languageComboBox.selectedItem = settings.commitLanguage
        loadCustomPrompts()
        loadChoosedPrompt()
        currentUi.promptTypeComboBox.selectedItem = settings.promptType
        loadFileExclusionSettings()
    }

    private fun loadCustomPrompts() {
        val model = ui!!.customPromptsTable.model as DefaultTableModel
        model.rowCount = 0
        settings.customPrompts.forEach { prompt ->
            model.addRow(arrayOf(prompt.description, prompt.prompt))
        }
    }

    private fun loadChoosedPrompt() {
        val currentUi = ui ?: return
        val customPrompt = settings.customPrompt
        val model = currentUi.customPromptsTable.model as DefaultTableModel
        for (i in 0 until model.rowCount) {
            val description = model.getValueAt(i, 0) as String
            val prompt = model.getValueAt(i, 1) as String
            if (customPrompt.description == description && customPrompt.prompt == prompt) {
                currentUi.customPromptsTable.setRowSelectionInterval(i, i)
            }
        }
    }

    private fun saveCustomPromptsAndChoosedPrompt() {
        val currentUi = ui ?: return
        val model = currentUi.customPromptsTable.model as DefaultTableModel
        val selectedRow = currentUi.SELECTED_ROW
        val customPrompts = mutableListOf<PromptInfo>()
        for (i in 0 until model.rowCount) {
            val promptInfo = PromptInfo(
                model.getValueAt(i, 0) as String,
                model.getValueAt(i, 1) as String,
            )
            customPrompts.add(i, promptInfo)
            if (selectedRow == i) {
                settings.customPrompt = promptInfo
            }
        }
        settings.customPrompts = customPrompts
    }

    private fun saveFileExclusionSettings() {
        val currentUi = ui ?: return
        settings.enableFileExclusion = currentUi.enableFileExclusionCheckBox.isSelected
        val excludePatterns = mutableListOf<String>()
        val excludePatternsText = currentUi.excludePatternsTextArea.text
        if (!excludePatternsText.isNullOrBlank()) {
            excludePatternsText.split("\n").forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    excludePatterns.add(trimmed)
                }
            }
        }
        settings.excludePatterns = excludePatterns
    }

    private fun loadFileExclusionSettings() {
        val currentUi = ui ?: return
        currentUi.enableFileExclusionCheckBox.isSelected = settings.isEnableFileExclusion()
        val excludePatterns = settings.excludePatterns
        currentUi.excludePatternsTextArea.text =
            if (excludePatterns.isNotEmpty()) excludePatterns.joinToString("\n") else Constants.DEFAULT_EXCLUDE_PATTERNS.joinToString("\n")
        val enabled = settings.isEnableFileExclusion()
        currentUi.excludePatternsTextArea.isEnabled = enabled
        currentUi.excludePatternsTextArea.parent.parent.isEnabled = enabled
    }
}
