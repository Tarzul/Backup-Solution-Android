package com.rezerv.upload

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

// ВАЖНО: data classes на верхнем уровне файла
data class HistoryRecord(
    val time: Long,
    val durationMs: Long,
    val checked: Int,
    val uploaded: Int,
    val downloaded: Int,
    val deleted: Int,
    val errors: Int,
    val status: String,
    val trigger: String,
    val bytesTransferred: Long = 0,
    val transferMs: Long = 0,
    val filesJson: String = "",
    val foldersJson: String = "",
    val taskName: String = "",
    val taskId: String = "",   // НОВОЕ: уникальный ID задания
    // НОВОЕ: поля live-прогресса (с дефолтными значениями для совместимости)
    val currentFileName: String = "",
    val currentFileIndex: Int = 0,
    val totalFiles: Int = 0,
    val liveStartedAt: Long = 0,
    // НОВОЕ: детали ошибок
    val errorsJson: String = ""
)

data class SyncFileDetail(val name: String, val size: Long, val ms: Long, val side: String)
data class SyncFolderDetail(val path: String, val side: String)
// НОВОЕ: детальная ошибка
data class SyncErrorDetail(val name: String, val reason: String)

object HistoryManager {
    private const val TAG = "HistoryManager"
    private const val PREFS_NAME = "webdav_history_v2"
    private const val KEY = "records"
    private const val MAX = 50

    fun addRecord(context: Context, r: HistoryRecord) {
        try {
            synchronized(this) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val records = getRecordsLocked(prefs).toMutableList()
                records.add(0, r)
                if (records.size > MAX) records.removeAt(records.lastIndex)
                saveRecords(prefs, records)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "addRecord ERROR", e)
        }
    }

    fun createLiveRecord(context: Context, time: Long, taskName: String, trigger: String, taskId: String = "") {
        try {
            synchronized(this) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val records = getRecordsLocked(prefs).toMutableList()
                // НОВОЕ: завершаем "висячие" live-записи от прерванных запусков
                for (i in records.indices) {
                    if (records[i].status == "running") {
                        records[i] = records[i].copy(status = "error")
                    }
                }
                records.add(0, HistoryRecord(
                    time = time, durationMs = 0, checked = 0, uploaded = 0,
                    downloaded = 0, deleted = 0, errors = 0,
                    status = "running", trigger = trigger,
                    taskName = taskName, taskId = taskId, liveStartedAt = time
                ))
                if (records.size > MAX) records.removeAt(records.lastIndex)
                saveRecords(prefs, records)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "createLiveRecord ERROR", e)
        }
    }

    // НОВОЕ: обновляет существующую запись live-прогрессом (без создания дублей)
    fun updateLiveRecord(context: Context, time: Long, currentFileName: String,
                         currentFileIndex: Int, totalFiles: Int) {
        try {
            synchronized(this) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val records = getRecordsLocked(prefs).toMutableList()
                val idx = records.indexOfFirst { it.time == time }
                if (idx < 0) return
                records[idx] = records[idx].copy(
                    currentFileName = currentFileName,
                    currentFileIndex = currentFileIndex,
                    totalFiles = totalFiles
                )
                saveRecords(prefs, records)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "updateLiveRecord ERROR", e)
        }
    }

    // НОВОЕ: заменяет live-запись финальной (по time) — чтобы не было дублей
    fun finalizeRecord(context: Context, finalRecord: HistoryRecord) {
        try {
            synchronized(this) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val records = getRecordsLocked(prefs).toMutableList()
                val idx = records.indexOfFirst { it.time == finalRecord.time }
                    .takeIf { it >= 0 }
                    // ИСПРАВЛЕНО: ищем по taskId, если он есть, иначе по taskName
                    ?: records.indexOfFirst {
                        it.status == "running" &&
                            (if (finalRecord.taskId.isNotEmpty()) it.taskId == finalRecord.taskId
                             else it.taskName == finalRecord.taskName)
                    }
                if (idx >= 0) {
                    records[idx] = finalRecord
                } else {
                    records.add(0, finalRecord)
                    if (records.size > MAX) records.removeAt(records.lastIndex)
                }
                saveRecords(prefs, records)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "finalizeRecord ERROR", e)
        }
    }

    fun getRecords(context: Context): List<HistoryRecord> {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            synchronized(this) { getRecordsLocked(prefs) }
        } catch (e: Throwable) {
            Log.e(TAG, "getRecords ERROR", e)
            emptyList()
        }
    }

    private fun getRecordsLocked(prefs: android.content.SharedPreferences): List<HistoryRecord> {
        val raw = prefs.getString(KEY, "") ?: ""
        if (raw.isBlank()) return emptyList()
        if (!raw.trim().startsWith("[")) return parseOldCsvFormat(raw)
        val jsonArray = JSONArray(raw)
        val list = mutableListOf<HistoryRecord>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            list.add(HistoryRecord(
                time = obj.optLong("time", 0),
                durationMs = obj.optLong("durationMs", 0),
                checked = obj.optInt("checked", 0),
                uploaded = obj.optInt("uploaded", 0),
                downloaded = obj.optInt("downloaded", 0),
                deleted = obj.optInt("deleted", 0),
                errors = obj.optInt("errors", 0),
                status = obj.optString("status", ""),
                trigger = obj.optString("trigger", ""),
                bytesTransferred = obj.optLong("bytesTransferred", 0),
                transferMs = obj.optLong("transferMs", 0),
                filesJson = obj.optString("filesJson", ""),
                foldersJson = obj.optString("foldersJson", ""),
                taskName = obj.optString("taskName", ""),
                taskId = obj.optString("taskId", ""),   // НОВОЕ
                currentFileName = obj.optString("currentFileName", ""),
                currentFileIndex = obj.optInt("currentFileIndex", 0),
                totalFiles = obj.optInt("totalFiles", 0),
                liveStartedAt = obj.optLong("liveStartedAt", 0),
                errorsJson = obj.optString("errorsJson", "")
            ))
        }
        return list
    }

    private fun saveRecords(prefs: android.content.SharedPreferences, records: List<HistoryRecord>) {
        val jsonArray = JSONArray()
        for (rec in records) {
            val obj = JSONObject().apply {
                put("time", rec.time); put("durationMs", rec.durationMs)
                put("checked", rec.checked); put("uploaded", rec.uploaded)
                put("downloaded", rec.downloaded); put("deleted", rec.deleted)
                put("errors", rec.errors); put("status", rec.status)
                put("trigger", rec.trigger); put("bytesTransferred", rec.bytesTransferred)
                put("transferMs", rec.transferMs)
                put("filesJson", rec.filesJson); put("foldersJson", rec.foldersJson)
                put("taskName", rec.taskName)
                put("taskId", rec.taskId)   // НОВОЕ
                put("currentFileName", rec.currentFileName)
                put("currentFileIndex", rec.currentFileIndex)
                put("totalFiles", rec.totalFiles)
                put("liveStartedAt", rec.liveStartedAt)
                put("errorsJson", rec.errorsJson)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY, jsonArray.toString()).apply()
    }

    private fun parseOldCsvFormat(raw: String): List<HistoryRecord> {
        return raw.split("\n").filter { it.isNotBlank() }.mapNotNull { line ->
            val p = line.split(";")
            if (p.size >= 9) HistoryRecord(
                p[0].toLongOrNull() ?: 0L, p[1].toLongOrNull() ?: 0L,
                p[2].toIntOrNull() ?: 0, p[3].toIntOrNull() ?: 0, p[4].toIntOrNull() ?: 0,
                p[5].toIntOrNull() ?: 0, p[6].toIntOrNull() ?: 0, p[7], p[8],
                p.getOrNull(9)?.toLongOrNull() ?: 0L, p.getOrNull(10)?.toLongOrNull() ?: 0L,
                p.getOrNull(11) ?: "", p.getOrNull(12) ?: "",
                p.getOrNull(13) ?: ""
            ) else null
        }
    }

    fun clear(context: Context) {
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().remove(KEY).apply()
        } catch (e: Exception) {
            Log.e(TAG, "clear ERROR", e)
        }
    }

    fun parseFiles(json: String): List<SyncFileDetail> {
        val list = mutableListOf<SyncFileDetail>()
        if (json.isBlank()) return list
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(SyncFileDetail(o.optString("n"), o.optLong("s"), o.optLong("m"), o.optString("d")))
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseFiles ERROR", e)
        }
        return list
    }

    fun parseFolders(json: String): List<SyncFolderDetail> {
        val list = mutableListOf<SyncFolderDetail>()
        if (json.isBlank()) return list
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(SyncFolderDetail(o.optString("p"), o.optString("d")))
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseFolders ERROR", e)
        }
        return list
    }

    // НОВОЕ: парсинг списка ошибок из JSON
    fun parseErrors(json: String): List<SyncErrorDetail> {
        val list = mutableListOf<SyncErrorDetail>()
        if (json.isBlank()) return list
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(SyncErrorDetail(o.optString("n", ""), o.optString("r", "")))
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseErrors ERROR", e)
        }
        return list
    }
}