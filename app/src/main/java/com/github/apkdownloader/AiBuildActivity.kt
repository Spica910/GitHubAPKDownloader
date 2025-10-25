package com.github.apkdownloader

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.github.apkdownloader.ai.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import java.io.File

class AiBuildActivity : AppCompatActivity() {

    private lateinit var projectConfig: ProjectConfig
    private lateinit var smartBuildManager: SmartBuildManager
    private lateinit var prCreator: PrCreator
    private lateinit var aiBuildHelper: AiBuildHelper

    // UI components
    private lateinit var projectPathText: TextView
    private lateinit var changeProjectButton: MaterialButton
    private lateinit var branchSpinner: Spinner
    private lateinit var geminiStatusText: TextView
    private lateinit var claudeStatusText: TextView
    private lateinit var cleanBuildCheckbox: MaterialCheckBox
    private lateinit var autoInstallCheckbox: MaterialCheckBox
    private lateinit var createPrCheckbox: MaterialCheckBox
    private lateinit var smartBuildButton: MaterialButton
    private lateinit var buildStatusCard: MaterialCardView
    private lateinit var buildStatusText: TextView
    private lateinit var buildProgressBar: ProgressBar
    private lateinit var viewLogsButton: MaterialButton

    private var currentBuildLog: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_build)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Initialize components
        projectConfig = ProjectConfig(this)
        val projectPath = projectConfig.getProjectPath()
        smartBuildManager = SmartBuildManager(this, projectPath)
        prCreator = PrCreator(projectPath)
        aiBuildHelper = AiBuildHelper(this, projectPath)

        initializeViews()
        setupListeners()
        loadConfiguration()
        checkAiAvailability()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun initializeViews() {
        projectPathText = findViewById(R.id.projectPathText)
        changeProjectButton = findViewById(R.id.changeProjectButton)
        branchSpinner = findViewById(R.id.branchSpinner)
        geminiStatusText = findViewById(R.id.geminiStatusText)
        claudeStatusText = findViewById(R.id.claudeStatusText)
        cleanBuildCheckbox = findViewById(R.id.cleanBuildCheckbox)
        autoInstallCheckbox = findViewById(R.id.autoInstallCheckbox)
        createPrCheckbox = findViewById(R.id.createPrCheckbox)
        smartBuildButton = findViewById(R.id.smartBuildButton)
        buildStatusCard = findViewById(R.id.buildStatusCard)
        buildStatusText = findViewById(R.id.buildStatusText)
        buildProgressBar = findViewById(R.id.buildProgressBar)
        viewLogsButton = findViewById(R.id.viewLogsButton)
    }

    private fun setupListeners() {
        changeProjectButton.setOnClickListener {
            showProjectLocationDialog()
        }

        smartBuildButton.setOnClickListener {
            startSmartBuild()
        }

        viewLogsButton.setOnClickListener {
            showBuildLog()
        }

        // Observe build status
        lifecycleScope.launch {
            smartBuildManager.buildStatus.collect { status ->
                updateBuildStatus(status)
            }
        }
    }

    private fun loadConfiguration() {
        // Show current project path
        val projectPath = projectConfig.getProjectPath()
        projectPathText.text = projectPath

        // Load branches
        loadBranches()

        // Load saved config
        val config = projectConfig.getAiBuildConfig()
        cleanBuildCheckbox.isChecked = config.cleanBuild
        autoInstallCheckbox.isChecked = config.autoInstall
        createPrCheckbox.isChecked = config.createPrOnFix
    }

    private fun loadBranches() {
        lifecycleScope.launch {
            try {
                val branches = listOf("master", "main", "develop") // TODO: Get from git
                val adapter = ArrayAdapter(
                    this@AiBuildActivity,
                    android.R.layout.simple_spinner_item,
                    branches
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                branchSpinner.adapter = adapter

                // Select default branch
                val defaultBranch = projectConfig.getDefaultBranch()
                val index = branches.indexOf(defaultBranch)
                if (index >= 0) {
                    branchSpinner.setSelection(index)
                }

            } catch (e: Exception) {
                android.util.Log.e("AiBuild", "Error loading branches: ${e.message}")
            }
        }
    }

    private fun checkAiAvailability() {
        lifecycleScope.launch {
            try {
                val (geminiAvailable, claudeAvailable) = aiBuildHelper.checkAvailability()

                geminiStatusText.text = if (geminiAvailable) {
                    geminiStatusText.setTextColor(getColor(android.R.color.holo_green_light))
                    "✅ Available (Free)"
                } else {
                    geminiStatusText.setTextColor(getColor(android.R.color.holo_red_light))
                    "❌ Not installed"
                }

                claudeStatusText.text = if (claudeAvailable) {
                    claudeStatusText.setTextColor(getColor(android.R.color.holo_green_light))
                    "✅ Available (Paid)"
                } else {
                    claudeStatusText.setTextColor(getColor(android.R.color.holo_red_light))
                    "❌ Not authenticated"
                }

            } catch (e: Exception) {
                android.util.Log.e("AiBuild", "Error checking AI: ${e.message}")
            }
        }
    }

    private fun startSmartBuild() {
        lifecycleScope.launch {
            try {
                // Disable button
                smartBuildButton.isEnabled = false
                smartBuildButton.text = "Building..."

                // Show status card
                buildStatusCard.visibility = View.VISIBLE
                currentBuildLog = ""

                // Get selected branch
                val branch = branchSpinner.selectedItem?.toString() ?: "master"

                // Create config from checkboxes
                val config = AiBuildConfig(
                    useGeminiFirst = true,
                    fallbackToClaude = true,
                    maxRetries = 3,
                    autoInstall = autoInstallCheckbox.isChecked,
                    createPrOnFix = createPrCheckbox.isChecked,
                    cleanBuild = cleanBuildCheckbox.isChecked
                )

                // Save config
                projectConfig.saveAiBuildConfig(config)

                // Start smart build
                val result = smartBuildManager.smartBuild(branch, config)

                // Handle result
                if (result.success) {
                    showSuccessDialog(result)

                    // Install APK if auto-install is enabled
                    if (config.autoInstall && result.apkPath != null) {
                        installApk(result.apkPath)
                    }
                } else {
                    showErrorDialog(result.errorLog ?: "Unknown error")
                }

            } catch (e: Exception) {
                android.util.Log.e("AiBuild", "Build error: ${e.message}", e)
                showErrorDialog(e.message ?: "Unknown error")
            } finally {
                smartBuildButton.isEnabled = true
                smartBuildButton.text = "🚀 Smart Build & Test"
            }
        }
    }

    private fun updateBuildStatus(status: BuildStatus) {
        buildStatusCard.visibility = View.VISIBLE

        when (status) {
            is BuildStatus.Idle -> {
                buildStatusText.text = "Idle"
                buildProgressBar.visibility = View.GONE
            }
            is BuildStatus.Syncing -> {
                buildStatusText.text = "🔄 Syncing from GitHub..."
                buildProgressBar.visibility = View.VISIBLE
                currentBuildLog += "\n[${System.currentTimeMillis()}] Syncing from GitHub..."
            }
            is BuildStatus.Building -> {
                buildStatusText.text = "🔨 Building APK..."
                buildProgressBar.visibility = View.VISIBLE
                currentBuildLog += "\n[${System.currentTimeMillis()}] Building APK..."
            }
            is BuildStatus.AiFixing -> {
                buildStatusText.text = "🤖 ${status.tier.displayName} is fixing errors (attempt ${status.attempt})..."
                buildProgressBar.visibility = View.VISIBLE
                currentBuildLog += "\n[${System.currentTimeMillis()}] AI fixing with ${status.tier.displayName}..."
            }
            is BuildStatus.CreatingPr -> {
                buildStatusText.text = "📤 Creating Pull Request..."
                buildProgressBar.visibility = View.VISIBLE
                currentBuildLog += "\n[${System.currentTimeMillis()}] Creating PR..."
            }
            is BuildStatus.Success -> {
                buildStatusText.text = "✅ Build successful! (${status.buildTime}ms)"
                buildProgressBar.visibility = View.GONE
                currentBuildLog += "\n[${System.currentTimeMillis()}] Build successful!"
            }
            is BuildStatus.Failed -> {
                buildStatusText.text = "❌ Build failed: ${status.error}"
                buildProgressBar.visibility = View.GONE
                currentBuildLog += "\n[${System.currentTimeMillis()}] Build failed: ${status.error}"
            }
        }
    }

    private fun showProjectLocationDialog() {
        val locations = ProjectConfig.COMMON_LOCATIONS.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select Project Location")
            .setItems(locations) { _, which ->
                val selectedPath = locations[which]
                projectConfig.setProjectPath(selectedPath)
                projectPathText.text = selectedPath

                // Reinitialize managers with new path
                smartBuildManager = SmartBuildManager(this, selectedPath)
                prCreator = PrCreator(selectedPath)
                aiBuildHelper = AiBuildHelper(this, selectedPath)

                Toast.makeText(
                    this,
                    "Project location changed to: $selectedPath",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showBuildLog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_build_log, null)
        val logText = dialogView.findViewById<TextView>(R.id.buildLogText)
        val closeButton = dialogView.findViewById<MaterialButton>(R.id.closeButton)

        logText.text = if (currentBuildLog.isEmpty()) {
            "No logs yet..."
        } else {
            currentBuildLog
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        closeButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showSuccessDialog(result: BuildResult) {
        val message = buildString {
            appendLine("✅ Build completed successfully!")
            appendLine()
            appendLine("⏱️ Build time: ${result.buildTime}ms")
            result.apkPath?.let {
                appendLine("📦 APK: ${File(it).name}")
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Build Success")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setNeutralButton("Install") { _, _ ->
                result.apkPath?.let { installApk(it) }
            }
            .show()
    }

    private fun showErrorDialog(error: String) {
        AlertDialog.Builder(this)
            .setTitle("Build Failed")
            .setMessage("❌ Build failed:\n\n$error")
            .setPositiveButton("OK", null)
            .setNeutralButton("View Log") { _, _ ->
                showBuildLog()
            }
            .show()
    }

    private fun installApk(apkPath: String) {
        try {
            val apkFile = File(apkPath)
            val apkUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            startActivity(installIntent)

        } catch (e: Exception) {
            android.util.Log.e("AiBuild", "Error installing APK: ${e.message}")
            Toast.makeText(
                this,
                "Error installing APK: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
