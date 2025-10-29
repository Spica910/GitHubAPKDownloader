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
    private val appTerminal = AppTerminal(context)
    private val _buildStatus = MutableStateFlow<BuildStatus>(BuildStatus.Idle)
    val buildStatus: StateFlow<BuildStatus> = _buildStatus

    companion object {
        private const val TAG = "SmartBuildManager"
        private const val DEFAULT_BRANCH = "master"
    }

    /**
     * Sync repository only (clone or pull)
     */
    suspend fun syncRepositoryOnly(
        repoUrl: String,
        branch: String = DEFAULT_BRANCH
    ): BuildResult = withContext(Dispatchers.IO) {
        try {
            _buildStatus.value = BuildStatus.Syncing
            Log.d(TAG, "📥 Syncing repository only...")

            val syncResult = syncRepository(repoUrl, branch)

            _buildStatus.value = if (syncResult) {
                BuildStatus.Success("", 0)
            } else {
                BuildStatus.Failed("Failed to sync repository")
            }

            BuildResult(
                success = syncResult,
                errorLog = if (syncResult) null else "Failed to clone/sync repository from GitHub"
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error during sync: ${e.message}", e)
            _buildStatus.value = BuildStatus.Failed(e.message ?: "Unknown error")
            BuildResult(
                success = false,
                errorLog = e.toString()
            )
        }
    }

    /**
     * Build only (no sync)
     */
    suspend fun buildOnly(
        config: AiBuildConfig = AiBuildConfig()
    ): BuildResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var fixAttempts = 0
        val previousFixes = mutableListOf<String>()

        try {
            // Check if project exists
            val projectDir = File(projectPath)
            if (!projectDir.exists() || !File(projectDir, ".git").exists()) {
                return@withContext BuildResult(
                    success = false,
                    errorLog = "Repository not found. Please sync repository first."
                )
            }

            // Clean build if requested
            if (config.cleanBuild) {
                Log.d(TAG, "🧹 Cleaning build cache...")
                cleanBuild()
            }

            // Attempt build (with AI auto-fix loop)
            var buildResult = BuildResult(success = false)

            while (!buildResult.success && fixAttempts < config.maxRetries) {
                _buildStatus.value = BuildStatus.Building
                Log.d(TAG, "🔨 Building APK (attempt ${fixAttempts + 1})...")

                buildResult = executeGradleBuild()

                if (buildResult.success) {
                    Log.d(TAG, "✅ Build successful!")
                    break
                }

                // Build failed - check if AI is enabled
                if (!config.useGeminiFirst && !config.fallbackToClaude) {
                    Log.d(TAG, "❌ Build failed. AI fix disabled.")
                    break
                }

                // Build failed - try AI fix
                Log.d(TAG, "❌ Build failed. Asking AI for help...")

                fixAttempts++
                val aiTier = if (fixAttempts == 1) AiTier.GEMINI else AiTier.CLAUDE
                _buildStatus.value = BuildStatus.AiFixing(aiTier, fixAttempts)

                // Check if AI is available
                val (geminiAvailable, claudeAvailable) = aiBuildHelper.checkAvailability()
                if (!geminiAvailable && !claudeAvailable) {
                    Log.w(TAG, "⚠️ No AI available. Build will fail without fixes.")
                    Log.w(TAG, "💡 Tip: Install Claude CLI in Termux for auto-fix")
                    break
                }

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

            _buildStatus.value = if (buildResult.success) {
                BuildStatus.Success(buildResult.apkPath ?: "", buildTime)
            } else {
                BuildStatus.Failed(buildResult.errorLog ?: "Unknown error")
            }

            buildResult.copy(buildTime = buildTime)

        } catch (e: Exception) {
            Log.e(TAG, "Error during build: ${e.message}", e)
            _buildStatus.value = BuildStatus.Failed(e.message ?: "Unknown error")
            BuildResult(
                success = false,
                errorLog = e.toString()
            )
        }
    }

    /**
     * Main entry point: Smart Build with AI assistance
     */
    suspend fun smartBuild(
        repoUrl: String,
        branch: String = DEFAULT_BRANCH,
        config: AiBuildConfig = AiBuildConfig()
    ): BuildResult = withContext(Dispatchers.IO) {

        val startTime = System.currentTimeMillis()
        var fixAttempts = 0
        val previousFixes = mutableListOf<String>()

        try {
            // Step 1: Clone or Sync from GitHub
            _buildStatus.value = BuildStatus.Syncing
            Log.d(TAG, "📥 Syncing repository from GitHub...")
            val syncResult = syncRepository(repoUrl, branch)
            if (!syncResult) {
                return@withContext BuildResult(
                    success = false,
                    errorLog = "Failed to clone/sync repository from GitHub"
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

                // Build failed - check if AI is enabled
                if (!config.useGeminiFirst && !config.fallbackToClaude) {
                    Log.d(TAG, "❌ Build failed. AI fix disabled.")
                    break
                }

                // Build failed - try AI fix
                Log.d(TAG, "❌ Build failed. Asking AI for help...")

                fixAttempts++
                val aiTier = if (fixAttempts == 1) AiTier.GEMINI else AiTier.CLAUDE
                _buildStatus.value = BuildStatus.AiFixing(aiTier, fixAttempts)

                // Check if AI is available
                val (geminiAvailable, claudeAvailable) = aiBuildHelper.checkAvailability()
                if (!geminiAvailable && !claudeAvailable) {
                    Log.w(TAG, "⚠️ No AI available. Build will fail without fixes.")
                    Log.w(TAG, "💡 Tip: Install Claude CLI in Termux for auto-fix")
                    break
                }

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
            val projectDir = File(projectPath)
            val gradlewFile = File(projectPath, "gradlew")
            val command = if (gradlewFile.exists()) {
                "./gradlew assembleDebug --stacktrace"
            } else {
                "gradle assembleDebug --stacktrace"
            }

            Log.d(TAG, "Executing: $command in $projectPath")

            val result = appTerminal.execute(command, projectDir)
            val output = result.output

            if (output.length > 1000) {
                Log.d(TAG, "Build output (last 1000 chars): ${output.takeLast(1000)}")
            } else {
                Log.d(TAG, "Build output: $output")
            }

            if (result.success) {
                // Find APK
                val apkFile = findGeneratedApk()
                BuildResult(
                    success = true,
                    apkPath = apkFile?.absolutePath
                )
            } else {
                // Extract error from output
                val errorLog = extractError(output)
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
     * Sync repository: Clone if not exists, Pull if exists
     */
    private suspend fun syncRepository(repoUrl: String, branch: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val projectDir = File(projectPath)
            val gitDir = File(projectDir, ".git")

            if (!gitDir.exists()) {
                // Repository not cloned yet - clone it
                Log.d(TAG, "📦 Repository not found. Cloning from $repoUrl...")
                return@withContext cloneRepository(repoUrl, branch)
            } else {
                // Repository exists - pull updates
                Log.d(TAG, "🔄 Repository exists. Pulling updates from branch $branch...")
                return@withContext gitPull(branch)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error syncing repository: ${e.message}", e)
            false
        }
    }

    /**
     * Git pull from remote (force update, overwrite local changes)
     */
    private suspend fun gitPull(branch: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Running git pull origin $branch in $projectPath (force update)")

            val projectDir = File(projectPath)
            if (!projectDir.exists()) {
                Log.e(TAG, "Project directory does not exist: $projectPath")
                return@withContext false
            }

            // Add git safe.directory first
            appTerminal.execute("git config --global --add safe.directory '$projectPath'")

            // Force pull: fetch and reset to match remote exactly
            // This will OVERWRITE any local changes
            // Use working directory instead of cd command
            val fetchResult = appTerminal.execute("git fetch origin $branch", projectDir)
            if (!fetchResult.success) {
                Log.e(TAG, "❌ Git fetch failed: ${fetchResult.output}")
                return@withContext false
            }

            val resetResult = appTerminal.execute("git reset --hard origin/$branch", projectDir)
            if (!resetResult.success) {
                Log.e(TAG, "❌ Git reset failed: ${resetResult.output}")
                return@withContext false
            }

            Log.d(TAG, "✅ Git pull successful (force updated)")
            Log.d(TAG, "Fetch: ${fetchResult.output}")
            Log.d(TAG, "Reset: ${resetResult.output}")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Git pull error: ${e.message}", e)
            false
        }
    }

    /**
     * Clone repository from GitHub (clean clone, removes existing directory)
     */
    private suspend fun cloneRepository(repoUrl: String, targetBranch: String = "master"): Boolean = withContext(Dispatchers.IO) {
        try {
            val projectDir = File(projectPath)

            // If directory exists, remove it for clean clone
            if (projectDir.exists()) {
                Log.d(TAG, "⚠️ Directory exists, removing for clean clone...")
                val removeResult = appTerminal.execute("rm -rf '$projectPath'")
                if (!removeResult.success) {
                    Log.w(TAG, "Failed to remove existing directory: ${removeResult.output}")
                    // Continue anyway, git clone might handle it
                }
            }

            // Create parent directory
            projectDir.parentFile?.mkdirs()

            Log.d(TAG, "📦 Cloning $repoUrl (branch: $targetBranch) to $projectPath")

            // Use git clone command with single quotes to avoid quote escaping issues
            // Single quotes prevent variable expansion in shell, so we use Kotlin string interpolation
            val result = appTerminal.execute(
                "git clone -b $targetBranch '$repoUrl' '$projectPath'"
            )

            if (result.success) {
                Log.d(TAG, "✅ Git clone successful")
                Log.d(TAG, result.output)

                // Add git safe.directory to prevent ownership errors
                appTerminal.execute("git config --global --add safe.directory '$projectPath'")

                // Make gradlew executable
                makeGradlewExecutable()

                true
            } else {
                Log.e(TAG, "❌ Git clone failed: ${result.output}")
                Log.e(TAG, "Command was: git clone -b $targetBranch '$repoUrl' '$projectPath'")
                false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Git clone error: ${e.message}", e)
            false
        }
    }

    /**
     * Make gradlew executable
     */
    private suspend fun makeGradlewExecutable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val gradlewFile = File(projectPath, "gradlew")
            if (!gradlewFile.exists()) {
                Log.d(TAG, "⚠️ gradlew not found, skipping chmod")
                return@withContext false
            }

            val result = appTerminal.execute("chmod +x '${gradlewFile.absolutePath}'")

            if (result.success) {
                Log.d(TAG, "✅ gradlew made executable")
                true
            } else {
                Log.w(TAG, "⚠️ Failed to make gradlew executable: ${result.output}")
                false
            }

        } catch (e: Exception) {
            Log.w(TAG, "chmod error: ${e.message}")
            false
        }
    }

    /**
     * Clean gradle build
     */
    private suspend fun cleanBuild(): Boolean = withContext(Dispatchers.IO) {
        try {
            val projectDir = File(projectPath)
            val gradlewFile = File(projectPath, "gradlew")
            val command = if (gradlewFile.exists()) {
                "./gradlew clean"
            } else {
                "gradle clean"
            }

            val result = appTerminal.execute(command, projectDir)
            result.success

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
            val projectDir = File(projectPath)
            if (!projectDir.exists()) {
                return@withContext false
            }

            val result = appTerminal.execute("git status --porcelain", projectDir)

            if (result.success) {
                result.output.trim().isNotEmpty()
            } else {
                false
            }

        } catch (e: Exception) {
            false
        }
    }
}
