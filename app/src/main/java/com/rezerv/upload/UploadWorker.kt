package com.rezerv.upload

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * ✅ Унифицированная ручная загрузка: работает через WorkManager,
 * переживает закрытие приложения, показывает foreground-уведомление.
 */
@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "UploadWorker"
        const val TASK_ID = "upload"                 // псевдо-ID для истории и отмены
        const val KEY_URIS = "uris"
        const val KEY_TARGET = "targetPath"
    }

    private var recordTime = 0L
    private var lastNotificationUpdate = 0L
    private val notificationLock = Any()

    override suspend fun doWork(): Result = try {
        runUpload()
    } catch (ce: kotlinx.coroutines.CancellationException) {
        // ✅ Отмена пользователем — честно финализируем запись
        if (recordTime > 0) finalizeAsCancelled()
        throw ce
    }

    private suspend fun runUpload(): Result {
        val uris = inputData.getStringArray(KEY_URIS) ?: return Result.failure()
        val targetPath = inputData.getString(KEY_TARGET) ?: return Result.failure()

        val (serverRaw, user, pass) = SecurePrefs.loadCredentials(applicationContext)
        val server = WebDavRepository.normalizeBaseUrl(serverRaw) ?: ""
        if (server.isEmpty()) {
            NotificationHelper.showResult(applicationContext, "Ручная загрузка", false, 1)
            return Result.failure()
        }

        val startTime = System.currentTimeMillis()
        recordTime = startTime
        if (!HistoryManager.createLiveRecord(
                applicationContext, startTime, "Ручная загрузка", "user", TASK_ID)) {
            return Result.success()   // уже идёт — не дублируем
        }

        try {
            setForeground(info(0, "Подготовка...", true))
        } catch (e: Exception) {
            Log.w(TAG, "setForeground недоступен", e)
        }

        // Папка на сервере (выбрана в пикере, но на всякий случай)
        try {
            WebDavClient.mkcol(server + WebDavRepository.encodePath(targetPath), user, pass)
        } catch (e: Exception) {
            Log.w(TAG, "mkcol: ${e.message}")
        }

        var uploaded = 0; var errors = 0; var bytes = 0L
        val tStart = System.currentTimeMillis()
        val files = mutableListOf<SyncFileDetail>()
        val errorList = mutableListOf<SyncErrorDetail>()
        val total = uris.size

        uris.forEachIndexed { index, uriString ->
            if (isStopped) return@forEachIndexed

            val uri = Uri.parse(uriString)
            val (name, size) = queryNameAndSize(uri)
            if (name == null) {
                errors++
                errorList.add(SyncErrorDetail(uriString, "не удалось открыть файл"))
                return@forEachIndexed
            }

            HistoryManager.updateLiveRecord(applicationContext, startTime, name, index + 1, total)
            updateNotification(index * 100 / total, "⬆ $name", false)

            val t0 = System.currentTimeMillis()
            try {
                val input = applicationContext.contentResolver.openInputStream(uri)
                if (input == null) {
                    errors++
                    errorList.add(SyncErrorDetail(name, "не удалось открыть"))
                } else {
                    input.use {
                        val code = WebDavClient.put(
                            url = server + WebDavRepository.encodePath(
                                targetPath.trimEnd('/') + "/" + name),
                            user = user,
                            pass = pass,
                            inputStream = it,
                            fileSize = size ?: 0L
                        )
                        if (code in 200..299) {
                            uploaded++
                            bytes += size ?: 0L
                            files.add(SyncFileDetail(name, size ?: 0L,
                                System.currentTimeMillis() - t0, "Вручную"))
                        } else {
                            errors++
                            errorList.add(SyncErrorDetail(name, "HTTP $code"))
                        }
                    }
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Exception) {
                errors++
                errorList.add(SyncErrorDetail(name, e.message ?: "исключение"))
                Log.e(TAG, "Upload error: $name", e)
            }
        }

        HistoryManager.finalizeRecord(applicationContext, HistoryRecord(
            time = startTime,
            durationMs = System.currentTimeMillis() - startTime,
            checked = total, uploaded = uploaded, downloaded = 0, deleted = 0,
            errors = errors, status = if (errors == 0) "ok" else "error",
            trigger = "user", bytesTransferred = bytes,
            transferMs = System.currentTimeMillis() - tStart,
            filesJson = filesToJson(files), errorsJson = errorsToJson(errorList),
            taskName = "Ручная загрузка", taskId = TASK_ID, totalFiles = total
        ))
        NotificationHelper.showResult(applicationContext, "Ручная загрузка", errors == 0, errors)
        return if (errors == 0) Result.success() else Result.failure()
    }

    // ==================== Утилиты ====================

    private fun finalizeAsCancelled() {
        try {
            HistoryManager.finalizeRecord(applicationContext, HistoryRecord(
                time = recordTime,
                durationMs = System.currentTimeMillis() - recordTime,
                checked = 0, uploaded = 0, downloaded = 0, deleted = 0,
                errors = 1, status = "error", trigger = "user",
                errorsJson = """[{"n":"Ручная загрузка","r":"отменено пользователем"}]""",
                taskName = "Ручная загрузка", taskId = TASK_ID
            ))
        } catch (_: Exception) { }
    }

    private fun queryNameAndSize(uri: Uri): Pair<String?, Long?> {
        try {
            applicationContext.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    val n = if (ni >= 0) c.getString(ni) else null
                    val s = if (si >= 0) c.getLong(si) else null
                    return n to s
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "query: ${e.message}")
        }
        return null to null
    }

    private fun info(progress: Int, text: String, indeterminate: Boolean): ForegroundInfo =
        NotificationHelper.createForegroundInfo(
            applicationContext, "Ручная загрузка", TASK_ID, progress, text, indeterminate)

    private suspend fun updateNotification(progress: Int, text: String, indeterminate: Boolean) {
        val should = synchronized(notificationLock) {
            val now = System.currentTimeMillis()
            if (now - lastNotificationUpdate >= 500L) {
                lastNotificationUpdate = now; true
            } else false
        }
        if (should) {
            try {
                setForeground(info(progress, text, indeterminate))
            } catch (e: Exception) {
                Log.w(TAG, "notification update failed", e)
            }
        }
    }

    private fun filesToJson(list: List<SyncFileDetail>): String {
        val arr = org.json.JSONArray()
        for (f in list) arr.put(org.json.JSONObject().apply {
            put("n", f.name); put("s", f.size); put("m", f.ms); put("d", f.side)
        })
        return arr.toString()
    }

    private fun errorsToJson(list: List<SyncErrorDetail>): String {
        val arr = org.json.JSONArray()
        for (e in list) arr.put(org.json.JSONObject().apply {
            put("n", e.name); put("r", e.reason)
        })
        return arr.toString()
    }
}