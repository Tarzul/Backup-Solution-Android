# ==================== Общие правила ====================
-optimizationpasses 5
-dontusemixedcaseclassnames
-verbose
-dontpreverify
-allowaccessmodification
-repackageclasses ''

# Сохраняем аннотации и сигнатуры (нужно для reflection)
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes InnerClasses,EnclosingMethod

# ==================== OkHttp ====================
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# OkHttp платформы
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ==================== Kotlin Coroutines ====================
-dontwarn kotlinx.coroutines.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
# Dispatchers.Main
-keepclassmembers class kotlinx.coroutines.android.AndroidDispatcherFactory {
    public <init>();
}

# ==================== Kotlin Reflection & Metadata ====================
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class **$Companion {
    <fields>;
    <methods>;
}

# ==================== Jetpack Compose (КРИТИЧНО!) ====================
-dontwarn androidx.compose.**

# Сохраняем Composable функции и их аннотации
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.foundation.** { *; }
-keep class androidx.compose.material.** { *; }
-keep class androidx.compose.material3.** { *; }
-keep class androidx.compose.animation.** { *; }

# Compose компилятор генерирует специальные классы
-keep class * extends androidx.compose.runtime.Composer { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# Compose state
-keepclassmembers class * {
    @androidx.compose.runtime.State *;
}

# Compose Preview (только для debug, но оставляем для safety)
-dontwarn androidx.compose.ui.tooling.**

# ==================== Room Database ====================
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Room DAO и Entity
-keep class com.rezerv.upload.data.local.** { *; }

# ==================== Hilt DI ====================
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Hilt генерируемые классы
-keep class **_HiltModules { *; }
-keep class **_HiltComponents { *; }
-keep class **_Factory { *; }
-keep class **_MembersInjector { *; }
-keepnames @dagger.hilt.android.lifecycle.HiltViewModel class *

# Сохраняем классы с @Inject конструкторами
-keepclasseswithmembers class * {
    @javax.inject.Inject <init>(...);
}

# @AndroidEntryPoint Activities и Fragments
-keep @dagger.hilt.android.AndroidEntryPoint class *

# ==================== EncryptedSharedPreferences ====================
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**
# Conscrypt для шифрования
-keep class org.conscrypt.** { *; }

# ==================== Coil 3.x (Compose) ====================
-dontwarn coil.**
-dontwarn coil3.**
-keep class coil.** { *; }
-keep class coil3.** { *; }

# Coil для старого ImageView API
-keep class io.coil-kt.** { *; }

# ==================== DataStore ====================
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# ==================== WebDAV XML парсинг ====================
-keep class org.xmlpull.v1.** { *; }
-dontwarn org.xmlpull.v1.**

# ==================== JSON (org.json) ====================
-keep class org.json.** { *; }
-dontwarn org.json.**

# ==================== Модели данных ====================
-keep class com.rezerv.upload.SyncTask { *; }
-keep class com.rezerv.upload.HistoryRecord { *; }
-keep class com.rezerv.upload.SyncFileDetail { *; }
-keep class com.rezerv.upload.SyncFolderDetail { *; }
-keep class com.rezerv.upload.SyncErrorDetail { *; }
-keep class com.rezerv.upload.WebDavRepository$FileInfo { *; }
-keep class com.rezerv.upload.WebDavRepository$FileMetadata { *; }
-keep class com.rezerv.upload.WebDavRepository$ConnectionResult { *; }
-keep class com.rezerv.upload.WebDavRepository$DownloadResult { *; }
-keep class com.rezerv.upload.WebDavRepository$DownloadResult$* { *; }
-keep class com.rezerv.upload.WebDavResult { *; }
-keep class com.rezerv.upload.WebDavResult$* { *; }

# ==================== WorkManager ====================
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
-keep @androidx.hilt.work.HiltWorker class *

# HiltWorker factory
-keep class androidx.hilt.work.HiltWorkerFactory { *; }

# ==================== UI компоненты ====================
# Сохраняем кастомные View (HistoryChartView, SkeletonView)
-keep class com.rezerv.upload.HistoryChartView { *; }
-keep class com.rezerv.upload.ui.** { *; }

# Material Design компоненты
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# ==================== Enum для UI ====================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ==================== Parcelable ====================
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ==================== Serializable ====================
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ==================== AndroidX Lifecycle ====================
-keep class androidx.lifecycle.** { *; }
-keepclassmembers class * {
    @androidx.lifecycle.OnLifecycleEvent *;
}

# ViewModel
-keep class * extends androidx.lifecycle.ViewModel {
    <init>();
}

# ==================== Navigation Compose ====================
-keep class androidx.navigation.** { *; }
-keep class androidx.hilt.navigation.** { *; }

# ==================== LeakCanary (debug only) ====================
-dontwarn leakcanary.**