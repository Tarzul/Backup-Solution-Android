package com.rezerv.upload

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltAndroidApp
class BackupApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // ✅ ЧЁРНЫЙ ЯЩИК v2: ловим ЛЮБОЙ неперехваченный краш с ЛЮБОГО потока
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val trace = android.util.Log.getStackTraceString(error)
                getSharedPreferences("crash_box", MODE_PRIVATE).edit()
                    .putString(
                        "last_crash",
                        "${java.util.Date()}\nThread: ${thread.name}\n" +
                            "${error.javaClass.name}: ${error.message}\n$trace"
                    )
                    .commit()
            } catch (_: Throwable) { }
            defaultHandler?.uncaughtException(thread, error)
        }

        // чистим осиротевшие live-записи после прошлого запуска
        CoroutineScope(Dispatchers.IO).launch {
            HistoryManager.getRecords(this@BackupApplication)
        }
    }
}