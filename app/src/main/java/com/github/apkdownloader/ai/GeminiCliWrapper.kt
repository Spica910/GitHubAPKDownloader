package com.github.apkdownloader.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Wrapper for Gemini CLI integration (Free tier)
 */
class GeminiCliWrapper {

    companion object {
        private const val TAG = "GeminiCli"
        private const val GEMINI_CLI_COMMAND = "gemini-cli"
    }

    /**
     * Check if Gemini CLI is installed
     */
    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("which", GEMINI_CLI_COMMAND).start()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "Gemini CLI not available: ${e.message}")
            false
        }
    }

    /**
     * Ask Gemini to fix a build error
     */
    suspend fun fixBuildError(request: AiFixRequest): FixResult = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) {
                return@withContext FixResult(
                    success = false,
                    aiUsed = "Gemini",
                    changesApplied = false,
                    description = "Gemini CLI not installed",
                    error = "Please install gemini-cli: pip install gemini-cli"
                )
            }

            val prompt = buildPrompt(request)
            Log.d(TAG, "Sending request to Gemini...")

            val process = ProcessBuilder(GEMINI_CLI_COMMAND, prompt)
                .redirectErrorStream(true)
                .start()

            val output = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.readText()
            }

            val exitCode = process.waitFor()

            if (exitCode != 0) {
                return@withContext FixResult(
                    success = false,
                    aiUsed = "Gemini",
                    changesApplied = false,
                    description = "Gemini CLI failed",
                    error = output
                )
            }

            // Parse Gemini's response
            parseResponse(output)

        } catch (e: Exception) {
            Log.e(TAG, "Error calling Gemini: ${e.message}", e)
            FixResult(
                success = false,
                aiUsed = "Gemini",
                changesApplied = false,
                description = "Error: ${e.message}",
                error = e.toString()
            )
        }
    }

    /**
     * Build prompt for Gemini
     */
    private fun buildPrompt(request: AiFixRequest): String {
        return """
            You are an Android build expert. Fix this Gradle build error.

            ERROR LOG:
            ${request.errorLog}

            ${request.buildGradleContent?.let { "BUILD.GRADLE:\n$it" } ?: ""}

            ${if (request.previousFixes.isNotEmpty()) "PREVIOUS FIXES TRIED:\n${request.previousFixes.joinToString("\n")}" else ""}

            Provide ONLY the fix as executable shell commands or file changes.
            Format your response as:

            FIX_DESCRIPTION: <brief description>
            COMMANDS:
            <shell commands to run>
            OR
            FILE_CHANGES:
            FILE: <filepath>
            CONTENT:
            <new content>
            ---

            Be concise and provide working solutions only.
        """.trimIndent()
    }

    /**
     * Parse Gemini's response
     */
    private fun parseResponse(output: String): FixResult {
        try {
            val lines = output.lines()
            var description = ""
            val filesModified = mutableListOf<String>()
            var fixSuggestion = output

            // Try to extract description
            lines.firstOrNull { it.startsWith("FIX_DESCRIPTION:") }?.let {
                description = it.substringAfter("FIX_DESCRIPTION:").trim()
            }

            // Try to extract file changes
            var currentFile: String? = null
            for (line in lines) {
                if (line.startsWith("FILE:")) {
                    currentFile = line.substringAfter("FILE:").trim()
                    currentFile?.let { filesModified.add(it) }
                }
            }

            return FixResult(
                success = description.isNotEmpty() || output.contains("COMMANDS:") || output.contains("FILE_CHANGES:"),
                aiUsed = "Gemini",
                changesApplied = false, // Will be applied by SmartBuildManager
                description = description.ifEmpty { "Gemini suggested fixes" },
                filesModified = filesModified,
                fixSuggestion = fixSuggestion
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Gemini response: ${e.message}")
            return FixResult(
                success = false,
                aiUsed = "Gemini",
                changesApplied = false,
                description = "Failed to parse response",
                error = e.message
            )
        }
    }
}
