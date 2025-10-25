package com.github.apkdownloader.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

/**
 * Built-in installer that works within the app's own environment
 * No external Termux dependency required
 */
class BuiltInInstaller(private val context: Context) {

    private val terminal = AppTerminal(context)
    private val _installProgress = MutableStateFlow<InstallProgress>(InstallProgress.Idle)
    val installProgress: StateFlow<InstallProgress> = _installProgress

    companion object {
        private const val TAG = "BuiltInInstaller"

        // Python standalone builds for Android
        private const val PYTHON_ARM64_URL = "https://github.com/lzhiyong/python-build-android/releases/download/v3.10.4/python-3.10.4-aarch64.tar.xz"
        private const val PYTHON_ARM_URL = "https://github.com/lzhiyong/python-build-android/releases/download/v3.10.4/python-3.10.4-arm.tar.xz"

        // Android build tools
        private const val ANDROID_TOOLS_URL = "https://github.com/lzhiyong/android-sdk-tools/releases/latest/download/android-sdk-tools-static-aarch64.zip"
    }

    /**
     * Check installation status
     */
    suspend fun checkInstallationStatus(): InstallationStatus = withContext(Dispatchers.IO) {
        val pythonInstalled = terminal.isCommandAvailable("python3")
        val pipInstalled = terminal.isCommandAvailable("pip3")
        val geminiInstalled = terminal.isCommandAvailable("gemini-cli") ||
                              terminal.isCommandAvailable("python3 -m gemini_cli")
        val claudeInstalled = terminal.isCommandAvailable("claude")
        val androidToolsInstalled = terminal.isCommandAvailable("aapt2")

        InstallationStatus(
            geminiInstalled = geminiInstalled,
            claudeInstalled = claudeInstalled,
            claudeAuthenticated = claudeInstalled, // Will check properly later
            pythonAvailable = pythonInstalled,
            npmAvailable = false, // Not implementing npm in app
            pkgAvailable = true, // We have our own package management
            androidToolsInstalled = androidToolsInstalled
        )
    }

    /**
     * Install Python in app's environment
     */
    suspend fun installPython(): Result<String> = withContext(Dispatchers.IO) {
        try {
            _installProgress.value = InstallProgress.Installing("Installing Python...")

            // For now, use pip from Termux if available, or show instructions
            // Python installation from tarball requires busybox which we don't have

            _installProgress.value = InstallProgress.Failed("Python installation requires manual setup")

            Result.failure(Exception(
                "Python installation not yet supported in built-in mode.\n\n" +
                "Please install Termux and run:\n" +
                "pkg install python\n\n" +
                "Or wait for future updates with precompiled Python."
            ))

        } catch (e: Exception) {
            Log.e(TAG, "Error installing Python: ${e.message}", e)
            _installProgress.value = InstallProgress.Failed(e.message ?: "Unknown error")
            Result.failure(e)
        }
    }

    /**
     * Install Gemini CLI using pip in app's environment
     */
    suspend fun installGeminiCli(): Result<String> = withContext(Dispatchers.IO) {
        try {
            _installProgress.value = InstallProgress.Installing("Installing Gemini CLI...")

            // Ensure Python is installed
            if (!terminal.isCommandAvailable("python3")) {
                return@withContext Result.failure(Exception("Python not installed. Install Python first."))
            }

            // Install gemini-cli via pip
            val installResult = terminal.execute("""
                python3 -m pip install --user google-generativeai gemini-cli
            """.trimIndent())

            if (installResult.success || terminal.isCommandAvailable("gemini-cli")) {
                _installProgress.value = InstallProgress.Success("Gemini CLI installed!")
                Result.success("Gemini CLI installed successfully")
            } else {
                _installProgress.value = InstallProgress.Failed("Installation failed")
                Result.failure(Exception("Failed to install Gemini CLI: ${installResult.output}"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error installing Gemini CLI: ${e.message}", e)
            _installProgress.value = InstallProgress.Failed(e.message ?: "Unknown error")
            Result.failure(e)
        }
    }

    /**
     * Install Claude CLI (binary download)
     */
    suspend fun installClaudeCli(): Result<String> = withContext(Dispatchers.IO) {
        try {
            _installProgress.value = InstallProgress.Installing("Installing Claude CLI...")

            val dirInfo = terminal.getDirectoryInfo()

            // Download Claude CLI binary for Android
            val downloadResult = downloadFile(
                "https://github.com/anthropics/anthropic-sdk-typescript/releases/latest/download/claude-android-arm64",
                File(dirInfo.bin, "claude")
            )

            if (downloadResult) {
                terminal.execute("chmod +x ${dirInfo.bin}/claude")
                _installProgress.value = InstallProgress.Success("Claude CLI installed!")
                Result.success("Claude CLI installed successfully")
            } else {
                // Fallback: Try via Python
                val pythonResult = terminal.execute("python3 -m pip install --user anthropic-cli")
                if (pythonResult.success) {
                    _installProgress.value = InstallProgress.Success("Claude CLI installed!")
                    Result.success("Claude CLI installed via pip")
                } else {
                    Result.failure(Exception("Failed to install Claude CLI"))
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error installing Claude CLI: ${e.message}", e)
            _installProgress.value = InstallProgress.Failed(e.message ?: "Unknown error")
            Result.failure(e)
        }
    }

    /**
     * Install Android build tools
     */
    suspend fun installAndroidTools(): Result<String> = withContext(Dispatchers.IO) {
        try {
            _installProgress.value = InstallProgress.Installing("Installing Android Build Tools...")

            val sdkDir = File(context.filesDir, "android-sdk/build-tools/34.0.0")
            sdkDir.mkdirs()

            // Download android-sdk-tools
            val downloadResult = terminal.execute("""
                cd ${sdkDir.absolutePath} && \
                wget -O tools.zip $ANDROID_TOOLS_URL && \
                unzip -o tools.zip && \
                rm tools.zip && \
                chmod +x aapt aapt2 zipalign apksigner
            """.trimIndent())

            if (downloadResult.success || File(sdkDir, "aapt2").exists()) {
                // Create symlinks
                val dirInfo = terminal.getDirectoryInfo()
                terminal.execute("""
                    ln -sf ${File(sdkDir, "aapt2").absolutePath} ${dirInfo.bin}/aapt2 && \
                    ln -sf ${File(sdkDir, "aapt").absolutePath} ${dirInfo.bin}/aapt && \
                    ln -sf ${File(sdkDir, "zipalign").absolutePath} ${dirInfo.bin}/zipalign && \
                    ln -sf ${File(sdkDir, "apksigner").absolutePath} ${dirInfo.bin}/apksigner
                """.trimIndent())

                _installProgress.value = InstallProgress.Success("Android Tools installed!")
                Result.success("Android build tools installed successfully")
            } else {
                Result.failure(Exception("Failed to install Android tools: ${downloadResult.output}"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error installing Android tools: ${e.message}", e)
            _installProgress.value = InstallProgress.Failed(e.message ?: "Unknown error")
            Result.failure(e)
        }
    }

    /**
     * Download a file from URL
     */
    private suspend fun downloadFile(url: String, destination: File): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Downloading $url to ${destination.absolutePath}")

            val connection = URL(url).openConnection()
            connection.connect()

            connection.getInputStream().use { input ->
                FileOutputStream(destination).use { output ->
                    input.copyTo(output)
                }
            }

            destination.setExecutable(true)
            Log.d(TAG, "Download completed")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Error downloading file: ${e.message}", e)
            false
        }
    }

    /**
     * Get manual installation instructions
     */
    fun getManualInstructions(): InstallInstructions {
        val dirInfo = terminal.getDirectoryInfo()

        return InstallInstructions(
            geminiSteps = listOf(
                "1. Install Python first (use Auto-Install Python button)",
                "2. Install Gemini: python3 -m pip install google-generativeai gemini-cli",
                "3. Verify: gemini-cli --version",
                "4. All files installed in: ${dirInfo.home}"
            ),
            claudeSteps = listOf(
                "1. Install Claude CLI (use Auto-Install Claude button)",
                "2. Authenticate: claude login",
                "3. Follow browser prompts",
                "4. Verify: claude --version"
            ),
            androidToolsSteps = listOf(
                "1. Auto-install from lzhiyong's repository",
                "2. Tools will be in: ${dirInfo.bin}",
                "3. Includes: aapt, aapt2, zipalign, apksigner",
                "4. Verify: aapt2 version"
            )
        )
    }
}
