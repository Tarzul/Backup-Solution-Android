package com.rezerv.upload

import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.target.Target
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object CoilPrefetch {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun prefetch(context: Context, server: String, user: String, pass: String,
                 files: List<WebDavRepository.FileInfo>, limit: Int = 20) {
        val images = files.asSequence()
            .filter { !it.isDirectory && FileUtils.isImageFile(it.name) }
            .take(limit).toList()
        if (images.isEmpty()) return

        val app = context.applicationContext
        val imageLoader = SingletonImageLoader.get(app)

        images.chunked(6).forEach { batch ->
            scope.launch {
                batch.forEach { f ->
                    val req = ImageRequest.Builder(app)
                        .data(WebDavImages.url(server, f.path))
                        // ✅ addHeader() работает напрямую
                        .addHeader("Authorization", WebDavImages.basicHeader(user, pass))
                        .memoryCacheKey(WebDavImages.cacheKey("thumb", f.path, f.size))
                        .diskCacheKey(WebDavImages.cacheKey("thumb", f.path, f.size))
                        .size(96)
                        .target(object : Target {})
                        .build()
                    imageLoader.enqueue(req)
                }
            }
        }
    }
}