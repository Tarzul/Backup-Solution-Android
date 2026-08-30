package com.rezerv.upload.data.impl

import com.rezerv.upload.SyncTask
import com.rezerv.upload.data.TaskRepository
import com.rezerv.upload.data.local.TaskDao
import com.rezerv.upload.data.local.TaskEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val taskDao: TaskDao
) : TaskRepository {

    override fun getAllTasks(): Flow<List<SyncTask>> {
        return taskDao.getAllTasks().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getTaskById(id: String): SyncTask? {
        return taskDao.getTaskById(id)?.toDomainModel()
    }

    override suspend fun getActiveTasks(): List<SyncTask> {
        return taskDao.getActiveTasks().map { it.toDomainModel() }
    }

    override suspend fun saveTask(task: SyncTask) {
        taskDao.insertTask(task.toEntity())
    }

    override suspend fun deleteTask(id: String) {
        taskDao.deleteTaskById(id)
    }

    override suspend fun clear() {
        taskDao.clearAll()
    }
}

private fun TaskEntity.toDomainModel(): SyncTask {
    return SyncTask(
        id = id,
        name = name,
        syncType = syncType,
        leftIsWebdav = leftIsWebdav,
        leftLocalUri = leftLocalUri,
        leftWebdavPath = leftWebdavPath,
        rightIsWebdav = rightIsWebdav,
        rightLocalUri = rightLocalUri,
        rightWebdavPath = rightWebdavPath,
        scheduleEnabled = scheduleEnabled,
        scheduleMode = scheduleMode,
        intervalValue = intervalValue,
        hour = hour,
        minute = minute,
        weekDays = weekDays,
        monthDays = monthDays,
        useWifi = useWifi,
        useMobile = useMobile,
        onlyCharging = onlyCharging,
        notifyOnSuccess = notifyOnSuccess,
        notifyOnError = notifyOnError,
        lastRun = lastRun,
        lastStatus = lastStatus
    )
}

private fun SyncTask.toEntity(): TaskEntity {
    return TaskEntity(
        id = id,
        name = name,
        syncType = syncType,
        leftIsWebdav = leftIsWebdav,
        leftLocalUri = leftLocalUri,
        leftWebdavPath = leftWebdavPath,
        rightIsWebdav = rightIsWebdav,
        rightLocalUri = rightLocalUri,
        rightWebdavPath = rightWebdavPath,
        scheduleEnabled = scheduleEnabled,
        scheduleMode = scheduleMode,
        intervalValue = intervalValue,
        hour = hour,
        minute = minute,
        weekDays = weekDays,
        monthDays = monthDays,
        useWifi = useWifi,
        useMobile = useMobile,
        onlyCharging = onlyCharging,
        notifyOnSuccess = notifyOnSuccess,
        notifyOnError = notifyOnError,
        lastRun = lastRun,
        lastStatus = lastStatus
    )
}