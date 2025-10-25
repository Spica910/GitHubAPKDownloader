package com.github.apkdownloader

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class RepositoryAdapter(
    private val onBrowseFilesClick: (Repository) -> Unit,
    private val onViewReleasesClick: (Repository) -> Unit,
    private val onDownloadApkClick: (ApkInfo) -> Unit,
    private val onAiBuildClick: (Repository) -> Unit
) : ListAdapter<RepositoryWithApk, RepositoryAdapter.RepositoryViewHolder>(RepositoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RepositoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_repository, parent, false)
        return RepositoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: RepositoryViewHolder, position: Int) {
        holder.bind(getItem(position), onBrowseFilesClick, onViewReleasesClick, onDownloadApkClick, onAiBuildClick)
    }

    class RepositoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.repoName)
        private val descriptionTextView: TextView = itemView.findViewById(R.id.repoDescription)
        private val languageTextView: TextView = itemView.findViewById(R.id.repoLanguage)
        private val browseFilesButton: Button = itemView.findViewById(R.id.browseFilesButton)
        private val viewReleasesButton: Button = itemView.findViewById(R.id.viewReleasesButton)
        private val aiBuildButton: Button = itemView.findViewById(R.id.aiBuildButton)
        private val apkInfoLayout: LinearLayout = itemView.findViewById(R.id.apkInfoLayout)
        private val apkListContainer: LinearLayout = itemView.findViewById(R.id.apkListContainer)

        fun bind(
            repoWithApk: RepositoryWithApk,
            onBrowseFilesClick: (Repository) -> Unit,
            onViewReleasesClick: (Repository) -> Unit,
            onDownloadApkClick: (ApkInfo) -> Unit,
            onAiBuildClick: (Repository) -> Unit
        ) {
            val repository = repoWithApk.repository

            nameTextView.text = repository.fullName
            descriptionTextView.text = repository.description ?: "No description"
            languageTextView.text = repository.language ?: "Unknown language"

            // Clear previous APK items
            apkListContainer.removeAllViews()

            // Collect all APKs (release + artifacts)
            val allApks = mutableListOf<ApkInfo>()
            repoWithApk.apkInfo?.let { allApks.add(it) }
            allApks.addAll(repoWithApk.artifactApks)

            // Show APK info if available
            if (allApks.isNotEmpty()) {
                apkInfoLayout.visibility = View.VISIBLE

                allApks.forEach { apkInfo ->
                    val apkItemView = createApkItemView(apkInfo, onDownloadApkClick)
                    apkListContainer.addView(apkItemView)
                }
            } else {
                apkInfoLayout.visibility = View.GONE
            }

            browseFilesButton.setOnClickListener {
                onBrowseFilesClick(repository)
            }

            viewReleasesButton.setOnClickListener {
                onViewReleasesClick(repository)
            }

            aiBuildButton.setOnClickListener {
                onAiBuildClick(repository)
            }
        }

        private fun createApkItemView(apkInfo: ApkInfo, onDownloadApkClick: (ApkInfo) -> Unit): View {
            val context = itemView.context
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8, 0, 8)
            }

            // APK source badge (already has icon in location)
            val sourceText = TextView(context).apply {
                text = apkInfo.location
                textSize = 11f
                setTextColor(android.graphics.Color.parseColor("#4CAF50"))
            }

            // APK details
            val detailsText = TextView(context).apply {
                text = "${apkInfo.fileName} (${formatFileSize(apkInfo.size)})"
                textSize = 12f
                setTextColor(android.graphics.Color.WHITE)
            }

            // Download button
            val downloadButton = com.google.android.material.button.MaterialButton(
                context,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                text = "Download"
                textSize = 11f
                setTextColor(android.graphics.Color.WHITE)
                setBackgroundColor(android.graphics.Color.parseColor("#4CAF50"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 4
                }
                setOnClickListener {
                    onDownloadApkClick(apkInfo)
                }
            }

            layout.addView(sourceText)
            layout.addView(detailsText)
            layout.addView(downloadButton)

            return layout
        }

        private fun formatFileSize(size: Long): String {
            return when {
                size < 1024 -> "$size B"
                size < 1024 * 1024 -> "${size / 1024} KB"
                else -> String.format("%.1f MB", size / (1024.0 * 1024.0))
            }
        }
    }

    private class RepositoryDiffCallback : DiffUtil.ItemCallback<RepositoryWithApk>() {
        override fun areItemsTheSame(oldItem: RepositoryWithApk, newItem: RepositoryWithApk): Boolean {
            return oldItem.repository.id == newItem.repository.id
        }

        override fun areContentsTheSame(oldItem: RepositoryWithApk, newItem: RepositoryWithApk): Boolean {
            return oldItem == newItem
        }
    }
}
