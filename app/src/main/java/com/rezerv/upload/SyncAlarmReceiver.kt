package com.rezerv.upload

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.rezerv.upload.data.SyncScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint   // ✅ для инъекции
class SyncAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var syncScheduler: SyncScheduler   // ✅

    companion object {
        private const val TAG = "SyncAlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra("taskId")
        Log.d(TAG, "⏰ Alarm fired: taskId=$taskId")

        val request = OneTimeWorkRequestBuilder<TaskWorker>()
            .setInputData(workDataOf("taskId" to (taskId ?: "")))
            .addTag("sync_task")
            .addTag(if (taskId != null) "task_$taskId" else "task_unknown")
            .build()
        WorkManager.getInstance(context).enqueue(request)

        // ✅ Перепланирование через Room (goAsync, чтобы процесс не убили)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                syncScheduler.scheduleNext(context)
            } catch (e: Exception) {
                Log.e(TAG, "scheduleNext ERROR", e)
            } finally {
                pending.finish()
            }
        }
    }
}