package com.rezerv.upload.data.impl

import android.content.Context
import com.rezerv.upload.AlarmScheduler
import com.rezerv.upload.SyncTask
import com.rezerv.upload.data.SyncScheduler
import com.rezerv.upload.data.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmSchedulerImpl @Inject constructor(
    private val taskRepository: TaskRepository   // ✅ инжектим репозиторий
) : SyncScheduler {

    override suspend fun scheduleNext(context: Context) {
        val tasks = withContext(Dispatchers.IO) { taskRepository.getActiveTasks() }
        AlarmScheduler.scheduleNext(context, tasks)
    }

    override suspend fun ensureScheduler(context: Context) = scheduleNext(context)

    override fun cancelForTask(context: Context, task: SyncTask) =
        AlarmScheduler.cancelForTask(context, task)

    override suspend fun cancelAll(context: Context) {
        val tasks = withContext(Dispatchers.IO) { taskRepository.getAllTasks().first() }
        AlarmScheduler.cancelAll(context, tasks)
    }
}