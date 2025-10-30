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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
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
    private lateinit var cloneRepoButton: MaterialButton
    private lateinit var syncRepoButton: MaterialButton
    private lateinit var openFolderButton: MaterialButton
    private lateinit var quickBuildButton: MaterialButton
    private lateinit var openTerminalButton: MaterialButton
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
        } else {
            // No repository passed, auto-load all repositories
            android.util.Log.d("AiBuild", "Auto-loading all repositories")
            loadUserRepositories()
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
        cloneRepoButton = findViewById(R.id.cloneRepoButton)
        syncRepoButton = findViewById(R.id.syncRepoButton)
        openFolderButton = findViewById(R.id.openFolderButton)
        quickBuildButton = findViewById(R.id.quickBuildButton)
        openTerminalButton = findViewById(R.id.openTerminalButton)
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

        cloneRepoButton.setOnClickListener {
            cloneSelectedRepository()
        }

        syncRepoButton.setOnClickListener {
            syncSelectedRepository()
        }

        openFolderButton.setOnClickListener {
            openSelectedRepositoryFolder()
        }

        quickBuildButton.setOnClickListener {
            quickBuildSelectedRepository()
        }

        openTerminalButton.setOnClickListener {
            openTerminalInFolder()
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

        // Start observing build status
        observeBuildStatus()
    }

    /**
     * Observe build status from SmartBuildManager
     */
    private fun observeBuildStatus() {
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
        val copyLogButton = dialogView.findViewById<MaterialButton>(R.id.copyLogButton)
        val closeButton = dialogView.findViewById<MaterialButton>(R.id.closeButton)

        logText.text = if (currentBuildLog.isEmpty()) {
            "No logs yet..."
        } else {
            currentBuildLog
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        copyLogButton.setOnClickListener {
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Build Log", currentBuildLog)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "📋 Build log copied to clipboard!", Toast.LENGTH_SHORT).show()
        }

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
                val token = GitHubAuthHelper.getToken(this@AiBuildActivity)

                android.util.Log.d("AiBuild", "Token: ${if (token != null) "Found" else "NULL"}")

                if (token.isNullOrEmpty()) {
                    android.util.Log.e("AiBuild", "No GitHub token found!")

                    // Keep placeholder visible
                    val errorAdapter = ArrayAdapter(
                        this@AiBuildActivity,
                        R.layout.spinner_item_white,
                        listOf("❌ GitHub 로그인 필요")
                    )
                    errorAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_white)
                    repositorySpinner.adapter = errorAdapter
                    repositorySpinner.isEnabled = false

                    Toast.makeText(
                        this@AiBuildActivity,
                        "GitHub 로그인이 필요합니다.\n메인 화면에서 로그인하세요.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                // Show loading state
                val loadingAdapter = ArrayAdapter(
                    this@AiBuildActivity,
                    R.layout.spinner_item_white,
                    listOf("⏳ 리포지토리 로딩 중...")
                )
                loadingAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_white)
                repositorySpinner.adapter = loadingAdapter
                repositorySpinner.isEnabled = false

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

                    // Show error in spinner
                    val errorAdapter = ArrayAdapter(
                        this@AiBuildActivity,
                        R.layout.spinner_item_white,
                        listOf("❌ 로딩 실패: ${response.code()}")
                    )
                    errorAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_white)
                    repositorySpinner.adapter = errorAdapter
                    repositorySpinner.isEnabled = false

                    Toast.makeText(
                        this@AiBuildActivity,
                        "리포지토리 로딩 실패: ${response.code()}\nGitHub 로그인 상태를 확인하세요",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: Exception) {
                android.util.Log.e("AiBuild", "Error loading repositories: ${e.message}", e)

                // Show error in spinner
                val errorAdapter = ArrayAdapter(
                    this@AiBuildActivity,
                    R.layout.spinner_item_white,
                    listOf("❌ 에러 발생")
                )
                errorAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_white)
                repositorySpinner.adapter = errorAdapter
                repositorySpinner.isEnabled = false

                Toast.makeText(
                    this@AiBuildActivity,
                    "에러: ${e.message}",
                    Toast.LENGTH_LONG
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
                val token = GitHubAuthHelper.getToken(this@AiBuildActivity)

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

    /**
     * Clone selected repository from GitHub
     */
    private fun cloneSelectedRepository() {
        lifecycleScope.launch {
            try {
                if (userRepositories.isEmpty()) {
                    Toast.makeText(
                        this@AiBuildActivity,
                        "먼저 리포지토리를 선택하세요",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                val selectedIndex = repositorySpinner.selectedItemPosition
                if (selectedIndex < 0 || selectedIndex >= userRepositories.size) {
                    Toast.makeText(
                        this@AiBuildActivity,
                        "리포지토리를 선택하세요",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                val selectedRepo = userRepositories[selectedIndex]
                val targetBranch = branchSpinner.selectedItem?.toString() ?: "main"

                // Disable button
                cloneRepoButton.isEnabled = false
                cloneRepoButton.text = "Cloning..."

                // Show progress
                buildStatusCard.visibility = View.VISIBLE
                buildStatusText.text = "📥 Cloning ${selectedRepo.fullName}..."
                buildProgressBar.visibility = View.VISIBLE

                // Clone repository
                val projectPath = projectConfig.getProjectPath()
                val projectName = "${selectedRepo.owner.login}/${selectedRepo.name}"
                val targetPath = File("$projectPath/$projectName")

                // Check if folder already exists
                if (targetPath.exists()) {
                    // Show confirmation dialog
                    withContext(Dispatchers.Main) {
                        cloneRepoButton.isEnabled = true
                        cloneRepoButton.text = "📥 Clone"
                        buildStatusCard.visibility = View.GONE

                        AlertDialog.Builder(this@AiBuildActivity)
                            .setTitle("⚠️ Folder Already Exists")
                            .setMessage("The project folder already exists:\n\n${targetPath.absolutePath}\n\nDo you want to delete it and clone again?")
                            .setPositiveButton("Cancel", null)
                            .setNegativeButton("🗑️ Delete & Re-clone") { _, _ ->
                                // User confirmed - delete and re-clone
                                lifecycleScope.launch {
                                    performClone(selectedRepo, projectName, targetPath, true)
                                }
                            }
                            .show()
                    }
                    return@launch
                }

                // Proceed with clone
                performClone(selectedRepo, projectName, targetPath, false)

            } catch (e: Exception) {
                android.util.Log.e("AiBuild", "Clone error: ${e.message}", e)
                buildStatusText.text = "❌ Clone failed: ${e.message}"
                buildProgressBar.visibility = View.GONE

                // Show error popup with log copy button
                val repoName = if (userRepositories.isNotEmpty()) {
                    userRepositories.getOrNull(repositorySpinner.selectedItemPosition)?.fullName ?: "Unknown"
                } else {
                    "Unknown"
                }
                showCloneErrorDialog(repoName, e.message ?: "Unknown error")
            } finally {
                cloneRepoButton.isEnabled = true
                cloneRepoButton.text = "📥 Clone"
            }
        }
    }

    /**
     * Perform the actual clone operation
     */
    private suspend fun performClone(
        selectedRepo: com.github.apkdownloader.Repository,
        projectName: String,
        targetPath: File,
        deleteFirst: Boolean
    ) {
        try {
            // Disable button
            cloneRepoButton.isEnabled = false
            cloneRepoButton.text = "Cloning..."

            // Show progress
            buildStatusCard.visibility = View.VISIBLE
            buildProgressBar.visibility = View.VISIBLE

            withContext(Dispatchers.IO) {
                // Delete existing folder if requested
                if (deleteFirst && targetPath.exists()) {
                    android.util.Log.d("AiBuild", "🗑️ Deleting existing folder: ${targetPath.absolutePath}")
                    withContext(Dispatchers.Main) {
                        buildStatusText.text = "🗑️ Deleting existing folder..."
                    }
                    targetPath.deleteRecursively()
                    android.util.Log.d("AiBuild", "✅ Folder deleted")
                }

                // Update status
                withContext(Dispatchers.Main) {
                    buildStatusText.text = "📥 Cloning ${selectedRepo.fullName}..."
                }

                // Build clone URL from htmlUrl
                val cloneUrl = "${selectedRepo.htmlUrl}.git"
                android.util.Log.d("AiBuild", "⬇️ Cloning $cloneUrl to ${targetPath.absolutePath}")

                // Use ProjectConfig.cloneRepository()
                val result = projectConfig.cloneRepository(cloneUrl, projectName)

                withContext(Dispatchers.Main) {
                    if (result.isSuccess) {
                        val clonedPath = result.getOrNull()
                        buildStatusText.text = "✅ Repository cloned successfully!"
                        buildProgressBar.visibility = View.GONE
                        android.util.Log.d("AiBuild", "✅ Cloned to: $clonedPath")

                        // Update project config
                        projectConfig.setSelectedRepository(selectedRepo.owner.login, selectedRepo.name)

                        // Show success popup
                        showCloneSuccessDialog(selectedRepo.fullName, clonedPath)
                    } else {
                        val error = result.exceptionOrNull()?.message ?: "Unknown error"
                        buildStatusText.text = "❌ Clone failed: $error"
                        buildProgressBar.visibility = View.GONE
                        android.util.Log.e("AiBuild", "❌ Clone failed: $error")

                        // Show error popup with log copy button
                        showCloneErrorDialog(selectedRepo.fullName, error)
                    }
                }
            }

        } catch (e: Exception) {
            android.util.Log.e("AiBuild", "Clone error: ${e.message}", e)
            withContext(Dispatchers.Main) {
                buildStatusText.text = "❌ Clone failed: ${e.message}"
                buildProgressBar.visibility = View.GONE
                showCloneErrorDialog(selectedRepo.fullName, e.message ?: "Unknown error")
            }
        } finally {
            withContext(Dispatchers.Main) {
                cloneRepoButton.isEnabled = true
                cloneRepoButton.text = "📥 Clone"
            }
        }
    }

    private fun syncSelectedRepository() {
        lifecycleScope.launch {
            try {
                if (userRepositories.isEmpty()) {
                    Toast.makeText(
                        this@AiBuildActivity,
                        "먼저 리포지토리를 선택하세요",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                val selectedIndex = repositorySpinner.selectedItemPosition
                if (selectedIndex < 0 || selectedIndex >= userRepositories.size) {
                    Toast.makeText(
                        this@AiBuildActivity,
                        "리포지토리를 선택하세요",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                val selectedRepo = userRepositories[selectedIndex]

                // Disable button
                syncRepoButton.isEnabled = false
                syncRepoButton.text = "Syncing..."

                // Show progress
                buildStatusCard.visibility = View.VISIBLE
                buildStatusText.text = "🔄 Syncing ${selectedRepo.fullName}..."
                buildProgressBar.visibility = View.VISIBLE

                // Sync repository (git pull)
                val projectPath = projectConfig.getProjectPath()
                val projectName = "${selectedRepo.owner.login}/${selectedRepo.name}"
                val repoPath = "$projectPath/$projectName"

                // Check if repo exists locally
                val repoDir = java.io.File(repoPath)
                if (!repoDir.exists()) {
                    buildStatusText.text = "❌ Repository not cloned yet"
                    buildProgressBar.visibility = View.GONE
                    android.util.Log.e("AiBuild", "Repository not found at: $repoPath")
                    Toast.makeText(
                        this@AiBuildActivity,
                        "Repository not found locally. Please clone it first.\nExpected path: $repoPath",
                        Toast.LENGTH_LONG
                    ).show()
                    syncRepoButton.isEnabled = true
                    syncRepoButton.text = "🔄 Sync"
                    return@launch
                }

                android.util.Log.d("AiBuild", "🔄 Syncing repository at $repoPath")

                // Execute git pull using AppTerminal
                val branch = branchSpinner.selectedItem?.toString() ?: "main"

                val success = withContext(Dispatchers.IO) {
                    try {
                        val terminal = AppTerminal(this@AiBuildActivity)
                        // Use full path to git to ensure it's found
                        val gitPath = "/data/data/com.termux/files/usr/bin/git"
                        val command = "$gitPath pull origin $branch"
                        android.util.Log.d("AiBuild", "Executing: $command in $repoPath")

                        val result = terminal.execute(command, repoDir)

                        android.util.Log.d("AiBuild", "Git pull exit code: ${result.exitCode}")
                        android.util.Log.d("AiBuild", "Git pull output: ${result.output}")

                        result.success
                    } catch (e: Exception) {
                        android.util.Log.e("AiBuild", "Git pull error: ${e.message}", e)
                        false
                    }
                }

                if (success) {
                    buildStatusText.text = "✅ Repository synced!"
                    buildProgressBar.visibility = View.GONE
                    Toast.makeText(
                        this@AiBuildActivity,
                        "✓ ${selectedRepo.fullName} synced with GitHub!",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    buildStatusText.text = "❌ Sync failed"
                    buildProgressBar.visibility = View.GONE
                    Toast.makeText(
                        this@AiBuildActivity,
                        "Failed to sync repository. Check logs.",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: Exception) {
                android.util.Log.e("AiBuild", "Sync error: ${e.message}", e)
                buildStatusText.text = "❌ Sync failed: ${e.message}"
                buildProgressBar.visibility = View.GONE
                Toast.makeText(
                    this@AiBuildActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                syncRepoButton.isEnabled = true
                syncRepoButton.text = "🔄 Sync"
            }
        }
    }

    /**
     * Open selected repository folder
     */
    private fun openSelectedRepositoryFolder() {
        if (userRepositories.isEmpty()) {
            Toast.makeText(this, "먼저 리포지토리를 선택하세요", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedIndex = repositorySpinner.selectedItemPosition
        if (selectedIndex < 0 || selectedIndex >= userRepositories.size) {
            Toast.makeText(this, "리포지토리를 선택하세요", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedRepo = userRepositories[selectedIndex]
        val projectPath = projectConfig.getProjectPath()
        val repoPath = "$projectPath/${selectedRepo.owner.login}/${selectedRepo.name}"
        val repoDir = File(repoPath)

        if (!repoDir.exists()) {
            Toast.makeText(
                this,
                "Repository not found locally. Please clone it first.\nExpected path: $repoPath",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Open folder with file manager
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            val uri = android.net.Uri.parse(repoPath)
            intent.setDataAndType(uri, "resource/folder")
            startActivity(Intent.createChooser(intent, "Open folder"))
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open folder: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Open Termux terminal in selected repository folder
     */
    private fun openTerminalInFolder() {
        if (userRepositories.isEmpty()) {
            Toast.makeText(this, "먼저 리포지토리를 선택하세요", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedIndex = repositorySpinner.selectedItemPosition
        if (selectedIndex < 0 || selectedIndex >= userRepositories.size) {
            Toast.makeText(this, "리포지토리를 선택하세요", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedRepo = userRepositories[selectedIndex]
        val projectPath = projectConfig.getProjectPath()
        val repoPath = "$projectPath/${selectedRepo.owner.login}/${selectedRepo.name}"
        val repoDir = File(repoPath)

        if (!repoDir.exists()) {
            Toast.makeText(
                this,
                "Repository not found locally. Please clone it first.\nExpected path: $repoPath",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Open Termux and automatically cd to the repository folder
        try {
            // Use Termux RUN_COMMAND intent to execute cd command
            val intent = Intent()
            intent.setClassName("com.termux", "com.termux.app.RunCommandService")
            intent.action = "com.termux.RUN_COMMAND"

            // Execute: cd to directory and start bash
            val command = "cd \"$repoPath\" && exec bash"
            intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
            intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", repoPath)
            intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
            intent.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0") // Open new session

            // Start Termux service
            this.startService(intent)

            // Also open Termux app to show the terminal
            val openIntent = Intent()
            openIntent.setClassName("com.termux", "com.termux.app.TermuxActivity")
            openIntent.action = Intent.ACTION_MAIN
            openIntent.addCategory(Intent.CATEGORY_LAUNCHER)
            openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(openIntent)

            // Show success message
            Toast.makeText(
                this,
                "✅ Termux opened in:\n$repoPath",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            android.util.Log.e("AiBuild", "Failed to open Termux: ${e.message}", e)

            // Fallback: Just open Termux and show path to copy
            try {
                val fallbackIntent = Intent()
                fallbackIntent.setClassName("com.termux", "com.termux.app.TermuxActivity")
                fallbackIntent.action = Intent.ACTION_MAIN
                fallbackIntent.addCategory(Intent.CATEGORY_LAUNCHER)
                fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(fallbackIntent)

                // Copy path to clipboard
                val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Project Path", "cd \"$repoPath\"")
                clipboard.setPrimaryClip(clip)

                Toast.makeText(
                    this,
                    "📋 Command copied to clipboard!\nPaste in Termux:\ncd \"$repoPath\"",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e2: Exception) {
                Toast.makeText(
                    this,
                    "Cannot open Termux. Is it installed?",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Quick build selected repository (without AI retry)
     */
    private fun quickBuildSelectedRepository() {
        if (userRepositories.isEmpty()) {
            Toast.makeText(this, "먼저 리포지토리를 선택하세요", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedIndex = repositorySpinner.selectedItemPosition
        if (selectedIndex < 0 || selectedIndex >= userRepositories.size) {
            Toast.makeText(this, "리포지토리를 선택하세요", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedRepo = userRepositories[selectedIndex]
        val projectPath = projectConfig.getProjectPath()
        val repoPath = "$projectPath/${selectedRepo.owner.login}/${selectedRepo.name}"

        // Re-initialize SmartBuildManager with selected repository path
        android.util.Log.d("AiBuild", "Quick build for: $repoPath")
        smartBuildManager = SmartBuildManager(this, repoPath)

        // Re-observe build status with new manager
        observeBuildStatus()

        // Start build
        startSmartBuild()
    }

    /**
     * Show success dialog when clone is completed
     */
    private fun showCloneSuccessDialog(repoName: String, clonedPath: String?) {
        val message = buildString {
            appendLine("✅ Clone Completed Successfully!")
            appendLine()
            appendLine("Repository: $repoName")
            if (clonedPath != null) {
                appendLine()
                appendLine("Location:")
                appendLine(clonedPath)
            }
            appendLine()
            appendLine("Would you like to build this project now?")
        }

        AlertDialog.Builder(this)
            .setTitle("Clone Success")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setNeutralButton("Open Folder") { _, _ ->
                // Open file manager to the cloned folder
                if (clonedPath != null) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW)
                        val uri = android.net.Uri.parse(clonedPath)
                        intent.setDataAndType(uri, "resource/folder")
                        startActivity(Intent.createChooser(intent, "Open folder"))
                    } catch (e: Exception) {
                        Toast.makeText(this, "Cannot open folder", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("🚀 Build Now") { _, _ ->
                // Re-initialize SmartBuildManager with the cloned project path
                if (clonedPath != null) {
                    android.util.Log.d("AiBuild", "Re-initializing SmartBuildManager with path: $clonedPath")
                    smartBuildManager = SmartBuildManager(this, clonedPath)

                    // Re-observe build status with new manager
                    observeBuildStatus()

                    // Start build process for the cloned repository
                    android.util.Log.d("AiBuild", "Starting build after clone: $repoName")
                    startSmartBuild()
                } else {
                    Toast.makeText(this, "Cannot start build: Invalid project path", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    /**
     * Show error dialog when clone fails (with log copy button)
     */
    private fun showCloneErrorDialog(repoName: String, errorMessage: String) {
        val fullErrorLog = buildString {
            appendLine("❌ Clone Failed")
            appendLine()
            appendLine("Repository: $repoName")
            appendLine()
            appendLine("Error Details:")
            appendLine(errorMessage)
            appendLine()
            appendLine("Timestamp: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
        }

        AlertDialog.Builder(this)
            .setTitle("Clone Failed")
            .setMessage("Failed to clone repository:\n\n$repoName\n\nError: $errorMessage")
            .setPositiveButton("OK", null)
            .setNeutralButton("📋 Copy Log") { _, _ ->
                // Copy error log to clipboard
                val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Clone Error Log", fullErrorLog)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "📋 Error log copied to clipboard!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("View Full Log") { _, _ ->
                // Show full log in a dialog
                showFullErrorLogDialog(fullErrorLog)
            }
            .show()
    }

    /**
     * Show full error log in a scrollable dialog
     */
    private fun showFullErrorLogDialog(errorLog: String) {
        val scrollView = android.widget.ScrollView(this)
        val textView = android.widget.TextView(this).apply {
            text = errorLog
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(16, 16, 16, 16)
            setTextIsSelectable(true)
        }
        scrollView.addView(textView)

        AlertDialog.Builder(this)
            .setTitle("Full Error Log")
            .setView(scrollView)
            .setPositiveButton("Close", null)
            .setNeutralButton("📋 Copy") { _, _ ->
                val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Clone Error Log", errorLog)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "📋 Error log copied!", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
}
