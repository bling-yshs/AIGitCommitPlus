package com.yshs.aicommit.service.impl

import com.fasterxml.jackson.databind.JsonNode
import com.yshs.aicommit.config.ApiKeySettings
import com.yshs.aicommit.constant.Constants
import com.yshs.aicommit.service.AIService
import com.yshs.aicommit.service.ModelDiscoveryService
import com.yshs.aicommit.util.CommonUtil
import com.yshs.aicommit.util.HttpUtil
import java.net.HttpURLConnection
import java.nio.charset.Charset
import org.apache.commons.lang3.tuple.Pair

class OpenAIAPIService : AIService {
    override val supportsStreaming: Boolean = true

    override fun generateCommitMessage(content: String): String {
        val settings = ApiKeySettings.getInstance()
        val config = settings.getModuleConfig(Constants.OPENAI)
        val model = settings.getSelectedModel(Constants.OPENAI)
        val connection = openConnection(config.url, model, config.apiKey, content, false)
        return HttpUtil.useConnection(connection) {
            val status = it.responseCode
            val body = HttpUtil.readBody(it, status >= 400)
            if (status >= 400) {
                error(body)
            }
            extractResponseText(HttpUtil.objectMapper.readTree(body))
        }
    }

    override fun generateCommitMessageStream(
        content: String,
        onNext: (String) -> Unit,
        onError: (Throwable) -> Unit,
        onComplete: () -> Unit,
    ) {
        val settings = ApiKeySettings.getInstance()
        val config = settings.getModuleConfig(Constants.OPENAI)
        val model = settings.getSelectedModel(Constants.OPENAI)
        val connection = openConnection(config.url, model, config.apiKey, content, true)
        val filter = ThinkTagFilter()

        try {
            if (connection.responseCode >= 400) {
                error(HttpUtil.readBody(connection, true))
            }

            val charset = Charset.forName(CommonUtil.getCharsetFromContentType(connection.contentType))
            HttpUtil.readEventStream(connection, charset) { line ->
                if (!line.startsWith("data: ")) {
                    return@readEventStream
                }

                val payload = line.removePrefix("data: ")
                if (payload == "[DONE]") {
                    filter.finish(onNext)
                    onComplete()
                    return@readEventStream
                }

                val root = HttpUtil.objectMapper.readTree(payload)
                val delta = firstArrayElement(root.path("choices"))?.path("delta")?.path("content")?.asText().orEmpty()
                if (delta.isNotBlank()) {
                    filter.consume(delta, onNext)
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
        val config = settings.getModuleConfig(Constants.OPENAI)
        return config.url.isNotBlank() &&
            config.apiKey.isNotBlank() &&
            settings.getSelectedModel(Constants.OPENAI).isNotBlank()
    }

    override fun validateConfig(config: Map<String, String>): Pair<Boolean, String> =
        validateWithConnection(
            openConnection(
                config["url"].orEmpty(),
                config["module"].orEmpty(),
                config["apiKey"].orEmpty(),
                "hi",
                false,
            ),
        )

    private fun openConnection(
        url: String,
        model: String,
        apiKey: String,
        input: String,
        stream: Boolean,
    ): HttpURLConnection =
        HttpUtil.openConnection(
            url = ModelDiscoveryService.normalizeOpenAIChatUrl(url),
            method = "POST",
            accept = if (stream) "text/event-stream" else "application/json",
            contentType = "application/json; charset=UTF-8",
            headers = mapOf("Authorization" to "Bearer $apiKey"),
            body = HttpUtil.objectMapper.writeValueAsString(
                mapOf(
                    "model" to model,
                    "stream" to stream,
                    "messages" to listOf(mapOf("role" to "user", "content" to input)),
                ),
            ),
        )

    private fun extractResponseText(root: JsonNode): String =
        firstArrayElement(root.path("choices"))
            ?.path("message")
            ?.path("content")
            ?.asText()
            .orEmpty()
            .replace("```", "")

    private fun validateWithConnection(connection: HttpURLConnection): Pair<Boolean, String> =
        try {
            HttpUtil.useConnection(connection) {
                val status = it.responseCode
                if (status >= 400) {
                    Pair.of(false, HttpUtil.readBody(it, true))
                } else {
                    Pair.of(true, "")
                }
            }
        } catch (throwable: Throwable) {
            Pair.of(false, throwable.message ?: throwable.javaClass.simpleName)
        }

    private fun firstArrayElement(node: JsonNode): JsonNode? =
        if (node.isArray && node.size() > 0) node[0] else null

    private class ThinkTagFilter {
        private val buffer = StringBuilder()
        private var insideThinkTag = false

        fun consume(token: String, onNext: (String) -> Unit) {
            buffer.append(token)
            var current = buffer.toString()

            while (true) {
                if (insideThinkTag) {
                    val endIndex = current.indexOf(THINK_END_TAG)
                    if (endIndex < 0) {
                        break
                    }
                    current = current.substring(endIndex + THINK_END_TAG.length)
                    insideThinkTag = false
                    continue
                }

                val startIndex = current.indexOf(THINK_START_TAG)
                if (startIndex >= 0) {
                    current.substring(0, startIndex)
                        .takeIf(String::isNotEmpty)
                        ?.let(onNext)
                    current = current.substring(startIndex + THINK_START_TAG.length)
                    insideThinkTag = true
                    continue
                }

                val safeLength = current.length - THINK_START_TAG.length + 1
                if (safeLength > 0) {
                    onNext(current.substring(0, safeLength))
                    current = current.substring(safeLength)
                }
                break
            }

            buffer.setLength(0)
            buffer.append(current)
        }

        fun finish(onNext: (String) -> Unit) {
            if (!insideThinkTag && buffer.isNotEmpty()) {
                onNext(buffer.toString())
            }
            buffer.setLength(0)
        }

        private companion object {
            const val THINK_START_TAG = "<think>"
            const val THINK_END_TAG = "</think>"
        }
    }
}
