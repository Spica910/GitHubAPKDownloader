package com.github.apkdownloader.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * AI Build Helper with tier system
 * Strategy: Try Gemini (free) first, fallback to Claude (paid) if needed
 */
class AiBuildHelper(
    private val context: Context,
    private val projectPath: String
) {

    private val geminiCli = GeminiCliWrapper(context)
    private val claudeCli = ClaudeCliWrapper(context, projectPath)

    companion object {
        private const val TAG = "AiBuildHelper"
    }

    /**
     * Fix build error using AI tier system
     */
    suspend fun fixBuildError(
        request: AiFixRequest,
        config: AiBuildConfig
    ): FixResult = withContext(Dispatchers.IO) {

        var geminiResult: FixResult? = null

        // Tier 1: Try Gemini first (free)
        if (config.useGeminiFirst) {
            Log.d(TAG, "🆓 Trying Gemini (Free tier)...")
            geminiResult = geminiCli.fixBuildError(request)

            if (geminiResult.success) {
                Log.d(TAG, "✅ Gemini found a solution!")
                // Apply Gemini's fix if it provided one
                if (geminiResult.fixSuggestion != null && !geminiResult.changesApplied) {
                    val applied = applyFix(geminiResult.fixSuggestion!!)
                    geminiResult = geminiResult.copy(changesApplied = applied)
                }
                return@withContext geminiResult
            }

            Log.d(TAG, "❌ Gemini couldn't fix it. Reason: ${geminiResult.error}")
        }

        // Tier 2: Fallback to Claude (paid, smarter)
        if (config.fallbackToClaude) {
            Log.d(TAG, "💎 Trying Claude (Paid tier)...")

            // Add Gemini's failed attempt to context
            val enhancedRequest = request.copy(
                previousFixes = request.previousFixes + listOfNotNull(
                    geminiResult?.fixSuggestion?.let { "Gemini suggestion (failed): $it" }
                )
            )

            val claudeResult = claudeCli.fixBuildError(enhancedRequest)

            if (claudeResult.success) {
                Log.d(TAG, "✅ Claude found a solution!")
                // Claude might have already applied changes via tools
                if (!claudeResult.changesApplied && claudeResult.fixSuggestion != null) {
                    val applied = claudeCli.applyFix(claudeResult.fixSuggestion!!)
                    return@withContext claudeResult.copy(changesApplied = applied)
                }
                return@withContext claudeResult
            }

            Log.d(TAG, "❌ Claude couldn't fix it either. Reason: ${claudeResult.error}")
        }

        // Both failed
        return@withContext FixResult(
            success = false,
            aiUsed = "Gemini + Claude",
            changesApplied = false,
            description = "All AI tiers failed to fix the error",
            error = "Please check the error log and fix manually"
        )
    }

    /**
     * Apply a fix suggestion (for Gemini which doesn't have tools)
     */
    private suspend fun applyFix(fixSuggestion: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Parse fix suggestion and apply changes
            // This is a simple implementation - Gemini's suggestions might need manual parsing

            // For now, we'll save the suggestion to a file for review
            val fixFile = File(projectPath, "AI_FIX_SUGGESTION.md")
            fixFile.writeText("""
                # AI Fix Suggestion

                The AI suggested the following fix. Please review and apply manually if needed:

                ```
                $fixSuggestion
                ```

                Generated at: ${java.util.Date()}
            """.trimIndent())

            Log.d(TAG, "Fix suggestion saved to: ${fixFile.absolutePath}")

            // For Gemini, we'll return false to indicate manual review needed
            // unless the fix is a simple command
            if (fixSuggestion.contains("COMMANDS:")) {
                // Try to execute commands
                return@withContext executeCommands(fixSuggestion)
            }

            false

        } catch (e: Exception) {
            Log.e(TAG, "Error applying fix: ${e.message}")
            false
        }
    }

    /**
     * Execute shell commands from AI suggestion
     */
    private suspend fun executeCommands(fixSuggestion: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val lines = fixSuggestion.lines()
            var inCommandBlock = false
            val commands = mutableListOf<String>()

            for (line in lines) {
                if (line.startsWith("COMMANDS:")) {
                    inCommandBlock = true
                    continue
                }
                if (inCommandBlock) {
                    if (line.startsWith("FILE_CHANGES:") || line.startsWith("---")) {
                        break
                    }
                    if (line.trim().isNotEmpty() && !line.startsWith("#")) {
                        commands.add(line.trim())
                    }
                }
            }

            if (commands.isEmpty()) {
                return@withContext false
            }

            Log.d(TAG, "Executing ${commands.size} commands from AI...")
            commands.forEach { cmd ->
                Log.d(TAG, "Running: $cmd")
                val process = ProcessBuilder("/system/bin/sh", "-c", cmd)
                    .directory(File(projectPath))
                    .redirectErrorStream(true)
                    .start()

                val exitCode = process.waitFor()
                if (exitCode != 0) {
                    Log.e(TAG, "Command failed: $cmd")
                    return@withContext false
                }
            }

            true

        } catch (e: Exception) {
            Log.e(TAG, "Error executing commands: ${e.message}")
            false
        }
    }

    /**
     * Check which AI services are available
     */
    suspend fun checkAvailability(): Pair<Boolean, Boolean> {
        val geminiAvailable = geminiCli.isAvailable()
        val claudeAvailable = claudeCli.isAvailable()
        return Pair(geminiAvailable, claudeAvailable)
    }

    /**
     * Get recommended tier based on error complexity
     */
    fun getRecommendedTier(errorLog: String): AiTier {
        // Simple heuristic: complex errors go straight to Claude
        val complexKeywords = listOf(
            "AAPT2",
            "dependency resolution",
            "version conflict",
            "duplicate class",
            "manifest merger"
        )

        return if (complexKeywords.any { errorLog.contains(it, ignoreCase = true) }) {
            AiTier.CLAUDE
        } else {
            AiTier.GEMINI
        }
    }
}
