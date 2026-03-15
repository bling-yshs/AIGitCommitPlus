package com.yshs.aicommit.config

import com.yshs.aicommit.constant.Constants
import com.yshs.aicommit.pojo.PromptInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "com.yshs.aicommit.config.ApiKeySettings", storages = [Storage("AIGitCommitPlusSettings.xml")])
class ApiKeySettings : PersistentStateComponent<ApiKeySettings> {
    var selectedClient: String = Constants.OPENAI
        set(value) {
            field = value
            val selectedModel = getSelectedModel(value)
            selectedModule = if (selectedModel.isNotBlank()) selectedModel else ""
        }
    var selectedModule: String = ""
    var commitLanguage: String = "English"
        get() = Constants.normalizeLanguageName(field)
        set(value) {
            field = Constants.normalizeLanguageName(value)
        }
    var promptType: String = Constants.CUSTOM_PROMPT
    var customPrompts: MutableList<PromptInfo> = mutableListOf()
    var customPrompt: PromptInfo = PromptInfo("", "")
    var moduleConfigs: MutableMap<String, ModuleConfig> = mutableMapOf()
        set(value) {
            field = value
            ensureModuleConfigState()
        }
    var enableFileExclusion: Boolean = false
    var excludePatterns: MutableList<String> = Constants.defaultExcludePatterns.toMutableList()

    override fun getState(): ApiKeySettings = this

    override fun loadState(state: ApiKeySettings) {
        selectedClient = state.selectedClient
        selectedModule = state.selectedModule
        commitLanguage = state.commitLanguage
        promptType = state.promptType
        customPrompts = state.customPrompts.map { it.copy() }.toMutableList()
        customPrompt = state.customPrompt.copy()
        moduleConfigs = state.moduleConfigs.mapValues { it.value.copyDeep() }.toMutableMap()
        enableFileExclusion = state.enableFileExclusion
        excludePatterns = state.excludePatterns.toMutableList()
        ensureModuleConfigState()
    }

    fun resolvedCustomPrompts(): MutableList<PromptInfo> {
        if (customPrompts.isEmpty()) {
            customPrompts = PromptInfo.defaultPrompts()
        }
        return customPrompts
    }

    fun resolvedExcludePatterns(): MutableList<String> {
        if (excludePatterns.isEmpty()) {
            excludePatterns = Constants.defaultExcludePatterns.toMutableList()
        }
        return excludePatterns
    }

    fun resetToDefaultExcludePatterns() {
        excludePatterns = Constants.defaultExcludePatterns.toMutableList()
    }

    fun isEnableFileExclusion(): Boolean = enableFileExclusion

    fun getModuleConfig(client: String): ModuleConfig {
        ensureModuleConfigState()
        return moduleConfigs.getOrPut(client) { Constants.defaultModuleConfig(client) }
    }

    fun getSelectedModel(client: String): String {
        if (client.isBlank()) {
            return ""
        }

        val moduleConfig = getModuleConfig(client)
        val stored = moduleConfig.resolvedSelectedModel()
        if (stored.isNotBlank()) {
            return stored
        }

        if (client == selectedClient &&
            selectedModule.isNotBlank() &&
            selectedModule in moduleConfig.availableModels
        ) {
            moduleConfig.selectModel(selectedModule)
            return selectedModule
        }

        return ""
    }

    fun setSelectedModel(client: String, selectedModel: String) {
        if (client.isBlank()) {
            return
        }

        getModuleConfig(client).selectModel(selectedModel)
        if (client == selectedClient) {
            this.selectedModule = selectedModel.trim()
        }
    }

    fun getAvailableModels(client: String): MutableList<String> =
        sanitizeModels(getModuleConfig(client).availableModels)

    fun setAvailableModels(client: String, availableModels: List<String>) {
        if (client.isBlank()) {
            return
        }
        getModuleConfig(client).availableModels = sanitizeModels(availableModels)
    }

    fun addAvailableModel(client: String, model: String) {
        if (client.isBlank() || model.isBlank()) {
            return
        }

        val config = getModuleConfig(client)
        val trimmed = model.trim()
        if (trimmed !in config.availableModels) {
            config.availableModels.add(trimmed)
        }
    }

    fun removeAvailableModel(client: String, model: String) {
        if (client.isBlank() || model.isBlank()) {
            return
        }
        getModuleConfig(client).availableModels.removeAll { it == model.trim() }
    }

    fun ensureModuleConfigState() {
        if (selectedClient !in Constants.LLM_CLIENTS) {
            selectedClient = Constants.OPENAI
            selectedModule = ""
        }

        for (client in Constants.LLM_CLIENTS) {
            val config = moduleConfigs.getOrPut(client) { Constants.defaultModuleConfig(client) }

            if (config.customModels.isNotEmpty()) {
                config.availableModels = sanitizeModels(config.availableModels + config.customModels)
                config.customModels.clear()
            } else {
                config.availableModels = sanitizeModels(config.availableModels)
            }

            if (config.resolvedSelectedModel().isBlank()) {
                val fallback =
                    when {
                        client == selectedClient &&
                            selectedModule.isNotBlank() &&
                            selectedModule in config.availableModels -> selectedModule
                        config.availableModels.isNotEmpty() -> config.availableModels.first()
                        else -> ""
                    }

                if (fallback.isNotBlank()) {
                    config.selectModel(fallback)
                }
            }
        }

        if (customPrompts.isEmpty()) {
            customPrompts = PromptInfo.defaultPrompts()
        }
        if (customPrompt.prompt.isBlank()) {
            customPrompt = customPrompts.firstOrNull()?.copy() ?: PromptInfo()
        }
        if (excludePatterns.isEmpty()) {
            excludePatterns = Constants.defaultExcludePatterns.toMutableList()
        }
    }

    data class ModuleConfig(
        var url: String = "",
        var apiKey: String = "",
        var modelId: String = "",
        var selectedModel: String = "",
        var availableModels: MutableList<String> = mutableListOf(),
        var customModels: MutableList<String> = mutableListOf(),
    ) {
        fun resolvedSelectedModel(): String = selectedModel.ifBlank { modelId.trim() }

        fun selectModel(value: String) {
            val sanitized = value.trim()
            selectedModel = sanitized
            modelId = sanitized
        }

        fun copyDeep(): ModuleConfig =
            copy(
                availableModels = availableModels.toMutableList(),
                customModels = customModels.toMutableList(),
            )
    }

    companion object {
        @JvmStatic
        fun getInstance(): ApiKeySettings =
            ApplicationManager.getApplication().getService(ApiKeySettings::class.java)

        private fun sanitizeModels(models: List<String>): MutableList<String> =
            models.map(String::trim)
                .filter(String::isNotBlank)
                .distinct()
                .toMutableList()
    }
}
