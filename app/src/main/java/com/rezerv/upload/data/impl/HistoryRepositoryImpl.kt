package com.rezerv.upload.data.impl

import android.content.Context
import com.rezerv.upload.HistoryManager
import com.rezerv.upload.HistoryRecord
import com.rezerv.upload.SyncErrorDetail
import com.rezerv.upload.SyncFileDetail
import com.rezerv.upload.SyncFolderDetail
import com.rezerv.upload.data.HistoryRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация HistoryRepository через существующий HistoryManager.
 */
@Singleton
class HistoryRepositoryImpl @Inject constructor() : HistoryRepository {

    override fun addRecord(context: Context, r: HistoryRecord) =
        HistoryManager.addRecord(context, r)

    override fun createLiveRecord(
        context: Context,
        time: Long,
        taskName: String,
        trigger: String,
        taskId: String
    ): Boolean = HistoryManager.createLiveRecord(context, time, taskName, trigger, taskId)

    override fun updateLiveRecord(
        context: Context,
        time: Long,
        currentFileName: String,
        currentFileIndex: Int,
        totalFiles: Int
    ) = HistoryManager.updateLiveRecord(context, time, currentFileName, currentFileIndex, totalFiles)

    override fun finalizeRecord(context: Context, finalRecord: HistoryRecord) =
        HistoryManager.finalizeRecord(context, finalRecord)

    override fun getRecords(context: Context): List<HistoryRecord> =
        HistoryManager.getRecords(context)

    override fun clear(context: Context) =
        HistoryManager.clear(context)

    override fun parseFiles(json: String): List<SyncFileDetail> =
        HistoryManager.parseFiles(json)

    override fun parseFolders(json: String): List<SyncFolderDetail> =
        HistoryManager.parseFolders(json)

    override fun parseErrors(json: String): List<SyncErrorDetail> =
        HistoryManager.parseErrors(json)
}