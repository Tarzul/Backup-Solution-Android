package com.rezerv.upload.data.impl

import android.content.Context
import com.rezerv.upload.SyncTask
import com.rezerv.upload.TaskManager
import com.rezerv.upload.data.TaskRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация TaskRepository через существующий TaskManager.
 * Паттерн "Адаптер" — позволяет мигрировать постепенно.
 */
@Singleton
class TaskRepositoryImpl @Inject constructor() : TaskRepository {

    override fun load(context: Context): List<SyncTask> =
        TaskManager.load(context)

    override fun save(context: Context, tasks: List<SyncTask>) =
        TaskManager.save(context, tasks)

    override fun upsert(context: Context, task: SyncTask) =
        TaskManager.upsert(context, task)

    override fun delete(context: Context, id: String) =
        TaskManager.delete(context, id)

    override fun getById(context: Context, id: String): SyncTask? =
        TaskManager.getById(context, id)

    override fun getActiveTasks(context: Context): List<SyncTask> =
        TaskManager.getActiveTasks(context)

    override fun clear(context: Context) =
        TaskManager.clear(context)
}