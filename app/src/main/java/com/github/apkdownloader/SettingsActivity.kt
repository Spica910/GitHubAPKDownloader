package com.github.apkdownloader

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.apkdownloader.ai.BuiltInInstaller
import com.github.apkdownloader.ai.InstallProgress
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var builtInInstaller: BuiltInInstaller

    // UI components
    private lateinit var openTerminalButton: MaterialButton
    private lateinit var pythonStatusBadge: TextView
    private lateinit var geminiStatusBadge: TextView
    private lateinit var claudeStatusBadge: TextView
    private lateinit var androidToolsStatusBadge: TextView
    private lateinit var installPythonButton: MaterialButton
    private lateinit var installGeminiButton: MaterialButton
    private lateinit var showGeminiInstructionsButton: MaterialButton
    private lateinit var installClaudeButton: MaterialButton
    private lateinit var authenticateClaudeButton: MaterialButton
    private lateinit var showClaudeInstructionsButton: MaterialButton
    private lateinit var installAndroidToolsButton: MaterialButton
    private lateinit var showAndroidToolsInstructionsButton: MaterialButton
    private lateinit var progressCard: MaterialCardView
    private lateinit var progressText: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Setup toolbar
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "AI Build Settings"

        builtInInstaller = BuiltInInstaller(this)

        initializeViews()
        setupListeners()
        checkInstallationStatus()
        observeProgress()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun initializeViews() {
        openTerminalButton = findViewById(R.id.openTerminalButton)
        pythonStatusBadge = findViewById(R.id.pythonStatusBadge)
        geminiStatusBadge = findViewById(R.id.geminiStatusBadge)
        claudeStatusBadge = findViewById(R.id.claudeStatusBadge)
        androidToolsStatusBadge = findViewById(R.id.androidToolsStatusBadge)
        installPythonButton = findViewById(R.id.installPythonButton)
        installGeminiButton = findViewById(R.id.installGeminiButton)
        showGeminiInstructionsButton = findViewById(R.id.showGeminiInstructionsButton)
        installClaudeButton = findViewById(R.id.installClaudeButton)
        authenticateClaudeButton = findViewById(R.id.authenticateClaudeButton)
        showClaudeInstructionsButton = findViewById(R.id.showClaudeInstructionsButton)
        installAndroidToolsButton = findViewById(R.id.installAndroidToolsButton)
        showAndroidToolsInstructionsButton = findViewById(R.id.showAndroidToolsInstructionsButton)
        progressCard = findViewById(R.id.progressCard)
        progressText = findViewById(R.id.progressText)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupListeners() {
        openTerminalButton.setOnClickListener {
            startActivity(Intent(this, TerminalActivity::class.java))
        }

        installPythonButton.setOnClickListener {
            installPython()
        }

        installGeminiButton.setOnClickListener {
            installGemini()
        }

        showGeminiInstructionsButton.setOnClickListener {
            showManualInstructions("Gemini")
        }

        installClaudeButton.setOnClickListener {
            installClaude()
        }

        authenticateClaudeButton.setOnClickListener {
            authenticateClaude()
        }

        showClaudeInstructionsButton.setOnClickListener {
            showManualInstructions("Claude")
        }

        installAndroidToolsButton.setOnClickListener {
            installAndroidTools()
        }

        showAndroidToolsInstructionsButton.setOnClickListener {
            showManualInstructions("AndroidTools")
        }
    }

    private fun checkInstallationStatus() {
        lifecycleScope.launch {
            try {
                val status = builtInInstaller.checkInstallationStatus()

                // Update Python status
                if (status.pythonAvailable) {
                    pythonStatusBadge.text = "✅ Installed"
                    pythonStatusBadge.setBackgroundColor(getColor(android.R.color.holo_green_dark))
                    installPythonButton.isEnabled = false
                    installPythonButton.text = "✅ Python Installed"
                } else {
                    pythonStatusBadge.text = "❌ Not Installed"
                    pythonStatusBadge.setBackgroundColor(getColor(android.R.color.holo_red_dark))
                }

                // Update Gemini status
                if (status.geminiInstalled) {
                    geminiStatusBadge.text = "✅ Installed"
                    geminiStatusBadge.setBackgroundColor(getColor(android.R.color.holo_green_dark))
                    installGeminiButton.isEnabled = false
                    installGeminiButton.text = "✅ Gemini Installed"
                } else {
                    geminiStatusBadge.text = "❌ Not Installed"
                    geminiStatusBadge.setBackgroundColor(getColor(android.R.color.holo_red_dark))
                }

                // Update Claude status
                when {
                    status.claudeInstalled && status.claudeAuthenticated -> {
                        claudeStatusBadge.text = "✅ Ready"
                        claudeStatusBadge.setBackgroundColor(getColor(android.R.color.holo_green_dark))
                        installClaudeButton.isEnabled = false
                        installClaudeButton.text = "✅ Claude Ready"
                        authenticateClaudeButton.visibility = View.GONE
                    }
                    status.claudeInstalled && !status.claudeAuthenticated -> {
                        claudeStatusBadge.text = "⚠️ Not Authenticated"
                        claudeStatusBadge.setBackgroundColor(getColor(android.R.color.holo_orange_dark))
                        installClaudeButton.isEnabled = false
                        installClaudeButton.text = "✅ Installed"
                        authenticateClaudeButton.visibility = View.VISIBLE
                    }
                    else -> {
                        claudeStatusBadge.text = "❌ Not Installed"
                        claudeStatusBadge.setBackgroundColor(getColor(android.R.color.holo_red_dark))
                        authenticateClaudeButton.visibility = View.GONE
                    }
                }

                // Update Android Tools status
                if (status.androidToolsInstalled) {
                    androidToolsStatusBadge.text = "✅ Installed"
                    androidToolsStatusBadge.setBackgroundColor(getColor(android.R.color.holo_green_dark))
                    installAndroidToolsButton.isEnabled = false
                    installAndroidToolsButton.text = "✅ Build Tools Installed"
                } else {
                    androidToolsStatusBadge.text = "❌ Not Installed"
                    androidToolsStatusBadge.setBackgroundColor(getColor(android.R.color.holo_red_dark))
                }

            } catch (e: Exception) {
                android.util.Log.e("Settings", "Error checking status: ${e.message}")
            }
        }
    }

    private fun installPython() {
        lifecycleScope.launch {
            progressCard.visibility = View.VISIBLE
            installPythonButton.isEnabled = false

            val result = builtInInstaller.installPython()

            result.onSuccess {
                checkInstallationStatus()
            }.onFailure {
                showError("Python Installation Failed", it.message ?: "Unknown error")
                installPythonButton.isEnabled = true
            }
        }
    }

    private fun installGemini() {
        lifecycleScope.launch {
            progressCard.visibility = View.VISIBLE
            installGeminiButton.isEnabled = false

            val result = builtInInstaller.installGeminiCli()

            result.onSuccess {
                checkInstallationStatus()
            }.onFailure {
                showError("Gemini Installation Failed", it.message ?: "Unknown error")
                installGeminiButton.isEnabled = true
            }
        }
    }

    private fun installClaude() {
        lifecycleScope.launch {
            progressCard.visibility = View.VISIBLE
            installClaudeButton.isEnabled = false

            val result = builtInInstaller.installClaudeCli()

            result.onSuccess {
                checkInstallationStatus()
            }.onFailure {
                showError("Claude Installation Failed", it.message ?: "Unknown error")
                installClaudeButton.isEnabled = true
            }
        }
    }

    private fun authenticateClaude() {
        showInfo(
            "Claude Authentication",
            "After installing Claude CLI, you need to authenticate manually:\n\n" +
            "1. Open a terminal in the app\n" +
            "2. Run: claude login\n" +
            "3. Follow the authentication prompts\n" +
            "4. Return here to verify installation"
        )
    }

    private fun installAndroidTools() {
        lifecycleScope.launch {
            progressCard.visibility = View.VISIBLE
            installAndroidToolsButton.isEnabled = false

            val result = builtInInstaller.installAndroidTools()

            result.onSuccess {
                checkInstallationStatus()
            }.onFailure {
                showError("Android Tools Installation Failed", it.message ?: "Unknown error")
                installAndroidToolsButton.isEnabled = true
            }
        }
    }

    private fun observeProgress() {
        lifecycleScope.launch {
            builtInInstaller.installProgress.collect { progress ->
                when (progress) {
                    is InstallProgress.Idle -> {
                        progressCard.visibility = View.GONE
                    }
                    is InstallProgress.Installing -> {
                        progressCard.visibility = View.VISIBLE
                        progressText.text = progress.message
                        progressBar.isIndeterminate = true
                    }
                    is InstallProgress.Success -> {
                        progressCard.visibility = View.VISIBLE
                        progressText.text = "✅ ${progress.message}"
                        progressBar.isIndeterminate = false
                        progressBar.progress = 100

                        // Hide after 2 seconds
                        progressCard.postDelayed({
                            progressCard.visibility = View.GONE
                            checkInstallationStatus()
                        }, 2000)
                    }
                    is InstallProgress.Failed -> {
                        progressCard.visibility = View.GONE
                        showError("Installation Failed", progress.error)
                    }
                    is InstallProgress.AuthRequired -> {
                        progressCard.visibility = View.GONE
                        // Open browser
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(progress.url))
                            startActivity(intent)
                        } catch (e: Exception) {
                            showInfo("Authentication", "Please visit: ${progress.url}")
                        }
                    }
                }
            }
        }
    }

    private fun showManualInstructions(cliName: String) {
        val instructions = builtInInstaller.getManualInstructions()

        val steps = when (cliName) {
            "Gemini" -> instructions.geminiSteps
            "Claude" -> instructions.claudeSteps
            "AndroidTools" -> instructions.androidToolsSteps
            else -> instructions.geminiSteps
        }

        val title = when (cliName) {
            "AndroidTools" -> "Android Build Tools Installation"
            else -> "$cliName CLI Installation"
        }

        val message = steps.joinToString("\n\n")

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setNeutralButton("Copy Steps") { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Installation Steps", message)
                clipboard.setPrimaryClip(clip)
                android.widget.Toast.makeText(this, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showError(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showInfo(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
