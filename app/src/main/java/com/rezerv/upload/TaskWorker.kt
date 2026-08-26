package com.rezerv.upload

import android.content.Context
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rezerv.upload.data.TaskRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltWorker
class TaskWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: TaskRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "TaskWorker"
        private const val MAX_RETRY = 3
    }

    // ✅ Throttler для Notification (не чаще раза в 500мс)
    private var lastNotificationUpdate = 0L
    private val notificationLock = Any()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val taskId = inputData.getString("taskId")
            val trigger = if (!taskId.isNullOrBlank()) "user" else "schedule"
            Log.d(TAG, "▶ doWork: taskId=$taskId, trigger=$trigger, попытка=$runAttemptCount")

            val tasksToRun = if (!taskId.isNullOrBlank()) {
                listOfNotNull(repository.getTaskById(taskId))
            } else {
                repository.getActiveTasks()
            }

            if (tasksToRun.isEmpty()) return@withContext Result.success()
            if (!isNetworkAvailable()) {
                return@withContext if (runAttemptCount < MAX_RETRY) Result.retry() else Result.failure()
            }

            var totalErrors = 0
            for (task in tasksToRun) {
                if (!passesConditions(task)) continue

                try {
                    val taskStartTime = System.currentTimeMillis()
                    if (!HistoryManager.createLiveRecord(applicationContext, taskStartTime, task.name, trigger, task.id)) {
                        Log.w(TAG, "'${task.name}' уже выполняется — пропуск")
                        continue
                    }

                    // ✅ Foreground Service: показываем уведомление перед началом
                    setForeground(NotificationHelper.createForegroundInfo(
                        applicationContext, task.name, task.id, 0, "Подготовка...", true  // ✅ Добавили task.id
                    ))

                    val result = SyncEngine.runTask(
                        applicationContext, task,
                        trigger = trigger,
                        startTime = taskStartTime,
                        onProgress = { message ->
                            Log.d(TAG, "  $message")
                            // ✅ Оборачиваем в launch для вызова suspend функции
                            launch {
                                throttledUpdate(task.id, task.name, 0, message, isIndeterminate = true)
                            }
                        },
                        onLiveUpdate = { name, idx, total ->
                            HistoryManager.updateLiveRecord(applicationContext, taskStartTime, name, idx, total)
                            val progress = if (total > 0) (idx * 100 / total) else 0
                            // ✅ Оборачиваем в launch для вызова suspend функции
                            launch {
                                throttledUpdate(task.id, task.name, progress, name, isIndeterminate = false)
                            }
                        }
                    )

                    val status = if (result.errors == 0) "ok" else "error"
                    repository.saveTask(task.copy(
                        lastRun = System.currentTimeMillis(), lastStatus = status))
                    totalErrors += result.errors

                    if (result.errors == 0 && task.notifyOnSuccess)
                        NotificationHelper.showResult(applicationContext, task.name, true, 0)
                    if (result.errors > 0 && task.notifyOnError)
                        NotificationHelper.showResult(applicationContext, task.name, false, result.errors)

                } catch (t: Throwable) {
                    if (t is kotlinx.coroutines.CancellationException) throw t
                    Log.e(TAG, "'${task.name}' упало", t)
                    totalErrors++
                    repository.saveTask(task.copy(
                        lastRun = System.currentTimeMillis(), lastStatus = "error"))
                    if (task.notifyOnError)
                        NotificationHelper.showResult(applicationContext, task.name, false, 1)
                }
            }

            if (totalErrors > 0 && runAttemptCount < MAX_RETRY) Result.retry()
            else Result.success()

        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            Log.e(TAG, "Критическая ошибка", t)
            Result.failure()
        }
    }

    private suspend fun throttledUpdate(
        taskId: String,
        taskName: String,
        progress: Int,
        text: String?,
        isIndeterminate: Boolean
    ) {
        // ✅ ШАГ 1: Внутри synchronized только проверяем условие
        val shouldUpdate = synchronized(notificationLock) {
            val now = System.currentTimeMillis()
            if (now - lastNotificationUpdate >= 500L) {
                lastNotificationUpdate = now
                true  // Нужно обновить
            } else {
                false // Слишком рано, пропускаем
            }
        }
        
        // ✅ ШАГ 2: Suspend вызов ВЫНЕСЕН за пределы synchronized
        if (shouldUpdate) {
            try {
                setForeground(NotificationHelper.createForegroundInfo(
                    applicationContext, taskName, taskId, progress, text, isIndeterminate
                ))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update notification", e)
            }
        }
    }

    private fun passesConditions(task: SyncTask): Boolean {
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm != null) {
            val caps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } else null
            if (caps != null) {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !task.useWifi) return false
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) && !task.useMobile) return false
            }
        }
        if (task.onlyCharging) {
            val b = applicationContext.registerReceiver(null, IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val st = b?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            if (st != BatteryManager.BATTERY_STATUS_CHARGING && st != BatteryManager.BATTERY_STATUS_FULL) return false
        }
        return true
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val n = cm.activeNetwork ?: return true
                cm.getNetworkCapabilities(n)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            } else {
                @Suppress("DEPRECATION")
                cm.activeNetworkInfo?.isConnected ?: true
            }
        } catch (e: Exception) { true }
    }
}