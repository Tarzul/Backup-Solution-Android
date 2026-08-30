package com.rezerv.upload.data

import android.content.Context
import com.rezerv.upload.SyncTask

interface SyncScheduler {
    suspend fun scheduleNext(context: Context)
    suspend fun ensureScheduler(context: Context)
    fun cancelForTask(context: Context, task: SyncTask)
    suspend fun cancelAll(context: Context)
}