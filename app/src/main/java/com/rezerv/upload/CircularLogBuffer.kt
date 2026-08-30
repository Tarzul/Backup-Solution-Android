package com.rezerv.upload

/**
 * Кольцевой буфер для логирования без переполнения памяти.
 * Автоматически удаляет старые записи при достижении лимита.
 */
class CircularLogBuffer(
    private val maxLines: Int = 200,
    private val maxChars: Int = 20000
) {
    private val buffer = ArrayDeque<String>(maxLines)
    private var currentLength = 0

    @Synchronized
    fun add(line: String) {
        buffer.addLast(line)
        currentLength += line.length + 1  // +1 для \n
        
        // Удаляем старые строки при переполнении
        while (buffer.size > maxLines || currentLength > maxChars) {
            val removed = buffer.removeFirst()
            currentLength -= removed.length + 1
        }
    }

    @Synchronized
    fun getText(): String {
        return buffer.joinToString("\n")
    }

    @Synchronized
    fun clear() {
        buffer.clear()
        currentLength = 0
    }

    val size: Int get() = buffer.size
}