package com.github.apkdownloader

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class AllApksAdapter(
    private val onDownloadClick: (ApkInfo) -> Unit,
    private val onInfoClick: (ApkInfo) -> Unit
) : ListAdapter<ApkInfo, AllApksAdapter.ApkViewHolder>(ApkDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ApkViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_apk, parent, false)
        return ApkViewHolder(view)
    }

    override fun onBindViewHolder(holder: ApkViewHolder, position: Int) {
        holder.bind(getItem(position), onDownloadClick, onInfoClick)
    }

    class ApkViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val fileNameTextView: TextView = itemView.findViewById(R.id.apkFileName)
        private val locationTextView: TextView = itemView.findViewById(R.id.apkLocation)
        private val sizeTextView: TextView = itemView.findViewById(R.id.apkSize)
        private val packageInfoTextView: TextView = itemView.findViewById(R.id.apkPackageInfo)
        private val updateBadge: TextView = itemView.findViewById(R.id.updateBadge)
        private val downloadButton: Button = itemView.findViewById(R.id.apkDownloadButton)
        private val infoButton: Button = itemView.findViewById(R.id.apkInfoButton)

        fun bind(apkInfo: ApkInfo, onDownloadClick: (ApkInfo) -> Unit, onInfoClick: (ApkInfo) -> Unit) {
            fileNameTextView.text = apkInfo.fileName
            locationTextView.text = apkInfo.location
            sizeTextView.text = formatFileSize(apkInfo.size)

            // Show package info if available
            if (apkInfo.packageInfo != null) {
                packageInfoTextView.visibility = View.VISIBLE
                packageInfoTextView.text = "📦 ${apkInfo.packageInfo!!.packageName} v${apkInfo.packageInfo!!.versionName}"
            } else {
                packageInfoTextView.visibility = View.GONE
            }

            // Show update badge if update is available
            if (apkInfo.updateInfo != null && apkInfo.updateInfo!!.isUpdateAvailable) {
                updateBadge.visibility = View.VISIBLE
            } else {
                updateBadge.visibility = View.GONE
            }

            downloadButton.setOnClickListener {
                onDownloadClick(apkInfo)
            }

            infoButton.setOnClickListener {
                onInfoClick(apkInfo)
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

    private class ApkDiffCallback : DiffUtil.ItemCallback<ApkInfo>() {
        override fun areItemsTheSame(oldItem: ApkInfo, newItem: ApkInfo): Boolean {
            return oldItem.downloadUrl == newItem.downloadUrl
        }

        override fun areContentsTheSame(oldItem: ApkInfo, newItem: ApkInfo): Boolean {
            return oldItem == newItem
        }
    }
}
