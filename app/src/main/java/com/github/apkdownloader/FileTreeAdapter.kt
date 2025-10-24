package com.github.apkdownloader

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class FileTreeAdapter(
    private val onItemClick: (GitTreeItem) -> Unit
) : ListAdapter<GitTreeItem, FileTreeAdapter.FileTreeViewHolder>(FileTreeDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileTreeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file_tree, parent, false)
        return FileTreeViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileTreeViewHolder, position: Int) {
        holder.bind(getItem(position), onItemClick)
    }

    class FileTreeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val fileIcon: TextView = itemView.findViewById(R.id.fileIcon)
        private val fileName: TextView = itemView.findViewById(R.id.fileName)
        private val fileSize: TextView = itemView.findViewById(R.id.fileSize)

        fun bind(item: GitTreeItem, onItemClick: (GitTreeItem) -> Unit) {
            val name = item.path.substringAfterLast("/")
            fileName.text = name

            if (item.type == "tree") {
                fileIcon.text = "📁"
                fileSize.text = "Folder"
            } else {
                // Determine icon based on file extension
                fileIcon.text = when {
                    name.endsWith(".kt") || name.endsWith(".java") -> "📝"
                    name.endsWith(".xml") -> "📋"
                    name.endsWith(".json") -> "📊"
                    name.endsWith(".md") -> "📄"
                    name.endsWith(".png") || name.endsWith(".jpg") -> "🖼️"
                    name.endsWith(".apk") -> "📦"
                    name.endsWith(".gradle") -> "⚙️"
                    else -> "📄"
                }

                val size = item.size ?: 0
                fileSize.text = formatFileSize(size)
            }

            itemView.setOnClickListener {
                onItemClick(item)
            }
        }

        private fun formatFileSize(size: Long): String {
            if (size < 1024) return "$size B"
            val kb = size / 1024
            if (kb < 1024) return "$kb KB"
            val mb = kb / 1024
            return "$mb MB"
        }
    }

    private class FileTreeDiffCallback : DiffUtil.ItemCallback<GitTreeItem>() {
        override fun areItemsTheSame(oldItem: GitTreeItem, newItem: GitTreeItem): Boolean {
            return oldItem.sha == newItem.sha
        }

        override fun areContentsTheSame(oldItem: GitTreeItem, newItem: GitTreeItem): Boolean {
            return oldItem == newItem
        }
    }
}
