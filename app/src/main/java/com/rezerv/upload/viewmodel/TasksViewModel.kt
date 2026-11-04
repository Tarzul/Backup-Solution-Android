package com.rezerv.upload.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.rezerv.upload.SyncEngine
import com.rezerv.upload.SyncTask
import com.rezerv.upload.data.HistoryRepository
import com.rezerv.upload.data.SyncScheduler
import com.rezerv.upload.data.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * ✅ ИСПРАВЛЕНО:
 * - Удалён бесконечный ticker loop
 * - Правильная инициализация tasks без null (emptyList())
 * - Добавлена проверка isRunning перед запуском
 * - Упрощена логика runTaskNow
 */
@HiltViewModel
class TasksViewModel @Inject constructor(
    application: Application,
    private val taskRepo: TaskRepository,
    private val historyRepo: HistoryRepository,
    private val syncScheduler: SyncScheduler
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
     * - Проверка isRunning перед запуском
     * - Удалён бесконечный ticker loop
     * - Упрощена логика без ticker.cancel()
     */
    fun runTaskNow(task: SyncTask) {
        // ✅ Проверка, не выполняется ли уже задача
        if (_isRunning.value == true) {
            _events.value = TaskEvent.ShowToast("Задача уже выполняется")
            return
        }

        _isRunning.value = true
        _events.value = TaskEvent.TaskStarted(task.name)
        appendLog("▶ Запуск: ${task.name}")

        viewModelScope.launch {
            try {
                val startTime = System.currentTimeMillis()

                if (!historyRepo.createLiveRecord(
                        getApplication(), startTime, task.name, "user", task.id)) {
                    _events.value = TaskEvent.ShowToast("Задание уже выполняется")
                    appendLog("⚠ Задание уже выполняется")
                    return@launch
                }

                val result = withContext(Dispatchers.IO) {
                    SyncEngine.runTask(
                        getApplication(), task, trigger = "user",
                        startTime = startTime,
                        onProgress = { m -> appendLog(m) },
                        onLiveUpdate = { name, idx, total ->
                            historyRepo.updateLiveRecord(getApplication(), startTime, name, idx, total)
                        }
                    )
                }

                val updated = task.copy(
                    lastRun = System.currentTimeMillis(),
                    lastStatus = if (result.errors == 0) "ok" else "error"
                )
                withContext(Dispatchers.IO) {
                    taskRepo.saveTask(updated)
                }
                syncScheduler.scheduleNext(getApplication())

                _events.value = TaskEvent.TaskCompleted(task.name, result.errors)
                appendLog("■ Завершено: ${task.name} (ошибок: ${result.errors})")

            } catch (e: Exception) {
                _events.value = TaskEvent.ShowToast("Ошибка: ${e.message}")
                appendLog("✗ Ошибка: ${e.message}")
            } finally {
                _isRunning.value = false
            }
        }
    }

    fun log(message: String) = appendLog(message)

    private fun appendLog(message: String) {
        val current = _log.value ?: ""
        val newLog = current + message + "\n"
        _log.value = if (newLog.length > 20000) newLog.takeLast(20000) else newLog
    }
}