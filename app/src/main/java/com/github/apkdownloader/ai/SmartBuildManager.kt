package com.github.apkdownloader.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Smart Build Manager - Orchestrates the entire AI-powered build process
 */
class SmartBuildManager(
    private val context: Context,
    private val projectPath: String
) {

    private val aiBuildHelper = AiBuildHelper(context, projectPath)
    private val _buildStatus = MutableStateFlow<BuildStatus>(BuildStatus.Idle)
    val buildStatus: StateFlow<BuildStatus> = _buildStatus

    companion object {
        private const val TAG = "SmartBuildManager"
        private const val DEFAULT_BRANCH = "master"
    }

    /**
     * Main entry point: Smart Build with AI assistance
     */
    suspend fun smartBuild(
        branch: String = DEFAULT_BRANCH,
        config: AiBuildConfig = AiBuildConfig()
    ): BuildResult = withContext(Dispatchers.IO) {

        val startTime = System.currentTimeMillis()
        var fixAttempts = 0
        val previousFixes = mutableListOf<String>()

        try {
            // Step 1: Sync from GitHub
            _buildStatus.value = BuildStatus.Syncing
            Log.d(TAG, "📥 Syncing from GitHub...")
            val syncResult = gitPull(branch)
            if (!syncResult) {
                return@withContext BuildResult(
                    success = false,
                    errorLog = "Failed to sync from GitHub"
                )
            }

            // Step 2: Clean build if requested
            if (config.cleanBuild) {
                Log.d(TAG, "🧹 Cleaning build cache...")
                cleanBuild()
            }

            // Step 3: Attempt build (with AI auto-fix loop)
            var buildResult = BuildResult(success = false)

            while (!buildResult.success && fixAttempts < config.maxRetries) {
                _buildStatus.value = BuildStatus.Building
                Log.d(TAG, "🔨 Building APK (attempt ${fixAttempts + 1})...")

                buildResult = executeGradleBuild()

                if (buildResult.success) {
                    Log.d(TAG, "✅ Build successful!")
                    break
                }

                // Build failed - try AI fix
                Log.d(TAG, "❌ Build failed. Asking AI for help...")

                fixAttempts++
                val aiTier = if (fixAttempts == 1) AiTier.GEMINI else AiTier.CLAUDE
                _buildStatus.value = BuildStatus.AiFixing(aiTier, fixAttempts)

                val fixRequest = AiFixRequest(
                    errorLog = buildResult.errorLog ?: "Unknown error",
                    buildGradleContent = readBuildGradle(),
                    previousFixes = previousFixes
                )

                val fixResult = aiBuildHelper.fixBuildError(fixRequest, config)

                if (fixResult.success) {
                    Log.d(TAG, "💡 AI suggested fix: ${fixResult.description}")
                    previousFixes.add("${fixResult.aiUsed}: ${fixResult.description}")

                    if (fixResult.changesApplied) {
                        Log.d(TAG, "✍️ Changes applied by AI")
                        // Continue loop to rebuild
                    } else {
                        Log.d(TAG, "⚠️ Manual review needed for AI suggestion")
                        break
                    }
                } else {
                    Log.e(TAG, "❌ AI couldn't fix the error")
                    break
                }
            }

            val buildTime = System.currentTimeMillis() - startTime

            // Step 4: If fixed and successful, create PR
            if (buildResult.success && fixAttempts > 0 && config.createPrOnFix) {
                _buildStatus.value = BuildStatus.CreatingPr
                // PR creation will be handled by PrCreator
            }

            // Step 5: Install APK if successful
            if (buildResult.success && config.autoInstall) {
                buildResult.apkPath?.let { apkPath ->
                    installApk(apkPath)
                }
            }

            _buildStatus.value = if (buildResult.success) {
                BuildStatus.Success(buildResult.apkPath ?: "", buildTime)
            } else {
                BuildStatus.Failed(buildResult.errorLog ?: "Unknown error")
            }

            buildResult.copy(buildTime = buildTime)

        } catch (e: Exception) {
            Log.e(TAG, "Error during smart build: ${e.message}", e)
            _buildStatus.value = BuildStatus.Failed(e.message ?: "Unknown error")
            BuildResult(
                success = false,
                errorLog = e.toString()
            )
        }
    }

    /**
     * Execute gradle build
     */
    private suspend fun executeGradleBuild(): BuildResult = withContext(Dispatchers.IO) {
        try {
            val gradlePath = File(projectPath, "gradlew").absolutePath
            val commands = if (File(gradlePath).exists()) {
                arrayOf(gradlePath, "assembleDebug", "--stacktrace")
            } else {
                arrayOf("gradle", "assembleDebug", "--stacktrace")
            }

            val process = ProcessBuilder(*commands)
                .directory(File(projectPath))
                .redirectErrorStream(true)
                .start()

            val output = StringBuilder()
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.forEachLine { line ->
                    output.appendLine(line)
                    Log.d(TAG, line)
                }
            }

            val exitCode = process.waitFor()

            if (exitCode == 0) {
                // Find APK
                val apkFile = findGeneratedApk()
                BuildResult(
                    success = true,
                    apkPath = apkFile?.absolutePath
                )
            } else {
                // Extract error from output
                val errorLog = extractError(output.toString())
                BuildResult(
                    success = false,
                    errorLog = errorLog
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Build execution error: ${e.message}", e)
            BuildResult(
                success = false,
                errorLog = e.toString()
            )
        }
    }

    /**
     * Find generated APK file
     */
    private fun findGeneratedApk(): File? {
        val apkDir = File(projectPath, "app/build/outputs/apk/debug")
        return apkDir.listFiles()
            ?.firstOrNull { it.name.endsWith(".apk") }
    }

    /**
     * Extract relevant error from build output
     */
    private fun extractError(output: String): String {
        val lines = output.lines()
        val errorLines = mutableListOf<String>()

        var captureError = false
        for (line in lines) {
            if (line.contains("FAILURE:") || line.contains("ERROR:") ||
                line.contains("* What went wrong:")) {
                captureError = true
            }

            if (captureError) {
                errorLines.add(line)
                if (errorLines.size > 50) break // Limit error size
            }
        }

        return if (errorLines.isNotEmpty()) {
            errorLines.joinToString("\n")
        } else {
            output.takeLast(1000) // Last 1000 chars if no specific error found
        }
    }

    /**
     * Git pull from remote (with clone if needed)
     */
    private suspend fun gitPull(branch: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val projectDir = File(projectPath)

            // Check if project directory exists and is a git repository
            val gitDir = File(projectDir, ".git")

            if (!gitDir.exists()) {
                Log.d(TAG, "Repository not cloned yet. Need to clone first.")
                // Repository not cloned - need repository URL
                // This requires repository information from ProjectConfig
                Log.w(TAG, "⚠️ Git clone not implemented yet. Manual setup required.")
                return@withContext false
            }

            // Directory exists, do git pull
            Log.d(TAG, "Running git pull origin $branch in $projectPath")

            val process = ProcessBuilder(
                "git", "pull", "origin", branch
            )
                .directory(projectDir)
                .redirectErrorStream(true)
                .start()

            val output = StringBuilder()
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.forEachLine { line ->
                    output.appendLine(line)
                    Log.d(TAG, "git: $line")
                }
            }

            val exitCode = process.waitFor()

            if (exitCode == 0) {
                Log.d(TAG, "✅ Git pull successful")
                true
            } else {
                Log.e(TAG, "❌ Git pull failed: $output")
                false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Git pull error: ${e.message}", e)
            false
        }
    }

    /**
     * Clone repository from GitHub
     */
    suspend fun cloneRepository(repoUrl: String, targetBranch: String = "master"): Boolean = withContext(Dispatchers.IO) {
        try {
            val projectDir = File(projectPath)

            // Check if directory already exists
            if (projectDir.exists() && projectDir.listFiles()?.isNotEmpty() == true) {
                Log.d(TAG, "Directory already exists, skipping clone")
                return@withContext true
            }

            // Create parent directory
            projectDir.parentFile?.mkdirs()

            Log.d(TAG, "Cloning $repoUrl to $projectPath")

            val process = ProcessBuilder(
                "git", "clone", "-b", targetBranch, repoUrl, projectPath
            )
                .redirectErrorStream(true)
                .start()

            val output = StringBuilder()
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.forEachLine { line ->
                    output.appendLine(line)
                    Log.d(TAG, "git: $line")
                }
            }

            val exitCode = process.waitFor()

            if (exitCode == 0) {
                Log.d(TAG, "✅ Git clone successful")
                true
            } else {
                Log.e(TAG, "❌ Git clone failed: $output")
                false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Git clone error: ${e.message}", e)
            false
        }
    }

    /**
     * Clean gradle build
     */
    private suspend fun cleanBuild(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("gradle", "clean")
                .directory(File(projectPath))
                .start()

            process.waitFor() == 0

        } catch (e: Exception) {
            Log.e(TAG, "Clean build error: ${e.message}")
            false
        }
    }

    /**
     * Read build.gradle content
     */
    private fun readBuildGradle(): String? {
        return try {
            File(projectPath, "app/build.gradle").readText()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Install APK
     */
    private suspend fun installApk(apkPath: String) = withContext(Dispatchers.IO) {
        try {
            // Copy to downloads for easy access
            val downloadsDir = File("/storage/emulated/0/Download")
            val targetFile = File(downloadsDir, "GitHubAPKDownloader.apk")

            File(apkPath).copyTo(targetFile, overwrite = true)

            Log.d(TAG, "📦 APK copied to: ${targetFile.absolutePath}")

            // Note: Actual installation needs to be triggered from UI with proper permissions

        } catch (e: Exception) {
            Log.e(TAG, "Error installing APK: ${e.message}")
        }
    }

    /**
     * Check if project has uncommitted changes
     */
    suspend fun hasUncommittedChanges(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder(
                "git", "status", "--porcelain"
            )
                .directory(File(projectPath))
                .start()

            val output = BufferedReader(InputStreamReader(process.inputStream)).use {
                it.readText()
            }

            output.trim().isNotEmpty()

        } catch (e: Exception) {
            false
        }
    }
}
