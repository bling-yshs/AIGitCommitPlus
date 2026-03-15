package com.yshs.aicommit.util

import com.yshs.aicommit.config.ApiKeySettings
import com.yshs.aicommit.constant.Constants
import com.intellij.openapi.project.Project

object PromptUtil {
    val DEFAULT_PROMPT_1: String = """
        Generate git commit message in {language}. Use conventional format:

        <type>(<scope>): <subject>

        Types: feat|fix|docs|style|refactor|test|chore
        Subject: ≤50 chars, imperative mood
        Body: optional, explain why/what

        Code changes:
        {diff}
    """.trimIndent()

    val DEFAULT_PROMPT_2: String = """
        Generate a concise yet detailed git commit message using the following format and information:

        <type>(<scope>): <subject>

        <body>

        <footer>

        Use the following placeholders in your analysis:
        - diff begin ：
        {diff}
        - diff end.

        Guidelines:

        1. <type>: Commit type (required)
           - Use standard types: feat, fix, docs, style, refactor, perf, test, chore

        2. <scope>: Area of impact (required)
           - Briefly mention the specific component or module affected

        3. <subject>: Short description (required)
           - Summarize the main change in one sentence (max 50 characters)
           - Use the imperative mood, e.g., "add" not "added" or "adds"
           - Don't capitalize the first letter
           - No period at the end

        4. <body>: Detailed description (required)
           - Explain the motivation for the change
           - Describe the key modifications (max 3 bullet points)
           - Mention any important technical details
           - Use the imperative mood

        5. <footer>: (optional)
           - Note any breaking changes
           - Reference related issues or PRs

        Notes:
        - Keep the entire message under 300 characters
        - Focus on what and why, not how
        - Summarize diff to highlight key changes; don't include raw diff output

        Note: The whole result should be given in {language} and the final result must not contain ‘```’
    """.trimIndent()

    val DEFAULT_PROMPT_3: String = """
        You are a Git commit message generation expert. Please analyze the following code changes and generate a clear, standardized commit message in {language}.

        Code changes:
        {diff}

        Requirements for the commit message:
        1. First line should start with one of these types:
           feat: (new feature)
           fix: (bug fix)
           docs: (documentation)
           style: (formatting)
           refactor: (code refactoring)
           perf: (performance)
           test: (testing)
           chore: (maintenance)

        2. First line should be no longer than 72 characters

        3. After the first line, leave one blank line and provide detailed explanation if needed

        4. Use present tense

        Please output only the commit message, without any additional explanations.
    """.trimIndent()

    val EMOJI: String = """
        Format: [EMOJI] [TYPE](scope): [description in {language}]
        GitMoji: ✨feat 🐛fix 📝docs 💄style ♻️refactor ⚡perf ✅test 🔧chore
        Max 120 chars, present tense.

        {diff}
    """.trimIndent()

    val CONVENTIONAL: String = """
        Generate conventional commit message in {language}.
        Format: type(scope): description
        Explain what and why, not how.

        {diff}
    """.trimIndent()

    fun constructPrompt(project: Project, diff: String): String {
        val settings = ApiKeySettings.getInstance()
        val promptContent =
            if (settings.promptType == Constants.PROJECT_PROMPT) {
                FileUtil.loadProjectPrompt(project)
            } else {
                settings.customPrompt.prompt
            }

        require("{diff}" in promptContent) {
            "The prompt file must contain the placeholder {diff}."
        }
        require(
            settings.promptType == Constants.PROJECT_PROMPT || "{language}" in promptContent,
        ) {
            "The prompt file must contain the placeholder {language}."
        }

        return promptContent
            .replace("{language}", settings.commitLanguage)
            .replace("{diff}", diff) +
            "\n\nNote: Output the result in plain text format, do not include any markdown formatting"
    }
}
