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
    private val onDownloadApkClick: (ApkInfo) -> Unit
) : ListAdapter<RepositoryWithApk, RepositoryAdapter.RepositoryViewHolder>(RepositoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RepositoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_repository, parent, false)
        return RepositoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: RepositoryViewHolder, position: Int) {
        holder.bind(getItem(position), onBrowseFilesClick, onViewReleasesClick, onDownloadApkClick)
    }

    class RepositoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.repoName)
        private val descriptionTextView: TextView = itemView.findViewById(R.id.repoDescription)
        private val languageTextView: TextView = itemView.findViewById(R.id.repoLanguage)
        private val browseFilesButton: Button = itemView.findViewById(R.id.browseFilesButton)
        private val viewReleasesButton: Button = itemView.findViewById(R.id.viewReleasesButton)
        private val apkInfoLayout: LinearLayout = itemView.findViewById(R.id.apkInfoLayout)
        private val apkLocationText: TextView = itemView.findViewById(R.id.apkLocationText)
        private val downloadApkButton: Button = itemView.findViewById(R.id.downloadApkButton)

        fun bind(
            repoWithApk: RepositoryWithApk,
            onBrowseFilesClick: (Repository) -> Unit,
            onViewReleasesClick: (Repository) -> Unit,
            onDownloadApkClick: (ApkInfo) -> Unit
        ) {
            val repository = repoWithApk.repository

            nameTextView.text = repository.fullName
            descriptionTextView.text = repository.description ?: "No description"
            languageTextView.text = repository.language ?: "Unknown language"

            // Show APK info if available
            if (repoWithApk.apkInfo != null) {
                apkInfoLayout.visibility = View.VISIBLE
                apkLocationText.text = "${repoWithApk.apkInfo.location}\n${repoWithApk.apkInfo.fileName} (${formatFileSize(repoWithApk.apkInfo.size)})"
                downloadApkButton.setOnClickListener {
                    onDownloadApkClick(repoWithApk.apkInfo)
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
