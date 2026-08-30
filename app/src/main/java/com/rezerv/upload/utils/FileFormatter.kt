package com.rezerv.upload.utils

/**
 * Общие утилиты форматирования для UI.
 */
object FileFormatter {
    fun formatSize(b: Long): String = when {
        b < 0 -> "—"
        b < 1024 -> "$b Б"
        b < 1024 * 1024 -> String.format("%.1f КБ", b / 1024.0)
        b < 1024L * 1024 * 1024 -> String.format("%.1f МБ", b / (1024.0 * 1024))
        else -> String.format("%.2f ГБ", b / (1024.0 * 1024 * 1024))
    }
}