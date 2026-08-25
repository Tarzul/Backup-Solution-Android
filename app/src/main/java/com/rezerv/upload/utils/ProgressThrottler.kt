package com.rezerv.upload.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Потокобезопасный throttler для прогресса.
 * Автоматически прыгает на Main Thread для обновления UI.
 */
class ProgressThrottler(
    private val minIntervalMs: Long = 250L,
    private val onUpdate: (written: Long, total: Long) -> Unit
) {
    private var lastEmitTime = 0L
    private val lock = Any()

    fun emit(written: Long, total: Long) {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val isFinished = (total > 0 && written >= total)
            
            if (now - lastEmitTime >= minIntervalMs || isFinished || lastEmitTime == 0L) {
                lastEmitTime = now
                // ПРЫГАЕМ НА MAIN THREAD для безопасного обновления UI
                GlobalScope.launch(Dispatchers.Main) {
                    // Защита от переполнения Int для файлов > 2 ГБ
                    val writtenInt = minOf(written, Int.MAX_VALUE.toLong()).toInt()
                    val totalInt = if (total > 0) minOf(total, Int.MAX_VALUE.toLong()).toInt() else 0
                    onUpdate(writtenInt, totalInt)
                }
            }
        }
    }
}