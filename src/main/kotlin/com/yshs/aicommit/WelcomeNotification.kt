package com.yshs.aicommit

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class WelcomeNotification : ProjectActivity {
    override suspend fun execute(project: Project) {
        when {
            isFirstInstall() -> {
                showWelcomeNotification(project, isFirstInstall = true)
                updateStoredVersion()
            }
            isUpdatedVersion() -> {
                showWelcomeNotification(project, isFirstInstall = false)
                updateStoredVersion()
            }
        }
    }

    private fun isFirstInstall(): Boolean =
        PropertiesComponent.getInstance().getValue(PLUGIN_VERSION_PROPERTY) == null

    private fun isUpdatedVersion(): Boolean {
        val storedVersion = PropertiesComponent.getInstance().getValue(PLUGIN_VERSION_PROPERTY)
        val currentVersion = currentPluginVersion()
        return storedVersion != null && storedVersion != currentVersion
    }

    private fun updateStoredVersion() {
        PropertiesComponent.getInstance().setValue(PLUGIN_VERSION_PROPERTY, currentPluginVersion())
    }

    private fun currentPluginVersion(): String =
        PluginManagerCore.getPlugin(PluginId.getId("com.yshs.aicommit"))?.version.orEmpty()

    private fun showWelcomeNotification(project: Project, isFirstInstall: Boolean) {
        val title = if (isFirstInstall) WELCOME_TITLE_INSTALL else WELCOME_TITLE_UPDATE
        val content = if (isFirstInstall) WELCOME_CONTENT_INSTALL else WELCOME_CONTENT_UPDATE

        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(title, content, NotificationType.INFORMATION)
            .addAction(ConfigureAction())
            .notify(project)
    }

    private class ConfigureAction : AnAction("Configure") {
        override fun actionPerformed(e: AnActionEvent) {
            ShowSettingsUtil.getInstance().showSettingsDialog(e.project, PLUGIN_NAME)
        }
    }

    private companion object {
        const val NOTIFICATION_GROUP_ID = "AI Git Commit Plus Notifications"
        const val PLUGIN_NAME = "AI Git Commit Plus"
        const val WELCOME_TITLE_INSTALL = "Welcome to AI Git Commit Plus!"
        const val WELCOME_CONTENT_INSTALL =
            "Thank you for installing AI Git Commit Plus. To get started, please configure the plugin in the settings."
        const val WELCOME_TITLE_UPDATE = "AI Git Commit Plus Updated!"
        const val WELCOME_CONTENT_UPDATE =
            "AI Git Commit Plus has been updated to a new version. Check out the latest features and improvements in the settings."
        const val PLUGIN_VERSION_PROPERTY = "com.yshs.aicommit.version"
    }
}
