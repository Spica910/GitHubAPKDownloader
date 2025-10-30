package com.github.apkdownloader.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Automatic Pull Request creator for AI fixes
 */
class PrCreator(private val projectPath: String) {

    companion object {
        private const val TAG = "PrCreator"
        private val dateFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
    }

    /**
     * Create a pull request for AI-applied fixes
     */
    suspend fun createPrForFixes(
        fixResults: List<FixResult>,
        originalError: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Step 1: Create a new branch for fixes
            val branchName = "ai-fix-${dateFormat.format(Date())}"
            if (!createBranch(branchName)) {
                return@withContext Result.failure(Exception("Failed to create branch"))
            }

            // Step 2: Stage and commit changes
            val commitMessage = buildCommitMessage(fixResults, originalError)
            if (!commitChanges(commitMessage)) {
                return@withContext Result.failure(Exception("Failed to commit changes"))
            }

            // Step 3: Push to remote
            if (!pushBranch(branchName)) {
                return@withContext Result.failure(Exception("Failed to push branch"))
            }

            // Step 4: Create PR using GitHub CLI
            val prUrl = createGitHubPr(branchName, fixResults, originalError)
                ?: return@withContext Result.failure(Exception("Failed to create PR"))

            Log.d(TAG, "✅ PR created: $prUrl")
            Result.success(prUrl)

        } catch (e: Exception) {
            Log.e(TAG, "Error creating PR: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Create a new git branch
     */
    private suspend fun createBranch(branchName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val processBuilder = ProcessBuilder(
                "git", "checkout", "-b", branchName
            )
                .directory(File(projectPath))
                .redirectErrorStream(true)

            // Add Termux paths to environment
            val env = processBuilder.environment()
            val termuxBin = "/data/data/com.termux/files/usr/bin"
            val currentPath = env["PATH"] ?: "/system/bin:/system/xbin"
            env["PATH"] = "$termuxBin:$currentPath"

            val process = processBuilder.start()

            val exitCode = process.waitFor()
            if (exitCode == 0) {
                Log.d(TAG, "✅ Created branch: $branchName")
                true
            } else {
                Log.e(TAG, "❌ Failed to create branch")
                false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error creating branch: ${e.message}")
            false
        }
    }

    /**
     * Commit changes
     */
    private suspend fun commitChanges(message: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Add all changes
            val addProcessBuilder = ProcessBuilder("git", "add", ".")
                .directory(File(projectPath))

            // Add Termux paths to environment
            val addEnv = addProcessBuilder.environment()
            val termuxBin = "/data/data/com.termux/files/usr/bin"
            var currentPath = addEnv["PATH"] ?: "/system/bin:/system/xbin"
            addEnv["PATH"] = "$termuxBin:$currentPath"

            var process = addProcessBuilder.start()

            if (process.waitFor() != 0) {
                return@withContext false
            }

            // Commit with message
            val commitProcessBuilder = ProcessBuilder(
                "git", "commit", "-m", message
            )
                .directory(File(projectPath))
                .redirectErrorStream(true)

            // Add Termux paths to environment
            val commitEnv = commitProcessBuilder.environment()
            currentPath = commitEnv["PATH"] ?: "/system/bin:/system/xbin"
            commitEnv["PATH"] = "$termuxBin:$currentPath"

            process = commitProcessBuilder.start()

            val exitCode = process.waitFor()
            if (exitCode == 0) {
                Log.d(TAG, "✅ Changes committed")
                true
            } else {
                Log.e(TAG, "❌ Failed to commit changes")
                false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error committing: ${e.message}")
            false
        }
    }

    /**
     * Push branch to remote
     */
    private suspend fun pushBranch(branchName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val processBuilder = ProcessBuilder(
                "git", "push", "-u", "origin", branchName
            )
                .directory(File(projectPath))
                .redirectErrorStream(true)

            // Add Termux paths to environment
            val env = processBuilder.environment()
            val termuxBin = "/data/data/com.termux/files/usr/bin"
            val currentPath = env["PATH"] ?: "/system/bin:/system/xbin"
            env["PATH"] = "$termuxBin:$currentPath"

            val process = processBuilder.start()

            val exitCode = process.waitFor()
            if (exitCode == 0) {
                Log.d(TAG, "✅ Branch pushed to remote")
                true
            } else {
                Log.e(TAG, "❌ Failed to push branch")
                false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error pushing: ${e.message}")
            false
        }
    }

    /**
     * Create GitHub PR using gh CLI
     */
    private suspend fun createGitHubPr(
        branchName: String,
        fixResults: List<FixResult>,
        originalError: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val title = buildPrTitle(fixResults)
            val body = buildPrBody(fixResults, originalError)

            val processBuilder = ProcessBuilder(
                "gh", "pr", "create",
                "--title", title,
                "--body", body,
                "--head", branchName
            )
                .directory(File(projectPath))
                .redirectErrorStream(true)

            // Add Termux paths to environment
            val env = processBuilder.environment()
            val termuxBin = "/data/data/com.termux/files/usr/bin"
            val currentPath = env["PATH"] ?: "/system/bin:/system/xbin"
            env["PATH"] = "$termuxBin:$currentPath"

            val process = processBuilder.start()

            val output = BufferedReader(InputStreamReader(process.inputStream)).use {
                it.readText()
            }

            val exitCode = process.waitFor()
            if (exitCode == 0) {
                // Extract PR URL from output
                output.lines().firstOrNull { it.startsWith("https://") }
            } else {
                Log.e(TAG, "gh pr create failed: $output")
                null
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error creating GitHub PR: ${e.message}")
            null
        }
    }

    /**
     * Build commit message
     */
    private fun buildCommitMessage(fixResults: List<FixResult>, originalError: String): String {
        val aiUsed = fixResults.joinToString(" → ") { it.aiUsed }
        val description = fixResults.lastOrNull()?.description ?: "Build error fixes"

        return """
            🤖 AI Auto-Fix: $description

            Fixed by: $aiUsed

            Original error:
            ${originalError.take(200)}...

            🤖 Generated with AI Build System

            Co-Authored-By: AI Build Assistant <ai@github.com>
        """.trimIndent()
    }

    /**
     * Build PR title
     */
    private fun buildPrTitle(fixResults: List<FixResult>): String {
        val aiUsed = fixResults.lastOrNull()?.aiUsed ?: "AI"
        val description = fixResults.lastOrNull()?.description ?: "Build error fixes"

        return "🤖 AI Auto-Fix ($aiUsed): $description"
    }

    /**
     * Build PR body
     */
    private fun buildPrBody(fixResults: List<FixResult>, originalError: String): String {
        val aiChain = fixResults.joinToString(" → ") { it.aiUsed }
        val filesModified = fixResults.flatMap { it.filesModified }.distinct()

        return """
            ## 🤖 Auto-generated by AI Build Assistant

            This PR contains automatic fixes applied by AI to resolve build errors.

            ### AI Strategy Used
            $aiChain

            ### Original Build Error
            ```
            ${originalError.take(500)}
            ${if (originalError.length > 500) "..." else ""}
            ```

            ### Fixes Applied
            ${fixResults.mapIndexed { index, fix ->
                "**${index + 1}. ${fix.aiUsed}**: ${fix.description}"
            }.joinToString("\n")}

            ### Files Modified
            ${if (filesModified.isNotEmpty()) {
                filesModified.joinToString("\n") { "- `$it`" }
            } else {
                "- Build configuration files"
            }}

            ### Build Status
            ✅ Build successful after applying AI fixes

            ### Testing
            - [ ] Manual code review
            - [ ] Test APK installation
            - [ ] Verify app functionality

            ---

            🤖 Generated automatically by AI Build System
            💡 Please review the changes before merging
        """.trimIndent()
    }

    /**
     * Check if current branch can create PR
     */
    suspend fun canCreatePr(): Boolean = withContext(Dispatchers.IO) {
        try {
            val processBuilder = ProcessBuilder("gh", "auth", "status")

            // Add Termux paths to environment
            val env = processBuilder.environment()
            val termuxBin = "/data/data/com.termux/files/usr/bin"
            val currentPath = env["PATH"] ?: "/system/bin:/system/xbin"
            env["PATH"] = "$termuxBin:$currentPath"

            val process = processBuilder.start()

            process.waitFor() == 0

        } catch (e: Exception) {
            false
        }
    }
}
