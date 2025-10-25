package com.github.apkdownloader.ai

import android.content.Context
import android.content.SharedPreferences
import java.io.File

/**
 * Project configuration manager
 * Supports external storage locations like /storage/emulated/0/Download
 */
class ProjectConfig(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "ai_build_config",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_PROJECT_PATH = "project_path"
        private const val KEY_DEFAULT_BRANCH = "default_branch"
        private const val KEY_SELECTED_REPO = "selected_repo"
        private const val KEY_SELECTED_REPO_OWNER = "selected_repo_owner"
        private const val KEY_SELECTED_REPO_NAME = "selected_repo_name"
        private const val KEY_USE_GEMINI_FIRST = "use_gemini_first"
        private const val KEY_FALLBACK_TO_CLAUDE = "fallback_to_claude"
        private const val KEY_AUTO_INSTALL = "auto_install"
        private const val KEY_CREATE_PR = "create_pr_on_fix"
        private const val KEY_MAX_RETRIES = "max_retries"

        // Default external storage path
        const val DEFAULT_EXTERNAL_PATH = "/storage/emulated/0/Download/GitHubProjects"

        // Common project locations
        val COMMON_LOCATIONS = listOf(
            "/storage/emulated/0/Download",
            "/storage/emulated/0/Documents",
            "/storage/emulated/0/Projects",
            "/data/data/com.termux/files/home"
        )
    }

    /**
     * Get current project path
     * Defaults to external storage for easy access
     */
    fun getProjectPath(): String {
        return prefs.getString(KEY_PROJECT_PATH, DEFAULT_EXTERNAL_PATH)
            ?: DEFAULT_EXTERNAL_PATH
    }

    /**
     * Set project path
     */
    fun setProjectPath(path: String) {
        prefs.edit().putString(KEY_PROJECT_PATH, path).apply()
    }

    /**
     * Get default branch
     */
    fun getDefaultBranch(): String {
        return prefs.getString(KEY_DEFAULT_BRANCH, "master") ?: "master"
    }

    /**
     * Set default branch
     */
    fun setDefaultBranch(branch: String) {
        prefs.edit().putString(KEY_DEFAULT_BRANCH, branch).apply()
    }

    /**
     * Get selected repository (full name like "owner/repo")
     */
    fun getSelectedRepository(): String? {
        return prefs.getString(KEY_SELECTED_REPO, null)
    }

    /**
     * Get selected repository owner
     */
    fun getSelectedRepositoryOwner(): String? {
        return prefs.getString(KEY_SELECTED_REPO_OWNER, null)
    }

    /**
     * Get selected repository name
     */
    fun getSelectedRepositoryName(): String? {
        return prefs.getString(KEY_SELECTED_REPO_NAME, null)
    }

    /**
     * Set selected repository
     */
    fun setSelectedRepository(owner: String, name: String) {
        prefs.edit().apply {
            putString(KEY_SELECTED_REPO, "$owner/$name")
            putString(KEY_SELECTED_REPO_OWNER, owner)
            putString(KEY_SELECTED_REPO_NAME, name)
            apply()
        }
    }

    /**
     * Clear selected repository
     */
    fun clearSelectedRepository() {
        prefs.edit().apply {
            remove(KEY_SELECTED_REPO)
            remove(KEY_SELECTED_REPO_OWNER)
            remove(KEY_SELECTED_REPO_NAME)
            apply()
        }
    }

    /**
     * Get AI build configuration
     */
    fun getAiBuildConfig(): AiBuildConfig {
        return AiBuildConfig(
            useGeminiFirst = prefs.getBoolean(KEY_USE_GEMINI_FIRST, true),
            fallbackToClaude = prefs.getBoolean(KEY_FALLBACK_TO_CLAUDE, true),
            maxRetries = prefs.getInt(KEY_MAX_RETRIES, 3),
            autoInstall = prefs.getBoolean(KEY_AUTO_INSTALL, true),
            createPrOnFix = prefs.getBoolean(KEY_CREATE_PR, true)
        )
    }

    /**
     * Save AI build configuration
     */
    fun saveAiBuildConfig(config: AiBuildConfig) {
        prefs.edit().apply {
            putBoolean(KEY_USE_GEMINI_FIRST, config.useGeminiFirst)
            putBoolean(KEY_FALLBACK_TO_CLAUDE, config.fallbackToClaude)
            putInt(KEY_MAX_RETRIES, config.maxRetries)
            putBoolean(KEY_AUTO_INSTALL, config.autoInstall)
            putBoolean(KEY_CREATE_PR, config.createPrOnFix)
            apply()
        }
    }

    /**
     * Check if project path exists
     */
    fun projectPathExists(): Boolean {
        val path = getProjectPath()
        return File(path).exists()
    }

    /**
     * Create project directory if it doesn't exist
     */
    fun ensureProjectDirectory(): Boolean {
        val path = getProjectPath()
        val dir = File(path)

        return if (!dir.exists()) {
            dir.mkdirs()
        } else {
            true
        }
    }

    /**
     * Get list of available projects in the configured path
     */
    fun getAvailableProjects(): List<File> {
        val path = getProjectPath()
        val dir = File(path)

        if (!dir.exists()) {
            return emptyList()
        }

        return dir.listFiles { file ->
            file.isDirectory && File(file, ".git").exists()
        }?.toList() ?: emptyList()
    }

    /**
     * Clone a repository to external storage
     */
    suspend fun cloneRepository(
        repoUrl: String,
        projectName: String
    ): Result<String> {
        return try {
            ensureProjectDirectory()

            val targetPath = File(getProjectPath(), projectName)
            if (targetPath.exists()) {
                return Result.failure(Exception("Project already exists"))
            }

            val process = ProcessBuilder(
                "git", "clone", repoUrl, targetPath.absolutePath
            ).start()

            val exitCode = process.waitFor()
            if (exitCode == 0) {
                Result.success(targetPath.absolutePath)
            } else {
                Result.failure(Exception("Git clone failed"))
            }

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get storage usage info
     */
    fun getStorageInfo(): StorageInfo {
        val path = getProjectPath()
        val file = File(path)

        return StorageInfo(
            path = path,
            exists = file.exists(),
            isWritable = file.canWrite(),
            freeSpace = file.freeSpace,
            totalSpace = file.totalSpace
        )
    }
}

/**
 * Storage information
 */
data class StorageInfo(
    val path: String,
    val exists: Boolean,
    val isWritable: Boolean,
    val freeSpace: Long,
    val totalSpace: Long
) {
    val freeSpaceGB: Double
        get() = freeSpace / (1024.0 * 1024.0 * 1024.0)

    val totalSpaceGB: Double
        get() = totalSpace / (1024.0 * 1024.0 * 1024.0)

    val usedSpacePercent: Int
        get() = ((totalSpace - freeSpace).toDouble() / totalSpace * 100).toInt()
}
