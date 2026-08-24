package com.rezerv.upload

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay     
import kotlinx.coroutines.isActive  
import kotlinx.coroutines.launch
class MainViewModel(application: Application) : AndroidViewModel(application) {

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
    // Список изображений для пейджера (переживает повороты)
    var pagerImages: List<WebDavRepository.FileInfo> = emptyList()

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
                // Выставляем корень сервера ДО переключения вкладки
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
            appendLog(" Получение списка: $path")
        
            // ИСПРАВЛЕНО: одно атомарное обновление с currentPath
            val files = WebDavRepository.listFiles(base, path, user, pass)
            val sorted = files.sortedWith(
                compareBy<WebDavRepository.FileInfo> { !it.isDirectory }.thenBy { it.name }
            )
        
            _uiState.value = _uiState.value?.copy(
                currentPath = path,  // ✅ Сохраняем путь
                files = sorted,
                isLoading = false,
                selectionMode = false,
                selectedIndices = emptySet()
            )
        
            appendLog("✓ Элементов: ${sorted.size}")
            CoilPrefetch.prefetch(getApplication(), server, user, pass, sorted)
        }
    }

    // ИСПРАВЛЕНО: добавлены отладочные логи для диагностики проблемы с возвратом назад
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

    // ==================== Загрузка файлов ====================
    fun uploadFilesToPath(
        server: String, user: String, pass: String,
        uris: List<Uri>, targetPath: String
    ) {
        appendLog("=== uploadFilesToPath ===")
        appendLog("Сервер: $server")
        appendLog("Папка: $targetPath")
        appendLog("Файлов: ${uris.size}")
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
            var success = 0
            var failed = 0
            val startTime = System.currentTimeMillis()

            // НОВОЕ: live-запись + переключение на вкладку ИСТОРИЯ (как у заданий)
            HistoryManager.createLiveRecord(getApplication(), startTime, "Ручная загрузка", "user")
            refreshHistory()
            _events.value = Event.SwitchTab(3)

            val ticker = viewModelScope.launch {
                while (isActive) {
                    delay(1000)
                    refreshHistory()
                }
            }

            val fileDetails = mutableListOf<SyncFileDetail>()
            var totalBytes = 0L
            var totalTransferMs = 0L

            uris.forEachIndexed { index, uri ->
                val meta = WebDavRepository.getFileMetadata(getApplication(), uri)
                val remotePath = targetPath.trimEnd('/') + "/" + meta.name
                appendLog("Загрузка: ${meta.name} -> $remotePath (${meta.size} байт)")

                // НОВОЕ: live-прогресс в историю
                HistoryManager.updateLiveRecord(getApplication(), startTime, meta.name, index + 1, total)

                val fileStart = System.currentTimeMillis()
                try {
                    val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
                    if (inputStream == null) {
                        appendLog("✗ Не удалось открыть файл: ${meta.name}")
                        failed++
                        return@forEachIndexed
                    }
                    val code = inputStream.use { input ->
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            WebDavClient.put(
                                base + WebDavRepository.encodePath(remotePath),
                                user, pass, input
                            )
                        }
                    }
                    val fileMs = System.currentTimeMillis() - fileStart
                    if (code in 200..299) {
                        appendLog("✓ ${meta.name} загружен (HTTP $code)")
                        success++
                        fileDetails.add(SyncFileDetail(meta.name, meta.size, fileMs, "Пользователь"))
                        totalBytes += meta.size
                        totalTransferMs += fileMs
                    } else {
                        appendLog("✗ ${meta.name}: HTTP $code")
                        failed++
                    }
                } catch (e: Exception) {
                    appendLog("✗ ${meta.name}: ${e.javaClass.simpleName}: ${e.message}")
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

            appendLog("Завершено: успешно $success, ошибок $failed")
            _events.value = Event.ShowToast("Загрузка: $success / $total")
            // picked очищается в MainActivity по ShowToast с префиксом "Загрузка:"
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
            selected.forEach { file ->
                val deleted = WebDavRepository.deleteFile(base, file.path, user, pass)
                if (deleted) appendLog("✓ Удалено: ${file.name}")
                else appendLog("✗ Не удалось удалить: ${file.name}")
            }
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
            var count = 0
            selected.filter { !it.isDirectory }.forEach { file ->
                val downloaded = WebDavRepository.downloadFile(
                    getApplication(), base, file.path, file.name, user, pass
                )
                if (downloaded) { appendLog("✓ Скачано: ${file.name}"); count++ }
                else appendLog("✗ Не удалось скачать: ${file.name}")
            }
            _events.value = Event.ShowToast("Скачано: $count")
        }
    }

    fun downloadFile(server: String, path: String, fileName: String, user: String, pass: String) {
        viewModelScope.launch {
            val base = WebDavRepository.normalizeBaseUrl(server) ?: return@launch
            appendLog("⬇ Скачивание: $fileName")
            val downloaded = WebDavRepository.downloadFile(
                getApplication(), base, path, fileName, user, pass
            )
            if (downloaded) appendLog("✓ Скачано: $fileName")
            else appendLog("✗ Не удалось скачать: $fileName")
        }
    }

    fun viewVideo(context: Context, item: WebDavRepository.FileInfo) {
        viewModelScope.launch {
            _events.value = Event.ShowToast("Скачивание видео: ${item.name}")
            val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
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
            val created = WebDavRepository.createFolder(base, newPath, user, pass)
            if (created) {
                appendLog("✓ Папка создана: $folderName")
                _events.value = Event.ShowToast("Папка создана")
                browseServer(server, state.currentPath, user, pass)
            } else {
                appendLog("✗ Ошибка создания папки")
                _events.value = Event.ShowToast("Ошибка создания папки")
            }
        }
    }

    // ==================== Задания и история ====================
    fun refreshTasks() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _tasks.postValue(TaskManager.load(getApplication()))
        }
    }

    fun refreshHistory() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _history.postValue(HistoryManager.getRecords(getApplication()))
        }
    }

    fun clearHistory() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            HistoryManager.clear(getApplication())
            _history.postValue(HistoryManager.getRecords(getApplication()))
        }
    }

    fun runTaskNow(t: SyncTask) {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            
            // НОВОЕ: защита от двойного запуска
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
            
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                SyncEngine.runTask(
                    getApplication(), t, trigger = "user",
                    startTime = startTime,   // НОВОЕ
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
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                TaskManager.upsert(getApplication(), updated)
            }
            AlarmScheduler.scheduleNext(getApplication())
            refreshTasks()
            refreshHistory()
        }
    }

    fun deleteTask(t: SyncTask) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            TaskManager.delete(getApplication(), t.id)
            AlarmScheduler.scheduleNext(getApplication())
            _tasks.postValue(TaskManager.load(getApplication()))
        }
    }

    fun log(message: String) { appendLog(message) }

    private fun appendLog(message: String) {
        val current = _uiState.value?.log ?: ""
        val newLog = current + message + "\n"
        _uiState.postValue(_uiState.value?.copy(
            log = if (newLog.length > 20000) newLog.takeLast(20000) else newLog
        ) ?: UiState(log = newLog))
    }

    // ==================== Карточки заданий ====================
    fun buildTaskCard(context: Context, t: SyncTask): android.view.View {
        val cardColor: Int
        val strokeColor: Int
        when {
            !t.scheduleEnabled -> {
                cardColor = 0xFF6D6D6D.toInt()
                strokeColor = 0xFFBDBDBD.toInt()
            }
            t.lastStatus == "error" -> {
                cardColor = 0xFF4A2D2D.toInt()
                strokeColor = 0xFFE57373.toInt()
            }
            else -> {
                cardColor = 0xFF2D4A2D.toInt()
                strokeColor = 0xFF81C784.toInt()
            }
        }
        val card = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val gd = android.graphics.drawable.GradientDrawable()
            gd.setColor(cardColor)
            gd.cornerRadius = 24f
            gd.setStroke(2, strokeColor)
            background = gd
            setPadding(24, 20, 24, 20)
            val lp = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 8, 0, 8)
            layoutParams = lp
            setOnClickListener {
                context.startActivity(
                    android.content.Intent(context, TaskDetailsActivity::class.java)
                        .putExtra("taskId", t.id)
                )
            }
        }
        card.addView(android.widget.TextView(context).apply {
            text = t.name
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        card.addView(android.widget.TextView(context).apply {
            text = when (t.syncType) {
                "two_way" -> "⇄ Двусторонняя"
                "to_left" -> "← В левую папку"
                else -> "→ В правую папку"
            }
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 12f
        })
        card.addView(android.widget.TextView(context).apply {
            text = if (t.lastRun > 0) "📅 ${formatDateTime(t.lastRun)}" else "Ещё не запускалось"
            setTextColor(0xFFCCCCCC.toInt())
            textSize = 12f
        })
        card.addView(android.widget.TextView(context).apply {
            text = if (t.scheduleEnabled) "⏰ Расписание: ${scheduleLabel(t)}" else "⏰ Расписание выключено"
            setTextColor(0xFFCCCCCC.toInt())
            textSize = 12f
        })
        val row = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            val lp = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = 14
            layoutParams = lp
        }
        row.addView(android.widget.Button(context).apply {
            text = "▶ ЗАПУСК"
            setBackgroundResource(R.drawable.bg_button_primary)
            setTextColor(0xFF000000.toInt())
            setOnClickListener { runTaskNow(t) }
            val bpl = android.widget.LinearLayout.LayoutParams(
                0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            layoutParams = bpl
        })
        row.addView(android.widget.Button(context).apply {
            text = "✏️"
            setBackgroundResource(R.drawable.bg_button_secondary)
            setTextColor(0xFFE0E0E0.toInt())
            setOnClickListener {
                context.startActivity(
                    android.content.Intent(context, TaskDetailsActivity::class.java)
                        .putExtra("taskId", t.id)
                )
            }
            val bpl = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            bpl.leftMargin = 12
            layoutParams = bpl
        })
        row.addView(android.widget.Button(context).apply {
            text = "🗑"
            setBackgroundResource(R.drawable.bg_button_danger)
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener {
                androidx.appcompat.app.AlertDialog.Builder(context)
                    .setTitle("Удалить задание?")
                    .setMessage("Задание \"${t.name}\" будет удалено безвозвратно.")
                    .setPositiveButton("Удалить") { _, _ ->
                        deleteTask(t)
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
            }
            val bpl = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            bpl.leftMargin = 12
            layoutParams = bpl
        })
        card.addView(row)
        return card
    }

    // ==================== Карточки истории ====================
    fun buildHistoryCard(context: Context, r: HistoryRecord): android.view.View {
        val isRunning = r.status == "running"
        val isOk = r.status == "ok"
        val cardColor = when {
            isRunning -> 0xFF1E3A5F.toInt()
            isOk -> 0xFF2D4A2D.toInt()
            else -> 0xFF4A2D2D.toInt()
        }
        val strokeColor = when {
            isRunning -> 0xFF64B5F6.toInt()
            isOk -> 0xFF81C784.toInt()
            else -> 0xFFE57373.toInt()
        }
        
        val card = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val gd = android.graphics.drawable.GradientDrawable()
            gd.setColor(cardColor)
            gd.cornerRadius = 24f
            gd.setStroke(2, strokeColor)
            background = gd
            setPadding(24, 20, 24, 20)
            val lp = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 8, 0, 8)
            layoutParams = lp
            if (!isRunning) {
                setOnClickListener {
                    context.startActivity(
                        android.content.Intent(context, HistoryDetailsActivity::class.java)
                            .putExtra("time", r.time)
                    )
                }
            }
        }
        
        val top = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        top.addView(android.widget.TextView(context).apply {
            text = r.taskName.ifBlank { "Синхронизация" }
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        top.addView(android.widget.TextView(context).apply {
            text = when {
                isRunning -> "⏳"
                isOk -> "✔"
                else -> "✖"
            }
            setTextColor(when {
                isRunning -> 0xFF64B5F6.toInt()
                isOk -> 0xFF81C784.toInt()
                else -> 0xFFE57373.toInt()
            })
            textSize = 22f
        })
        card.addView(top)
        
        if (isRunning) {
            // НОВОЕ: live-карточка
            val elapsed = System.currentTimeMillis() - r.liveStartedAt
            card.addView(cardRow(context, "🔄 Выполняется...", "⏱ ${formatDuration(elapsed)}"))
            
            if (r.totalFiles > 0) {
                val pb = android.widget.ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = r.totalFiles
                    progress = r.currentFileIndex
                    val lp = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.topMargin = 12
                    layoutParams = lp
                }
                card.addView(pb)
                
                card.addView(android.widget.TextView(context).apply {
                    text = "📄 ${r.currentFileIndex} из ${r.totalFiles}"
                    setTextColor(0xFFCCCCCC.toInt())
                    textSize = 13f
                    val lp = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.topMargin = 4
                    layoutParams = lp
                })
            }
            
            if (r.currentFileName.isNotEmpty()) {
                card.addView(android.widget.TextView(context).apply {
                    text = "▸ ${r.currentFileName}"
                    setTextColor(0xFF64B5F6.toInt())
                    textSize = 12f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                    val lp = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.topMargin = 4
                    layoutParams = lp
                })
            }
            
            if (r.currentFileIndex > 0 && r.totalFiles > r.currentFileIndex) {
                val avgPerFile = elapsed.toFloat() / r.currentFileIndex
                val etaMs = (avgPerFile * (r.totalFiles - r.currentFileIndex)).toLong()
                card.addView(android.widget.TextView(context).apply {
                    text = "⏱ Осталось примерно: ${formatDuration(etaMs)}"
                    setTextColor(0xFFAAAAAA.toInt())
                    textSize = 11f
                    val lp = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.topMargin = 4
                    layoutParams = lp
                })
            }
        } else {
            // Финальная карточка
            card.addView(cardRow(context, "📅 ${formatDateTime(r.time)}", "⏱ ${formatDuration(r.durationMs)}"))
            card.addView(cardRow(context, "🔍 Проверено: ${r.checked}", "⬆ Передано: ${r.uploaded + r.downloaded}"))
            if (r.uploaded > 0 || r.downloaded > 0) {
                card.addView(cardRow(context, "⬆ Загружено: ${r.uploaded} ф.", "⬇ Скачано: ${r.downloaded} ф."))
            }
            if (r.bytesTransferred > 0) {
                val speed = if (r.transferMs > 0) {
                    val v = (r.bytesTransferred / 1048576.0) / (r.transferMs / 1000.0)
                    if (v >= 1) String.format("%.1f МБ/с", v) else "${FileUtils.formatSize((r.bytesTransferred / (r.transferMs / 1000.0)).toLong())}/с"
                } else "—"
                card.addView(cardRow(context, "💾 ${FileUtils.formatSize(r.bytesTransferred)}", "⚡ $speed"))
            }
            card.addView(cardRow(context, "✗ Ошибок: ${r.errors}", triggerLabel(r.trigger)))
        }
        
        return card
    }
    
    // НОВОЕ: парсинг JSON файлов из HistoryRecord
    private fun parseFilesJson(json: String?): List<SyncFileDetail> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                SyncFileDetail(
                    name = obj.optString("n", ""),
                    size = obj.optLong("s", 0),
                    ms = obj.optLong("m", 0),
                    side = obj.optString("d", "")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun cardRow(context: Context, left: String, right: String): android.view.View {
        val row = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            val lp = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = 8
            layoutParams = lp
        }
        row.addView(android.widget.TextView(context).apply {
            text = left
            setTextColor(0xFFCCCCCC.toInt())
            textSize = 13f
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        })
        row.addView(android.widget.TextView(context).apply {
            text = right
            setTextColor(0xFFCCCCCC.toInt())
            textSize = 13f
        })
        return row
    }

    // ==================== Утилиты ====================
    private fun scheduleLabel(t: SyncTask): String = when (t.scheduleMode) {
        "minutes" -> "каждые ${t.intervalValue} мин"
        "hourly" -> "каждые ${t.intervalValue} ч"
        "daily" -> "ежедневно ${String.format("%02d:%02d", t.hour, t.minute)}"
        "weekly" -> "еженедельно ${String.format("%02d:%02d", t.hour, t.minute)}"
        "monthly" -> "ежемесячно ${String.format("%02d:%02d", t.hour, t.minute)}"
        else -> t.scheduleMode
    }

    private fun formatDateTime(time: Long): String =
        java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(time))

        private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return when {
            h > 0 -> "${h}ч ${m}м"
            m > 0 -> "${m}м ${s}с"
            else -> "${s}с"
        }
    }

    private fun triggerLabel(t: String): String = when (t) {
        "user" -> "👤 Пользователь"
        "test" -> "🧪 Тест"
        "schedule" -> "⏰ Расписание"
        else -> "•"
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