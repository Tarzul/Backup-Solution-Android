package com.rezerv.upload.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.rezerv.upload.SyncTask
import com.rezerv.upload.data.HistoryRepository
import com.rezerv.upload.data.SyncScheduler
import com.rezerv.upload.data.TaskRepository
import com.rezerv.upload.domain.usecase.RunSyncTaskUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * ✅ ИСПРАВЛЕНО:
 * - ViewModel теперь тонкий - только координирует UI
 * - Бизнес-логика вынесена в RunSyncTaskUseCase
 * - Правильная инициализация tasks без null
 * - Удалён бесконечный ticker loop
 */
@HiltViewModel
class TasksViewModel @Inject constructor(
    application: Application,
    private val taskRepo: TaskRepository,
    private val historyRepo: HistoryRepository,
    private val syncScheduler: SyncScheduler,
    private val runSyncTask: RunSyncTaskUseCase  // ✅ Внедряем UseCase
) : AndroidViewModel(application) {

    sealed class TaskEvent {
        data class ShowToast(val message: String) : TaskEvent()
        data class TaskStarted(val taskName: String) : TaskEvent()
        data class TaskCompleted(val taskName: String, val errors: Int) : TaskEvent()
    }

    // ✅ ИСПРАВЛЕНО: Правильная инициализация без null
    val tasks: StateFlow<List<SyncTask>> = taskRepo.getAllTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _events = MutableLiveData<TaskEvent>()
    val events: LiveData<TaskEvent> = _events

    private val _log = MutableLiveData("")
    val log: LiveData<String> = _log

    private val _isRunning = MutableLiveData(false)
    val isRunning: LiveData<Boolean> = _isRunning

    fun deleteTask(task: SyncTask) {
        viewModelScope.launch(Dispatchers.IO) {
            syncScheduler.cancelForTask(getApplication(), task)
            taskRepo.deleteTask(task.id)
            syncScheduler.scheduleNext(getApplication())
        }
    }

    fun ensureScheduler() {
        viewModelScope.launch { syncScheduler.ensureScheduler(getApplication()) }
    }

    /**
     * ✅ ИСПРАВЛЕНО:
     * - Использует RunSyncTaskUseCase вместо прямой работы с SyncEngine
     * - Удалён бесконечный ticker loop
     * - Правильная обработка разных типов результатов
     * - Проверка состояния isRunning перед запуском
     */
    fun runTaskNow(task: SyncTask) {
        if (_isRunning.value == true) {
            _events.value = TaskEvent.ShowToast("Задача уже выполняется")
            return
        }

        _isRunning.value = true
        _events.value = TaskEvent.TaskStarted(task.name)
        appendLog("▶ Запуск: ${task.name}")

        viewModelScope.launch {
            when (val result = runSyncTask(task, trigger = "user")) {
                is RunSyncTaskUseCase.Result.Success -> {
                    _events.value = TaskEvent.TaskCompleted(result.taskName, result.errors)
                    appendLog("■ Завершено: ${result.taskName} (ошибок: ${result.errors})")
                }
                is RunSyncTaskUseCase.Result.AlreadyRunning -> {
                    _events.value = TaskEvent.ShowToast("Задание уже выполняется")
                    appendLog("⚠ Задание уже выполняется")
                }
                is RunSyncTaskUseCase.Result.Error -> {
                    _events.value = TaskEvent.ShowToast("Ошибка: ${result.message}")
                    appendLog("✗ Ошибка: ${result.message}")
                }
            }

            _isRunning.value = false
            syncScheduler.scheduleNext(getApplication())
        }
    }

    fun log(message: String) = appendLog(message)

    private fun appendLog(message: String) {
        val current = _log.value ?: ""
        val newLog = current + message + "\n"
        _log.value = if (newLog.length > 20000) newLog.takeLast(20000) else newLog
    }
}