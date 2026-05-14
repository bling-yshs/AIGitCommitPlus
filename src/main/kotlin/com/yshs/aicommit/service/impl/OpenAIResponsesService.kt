package com.yshs.aicommit.service.impl

import com.fasterxml.jackson.databind.JsonNode
import com.yshs.aicommit.config.ApiKeySettings
import com.yshs.aicommit.constant.Constants
import com.yshs.aicommit.service.AIService
import com.yshs.aicommit.service.ModelDiscoveryService
import com.yshs.aicommit.util.HeaderUtil
import com.yshs.aicommit.util.HttpUtil
import java.net.HttpURLConnection
import org.apache.commons.lang3.tuple.Pair

class OpenAIResponsesService : AIService {
    override val supportsStreaming: Boolean = true

    override fun generateCommitMessage(content: String): String {
        val settings = ApiKeySettings.getInstance()
        val config = settings.getModuleConfig(Constants.OPENAI_RESPONSE)
        val model = settings.getSelectedModel(Constants.OPENAI_RESPONSE)
        val connection = openConnection(config, model, content, false)
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
        val config = settings.getModuleConfig(Constants.OPENAI_RESPONSE)
        val model = settings.getSelectedModel(Constants.OPENAI_RESPONSE)
        val connection = openConnection(config, model, content, true)

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
                when (root.path("type").asText()) {
                    "response.output_text.delta" -> root.path("delta").textOrEmpty().takeIf(String::isNotEmpty)?.let(onNext)
                    "response.completed" -> onComplete()
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
        val config = settings.getModuleConfig(Constants.OPENAI_RESPONSE)
        return config.url.isNotBlank() &&
            config.apiKey.isNotBlank() &&
            settings.getSelectedModel(Constants.OPENAI_RESPONSE).isNotBlank()
    }

    override fun validateConfig(config: Map<String, String>): Pair<Boolean, String> =
        try {
            HttpUtil.useConnection(
                openConnection(
                    ApiKeySettings.ModuleConfig(
                        url = config["url"].orEmpty(),
                        apiKey = config["apiKey"].orEmpty(),
                    ),
                    config["module"].orEmpty(),
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
        config: ApiKeySettings.ModuleConfig,
        model: String,
        input: String,
        stream: Boolean,
    ): HttpURLConnection =
        HttpUtil.openConnection(
            url = ModelDiscoveryService.normalizeOpenAIResponsesUrl(config.url),
            method = "POST",
            accept = if (stream) "text/event-stream" else "application/json",
            contentType = "application/json; charset=UTF-8",
            headers = HeaderUtil.mergeHeaders(
                mapOf("Authorization" to "Bearer ${config.apiKey}"),
                config.customHeadersJson,
            ),
            body = HttpUtil.objectMapper.writeValueAsString(
                mapOf(
                    "model" to model,
                    "input" to input,
                    "stream" to stream,
                ),
            ),
        )

    private fun extractText(root: JsonNode): String {
        root.path("output_text").textOrEmpty().takeIf(String::isNotBlank)?.let { return it.replace("```", "") }

        val output = root.path("output")
        if (output.isArray) {
            for (item in output) {
                val content = item.path("content")
                if (!content.isArray) {
                    continue
                }
                val builder = StringBuilder()
                for (part in content) {
                    val text = part.path("text").textOrEmpty()
                    if (text.isNotBlank()) {
                        if (builder.isNotEmpty()) {
                            builder.append('\n')
                        }
                        builder.append(text)
                    }
                }
                if (builder.isNotEmpty()) {
                    return builder.toString().replace("```", "")
                }
            }
        }
        return ""
    }

    /**
     * Returns an empty string for missing or explicit JSON null text nodes.
     */
    private fun JsonNode.textOrEmpty(): String =
        if (isMissingNode || isNull) "" else asText()
}
