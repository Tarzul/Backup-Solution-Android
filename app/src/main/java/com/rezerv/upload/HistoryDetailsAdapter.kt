package com.rezerv.upload

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class HistoryDetailsAdapter {

    // Адаптер для файлов
    class FileAdapter(
        private var files: List<SyncFileDetail>
    ) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

        fun updateData(newFiles: List<SyncFileDetail>) {
            files = newFiles
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_history_file, parent, false)
            return FileViewHolder(view)
        }

        override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
            val f = files[position]
            holder.tvFileName.text = f.name
            holder.tvFileSize.text = formatSize(f.size)
            holder.tvFileDuration.text = formatShortDuration(f.ms)
            holder.tvFileSide.text = f.side
        }

        override fun getItemCount() = files.size

        private fun formatSize(b: Long): String = when {
            b < 0 -> "—"
            b < 1024 -> "$b Б"
            b < 1024 * 1024 -> String.format("%.1f КБ", b / 1024.0)
            b < 1024L * 1024 * 1024 -> String.format("%.1f МБ", b / (1024.0 * 1024))
            else -> String.format("%.2f ГБ", b / (1024.0 * 1024 * 1024))
        }

        private fun formatShortDuration(ms: Long): String {
            val total = ms / 1000
            return "${total / 60}м ${total % 60}с"
        }

        class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvFileName: TextView = itemView.findViewById(R.id.tvFileName)
            val tvFileSize: TextView = itemView.findViewById(R.id.tvFileSize)
            val tvFileDuration: TextView = itemView.findViewById(R.id.tvFileDuration)
            val tvFileSide: TextView = itemView.findViewById(R.id.tvFileSide)
        }
    }

    // Адаптер для папок
    class FolderAdapter(
        private var folders: List<SyncFolderDetail>
    ) : RecyclerView.Adapter<FolderAdapter.FolderViewHolder>() {

        fun updateData(newFolders: List<SyncFolderDetail>) {
            folders = newFolders
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_history_folder, parent, false)
            return FolderViewHolder(view)
        }

        override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
            val f = folders[position]
            holder.tvFolderPath.text = f.path
            holder.tvFolderSide.text = f.side
        }

        override fun getItemCount() = folders.size

        class FolderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvFolderPath: TextView = itemView.findViewById(R.id.tvFolderPath)
            val tvFolderSide: TextView = itemView.findViewById(R.id.tvFolderSide)
        }
    }
}