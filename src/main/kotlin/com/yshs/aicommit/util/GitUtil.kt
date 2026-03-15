package com.yshs.aicommit.util

import com.yshs.aicommit.config.ApiKeySettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.vcsUtil.VcsUtil
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.config.GitExecutableManager
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import java.io.IOException
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.PathMatcher
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

object GitUtil {
    private val pathMatcherCache = ConcurrentHashMap<String, PathMatcher?>()

    fun shouldExcludeFile(filePath: String): Boolean {
        val settings = ApiKeySettings.getInstance()
        if (!settings.enableFileExclusion) {
            return false
        }

        val patterns = settings.resolvedExcludePatterns()
        if (patterns.isEmpty()) {
            return false
        }

        val normalizedPath = filePath.replace('\\', '/')
        val path = Paths.get(normalizedPath)
        val fileName = path.fileName?.toString().orEmpty()

        for (pattern in patterns) {
            val trimmedPattern = pattern.trim()
            if (trimmedPattern.isBlank()) {
                continue
            }

            try {
                if (matchesFileName(fileName, trimmedPattern) ||
                    matchesPathPattern(normalizedPath, trimmedPattern) ||
                    matchesGlobPattern(normalizedPath, trimmedPattern)
                ) {
                    return true
                }
            } catch (_: Exception) {
            }
        }

        return false
    }

    fun computeDiff(includedChanges: List<Change>, unversionedFiles: List<FilePath>, project: Project): String {
        val diffBuilder = StringBuilder()
        val filteredChanges = includedChanges.filterNot { change ->
            getFilePathFromChange(change)?.let(::shouldExcludeFile) == true
        }
        val filteredUnversionedFiles = unversionedFiles.filterNot { shouldExcludeFile(it.path) }

        diffBuilder.append(computeDiff(filteredChanges, project))
        for (file in filteredUnversionedFiles) {
            val virtualFile = file.virtualFile
            diffBuilder.append(
                if (virtualFile != null && virtualFile.fileType.isBinary) {
                    buildBinaryUnversionedFileContent(file, project)
                } else {
                    buildUnversionedFileContent(file, project)
                },
            )
        }
        return diffBuilder.toString()
    }

    fun computeDiff(includedChanges: List<Change>, project: Project): String {
        val repositoryManager = GitRepositoryManager.getInstance(project)
        val filteredChanges = includedChanges.filterNot { change ->
            getFilePathFromChange(change)?.let(::shouldExcludeFile) == true
        }
        val pathsByRepository = linkedMapOf<GitRepository, LinkedHashSet<FilePath>>()

        for (change in filteredChanges) {
            val changePaths = getTrackedFilePaths(change)
            if (changePaths.isEmpty()) {
                continue
            }

            val repositories = changePaths
                .mapNotNull(repositoryManager::getRepositoryForFile)
                .distinct()

            for (repository in repositories) {
                val repositoryPaths = pathsByRepository.getOrPut(repository) { linkedSetOf() }
                changePaths
                    .filter { isPathInsideRepository(it, repository) }
                    .forEach(repositoryPaths::add)
            }
        }

        val diffBuilder = StringBuilder()
        for ((repository, paths) in pathsByRepository) {
            if (paths.isEmpty()) {
                continue
            }
            diffBuilder.append(runNativeGitDiff(project, repository, paths.toList()))
        }
        return diffBuilder.toString()
    }

    fun getSelectedFilesDiff(includedChanges: List<Change>, unversionedFiles: List<FilePath>, project: Project): String {
        val diff = computeDiff(includedChanges, unversionedFiles, project)
        require(diff.isNotBlank()) {
            "No Git diff content was generated for the selected files. Check whether all selected files were excluded by the file filtering rules."
        }
        return diff
    }

    fun isGitRepository(project: Project?): Boolean {
        project ?: return false
        return GitRepositoryManager.getInstance(project).repositories.isNotEmpty()
    }

    private fun matchesFileName(fileName: String, pattern: String): Boolean {
        if ('/' in pattern) {
            return false
        }
        return matchesSimplePattern(fileName, pattern)
    }

    private fun matchesPathPattern(filePath: String, pattern: String): Boolean =
        when {
            filePath == pattern -> true
            pattern.endsWith('/') -> {
                val directory = pattern.removeSuffix("/")
                filePath == directory || filePath.startsWith("$directory/")
            }
            else -> filePath.contains(pattern)
        }

    private fun matchesGlobPattern(filePath: String, pattern: String): Boolean {
        val matcher =
            pathMatcherCache.computeIfAbsent(pattern) {
                try {
                    FileSystems.getDefault().getPathMatcher("glob:$pattern")
                } catch (_: Exception) {
                    null
                }
            } ?: return false

        val path = Paths.get(filePath)
        return matcher.matches(path) || matcher.matches(path.fileName)
    }

    private fun matchesSimplePattern(text: String, pattern: String): Boolean {
        if (pattern == "*") {
            return true
        }
        val regex = pattern.replace(".", "\\.").replace("*", ".*").replace("?", ".")
        return runCatching { Regex(regex).matches(text) }.getOrDefault(false)
    }

    private fun getFilePathFromChange(change: Change): String? =
        change.virtualFile?.path
            ?: change.beforeRevision?.file?.path
            ?: change.afterRevision?.file?.path

    private fun getTrackedFilePaths(change: Change): List<FilePath> {
        val paths = linkedMapOf<String, FilePath>()
        change.beforeRevision?.file?.let { paths.putIfAbsent(it.path, it) }
        change.afterRevision?.file?.let { paths.putIfAbsent(it.path, it) }

        if (paths.isEmpty()) {
            change.virtualFile?.let { virtualFile ->
                val filePath = VcsUtil.getFilePath(virtualFile)
                paths.putIfAbsent(filePath.path, filePath)
            }
        }

        return paths.values.toList()
    }

    private fun runNativeGitDiff(project: Project, repository: GitRepository, paths: List<FilePath>): String {
        if (paths.isEmpty()) {
            return ""
        }

        val executableManager = GitExecutableManager.getInstance()
        val executablePath = executableManager.getPathToGit(project)
        require(executablePath.isNotBlank()) {
            "Git executable is not configured for project ${project.name}."
        }

        val executable = executableManager.getExecutable(project, File(repository.root.path))
        val handler = GitLineHandler(project, File(repository.root.path), executable, GitCommand.DIFF, emptyList())
        handler.setSilent(true)
        handler.addParameters("--no-ext-diff")
        handler.endOptions()
        handler.addRelativePaths(paths)

        val result = Git.getInstance().runCommand(handler)
        if (!result.success()) {
            val errorMessage = result.getErrorOutputAsJoinedString().ifBlank {
                "git diff failed in ${repository.root.path} with exit code ${result.exitCode}."
            }
            throw IllegalStateException(errorMessage)
        }

        val output = result.getOutputAsJoinedString()
        if (output.isBlank()) {
            return ""
        }

        return buildString {
            append(output)
            if (!output.endsWith('\n')) {
                append('\n')
            }
        }
    }

    private fun buildUnversionedFileContent(unversionedFile: FilePath, project: Project): String {
        val diffPath = getRelativeDiffPath(unversionedFile, project)
        val content = try {
            Files.readString(Paths.get(unversionedFile.path))
        } catch (exception: IOException) {
            throw IllegalStateException("Failed to read untracked file content: $diffPath", exception)
        }

        return buildNewFileBlock(diffPath, content)
    }

    private fun buildBinaryUnversionedFileContent(unversionedFile: FilePath, project: Project): String {
        val diffPath = getRelativeDiffPath(unversionedFile, project)
        return buildNewFileBlock(diffPath, "[binary file skipped]")
    }

    private fun buildNewFileBlock(diffPath: String, content: String): String =
        buildString {
            append("new file: ").append(diffPath).append('\n')
            append("```\n")
            append(content)
            if (content.isNotEmpty() && !content.endsWith('\n')) {
                append('\n')
            }
            append("```\n\n")
        }

    private fun isPathInsideRepository(filePath: FilePath, repository: GitRepository): Boolean {
        val repositoryRoot = repository.root.toNioPath().toAbsolutePath().normalize()
        val file = Paths.get(filePath.path).toAbsolutePath().normalize()
        return file.startsWith(repositoryRoot)
    }

    private fun getRelativeDiffPath(filePath: FilePath, project: Project): String {
        val repository = GitRepositoryManager.getInstance(project).getRepositoryForFile(filePath)
        val absolutePath = filePath.path.replace('\\', '/')
        if (repository == null) {
            return absolutePath
        }

        val repositoryRoot = repository.root.toNioPath().toAbsolutePath().normalize()
        val file = Paths.get(filePath.path).toAbsolutePath().normalize()
        return runCatching { repositoryRoot.relativize(file).toString().replace('\\', '/') }
            .getOrDefault(absolutePath)
    }
}
