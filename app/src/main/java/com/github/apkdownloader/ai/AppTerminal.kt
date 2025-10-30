package com.github.apkdownloader.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Built-in terminal executor for the app
 * Runs commands in the app's own environment (no Termux dependency)
 */
class AppTerminal(private val context: Context) {

    companion object {
        private const val TAG = "AppTerminal"
    }

    // App's own binary directory
    private val appBinDir = File(context.filesDir, "bin")
    private val appLibDir = File(context.filesDir, "lib")
    private val appHomeDir = context.filesDir

    init {
        // Create necessary directories
        appBinDir.mkdirs()
        appLibDir.mkdirs()
    }

    /**
     * Execute a command in the app's environment
     * Uses Termux RUN_COMMAND intent for git commands to bypass Android app sandboxing
     */
    suspend fun execute(command: String, workingDir: File? = null): CommandResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Executing: $command")

            // Check if this is a git command
            val isGitCommand = command.trim().startsWith("/data/data/com.termux/files/usr/bin/git") ||
                              command.trim().startsWith("git ")

            if (isGitCommand) {
                // Use Termux RUN_COMMAND intent for git commands
                return@withContext executeViaTermux(command, workingDir)
            }

            // For non-git commands, use regular shell execution
            val processBuilder = ProcessBuilder("/system/bin/sh", "-c", command)

            // Set working directory
            if (workingDir != null && workingDir.exists()) {
                processBuilder.directory(workingDir)
            } else {
                processBuilder.directory(appHomeDir)
            }

            // Build environment with app's paths
            val env = buildEnvironment()
            processBuilder.environment().putAll(env)
            processBuilder.redirectErrorStream(true)

            val process = processBuilder.start()

            val output = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.readText()
            }

            val exitCode = process.waitFor()

            Log.d(TAG, "Exit code: $exitCode")
            Log.d(TAG, "Output: $output")

            CommandResult(
                exitCode = exitCode,
                output = output,
                success = exitCode == 0
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error executing command: ${e.message}", e)
            CommandResult(
                exitCode = -1,
                output = e.message ?: "Unknown error",
                success = false
            )
        }
    }

    /**
     * Execute a command using Termux RUN_COMMAND intent
     * This allows the app to run commands in Termux's environment
     */
    private suspend fun executeViaTermux(command: String, workingDir: File?): CommandResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Executing via Termux: $command")

            // Create a unique output file for this command
            val outputFile = File(context.cacheDir, "termux_output_${System.currentTimeMillis()}.txt")
            val errorFile = File(context.cacheDir, "termux_error_${System.currentTimeMillis()}.txt")

            // Build the command with output redirection
            val workingDirPath = workingDir?.absolutePath ?: "/storage/emulated/0"
            val fullCommand = "cd '$workingDirPath' && $command > '${outputFile.absolutePath}' 2> '${errorFile.absolutePath}'; echo \$? > '${outputFile.absolutePath}.exit'"

            val intent = android.content.Intent()
            intent.setClassName("com.termux", "com.termux.app.RunCommandService")
            intent.action = "com.termux.RUN_COMMAND"
            intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", fullCommand))
            intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", workingDirPath)
            intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)

            // Start the service
            context.startService(intent)

            // Wait for the command to complete (check for output files)
            var waitTime = 0
            val maxWaitTime = 60000 // 60 seconds
            while (waitTime < maxWaitTime) {
                kotlinx.coroutines.delay(500)
                waitTime += 500

                if (File("${outputFile.absolutePath}.exit").exists()) {
                    break
                }
            }

            // Read the output
            val output = if (outputFile.exists()) {
                outputFile.readText()
            } else {
                ""
            }

            val errorOutput = if (errorFile.exists()) {
                errorFile.readText()
            } else {
                ""
            }

            val exitCodeFile = File("${outputFile.absolutePath}.exit")
            val exitCode = if (exitCodeFile.exists()) {
                try {
                    exitCodeFile.readText().trim().toInt()
                } catch (e: Exception) {
                    -1
                }
            } else {
                -1
            }

            // Cleanup
            outputFile.delete()
            errorFile.delete()
            exitCodeFile.delete()

            val combinedOutput = if (errorOutput.isNotEmpty()) {
                "$output\n$errorOutput"
            } else {
                output
            }

            Log.d(TAG, "Termux execution completed. Exit code: $exitCode")
            Log.d(TAG, "Output: $combinedOutput")

            CommandResult(
                exitCode = exitCode,
                output = combinedOutput,
                success = exitCode == 0
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error executing via Termux: ${e.message}", e)
            CommandResult(
                exitCode = -1,
                output = "Failed to execute via Termux: ${e.message}",
                success = false
            )
        }
    }

    /**
     * Build environment variables for the app
     */
    private fun buildEnvironment(): Map<String, String> {
        val nativeLibDir = context.applicationInfo.nativeLibraryDir

        // Include Termux paths if available
        val termuxPrefix = "/data/data/com.termux/files/usr"
        val termuxBin = "$termuxPrefix/bin"
        val termuxLib = "$termuxPrefix/lib"

        return mapOf(
            "HOME" to appHomeDir.absolutePath,
            "PATH" to "${appBinDir.absolutePath}:${termuxBin}:${nativeLibDir}:/system/bin:/system/xbin",
            "LD_LIBRARY_PATH" to "${appLibDir.absolutePath}:${termuxLib}:${nativeLibDir}",
            "TMPDIR" to context.cacheDir.absolutePath,
            "PREFIX" to appHomeDir.absolutePath
        )
    }

    /**
     * Check if a command/binary is available
     */
    suspend fun isCommandAvailable(command: String): Boolean {
        // Check in multiple locations
        val termuxBin = "/data/data/com.termux/files/usr/bin"

        // First try which command (uses PATH)
        val whichResult = execute("which $command")
        if (whichResult.success && whichResult.output.isNotBlank()) {
            return true
        }

        // Check app's bin directory
        if (File(appBinDir, command).exists()) {
            return true
        }

        // Check Termux bin directory
        if (File(termuxBin, command).exists()) {
            return true
        }

        return false
    }

    /**
     * Download and install a binary from URL
     */
    suspend fun downloadBinary(url: String, binaryName: String): CommandResult {
        val targetFile = File(appBinDir, binaryName)

        val result = execute("""
            wget -O ${targetFile.absolutePath} $url && \
            chmod +x ${targetFile.absolutePath}
        """.trimIndent())

        if (result.success) {
            Log.d(TAG, "Downloaded $binaryName to ${targetFile.absolutePath}")
        }

        return result
    }

    /**
     * Get app directories info
     */
    fun getDirectoryInfo(): AppDirectoryInfo {
        return AppDirectoryInfo(
            home = appHomeDir.absolutePath,
            bin = appBinDir.absolutePath,
            lib = appLibDir.absolutePath,
            cache = context.cacheDir.absolutePath,
            nativeLib = context.applicationInfo.nativeLibraryDir
        )
    }
}

/**
 * Command execution result
 */
data class CommandResult(
    val exitCode: Int,
    val output: String,
    val success: Boolean
)

/**
 * App directory information
 */
data class AppDirectoryInfo(
    val home: String,
    val bin: String,
    val lib: String,
    val cache: String,
    val nativeLib: String
)
