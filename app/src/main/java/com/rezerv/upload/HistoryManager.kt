package com.rezerv.upload

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

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
    val taskId: String = "",
    val currentFileName: String = "",
    val currentFileIndex: Int = 0,
    val totalFiles: Int = 0,
    val liveStartedAt: Long = 0,
    val liveLastUpdateAt: Long = 0,
    val errorsJson: String = ""
)

data class SyncFileDetail(val name: String, val size: Long, val ms: Long, val side: String)
data class SyncFolderDetail(val path: String, val side: String)
data class SyncErrorDetail(val name: String, val reason: String)

object HistoryManager {
    private const val TAG = "HistoryManager"
    private const val PREFS_NAME = "webdav_history_v2"
    private const val KEY = "records"
    private const val MAX = 50
    private const val LIVE_TIMEOUT_MS = 60 * 60 * 1000L

// ЕДИНЫЙ лок для ВСЕХ операций записи
private val writeLock = Any()
private val liveSessions = mutableSetOf<Long>()

    // ==================== Атомарные операции ====================

    fun createLiveRecord(
        context: Context, time: Long, taskName: String, trigger: String, taskId: String = ""
    ): Boolean {
        synchronized(writeLock) {
            val prefs = getPrefs(context)
            val records = loadRecords(prefs).toMutableList()

            // 1. ✅ Чистим "зависшие" running-записи (переиспользуем helper)
            val cleaned = cleanupStaleRunning(records)

            // 2. Проверяем дубль (в том же synchronized-блоке!)
            if (taskId.isNotEmpty() &&
                records.any { it.status == "running" && it.taskId == taskId }) {
                if (cleaned) saveRecords(prefs, records)
                return false
            }

            // 3. Добавляем (гарантированно нет дубля)
            records.add(0, HistoryRecord(
                time = time, durationMs = 0, checked = 0, uploaded = 0,
                downloaded = 0, deleted = 0, errors = 0,
                status = "running", trigger = trigger,
                taskName = taskName, taskId = taskId,
                liveStartedAt = time,
                liveLastUpdateAt = time
            ))
            
            if (records.size > MAX) records.removeAt(records.lastIndex)
            saveRecords(prefs, records)
            liveSessions.add(time)   // ✅ Регистрируем сессию
            return true
        }
    }

    fun updateLiveRecord(
        context: Context, time: Long,
        currentFileName: String, currentFileIndex: Int, totalFiles: Int
    ) {
        synchronized(writeLock) {
            val prefs = getPrefs(context)
            val records = loadRecords(prefs).toMutableList()
            val idx = records.indexOfFirst { it.time == time }
            if (idx < 0) return

            records[idx] = records[idx].copy(
                currentFileName = currentFileName,
                currentFileIndex = currentFileIndex,
                totalFiles = totalFiles,
                liveLastUpdateAt = System.currentTimeMillis()   // ✅ ДОБАВЬ: запись жива
            )
            saveRecords(prefs, records)
        }
    }

    fun finalizeRecord(context: Context, finalRecord: HistoryRecord) {
        synchronized(writeLock) {
            val prefs = getPrefs(context)
            val records = loadRecords(prefs).toMutableList()

            val idx = records.indexOfFirst { it.time == finalRecord.time }
                .takeIf { it >= 0 }
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
            liveSessions.remove(finalRecord.time)
        }
    }

    fun addRecord(context: Context, r: HistoryRecord) {
        synchronized(writeLock) {
            val prefs = getPrefs(context)
            val records = loadRecords(prefs).toMutableList()
            records.add(0, r)
            if (records.size > MAX) records.removeAt(records.lastIndex)
            saveRecords(prefs, records)
        }
    }

    fun getRecords(context: Context): List<HistoryRecord> {
        synchronized(writeLock) {
            val prefs = getPrefs(context)
            val records = loadRecords(prefs).toMutableList()
            // ✅ Чистим зависшие live-записи при КАЖДОМ чтении истории
            if (cleanupStaleRunning(records)) {
                saveRecords(prefs, records)
            }
            return records
        }
    }

    fun clear(context: Context) {
        synchronized(writeLock) {
            getPrefs(context).edit().remove(KEY).apply()
        }
    }

    // ==================== Парсинг деталей ====================

    fun parseFiles(json: String): List<SyncFileDetail> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SyncFileDetail(o.optString("n"), o.optLong("s"), o.optLong("m"), o.optString("d"))
            }
        } catch (e: Exception) { emptyList() }
    }

    fun parseFolders(json: String): List<SyncFolderDetail> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SyncFolderDetail(o.optString("p"), o.optString("d"))
            }
        } catch (e: Exception) { emptyList() }
    }

    fun parseErrors(json: String): List<SyncErrorDetail> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SyncErrorDetail(o.optString("n", ""), o.optString("r", ""))
            }
        } catch (e: Exception) { emptyList() }
    }

    // ==================== Приватные утилиты ====================

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Чтение БЕЗ лок-объекта (вызывается ТОЛЬКО внутри synchronized(writeLock)) */
    private fun loadRecords(prefs: SharedPreferences): List<HistoryRecord> {
        val raw = prefs.getString(KEY, "") ?: ""
        if (raw.isBlank()) return emptyList()
        if (!raw.trim().startsWith("[")) return parseOldCsvFormat(raw)

        return try {
            val jsonArray = JSONArray(raw)
            (0 until jsonArray.length()).map { i ->
                val obj = jsonArray.getJSONObject(i)
                HistoryRecord(
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
                    taskId = obj.optString("taskId", ""),
                    currentFileName = obj.optString("currentFileName", ""),
                    currentFileIndex = obj.optInt("currentFileIndex", 0),
                    totalFiles = obj.optInt("totalFiles", 0),
                    liveStartedAt = obj.optLong("liveStartedAt", 0),
                    liveLastUpdateAt = obj.optLong("liveLastUpdateAt", 0), 
                    errorsJson = obj.optString("errorsJson", "")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "JSON parse error", e)
            emptyList()
        }
    }

    /** Запись БЕЗ лок-объекта (вызывается ТОЛЬКО внутри synchronized(writeLock)) */
    private fun saveRecords(prefs: SharedPreferences, records: List<HistoryRecord>) {
        val jsonArray = JSONArray()
        for (rec in records) {
            jsonArray.put(JSONObject().apply {
                put("time", rec.time); put("durationMs", rec.durationMs)
                put("checked", rec.checked); put("uploaded", rec.uploaded)
                put("downloaded", rec.downloaded); put("deleted", rec.deleted)
                put("errors", rec.errors); put("status", rec.status)
                put("trigger", rec.trigger); put("bytesTransferred", rec.bytesTransferred)
                put("transferMs", rec.transferMs)
                put("filesJson", rec.filesJson); put("foldersJson", rec.foldersJson)
                put("taskName", rec.taskName); put("taskId", rec.taskId)
                put("currentFileName", rec.currentFileName)
                put("currentFileIndex", rec.currentFileIndex)
                put("totalFiles", rec.totalFiles)
                put("liveStartedAt", rec.liveStartedAt)
                put("liveLastUpdateAt", rec.liveLastUpdateAt)
                put("errorsJson", rec.errorsJson)
            })
        }
        // ИСПРАВЛЕНО: commit() вместо apply() для гарантии записи
        prefs.edit().putString(KEY, jsonArray.toString()).commit()
    }

    private fun parseOldCsvFormat(raw: String): List<HistoryRecord> {
        return raw.split("\n").filter { it.isNotBlank() }.mapNotNull { line ->
            val p = line.split(";")
            if (p.size >= 9) HistoryRecord(
                time = p[0].toLongOrNull() ?: 0L,
                durationMs = p[1].toLongOrNull() ?: 0L,
                checked = p[2].toIntOrNull() ?: 0,
                uploaded = p[3].toIntOrNull() ?: 0,
                downloaded = p[4].toIntOrNull() ?: 0,
                deleted = p[5].toIntOrNull() ?: 0,
                errors = p[6].toIntOrNull() ?: 0,
                status = p[7],
                trigger = p[8],
                bytesTransferred = p.getOrNull(9)?.toLongOrNull() ?: 0L,
                transferMs = p.getOrNull(10)?.toLongOrNull() ?: 0L,
                filesJson = p.getOrNull(11) ?: "",
                foldersJson = p.getOrNull(12) ?: "",
                taskName = p.getOrNull(13) ?: ""
            ) else null
        }
    }

    private fun cleanupStaleRunning(records: MutableList<HistoryRecord>): Boolean {
        val now = System.currentTimeMillis()
        var changed = false
        for (i in records.indices) {
            val r = records[i]
            if (r.status != "running") continue

            val lastActivity = maxOf(r.liveLastUpdateAt, r.liveStartedAt)
            val staleByTimeout = now - lastActivity > LIVE_TIMEOUT_MS
            val fresh = now - r.liveStartedAt < 15_000L
            val orphaned = !fresh && r.time !in liveSessions

            if (staleByTimeout || orphaned) {
                val reason = if (r.currentFileName.isNotEmpty())
                    "прервано на файле: ${r.currentFileName}"
                else "процесс завершён во время синхронизации"

                records[i] = r.copy(
                    status = "error",
                    durationMs = (lastActivity - r.liveStartedAt).coerceAtLeast(0),
                    errors = r.errors.coerceAtLeast(1),
                    errorsJson = """[{"n":"${r.currentFileName.ifEmpty { "Синхронизация" }}","r":"$reason"}]"""
                )
                changed = true
            }
        }
        return changed
    }
}