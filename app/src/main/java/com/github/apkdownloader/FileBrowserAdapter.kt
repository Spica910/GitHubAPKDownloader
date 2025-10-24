package com.github.apkdownloader

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class FileBrowserAdapter(
    private val onItemClick: (File) -> Unit
) : ListAdapter<File, FileBrowserAdapter.FileViewHolder>(FileDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file_tree, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(getItem(position), onItemClick)
    }

    class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val fileIcon: TextView = itemView.findViewById(R.id.fileIcon)
        private val fileName: TextView = itemView.findViewById(R.id.fileName)
        private val fileSize: TextView = itemView.findViewById(R.id.fileSize)

        fun bind(file: File, onItemClick: (File) -> Unit) {
            fileName.text = file.name

            if (file.isDirectory) {
                fileIcon.text = "📁"
                fileSize.text = "Folder"
            } else {
                fileIcon.text = when {
                    file.extension == "kt" || file.extension == "java" -> "📝"
                    file.extension == "xml" -> "📋"
                    file.extension == "json" -> "📊"
                    file.extension == "md" -> "📄"
                    file.extension == "png" || file.extension == "jpg" -> "🖼️"
                    file.extension == "apk" -> "📦"
                    file.extension == "gradle" -> "⚙️"
                    file.extension == "txt" -> "📄"
                    else -> "📄"
                }

                fileSize.text = formatFileSize(file.length())
            }

            itemView.setOnClickListener {
                onItemClick(file)
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

    private class FileDiffCallback : DiffUtil.ItemCallback<File>() {
        override fun areItemsTheSame(oldItem: File, newItem: File): Boolean {
            return oldItem.absolutePath == newItem.absolutePath
        }

        override fun areContentsTheSame(oldItem: File, newItem: File): Boolean {
            return oldItem.absolutePath == newItem.absolutePath &&
                   oldItem.lastModified() == newItem.lastModified()
        }
    }
}
