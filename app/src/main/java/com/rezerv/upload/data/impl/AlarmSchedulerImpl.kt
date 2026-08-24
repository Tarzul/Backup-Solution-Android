package com.rezerv.upload.data.impl

import android.content.Context
import com.rezerv.upload.AlarmScheduler
import com.rezerv.upload.SyncTask
import com.rezerv.upload.data.SyncScheduler
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация SyncScheduler через существующий object AlarmScheduler.
 */
@Singleton
class AlarmSchedulerImpl @Inject constructor() : SyncScheduler {

    override fun scheduleNext(context: Context) =
        AlarmScheduler.scheduleNext(context)

    override fun ensureScheduler(context: Context) =
        AlarmScheduler.ensureScheduler(context)

    override fun cancelForTask(context: Context, task: SyncTask) =
        AlarmScheduler.cancelForTask(context, task)

    override fun cancelAll(context: Context) =
        AlarmScheduler.cancelAll(context)
}