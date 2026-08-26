package com.rezerv.upload

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo

object NotificationHelper {
    
    private const val CHANNEL_ID_SYNC = "sync_channel"
    private const val CHANNEL_NAME_SYNC = "Синхронизация"
    private const val NOTIFICATION_ID_SYNC = 1001

    fun createForegroundInfo(
        context: Context,
        taskName: String,
        taskId: String,
        progress: Int,
        fileName: String? = null,
        isIndeterminate: Boolean = false
    ): ForegroundInfo {
        ensureChannel(context)
        val notification = buildSyncNotification(context, taskName, taskId, progress, fileName, isIndeterminate)
        return ForegroundInfo(
            NOTIFICATION_ID_SYNC,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            else 0
        )
    }

    private fun buildSyncNotification(
        context: Context,
        taskName: String,
        taskId: String,  // ✅ ДОБАВЛЕНО
        progress: Int,
        fileName: String?,
        isIndeterminate: Boolean
    ): Notification {
        val title = if (isIndeterminate) "Подготовка..." else "Синхронизация: $taskName"
        val text = fileName ?: if (progress > 0) "$progress%" else "Обработка файлов..."
        
        // Intent для открытия приложения при клике
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // ✅ ИСПРАВЛЕНО: Intent для отмены задачи с передачей taskId
        val cancelIntent = Intent(context, CancelSyncReceiver::class.java).apply {
            putExtra(CancelSyncReceiver.EXTRA_TASK_ID, taskId)
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context, taskId.hashCode(), cancelIntent,  // ✅ Используем taskId.hashCode() как requestCode
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID_SYNC)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_sync)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPendingIntent)
            .setProgress(100, progress, isIndeterminate)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Остановить",
                cancelPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Показывает уведомление о завершении синхронизации.
     */
    fun showResult(
        context: Context,
        taskName: String,
        success: Boolean,
        errorCount: Int
    ) {
        ensureChannel(context)
        
        val title = if (success) "✓ Синхронизация завершена" else "✗ Ошибка синхронизации"
        val text = if (success) {
            "$taskName: успешно"
        } else {
            "$taskName: $errorCount ошибок"
        }
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SYNC)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(if (success) R.drawable.ic_check else R.drawable.ic_error)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_SYNC,
                CHANNEL_NAME_SYNC,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Уведомления о синхронизации файлов"
                setShowBadge(false)
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}