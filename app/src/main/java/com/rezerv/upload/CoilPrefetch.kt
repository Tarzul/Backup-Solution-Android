package com.rezerv.upload

import android.content.Context
import coil.imageLoader
import coil.request.ImageRequest
import coil.target.Target
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
        images.chunked(6).forEach { batch ->
            scope.launch {
                batch.forEach { f ->
                    val req = ImageRequest.Builder(app)
                        .data(WebDavImages.url(server, f.path))
                        .addHeader("Authorization", WebDavImages.basicHeader(user, pass))
                        .memoryCacheKey(WebDavImages.cacheKey("thumb", f.path, f.size))
                        .diskCacheKey(WebDavImages.cacheKey("thumb", f.path, f.size))
                        .size(96)
                        .target(object : Target {})
                        .build()
                    app.imageLoader.execute(req)
                }
            }
        }
    }
}