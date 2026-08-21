package com.rezerv.upload

import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val TAG = "MainActivity"

    // Вкладки
    private lateinit var btnTabConnection: Button
    private lateinit var btnTabBrowser: Button
    private lateinit var btnTabTasks: Button
    private lateinit var btnTabHistory: Button
    private lateinit var tabConnection: ScrollView
    private lateinit var tabBrowser: View
    private lateinit var tabTasks: ScrollView
    private lateinit var tabHistory: ScrollView

    // Подключение
    private lateinit var etServer: EditText
    private lateinit var etUser: EditText
    private lateinit var etPass: EditText
    private lateinit var spAuth: Spinner
    private lateinit var tvLog: TextView
    private lateinit var btnConnect: Button

    // Файлы (RecyclerView)
    private lateinit var tvCurrentPath: TextView
    private lateinit var rvFiles: RecyclerView
    private lateinit var pbFiles: ProgressBar
    private lateinit var pbBytes: ProgressBar
    private lateinit var tvProgressFiles: TextView
    private lateinit var tvProgressBytes: TextView
    private lateinit var btnBack: Button
    private lateinit var btnNewFolder: Button
    private lateinit var llSelection: LinearLayout
    private lateinit var tvSelectionCount: TextView

    // Задания
    private lateinit var tasksContainer: LinearLayout

    // История
    private lateinit var historyChart: HistoryChartView
    private lateinit var historyContainer: LinearLayout
    private lateinit var tvHistoryEmpty: TextView
    private lateinit var btnClearHistory: Button

    private var picked: List<Uri> = emptyList()
    private lateinit var fileAdapter: FileRecyclerViewAdapter

    private val picker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        picked = uris ?: emptyList()
        picked.forEach { uri ->
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                Log.w(TAG, "Не удалось взять разрешение для $uri: ${e.message}")
            }
        }
        viewModel.log("Выбрано файлов: ${picked.size}")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        coil.Coil.setImageLoader(
            coil.ImageLoader.Builder(this)
                .okHttpClient(WebDavClient.httpClient)
                .memoryCache { coil.memory.MemoryCache.Builder(this).maxSizePercent(0.25).build() }
                .build()
        )
        initViews()
        setupListeners()
        loadSettings()
        observeViewModel()
        viewModel.ensureScheduler()
        requestNotifications()   // ИСПРАВЛЕНО: runtime-запрос POST_NOTIFICATIONS (33+)
        promptExactAlarms()      // ИСПРАВЛЕНО: запрос точных будильников (S+)
    }

    override fun onResume() {
        super.onResume()
        if (tabTasks.visibility == View.VISIBLE) viewModel.refreshTasks()
        if (tabHistory.visibility == View.VISIBLE) viewModel.refreshHistory()
    }

    // ИСПРАВЛЕНО: гасим scope адаптера
    override fun onDestroy() {
        super.onDestroy()
    }

    // ==================== Разрешения ====================
    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }

    private fun promptExactAlarms() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(AlarmManager::class.java)
            if (!am.canScheduleExactAlarms()) {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:$packageName")))
                } catch (_: Exception) {
                    try {
                        startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun initViews() {
        btnTabConnection = findViewById(R.id.btnTabConnection)
        btnTabBrowser = findViewById(R.id.btnTabBrowser)
        btnTabTasks = findViewById(R.id.btnTabTasks)
        btnTabHistory = findViewById(R.id.btnTabHistory)
        tabConnection = findViewById(R.id.tabConnection)
        tabBrowser = findViewById(R.id.tabBrowser)
        tabTasks = findViewById(R.id.tabTasks)
        tabHistory = findViewById(R.id.tabHistory)
        etServer = findViewById(R.id.etServer)
        etUser = findViewById(R.id.etUser)
        etPass = findViewById(R.id.etPass)
        spAuth = findViewById(R.id.spAuth)
        tvLog = findViewById(R.id.tvLog)
        btnConnect = findViewById(R.id.btnConnect)
        tvCurrentPath = findViewById(R.id.tvCurrentPath)
        rvFiles = findViewById(R.id.rvFiles)
        pbFiles = findViewById(R.id.pbFiles)
        pbBytes = findViewById(R.id.pbBytes)
        tvProgressFiles = findViewById(R.id.tvProgressFiles)
        tvProgressBytes = findViewById(R.id.tvProgressBytes)
        btnBack = findViewById(R.id.btnBack)
        btnNewFolder = findViewById(R.id.btnNewFolder)
        llSelection = findViewById(R.id.llSelection)
        tvSelectionCount = findViewById(R.id.tvSelectionCount)
        tasksContainer = findViewById(R.id.tasksContainer)
        historyChart = findViewById(R.id.historyChart)
        historyContainer = findViewById(R.id.historyContainer)
        tvHistoryEmpty = findViewById(R.id.tvHistoryEmpty)
        btnClearHistory = findViewById(R.id.btnClearHistory)

        rvFiles.layoutManager = LinearLayoutManager(this)
        rvFiles.itemAnimator = null
        fileAdapter = FileRecyclerViewAdapter(
            this,
            serverUrl = { viewModel.loadSettings().first },
            user = { viewModel.loadSettings().second },
            pass = { viewModel.loadSettings().third },
            onItemClick = { item, position -> handleFileClick(item, position) }
        )
        rvFiles.adapter = fileAdapter
        rvFiles.addOnItemTouchListener(
            DragSelectionListener(
                recyclerView = rvFiles,
                isSelectionActive = { viewModel.uiState.value?.selectionMode ?: false },
                onStartSelection = { pos -> viewModel.startSelectionMode(pos) },
                onDragStart = { anchor, forceAdd -> viewModel.beginRangeSelection(anchor, forceAdd) },
                onRangeSelect = { anchor, current -> viewModel.selectRange(anchor, current) }
            )
        )

        val authTypes = resources.getStringArray(R.array.auth_types)
        val spinnerAdapter = ArrayAdapter(this, R.layout.spinner_item, authTypes)
        spinnerAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spAuth.adapter = spinnerAdapter
        spAuth.background = null
    }

    private fun setupListeners() {
        btnTabConnection.setOnClickListener { switchToTab(0) }
        btnTabBrowser.setOnClickListener { switchToTab(1) }
        btnTabTasks.setOnClickListener { switchToTab(2) }
        btnTabHistory.setOnClickListener { switchToTab(3) }

        findViewById<Button>(R.id.btnCreateTask).setOnClickListener {
            startActivity(Intent(this, TaskWizardActivity::class.java))
        }

        btnClearHistory.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Очистить историю?")
                .setPositiveButton("Очистить") { _, _ -> viewModel.clearHistory() }
                .setNegativeButton("Отмена", null).show()
        }

        btnConnect.setOnClickListener {
            val server = etServer.text.toString()
            val user = etUser.text.toString()
            val pass = etPass.text.toString()
            val authType = spAuth.selectedItemPosition
            viewModel.saveSettings(server, user, pass, authType)
            viewModel.connect(server, user, pass)
        }

        btnBack.setOnClickListener {
            val (server, user, pass) = viewModel.loadSettings()
            viewModel.navigateBack(server, user, pass)
        }

        btnNewFolder.setOnClickListener {
            val input = EditText(this).apply {
                hint = "Имя папки"
                setPadding(48, 24, 48, 24)
                setTextColor(0xFFFFFFFF.toInt())
                setHintTextColor(0xFF888888.toInt())
                setBackgroundResource(R.drawable.bg_input_dark)
            }
            AlertDialog.Builder(this)
                .setTitle("Создать папку")
                .setView(input)
                .setPositiveButton("Создать") { _, _ ->
                    val name = input.text.toString().trim()
                    if (name.isNotEmpty()) {
                        val (server, user, pass) = viewModel.loadSettings()
                        viewModel.createFolder(server, name, user, pass)
                    }
                }
                .setNegativeButton("Отмена", null).show()
        }

        findViewById<Button>(R.id.btnPick).setOnClickListener { picker.launch(arrayOf("*/*")) }

        findViewById<Button>(R.id.btnUpload).setOnClickListener {
            val (server, user, pass) = viewModel.loadSettings()
            if (server.isBlank()) { viewModel.log("Ошибка: сервер не подключён"); return@setOnClickListener }
            if (picked.isEmpty()) { viewModel.log("Нет выбранных файлов"); return@setOnClickListener }
            showServerFolderPicker(server, user, pass) { targetPath ->
                viewModel.uploadFilesToPath(server, user, pass, picked, targetPath)
            }
        }

        findViewById<Button>(R.id.btnSelDownload).setOnClickListener {
            val (server, user, pass) = viewModel.loadSettings()
            viewModel.downloadSelected(server, user, pass)
        }

        findViewById<Button>(R.id.btnSelDelete).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Удалить элементы?")
                .setMessage("Выбрано: ${viewModel.getSelectedCount()}")
                .setPositiveButton("Удалить") { _, _ ->
                    val (server, user, pass) = viewModel.loadSettings()
                    viewModel.deleteSelected(server, user, pass)
                }
                .setNegativeButton("Отмена", null).show()
        }

        findViewById<Button>(R.id.btnSelCancel).setOnClickListener { viewModel.exitSelectionMode() }
    }

    // ==================== Клики по файлам ====================
    private fun handleFileClick(item: WebDavRepository.FileInfo, position: Int) {
        val state = viewModel.uiState.value ?: return
    
        // ИСПРАВЛЕНО: добавлен лог для отладки
        Log.d(TAG, "handleFileClick: ${item.name}, isDir=${item.isDirectory}, selectionMode=${state.selectionMode}")
    
        if (state.selectionMode) {
            viewModel.toggleSelection(position)
        } else if (item.isDirectory) {
            val (server, user, pass) = viewModel.loadSettings()
            // ИСПРАВЛЕНО: проверяем что сервер не пустой перед навигацией
            if (server.isNotBlank()) {
            viewModel.browseServer(server, item.path, user, pass)
            } else {
                viewModel.log("Ошибка: сервер не подключён")
            }
        } else if (FileUtils.isImageFile(item.name)) {
            val images = viewModel.getImageList()
            val index = images.indexOfFirst { it.path == item.path }
            if (index >= 0) showImagePager(images, index)
        } else if (FileUtils.isVideoFile(item.name)) {
            viewModel.viewVideo(this, item)
        } else {
            val (server, user, pass) = viewModel.loadSettings()
            viewModel.downloadFile(server, item.path, item.name, user, pass)
        }
    }

    // ==================== Диалог выбора папки на сервере ====================
    private fun showServerFolderPicker(server: String, user: String, pass: String, onFolderSelected: (String) -> Unit) {
        val base = WebDavRepository.normalizeBaseUrl(server)
        if (base == null) { viewModel.log("Ошибка: неверный адрес сервера"); return }
        val root = WebDavRepository.getServerPath(server)
        showFolderPickerDialog(base, root, user, pass, root, onFolderSelected)
    }

    private fun showFolderPickerDialog(
        base: String, root: String, user: String, pass: String,
        currentPath: String, onFolderSelected: (String) -> Unit
    ) {
        lifecycleScope.launch {
            try {
                val files = withContext(Dispatchers.IO) {
                    WebDavRepository.listFiles(base, currentPath, user, pass)
                }
                val folders = files.filter { it.isDirectory }.map { it.name }.sorted()
                val displayItems = mutableListOf<String>()
                if (currentPath != root) displayItems.add(".. (вверх)")
                displayItems.addAll(folders)
                withContext(Dispatchers.Main) {
                    if (isFinishing || isDestroyed) return@withContext
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Папка: $currentPath")
                        .setItems(displayItems.toTypedArray()) { _, which ->
                            val selected = displayItems[which]
                            if (selected == ".. (вверх)") {
                                val trimmed = currentPath.trimEnd('/')
                                val lastSlash = trimmed.lastIndexOf('/')
                                val newPath = if (lastSlash <= 0) root else trimmed.substring(0, lastSlash + 1)
                                showFolderPickerDialog(base, root, user, pass, newPath, onFolderSelected)
                            } else {
                                val newPath = currentPath.trimEnd('/') + "/" + selected + "/"
                                showFolderPickerDialog(base, root, user, pass, newPath, onFolderSelected)
                            }
                        }
                        .setPositiveButton("Выбрать эту папку") { _, _ -> onFolderSelected(currentPath) }
                        .setNegativeButton("Отмена", null)
                        .show()
                }
            } catch (e: Exception) {
                viewModel.log("Ошибка загрузки списка папок: ${e.message}")
            }
        }
    }

    // ==================== Наблюдение за ViewModel ====================
    private fun observeViewModel() {
        viewModel.uiState.observe(this, Observer { state ->
            updateFileList(state.files)
            updateProgress(state)
            updateLog(state.log)
            updateSelectionUI(state.selectionMode, state.selectedIndices.size)
            tvCurrentPath.text = "Путь: ${state.currentPath}"
        })
        viewModel.tasks.observe(this, Observer { tasks -> refreshTasks(tasks) })
        viewModel.history.observe(this, Observer { records -> refreshHistory(records) })
        viewModel.events.observe(this, Observer { event ->
            when (event) {
                is MainViewModel.Event.ShowToast ->
                    Toast.makeText(this, event.message, Toast.LENGTH_SHORT).show()
                is MainViewModel.Event.SwitchTab -> switchToTab(event.tab)
                is MainViewModel.Event.UploadFinished -> picked = emptyList()
            }
        })
    }

    private fun updateFileList(files: List<WebDavRepository.FileInfo>) {
        val state = viewModel.uiState.value
        fileAdapter.selectionMode = state?.selectionMode ?: false
        fileAdapter.selectedIndices = state?.selectedIndices?.toMutableSet() ?: mutableSetOf()
        fileAdapter.submitList(files)
    }

    private fun updateProgress(state: MainViewModel.UiState) {
        val progress = state.uploadProgress
        if (progress != null) {
            pbFiles.max = progress.totalFiles
            pbFiles.progress = progress.currentFile - 1
            tvProgressFiles.text = "Файлов: ${progress.currentFile} / ${progress.totalFiles}"
            tvProgressBytes.text = "${progress.fileName}: ${FileUtils.formatSize(progress.bytesUploaded)} / ${FileUtils.formatSize(progress.totalBytes)}"
            if (progress.totalBytes > 0) {
                pbBytes.max = 100
                pbBytes.progress = ((progress.bytesUploaded * 100) / progress.totalBytes).toInt()
                pbBytes.isIndeterminate = false
            } else {
                pbBytes.isIndeterminate = true
            }
        } else {
            pbFiles.progress = 0
            pbBytes.progress = 0
            pbBytes.isIndeterminate = false
            tvProgressFiles.text = "Файлов: 0 / 0"
            tvProgressBytes.text = "Загрузка завершена"
        }
    }

    private fun updateLog(log: String) {
        tvLog.text = log
    }

    private fun updateSelectionUI(selectionMode: Boolean, count: Int) {
        llSelection.visibility = if (selectionMode) View.VISIBLE else View.GONE
        tvSelectionCount.text = "Выбрано: $count"
        fileAdapter.selectionMode = selectionMode
        fileAdapter.selectedIndices = viewModel.uiState.value?.selectedIndices?.toMutableSet() ?: mutableSetOf()
        fileAdapter.notifyDataSetChanged()
    }

    private fun switchToTab(tab: Int) {
        tabConnection.visibility = View.GONE
        tabBrowser.visibility = View.GONE
        tabTasks.visibility = View.GONE
        tabHistory.visibility = View.GONE
        btnTabConnection.setBackgroundResource(R.drawable.bg_button_secondary)
        btnTabBrowser.setBackgroundResource(R.drawable.bg_button_secondary)
        btnTabTasks.setBackgroundResource(R.drawable.bg_button_secondary)
        btnTabHistory.setBackgroundResource(R.drawable.bg_button_secondary)
        val inactiveColor = 0xFFE0E0E0.toInt()
        btnTabConnection.setTextColor(inactiveColor)
        btnTabBrowser.setTextColor(inactiveColor)
        btnTabTasks.setTextColor(inactiveColor)
        btnTabHistory.setTextColor(inactiveColor)

        when (tab) {
            0 -> {
                tabConnection.visibility = View.VISIBLE
                btnTabConnection.setBackgroundResource(R.drawable.bg_button_primary)
                btnTabConnection.setTextColor(0xFF000000.toInt())
            }
            1 -> {
                tabBrowser.visibility = View.VISIBLE
                btnTabBrowser.setBackgroundResource(R.drawable.bg_button_primary)
                btnTabBrowser.setTextColor(0xFF000000.toInt())
                // ИСПРАВЛЕНО (гонка 405):
                // - не запрашиваем повторно, если список уже есть или запрос в процессе;
                // - fallback — корень СЕРВЕРА (getServerPath), а не запрещённый "/"
                val (server, user, pass) = viewModel.loadSettings()
                val state = viewModel.uiState.value
                if (server.isNotBlank()
                    && state?.files.isNullOrEmpty()
                    && state?.isLoading != true) {
                    viewModel.browseServer(server, WebDavRepository.getServerPath(server), user, pass)
                }
            }
            2 -> {
                tabTasks.visibility = View.VISIBLE
                btnTabTasks.setBackgroundResource(R.drawable.bg_button_primary)
                btnTabTasks.setTextColor(0xFF000000.toInt())
                viewModel.refreshTasks()
            }
            3 -> {
                tabHistory.visibility = View.VISIBLE
                btnTabHistory.setBackgroundResource(R.drawable.bg_button_primary)
                btnTabHistory.setTextColor(0xFF000000.toInt())
                viewModel.refreshHistory()
            }
        }
    }

    private fun refreshTasks(tasks: List<SyncTask>) {
        tasksContainer.removeAllViews()
        if (tasks.isEmpty()) {
            tasksContainer.addView(TextView(this).apply {
                text = "Нет заданий.\nНажмите «➕ Создать задание»."
                setTextColor(0xFF888888.toInt())
                gravity = android.view.Gravity.CENTER
            })
            return
        }
        for (t in tasks) tasksContainer.addView(viewModel.buildTaskCard(this, t))
    }

    private fun refreshHistory(records: List<HistoryRecord>) {
        historyContainer.removeAllViews()
        historyChart.setRecords(records)
        if (records.isEmpty()) {
            tvHistoryEmpty.visibility = View.VISIBLE
            return
        }
        tvHistoryEmpty.visibility = View.GONE
        for (r in records) historyContainer.addView(viewModel.buildHistoryCard(this, r))
    }

    private fun showImagePager(images: List<WebDavRepository.FileInfo>, startIndex: Int) {
        viewModel.pagerImages = images
        val (server, user, pass) = viewModel.loadSettings()
        val fragment = ImagePagerFragment.newInstance(startIndex, server, user, pass)
        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun loadSettings() {
        val (server, user, pass) = viewModel.loadSettings()
        val authType = SecurePrefs.loadAuthType(this)
        etServer.setText(server)
        etUser.setText(user)
        etPass.setText(pass)
        spAuth.setSelection(authType)
        if (server.isNotEmpty()) viewModel.log("Настройки загружены")
    }
}