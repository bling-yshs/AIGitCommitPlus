package com.yshs.aicommit.service

import org.apache.commons.lang3.tuple.Pair

interface AIService {
    val supportsStreaming: Boolean

    fun generateCommitMessage(content: String): String

    fun generateCommitMessageStream(
        content: String,
        onNext: (String) -> Unit,
        onError: (Throwable) -> Unit,
        onComplete: () -> Unit,
    ) {
        try {
            generateCommitMessage(content)
                .takeIf(String::isNotBlank)
                ?.let(onNext)
            onComplete()
        } catch (throwable: Throwable) {
            onError(throwable)
            throw throwable
        }
    }

    fun checkNecessaryModuleConfigIsRight(): Boolean

    fun validateConfig(config: Map<String, String>): Pair<Boolean, String>
}
