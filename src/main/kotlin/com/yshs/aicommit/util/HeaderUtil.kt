package com.yshs.aicommit.util

import com.fasterxml.jackson.databind.JsonNode

object HeaderUtil {
    /**
     * Parses the user-entered custom header JSON into request headers.
     */
    fun parseCustomHeaders(customHeadersJson: String): Map<String, String> {
        val trimmedJson = customHeadersJson.trim()
        if (trimmedJson.isBlank()) {
            return emptyMap()
        }

        val root =
            try {
                HttpUtil.objectMapper.readTree(trimmedJson)
            } catch (throwable: Throwable) {
                throw IllegalArgumentException("Custom Headers JSON must be a valid JSON object.", throwable)
            }

        require(root.isObject) { "Custom Headers JSON must be a JSON object." }
        if (root.size() == 0) {
            return emptyMap()
        }

        return root.fields().asSequence()
            .associate { (key, value) -> key to headerValueToString(value) }
    }

    /**
     * Merges generated headers with user custom headers, letting user values win.
     */
    fun mergeHeaders(defaultHeaders: Map<String, String>, customHeadersJson: String): Map<String, String> =
        LinkedHashMap<String, String>().apply {
            putAll(defaultHeaders)
            putAll(parseCustomHeaders(customHeadersJson))
        }

    /**
     * Converts JSON header values to string values accepted by HttpURLConnection.
     */
    private fun headerValueToString(value: JsonNode): String =
        when {
            value.isNull -> ""
            value.isTextual -> value.asText()
            value.isContainerNode -> value.toString()
            else -> value.asText()
        }
}
