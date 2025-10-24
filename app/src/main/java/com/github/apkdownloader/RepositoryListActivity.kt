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
                    val apkCount = reposWithApk.count { it.apkInfo != null }
                    repoCountText.text = "📦 ${allRepositories.size} repositories (${apkCount} with APKs)"

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
        return repos.map { repo ->
            try {
                // Check latest release for APK files
                val response = RetrofitClient.gitHubApiService.getRepositoryReleases(
                    "Bearer $token",
                    repo.owner.login,
                    repo.name,
                    perPage = 1
                )

                if (response.isSuccessful) {
                    val releases = response.body() ?: emptyList()
                    val latestRelease = releases.firstOrNull()

                    if (latestRelease != null) {
                        // Find first APK file in release assets
                        val apkAsset = latestRelease.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }

                        if (apkAsset != null) {
                            val apkInfo = ApkInfo(
                                fileName = apkAsset.name,
                                downloadUrl = apkAsset.browserDownloadUrl,
                                location = "Release: ${latestRelease.tagName}",
                                size = apkAsset.size
                            )
                            RepositoryWithApk(repo, apkInfo)
                        } else {
                            RepositoryWithApk(repo, null)
                        }
                    } else {
                        RepositoryWithApk(repo, null)
                    }
                } else {
                    RepositoryWithApk(repo, null)
                }
            } catch (e: Exception) {
                android.util.Log.e("ApkCheck", "Error checking ${repo.fullName}: ${e.message}")
                RepositoryWithApk(repo, null)
            }
        }
    }

    private fun downloadApk(apkInfo: ApkInfo) {
        Toast.makeText(
            this,
            "Downloading ${apkInfo.fileName}...",
            Toast.LENGTH_SHORT
        ).show()

        // Create temporary Asset object from ApkInfo
        val tempAsset = Asset(
            id = 0,
            name = apkInfo.fileName,
            browserDownloadUrl = apkInfo.downloadUrl,
            size = apkInfo.size,
            contentType = "application/vnd.android.package-archive"
        )

        // Use existing ApkDownloader
        ApkDownloader(this).downloadApk(tempAsset, "")
    }
}
