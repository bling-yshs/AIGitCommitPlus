package com.yshs.aicommit.service

import com.yshs.aicommit.config.ApiKeySettings
import com.yshs.aicommit.constant.Constants
import com.yshs.aicommit.service.impl.AnthropicService
import com.yshs.aicommit.service.impl.GeminiService
import com.yshs.aicommit.service.impl.OpenAIAPIService
import com.yshs.aicommit.service.impl.OpenAIResponsesService
import com.yshs.aicommit.util.PromptUtil
import com.intellij.openapi.project.Project

class CommitMessageService(
    private val settings: ApiKeySettings = ApiKeySettings.getInstance(),
) {
    private val aiService: AIService = getAIService(settings.selectedClient)

    fun checkNecessaryModuleConfigIsRight(): Boolean = aiService.checkNecessaryModuleConfigIsRight()

    fun generateCommitMessage(project: Project, diff: String): String {
        val prompt = PromptUtil.constructPrompt(project, diff)
        LastPromptService.setLastPrompt(project, prompt)
        return aiService.generateCommitMessage(prompt)
    }

    fun generateCommitMessageStream(
        project: Project,
        diff: String,
        onNext: (String) -> Unit,
        onError: (Throwable) -> Unit,
        onComplete: () -> Unit,
    ) {
        val prompt = PromptUtil.constructPrompt(project, diff)
        LastPromptService.setLastPrompt(project, prompt)
        aiService.generateCommitMessageStream(prompt, onNext, onError, onComplete)
    }

    fun generateByStream(): Boolean = aiService.supportsStreaming

    companion object {
        @JvmStatic
        fun getAIService(selectedClient: String): AIService =
            when (selectedClient) {
                Constants.OPENAI -> OpenAIAPIService()
                Constants.OPENAI_RESPONSE -> OpenAIResponsesService()
                Constants.GEMINI -> GeminiService()
                Constants.ANTHROPIC -> AnthropicService()
                else -> error("Invalid LLM client: $selectedClient")
            }
    }
}
