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
import com.github.apkdownloader.ai.CliInstaller
import com.github.apkdownloader.ai.InstallProgress
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

class CliSetupActivity : AppCompatActivity() {

    private lateinit var cliInstaller: CliInstaller

    // UI components
    private lateinit var geminiStatusBadge: TextView
    private lateinit var claudeStatusBadge: TextView
    private lateinit var installGeminiButton: MaterialButton
    private lateinit var showGeminiInstructionsButton: MaterialButton
    private lateinit var installClaudeButton: MaterialButton
    private lateinit var authenticateClaudeButton: MaterialButton
    private lateinit var showClaudeInstructionsButton: MaterialButton
    private lateinit var progressCard: MaterialCardView
    private lateinit var progressText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var skipButton: MaterialButton
    private lateinit var continueButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cli_setup)

        cliInstaller = CliInstaller(this)

        initializeViews()
        setupListeners()
        checkInstallationStatus()
        observeProgress()
    }

    private fun initializeViews() {
        geminiStatusBadge = findViewById(R.id.geminiStatusBadge)
        claudeStatusBadge = findViewById(R.id.claudeStatusBadge)
        installGeminiButton = findViewById(R.id.installGeminiButton)
        showGeminiInstructionsButton = findViewById(R.id.showGeminiInstructionsButton)
        installClaudeButton = findViewById(R.id.installClaudeButton)
        authenticateClaudeButton = findViewById(R.id.authenticateClaudeButton)
        showClaudeInstructionsButton = findViewById(R.id.showClaudeInstructionsButton)
        progressCard = findViewById(R.id.progressCard)
        progressText = findViewById(R.id.progressText)
        progressBar = findViewById(R.id.progressBar)
        skipButton = findViewById(R.id.skipButton)
        continueButton = findViewById(R.id.continueButton)
    }

    private fun setupListeners() {
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

        skipButton.setOnClickListener {
            finish()
        }

        continueButton.setOnClickListener {
            // Mark setup as complete
            getSharedPreferences("cli_setup", MODE_PRIVATE)
                .edit()
                .putBoolean("setup_complete", true)
                .apply()
            finish()
        }
    }

    private fun checkInstallationStatus() {
        lifecycleScope.launch {
            try {
                val status = cliInstaller.checkInstallationStatus()

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

                // Enable continue button if at least one AI is ready
                continueButton.isEnabled = status.geminiInstalled || status.allReady

            } catch (e: Exception) {
                android.util.Log.e("CliSetup", "Error checking status: ${e.message}")
            }
        }
    }

    private fun installGemini() {
        lifecycleScope.launch {
            progressCard.visibility = View.VISIBLE
            installGeminiButton.isEnabled = false

            val result = cliInstaller.installGeminiCli()

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

            val result = cliInstaller.installClaudeCli()

            result.onSuccess {
                checkInstallationStatus()
            }.onFailure {
                showError("Claude Installation Failed", it.message ?: "Unknown error")
                installClaudeButton.isEnabled = true
            }
        }
    }

    private fun authenticateClaude() {
        lifecycleScope.launch {
            progressCard.visibility = View.VISIBLE
            progressText.text = "Opening Claude authentication..."

            val result = cliInstaller.authenticateClaude()

            result.onSuccess { message ->
                if (message.contains("https://")) {
                    // Open browser for authentication
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(message.substringAfter("visit: ")))
                        startActivity(intent)
                        showInfo("Authentication", "Please complete authentication in your browser, then return here and check status.")
                    } catch (e: Exception) {
                        showInfo("Authentication URL", message)
                    }
                } else {
                    showInfo("Success", message)
                }
                progressCard.visibility = View.GONE
            }.onFailure {
                showError("Authentication Failed", it.message ?: "Unknown error")
                progressCard.visibility = View.GONE
            }
        }
    }

    private fun observeProgress() {
        lifecycleScope.launch {
            cliInstaller.installProgress.collect { progress ->
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
        val instructions = cliInstaller.getManualInstructions()

        val steps = if (cliName == "Gemini") {
            instructions.geminiSteps
        } else {
            instructions.claudeSteps
        }

        val message = steps.joinToString("\n\n")

        AlertDialog.Builder(this)
            .setTitle("$cliName CLI Installation")
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
