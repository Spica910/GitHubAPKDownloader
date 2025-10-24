package com.github.apkdownloader

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FileTreeActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: TextView
    private lateinit var currentPathText: TextView
    private lateinit var fab: FloatingActionButton
    private lateinit var branchSpinner: Spinner
    private lateinit var fileTreeAdapter: FileTreeAdapter

    private var owner: String = ""
    private var repo: String = ""
    private var branch: String = "main"
    private var currentPath: String = ""
    private var allFiles: List<GitTreeItem> = emptyList()
    private var branches: List<GitBranch> = emptyList()

    companion object {
        private const val FILE_PICKER_REQUEST = 1001
        private const val FOLDER_PICKER_REQUEST = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_tree)

        owner = intent.getStringExtra("owner") ?: ""
        repo = intent.getStringExtra("repo") ?: ""
        val repoFullName = intent.getStringExtra("repoFullName") ?: "$owner/$repo"

        supportActionBar?.title = "Files: $repoFullName"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        recyclerView = findViewById(R.id.fileTreeRecyclerView)
        progressBar = findViewById(R.id.progressBar)
        emptyView = findViewById(R.id.emptyView)
        currentPathText = findViewById(R.id.currentPathText)
        fab = findViewById(R.id.fab)
        branchSpinner = findViewById(R.id.branchSpinner)

        fileTreeAdapter = FileTreeAdapter { item ->
            onFileItemClick(item)
        }

        recyclerView.adapter = fileTreeAdapter

        fab.setOnClickListener {
            showUploadDialog()
        }

        // Load branches first, then load file tree
        loadBranches()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun loadBranches() {
        val token = GitHubAuthHelper.getToken(this) ?: return

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.gitHubApiService.getRepositoryBranches(
                    "Bearer $token",
                    owner,
                    repo
                )

                if (response.isSuccessful) {
                    branches = response.body() ?: emptyList()

                    if (branches.isNotEmpty()) {
                        // Set up spinner adapter
                        val branchNames = branches.map { it.name }
                        val adapter = ArrayAdapter(
                            this@FileTreeActivity,
                            android.R.layout.simple_spinner_item,
                            branchNames
                        )
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        branchSpinner.adapter = adapter

                        // Set default branch
                        val defaultBranch = if (branchNames.contains("main")) {
                            "main"
                        } else if (branchNames.contains("master")) {
                            "master"
                        } else {
                            branchNames[0]
                        }
                        branch = defaultBranch
                        branchSpinner.setSelection(branchNames.indexOf(defaultBranch))

                        // Handle branch selection
                        branchSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                                val selectedBranch = branchNames[position]
                                if (selectedBranch != branch) {
                                    branch = selectedBranch
                                    currentPath = ""
                                    loadFileTree()
                                }
                            }

                            override fun onNothingSelected(parent: AdapterView<*>?) {}
                        }
                    }

                    // Load file tree with selected branch
                    loadFileTree()
                } else {
                    Toast.makeText(
                        this@FileTreeActivity,
                        "Failed to load branches",
                        Toast.LENGTH_SHORT
                    ).show()
                    // Still try to load file tree with default branch
                    loadFileTree()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@FileTreeActivity,
                    "Error loading branches: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                // Still try to load file tree with default branch
                loadFileTree()
            }
        }
    }

    private fun loadFileTree() {
        progressBar.visibility = View.VISIBLE
        emptyView.visibility = View.GONE

        val token = GitHubAuthHelper.getToken(this) ?: return

        lifecycleScope.launch {
            try {
                // First get the default branch
                val branchesResponse = RetrofitClient.gitHubApiService.getRepositoryBranches(
                    "Bearer $token",
                    owner,
                    repo
                )

                if (branchesResponse.isSuccessful) {
                    val branches = branchesResponse.body()
                    branch = branches?.firstOrNull()?.name ?: "main"
                    val treeSha = branches?.firstOrNull()?.commit?.sha ?: ""

                    // Get the file tree
                    val treeResponse = RetrofitClient.gitHubApiService.getGitTree(
                        "Bearer $token",
                        owner,
                        repo,
                        treeSha,
                        1
                    )

                    progressBar.visibility = View.GONE

                    if (treeResponse.isSuccessful) {
                        allFiles = treeResponse.body()?.tree ?: emptyList()
                        updateFileList()
                    } else {
                        showError("Failed to load file tree")
                    }
                } else {
                    progressBar.visibility = View.GONE
                    showError("Failed to load branches")
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                showError("Error: ${e.message}")
            }
        }
    }

    private fun updateFileList() {
        val prefix = if (currentPath.isEmpty()) "" else "$currentPath/"

        val filteredFiles = if (currentPath.isEmpty()) {
            // Show root level files and folders
            allFiles.filter { !it.path.contains("/") }
        } else {
            // Show files in current directory
            allFiles.filter {
                it.path.startsWith(prefix) &&
                it.path.substring(prefix.length).let { rest -> !rest.contains("/") }
            }
        }

        currentPathText.text = if (currentPath.isEmpty()) "/" else "/$currentPath"

        if (filteredFiles.isEmpty()) {
            emptyView.visibility = View.VISIBLE
        } else {
            fileTreeAdapter.submitList(filteredFiles)
        }
    }

    private fun onFileItemClick(item: GitTreeItem) {
        if (item.type == "tree") {
            // Navigate into folder
            currentPath = item.path
            updateFileList()
        } else {
            // Show file info or download
            showFileOptions(item)
        }
    }

    private fun showFileOptions(item: GitTreeItem) {
        AlertDialog.Builder(this)
            .setTitle(item.path.substringAfterLast("/"))
            .setMessage("Size: ${formatFileSize(item.size ?: 0)}\nPath: ${item.path}")
            .setPositiveButton("Download") { _, _ ->
                downloadFile(item)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun downloadFile(item: GitTreeItem) {
        Toast.makeText(this, "Downloading ${item.path}...", Toast.LENGTH_SHORT).show()

        val token = GitHubAuthHelper.getToken(this) ?: return

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.gitHubApiService.getFileContent(
                    "Bearer $token",
                    owner,
                    repo,
                    item.path,
                    branch
                )

                if (response.isSuccessful) {
                    val fileContent = response.body()
                    val downloadUrl = fileContent?.downloadUrl

                    if (downloadUrl != null) {
                        // Download using the download URL
                        Toast.makeText(
                            this@FileTreeActivity,
                            "File info loaded. Download URL available.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        this@FileTreeActivity,
                        "Failed to get file info",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@FileTreeActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showUploadDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_upload_file, null)
        val filePathInput = dialogView.findViewById<TextInputEditText>(R.id.filePathInput)
        val targetPathInput = dialogView.findViewById<TextInputEditText>(R.id.targetPathInput)
        val commitMessageInput = dialogView.findViewById<TextInputEditText>(R.id.commitMessageInput)
        val browseButton = dialogView.findViewById<Button>(R.id.browseFileButton)
        val browseFolderButton = dialogView.findViewById<Button>(R.id.browseFolderButton)
        val selectedFileInfo = dialogView.findViewById<TextView>(R.id.selectedFileInfo)

        var selectedFile: File? = null

        browseButton.setOnClickListener {
            val intent = Intent(this, FileBrowserActivity::class.java)
            startActivityForResult(intent, FILE_PICKER_REQUEST)
        }

        browseFolderButton.setOnClickListener {
            val intent = Intent(this, FileBrowserActivity::class.java)
            intent.putExtra("select_folder", true)
            startActivityForResult(intent, FOLDER_PICKER_REQUEST)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Upload File")
            .setView(dialogView)
            .setPositiveButton("Upload") { _, _ ->
                val localPath = filePathInput.text.toString()
                val targetPath = targetPathInput.text.toString()
                val commitMessage = commitMessageInput.text.toString()

                if (localPath.isNotEmpty() && targetPath.isNotEmpty()) {
                    uploadFile(localPath, targetPath, commitMessage)
                } else {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_PICKER_REQUEST && resultCode == Activity.RESULT_OK) {
            val filePath = data?.getStringExtra("selected_file")
            if (filePath != null) {
                // Update the dialog if it's still open
                Toast.makeText(this, "Selected file: $filePath", Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == FOLDER_PICKER_REQUEST && resultCode == Activity.RESULT_OK) {
            val folderPath = data?.getStringExtra("selected_folder")
            if (folderPath != null) {
                Toast.makeText(this, "Selected folder: $folderPath", Toast.LENGTH_LONG).show()
                uploadFolder(folderPath)
            }
        }
    }

    private fun uploadFolder(folderPath: String) {
        progressBar.visibility = View.VISIBLE

        val token = GitHubAuthHelper.getToken(this) ?: return
        val folder = File(folderPath)

        if (!folder.isDirectory) {
            Toast.makeText(this, "Not a valid folder", Toast.LENGTH_SHORT).show()
            progressBar.visibility = View.GONE
            return
        }

        lifecycleScope.launch {
            try {
                var uploadedCount = 0
                var failedCount = 0

                // Get all files recursively
                val files = folder.walkTopDown().filter { it.isFile }.toList()

                for (file in files) {
                    try {
                        // Calculate relative path
                        val relativePath = file.relativeTo(folder).path
                        val targetPath = if (currentPath.isEmpty()) {
                            relativePath
                        } else {
                            "$currentPath/$relativePath"
                        }

                        // Read file content
                        val content = withContext(Dispatchers.IO) {
                            file.readBytes()
                        }
                        val base64Content = android.util.Base64.encodeToString(
                            content,
                            android.util.Base64.NO_WRAP
                        )

                        val request = CreateFileRequest(
                            message = "Upload ${file.name} via Android app",
                            content = base64Content,
                            branch = branch
                        )

                        val response = RetrofitClient.gitHubApiService.createOrUpdateFile(
                            "Bearer $token",
                            owner,
                            repo,
                            targetPath,
                            request
                        )

                        if (response.isSuccessful) {
                            uploadedCount++
                        } else {
                            failedCount++
                        }

                    } catch (e: Exception) {
                        failedCount++
                    }
                }

                progressBar.visibility = View.GONE

                Toast.makeText(
                    this@FileTreeActivity,
                    "Uploaded $uploadedCount files successfully, $failedCount failed",
                    Toast.LENGTH_LONG
                ).show()

                // Refresh the file tree
                loadFileTree()

            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(
                    this@FileTreeActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun uploadFile(localPath: String, targetPath: String, commitMessage: String) {
        progressBar.visibility = View.VISIBLE

        val token = GitHubAuthHelper.getToken(this) ?: return

        lifecycleScope.launch {
            try {
                val file = File(localPath)
                if (!file.exists()) {
                    showError("File not found: $localPath")
                    return@launch
                }

                // Read file and encode to base64
                val fileContent = file.readBytes()
                val base64Content = Base64.encodeToString(fileContent, Base64.NO_WRAP)

                val request = CreateFileRequest(
                    message = commitMessage,
                    content = base64Content,
                    branch = branch
                )

                val response = RetrofitClient.gitHubApiService.createOrUpdateFile(
                    "Bearer $token",
                    owner,
                    repo,
                    targetPath,
                    request
                )

                progressBar.visibility = View.GONE

                if (response.isSuccessful) {
                    Toast.makeText(
                        this@FileTreeActivity,
                        "File uploaded successfully!",
                        Toast.LENGTH_SHORT
                    ).show()
                    loadFileTree() // Refresh the list
                } else {
                    showError("Upload failed: ${response.code()}")
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                showError("Error: ${e.message}")
            }
        }
    }

    private fun formatFileSize(size: Long): String {
        if (size < 1024) return "$size B"
        val kb = size / 1024
        if (kb < 1024) return "$kb KB"
        val mb = kb / 1024
        return "$mb MB"
    }

    private fun showError(message: String) {
        progressBar.visibility = View.GONE
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
