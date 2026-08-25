package com.rezerv.upload.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ProgressThrottler(
    private val scope: CoroutineScope,  // ✅ Принимаем scope извне
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
                // ✅ Используем переданный scope вместо GlobalScope
                scope.launch(Dispatchers.Main) {
                    val writtenInt = minOf(written, Int.MAX_VALUE.toLong()).toInt()
                    val totalInt = if (total > 0) minOf(total, Int.MAX_VALUE.toLong()).toInt() else 0
                    onUpdate(writtenInt.toLong(), totalInt.toLong())
                }
            }
        }
    }
}