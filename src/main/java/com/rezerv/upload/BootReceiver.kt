package com.rezerv.upload

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    
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
                try {
                    AlarmScheduler.scheduleNext(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to reschedule alarms after boot", e)
                }
            }
            else -> {
                Log.w(TAG, "Unexpected action received: $action")
            }
        }
    }
}