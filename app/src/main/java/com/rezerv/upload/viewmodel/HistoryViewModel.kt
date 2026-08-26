package com.rezerv.upload.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.rezerv.upload.HistoryRecord
import com.rezerv.upload.data.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject  // ✅ ВАЖНО: этот импорт обязателен

/**
 * ViewModel для вкладки "История".
 * Отвечает за: отображение записей, live-обновление, очистку.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(  // ✅ @Inject обязателен
    application: Application,
    private val historyRepo: HistoryRepository  // ✅ Внедрённая зависимость
) : AndroidViewModel(application) {

    private val _records = MutableLiveData<List<HistoryRecord>>(emptyList())
    val records: LiveData<List<HistoryRecord>> = _records

    private val _isLiveUpdating = MutableLiveData(false)
    val isLiveUpdating: LiveData<Boolean> = _isLiveUpdating

    private var liveUpdateJob: Job? = null

    // ==================== Загрузка данных ====================

    fun refreshHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            _records.postValue(historyRepo.getRecords(getApplication()))
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            historyRepo.clear(getApplication())
            _records.postValue(historyRepo.getRecords(getApplication()))
        }
    }

    // ==================== Live-обновление ====================

    fun startLiveUpdates() {
        if (_isLiveUpdating.value == true) return
        _isLiveUpdating.postValue(true)

        liveUpdateJob?.cancel()
        liveUpdateJob = viewModelScope.launch {
            while (isActive) {
                refreshHistory()
                delay(1000)
            }
        }
    }

    fun stopLiveUpdates() {
        liveUpdateJob?.cancel()
        liveUpdateJob = null
        _isLiveUpdating.postValue(false)
        refreshHistory()
    }

    // ==================== Утилиты ====================

    fun hasRunningTasks(): Boolean {
        return _records.value?.any { it.status == "running" } ?: false
    }

    override fun onCleared() {
        super.onCleared()
        liveUpdateJob?.cancel()
    }
}