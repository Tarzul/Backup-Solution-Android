package com.rezerv.upload.di

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
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt модуль для связывания интерфейсов с реализациями.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

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