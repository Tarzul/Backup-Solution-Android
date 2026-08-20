package com.rezerv.upload

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.Executors

object ImageDiskCache {

    private const val TAG = "ImageDiskCache"
    private const val MAX_CACHE_SIZE_MB = 100L // Лимит 100 МБ

    private lateinit var thumbsDir: File
    private lateinit var previewsDir: File

    private val writeExecutor = Executors.newSingleThreadExecutor()

    fun init(context: Context) {
        thumbsDir = File(context.cacheDir, "thumbs").apply { mkdirs() }
        previewsDir = File(context.cacheDir, "previews").apply { mkdirs() }
    }

    private fun isInitialized(): Boolean = ::thumbsDir.isInitialized && ::previewsDir.isInitialized

    // MD5 хеш вместо hashCode() для уникальности
    private fun key(path: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(path.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    // ===== Чтение (вызывать из фонового потока или с осторожностью) =====

    fun getThumb(path: String): Bitmap? {
        if (!isInitialized()) {
            Log.w(TAG, "Cache not initialized. Call init() first.")
            return null
        }
        return try {
            val f = File(thumbsDir, key(path) + ".jpg")
            if (!f.exists() || f.length() == 0L) null
            else {
                // Обновляем время доступа для LRU
                f.setLastModified(System.currentTimeMillis())
                BitmapFactory.decodeFile(f.absolutePath)
            }
        } catch (e: Exception) {
            Log.e(TAG, "getThumb error for $path", e)
            null
        }
    }

    fun getPreview(path: String): Bitmap? {
        if (!isInitialized()) {
            Log.w(TAG, "Cache not initialized.")
            return null
        }
        return try {
            val f = File(previewsDir, key(path) + ".jpg")
            if (!f.exists() || f.length() == 0L) null
            else {
                f.setLastModified(System.currentTimeMillis())
                BitmapFactory.decodeFile(f.absolutePath)
            }
        } catch (e: Exception) {
            Log.e(TAG, "getPreview error for $path", e)
            null
        }
    }

    // ===== Запись (асинхронная с атомарным переименованием) =====

    fun putThumb(path: String, bitmap: Bitmap) {
        if (!isInitialized()) return
        writeExecutor.execute {
            try {
                val target = File(thumbsDir, key(path) + ".jpg")
                val temp = File(thumbsDir, key(path) + ".tmp")
                
                // Пишем во временный файл
                FileOutputStream(temp).use { 
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) 
                }
                
                // Атомарно переименовываем (защищает от гонок при чтении)
                if (temp.exists()) {
                    temp.renameTo(target)
                }
                
                // Проверяем размер кэша и удаляем старые файлы
                evictOldFiles(thumbsDir, MAX_CACHE_SIZE_MB)
            } catch (e: Exception) {
                Log.e(TAG, "putThumb error for $path", e)
            }
        }
    }

    fun putPreview(path: String, bitmap: Bitmap) {
        if (!isInitialized()) return
        writeExecutor.execute {
            try {
                val target = File(previewsDir, key(path) + ".jpg")
                val temp = File(previewsDir, key(path) + ".tmp")
                
                FileOutputStream(temp).use { 
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) 
                }
                
                if (temp.exists()) {
                    temp.renameTo(target)
                }
                
                evictOldFiles(previewsDir, MAX_CACHE_SIZE_MB)
            } catch (e: Exception) {
                Log.e(TAG, "putPreview error for $path", e)
            }
        }
    }

    // LRU Eviction: удаляем самые старые файлы, если кэш превысил лимит
    private fun evictOldFiles(dir: File, maxSizeMB: Long) {
        try {
            val maxSizeBytes = maxSizeMB * 1024 * 1024
            val files = dir.listFiles { file -> file.isFile && file.name.endsWith(".jpg") } ?: return
            
            var totalSize = files.sumOf { it.length() }
            
            if (totalSize <= maxSizeBytes) return
            
            // Сортируем по времени изменения (самые старые первыми)
            val sorted = files.sortedBy { it.lastModified() }
            
            for (file in sorted) {
                if (totalSize <= maxSizeBytes) break
                val size = file.length()
                if (file.delete()) {
                    totalSize -= size
                    Log.d(TAG, "Evicted old cache file: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "evictOldFiles error", e)
        }
    }

    // Очистка всего кэша (например, при выходе из аккаунта)
    fun clear() {
        if (!isInitialized()) return
        writeExecutor.execute {
            try {
                thumbsDir.listFiles()?.forEach { it.delete() }
                previewsDir.listFiles()?.forEach { it.delete() }
                Log.d(TAG, "Cache cleared")
            } catch (e: Exception) {
                Log.e(TAG, "clear error", e)
            }
        }
    }
}