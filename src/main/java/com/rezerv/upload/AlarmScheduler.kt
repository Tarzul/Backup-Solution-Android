package com.rezerv.upload

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar
import java.util.Date

/**
 * AlarmScheduler — планировщик будильников синхронизации.
 *
 * ИСПРАВЛЕНО: каждое задание имеет СВОЙ независимый будильник
 * (requestCode = task.id.hashCode(), в Intent передаётся taskId).
 * При срабатывании воркер выполняет только это задание —
 * задания с разным расписанием больше не запускают друг друга.
 */
object AlarmScheduler {
    private const val TAG = "AlarmScheduler"

    // ==================== Основное API ====================

    /** Переназначает будильники для ВСЕХ активных заданий. */
    fun scheduleNext(context: Context) {
        try {
            val tasks = TaskManager.getActiveTasks(context)
            if (tasks.isEmpty()) {
                cancelAll(context)
                return
            }
            val now = System.currentTimeMillis()
            var scheduled = 0
            for (t in tasks) {
                var next = nextRun(t, now)
                if (next == Long.MAX_VALUE) {
                    cancelForTask(context, t)
                    continue
                }
                if (next <= now) next = now + 60_000L   // страховка
                setAlarm(context, t, next)
                scheduled++
            }
            Log.d(TAG, "scheduleNext: назначено будильников: $scheduled")
        } catch (e: Exception) {
            Log.e(TAG, "scheduleNext ERROR", e)
        }
    }

    /** Вызывается при старте приложения / после загрузки системы. */
    fun ensureScheduler(context: Context) {
        scheduleNext(context)
    }

    /** ИСПРАВЛЕНО: отменяет будильник конкретного задания (вызывать ДО удаления задания из хранилища!). */
    fun cancelForTask(context: Context, task: SyncTask) {
        try {
            val intent = Intent(context, SyncAlarmReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                context, task.id.hashCode(), intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            if (pi != null) {
                (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi)
                pi.cancel()
                Log.d(TAG, "cancelForTask: '${task.name}'")
            }
        } catch (e: Exception) {
            Log.e(TAG, "cancelForTask ERROR", e)
        }
    }

    /** ИСПРАВЛЕНО: отменяет будильники всех заданий, лежащих в хранилище. */
    fun cancelAll(context: Context) {
        try {
            val all = TaskManager.load(context)
            for (t in all) cancelForTask(context, t)
            Log.d(TAG, "cancelAll: отменено ${all.size}")
        } catch (e: Exception) {
            Log.e(TAG, "cancelAll ERROR", e)
        }
    }

    // ==================== Установка будильника ====================

    private fun setAlarm(context: Context, task: SyncTask, at: Long) {
        val intent = Intent(context, SyncAlarmReceiver::class.java)
            .putExtra("taskId", task.id)
        // ИСПРАВЛЕНО: requestCode = hash(id) — у каждого задания свой PendingIntent
        val pi = PendingIntent.getBroadcast(
            context, task.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && am.canScheduleExactAlarms() ->
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else -> am.set(AlarmManager.RTC_WAKEUP, at, pi)
        }
        Log.d(TAG, "Будильник '${task.name}' -> ${Date(at)}")
    }

    // ==================== Вычисление следующего запуска ====================

    private fun nextRun(t: SyncTask, now: Long): Long {
        if (!t.scheduleEnabled) return Long.MAX_VALUE
        return when (t.scheduleMode) {
            // ИСПРАВЛЕНО: планируем от текущего момента, а не от устаревшего lastRun
            "minutes" -> now + t.intervalValue * 60_000L
            "hourly" -> now + t.intervalValue * 3_600_000L
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