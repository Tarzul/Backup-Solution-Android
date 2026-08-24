package com.rezerv.upload.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.rezerv.upload.CoilPrefetch
import com.rezerv.upload.FileUtils
import com.rezerv.upload.HistoryRecord
import com.rezerv.upload.SecurePrefs
import com.rezerv.upload.SyncFileDetail
import com.rezerv.upload.WebDavClient
import com.rezerv.upload.WebDavRepository
import com.rezerv.upload.WebDavResult
import com.rezerv.upload.data.HistoryRepository
import com.rezerv.upload.data.SettingsRepository
import com.rezerv.upload.data.WebDavService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * ViewModel для вкладки "Браузер".
 */
@HiltViewModel
class BrowserViewModel @Inject constructor(
    application: Application,
    private val webDavService: WebDavService,       // ✅ Внедрение
    private val historyRepo: HistoryRepository,     // ✅ Внедрение
    private val settingsRepo: SettingsRepository    // ✅ Внедрение
) : AndroidViewModel(application) {

    companion object {
        private const val MAX_PARALLEL_UPLOADS = 3
        private const val MAX_IMAGES_IN_PAGER = 500
    }

    data class BrowserState(
        val currentPath: String = "/",
        val files: List<WebDavRepository.FileInfo> = emptyList(),
        val isLoading: Boolean = false,
        val selectedIndices: Set<Int> = emptySet(),
        val selectionMode: Boolean = false
    )

    sealed class BrowserEvent {
        data class ShowToast(val message: String) : BrowserEvent()
        data class OpenImagePager(val startIndex: Int) : BrowserEvent()
        data class UploadCompleted(val success: Int, val total: Int) : BrowserEvent()
    }

    private val _state = MutableLiveData(BrowserState())
    val state: LiveData<BrowserState> = _state

    private val _events = MutableLiveData<BrowserEvent>()
    val events: LiveData<BrowserEvent> = _events

    private val _log = MutableLiveData("")
    val log: LiveData<String> = _log

    private var rangeBase: Set<Int> = emptySet()
    private var rangeModeAdd = true
    private val uploadSemaphore = Semaphore(MAX_PARALLEL_UPLOADS)

    var pagerImages: List<WebDavRepository.FileInfo> = emptyList()
        private set

    // ==================== Навигация ====================

    fun browseServer(server: String, path: String, user: String, pass: String) {
        viewModelScope.launch {
            val base = WebDavRepository.normalizeBaseUrl(server) ?: return@launch
            appendLog("📋 Получение списка: $path")
            _state.value = _state.value?.copy(isLoading = true)

            val files = webDavService.listFiles(base, path, user, pass)
            val sorted = files.sortedWith(
                compareBy<WebDavRepository.FileInfo> { !it.isDirectory }.thenBy { it.name }
            )

            _state.value = _state.value?.copy(
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
        val currentPath = _state.value?.currentPath ?: "/"
        val root = WebDavRepository.getServerPath(server)

        if (currentPath == root || currentPath == "/") {
            _events.value = BrowserEvent.ShowToast("Уже в корне")
            return
        }

        val trimmed = currentPath.trimEnd('/')
        if (trimmed.isEmpty()) {
            browseServer(server, root, user, pass)
            return
        }

        val lastSlash = trimmed.lastIndexOf('/')
        val parentPath = if (lastSlash <= 0) root else trimmed.substring(0, lastSlash + 1)
        browseServer(server, parentPath, user, pass)
    }

    // ==================== Выделение ====================

    fun toggleSelection(index: Int) {
        val state = _state.value ?: return
        val selected = state.selectedIndices.toMutableSet()
        if (selected.contains(index)) selected.remove(index) else selected.add(index)
        _state.value = state.copy(
            selectedIndices = selected,
            selectionMode = selected.isNotEmpty()
        )
    }

    fun startSelectionMode(index: Int) {
        _state.value = _state.value?.copy(
            selectionMode = true,
            selectedIndices = setOf(index)
        )
    }

    fun exitSelectionMode() {
        _state.value = _state.value?.copy(
            selectionMode = false,
            selectedIndices = emptySet()
        )
    }

    fun beginRangeSelection(anchor: Int, forceAdd: Boolean) {
        val base = _state.value?.selectedIndices?.toSet() ?: emptySet()
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
        _state.value = _state.value?.copy(
            selectedIndices = sel,
            selectionMode = sel.isNotEmpty()
        )
    }

    fun getSelectedCount(): Int = _state.value?.selectedIndices?.size ?: 0

    fun getFileAt(position: Int): WebDavRepository.FileInfo? =
        _state.value?.files?.getOrNull(position)

    fun getImageList(): List<WebDavRepository.FileInfo> =
        _state.value?.files?.filter { !it.isDirectory && FileUtils.isImageFile(it.name) } ?: emptyList()

    fun setPagerImages(images: List<WebDavRepository.FileInfo>) {
        pagerImages = if (images.size > MAX_IMAGES_IN_PAGER) {
            images.take(MAX_IMAGES_IN_PAGER)
        } else {
            images
        }
    }

    fun openImagePager(item: WebDavRepository.FileInfo) {
        val images = getImageList()
        val index = images.indexOfFirst { it.path == item.path }
        if (index >= 0) {
            setPagerImages(images)
            _events.value = BrowserEvent.OpenImagePager(index)
        }
    }

    // ==================== Операции с файлами ====================

    fun deleteSelected(server: String, user: String, pass: String) {
        viewModelScope.launch {
            val state = _state.value ?: return@launch
            val base = WebDavRepository.normalizeBaseUrl(server) ?: return@launch
            val selected = state.selectedIndices.mapNotNull { state.files.getOrNull(it) }
            if (selected.isEmpty()) return@launch
            exitSelectionMode()

            var successCount = 0
            var errorCount = 0

            selected.forEach { file ->
                val result = webDavService.deleteFile(base, file.path, user, pass)
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

            _events.value = BrowserEvent.ShowToast("Удалено: $successCount, ошибок: $errorCount")
            browseServer(server, state.currentPath, user, pass)
        }
    }

    fun downloadSelected(server: String, user: String, pass: String) {
        viewModelScope.launch {
            val state = _state.value ?: return@launch
            val base = WebDavRepository.normalizeBaseUrl(server) ?: return@launch
            val selected = state.selectedIndices.mapNotNull { state.files.getOrNull(it) }
            if (selected.isEmpty()) return@launch
            exitSelectionMode()

            var successCount = 0
            var errorCount = 0

            selected.filter { !it.isDirectory }.forEach { file ->
                val result = webDavService.downloadFile(
                    getApplication(), base, file.path, file.name, user, pass
                )
                when (result) {
                    is WebDavRepository.DownloadResult.Success -> {
                        appendLog("✓ Скачано: ${file.name}")
                        successCount++
                    }
                    else -> {
                        appendLog("✗ ${file.name}: ошибка")
                        errorCount++
                    }
                }
            }

            _events.value = BrowserEvent.ShowToast("Скачано: $successCount, ошибок: $errorCount")
        }
    }

    fun downloadFile(server: String, path: String, fileName: String, user: String, pass: String) {
        viewModelScope.launch {
            val base = WebDavRepository.normalizeBaseUrl(server) ?: return@launch
            appendLog("⬇ Скачивание: $fileName")

            val result = webDavService.downloadFile(
                getApplication(), base, path, fileName, user, pass
            )

            when (result) {
                is WebDavRepository.DownloadResult.Success -> {
                    appendLog("✓ Скачано: $fileName")
                    _events.value = BrowserEvent.ShowToast("Скачано: $fileName")
                }
                is WebDavRepository.DownloadResult.HttpError -> {
                    appendLog("✗ $fileName: HTTP ${result.code}")
                    _events.value = BrowserEvent.ShowToast("Ошибка HTTP ${result.code}")
                }
                is WebDavRepository.DownloadResult.IoError -> {
                    appendLog("✗ $fileName: ${result.message}")
                    _events.value = BrowserEvent.ShowToast("Ошибка: ${result.message}")
                }
            }
        }
    }

    fun createFolder(server: String, folderName: String, user: String, pass: String) {
        viewModelScope.launch {
            val state = _state.value ?: return@launch
            val base = WebDavRepository.normalizeBaseUrl(server) ?: return@launch
            val newPath = (if (state.currentPath.endsWith("/")) state.currentPath
            else "${state.currentPath}/") + folderName + "/"

            val result = webDavService.createFolder(base, newPath, user, pass)

            when (result) {
                is WebDavResult.Success -> {
                    appendLog("✓ Папка создана: $folderName")
                    _events.value = BrowserEvent.ShowToast("Папка создана")
                    browseServer(server, state.currentPath, user, pass)
                }
                else -> {
                    appendLog("✗ Ошибка создания папки: ${result.errorMessage()}")
                    _events.value = BrowserEvent.ShowToast("Ошибка: ${result.errorMessage()}")
                }
            }
        }
    }

    // ==================== Загрузка файлов (параллельная) ====================

    fun uploadFilesToPath(
        server: String, user: String, pass: String,
        uris: List<Uri>, targetPath: String
    ) {
        appendLog("=== Загрузка ${uris.size} файлов (параллельно: $MAX_PARALLEL_UPLOADS) ===")

        if (uris.isEmpty()) {
            _events.value = BrowserEvent.ShowToast("Нет файлов для загрузки")
            return
        }

        viewModelScope.launch {
            val base = WebDavRepository.normalizeBaseUrl(server)
            if (base == null) {
                appendLog("✗ Неверный адрес сервера")
                _events.value = BrowserEvent.ShowToast("Ошибка: неверный адрес сервера")
                return@launch
            }

            val total = uris.size
            val startTime = System.currentTimeMillis()

            historyRepo.createLiveRecord(getApplication(), startTime, "Ручная загрузка", "user")

            val deferredResults = uris.mapIndexed { index, uri ->
                async(Dispatchers.IO) {
                    uploadSemaphore.withPermit {
                        val meta = webDavService.getFileMetadata(getApplication(), uri)
                        val remotePath = targetPath.trimEnd('/') + "/" + meta.name
                        appendLog("[$index/$total] Загрузка: ${meta.name}")

                        historyRepo.updateLiveRecord(getApplication(), startTime, meta.name, index + 1, total)

                        val fileStart = System.currentTimeMillis()
                        try {
                            val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
                            if (inputStream == null) {
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
                                appendLog("✓ [$index/$total] ${meta.name}")
                                Triple(true, meta.name, meta.size to fileMs)
                            } else {
                                appendLog("✗ [$index/$total] ${meta.name}: HTTP $code")
                                Triple(false, meta.name, 0L to fileMs)
                            }
                        } catch (e: Exception) {
                            appendLog("✗ [$index/$total] ${meta.name}: ${e.message}")
                            Triple(false, meta.name, 0L to (System.currentTimeMillis() - fileStart))
                        }
                    }
                }
            }

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

            val durationMs = System.currentTimeMillis() - startTime

            historyRepo.finalizeRecord(getApplication(), HistoryRecord(
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

            appendLog("✓ Завершено: $success / $total")
            _events.value = BrowserEvent.UploadCompleted(success, total)
            browseServer(server, _state.value?.currentPath ?: "/", user, pass)
        }
    }

    fun viewVideo(context: Context, item: WebDavRepository.FileInfo) {
        viewModelScope.launch {
            _events.value = BrowserEvent.ShowToast("Скачивание видео: ${item.name}")
            val file = withContext(Dispatchers.IO) {
                try {
                    val (s, u, p) = settingsRepo.loadCredentials(getApplication())
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
                    _events.value = BrowserEvent.ShowToast("Нет приложения для просмотра видео")
                }
            } else _events.value = BrowserEvent.ShowToast("Не удалось скачать видео")
        }
    }

    // ==================== Утилиты ====================

    fun log(message: String) = appendLog(message)

    private fun appendLog(message: String) {
        val current = _log.value ?: ""
        val newLog = current + message + "\n"
        _log.value = if (newLog.length > 20000) newLog.takeLast(20000) else newLog
    }

    private fun filesToJson(list: List<SyncFileDetail>): String {
        val arr = org.json.JSONArray()
        for (f in list) arr.put(org.json.JSONObject().apply {
            put("n", f.name); put("s", f.size); put("m", f.ms); put("d", f.side)
        })
        return arr.toString()
    }

    override fun onCleared() {
        super.onCleared()
        pagerImages = emptyList()
    }
}