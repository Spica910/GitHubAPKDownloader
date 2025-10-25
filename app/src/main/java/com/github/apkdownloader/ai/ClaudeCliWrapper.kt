package com.github.apkdownloader.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Wrapper for Claude CLI integration (Paid, smarter)
 */
class ClaudeCliWrapper(private val projectPath: String) {

    companion object {
        private const val TAG = "ClaudeCli"
        private const val CLAUDE_CLI_COMMAND = "claude"
    }

    /**
     * Check if Claude CLI is installed and authenticated
     */
    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder(CLAUDE_CLI_COMMAND, "--version").start()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "Claude CLI not available: ${e.message}")
            false
        }
    }

    /**
     * Ask Claude to fix a build error (smarter, paid)
     */
    suspend fun fixBuildError(request: AiFixRequest): FixResult = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) {
                return@withContext FixResult(
                    success = false,
                    aiUsed = "Claude",
                    changesApplied = false,
                    description = "Claude CLI not installed",
                    error = "Please install and authenticate Claude CLI"
                )
            }

            val prompt = buildPrompt(request)
            Log.d(TAG, "Sending request to Claude...")

            // Use Claude with project context
            val process = ProcessBuilder(
                CLAUDE_CLI_COMMAND,
                "--no-tty",
                "-p", prompt
            )
                .directory(File(projectPath))
                .redirectErrorStream(true)
                .start()

            val output = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.readText()
            }

            val exitCode = process.waitFor()

            if (exitCode != 0) {
                return@withContext FixResult(
                    success = false,
                    aiUsed = "Claude",
                    changesApplied = false,
                    description = "Claude CLI failed",
                    error = output
                )
            }

            // Parse Claude's response
            parseResponse(output)

        } catch (e: Exception) {
            Log.e(TAG, "Error calling Claude: ${e.message}", e)
            FixResult(
                success = false,
                aiUsed = "Claude",
                changesApplied = false,
                description = "Error: ${e.message}",
                error = e.toString()
            )
        }
    }

    /**
     * Build prompt for Claude (more sophisticated)
     */
    private fun buildPrompt(request: AiFixRequest): String {
        return """
            I'm building an Android app and encountered a Gradle build error. Please analyze and fix it.

            PROJECT CONTEXT:
            - This is an Android Kotlin project using Gradle
            - Build tool: gradle assembleDebug
            - Target: Generate APK file

            BUILD ERROR:
            ```
            ${request.errorLog}
            ```

            ${request.buildGradleContent?.let {
                """
                CURRENT BUILD.GRADLE:
                ```gradle
                $it
                ```
                """.trimIndent()
            } ?: ""}

            ${if (request.previousFixes.isNotEmpty()) {
                """
                PREVIOUS FIX ATTEMPTS (that didn't work):
                ${request.previousFixes.mapIndexed { i, fix -> "${i + 1}. $fix" }.joinToString("\n")}
                """.trimIndent()
            } else ""}

            Please:
            1. Analyze the root cause of the error
            2. Provide EXACT file changes needed
            3. Use the Edit or Write tool to apply fixes directly
            4. Verify the fix will work

            After fixing, respond with:
            - Brief description of what was wrong
            - What files were changed
            - Expected outcome

            IMPORTANT: Actually make the changes using available tools, don't just suggest them!
        """.trimIndent()
    }

    /**
     * Parse Claude's response
     */
    private fun parseResponse(output: String): FixResult {
        try {
            // Claude with tools will actually make file changes
            // We need to detect if files were modified

            val filesModified = mutableListOf<String>()
            val lines = output.lines()

            // Look for Claude's tool usage indications
            var description = ""
            var changesApplied = false

            // Check if Claude used Edit/Write tools
            if (output.contains("Edit") || output.contains("Write") ||
                output.contains("modified") || output.contains("updated")) {
                changesApplied = true

                // Try to extract modified files from output
                lines.filter { it.contains(".gradle") || it.contains(".kt") || it.contains(".xml") }
                    .forEach { line ->
                        // Extract file paths
                        val words = line.split(" ", "/")
                        words.filter { it.endsWith(".gradle") || it.endsWith(".kt") || it.endsWith(".xml") }
                            .forEach { filesModified.add(it) }
                    }
            }

            // Extract description from Claude's response
            description = lines.filter {
                !it.startsWith("```") &&
                !it.trim().isEmpty() &&
                it.length > 10
            }.firstOrNull()?.trim() ?: "Claude applied fixes"

            return FixResult(
                success = true,
                aiUsed = "Claude",
                changesApplied = changesApplied,
                description = description,
                filesModified = filesModified,
                fixSuggestion = output
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Claude response: ${e.message}")
            return FixResult(
                success = false,
                aiUsed = "Claude",
                changesApplied = false,
                description = "Failed to parse response",
                error = e.message,
                fixSuggestion = output
            )
        }
    }

    /**
     * Ask Claude to apply a specific fix suggestion
     */
    suspend fun applyFix(fixSuggestion: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                Please apply this fix to the project:

                $fixSuggestion

                Use the Edit or Write tool to make the necessary changes.
            """.trimIndent()

            val process = ProcessBuilder(
                CLAUDE_CLI_COMMAND,
                "--no-tty",
                "-p", prompt
            )
                .directory(File(projectPath))
                .start()

            val exitCode = process.waitFor()
            exitCode == 0

        } catch (e: Exception) {
            Log.e(TAG, "Error applying fix: ${e.message}")
            false
        }
    }
}
