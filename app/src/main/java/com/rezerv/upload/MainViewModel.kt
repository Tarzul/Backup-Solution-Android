package com.rezerv.upload

import com.rezerv.upload.ui.TaskCardBuilder
import com.rezerv.upload.ui.HistoryCardBuilder
import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val MAX_IMAGES_IN_PAGER = 500
        private const val MAX_PARALLEL_UPLOADS = 3
    }

    data class UiState(
        val currentPath: String = "/",
        val files: List<WebDavRepository.FileInfo> = emptyList(),
        val isLoading: Boolean = false,
        val selectedIndices: Set<Int> = emptySet(),
        val selectionMode: Boolean = false,
        val log: String = "",
    )

    sealed class Event {
        data class ShowToast(val message: String) : Event()
        data class SwitchTab(val tab: Int) : Event()
    }

    private val _uiState = MutableLiveData(UiState())
    val uiState: LiveData<UiState> = _uiState
    private val _tasks = MutableLiveData<List<SyncTask>>(emptyList())
    val tasks: LiveData<List<SyncTask>> = _tasks
    private val _history = MutableLiveData<List<HistoryRecord>>(emptyList())
    val history: LiveData<List<HistoryRecord>> = _history
    private val _events = MutableLiveData<Event>()
    val events: LiveData<Event> = _events

    private var rangeBase: Set<Int> = emptySet()
    private var rangeModeAdd = true
    
    // ✅ Оптимизация 3: Ограниченный список изображений
    var pagerImages: List<WebDavRepository.FileInfo> = emptyList()
        private set
    
    // ✅ Оптимизация 1: Семафор для параллельных загрузок
    private val uploadSemaphore = Semaphore(MAX_PARALLEL_UPLOADS)
    
    // ✅ Оптимизация 4: Кольцевой буфер для логов
    private val logBuffer = CircularLogBuffer(maxLines = 200, maxChars = 20000)

    // ==================== Настройки (SecurePrefs) ====================
    fun loadSettings(): Triple<String, String, String> =
        SecurePrefs.loadCredentials(getApplication())

    fun loadAllSettings(): List<String> {
        val (server, user, pass) = SecurePrefs.loadCredentials(getApplication())
        val authType = SecurePrefs.loadAuthType(getApplication())
        return listOf(server, user, pass, authType.toString())
    }

    fun saveSettings(server: String, user: String, pass: String, authType: Int = 0) {
        SecurePrefs.saveCredentials(getApplication(), server, user, pass, authType)
    }

    fun ensureScheduler() {
        AlarmScheduler.scheduleNext(getApplication())
    }

    // ==================== Подключение и навигация ====================
    fun connect(server: String, user: String, pass: String) {
        viewModelScope.launch {
            appendLog("🔌 Подключение к $server")
            val base = WebDavRepository.normalizeBaseUrl(server)
            if (base == null) {
                appendLog("✗ Неверный адрес сервера")
                return@launch
            }
            _uiState.value = _uiState.value?.copy(isLoading = true)
            val result = WebDavRepository.testConnection(base, user, pass)
            if (result.success) {
                appendLog("✓ Соединение установлено (HTTP ${result.code})")
                appendLog("   Возможности: ${result.capabilities}")
                val testPath = WebDavRepository.getServerPath(server)
                _uiState.value = _uiState.value?.copy(currentPath = testPath, isLoading = true)
                browseServer(server, testPath, user, pass)
                _events.value = Event.SwitchTab(1)
            } else {
                appendLog("✗ Ошибка подключения: ${result.error ?: "HTTP ${result.code}"}")
                _uiState.value = _uiState.value?.copy(isLoading = false)
            }
        }
    }

    fun browseServer(server: String, path: String, user: String, pass: String) {
        viewModelScope.launch {
            val base = WebDavRepository.normalizeBaseUrl(server) ?: return@launch
            appendLog("📋 Получение списка: $path")
        
            val files = WebDavRepository.listFiles(base, path, user, pass)
            val sorted = files.sortedWith(
                compareBy<WebDavRepository.FileInfo> { !it.isDirectory }.thenBy { it.name }
            )
        
            _uiState.value = _uiState.value?.copy(
                currentPath = path,
                files = sorted,
                isLoading = false,
                selectionMode = false,
                selectedIndices = emptySet()
            )
        
            appendLog("✓ Элементов: ${sorted.size}")
            CoilPrefetch.prefetch(getApplication(), server, user, pass, sorted)
        }
    }

    fun navigateBack(server: String, user: String, pass: String) {
        val currentPath = _uiState.value?.currentPath ?: "/"
        val root = WebDavRepository.getServerPath(server)
        
        appendLog("⬅ DEBUG Назад: текущий='$currentPath', корень='$root'")
        
        if (currentPath == root || currentPath == "/") {
            _events.value = Event.ShowToast("Уже в корне")
            return
        }
        
        val trimmed = currentPath.trimEnd('/')
        if (trimmed.isEmpty()) {
            appendLog("⬅ Переход в корень: $root")
            browseServer(server, root, user, pass)
            return
        }
        
        val lastSlash = trimmed.lastIndexOf('/')
        val parentPath = if (lastSlash <= 0) root else trimmed.substring(0, lastSlash + 1)
        
        appendLog("⬅ Переход в родительскую: '$parentPath'")
        browseServer(server, parentPath, user, pass)
    }

    // ==================== Выделение ====================
    fun toggleSelection(index: Int) {
        val state = _uiState.value ?: return
        val selected = state.selectedIndices.toMutableSet()
        if (selected.contains(index)) selected.remove(index) else selected.add(index)
        _uiState.value = state.copy(
            selectedIndices = selected,
            selectionMode = selected.isNotEmpty()
        )
        appendLog("Выделение: ${selected.size} элементов")
    }

    fun startSelectionMode(index: Int) {
        _uiState.value = _uiState.value?.copy(
            selectionMode = true,
            selectedIndices = setOf(index)
        )
        appendLog("Режим выделения включён")
    }

    fun exitSelectionMode() {
        _uiState.value = _uiState.value?.copy(
            selectionMode = false,
            selectedIndices = emptySet()
        )
        appendLog("Режим выделения выключен")
    }

    fun beginRangeSelection(anchor: Int, forceAdd: Boolean) {
        val base = _uiState.value?.selectedIndices?.toSet() ?: emptySet()
        rangeBase = base
        rangeModeAdd = if (forceAdd) true else !base.contains(anchor)
    }

    fun selectRange(anchor: Int, current: Int) {
        val lo = minOf(anchor, current)
        val hi = maxOf(anchor, current)
        val sel = rangeBase.toMutableSet()
        if (rangeModeAdd) {
            for (i in lo..hi) sel.add(i)
        } else {
            for (i in lo..hi) sel.remove(i)
        }
        _uiState.value = _uiState.value?.copy(
            selectedIndices = sel,
            selectionMode = sel.isNotEmpty()
        )
    }

    fun getFileAt(position: Int): WebDavRepository.FileInfo? =
        _uiState.value?.files?.getOrNull(position)

    fun getSelectedCount(): Int = _uiState.value?.selectedIndices?.size ?: 0

    fun getImageList(): List<WebDavRepository.FileInfo> =
        _uiState.value?.files?.filter { !it.isDirectory && FileUtils.isImageFile(it.name) } ?: emptyList()

    // ✅ Оптимизация 3: Ограничение размера списка изображений
    fun setPagerImages(images: List<WebDavRepository.FileInfo>) {
        pagerImages = if (images.size > MAX_IMAGES_IN_PAGER) {
            images.take(MAX_IMAGES_IN_PAGER)
        } else {
            images
        }
    }

    // ==================== ПАРАЛЛЕЛЬНАЯ загрузка файлов ====================
    fun uploadFilesToPath(
        server: String, user: String, pass: String,
        uris: List<Uri>, targetPath: String
    ) {
        appendLog("=== uploadFilesToPath (параллельная загрузка) ===")
        appendLog("Сервер: $server")
        appendLog("Папка: $targetPath")
        appendLog("Файлов: ${uris.size} (макс. $MAX_PARALLEL_UPLOADS параллельно)")
        
        if (uris.isEmpty()) {
            _events.value = Event.ShowToast("Нет файлов для загрузки")
            return
        }
        
        viewModelScope.launch {
            val base = WebDavRepository.normalizeBaseUrl(server)
            if (base == null) {
                appendLog("✗ Неверный адрес сервера")
                _events.value = Event.ShowToast("Ошибка: неверный адрес сервера")
                return@launch
            }
            
            val total = uris.size
            val startTime = System.currentTimeMillis()

            HistoryManager.createLiveRecord(getApplication(), startTime, "Ручная загрузка", "user")
            refreshHistory()
            _events.value = Event.SwitchTab(3)

            val ticker = viewModelScope.launch {
                while (isActive) {
                    delay(1000)
                    refreshHistory()
                }
            }

            // ✅ Параллельная загрузка через async + semaphore
            val deferredResults = uris.mapIndexed { index, uri ->
                async(Dispatchers.IO) {
                    uploadSemaphore.withPermit {
                        val meta = WebDavRepository.getFileMetadata(getApplication(), uri)
                        val remotePath = targetPath.trimEnd('/') + "/" + meta.name
                        appendLog("[$index/$total] Загрузка: ${meta.name} (${FileUtils.formatSize(meta.size)})")

                        HistoryManager.updateLiveRecord(getApplication(), startTime, meta.name, index + 1, total)

                        val fileStart = System.currentTimeMillis()
                        try {
                            val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
                            if (inputStream == null) {
                                appendLog("✗ [$index/$total] Не удалось открыть: ${meta.name}")
                                return@withPermit Triple(false, meta.name, 0L to 0L)
                            }
                            
                            val code = inputStream.use { input ->
                                WebDavClient.put(
                                    base + WebDavRepository.encodePath(remotePath),
                                    user, pass, input
                                )
                            }
                            
                            val fileMs = System.currentTimeMillis() - fileStart
                            if (code in 200..299) {
                                appendLog("✓ [$index/$total] ${meta.name} загружен")
                                Triple(true, meta.name, meta.size to fileMs)
                            } else {
                                appendLog("✗ [$index/$total] ${meta.name}: HTTP $code")
                                Triple(false, meta.name, 0L to fileMs)
                            }
                        } catch (e: Exception) {
                            appendLog("✗ [$index/$total] ${meta.name}: ${e.javaClass.simpleName}: ${e.message}")
                            Triple(false, meta.name, 0L to (System.currentTimeMillis() - fileStart))
                        }
                    }
                }
            }

            // Ждём завершения всех загрузок
            val results = deferredResults.awaitAll()

            val fileDetails = mutableListOf<SyncFileDetail>()
            var success = 0
            var failed = 0
            var totalBytes = 0L
            var totalTransferMs = 0L

            results.forEach { (isSuccess, fileName, bytesAndMs) ->
                val (bytes, ms) = bytesAndMs
                if (isSuccess) {
                    success++
                    totalBytes += bytes
                    totalTransferMs += ms
                    fileDetails.add(SyncFileDetail(fileName, bytes, ms, "Пользователь"))
                } else {
                    failed++
                }
            }

            ticker.cancel()
            val durationMs = System.currentTimeMillis() - startTime

            HistoryManager.finalizeRecord(getApplication(), HistoryRecord(
                time = startTime,
                durationMs = durationMs,
                checked = total,
                uploaded = success,
                downloaded = 0,
                deleted = 0,
                errors = failed,
                status = if (failed == 0) "ok" else "error",
                trigger = "user",
                bytesTransferred = totalBytes,
                transferMs = totalTransferMs,
                filesJson = filesToJson(fileDetails),
                taskName = "Ручная загрузка",
                totalFiles = total
            ))

            appendLog("✓ Завершено: $success / $total (ошибок: $failed)")
            _events.value = Event.ShowToast("Загрузка: $success / $total")
            refreshHistory()
            browseServer(server, _uiState.value?.currentPath ?: "/", user, pass)
        }
    }

    // ==================== Операции с файлами ====================
    fun deleteSelected(server: String, user: String, pass: String) {
        viewModelScope.launch {
            val state = _uiState.value ?: return@launch
            val base = WebDavRepository.normalizeBaseUrl(server) ?: return@launch
            val selected = state.selectedIndices.mapNotNull { state.files.getOrNull(it) }
            if (selected.isEmpty()) return@launch
            exitSelectionMode()
            
            var successCount = 0
            var errorCount = 0
            
            selected.forEach { file ->
                val result = WebDavRepository.deleteFile(base, file.path, user, pass)
                when (result) {
                    is WebDavResult.Success -> {
                        appendLog("✓ Удалено: ${file.name}")
                        successCount++
                    }
                    else -> {
                        appendLog("✗ Не удалось удалить: ${file.name} (${result.errorMessage()})")
                        errorCount++
                    }
                }
            }
            
            _events.value = Event.ShowToast("Удалено: $successCount, ошибок: $errorCount")
            browseServer(server, state.currentPath, user, pass)
        }
    }

    fun downloadSelected(server: String, user: String, pass: String) {
        viewModelScope.launch {
            val state = _uiState.value ?: return@launch
            val base = WebDavRepository.normalizeBaseUrl(server) ?: return@launch
            val selected = state.selectedIndices.mapNotNull { state.files.getOrNull(it) }
            if (selected.isEmpty()) return@launch
            exitSelectionMode()
            
            var successCount = 0
            var errorCount = 0
            
            selected.filter { !it.isDirectory }.forEach { file ->
                val result = WebDavRepository.downloadFile(
                    getApplication(), base, file.path, file.name, user, pass
                )
                when (result) {
                    is WebDavRepository.DownloadResult.Success -> {
                        appendLog("✓ Скачано: ${file.name} (${FileUtils.formatSize(result.bytesDownloaded)})")
                        successCount++
                    }
                    is WebDavRepository.DownloadResult.HttpError -> {
                        appendLog("✗ ${file.name}: HTTP ${result.code}")
                        errorCount++
                    }
                    is WebDavRepository.DownloadResult.IoError -> {
                        appendLog("✗ ${file.name}: ${result.message}")
                        errorCount++
                    }
                }
            }
            
            _events.value = Event.ShowToast("Скачано: $successCount, ошибок: $errorCount")
        }
    }

    fun downloadFile(server: String, path: String, fileName: String, user: String, pass: String) {
        viewModelScope.launch {
            val base = WebDavRepository.normalizeBaseUrl(server) ?: return@launch
            appendLog("⬇ Скачивание: $fileName")
        
            val result = WebDavRepository.downloadFile(
                getApplication(), base, path, fileName, user, pass
            )
        
            when (result) {
                is WebDavRepository.DownloadResult.Success -> {
                    appendLog("✓ Скачано: $fileName (${FileUtils.formatSize(result.bytesDownloaded)})")
                    _events.value = Event.ShowToast("Скачано: $fileName")
                }
                is WebDavRepository.DownloadResult.HttpError -> {
                    appendLog("✗ $fileName: HTTP ${result.code}")
                    _events.value = Event.ShowToast("Ошибка HTTP ${result.code}")
                }
                is WebDavRepository.DownloadResult.IoError -> {
                    appendLog("✗ $fileName: ${result.message}")
                    _events.value = Event.ShowToast("Ошибка: ${result.message}")
                }
            }
        }
    }

    fun viewVideo(context: Context, item: WebDavRepository.FileInfo) {
        viewModelScope.launch {
            _events.value = Event.ShowToast("Скачивание видео: ${item.name}")
            val file = withContext(Dispatchers.IO) {
                try {
                    val (s, u, p) = SecurePrefs.loadCredentials(getApplication())
                    val base = WebDavRepository.normalizeBaseUrl(s) ?: return@withContext null
                    val resp = WebDavClient.get(base + WebDavRepository.encodePath(item.path), u, p)
                    if (resp.code !in 200..299) { resp.close(); return@withContext null }
                    val dir = java.io.File(getApplication<Application>().cacheDir, "videos").apply { mkdirs() }
                    val f = java.io.File(dir, item.name)
                    f.outputStream().use { out -> resp.body?.byteStream()?.use { it.copyTo(out) } }
                    resp.close()
                    f
                } catch (e: Exception) { null }
            }
            if (file != null) {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    getApplication(), getApplication<Application>().packageName + ".fileprovider", file)
                try {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW)
                        .setDataAndType(uri, "video/*")
                        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION))
                } catch (e: Exception) {
                    _events.value = Event.ShowToast("Нет приложения для просмотра видео")
                }
            } else _events.value = Event.ShowToast("Не удалось скачать видео")
        }
    }

    fun createFolder(server: String, folderName: String, user: String, pass: String) {
        viewModelScope.launch {
            val state = _uiState.value ?: return@launch
            val base = WebDavRepository.normalizeBaseUrl(server) ?: return@launch
            val newPath = (if (state.currentPath.endsWith("/")) state.currentPath
            else "${state.currentPath}/") + folderName + "/"
        
            val result = WebDavRepository.createFolder(base, newPath, user, pass)
        
            when (result) {
                is WebDavResult.Success -> {
                    appendLog("✓ Папка создана: $folderName")
                    _events.value = Event.ShowToast("Папка создана")
                    browseServer(server, state.currentPath, user, pass)
                }
                else -> {
                    appendLog("✗ Ошибка создания папки: ${result.errorMessage()}")
                    _events.value = Event.ShowToast("Ошибка: ${result.errorMessage()}")
                }   
            }
        }
    }

    // ==================== Задания и история ====================
    fun refreshTasks() {
        viewModelScope.launch(Dispatchers.IO) {
            _tasks.postValue(TaskManager.load(getApplication()))
        }
    }

    fun refreshHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            _history.postValue(HistoryManager.getRecords(getApplication()))
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            HistoryManager.clear(getApplication())
            _history.postValue(HistoryManager.getRecords(getApplication()))
        }
    }

    fun runTaskNow(t: SyncTask) {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            
            if (!HistoryManager.createLiveRecord(getApplication(), startTime, t.name, "user", t.id)) {
                _events.value = Event.ShowToast("Задание уже выполняется")
                return@launch
            }
            
            refreshHistory()
            _events.value = Event.SwitchTab(3)
            _events.value = Event.ShowToast("Запуск синхронизации...")
            
            val ticker = viewModelScope.launch {
                while (isActive) {
                    delay(1000)
                    refreshHistory()
                }
            }
            
            val result = withContext(Dispatchers.IO) {
                SyncEngine.runTask(
                    getApplication(), t, trigger = "user",
                    startTime = startTime,
                    onProgress = { m -> appendLog(m) },
                    onLiveUpdate = { name, idx, total ->
                        HistoryManager.updateLiveRecord(getApplication(), startTime, name, idx, total)
                    }
                )
            }
            
            ticker.cancel()
            val updated = t.copy(
                lastRun = System.currentTimeMillis(),
                lastStatus = if (result.errors == 0) "ok" else "error"
            )
            withContext(Dispatchers.IO) {
                TaskManager.upsert(getApplication(), updated)
            }
            AlarmScheduler.scheduleNext(getApplication())
            refreshTasks()
            refreshHistory()
        }
    }

    fun deleteTask(t: SyncTask) {
        viewModelScope.launch(Dispatchers.IO) {
            TaskManager.delete(getApplication(), t.id)
            AlarmScheduler.scheduleNext(getApplication())
            _tasks.postValue(TaskManager.load(getApplication()))
        }
    }

    fun log(message: String) { appendLog(message) }

    // ✅ Оптимизация 4: Кольцевой буфер для логов
    private fun appendLog(message: String) {
        logBuffer.add(message)
        _uiState.postValue(_uiState.value?.copy(log = logBuffer.getText()) ?: UiState(log = logBuffer.getText()))
    }

    // ✅ Оптимизация 3: Очистка памяти при уничтожении ViewModel
    override fun onCleared() {
        super.onCleared()
        pagerImages = emptyList()
        logBuffer.clear()
    }

    private fun filesToJson(list: List<SyncFileDetail>): String {
        val arr = org.json.JSONArray()
        for (f in list) arr.put(org.json.JSONObject().apply {
            put("n", f.name)
            put("s", f.size)
            put("m", f.ms)
            put("d", f.side)
        })
        return arr.toString()
    }
}