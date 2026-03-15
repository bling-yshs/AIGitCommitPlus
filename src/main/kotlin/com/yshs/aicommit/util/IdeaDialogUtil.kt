package com.yshs.aicommit.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

object IdeaDialogUtil {
    fun showWarning(project: Project?, message: String, title: String) {
        ApplicationManager.getApplication().invokeLater {
            Messages.showWarningDialog(project, message, title)
        }
    }

    fun showError(project: Project?, message: String, title: String) {
        ApplicationManager.getApplication().invokeLater {
            Messages.showErrorDialog(project, message, title)
        }
    }

    fun handleModuleNecessaryConfigIsWrong(project: Project?) {
        Messages.showWarningDialog(project, "Please check the necessary configuration.", "Necessary Configuration Error")
    }

    fun handleNoChangesSelected(project: Project?) {
        Messages.showWarningDialog(project, "No changes selected. Please select files to commit.", "No Changes Selected")
    }

    fun handleGenerationError(project: Project?, errorMessage: String) {
        showError(project, "Error generating commit message: $errorMessage", "Error")
    }
}
