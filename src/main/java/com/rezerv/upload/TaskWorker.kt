package com.rezerv.upload

import android.content.Context
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TaskWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "TaskWorker"
        private const val MAX_RETRY = 3
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val taskId = inputData.getString("taskId")
            
            // ИСПРАВЛЕНО: если передан taskId — выполняем только его
            val activeTasks = if (!taskId.isNullOrBlank()) {
                val task = TaskManager.getById(applicationContext, taskId)
                if (task != null && task.scheduleEnabled) listOf(task) else emptyList()
            } else {
                // Fallback: если taskId не передан (старый будильник) — выполняем все
                TaskManager.getActiveTasks(applicationContext)
            }
            
            if (activeTasks.isEmpty()) return@withContext Result.success()
            
            if (!isNetworkAvailable()) {
                return@withContext if (runAttemptCount < MAX_RETRY) Result.retry() else Result.failure()
            }

            var totalErrors = 0
            for (task in activeTasks) {
                // УСЛОВИЯ ИЗ ПАНЕЛИ «ДОПОЛНИТЕЛЬНО»: Wi-Fi / мобильная / зарядка
                if (!passesConditions(task)) {
                    Log.d(TAG, "Задание '${task.name}' пропущено: условия не выполнены")
                    continue
                }
                try {
                    val result = SyncEngine.runTask(applicationContext, task, trigger = "schedule") {
                        Log.d(TAG, "  $it")
                    }
                    val status = if (result.errors == 0) "ok" else "error"
                    TaskManager.upsert(applicationContext, task.copy(
                        lastRun = System.currentTimeMillis(), lastStatus = status))
                    totalErrors += result.errors
                    // УВЕДОМЛЕНИЯ ИЗ ПАНЕЛИ «ДОПОЛНИТЕЛЬНО»
                    if (result.errors == 0 && task.notifyOnSuccess)
                        NotificationHelper.showResult(applicationContext, task.name, true, 0)
                    if (result.errors > 0 && task.notifyOnError)
                        NotificationHelper.showResult(applicationContext, task.name, false, result.errors)
                } catch (e: Exception) {
                    Log.e(TAG, "Задание '${task.name}' упало с исключением", e)
                    totalErrors++
                    TaskManager.upsert(applicationContext, task.copy(
                        lastRun = System.currentTimeMillis(), lastStatus = "error"))
                    if (task.notifyOnError)
                        NotificationHelper.showResult(applicationContext, task.name, false, 1)
                }
            }
            if (totalErrors > 0 && runAttemptCount < MAX_RETRY) Result.retry()
            else if (totalErrors > 0) Result.failure()
            else Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Критическая ошибка", e)
            Result.failure()
        }
    }

    /** Проверяет условия задания: транспорт сети и зарядку. */
    private fun passesConditions(task: SyncTask): Boolean {
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val caps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } else null
        if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false

        val transportOk = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> task.useWifi
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> task.useMobile
            else -> true // Ethernet и прочие транспорты не ограничиваем
        }
        if (!transportOk) return false

        if (task.onlyCharging) {
            val b = applicationContext.registerReceiver(null,
                IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val st = b?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = st == BatteryManager.BATTERY_STATUS_CHARGING ||
                    st == BatteryManager.BATTERY_STATUS_FULL
            if (!charging) return false
        }
        return true
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val n = cm.activeNetwork ?: return false
            cm.getNetworkCapabilities(n)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } else @Suppress("DEPRECATION") {
            cm.activeNetworkInfo?.isConnected == true
        }
    }
}