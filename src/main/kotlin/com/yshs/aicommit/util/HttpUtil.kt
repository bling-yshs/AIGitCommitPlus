package com.yshs.aicommit.util

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

object HttpUtil {
    val objectMapper: ObjectMapper = ObjectMapper()

    fun openConnection(
        url: String,
        method: String,
        accept: String,
        headers: Map<String, String>,
        contentType: String? = null,
        body: String? = null,
        connectTimeoutMillis: Int = 30_000,
        readTimeoutMillis: Int = 30_000,
    ): HttpURLConnection {
        val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.setRequestProperty("Accept", accept)
        contentType?.let { connection.setRequestProperty("Content-Type", it) }
        headers.forEach(connection::setRequestProperty)
        connection.connectTimeout = connectTimeoutMillis
        connection.readTimeout = readTimeoutMillis

        if (body != null) {
            connection.doOutput = true
            connection.outputStream.use { output ->
                output.write(body.toByteArray(StandardCharsets.UTF_8))
            }
        }

        return connection
    }

    fun readBody(connection: HttpURLConnection, useErrorStream: Boolean): String {
        val stream =
            when {
                useErrorStream -> connection.errorStream
                else -> connection.inputStream
            } ?: return ""

        val charset = Charset.forName(CommonUtil.getCharsetFromContentType(connection.contentType))
        return BufferedReader(InputStreamReader(stream, charset)).use { reader ->
            buildString {
                var line = reader.readLine()
                while (line != null) {
                    append(line)
                    line = reader.readLine()
                }
            }
        }
    }

    fun readEventStream(
        connection: HttpURLConnection,
        charset: Charset = StandardCharsets.UTF_8,
        onLine: (String) -> Unit,
    ) {
        BufferedReader(InputStreamReader(connection.inputStream, charset)).use { reader ->
            var line = reader.readLine()
            while (line != null) {
                onLine(line)
                line = reader.readLine()
            }
        }
    }

    inline fun <T> useConnection(connection: HttpURLConnection, block: (HttpURLConnection) -> T): T =
        try {
            block(connection)
        } finally {
            connection.disconnect()
        }
}
