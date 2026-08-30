package com.rezerv.upload.permissions

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionHandler @Inject constructor() {

    fun getRequiredMediaPermissions(activity: Activity): List<String> {
        val permissions = mutableListOf<String>()

        when {
            Build.VERSION.SDK_INT >= 34 -> {
                if (activity.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) !=
                    PackageManager.PERMISSION_GRANTED) {
                    permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
                }
                if (activity.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) !=
                    PackageManager.PERMISSION_GRANTED) {
                    permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
                }
                if (activity.checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) !=
                    PackageManager.PERMISSION_GRANTED) {
                    permissions.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                }
            }
            Build.VERSION.SDK_INT >= 33 -> {
                if (activity.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) !=
                    PackageManager.PERMISSION_GRANTED) {
                    permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
                }
                if (activity.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) !=
                    PackageManager.PERMISSION_GRANTED) {
                    permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
                }
            }
            Build.VERSION.SDK_INT >= 23 -> {
                if (activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) !=
                    PackageManager.PERMISSION_GRANTED) {
                    permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                if (activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                    PackageManager.PERMISSION_GRANTED) {
                    permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }

        return permissions
    }

    fun shouldRequestNotifications(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < 33) return false

        return when {
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED -> false
            activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                AlertDialog.Builder(activity)
                    .setTitle("Уведомления")
                    .setMessage("Приложение отправляет уведомления о результатах синхронизации. Разрешить?")
                    .setPositiveButton("Разрешить") { _, _ -> }
                    .setNegativeButton("Не сейчас", null)
                    .show()
                true
            }
            else -> true
        }
    }

    fun needsExactAlarmPermission(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false

        val alarmManager = activity.getSystemService(AlarmManager::class.java) ?: return false

        return if (Build.VERSION.SDK_INT >= 34) {
            !alarmManager.canScheduleExactAlarms()
        } else {
            false
        }
    }

    fun needsBatteryOptimizationExemption(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false

        val powerManager = activity.getSystemService(PowerManager::class.java) ?: return false

        return !powerManager.isIgnoringBatteryOptimizations(activity.packageName)
    }

    fun createExactAlarmIntent(activity: Activity): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${activity.packageName}"))
        } else {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        }
    }

    fun createBatteryOptimizationIntent(activity: Activity): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${activity.packageName}"))
    }
}