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
        Log.d(TAG, "Alarm fired: taskId=$taskId")

        // ИСПРАВЛЕНО: убираем UNIQUE_WORK_NAME (не использовался)
        val request = OneTimeWorkRequestBuilder<TaskWorker>()
            .setInputData(workDataOf("taskId" to (taskId ?: "")))
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}