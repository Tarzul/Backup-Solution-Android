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
    // ИСПРАВЛЕНО: имя задания для отображения в истории (вместо хардкода "SD CARD > WebDAV")
    val taskName: String = ""
)

data class SyncFileDetail(val name: String, val size: Long, val ms: Long, val side: String)
data class SyncFolderDetail(val path: String, val side: String)

object HistoryManager {
    private const val TAG = "HistoryManager"
    private const val PREFS_NAME = "webdav_history_v2"
    private const val KEY = "records"
    private const val MAX = 50

    fun addRecord(context: Context, r: HistoryRecord) {
        try {
            synchronized(this) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val records = getRecords(context).toMutableList()
                records.add(0, r)
                if (records.size > MAX) records.removeAt(records.lastIndex)
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
                        put("taskName", rec.taskName)   // ИСПРАВЛЕНО
                    }
                    jsonArray.put(obj)
                }
                prefs.edit().putString(KEY, jsonArray.toString()).apply()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "addRecord ERROR", e)
        }
    }

    fun getRecords(context: Context): List<HistoryRecord> {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
                    taskName = obj.optString("taskName", "")   // ИСПРАВЛЕНО
                ))
            }
            list
        } catch (e: Throwable) {
            Log.e(TAG, "getRecords ERROR", e)
            emptyList()
        }
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
                p.getOrNull(13) ?: ""   // ИСПРАВЛЕНО: taskName из legacy-формата (если есть)
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
}