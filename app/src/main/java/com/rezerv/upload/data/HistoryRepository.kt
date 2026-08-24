package com.rezerv.upload.data

import android.content.Context
import com.rezerv.upload.HistoryRecord
import com.rezerv.upload.SyncErrorDetail
import com.rezerv.upload.SyncFileDetail
import com.rezerv.upload.SyncFolderDetail

/**
 * Интерфейс репозитория истории синхронизаций.
 */
interface HistoryRepository {
    fun addRecord(context: Context, r: HistoryRecord)
    fun createLiveRecord(
        context: Context,
        time: Long,
        taskName: String,
        trigger: String,
        taskId: String = ""
    ): Boolean
    fun updateLiveRecord(
        context: Context,
        time: Long,
        currentFileName: String,
        currentFileIndex: Int,
        totalFiles: Int
    )
    fun finalizeRecord(context: Context, finalRecord: HistoryRecord)
    fun getRecords(context: Context): List<HistoryRecord>
    fun clear(context: Context)
    fun parseFiles(json: String): List<SyncFileDetail>
    fun parseFolders(json: String): List<SyncFolderDetail>
    fun parseErrors(json: String): List<SyncErrorDetail>
}