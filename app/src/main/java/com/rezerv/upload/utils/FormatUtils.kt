package com.rezerv.upload.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FormatUtils {
    fun formatSize(b: Long): String = when {
        b < 0 -> "—"
        b < 1024 -> "$b Б"
        b < 1024 * 1024 -> String.format("%.1f КБ", b / 1024.0)
        b < 1024L * 1024 * 1024 -> String.format("%.1f МБ", b / (1024.0 * 1024))
        else -> String.format("%.2f ГБ", b / (1024.0 * 1024 * 1024))
    }

    fun formatDateTime(time: Long): String =
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(time))

    fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return when {
            h > 0 -> "${h}ч ${m}м"
            m > 0 -> "${m}м ${s}с"
            else -> "${s}с"
        }
    }
}