# ==================== Модели данных (JSON-сериализация) ====================
-keep class com.rezerv.upload.SyncTask { *; }
-keep class com.rezerv.upload.HistoryRecord { *; }
-keep class com.rezerv.upload.SyncFileDetail { *; }
-keep class com.rezerv.upload.SyncFolderDetail { *; }

# ==================== WorkManager ====================
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }

# ==================== BroadcastReceiver ====================
-keep class com.rezerv.upload.SyncAlarmReceiver { *; }
-keep class com.rezerv.upload.BootReceiver { *; }

# ==================== Custom View (используется в XML-разметке) ====================
-keep class com.rezerv.upload.HistoryChartView { *; }

# ==================== OkHttp ====================
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# ==================== Kotlin Coroutines ====================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ==================== AndroidX Security Crypto ====================
-keep class androidx.security.crypto.** { *; }

# ==================== Enum ====================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}