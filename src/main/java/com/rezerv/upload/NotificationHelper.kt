package com.rezerv.upload

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {
    private const val CHANNEL = "sync_results"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(
                    CHANNEL, "Результаты синхронизации", NotificationManager.IMPORTANCE_DEFAULT))
            }
        }
    }

    fun showResult(context: Context, taskName: String, ok: Boolean, errors: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        ensureChannel(context)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle(taskName)
            .setContentText(if (ok) "Синхронизация завершена успешно" else "Ошибок при синхронизации: $errors")
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(taskName.hashCode(), n)
        } catch (_: Exception) {}
    }
}