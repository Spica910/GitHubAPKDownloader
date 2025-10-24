package com.github.apkdownloader

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.roundToInt

class ReleasesAdapter(
    private val onDownloadClick: (Asset, Release) -> Unit
) : ListAdapter<Release, ReleasesAdapter.ReleaseViewHolder>(ReleaseDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReleaseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_release, parent, false)
        return ReleaseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReleaseViewHolder, position: Int) {
        holder.bind(getItem(position), onDownloadClick)
    }

    class ReleaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.releaseName)
        private val tagTextView: TextView = itemView.findViewById(R.id.releaseTag)
        private val assetTextView: TextView = itemView.findViewById(R.id.assetName)
        private val downloadButton: Button = itemView.findViewById(R.id.downloadButton)

        fun bind(release: Release, onDownloadClick: (Asset, Release) -> Unit) {
            nameTextView.text = release.name ?: "Release ${release.tagName}"
            tagTextView.text = "Tag: ${release.tagName}"

            // Find APK assets
            val apkAssets = release.assets.filter { it.name.endsWith(".apk", ignoreCase = true) }

            if (apkAssets.isNotEmpty()) {
                val firstApk = apkAssets.first()
                val sizeMB = (firstApk.size / (1024.0 * 1024.0) * 100).roundToInt() / 100.0
                assetTextView.text = "${firstApk.name} (${sizeMB} MB)"

                downloadButton.setOnClickListener {
                    onDownloadClick(firstApk, release)
                }

                // If multiple APKs, show count
                if (apkAssets.size > 1) {
                    assetTextView.text = "${assetTextView.text} + ${apkAssets.size - 1} more"
                }
            } else {
                assetTextView.text = "No APK files"
                downloadButton.isEnabled = false
            }
        }
    }

    private class ReleaseDiffCallback : DiffUtil.ItemCallback<Release>() {
        override fun areItemsTheSame(oldItem: Release, newItem: Release): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Release, newItem: Release): Boolean {
            return oldItem == newItem
        }
    }
}
