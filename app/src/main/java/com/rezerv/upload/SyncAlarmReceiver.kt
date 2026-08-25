package com.rezerv.upload

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

class SyncAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SyncAlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra("taskId")
        Log.d(TAG, "⏰ Alarm fired: taskId=$taskId")

        // ✅ ДОБАВЛЕНО: Теги для возможности отмены через WorkManager
        val request = OneTimeWorkRequestBuilder<TaskWorker>()
            .setInputData(workDataOf("taskId" to (taskId ?: "")))
            .addTag("sync_task")                    // ✅ Общий тег для всех задач
            .addTag(if (taskId != null) "task_$taskId" else "task_unknown")  // ✅ Тег для конкретной задачи
            .build()
        
        WorkManager.getInstance(context).enqueue(request)

        // ИСПРАВЛЕНО: ВОЗВРАЩАЮ планирование следующего будильника сюда.
        // Это гарантирует непрерывную цепочку: сработал будильник -> сразу
        // назначен следующий. (Двойного планирования не будет: scheduleNext
        // идемпотентен — тот же requestCode просто обновляет время.)
        AlarmScheduler.scheduleNext(context)
    }
}