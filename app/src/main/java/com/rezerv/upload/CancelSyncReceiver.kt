package com.rezerv.upload

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.WorkManager
import com.rezerv.upload.data.TaskRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first   // ✅ Импорт для extension-функции
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receiver для отмены синхронизации через Notification Action.
 * Отменяет И текущую работу WorkManager, И следующий запланированный будильник.
 */
@AndroidEntryPoint
class CancelSyncReceiver : BroadcastReceiver() {
    
    @Inject lateinit var taskRepository: TaskRepository
    
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
        val pendingResult = goAsync()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (taskId != null) {
                    val task = taskRepository.getTaskById(taskId)
                    if (task != null) {
                        AlarmScheduler.cancelForTask(context, task)
                        Log.d(TAG, "✓ AlarmManager: отменён будильник для '${task.name}'")
                    } else {
                        Log.w(TAG, "⚠ Задача $taskId не найдена в Room")
                    }
                } else {
                    // ✅ ПРАВИЛЬНЫЙ СИНТАКСИС: .first() вызывается НА Flow, а не как функция
                    val allTasks = taskRepository.getAllTasks().first()
                    AlarmScheduler.cancelAll(context, allTasks)
                    Log.d(TAG, "✓ AlarmManager: отменены все будильники (${allTasks.size} задач)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка отмены AlarmManager", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}