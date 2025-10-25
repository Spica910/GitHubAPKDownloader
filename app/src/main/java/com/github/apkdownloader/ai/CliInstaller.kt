package com.github.apkdownloader.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Automatic CLI installer for Gemini and Claude
 */
class CliInstaller(private val context: Context) {

    private val _installProgress = MutableStateFlow<InstallProgress>(InstallProgress.Idle)
    val installProgress: StateFlow<InstallProgress> = _installProgress

    companion object {
        private const val TAG = "CliInstaller"

        // Termux paths
        private const val TERMUX_BASH = "/data/data/com.termux/files/usr/bin/bash"
        private const val TERMUX_BIN = "/data/data/com.termux/files/usr/bin"
    }

    /**
     * Execute command in Termux environment
     */
    private suspend fun executeInTermux(command: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Executing in Termux: $command")

            val process = ProcessBuilder(TERMUX_BASH, "-c", command)
                .redirectErrorStream(true)
                .start()

            val output = BufferedReader(InputStreamReader(process.inputStream)).use {
                it.readText()
            }

            val exitCode = process.waitFor()
            Log.d(TAG, "Exit code: $exitCode, Output: $output")

            Pair(exitCode, output)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing command: ${e.message}", e)
            Pair(-1, e.message ?: "Unknown error")
        }
    }

    /**
     * Check installation status of both CLIs
     */
    suspend fun checkInstallationStatus(): InstallationStatus = withContext(Dispatchers.IO) {
        val geminiInstalled = isGeminiInstalled()
        val claudeInstalled = isClaudeInstalled()
        val claudeAuthenticated = if (claudeInstalled) isClaudeAuthenticated() else false

        InstallationStatus(
            geminiInstalled = geminiInstalled,
            claudeInstalled = claudeInstalled,
            claudeAuthenticated = claudeAuthenticated,
            pythonAvailable = isPythonAvailable(),
            npmAvailable = isNpmAvailable(),
            pkgAvailable = isPkgAvailable()
        )
    }

    /**
     * Auto-install Gemini CLI
     */
    suspend fun installGeminiCli(): Result<String> = withContext(Dispatchers.IO) {
        try {
            _installProgress.value = InstallProgress.Installing("Gemini CLI")

            // Method 1: Try pip install
            _installProgress.value = InstallProgress.Installing("Installing dependencies...")

            // Ensure Python and pip are installed
            if (!isPythonAvailable()) {
                Log.d(TAG, "Installing Python...")
                val pythonResult = installPython()
                if (!pythonResult) {
                    return@withContext Result.failure(Exception("Failed to install Python"))
                }
            }

            // Install gemini-cli via pip
            Log.d(TAG, "Installing gemini-cli via pip...")
            _installProgress.value = InstallProgress.Installing("Installing gemini-cli via pip...")

            val (exitCode, output) = executeInTermux("pip install google-generativeai gemini-cli -q")

            if (exitCode == 0 || isGeminiInstalled()) {
                Log.d(TAG, "Gemini CLI installed successfully")
                _installProgress.value = InstallProgress.Success("Gemini CLI installed!")
                Result.success("Gemini CLI installed successfully")
            } else {
                Log.e(TAG, "Installation failed: $output")
                _installProgress.value = InstallProgress.Failed("Installation failed: $output")
                Result.failure(Exception("Installation failed: $output"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error installing Gemini CLI: ${e.message}", e)
            _installProgress.value = InstallProgress.Failed(e.message ?: "Unknown error")
            Result.failure(e)
        }
    }

    /**
     * Auto-install Claude CLI
     */
    suspend fun installClaudeCli(): Result<String> = withContext(Dispatchers.IO) {
        try {
            _installProgress.value = InstallProgress.Installing("Claude CLI")

            // Method 1: Try installing via official method
            Log.d(TAG, "Installing Claude CLI...")
            _installProgress.value = InstallProgress.Installing("Downloading Claude CLI...")

            // Install Claude CLI directly using Termux environment
            val installCommands = listOf(
                "curl -fsSL https://cli.anthropic.com/install.sh | sh",
                "npm install -g @anthropic-ai/claude-code",
                "pip install anthropic-claude-cli"
            )

            var installed = false
            var lastError = ""

            for ((index, cmd) in installCommands.withIndex()) {
                try {
                    Log.d(TAG, "Trying installation method ${index + 1}: $cmd")
                    _installProgress.value = InstallProgress.Installing("Method ${index + 1}/3...")

                    val (exitCode, output) = executeInTermux(cmd)

                    if (exitCode == 0 || isClaudeInstalled()) {
                        installed = true
                        break
                    }

                    lastError = output

                } catch (e: Exception) {
                    lastError = e.message ?: "Unknown error"
                    Log.e(TAG, "Method ${index + 1} failed: $lastError")
                }
            }

            if (installed || isClaudeInstalled()) {
                Log.d(TAG, "Claude CLI installed successfully")
                _installProgress.value = InstallProgress.Success("Claude CLI installed! Please authenticate.")
                Result.success("Claude CLI installed successfully. Run 'claude login' to authenticate.")
            } else {
                _installProgress.value = InstallProgress.Failed("All installation methods failed")
                Result.failure(Exception("Installation failed: $lastError"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error installing Claude CLI: ${e.message}", e)
            _installProgress.value = InstallProgress.Failed(e.message ?: "Unknown error")
            Result.failure(e)
        }
    }

    /**
     * Authenticate Claude CLI
     */
    suspend fun authenticateClaude(): Result<String> = withContext(Dispatchers.IO) {
        try {
            _installProgress.value = InstallProgress.Installing("Authenticating Claude...")

            // Start Claude authentication using Termux environment
            val (exitCode, output) = executeInTermux("claude login")

            // Extract authentication URL if present
            val authUrl = output.lines().firstOrNull {
                it.contains("https://") && it.contains("anthropic")
            }

            if (authUrl != null) {
                _installProgress.value = InstallProgress.AuthRequired(authUrl)
                Result.success("Please visit: $authUrl")
            } else if (exitCode == 0) {
                _installProgress.value = InstallProgress.Success("Claude authenticated!")
                Result.success("Authentication complete")
            } else {
                _installProgress.value = InstallProgress.Failed("Authentication failed: $output")
                Result.failure(Exception("Authentication failed: $output"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error authenticating Claude: ${e.message}", e)
            _installProgress.value = InstallProgress.Failed(e.message ?: "Unknown error")
            Result.failure(e)
        }
    }

    /**
     * Install Python (Termux)
     */
    private suspend fun installPython(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Installing Python via pkg...")
            _installProgress.value = InstallProgress.Installing("Installing Python...")

            val (exitCode, output) = executeInTermux("pkg install python -y")

            if (exitCode == 0) {
                Log.d(TAG, "Python installed successfully")
                true
            } else {
                Log.e(TAG, "Python installation failed: $output")
                false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error installing Python: ${e.message}")
            false
        }
    }

    /**
     * Check if Gemini CLI is installed
     */
    private suspend fun isGeminiInstalled(): Boolean = withContext(Dispatchers.IO) {
        try {
            val commands = listOf("gemini-cli", "gemini", "python -m gemini_cli")

            for (cmd in commands) {
                try {
                    val (exitCode, _) = executeInTermux("which ${cmd.split(" ").first()}")
                    if (exitCode == 0) {
                        return@withContext true
                    }
                } catch (e: Exception) {
                    continue
                }
            }
            false

        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if Claude CLI is installed
     */
    private suspend fun isClaudeInstalled(): Boolean = withContext(Dispatchers.IO) {
        try {
            val (exitCode, _) = executeInTermux("which claude")
            exitCode == 0

        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if Claude is authenticated
     */
    private suspend fun isClaudeAuthenticated(): Boolean = withContext(Dispatchers.IO) {
        try {
            val (exitCode, _) = executeInTermux("claude --version")

            // If version command works, likely authenticated
            exitCode == 0

        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if Python is available
     */
    private suspend fun isPythonAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val (exitCode, _) = executeInTermux("which python")
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if npm is available
     */
    private suspend fun isNpmAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val (exitCode, _) = executeInTermux("which npm")
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if pkg (Termux) is available
     */
    private suspend fun isPkgAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val (exitCode, _) = executeInTermux("which pkg")
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get installation instructions
     */
    fun getManualInstructions(): InstallInstructions {
        return InstallInstructions(
            geminiSteps = listOf(
                "1. Install Python: pkg install python",
                "2. Install pip: pkg install python-pip",
                "3. Install Gemini: pip install google-generativeai gemini-cli",
                "4. Verify: gemini-cli --version"
            ),
            claudeSteps = listOf(
                "1. Install Node.js: pkg install nodejs",
                "2. Install Claude: npm install -g @anthropic-ai/claude-code",
                "   OR: curl -fsSL https://cli.anthropic.com/install.sh | sh",
                "3. Authenticate: claude login",
                "4. Follow the browser prompts to complete authentication",
                "5. Verify: claude --version"
            )
        )
    }
}

/**
 * Installation progress states
 */
sealed class InstallProgress {
    object Idle : InstallProgress()
    data class Installing(val message: String) : InstallProgress()
    data class Success(val message: String) : InstallProgress()
    data class Failed(val error: String) : InstallProgress()
    data class AuthRequired(val url: String) : InstallProgress()
}

/**
 * Installation status
 */
data class InstallationStatus(
    val geminiInstalled: Boolean,
    val claudeInstalled: Boolean,
    val claudeAuthenticated: Boolean,
    val pythonAvailable: Boolean,
    val npmAvailable: Boolean,
    val pkgAvailable: Boolean
) {
    val allReady: Boolean
        get() = geminiInstalled && claudeInstalled && claudeAuthenticated

    val missingComponents: List<String>
        get() = buildList {
            if (!geminiInstalled) add("Gemini CLI")
            if (!claudeInstalled) add("Claude CLI")
            if (claudeInstalled && !claudeAuthenticated) add("Claude Authentication")
        }
}

/**
 * Manual installation instructions
 */
data class InstallInstructions(
    val geminiSteps: List<String>,
    val claudeSteps: List<String>
)
