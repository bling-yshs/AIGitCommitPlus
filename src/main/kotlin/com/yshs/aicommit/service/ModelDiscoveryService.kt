package com.yshs.aicommit.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.yshs.aicommit.config.ApiKeySettings
import com.yshs.aicommit.constant.Constants
import com.yshs.aicommit.util.HttpUtil
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.LinkedHashSet

class ModelDiscoveryService {
    fun fetchModels(client: String): List<String> {
        val settings = ApiKeySettings.getInstance()
        return fetchModels(client, settings.getModuleConfig(client).copyDeep())
    }

    fun fetchModels(client: String, config: ApiKeySettings.ModuleConfig): List<String> {
        validateConfig(client, config)
        return when (client) {
            Constants.OPENAI, Constants.OPENAI_RESPONSE -> fetchOpenAIModels(config)
            Constants.GEMINI -> fetchGeminiModels(config)
            Constants.ANTHROPIC -> fetchAnthropicModels(config)
            else -> error("Unsupported client: $client")
        }
    }

    private fun fetchOpenAIModels(config: ApiKeySettings.ModuleConfig): List<String> {
        val connection = openGetConnection(normalizeOpenAIModelsUrl(config.url))
        connection.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        return parseModelIds(connection, "data", "id")
    }

    private fun fetchGeminiModels(config: ApiKeySettings.ModuleConfig): List<String> {
        val baseUrl = normalizeGeminiModelsUrl(config.url)
        val separator = if ('?' in baseUrl) "&" else "?"
        val endpoint = "$baseUrl${separator}key=${URLEncoder.encode(config.apiKey, StandardCharsets.UTF_8)}"
        return parseModelIds(openGetConnection(endpoint), "models", "name", "models/")
    }

    private fun fetchAnthropicModels(config: ApiKeySettings.ModuleConfig): List<String> {
        val connection = openGetConnection(normalizeAnthropicModelsUrl(config.url))
        connection.setRequestProperty("x-api-key", config.apiKey)
        connection.setRequestProperty("anthropic-version", ANTHROPIC_VERSION)
        return parseModelIds(connection, "data", "id")
    }

    private fun parseModelIds(
        connection: HttpURLConnection,
        arrayField: String,
        itemField: String,
        prefixToTrim: String = "",
    ): List<String> =
        HttpUtil.useConnection(connection) {
            val statusCode = it.responseCode
            val body = HttpUtil.readBody(it, statusCode >= 400)
            if (statusCode >= 400) {
                throw IOException(body)
            }

            val items = OBJECT_MAPPER.readTree(body).path(arrayField)
            if (!items.isArray) {
                return@useConnection emptyList()
            }

            val models = LinkedHashSet<String>()
            for (node in items) {
                var model = node.path(itemField).asText()
                if (model.isBlank()) {
                    continue
                }
                if (prefixToTrim.isNotBlank() && model.startsWith(prefixToTrim)) {
                    model = model.removePrefix(prefixToTrim)
                }
                models += model
            }
            models.toList()
        }

    private fun validateConfig(client: String, config: ApiKeySettings.ModuleConfig) {
        require(config.url.isNotBlank()) { "URL cannot be empty" }
        require(config.apiKey.isNotBlank()) { "API Key cannot be empty" }
        require(!config.url.contains('{') && !config.url.contains('}')) {
            "Please replace placeholder values in the URL first"
        }
        require(client in Constants.LLM_CLIENTS) { "Missing configuration for $client" }
    }

    private fun openGetConnection(url: String): HttpURLConnection =
        HttpUtil.openConnection(
            url = url,
            method = "GET",
            accept = "application/json",
            headers = emptyMap(),
        )

    private fun normalizeOpenAIModelsUrl(url: String): String =
        normalizeVersionedEndpoint(url, "/v1", "/models", "/chat/completions", "/responses", "/models")

    private fun normalizeAnthropicModelsUrl(url: String): String =
        normalizeVersionedEndpoint(url, "/v1", "/models", "/messages", "/models")

    private fun normalizeGeminiModelsUrl(url: String): String = normalizeGeminiBaseUrl(url)

    companion object {
        private val OBJECT_MAPPER = ObjectMapper()
        private const val ANTHROPIC_VERSION = "2023-06-01"
        private const val VERSION_BYPASS_MARKER = "#"

        @JvmStatic
        fun normalizeOpenAIChatUrl(url: String): String =
            normalizeVersionedEndpoint(url, "/v1", "/chat/completions", "/chat/completions", "/responses", "/models")

        @JvmStatic
        fun normalizeOpenAIResponsesUrl(url: String): String =
            normalizeVersionedEndpoint(url, "/v1", "/responses", "/chat/completions", "/responses", "/models")

        @JvmStatic
        fun normalizeAnthropicMessagesUrl(url: String): String =
            normalizeVersionedEndpoint(url, "/v1", "/messages", "/messages", "/models")

        @JvmStatic
        fun normalizeGeminiGenerateContentUrl(url: String, model: String): String {
            var trimmed = url.trim()
            val skipVersionSegment = trimmed.endsWith(VERSION_BYPASS_MARKER)
            if (skipVersionSegment) {
                trimmed = trimmed.removeSuffix(VERSION_BYPASS_MARKER).trim()
            }

            val uri = URI.create(trimmed)
            val basePath = normalizeGeminiBasePath(uri.path, skipVersionSegment)
            return rebuildUri(uri, "$basePath/models/$model:generateContent")
        }

        @JvmStatic
        fun normalizeRequestUrl(client: String, url: String): String {
            if (url.isBlank()) {
                return ""
            }

            return when (client) {
                Constants.OPENAI -> normalizeOpenAIChatUrl(url)
                Constants.OPENAI_RESPONSE -> normalizeOpenAIResponsesUrl(url)
                Constants.GEMINI -> normalizeGeminiBaseUrl(url)
                Constants.ANTHROPIC -> normalizeAnthropicMessagesUrl(url)
                else -> url.trim()
            }
        }

        private fun normalizeGeminiBaseUrl(url: String): String {
            var trimmed = url.trim()
            val skipVersionSegment = trimmed.endsWith(VERSION_BYPASS_MARKER)
            if (skipVersionSegment) {
                trimmed = trimmed.removeSuffix(VERSION_BYPASS_MARKER).trim()
            }

            val uri = URI.create(trimmed)
            val basePath = normalizeGeminiBasePath(uri.path, skipVersionSegment)
            return rebuildUri(uri, "$basePath/models")
        }

        private fun normalizeVersionedEndpoint(
            url: String,
            versionSegment: String,
            expectedSuffix: String,
            vararg knownSuffixes: String,
        ): String {
            var trimmed = url.trim()
            val skipVersionSegment = trimmed.endsWith(VERSION_BYPASS_MARKER)
            if (skipVersionSegment) {
                trimmed = trimmed.removeSuffix(VERSION_BYPASS_MARKER).trim()
            }

            val uri = URI.create(trimmed)
            val normalizedPath = normalizePath(uri.path.orEmpty(), versionSegment, expectedSuffix, skipVersionSegment, *knownSuffixes)
            return rebuildUri(uri, normalizedPath)
        }

        private fun normalizePath(
            path: String,
            versionSegment: String,
            expectedSuffix: String,
            skipVersionSegment: Boolean,
            vararg knownSuffixes: String,
        ): String {
            var normalizedPath = trimTrailingSlash(path)
            for (knownSuffix in knownSuffixes) {
                if (normalizedPath.endsWith(knownSuffix)) {
                    normalizedPath = trimTrailingSlash(normalizedPath.removeSuffix(knownSuffix))
                    break
                }
            }

            if (!skipVersionSegment && !normalizedPath.endsWith(versionSegment)) {
                normalizedPath += versionSegment
            }

            normalizedPath = trimTrailingSlash(normalizedPath)
            normalizedPath = if (normalizedPath.isBlank()) expectedSuffix else normalizedPath + expectedSuffix
            return if (normalizedPath.startsWith('/')) normalizedPath else "/$normalizedPath"
        }

        private fun normalizeGeminiBasePath(path: String, skipVersionSegment: Boolean): String {
            var normalizedPath = trimTrailingSlash(path)
            val modelsIndex = normalizedPath.indexOf("/models/")
            normalizedPath =
                when {
                    modelsIndex >= 0 -> normalizedPath.substring(0, modelsIndex)
                    normalizedPath.endsWith("/models") -> normalizedPath.removeSuffix("/models")
                    else -> normalizedPath
                }

            normalizedPath = trimTrailingSlash(normalizedPath)
            if (!skipVersionSegment && !normalizedPath.endsWith("/v1beta")) {
                normalizedPath += "/v1beta"
            }
            normalizedPath = trimTrailingSlash(normalizedPath)
            return when {
                normalizedPath.isEmpty() -> ""
                normalizedPath.startsWith('/') -> normalizedPath
                else -> "/$normalizedPath"
            }
        }

        private fun trimTrailingSlash(value: String): String {
            if (value.isBlank() || value == "/") {
                return ""
            }

            var trimmed = value
            while (trimmed.endsWith('/')) {
                trimmed = trimmed.dropLast(1)
            }
            return trimmed
        }

        private fun rebuildUri(uri: URI, path: String): String =
            URI(
                uri.scheme,
                uri.userInfo,
                uri.host,
                uri.port,
                path,
                null,
                null,
            ).toString()
    }
}
