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
    private lateinit var repositorySpinner: Spinner
    private lateinit var selectRepositoryButton: MaterialButton
    private lateinit var branchSpinner: Spinner
    private lateinit var geminiStatusText: TextView
    private lateinit var claudeStatusText: TextView
    private lateinit var setupCliButton: MaterialButton
    private lateinit var cleanBuildCheckbox: MaterialCheckBox
    private lateinit var autoInstallCheckbox: MaterialCheckBox
    private lateinit var createPrCheckbox: MaterialCheckBox
    private lateinit var smartBuildButton: MaterialButton
    private lateinit var buildStatusCard: MaterialCardView
    private lateinit var buildStatusText: TextView
    private lateinit var buildProgressBar: ProgressBar
    private lateinit var viewLogsButton: MaterialButton

    private var currentBuildLog: String = ""
    private var userRepositories: List<Repository> = emptyList()

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

        // Check if repository was passed from repository list
        val repoFullName = intent.getStringExtra("repoFullName")
        val owner = intent.getStringExtra("owner")
        val repo = intent.getStringExtra("repo")

        if (repoFullName != null && owner != null && repo != null) {
            // Repository was selected from list, auto-load it
            android.util.Log.d("AiBuild", "Auto-loading repository: $repoFullName")
            loadUserRepositoriesAndSelect(repoFullName)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun initializeViews() {
        projectPathText = findViewById(R.id.projectPathText)
        changeProjectButton = findViewById(R.id.changeProjectButton)
        repositorySpinner = findViewById(R.id.repositorySpinner)
        selectRepositoryButton = findViewById(R.id.selectRepositoryButton)
        branchSpinner = findViewById(R.id.branchSpinner)
        geminiStatusText = findViewById(R.id.geminiStatusText)
        claudeStatusText = findViewById(R.id.claudeStatusText)
        setupCliButton = findViewById(R.id.setupCliButton)
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

        selectRepositoryButton.setOnClickListener {
            loadUserRepositories()
        }

        repositorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (userRepositories.isNotEmpty() && position < userRepositories.size) {
                    val selectedRepo = userRepositories[position]
                    projectConfig.setSelectedRepository(selectedRepo.owner.login, selectedRepo.name)
                    // Reload branches for this repository
                    loadBranches()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        setupCliButton.setOnClickListener {
            startActivity(Intent(this, CliSetupActivity::class.java))
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

        // Initialize spinner with placeholder
        val placeholderAdapter = ArrayAdapter(
            this,
            R.layout.spinner_item_white,
            listOf("리포지토리를 선택하세요...")
        )
        placeholderAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_white)
        repositorySpinner.adapter = placeholderAdapter
        repositorySpinner.isEnabled = false // Disable until loaded

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
                    R.layout.spinner_item_white,
                    branches
                )
                adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_white)
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

    private fun loadUserRepositories() {
        lifecycleScope.launch {
            try {
                // Get authenticated user's repositories
                val prefs = getSharedPreferences("github_auth", MODE_PRIVATE)
                val token = prefs.getString("access_token", null)

                if (token.isNullOrEmpty()) {
                    Toast.makeText(
                        this@AiBuildActivity,
                        "Please login to GitHub first",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                // Fetch repositories from GitHub API
                val response = RetrofitClient.gitHubApiService.getUserRepositories(
                    "Bearer $token",
                    perPage = 100
                )

                if (response.isSuccessful && response.body() != null) {
                    userRepositories = response.body()!!

                    android.util.Log.d("AiBuild", "Loaded ${userRepositories.size} repositories")

                    if (userRepositories.isEmpty()) {
                        Toast.makeText(
                            this@AiBuildActivity,
                            "No repositories found. Create a repository on GitHub first.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@launch
                    }

                    // Create adapter for spinner with white text
                    val repoNames = userRepositories.map { it.fullName }
                    android.util.Log.d("AiBuild", "Repository names: $repoNames")

                    val adapter = ArrayAdapter(
                        this@AiBuildActivity,
                        R.layout.spinner_item_white,
                        repoNames
                    )
                    adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_white)
                    repositorySpinner.adapter = adapter
                    repositorySpinner.isEnabled = true // Enable after loading

                    // Select previously selected repository if any
                    val selectedRepo = projectConfig.getSelectedRepository()
                    android.util.Log.d("AiBuild", "Previously selected: $selectedRepo")

                    if (selectedRepo != null) {
                        val index = repoNames.indexOf(selectedRepo)
                        if (index >= 0) {
                            repositorySpinner.setSelection(index)
                        }
                    }

                    Toast.makeText(
                        this@AiBuildActivity,
                        "✓ Loaded ${userRepositories.size} repositories",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    val errorBody = response.errorBody()?.string()
                    android.util.Log.e("AiBuild", "Failed to load repositories: ${response.code()} - $errorBody")

                    Toast.makeText(
                        this@AiBuildActivity,
                        "Failed: ${response.code()} - Check if you're logged in to GitHub",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: Exception) {
                android.util.Log.e("AiBuild", "Error loading repositories: ${e.message}", e)
                Toast.makeText(
                    this@AiBuildActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Load repositories and auto-select the specified one
     */
    private fun loadUserRepositoriesAndSelect(repoFullName: String) {
        lifecycleScope.launch {
            try {
                val prefs = getSharedPreferences("github_auth", MODE_PRIVATE)
                val token = prefs.getString("access_token", null)

                if (token.isNullOrEmpty()) {
                    Toast.makeText(
                        this@AiBuildActivity,
                        "GitHub 로그인이 필요합니다",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                // Fetch repositories
                val response = RetrofitClient.gitHubApiService.getUserRepositories(
                    "Bearer $token",
                    perPage = 100
                )

                if (response.isSuccessful && response.body() != null) {
                    userRepositories = response.body()!!

                    android.util.Log.d("AiBuild", "Loaded ${userRepositories.size} repositories, selecting: $repoFullName")

                    if (userRepositories.isEmpty()) {
                        Toast.makeText(
                            this@AiBuildActivity,
                            "리포지토리가 없습니다",
                            Toast.LENGTH_LONG
                        ).show()
                        return@launch
                    }

                    val repoNames = userRepositories.map { it.fullName }
                    val adapter = ArrayAdapter(
                        this@AiBuildActivity,
                        R.layout.spinner_item_white,
                        repoNames
                    )
                    adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_white)
                    repositorySpinner.adapter = adapter
                    repositorySpinner.isEnabled = true // Enable after loading

                    // Auto-select the passed repository
                    val index = repoNames.indexOf(repoFullName)
                    if (index >= 0) {
                        repositorySpinner.setSelection(index)
                        android.util.Log.d("AiBuild", "Selected repository at index: $index")
                        Toast.makeText(
                            this@AiBuildActivity,
                            "✓ $repoFullName 선택됨",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        android.util.Log.w("AiBuild", "Repository $repoFullName not found in list")
                        Toast.makeText(
                            this@AiBuildActivity,
                            "리포지토리를 찾을 수 없습니다: $repoFullName",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                } else {
                    android.util.Log.e("AiBuild", "Failed to load repositories: ${response.code()}")
                    Toast.makeText(
                        this@AiBuildActivity,
                        "리포지토리 로딩 실패: ${response.code()}",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: Exception) {
                android.util.Log.e("AiBuild", "Error: ${e.message}", e)
                Toast.makeText(
                    this@AiBuildActivity,
                    "에러: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
