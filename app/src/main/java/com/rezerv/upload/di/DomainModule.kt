package com.rezerv.upload.di

import android.app.Application
import com.rezerv.upload.data.TaskRepository
import com.rezerv.upload.data.HistoryRepository
import com.rezerv.upload.domain.usecase.RunSyncTaskUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * ✅ НОВЫЙ ФАЙЛ: Hilt модуль для предоставления UseCase'ов.
 *
 * Регистрирует RunSyncTaskUseCase в DI контейнере.
 */
@Module
@InstallIn(SingletonComponent::class)
object DomainModule {

    @Provides
    fun provideRunSyncTaskUseCase(
        application: Application,
        taskRepository: TaskRepository,
        historyRepository: HistoryRepository
    ): RunSyncTaskUseCase {
        return RunSyncTaskUseCase(application, taskRepository, historyRepository)
    }
}