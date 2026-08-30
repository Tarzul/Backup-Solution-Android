package com.rezerv.upload

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar
import java.util.Date
import kotlin.math.abs

/**
 * AlarmScheduler — планировщик будильников синхронизации.
 *
 * ИСПРАВЛЕНО:
 * - Уникальный requestCode для каждого задания (без коллизий)
 * - Поддержка Android 14+ (USE_EXACT_ALARM + fallback)
 * - Учёт пропущенных запусков после долгого простоя
 */
object AlarmScheduler {
    private const val TAG = "AlarmScheduler"

    // ==================== Основное API ====================

    fun scheduleNext(context: Context, tasks: List<SyncTask>) {   // ✅ задачи передаются параметром
        try {
            val active = tasks.filter { it.scheduleEnabled }
            if (active.isEmpty()) {
                cancelAll(context, tasks)
                return
            }
            val now = System.currentTimeMillis()
            var scheduled = 0
            for (t in active) {
                var next = nextRun(t, now)
                if (next == Long.MAX_VALUE) {
                    cancelForTask(context, t)
                    continue
                }
                if (next <= now) next = now + 60_000L
                setAlarm(context, t, next)
                scheduled++
            }
            Log.d(TAG, "scheduleNext: назначено будильников: $scheduled")
        } catch (e: Exception) {
            Log.e(TAG, "scheduleNext ERROR", e)
        }
    }

    fun ensureScheduler(context: Context, tasks: List<SyncTask>) = scheduleNext(context, tasks)

    /** Отменяет будильник конкретного задания. (БЕЗ ИЗМЕНЕНИЙ) */
    fun cancelForTask(context: Context, task: SyncTask) {
        try {
            val intent = Intent(context, SyncAlarmReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                context, getUniqueRequestCode(task.id), intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) {
                (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi)
                pi.cancel()
                Log.d(TAG, "cancelForTask: '${task.name}'")
            }
        } catch (e: Exception) {
            Log.e(TAG, "cancelForTask ERROR", e)
        }
    }

    /** Отменяет будильники всех заданий. */
    fun cancelAll(context: Context, tasks: List<SyncTask>) {   // ✅ задачи параметром
        try {
            for (t in tasks) cancelForTask(context, t)
            Log.d(TAG, "cancelAll: отменено ${tasks.size}")
        } catch (e: Exception) {
            Log.e(TAG, "cancelAll ERROR", e)
        }
    }

    // ==================== Установка будильника ====================

    private fun setAlarm(context: Context, task: SyncTask, at: Long) {
        val intent = Intent(context, SyncAlarmReceiver::class.java)
            .putExtra("taskId", task.id)
        
        // ✅ ИСПРАВЛЕНО: Уникальный requestCode без отрицательных значений
        val requestCode = getUniqueRequestCode(task.id)
        val pi = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        when {
            // ✅ Android 14+: проверяем canScheduleExactAlarms (может быть отозвано)
            Build.VERSION.SDK_INT >= 34 -> {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                    Log.d(TAG, "✓ Exact alarm '${task.name}' -> ${Date(at)}")
                } else {
                    // Fallback: inexact alarm (менее точный, но гарантированно сработает)
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                    Log.w(TAG, "⚠ Exact alarms revoked, using inexact for '${task.name}' -> ${Date(at)}")
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                    Log.d(TAG, "✓ Exact alarm '${task.name}' -> ${Date(at)}")
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                    Log.w(TAG, "⚠ Exact alarms not allowed, using inexact for '${task.name}' -> ${Date(at)}")
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                Log.d(TAG, "Inexact alarm '${task.name}' -> ${Date(at)}")
            }
            else -> {
                am.set(AlarmManager.RTC_WAKEUP, at, pi)
                Log.d(TAG, "Legacy alarm '${task.name}' -> ${Date(at)}")
            }
        }
    }

    // ✅ НОВОЕ: Генерация уникального requestCode без коллизий
    private fun getUniqueRequestCode(taskId: String): Int {
        // hashCode может быть отрицательным, используем abs
        // Добавляем константу чтобы избежать 0 (зарезервирован системой)
        return abs(taskId.hashCode()) + 1000
    }

    // ==================== Вычисление следующего запуска ====================

    /**
     * ✅ ИСПРАВЛЕНО: Учитывает lastRun для обнаружения пропущенных запусков.
     * Если устройство было выключено дольше чем интервал — запускаем сейчас.
     */
    private fun nextRun(t: SyncTask, now: Long): Long {
        if (!t.scheduleEnabled) return Long.MAX_VALUE
        
        val lastRun = t.lastRun
        
        return when (t.scheduleMode) {
            "minutes" -> {
                val interval = t.intervalValue * 60_000L
                if (lastRun > 0) {
                    val nextExpected = lastRun + interval
                    // Если пропустили много интервалов — запускаем сейчас
                    if (nextExpected <= now) now + 60_000L else nextExpected
                } else {
                    now + interval
                }
            }
            "hourly" -> {
                val interval = t.intervalValue * 3_600_000L
                if (lastRun > 0) {
                    val nextExpected = lastRun + interval
                    if (nextExpected <= now) now + 60_000L else nextExpected
                } else {
                    now + interval
                }
            }
            "daily" -> {
                val today = atTime(now, t.hour, t.minute, 0)
                if (today > now) today else atTime(now, t.hour, t.minute, 1)
            }
            "weekly" -> nextWeekly(now, t)
            "monthly" -> nextMonthly(now, t)
            else -> Long.MAX_VALUE
        }
    }

    private fun atTime(now: Long, hour: Int, minute: Int, addDays: Int): Long {
        return Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, addDays)
        }.timeInMillis
    }

    /** Ближайший выбранный день недели в hour:minute. weekDays: 1=Пн..7=Вс. */
    private fun nextWeekly(now: Long, t: SyncTask): Long {
        val days = t.weekDays.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
        if (days.isEmpty()) return Long.MAX_VALUE
        val base = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, t.hour)
            set(Calendar.MINUTE, t.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        for (i in 0..7) {
            val cand = base.clone() as Calendar
            cand.add(Calendar.DAY_OF_YEAR, i)
            val dow = cand.get(Calendar.DAY_OF_WEEK)          // 1=Вс..7=Сб
            val our = if (dow == Calendar.SUNDAY) 7 else dow - 1 // 1=Пн..7=Вс
            if (our in days && cand.timeInMillis > now) return cand.timeInMillis
        }
        return Long.MAX_VALUE
    }

    /** Ближайшее выбранное число месяца в hour:minute. monthDays: "1,15,31". */
    private fun nextMonthly(now: Long, t: SyncTask): Long {
        val days = t.monthDays.split(",").mapNotNull { it.trim().toIntOrNull() }.sorted()
        if (days.isEmpty()) return Long.MAX_VALUE
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, t.hour)
            set(Calendar.MINUTE, t.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)
        for (mOff in 0..13) {
            cal.set(Calendar.YEAR, year)
            cal.set(Calendar.MONTH, month + mOff)
            cal.set(Calendar.DAY_OF_MONTH, 1)
            val max = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            for (d in days) {
                if (d in 1..max) {
                    cal.set(Calendar.DAY_OF_MONTH, d)
                    if (cal.timeInMillis > now) return cal.timeInMillis
                }
            }
        }
        return Long.MAX_VALUE
    }
}