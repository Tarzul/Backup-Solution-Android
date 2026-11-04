package com.rezerv.upload

import android.app.Application
import android.content.Context
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
        val androidContext = context as Context

        // ✅ Создаем OkHttpClient специально для Coil, который автоматически добавляет заголовок Authorization
        val coilHttpClient = WebDavClient.httpClient.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request()
                // Если заголовок уже есть, не трогаем его
                if (request.header("Authorization") != null) {
                    return@addInterceptor chain.proceed(request)
                }

                // Берем сохраненные credentials
                val (server, user, pass) = SecurePrefs.loadCredentials(androidContext)
                if (user.isNotEmpty() && pass.isNotEmpty()) {
                    val authHeader = "Basic " + android.util.Base64.encodeToString(
                        "$user:$pass".toByteArray(),
                        android.util.Base64.NO_WRAP
                    )
                    val newRequest = request.newBuilder()
                        .addHeader("Authorization", authHeader)
                        .build()
                    return@addInterceptor chain.proceed(newRequest)
                }

                chain.proceed(request)
            }
            .build()

        return ImageLoader.Builder(context)
            .components {
                // ✅ Используем наш модифицированный клиент с авто-авторизацией
                add(OkHttpNetworkFetcherFactory(coilHttpClient))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(androidContext, 0.25)
                    .build()
            }
            .build()
    }
}