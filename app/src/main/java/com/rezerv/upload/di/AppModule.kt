package com.rezerv.upload.di

import android.content.Context
import androidx.room.Room
import com.rezerv.upload.data.HistoryRepository
import com.rezerv.upload.data.SettingsRepository
import com.rezerv.upload.data.SyncScheduler
import com.rezerv.upload.data.TaskRepository
import com.rezerv.upload.data.WebDavService
import com.rezerv.upload.data.impl.AlarmSchedulerImpl
import com.rezerv.upload.data.impl.HistoryRepositoryImpl
import com.rezerv.upload.data.impl.SettingsRepositoryImpl
import com.rezerv.upload.data.impl.TaskRepositoryImpl
import com.rezerv.upload.data.impl.WebDavServiceImpl
import com.rezerv.upload.data.local.AppDatabase
import com.rezerv.upload.data.local.TaskDao
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ✅ Room Database
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "rezerv_database"
        )
        .fallbackToDestructiveMigration() // Удаляет старые данные при обновлении схемы
        .build()
    }

    @Provides
    fun provideTaskDao(db: AppDatabase): TaskDao = db.taskDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModuleBindings {

    @Binds
    @Singleton
    abstract fun bindTaskRepository(impl: TaskRepositoryImpl): TaskRepository

    @Binds
    @Singleton
    abstract fun bindHistoryRepository(impl: HistoryRepositoryImpl): HistoryRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindWebDavService(impl: WebDavServiceImpl): WebDavService

    @Binds
    @Singleton
    abstract fun bindSyncScheduler(impl: AlarmSchedulerImpl): SyncScheduler
}