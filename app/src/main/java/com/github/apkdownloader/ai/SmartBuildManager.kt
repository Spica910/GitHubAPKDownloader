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
            // Step 1: Sync from GitHub (skipped - not needed for local builds)
            // Users can manually use Sync button if they want to pull latest changes
            Log.d(TAG, "⏭️ Skipping git sync for faster builds")

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
            val gradlewFile = File(projectPath, "gradlew")

            // Create a build script that can be executed in Termux
            val buildScriptFile = File(projectPath, "run_build.sh")
            val buildScript = """
                #!/data/data/com.termux/files/usr/bin/bash
                # Auto-generated build script for Termux

                cd "$projectPath" || exit 1

                # Setup Java environment
                export JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-21-openjdk
                export PATH=${'$'}JAVA_HOME/bin:/data/data/com.termux/files/usr/bin:${'$'}PATH

                echo "🔨 Starting build..."
                echo "📂 Working directory: ${'$'}PWD"
                echo "☕ JAVA_HOME: ${'$'}JAVA_HOME"
                echo ""

                # Run gradle build
                if [ -f "./gradlew" ]; then
                    bash ./gradlew assembleDebug --stacktrace
                    BUILD_EXIT=${'$'}?
                else
                    echo "❌ Error: gradlew not found in ${'$'}PWD"
                    BUILD_EXIT=1
                fi

                echo ""
                if [ ${'$'}BUILD_EXIT -eq 0 ]; then
                    echo "✅ BUILD SUCCESSFUL"
                    echo "📦 APK location: app/build/outputs/apk/debug/app-debug.apk"

                    # Copy to Download folder
                    if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
                        APK_NAME="${'$'}(basename "$projectPath").apk"
                        cp app/build/outputs/apk/debug/app-debug.apk "/storage/emulated/0/Download/${'$'}APK_NAME"
                        echo "✅ APK copied to: /storage/emulated/0/Download/${'$'}APK_NAME"
                    fi

                    # Write success marker
                    echo "SUCCESS" > build_result.txt
                else
                    echo "❌ BUILD FAILED (exit code: ${'$'}BUILD_EXIT)"
                    echo "FAILED" > build_result.txt
                fi

                echo "BUILD_EXIT_CODE=${'$'}BUILD_EXIT" >> build_result.txt
            """.trimIndent()

            buildScriptFile.writeText(buildScript)
            Log.d(TAG, "✅ Created build script: ${buildScriptFile.absolutePath}")

            // Delete old result file
            val resultFile = File(projectPath, "build_result.txt")
            if (resultFile.exists()) resultFile.delete()

            // Open Termux with the build command
            val intent = android.content.Intent()
            intent.action = android.content.Intent.ACTION_VIEW
            intent.setClassName("com.termux", "com.termux.app.TermuxActivity")
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)

            try {
                context.startActivity(intent)
                Log.d(TAG, "📱 Opened Termux app")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open Termux: ${e.message}")
            }

            // Copy command to clipboard
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText(
                "Build Command",
                "cd \"$projectPath\" && bash run_build.sh"
            )
            clipboard.setPrimaryClip(clip)
            Log.d(TAG, "📋 Build command copied to clipboard")

            // Return with instruction to run manually
            BuildResult(
                success = false,
                errorLog = """
                    ⚠️ 수동 빌드가 필요합니다

                    Termux가 열렸고 빌드 명령어가 클립보드에 복사되었습니다.

                    📋 Termux에서 다음 명령어를 붙여넣고 실행하세요:
                    cd "$projectPath" && bash run_build.sh

                    또는 간단히:
                    bash run_build.sh

                    ✅ 빌드가 완료되면 APK가 Download 폴더에 자동으로 복사됩니다.

                    💡 TIP: 자동 빌드가 완료되면 앱으로 돌아와서 "Refresh" 버튼을 눌러 결과를 확인하세요.
                """.trimIndent()
            )

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
                Log.d(TAG, "Repository not cloned yet. Skipping git pull.")
                // If repo is just cloned, we already have the latest code
                return@withContext true
            }

            // Directory exists, do git pull using JGit
            Log.d(TAG, "Running git pull origin $branch in $projectPath")

            try {
                val git = org.eclipse.jgit.api.Git.open(projectDir)

                // Pull latest changes
                val pullResult = git.pull()
                    .setRemote("origin")
                    .setRemoteBranchName(branch)
                    .setProgressMonitor(object : org.eclipse.jgit.lib.ProgressMonitor {
                        override fun start(totalTasks: Int) {
                            Log.d(TAG, "Pull started: $totalTasks tasks")
                        }
                        override fun beginTask(title: String?, totalWork: Int) {
                            Log.d(TAG, "Task: $title")
                        }
                        override fun update(completed: Int) {}
                        override fun endTask() {}
                        override fun isCancelled(): Boolean = false
                        override fun showDuration(enabled: Boolean) {}
                    })
                    .call()

                git.close()

                if (pullResult.isSuccessful) {
                    Log.d(TAG, "✅ Git pull successful")
                    true
                } else {
                    Log.w(TAG, "⚠️ Git pull completed with issues")
                    // Still return true to continue build
                    true
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Git pull failed: ${e.message}", e)
                // Even if pull fails, continue with build (maybe already up to date)
                Log.d(TAG, "Continuing with build despite pull failure")
                true
            }

        } catch (e: Exception) {
            Log.e(TAG, "Git pull error: ${e.message}", e)
            // Don't fail the build if sync fails
            true
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

            val processBuilder = ProcessBuilder(
                "git", "clone", "-b", targetBranch, repoUrl, projectPath
            )
                .redirectErrorStream(true)

            // Add Termux paths to environment
            val env = processBuilder.environment()
            val termuxBin = "/data/data/com.termux/files/usr/bin"
            val currentPath = env["PATH"] ?: "/system/bin:/system/xbin"
            env["PATH"] = "$termuxBin:$currentPath"

            val process = processBuilder.start()

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
            val processBuilder = ProcessBuilder("gradle", "clean")
                .directory(File(projectPath))

            // Add Termux paths to environment
            val env = processBuilder.environment()
            val termuxBin = "/data/data/com.termux/files/usr/bin"
            val currentPath = env["PATH"] ?: "/system/bin:/system/xbin"
            env["PATH"] = "$termuxBin:$currentPath"

            val process = processBuilder.start()

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
            val processBuilder = ProcessBuilder(
                "git", "status", "--porcelain"
            )
                .directory(File(projectPath))

            // Add Termux paths to environment
            val env = processBuilder.environment()
            val termuxBin = "/data/data/com.termux/files/usr/bin"
            val currentPath = env["PATH"] ?: "/system/bin:/system/xbin"
            env["PATH"] = "$termuxBin:$currentPath"

            val process = processBuilder.start()

            val output = BufferedReader(InputStreamReader(process.inputStream)).use {
                it.readText()
            }

            output.trim().isNotEmpty()

        } catch (e: Exception) {
            false
        }
    }
}
