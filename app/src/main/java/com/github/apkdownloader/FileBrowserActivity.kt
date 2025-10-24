package com.github.apkdownloader

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import java.io.File

class FileBrowserActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var currentPathText: TextView
    private lateinit var emptyView: TextView
    private lateinit var fileBrowserAdapter: FileBrowserAdapter

    private var currentDirectory: File = File("/data/data/com.termux/files/home")
    private var selectFolderMode: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_browser)

        selectFolderMode = intent.getBooleanExtra("select_folder", false)

        supportActionBar?.title = if (selectFolderMode) "Select Folder" else "Select File"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        recyclerView = findViewById(R.id.filesRecyclerView)
        currentPathText = findViewById(R.id.currentPathText)
        emptyView = findViewById(R.id.emptyView)
        val selectFolderButton = findViewById<MaterialButton>(R.id.selectFolderButton)

        if (selectFolderMode) {
            selectFolderButton.visibility = View.VISIBLE
            selectFolderButton.setOnClickListener {
                val intent = Intent()
                intent.putExtra("selected_folder", currentDirectory.absolutePath)
                setResult(Activity.RESULT_OK, intent)
                finish()
            }
        }

        fileBrowserAdapter = FileBrowserAdapter { file ->
            onFileClick(file)
        }

        recyclerView.adapter = fileBrowserAdapter

        loadDirectory(currentDirectory)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onBackPressed() {
        if (currentDirectory.parent != null && currentDirectory.absolutePath != "/") {
            loadDirectory(currentDirectory.parentFile ?: currentDirectory)
        } else {
            super.onBackPressed()
        }
    }

    private fun loadDirectory(directory: File) {
        currentDirectory = directory
        currentPathText.text = directory.absolutePath

        try {
            val files = directory.listFiles()?.toList() ?: emptyList()
            val sortedFiles = files.sortedWith(compareBy({ !it.isDirectory }, { it.name }))

            if (sortedFiles.isEmpty()) {
                emptyView.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                emptyView.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                fileBrowserAdapter.submitList(sortedFiles)
            }
        } catch (e: Exception) {
            emptyView.text = "Cannot access directory"
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        }
    }

    private fun onFileClick(file: File) {
        if (file.isDirectory) {
            loadDirectory(file)
        } else {
            // Return selected file
            val intent = Intent()
            intent.putExtra("selected_file", file.absolutePath)
            setResult(Activity.RESULT_OK, intent)
            finish()
        }
    }
}
