package com.rezerv.upload

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.WorkManager

/**
 * Receiver для отмены синхронизации через Notification Action.
 * Отменяет И текущую работу WorkManager, И следующий запланированный будильник.
 */
class CancelSyncReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "CancelSyncReceiver"
        const val EXTRA_TASK_ID = "taskId"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID)
        Log.d(TAG, "▶ Получен запрос отмены: taskId=$taskId")
        
        // 1. Отменяем текущую WorkManager работу по тегу
        try {
            val workManager = WorkManager.getInstance(context)
            if (taskId != null) {
                workManager.cancelAllWorkByTag("task_$taskId")
                Log.d(TAG, "✓ WorkManager: отменена задача $taskId")
            } else {
                workManager.cancelAllWorkByTag("sync_task")
                Log.d(TAG, "✓ WorkManager: отменены все задачи синхронизации")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка отмены WorkManager", e)
        }
        
        // 2. Отменяем следующий запланированный будильник AlarmManager
        // Это предотвратит автоматический перезапуск задачи через scheduleNext
        if (taskId != null) {
            try {
                // Загружаем задачу из SharedPreferences через TaskManager
                val task = TaskManager.getById(context, taskId)
                if (task != null) {
                    AlarmScheduler.cancelForTask(context, task)
                    Log.d(TAG, "✓ AlarmManager: отменён будильник для '${task.name}'")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка отмены AlarmManager", e)
            }
        } else {
            AlarmScheduler.cancelAll(context)
            Log.d(TAG, "✓ AlarmManager: отменены все будильники")
        }
    }
}