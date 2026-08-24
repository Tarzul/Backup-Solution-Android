package com.rezerv.upload.data

import android.content.Context
import com.rezerv.upload.SyncTask

interface SyncScheduler {
    fun scheduleNext(context: Context)
    fun ensureScheduler(context: Context)
    fun cancelForTask(context: Context, task: SyncTask)
    fun cancelAll(context: Context)
}