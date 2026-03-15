package com.yshs.aicommit.service.impl

import com.fasterxml.jackson.databind.JsonNode
import com.yshs.aicommit.config.ApiKeySettings
import com.yshs.aicommit.constant.Constants
import com.yshs.aicommit.service.AIService
import com.yshs.aicommit.service.ModelDiscoveryService
import com.yshs.aicommit.util.HttpUtil
import java.io.IOException
import java.net.HttpURLConnection
import org.apache.commons.lang3.tuple.Pair

class GeminiService : AIService {
    override val supportsStreaming: Boolean = false

    override fun generateCommitMessage(content: String): String {
        val settings = ApiKeySettings.getInstance()
        val config = settings.getModuleConfig(Constants.GEMINI)
        val model = settings.getSelectedModel(Constants.GEMINI)
        val connection = openConnection(config.url, model, config.apiKey, content)
        return HttpUtil.useConnection(connection) {
            val status = it.responseCode
            val body = HttpUtil.readBody(it, status >= 400)
            if (status >= 400) {
                throw IOException(body)
            }

            extractText(HttpUtil.objectMapper.readTree(body))
                .ifBlank { throw IOException("Gemini response did not contain any text content") }
                .replace("```", "")
        }
    }

    override fun checkNecessaryModuleConfigIsRight(): Boolean {
        val settings = ApiKeySettings.getInstance()
        val config = settings.getModuleConfig(Constants.GEMINI)
        return config.url.isNotBlank() &&
            config.apiKey.isNotBlank() &&
            settings.getSelectedModel(Constants.GEMINI).isNotBlank()
    }

    override fun validateConfig(config: Map<String, String>): Pair<Boolean, String> =
        try {
            HttpUtil.useConnection(
                openConnection(
                    config["url"].orEmpty(),
                    config["module"].orEmpty(),
                    config["apiKey"].orEmpty(),
                    "hi",
                ),
            ) {
                if (it.responseCode >= 400) {
                    Pair.of(false, HttpUtil.readBody(it, true))
                } else {
                    Pair.of(true, "")
                }
            }
        } catch (throwable: Throwable) {
            Pair.of(false, throwable.message ?: throwable.javaClass.simpleName)
        }

    private fun openConnection(
        url: String,
        model: String,
        apiKey: String,
        textContent: String,
    ): HttpURLConnection =
        HttpUtil.openConnection(
            url = ModelDiscoveryService.normalizeGeminiGenerateContentUrl(url, model),
            method = "POST",
            accept = "application/json",
            contentType = "application/json",
            headers = mapOf("x-goog-api-key" to apiKey),
            body = HttpUtil.objectMapper.writeValueAsString(
                mapOf(
                    "contents" to listOf(
                        mapOf(
                            "parts" to listOf(
                                mapOf("text" to textContent),
                            ),
                        ),
                    ),
                    "generationConfig" to mapOf(
                        "thinkingConfig" to mapOf("thinkingBudget" to 0),
                    ),
                ),
            ),
        )

    private fun extractText(root: JsonNode): String {
        val candidates = root.path("candidates")
        if (!candidates.isArray || candidates.isEmpty) {
            return ""
        }

        val builder = StringBuilder()
        val parts = candidates[0].path("content").path("parts")
        if (!parts.isArray) {
            return ""
        }

        for (part in parts) {
            val text = part.path("text").asText()
            if (text.isBlank()) {
                continue
            }
            if (builder.isNotEmpty()) {
                builder.append('\n')
            }
            builder.append(text)
        }
        return builder.toString()
    }
}
