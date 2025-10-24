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
    private lateinit var releasesAdapter: ReleasesAdapter

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

        releasesAdapter = ReleasesAdapter { asset, release ->
            downloadApk(asset, release)
        }

        recyclerView.adapter = releasesAdapter

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
                val response = RetrofitClient.gitHubApiService.getRepositoryReleases(
                    "Bearer $token",
                    owner,
                    repo
                )

                progressBar.visibility = View.GONE

                if (response.isSuccessful) {
                    val releases = response.body() ?: emptyList()
                    // Filter releases that have APK files
                    val releasesWithApks = releases.filter { release ->
                        release.assets.any { it.name.endsWith(".apk", ignoreCase = true) }
                    }

                    if (releasesWithApks.isEmpty()) {
                        emptyView.text = "No releases with APK files found"
                        emptyView.visibility = View.VISIBLE
                    } else {
                        releasesAdapter.submitList(releasesWithApks)
                    }
                } else {
                    Toast.makeText(
                        this@ReleasesActivity,
                        "Failed to load releases",
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

    private fun downloadApk(asset: Asset, release: Release) {
        val downloader = ApkDownloader(this)
        downloader.downloadApk(asset, "$repo-${release.tagName}")
    }
}
