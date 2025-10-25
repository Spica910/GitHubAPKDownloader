package com.github.apkdownloader

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class ReleasesActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: TextView
    private lateinit var allApksAdapter: AllApksAdapter

    private var owner: String = ""
    private var repo: String = ""

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_releases)

        owner = intent.getStringExtra("owner") ?: ""
        repo = intent.getStringExtra("repo") ?: ""
        val repoFullName = intent.getStringExtra("repoFullName") ?: "$owner/$repo"

        supportActionBar?.title = repoFullName
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        recyclerView = findViewById(R.id.releasesRecyclerView)
        progressBar = findViewById(R.id.progressBar)
        emptyView = findViewById(R.id.emptyView)

        allApksAdapter = AllApksAdapter(
            onDownloadClick = { apkInfo ->
                downloadApk(apkInfo)
            },
            onInfoClick = { apkInfo ->
                showApkInfo(apkInfo)
            }
        )

        recyclerView.adapter = allApksAdapter

        checkPermissionsAndLoadReleases()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun checkPermissionsAndLoadReleases() {
        // For Android 10 (API 29) and above, we use scoped storage
        // For Android 6-9, we need WRITE_EXTERNAL_STORAGE permission
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    PERMISSION_REQUEST_CODE
                )
            } else {
                loadReleases()
            }
        } else {
            loadReleases()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadReleases()
            } else {
                Toast.makeText(
                    this,
                    "Storage permission required to download files",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun loadReleases() {
        progressBar.visibility = View.VISIBLE
        emptyView.visibility = View.GONE

        val token = GitHubAuthHelper.getToken(this) ?: return

        lifecycleScope.launch {
            try {
                val allApks = mutableListOf<ApkInfo>()

                // 1. Scan all folders for APK files
                android.util.Log.d("AllApks", "Scanning folders in $owner/$repo")
                try {
                    val branchesResponse = RetrofitClient.gitHubApiService.getRepositoryBranches(
                        "Bearer $token",
                        owner,
                        repo
                    )

                    if (branchesResponse.isSuccessful) {
                        val branches = branchesResponse.body() ?: emptyList()
                        val defaultBranch = branches.firstOrNull { it.name == "main" || it.name == "master" }
                            ?: branches.firstOrNull()

                        if (defaultBranch != null) {
                            val treeResponse = RetrofitClient.gitHubApiService.getGitTree(
                                "Bearer $token",
                                owner,
                                repo,
                                defaultBranch.commit.sha,
                                recursive = 1
                            )

                            if (treeResponse.isSuccessful) {
                                val tree = treeResponse.body()
                                val apkFiles = tree?.tree?.filter {
                                    it.type == "blob" && it.path.endsWith(".apk", ignoreCase = true)
                                } ?: emptyList()

                                android.util.Log.d("AllApks", "Found ${apkFiles.size} APK files in folders")

                                apkFiles.forEach { apkFile ->
                                    val downloadUrl = "https://raw.githubusercontent.com/$owner/$repo/${defaultBranch.name}/${apkFile.path}"
                                    allApks.add(ApkInfo(
                                        fileName = apkFile.path.substringAfterLast("/"),
                                        downloadUrl = downloadUrl,
                                        location = "📁 Folder: ${apkFile.path}",
                                        size = apkFile.size ?: 0,
                                        source = ApkSource.RELEASE
                                    ))
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AllApks", "Error scanning folders: ${e.message}")
                }

                // 2. Load artifacts
                android.util.Log.d("AllApks", "Loading artifacts")
                try {
                    val artifactsResponse = RetrofitClient.gitHubApiService.getRepositoryArtifacts(
                        "Bearer $token",
                        owner,
                        repo,
                        30
                    )

                    if (artifactsResponse.isSuccessful) {
                        val artifacts = artifactsResponse.body()?.artifacts?.filter { !it.expired } ?: emptyList()
                        android.util.Log.d("AllApks", "Found ${artifacts.size} artifacts")

                        artifacts.forEach { artifact ->
                            if (artifact.name.contains("apk", ignoreCase = true) ||
                                artifact.name.contains("app", ignoreCase = true)) {
                                allApks.add(ApkInfo(
                                    fileName = artifact.name,
                                    downloadUrl = artifact.archiveDownloadUrl,
                                    location = "⚙️ Artifact: ${artifact.createdAt.take(10)}",
                                    size = artifact.sizeInBytes,
                                    source = ApkSource.ARTIFACT
                                ))
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AllApks", "Error loading artifacts: ${e.message}")
                }

                // 3. Load releases
                android.util.Log.d("AllApks", "Loading releases")
                try {
                    val releasesResponse = RetrofitClient.gitHubApiService.getRepositoryReleases(
                        "Bearer $token",
                        owner,
                        repo
                    )

                    if (releasesResponse.isSuccessful) {
                        val releases = releasesResponse.body() ?: emptyList()
                        android.util.Log.d("AllApks", "Found ${releases.size} releases")

                        releases.forEach { release ->
                            release.assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
                                .forEach { asset ->
                                    allApks.add(ApkInfo(
                                        fileName = asset.name,
                                        downloadUrl = asset.browserDownloadUrl,
                                        location = "🎉 Release: ${release.tagName}",
                                        size = asset.size,
                                        source = ApkSource.RELEASE
                                    ))
                                }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AllApks", "Error loading releases: ${e.message}")
                }

                progressBar.visibility = View.GONE

                if (allApks.isEmpty()) {
                    emptyView.text = "No APK files found in folders, artifacts, or releases"
                    emptyView.visibility = View.VISIBLE
                } else {
                    android.util.Log.d("AllApks", "Total APKs found: ${allApks.size}")
                    allApksAdapter.submitList(allApks)
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(
                    this@ReleasesActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun downloadApk(apkInfo: ApkInfo) {
        val downloader = ApkDownloader(this)
        val token = GitHubAuthHelper.getToken(this)
        downloader.downloadApkFromInfo(apkInfo, token)
    }

    private fun showApkInfo(apkInfo: ApkInfo) {
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val token = GitHubAuthHelper.getToken(this@ReleasesActivity)
                val extractor = ApkInfoExtractor(this@ReleasesActivity)

                // Extract APK package info
                val packageInfo = extractor.extractApkInfo(apkInfo.downloadUrl, token)

                progressBar.visibility = View.GONE

                if (packageInfo != null) {
                    // Update the apkInfo object
                    apkInfo.packageInfo = packageInfo

                    // Check for updates
                    val updateInfo = extractor.checkForUpdate(packageInfo.packageName, packageInfo.versionCode)
                    apkInfo.updateInfo = updateInfo

                    // Refresh the adapter to show the new info
                    allApksAdapter.notifyDataSetChanged()

                    // Show detailed info dialog
                    val message = buildString {
                        appendLine("📦 Package: ${packageInfo.packageName}")
                        appendLine("📱 App Name: ${packageInfo.appName}")
                        appendLine("🔢 Version: ${packageInfo.versionName} (${packageInfo.versionCode})")
                        appendLine("📊 Size: ${formatFileSize(apkInfo.size)}")
                        packageInfo.minSdkVersion?.let { appendLine("📱 Min SDK: $it") }
                        packageInfo.targetSdkVersion?.let { appendLine("🎯 Target SDK: $it") }
                        appendLine()
                        if (updateInfo.isInstalled) {
                            appendLine("✅ App is installed")
                            appendLine("Current version: ${updateInfo.installedVersionName} (${updateInfo.installedVersionCode})")
                            if (updateInfo.isUpdateAvailable) {
                                appendLine()
                                appendLine("🔄 UPDATE AVAILABLE!")
                            } else {
                                appendLine()
                                appendLine("✅ Already up to date")
                            }
                        } else {
                            appendLine("❌ App not installed")
                        }
                    }

                    android.app.AlertDialog.Builder(this@ReleasesActivity)
                        .setTitle("APK Information")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .setNeutralButton("Download") { _, _ ->
                            downloadApk(apkInfo)
                        }
                        .show()
                } else {
                    Toast.makeText(
                        this@ReleasesActivity,
                        "Failed to extract APK information",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(
                    this@ReleasesActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            else -> String.format("%.1f MB", size / (1024.0 * 1024.0))
        }
    }
}
