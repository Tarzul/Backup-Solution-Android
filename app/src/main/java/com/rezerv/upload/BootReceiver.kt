package com.rezerv.upload

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.rezerv.upload.data.SyncScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint   // ✅ Для инъекции Hilt
class BootReceiver : BroadcastReceiver() {
    
    @Inject lateinit var syncScheduler: SyncScheduler   // ✅ Инжектим планировщик
    
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON", // HTC, некоторые другие
            "com.htc.intent.action.QUICKBOOT_POWERON" -> {
                Log.i(TAG, "Received action: $action. Rescheduling alarms.")
                
                // ✅ goAsync() защищает процесс от уничтожения системой
                // пока мы читаем задачи из Room и планируем будильники
                val pendingResult = goAsync()
                
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        syncScheduler.ensureScheduler(context)   // ✅ Room-based
                        Log.i(TAG, "✓ Alarms rescheduled successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to reschedule alarms after boot", e)
                    } finally {
                        // ✅ ОБЯЗАТЕЛЬНО: сообщаем системе, что BroadcastReceiver закончил работу
                        pendingResult.finish()
                    }
                }
            }
            else -> {
                Log.w(TAG, "Unexpected action received: $action")
            }
        }
    }
}