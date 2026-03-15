package com.yshs.aicommit.service.impl

import com.fasterxml.jackson.databind.JsonNode
import com.yshs.aicommit.config.ApiKeySettings
import com.yshs.aicommit.constant.Constants
import com.yshs.aicommit.service.AIService
import com.yshs.aicommit.service.ModelDiscoveryService
import com.yshs.aicommit.util.HttpUtil
import java.net.HttpURLConnection
import org.apache.commons.lang3.tuple.Pair

class AnthropicService : AIService {
    override val supportsStreaming: Boolean = true

    override fun generateCommitMessage(content: String): String {
        val settings = ApiKeySettings.getInstance()
        val config = settings.getModuleConfig(Constants.ANTHROPIC)
        val model = settings.getSelectedModel(Constants.ANTHROPIC)
        val connection = openConnection(config.url, model, config.apiKey, content, false)
        return HttpUtil.useConnection(connection) {
            val status = it.responseCode
            val body = HttpUtil.readBody(it, status >= 400)
            if (status >= 400) {
                error(body)
            }
            extractText(HttpUtil.objectMapper.readTree(body))
        }
    }

    override fun generateCommitMessageStream(
        content: String,
        onNext: (String) -> Unit,
        onError: (Throwable) -> Unit,
        onComplete: () -> Unit,
    ) {
        val settings = ApiKeySettings.getInstance()
        val config = settings.getModuleConfig(Constants.ANTHROPIC)
        val model = settings.getSelectedModel(Constants.ANTHROPIC)
        val connection = openConnection(config.url, model, config.apiKey, content, true)

        try {
            if (connection.responseCode >= 400) {
                error(HttpUtil.readBody(connection, true))
            }

            HttpUtil.readEventStream(connection) { line ->
                if (!line.startsWith("data: ")) {
                    return@readEventStream
                }

                val payload = line.removePrefix("data: ")
                if (payload == "[DONE]") {
                    onComplete()
                    return@readEventStream
                }

                val root = HttpUtil.objectMapper.readTree(payload)
                when {
                    root.path("type").asText() == "content_block_delta" &&
                        root.path("delta").path("type").asText() == "text_delta" -> {
                        root.path("delta").path("text").asText().takeIf(String::isNotBlank)?.let(onNext)
                    }
                    root.path("type").asText() == "message_stop" -> onComplete()
                }
            }
        } catch (throwable: Throwable) {
            onError(throwable)
            throw throwable
        } finally {
            connection.disconnect()
        }
    }

    override fun checkNecessaryModuleConfigIsRight(): Boolean {
        val settings = ApiKeySettings.getInstance()
        val config = settings.getModuleConfig(Constants.ANTHROPIC)
        return config.url.isNotBlank() &&
            config.apiKey.isNotBlank() &&
            settings.getSelectedModel(Constants.ANTHROPIC).isNotBlank()
    }

    override fun validateConfig(config: Map<String, String>): Pair<Boolean, String> =
        try {
            HttpUtil.useConnection(
                openConnection(
                    config["url"].orEmpty(),
                    config["module"].orEmpty(),
                    config["apiKey"].orEmpty(),
                    "hi",
                    false,
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
        input: String,
        stream: Boolean,
    ): HttpURLConnection =
        HttpUtil.openConnection(
            url = ModelDiscoveryService.normalizeAnthropicMessagesUrl(url),
            method = "POST",
            accept = if (stream) "text/event-stream" else "application/json",
            contentType = "application/json; charset=UTF-8",
            headers = mapOf(
                "x-api-key" to apiKey,
                "anthropic-version" to ANTHROPIC_VERSION,
            ),
            body = HttpUtil.objectMapper.writeValueAsString(
                mapOf(
                    "model" to model,
                    "max_tokens" to 1024,
                    "stream" to stream,
                    "messages" to listOf(mapOf("role" to "user", "content" to input)),
                ),
            ),
        )

    private fun extractText(root: JsonNode): String {
        val content = root.path("content")
        if (!content.isArray) {
            return ""
        }

        val builder = StringBuilder()
        for (item in content) {
            val text = item.path("text").asText()
            if (text.isBlank()) {
                continue
            }
            if (builder.isNotEmpty()) {
                builder.append('\n')
            }
            builder.append(text)
        }
        return builder.toString().replace("```", "")
    }

    private companion object {
        const val ANTHROPIC_VERSION = "2023-06-01"
    }
}
