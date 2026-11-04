package com.rezerv.upload.domain.usecase

import android.app.Application
import com.rezerv.upload.HistoryManager
import com.rezerv.upload.SyncEngine
import com.rezerv.upload.SyncTask
import com.rezerv.upload.data.HistoryRepository
import com.rezerv.upload.data.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * ✅ НОВЫЙ ФАЙЛ: UseCase для запуска задачи синхронизации.
 *
 * Выносит бизнес-логику из ViewModel в Domain layer.
 * Следует принципам Clean Architecture.
 *
 * Ответственности:
 * - Проверка, не выполняется ли уже задача
 * - Запуск синхронизации через SyncEngine
 * - Обновление задачи в репозитории
 * - Возврат структурированного результата
 */
class RunSyncTaskUseCase @Inject constructor(
    private val application: Application,
    private val taskRepository: TaskRepository,
    private val historyRepository: HistoryRepository
) {

    /**
     * Структурированный результат выполнения задачи.
     * Позволяет UI обрабатывать разные сценарии.
     */
    sealed class Result {
        data class Success(val taskName: String, val errors: Int) : Result()
        data class AlreadyRunning(val taskName: String) : Result()
        data class Error(val message: String) : Result()
    }

    /**
     * Запускает задачу синхронизации.
     *
     * @param task Задача для выполнения
     * @param trigger Источник запуска ("user" или "schedule")
     * @return Результат выполнения
     */
    suspend operator fun invoke(task: SyncTask, trigger: String = "user"): Result {
        val startTime = System.currentTimeMillis()

        // Проверяем, не выполняется ли уже задача
        val canStart = withContext(Dispatchers.IO) {
            HistoryManager.createLiveRecord(
                application, startTime, task.name, trigger, task.id
            )
        }

        if (!canStart) {
            return Result.AlreadyRunning(task.name)
        }

        // Запускаем синхронизацию
        return try {
            val syncResult = withContext(Dispatchers.IO) {
                SyncEngine.runTask(
                    context = application,
                    task = task,
                    trigger = trigger,
                    startTime = startTime,
                    onProgress = { message ->
                        // Логирование можно добавить здесь при необходимости
                    },
                    onLiveUpdate = { name, idx, total ->
                        HistoryManager.updateLiveRecord(application, startTime, name, idx, total)
                    }
                )
            }

            // Обновляем задачу
            val updatedTask = task.copy(
                lastRun = System.currentTimeMillis(),
                lastStatus = if (syncResult.errors == 0) "ok" else "error"
            )

            withContext(Dispatchers.IO) {
                taskRepository.saveTask(updatedTask)
            }

            Result.Success(task.name, syncResult.errors)

        } catch (e: Exception) {
            Result.Error(e.message ?: "Неизвестная ошибка")
        }
    }
}