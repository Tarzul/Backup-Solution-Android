package com.rezerv.upload.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.rezerv.upload.SyncTask
import com.rezerv.upload.TaskWorker
import com.rezerv.upload.data.HistoryRepository
import com.rezerv.upload.data.SyncScheduler
import com.rezerv.upload.data.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

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

    val tasks: StateFlow<List<SyncTask>?> = taskRepo.getAllTasks()
        .map<List<SyncTask>, List<SyncTask>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _events = MutableLiveData<TaskEvent>()
    val events: LiveData<TaskEvent> = _events

    private val _log = MutableLiveData("")
    val log: LiveData<String> = _log

    fun deleteTask(task: SyncTask) {
        viewModelScope.launch(Dispatchers.IO) {
            syncScheduler.cancelForTask(getApplication(), task)
            taskRepo.deleteTask(task.id)
            syncScheduler.scheduleNext(getApplication())
        }
    }

    fun ensureScheduler() {
        viewModelScope.launch { syncScheduler.ensureScheduler(getApplication()) }  // ✅ suspend теперь
    }

    fun runTaskNow(task: SyncTask) {
        viewModelScope.launch {
            val records = historyRepo.getRecords(getApplication())
            if (records.any { it.status == "running" && it.taskId == task.id }) {
                _events.value = TaskEvent.ShowToast("Задание уже выполняется")
                return@launch
            }
            val request = androidx.work.OneTimeWorkRequestBuilder<TaskWorker>()
                .setInputData(androidx.work.workDataOf("taskId" to task.id))
                .addTag("sync_task")
                .addTag("task_${task.id}")
                .build()
            androidx.work.WorkManager.getInstance(getApplication()).enqueue(request)

            _events.value = TaskEvent.TaskStarted(task.name)
            appendLog("▶ Запуск: ${task.name} (WorkManager)")
        }
    }

    fun log(message: String) = appendLog(message)

    private fun appendLog(message: String) {
        val current = _log.value ?: ""
        val newLog = current + message + "\n"
        _log.postValue(if (newLog.length > 20000) newLog.takeLast(20000) else newLog)
    }
}