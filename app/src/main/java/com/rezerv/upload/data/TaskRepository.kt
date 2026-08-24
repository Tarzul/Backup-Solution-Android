package com.rezerv.upload.data

import android.content.Context
import com.rezerv.upload.SyncTask

/**
 * Интерфейс репозитория заданий синхронизации.
 */
interface TaskRepository {
    fun load(context: Context): List<SyncTask>
    fun save(context: Context, tasks: List<SyncTask>)
    fun upsert(context: Context, task: SyncTask)
    fun delete(context: Context, id: String)
    fun getById(context: Context, id: String): SyncTask?
    fun getActiveTasks(context: Context): List<SyncTask>
    fun clear(context: Context)
}