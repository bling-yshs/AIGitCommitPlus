package com.yshs.aicommit.service

import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap

object LastPromptService {
    private val lastPromptMap = ConcurrentHashMap<Project, String>()

    @JvmStatic
    fun setLastPrompt(project: Project?, prompt: String?) {
        if (project == null || prompt.isNullOrBlank()) {
            return
        }
        lastPromptMap[project] = prompt
    }

    @JvmStatic
    fun getLastPrompt(project: Project?): String? = project?.let(lastPromptMap::get)
}
