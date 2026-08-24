# ==================== OkHttp ====================
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# OkHttp платформа
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ==================== Kotlin Coroutines ====================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ==================== EncryptedSharedPreferences ====================
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# ==================== Coil (загрузка изображений) ====================
-dontwarn coil.**
-keep class coil.** { *; }

# ==================== Kotlin Reflection ====================
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings {
    <fields>;
}

# ==================== WebDAV XML парсинг ====================
-keep class org.xmlpull.v1.** { *; }
-dontwarn org.xmlpull.v1.**

# ==================== Сохраняем модели данных ====================
-keep class com.rezerv.upload.SyncTask { *; }
-keep class com.rezerv.upload.HistoryRecord { *; }
-keep class com.rezerv.upload.SyncFileDetail { *; }
-keep class com.rezerv.upload.SyncFolderDetail { *; }
-keep class com.rezerv.upload.SyncErrorDetail { *; }
-keep class com.rezerv.upload.WebDavRepository$FileInfo { *; }
-keep class com.rezerv.upload.WebDavRepository$FileMetadata { *; }
-keep class com.rezerv.upload.WebDavRepository$ConnectionResult { *; }

# ==================== WorkManager ====================
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }

# ==================== Сохраняем enum для UI ====================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}