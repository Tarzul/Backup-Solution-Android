package com.rezerv.upload

import android.content.Context
import androidx.core.content.ContextCompat

object FileUtils {
    fun formatSize(b: Long): String = when {
        b < 0 -> "—"
        b < 1024 -> "$b Б"
        b < 1024 * 1024 -> String.format("%.1f КБ", b / 1024.0)
        b < 1024L * 1024 * 1024 -> String.format("%.1f МБ", b / (1024.0 * 1024))
        else -> String.format("%.2f ГБ", b / (1024.0 * 1024 * 1024))
    }

    fun isImageFile(n: String): Boolean = n.lowercase().let {
        it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") ||
        it.endsWith(".gif") || it.endsWith(".bmp") || it.endsWith(".webp")
    }

    fun isVideoFile(n: String): Boolean = n.lowercase().let {
        it.endsWith(".mp4") || it.endsWith(".avi") || it.endsWith(".mkv") ||
        it.endsWith(".mov") || it.endsWith(".webm") || it.endsWith(".3gp") ||
        it.endsWith(".wmv") || it.endsWith(".flv")
    }

    fun getIconResource(item: WebDavRepository.FileInfo): Int {
        if (item.isDirectory) return android.R.drawable.ic_menu_save
        val lower = item.name.lowercase()
        return when {
            isImageFile(lower) -> android.R.drawable.ic_menu_gallery
            isVideoFile(lower) -> android.R.drawable.ic_media_play
            lower.endsWith(".mp3") || lower.endsWith(".wav") ||
            lower.endsWith(".flac") || lower.endsWith(".ogg") -> android.R.drawable.ic_media_play
            lower.endsWith(".pdf") -> android.R.drawable.ic_menu_agenda
            lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z") ->
                android.R.drawable.ic_menu_share
            lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".log") ->
                android.R.drawable.ic_menu_edit
            else -> android.R.drawable.ic_menu_set_as
        }
    }

    fun getNameColor(context: Context, item: WebDavRepository.FileInfo): Int = when {
        item.isDirectory -> ContextCompat.getColor(context, R.color.file_folder)
        isImageFile(item.name) -> ContextCompat.getColor(context, R.color.file_image)
        isVideoFile(item.name) -> ContextCompat.getColor(context, R.color.file_video)
        else -> ContextCompat.getColor(context, R.color.text_primary)
    }

    fun getInfoText(item: WebDavRepository.FileInfo): String =
        if (item.isDirectory) "📁 Папка" else "📄 ${formatSize(item.size)}"
}