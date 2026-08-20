package com.rezerv.upload

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder

object RemoteImageLoader {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ИСПРАВЛЕНО: 1/8 heap вместо жёстких 128 МБ (защита от OOM на слабых устройствах)
    private val memoryCache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    suspend fun loadThumbnail(server: String, user: String, pass: String, path: String): Bitmap? =
        load(server, user, pass, path, 160)

    suspend fun loadFull(server: String, user: String, pass: String, path: String): Bitmap? =
        load(server, user, pass, path, 1200)

    suspend fun load(server: String, user: String, pass: String, path: String, reqSize: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            val key = "$reqSize:$path"
            memoryCache.get(key)?.let { return@withContext it }
            // ИСПРАВЛЕНО: читаем из дискового кэша (миниатюры ≤200, превью >200)
            ImageDiskCache.run { if (reqSize <= 200) getThumb(path) else getPreview(path) }?.let {
                memoryCache.put(key, it); return@withContext it
            }
            val base = WebDavRepository.normalizeBaseUrl(server) ?: return@withContext null
            try {
                val resp = WebDavClient.get(base + encodePath(path), user, pass)
                val bytes = resp.body?.byteStream()?.use { it.readBytes() }
                resp.close()
                if (bytes == null) return@withContext null
                val bmp = decodeSampled(bytes, reqSize) ?: return@withContext null
                memoryCache.put(key, bmp)
                // ИСПРАВЛЕНО: пишем в дисковый кэш
                if (reqSize <= 200) ImageDiskCache.putThumb(path, bmp) else ImageDiskCache.putPreview(path, bmp)
                bmp
            } catch (e: Exception) { null }
        }

    fun prefetch(server: String, user: String, pass: String, files: List<WebDavRepository.FileInfo>, limit: Int = 20) {
        val images = files.asSequence()
            .filter { !it.isDirectory && FileUtils.isImageFile(it.name) }
            .take(limit).toList()
        if (images.isEmpty()) return
        images.chunked(6).forEach { batch ->
            scope.launch { batch.forEach { f -> loadThumbnail(server, user, pass, f.path) } }
        }
    }

    private fun decodeSampled(bytes: ByteArray, reqSize: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= reqSize) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun encodePath(p: String): String = p.split("/").joinToString("/") {
        if (it.isEmpty()) "" else URLEncoder.encode(it, "UTF-8").replace("+", "%20")
    }
}