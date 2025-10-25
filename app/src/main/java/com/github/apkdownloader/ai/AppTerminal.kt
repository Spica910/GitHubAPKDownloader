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
     */
    suspend fun execute(command: String, workingDir: File? = null): CommandResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Executing: $command")

            // Build environment with app's paths
            val env = buildEnvironment()

            val processBuilder = ProcessBuilder("/system/bin/sh", "-c", command)

            // Set working directory
            if (workingDir != null && workingDir.exists()) {
                processBuilder.directory(workingDir)
            } else {
                processBuilder.directory(appHomeDir)
            }

            // Set environment variables
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
     * Build environment variables for the app
     */
    private fun buildEnvironment(): Map<String, String> {
        val nativeLibDir = context.applicationInfo.nativeLibraryDir

        return mapOf(
            "HOME" to appHomeDir.absolutePath,
            "PATH" to "${appBinDir.absolutePath}:${nativeLibDir}:/system/bin:/system/xbin",
            "LD_LIBRARY_PATH" to "${appLibDir.absolutePath}:${nativeLibDir}",
            "TMPDIR" to context.cacheDir.absolutePath,
            "PREFIX" to appHomeDir.absolutePath
        )
    }

    /**
     * Check if a command/binary is available
     */
    suspend fun isCommandAvailable(command: String): Boolean {
        val result = execute("which $command || test -f ${appBinDir}/$command")
        return result.success
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
