package com.rezerv.upload.data

import com.rezerv.upload.SyncTask
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun getAllTasks(): Flow<List<SyncTask>>
    suspend fun getTaskById(id: String): SyncTask?
    suspend fun getActiveTasks(): List<SyncTask>
    suspend fun saveTask(task: SyncTask)
    suspend fun deleteTask(id: String)
    suspend fun clear()
}