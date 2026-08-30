package com.rezerv.upload.data

import android.content.Context
import com.rezerv.upload.SyncTask

interface SyncScheduler {
    suspend fun scheduleNext(context: Context)      // ✅ suspend
    suspend fun ensureScheduler(context: Context)   // ✅ suspend
    fun cancelForTask(context: Context, task: SyncTask)
    suspend fun cancelAll(context: Context)         // ✅ suspend
}