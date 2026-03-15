package com.yshs.aicommit.util

import com.yshs.aicommit.constant.Constants
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path

object FileUtil {
    fun readFile(filePath: String): String = Files.readString(Path.of(filePath))

    fun loadProjectPrompt(project: Project?): String {
        requireNotNull(project) { "No project provided." }
        val basePath = project.basePath ?: error("No project base path available.")
        val promptFile = Path.of(basePath, Constants.PROJECT_PROMPT_FILE_NAME)
        require(Files.exists(promptFile)) {
            "No ${Constants.PROJECT_PROMPT_FILE_NAME} file found in the project root directory : $basePath"
        }

        val content = readFile(promptFile.toString())
        require(content.isNotBlank()) { "No content found in the project prompt file." }
        return content
    }
}
