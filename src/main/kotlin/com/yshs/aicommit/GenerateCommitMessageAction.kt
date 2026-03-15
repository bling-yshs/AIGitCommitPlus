package com.yshs.aicommit

import com.yshs.aicommit.constant.Constants
import com.yshs.aicommit.service.CommitMessageService
import com.yshs.aicommit.util.DialogUtil
import com.yshs.aicommit.util.GitUtil
import com.yshs.aicommit.util.IdeaDialogUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.openapi.wm.WindowManager
import com.intellij.vcs.commit.AbstractCommitWorkflowHandler
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.Icon
import javax.swing.Timer

class GenerateCommitMessageAction : AnAction() {
    private val messageBuilder = StringBuilder()
    private val isGenerating = AtomicBoolean(false)
    private val originalIcon: Icon = IconLoader.getIcon("/icons/git-commit-logo.svg", javaClass)
    private val progressIcons = arrayOf(
        AllIcons.Process.Step_1,
        AllIcons.Process.Step_2,
        AllIcons.Process.Step_3,
        AllIcons.Process.Step_4,
        AllIcons.Process.Step_5,
        AllIcons.Process.Step_6,
        AllIcons.Process.Step_7,
        AllIcons.Process.Step_8,
    )

    private var iconAnimationTimer: Timer? = null
    private var currentIconIndex: Int = 0

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        if (isGenerating.get()) {
            return
        }

        val commitMessage = e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL) as? CommitMessage ?: return
        val commitWorkflowHandler = e.getData(VcsDataKeys.COMMIT_WORKFLOW_HANDLER) as? AbstractCommitWorkflowHandler<*, *>
        if (commitWorkflowHandler == null) {
            IdeaDialogUtil.handleNoChangesSelected(project)
            return
        }

        startIconAnimation(e)

        val commitMessageService = CommitMessageService()
        if (!commitMessageService.checkNecessaryModuleConfigIsRight()) {
            stopIconAnimation(e)
            IdeaDialogUtil.handleModuleNecessaryConfigIsWrong(project)
            return
        }

        val includedChanges = commitWorkflowHandler.ui.getIncludedChanges()
        val includedUnversionedFiles = commitWorkflowHandler.ui.getIncludedUnversionedFiles()
        if (includedChanges.isEmpty() && includedUnversionedFiles.isEmpty()) {
            commitMessage.setCommitMessage(Constants.NO_FILE_SELECTED)
            stopIconAnimation(e)
            return
        }

        commitMessage.setCommitMessage(Constants.GENERATING_COMMIT_MESSAGE)

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, Constants.TASK_TITLE, true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val diff = GitUtil.getSelectedFilesDiff(includedChanges, includedUnversionedFiles, project)
                    if (commitMessageService.generateByStream()) {
                        messageBuilder.setLength(0)
                        commitMessageService.generateCommitMessageStream(
                            project = project,
                            diff = diff,
                            onNext = { token ->
                                ApplicationManager.getApplication().invokeLater {
                                    messageBuilder.append(token)
                                    commitMessage.setCommitMessage(messageBuilder.toString())
                                }
                            },
                            onError = {
                                stopIconAnimation(e)
                            },
                            onComplete = {
                                stopIconAnimation(e)
                            },
                        )
                    } else {
                        val generated = commitMessageService.generateCommitMessage(project, diff).trim()
                        ApplicationManager.getApplication().invokeLater {
                            commitMessage.setCommitMessage(generated)
                            stopIconAnimation(e)
                        }
                    }
                } catch (throwable: Throwable) {
                    stopIconAnimation(e)
                    ApplicationManager.getApplication().invokeLater {
                        DialogUtil.showErrorDialog(
                            WindowManager.getInstance().getFrame(project),
                            throwable.message,
                            DialogUtil.GENERATE_COMMIT_MESSAGE_ERROR_TITLE,
                        )
                    }
                }
            }

            override fun onFinished() {
                stopIconAnimation(e)
            }
        })
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val enabled = project != null && GitUtil.isGitRepository(project)
        e.presentation.isEnabledAndVisible = enabled
        if (!isGenerating.get()) {
            e.presentation.icon = originalIcon
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private fun startIconAnimation(event: AnActionEvent) {
        iconAnimationTimer?.stop()
        currentIconIndex = 0
        isGenerating.set(true)
        iconAnimationTimer = Timer(100) {
            if (isGenerating.get()) {
                currentIconIndex = (currentIconIndex + 1) % progressIcons.size
                event.presentation.icon = progressIcons[currentIconIndex]
            }
        }.apply { start() }
    }

    private fun stopIconAnimation(event: AnActionEvent) {
        ApplicationManager.getApplication().invokeLater {
            iconAnimationTimer?.stop()
            iconAnimationTimer = null
            isGenerating.set(false)
            event.presentation.icon = originalIcon
        }
    }
}
