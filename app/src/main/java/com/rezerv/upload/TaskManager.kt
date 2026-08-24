package com.rezerv.upload

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * TaskManager — хранилище заданий синхронизации.
 * Использует SharedPreferences + JSON для персистентности.
 * Все операции потокобезопасны (synchronized).
 */
object TaskManager {

    private const val TAG = "TaskManager"
    private const val PREFS_NAME = "sync_tasks_v2"
    private const val KEY_TASKS = "tasks"

    // ==================== CRUD операции ====================

    /**
     * Загружает все задания из хранилища.
     * Возвращает пустой список, если заданий нет или произошла ошибка.
     */
    fun load(context: Context): List<SyncTask> {
        return try {
            synchronized(this) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val raw = prefs.getString(KEY_TASKS, "") ?: ""
                if (raw.isBlank()) return emptyList()

                val jsonArray = JSONArray(raw)
                val list = mutableListOf<SyncTask>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(fromJson(obj))
                }
                list
            }
        } catch (e: Throwable) {
            Log.e(TAG, "load ERROR", e)
            emptyList()
        }
    }

    /**
     * Сохраняет весь список заданий (полная перезапись).
     */
    fun save(context: Context, tasks: List<SyncTask>) {
        try {
            synchronized(this) {
                val jsonArray = JSONArray()
                for (task in tasks) {
                    jsonArray.put(toJson(task))
                }
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_TASKS, jsonArray.toString())
                    .apply()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "save ERROR", e)
        }
    }

    /**
     * Добавляет новое задание или обновляет существующее (по id).
     * Если id пустой — генерирует новый UUID.
     */
    fun upsert(context: Context, task: SyncTask) {
        try {
            synchronized(this) {
                val tasks = load(context).toMutableList()
                val index = tasks.indexOfFirst { it.id == task.id }

                val finalTask = if (task.id.isBlank()) {
                    task.copy(id = UUID.randomUUID().toString())
                } else {
                    task
                }

                if (index >= 0) {
                    tasks[index] = finalTask
                } else {
                    tasks.add(finalTask)
                }

                save(context, tasks)
                Log.d(TAG, "upsert: task '${finalTask.name}' (id=${finalTask.id})")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "upsert ERROR", e)
        }
    }

    /**
     * Удаляет задание по id.
     */
    fun delete(context: Context, id: String) {
        try {
            synchronized(this) {
                val tasks = load(context).filter { it.id != id }
                save(context, tasks)
                Log.d(TAG, "delete: id=$id")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "delete ERROR", e)
        }
    }

    /**
     * Возвращает задание по id или null, если не найдено.
     */
    fun getById(context: Context, id: String): SyncTask? {
        return load(context).firstOrNull { it.id == id }
    }

    /**
     * Возвращает только задания с включённым расписанием.
     * Используется в AlarmScheduler и TaskWorker.
     */
    fun getActiveTasks(context: Context): List<SyncTask> {
        return load(context).filter { it.scheduleEnabled }
    }

    /**
     * Очищает все задания.
     */
    fun clear(context: Context) {
        try {
            synchronized(this) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .remove(KEY_TASKS)
                    .apply()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "clear ERROR", e)
        }
    }

    // ==================== Сериализация ====================

    private fun toJson(task: SyncTask): JSONObject {
        return JSONObject().apply {
            put("id", task.id)
            put("name", task.name)
            put("syncType", task.syncType)

            put("leftIsWebdav", task.leftIsWebdav)
            put("leftLocalUri", task.leftLocalUri)
            put("leftWebdavPath", task.leftWebdavPath)

            put("rightIsWebdav", task.rightIsWebdav)
            put("rightLocalUri", task.rightLocalUri)
            put("rightWebdavPath", task.rightWebdavPath)

            put("scheduleEnabled", task.scheduleEnabled)
            put("scheduleMode", task.scheduleMode)
            put("intervalValue", task.intervalValue)
            put("hour", task.hour)
            put("minute", task.minute)
            put("weekDays", task.weekDays)
            put("monthDays", task.monthDays)

            put("useWifi", task.useWifi)
            put("useMobile", task.useMobile)
            put("onlyCharging", task.onlyCharging)
            put("notifyOnSuccess", task.notifyOnSuccess)
            put("notifyOnError", task.notifyOnError)

            put("lastRun", task.lastRun)
            put("lastStatus", task.lastStatus)
        }
    }

    private fun fromJson(obj: JSONObject): SyncTask {
        return SyncTask(
            id = obj.optString("id", ""),
            name = obj.optString("name", ""),
            syncType = obj.optString("syncType", "two_way"),

            leftIsWebdav = obj.optBoolean("leftIsWebdav", false),
            leftLocalUri = obj.optString("leftLocalUri", ""),
            leftWebdavPath = obj.optString("leftWebdavPath", ""),

            rightIsWebdav = obj.optBoolean("rightIsWebdav", true),
            rightLocalUri = obj.optString("rightLocalUri", ""),
            rightWebdavPath = obj.optString("rightWebdavPath", "/"),

            scheduleEnabled = obj.optBoolean("scheduleEnabled", false),
            scheduleMode = obj.optString("scheduleMode", "daily"),
            intervalValue = obj.optInt("intervalValue", 1),
            hour = obj.optInt("hour", 3),
            minute = obj.optInt("minute", 0),
            weekDays = obj.optString("weekDays", ""),
            monthDays = obj.optString("monthDays", ""),

            useWifi = obj.optBoolean("useWifi", true),
            useMobile = obj.optBoolean("useMobile", false),
            onlyCharging = obj.optBoolean("onlyCharging", false),
            notifyOnSuccess = obj.optBoolean("notifyOnSuccess", false),
            notifyOnError = obj.optBoolean("notifyOnError", true),

            lastRun = obj.optLong("lastRun", 0L),
            lastStatus = obj.optString("lastStatus", "")
        )
    }
}