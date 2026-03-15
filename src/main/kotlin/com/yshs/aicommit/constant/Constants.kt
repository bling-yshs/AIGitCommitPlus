package com.yshs.aicommit.constant

import com.yshs.aicommit.config.ApiKeySettings

object Constants {
    const val NO_FILE_SELECTED = "No file selected"
    const val GENERATING_COMMIT_MESSAGE = "Generating commit message..."
    const val TASK_TITLE = "Generating commit message"

    @JvmField
    val languages = arrayOf(
        "English",
        "简体中文",
        "日本语",
        "한국어",
        "Français",
        "Español",
        "Deutsch",
        "Русский",
        "العربية",
        "Português",
    )

    val languageDisplayNames = mapOf(
        "English" to "English",
        "Chinese" to "简体中文",
        "Japanese" to "日本语",
        "Korean" to "한국어",
        "French" to "Français",
        "Spanish" to "Español",
        "German" to "Deutsch",
        "Russian" to "Русский",
        "Arabic" to "العربية",
        "Portuguese" to "Português",
    )

    const val PROJECT_PROMPT_FILE_NAME = "commit-prompt.txt"
    const val PROJECT_PROMPT = "Project Prompt"
    const val CUSTOM_PROMPT = "Custom Prompt"

    val defaultExcludePatterns = listOf(
        "*.pb.go",
        "*.pb.cc",
        "*.pb.h",
        "go.sum",
        "go.mod",
        "package-lock.json",
        "yarn.lock",
        "pnpm-lock.yaml",
        "Cargo.lock",
        "Pipfile.lock",
        "poetry.lock",
        "*.generated.*",
        "*.gen.*",
        "*_generated.*",
        "*_gen.*",
        "vendor/**",
        "node_modules/**",
        ".next/**",
        "dist/**",
        "build/**",
        "target/**",
        "*.min.js",
        "*.min.css",
        "*.bundle.*",
        "*.chunk.*",
        "coverage/**",
        ".nyc_output/**",
        "*.lcov",
        "*.log",
        "*.tmp",
        "*.temp",
        ".DS_Store",
        "Thumbs.db",
        "*.swp",
        "*.swo",
        "*~",
    )

    val defaultExcludePatternsText: String
        get() = defaultExcludePatterns.joinToString("\n")

    const val EXCLUDE_PATTERNS_HELP_TEXT =
        "<html>" +
            "<b>File Exclusion Rules:</b><br/>" +
            "• Supports wildcard patterns, e.g., *.pb.go matches all .pb.go files<br/>" +
            "• Supports directory patterns, e.g., vendor/** matches all files in vendor directory<br/>" +
            "• One rule per line, empty lines and lines starting with # are ignored<br/>" +
            "• Common generated files and dependency files are pre-configured, adjust as needed<br/>" +
            "<br/>" +
            "<b>Pre-configured rules include:</b><br/>" +
            "• Protocol Buffer generated files (*.pb.go, *.pb.cc, etc.)<br/>" +
            "• Dependency lock files (go.sum, package-lock.json, etc.)<br/>" +
            "• Build output directories (dist/, build/, target/, etc.)<br/>" +
            "• Compressed and bundled files (*.min.js, *.bundle.*, etc.)<br/>" +
            "• System and temporary files (.DS_Store, *.log, etc.)<br/>" +
            "</html>"

    const val OPENAI = "OpenAI"
    const val OPENAI_RESPONSE = "OpenAI-Response"
    const val GEMINI = "Gemini"
    const val ANTHROPIC = "Anthropic"

    @JvmField
    val OpenAI = OPENAI

    @JvmField
    val OpenAI_Response = OPENAI_RESPONSE

    @JvmField
    val Gemini = GEMINI

    @JvmField
    val Anthropic = ANTHROPIC

    @JvmField
    val LLM_CLIENTS = arrayOf(OPENAI, OPENAI_RESPONSE, GEMINI, ANTHROPIC)

    @JvmField
    val DEFAULT_EXCLUDE_PATTERNS = defaultExcludePatterns.toTypedArray()

    @JvmField
    val DEFAULT_EXCLUDE_PATTERNS_TEXT = defaultExcludePatternsText

    @JvmField
    val moduleConfigs = linkedMapOf(
        OPENAI to defaultModuleConfig(OPENAI),
        OPENAI_RESPONSE to defaultModuleConfig(OPENAI_RESPONSE),
        GEMINI to defaultModuleConfig(GEMINI),
        ANTHROPIC to defaultModuleConfig(ANTHROPIC),
    )

    fun promptTypes(): Array<String> = arrayOf(PROJECT_PROMPT, CUSTOM_PROMPT)

    @JvmStatic
    fun getAllPromptTypes(): Array<String> = promptTypes()

    fun defaultModuleConfig(client: String): ApiKeySettings.ModuleConfig =
        when (client) {
            OPENAI -> ApiKeySettings.ModuleConfig(url = "https://api.openai.com/v1/chat/completions")
            OPENAI_RESPONSE -> ApiKeySettings.ModuleConfig(url = "https://api.openai.com/v1/responses")
            GEMINI -> ApiKeySettings.ModuleConfig(url = "https://generativelanguage.googleapis.com/v1beta/models")
            ANTHROPIC -> ApiKeySettings.ModuleConfig(url = "https://api.anthropic.com/v1/messages")
            else -> ApiKeySettings.ModuleConfig()
        }

    val clientHelpUrls = mapOf(
        OPENAI to "https://platform.openai.com/docs/overview",
        OPENAI_RESPONSE to "https://platform.openai.com/docs/api-reference/responses",
        GEMINI to "https://ai.google.dev/gemini-api/docs",
        ANTHROPIC to "https://docs.anthropic.com/en/api/overview",
    )

    @JvmField
    val CLIENT_HELP_URLS = clientHelpUrls

    fun helpText(client: String): String =
        when (client) {
            OPENAI -> "<html><li>Use the Chat Completions protocol.</li><li>You can paste either the full endpoint or a base URL ending in /v1.</li><li>Refresh models from the network, or type a custom model manually.</li></html>"
            OPENAI_RESPONSE -> "<html><li>Use the OpenAI Responses protocol.</li><li>You can paste either the full /v1/responses endpoint or a base URL ending in /v1.</li><li>Refresh models from the network, or type a custom model manually.</li></html>"
            GEMINI -> "<html><li>Get your API key from <a href='https://aistudio.google.com/app/apikey'>Google AI Studio</a>.</li><li>The URL should point to the Gemini models collection endpoint.</li><li>Refresh models from the network, or type a custom model manually.</li></html>"
            ANTHROPIC -> "<html><li>Use the Anthropic Messages protocol.</li><li>You can paste either the full /v1/messages endpoint or a base URL ending in /v1.</li><li>Refresh models from the network, or type a custom model manually.</li></html>"
            else -> ""
        }

    @JvmStatic
    fun getHelpText(client: String): String = helpText(client)

    fun normalizeLanguageName(language: String?): String {
        if (language.isNullOrBlank()) {
            return "English"
        }
        return languageDisplayNames[language] ?: language
    }
}
