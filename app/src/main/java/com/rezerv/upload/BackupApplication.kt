package com.rezerv.upload

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class BackupApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        initTimber()
    }

    private fun initTimber() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.d("Timber initialized in DEBUG mode")
        } else {
            Timber.d("Timber initialized in RELEASE mode")
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.INFO)
            .build()

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val androidContext = context as android.content.Context

        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(WebDavClient.httpClient))
            }
            .memoryCache {
                MemoryCache.Builder()  // ✅ БЕЗ параметров
                    .maxSizePercent(androidContext, 0.25)  // ✅ Context как первый параметр
                    .build()
            }
            // ✅ crossfade убираем отсюда, добавим через transition в components если нужно
            .build()
    }
}