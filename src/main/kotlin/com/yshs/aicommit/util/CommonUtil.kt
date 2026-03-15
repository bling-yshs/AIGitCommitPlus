package com.yshs.aicommit.util

import java.nio.charset.StandardCharsets

object CommonUtil {
    fun getCharsetFromContentType(contentType: String?): String {
        if (!contentType.isNullOrBlank()) {
            for (value in contentType.split(';')) {
                val trimmed = value.trim()
                if (trimmed.startsWith("charset=", ignoreCase = true)) {
                    return trimmed.substringAfter('=')
                }
            }
        }
        return StandardCharsets.UTF_8.name()
    }
}
