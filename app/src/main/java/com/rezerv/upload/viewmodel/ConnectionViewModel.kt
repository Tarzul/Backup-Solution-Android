package com.rezerv.upload.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.rezerv.upload.WebDavRepository
import com.rezerv.upload.data.SettingsRepository
import com.rezerv.upload.data.SyncScheduler
import com.rezerv.upload.data.WebDavService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel для вкладки "Подключение".
 * Отвечает за: настройки сервера, тестирование соединения.
 */
@HiltViewModel
class ConnectionViewModel @Inject constructor(
    application: Application,
    private val settingsRepo: SettingsRepository,   // ✅ Внедрение
    private val webDavService: WebDavService,       // ✅ Внедрение
    private val syncScheduler: SyncScheduler        // ✅ Внедрение
) : AndroidViewModel(application) {

    data class ConnectionState(
        val isConnected: Boolean = false,
        val isLoading: Boolean = false,
        val serverPath: String = "/",
        val capabilities: String = "",
        val error: String? = null
    )

    sealed class ConnectionEvent {
        data class Connected(val serverPath: String) : ConnectionEvent()
        data class ConnectionFailed(val error: String) : ConnectionEvent()
    }

    private val _state = MutableLiveData(ConnectionState())
    val state: LiveData<ConnectionState> = _state

    private val _events = MutableLiveData<ConnectionEvent>()
    val events: LiveData<ConnectionEvent> = _events

    private val _log = MutableLiveData("")
    val log: LiveData<String> = _log

    // ==================== Настройки ====================

    fun loadSettings(): Triple<String, String, String> =
        settingsRepo.loadCredentials(getApplication())

    fun loadAllSettings(): List<String> {
        val (server, user, pass) = settingsRepo.loadCredentials(getApplication())
        val authType = settingsRepo.loadAuthType(getApplication())
        return listOf(server, user, pass, authType.toString())
    }

    fun saveSettings(server: String, user: String, pass: String, authType: Int = 0) {
        settingsRepo.saveCredentials(getApplication(), server, user, pass, authType)
    }

    fun ensureScheduler() {
        viewModelScope.launch { syncScheduler.ensureScheduler(getApplication()) }
    }

    // ==================== Подключение ====================

    fun connect(server: String, user: String, pass: String) {
        viewModelScope.launch {
            appendLog("🔌 Подключение к $server")
            // ✅ Утилитарный метод — вызываем напрямую из object
            val base = WebDavRepository.normalizeBaseUrl(server)
            if (base == null) {
                appendLog("✗ Неверный адрес сервера")
                return@launch
            }

            _state.value = _state.value?.copy(isLoading = true, error = null)
            // ✅ Внедрённый сервис
            val result = webDavService.testConnection(base, user, pass)

            if (result.success) {
                appendLog("✓ Соединение установлено (HTTP ${result.code})")
                appendLog("   Возможности: ${result.capabilities}")
                val serverPath = WebDavRepository.getServerPath(server)

                _state.value = _state.value?.copy(
                    isConnected = true,
                    isLoading = false,
                    serverPath = serverPath,
                    capabilities = result.capabilities
                )
                _events.value = ConnectionEvent.Connected(serverPath)
            } else {
                val errorMsg = result.error ?: "HTTP ${result.code}"
                appendLog("✗ Ошибка подключения: $errorMsg")
                _state.value = _state.value?.copy(
                    isConnected = false,
                    isLoading = false,
                    error = errorMsg
                )
                _events.value = ConnectionEvent.ConnectionFailed(errorMsg)
            }
        }
    }

    fun isConnected(): Boolean = _state.value?.isConnected ?: false
    fun getServerPath(): String = _state.value?.serverPath ?: "/"

    // ==================== Логирование ====================

    fun log(message: String) = appendLog(message)

    private fun appendLog(message: String) {
        val current = _log.value ?: ""
        val newLog = current + message + "\n"
        _log.value = if (newLog.length > 20000) newLog.takeLast(20000) else newLog
    }
}