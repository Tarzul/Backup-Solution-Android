package com.rezerv.upload

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