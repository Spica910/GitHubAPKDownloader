package com.github.apkdownloader

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RepositoryListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: TextView
    private lateinit var repoCountText: TextView
    private lateinit var buildTimeText: TextView
    private lateinit var repositoryAdapter: RepositoryAdapter

    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_repository_list)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        recyclerView = findViewById(R.id.repositoriesRecyclerView)
        progressBar = findViewById(R.id.progressBar)
        emptyView = findViewById(R.id.emptyView)
        repoCountText = findViewById(R.id.repoCountText)
        buildTimeText = findViewById(R.id.buildTimeText)

        // Display build time
        buildTimeText.text = "📅 Built: ${BuildConfig.BUILD_TIME}"

        repositoryAdapter = RepositoryAdapter(
            onBrowseFilesClick = { repository ->
                openFileTreeActivity(repository)
            },
            onViewReleasesClick = { repository ->
                openReleasesActivity(repository)
            },
            onDownloadApkClick = { apkInfo ->
                downloadApk(apkInfo)
            }
        )

        recyclerView.adapter = repositoryAdapter

        loadUserRepositories()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.repository_menu, menu)

        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { searchRepositories(it) }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(500)
                    if (!newText.isNullOrBlank() && newText.length >= 3) {
                        searchRepositories(newText)
                    } else if (newText.isNullOrBlank()) {
                        loadUserRepositories()
                    }
                }
                return true
            }
        })

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                loadUserRepositories()
                true
            }
            R.id.action_create_repo -> {
                showCreateRepositoryDialog()
                true
            }
            R.id.action_ai_build -> {
                startActivity(Intent(this, AiBuildActivity::class.java))
                true
            }
            R.id.action_logout -> {
                GitHubAuthHelper.clearToken(this)
                startActivity(Intent(this, MainActivity::class.java))
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadUserRepositories() {
        progressBar.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        repoCountText.text = "Loading repositories..."

        val token = GitHubAuthHelper.getToken(this) ?: return

        lifecycleScope.launch {
            try {
                // Check token scopes first
                checkTokenScopes(token)
                // Load all repositories with pagination
                val allRepositories = mutableListOf<Repository>()
                var page = 1
                var hasMore = true

                while (hasMore && page <= 10) { // Max 10 pages = 1000 repos
                    android.util.Log.d("RepoLoad", "=== Loading page $page ===")
                    repoCountText.text = "Loading page $page..."

                    val response = RetrofitClient.gitHubApiService.getUserRepositories(
                        "Bearer $token",
                        perPage = 100,
                        page = page
                    )

                    android.util.Log.d("RepoLoad", "Response code: ${response.code()}")

                    if (response.isSuccessful) {
                        val repos = response.body() ?: emptyList()
                        android.util.Log.d("RepoLoad", "Page $page: Received ${repos.size} repositories")

                        if (repos.isEmpty()) {
                            android.util.Log.d("RepoLoad", "Empty response, stopping pagination")
                            hasMore = false
                        } else {
                            allRepositories.addAll(repos)
                            android.util.Log.d("RepoLoad", "Total so far: ${allRepositories.size} repos")

                            // Update counter in real-time
                            repoCountText.text = "Loaded ${allRepositories.size} repositories so far..."

                            page++
                            // GitHub API returns fewer items on the last page
                            if (repos.size < 100) {
                                android.util.Log.d("RepoLoad", "Last page detected (${repos.size} < 100)")
                                hasMore = false
                            }
                        }
                    } else {
                        val errorBody = response.errorBody()?.string()
                        android.util.Log.e("RepoLoad", "Failed to load page $page")
                        android.util.Log.e("RepoLoad", "Error code: ${response.code()}")
                        android.util.Log.e("RepoLoad", "Error body: $errorBody")
                        hasMore = false
                    }
                }

                android.util.Log.d("RepoLoad", "=== FINAL: Total loaded: ${allRepositories.size} repos ===")

                progressBar.visibility = View.GONE

                if (allRepositories.isEmpty()) {
                    emptyView.visibility = View.VISIBLE
                    emptyView.text = "No repositories found"
                    repoCountText.text = "No repositories"
                } else {
                    // Sort by ID (most recent first)
                    val sortedRepos = allRepositories.sortedByDescending { it.id }

                    android.util.Log.d("RepoLoad", "Submitting ${sortedRepos.size} repos to adapter")

                    // Check for APKs in releases for each repository
                    repoCountText.text = "Checking for APK files..."
                    val reposWithApk = checkForApksInRepos(sortedRepos, token)

                    repositoryAdapter.submitList(reposWithApk)

                    // Update counter
                    val reposWithApks = reposWithApk.count { it.apkInfo != null || it.artifactApks.isNotEmpty() }
                    val totalApks = reposWithApk.sumOf {
                        (if (it.apkInfo != null) 1 else 0) + it.artifactApks.size
                    }
                    repoCountText.text = "📦 ${allRepositories.size} repos ($reposWithApks with $totalApks APKs)"

                    // Log first 10 repo names
                    android.util.Log.d("RepoLoad", "First 10 repos:")
                    sortedRepos.take(10).forEachIndexed { index, repo ->
                        android.util.Log.d("RepoLoad", "${index + 1}. ${repo.fullName}")
                    }
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(
                    this@RepositoryListActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun searchRepositories(query: String) {
        progressBar.visibility = View.VISIBLE
        emptyView.visibility = View.GONE

        val token = GitHubAuthHelper.getToken(this) ?: return

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.gitHubApiService.searchRepositories(
                    "Bearer $token",
                    query
                )

                progressBar.visibility = View.GONE

                if (response.isSuccessful) {
                    val repositories = response.body()?.items ?: emptyList()
                    if (repositories.isEmpty()) {
                        emptyView.visibility = View.VISIBLE
                    } else {
                        // Convert to RepositoryWithApk (without APK check for search results)
                        val reposWithApk = repositories.map { RepositoryWithApk(it, null) }
                        repositoryAdapter.submitList(reposWithApk)
                    }
                } else {
                    Toast.makeText(
                        this@RepositoryListActivity,
                        "Search failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(
                    this@RepositoryListActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun openReleasesActivity(repository: Repository) {
        val intent = Intent(this, ReleasesActivity::class.java).apply {
            putExtra("owner", repository.owner.login)
            putExtra("repo", repository.name)
            putExtra("repoFullName", repository.fullName)
        }
        startActivity(intent)
    }

    private fun openFileTreeActivity(repository: Repository) {
        val intent = Intent(this, FileTreeActivity::class.java).apply {
            putExtra("owner", repository.owner.login)
            putExtra("repo", repository.name)
            putExtra("repoFullName", repository.fullName)
        }
        startActivity(intent)
    }

    private fun showCreateRepositoryDialog() {
        val dialogView = layoutInflater.inflate(android.R.layout.simple_list_item_2, null)
        val nameInput = android.widget.EditText(this).apply {
            hint = "Repository name"
            textSize = 16f
            setPadding(16, 16, 16, 16)
        }
        val descInput = android.widget.EditText(this).apply {
            hint = "Description (optional)"
            textSize = 14f
            setPadding(16, 8, 16, 16)
        }

        val infoText = android.widget.TextView(this).apply {
            text = "Note: Repository will be created as public"
            textSize = 12f
            setPadding(16, 8, 16, 16)
            setTextColor(android.graphics.Color.GRAY)
        }

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(nameInput)
            addView(descInput)
            addView(infoText)
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Create New Repository")
            .setView(layout)
            .setPositiveButton("Create") { _, _ ->
                val name = nameInput.text.toString().trim()
                val description = descInput.text.toString().trim()

                if (name.isNotEmpty()) {
                    createRepository(name, description.ifEmpty { null }, false)
                } else {
                    Toast.makeText(this, "Repository name is required", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private suspend fun checkTokenScopes(token: String) {
        try {
            val client = okhttp3.OkHttpClient()
            val request = okhttp3.Request.Builder()
                .url("https://api.github.com/user")
                .addHeader("Authorization", "Bearer $token")
                .build()

            val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                client.newCall(request).execute()
            }

            val scopes = response.header("X-OAuth-Scopes") ?: "none"
            android.util.Log.d("TokenScopes", "Current scopes: $scopes")

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                Toast.makeText(
                    this@RepositoryListActivity,
                    "Token scopes: $scopes",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("TokenScopes", "Error checking scopes", e)
        }
    }

    private fun createRepository(name: String, description: String?, isPrivate: Boolean) {
        progressBar.visibility = View.VISIBLE
        val token = GitHubAuthHelper.getToken(this) ?: return

        lifecycleScope.launch {
            try {
                val request = CreateRepositoryRequest(
                    name = name,
                    description = description,
                    isPrivate = isPrivate,
                    autoInit = true
                )

                android.util.Log.d("CreateRepo", "Creating repository: $name, private: $isPrivate")

                val response = RetrofitClient.gitHubApiService.createRepository(
                    "Bearer $token",
                    request
                )

                progressBar.visibility = View.GONE

                android.util.Log.d("CreateRepo", "Response code: ${response.code()}")

                if (response.isSuccessful) {
                    Toast.makeText(
                        this@RepositoryListActivity,
                        "Repository created successfully!",
                        Toast.LENGTH_SHORT
                    ).show()
                    loadUserRepositories()
                } else {
                    val errorBody = response.errorBody()?.string()
                    android.util.Log.e("CreateRepo", "Error: $errorBody")
                    Toast.makeText(
                        this@RepositoryListActivity,
                        "Failed: ${response.code()} - $errorBody",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                android.util.Log.e("CreateRepo", "Exception", e)
                Toast.makeText(
                    this@RepositoryListActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private suspend fun checkForApksInRepos(repos: List<Repository>, token: String): List<RepositoryWithApk> {
        return repos.mapIndexed { index, repo ->
            try {
                var releaseApk: ApkInfo? = null
                val artifactApks = mutableListOf<ApkInfo>()
                val folderApks = mutableListOf<ApkInfo>()

                // For FIRST repository only: scan all folders for APK files
                if (index == 0) {
                    android.util.Log.d("ApkCheck", "Scanning all folders in ${repo.fullName}")

                    // Get default branch
                    val branchesResponse = RetrofitClient.gitHubApiService.getRepositoryBranches(
                        "Bearer $token",
                        repo.owner.login,
                        repo.name
                    )

                    if (branchesResponse.isSuccessful) {
                        val branches = branchesResponse.body() ?: emptyList()
                        val defaultBranch = branches.firstOrNull { it.name == "main" || it.name == "master" }
                            ?: branches.firstOrNull()

                        if (defaultBranch != null) {
                            // Get git tree recursively
                            val treeResponse = RetrofitClient.gitHubApiService.getGitTree(
                                "Bearer $token",
                                repo.owner.login,
                                repo.name,
                                defaultBranch.commit.sha,
                                recursive = 1
                            )

                            if (treeResponse.isSuccessful) {
                                val tree = treeResponse.body()
                                val apkFiles = tree?.tree?.filter {
                                    it.type == "blob" && it.path.endsWith(".apk", ignoreCase = true)
                                } ?: emptyList()

                                android.util.Log.d("ApkCheck", "Found ${apkFiles.size} APK files in folders")

                                apkFiles.take(5).forEach { apkFile ->
                                    val downloadUrl = "https://raw.githubusercontent.com/${repo.owner.login}/${repo.name}/${defaultBranch.name}/${apkFile.path}"
                                    folderApks.add(ApkInfo(
                                        fileName = apkFile.path.substringAfterLast("/"),
                                        downloadUrl = downloadUrl,
                                        location = "📁 ${apkFile.path}",
                                        size = apkFile.size ?: 0,
                                        source = ApkSource.RELEASE
                                    ))
                                }
                            }
                        }
                    }
                }

                // Check latest release for APK files
                val releaseResponse = RetrofitClient.gitHubApiService.getRepositoryReleases(
                    "Bearer $token",
                    repo.owner.login,
                    repo.name,
                    perPage = 1
                )

                if (releaseResponse.isSuccessful) {
                    val releases = releaseResponse.body() ?: emptyList()
                    val latestRelease = releases.firstOrNull()

                    if (latestRelease != null) {
                        val apkAsset = latestRelease.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }

                        if (apkAsset != null) {
                            releaseApk = ApkInfo(
                                fileName = apkAsset.name,
                                downloadUrl = apkAsset.browserDownloadUrl,
                                location = "🎉 Release: ${latestRelease.tagName}",
                                size = apkAsset.size,
                                source = ApkSource.RELEASE
                            )
                        }
                    }
                }

                // Check GitHub Actions artifacts
                val artifactsResponse = RetrofitClient.gitHubApiService.getRepositoryArtifacts(
                    "Bearer $token",
                    repo.owner.login,
                    repo.name,
                    perPage = 10
                )

                if (artifactsResponse.isSuccessful) {
                    val artifacts = artifactsResponse.body()?.artifacts ?: emptyList()

                    artifacts.filter {
                        !it.expired && it.name.contains("apk", ignoreCase = true)
                    }.take(3).forEach { artifact ->
                        artifactApks.add(ApkInfo(
                            fileName = artifact.name,
                            downloadUrl = artifact.archiveDownloadUrl,
                            location = "⚙️ Artifact: ${artifact.name}",
                            size = artifact.sizeInBytes,
                            source = ApkSource.ARTIFACT
                        ))
                    }
                }

                // Combine all APKs: folder APKs + release + artifacts
                val allArtifactApks = folderApks + artifactApks

                RepositoryWithApk(repo, releaseApk, allArtifactApks)
            } catch (e: Exception) {
                android.util.Log.e("ApkCheck", "Error checking ${repo.fullName}: ${e.message}")
                e.printStackTrace()
                RepositoryWithApk(repo, null, emptyList())
            }
        }
    }

    private fun downloadApk(apkInfo: ApkInfo) {
        val token = GitHubAuthHelper.getToken(this)

        Toast.makeText(
            this,
            "Downloading ${apkInfo.fileName}...",
            Toast.LENGTH_SHORT
        ).show()

        // Use ApkDownloader with new method
        ApkDownloader(this).downloadApkFromInfo(apkInfo, token)
    }
}
