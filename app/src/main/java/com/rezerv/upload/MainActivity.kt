package com.rezerv.upload

import android.Manifest
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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rezerv.upload.ui.HistoryCardBuilder
import com.rezerv.upload.ui.TaskCardBuilder
import com.rezerv.upload.viewmodel.BrowserViewModel
import com.rezerv.upload.viewmodel.ConnectionViewModel
import com.rezerv.upload.viewmodel.HistoryViewModel
import com.rezerv.upload.viewmodel.TasksViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.ui.platform.ComposeView
import com.rezerv.upload.ui.compose.TasksTab
import com.rezerv.upload.ui.theme.RezervTheme

import com.rezerv.upload.utils.Validators

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    private var isHistoryFirstLoad = true  

    // ✅ 4 ViewModel вместо одной
    private val connectionVM: ConnectionViewModel by viewModels()
    private val browserVM: BrowserViewModel by viewModels()
    private val tasksVM: TasksViewModel by viewModels()
    private val historyVM: HistoryViewModel by viewModels()

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

    // Файлы
    private lateinit var tvCurrentPath: TextView
    private lateinit var rvFiles: RecyclerView
    private lateinit var btnBack: Button
    private lateinit var btnNewFolder: Button
    private lateinit var llSelection: LinearLayout
    private lateinit var tvSelectionCount: TextView

    // История
    private lateinit var historyChart: HistoryChartView
    private lateinit var historyContainer: LinearLayout
    private lateinit var tvHistoryEmpty: TextView
    private lateinit var btnClearHistory: Button

    // Skeleton screens
    private lateinit var skeletonFiles: View
    private lateinit var skeletonHistory: LinearLayout
    private lateinit var tasksComposeView: ComposeView

    private var picked: List<Uri> = emptyList()
    private lateinit var fileAdapter: FileRecyclerViewAdapter

    // ==================== Permission Launchers ====================

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) connectionVM.log("✓ Уведомления разрешены")
    }

    private val mediaPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    private val exactAlarmSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        connectionVM.ensureScheduler()
    }

    private val picker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        picked = uris ?: emptyList()
        picked.forEach { uri ->
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                Log.w(TAG, "Не удалось взять разрешение для $uri")
            }
        }
        browserVM.log("Выбрано файлов: ${picked.size}")
    }

    // ==================== Lifecycle ====================

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
        observeViewModels()
        connectionVM.ensureScheduler()
        requestMediaPermissions()
        requestNotifications()
        promptExactAlarms()
        promptBatteryOptimization()
    }

    override fun onResume() {
        super.onResume()
        if (tabHistory.visibility == View.VISIBLE) historyVM.refreshHistory()
        checkExactAlarmStatus()
    }

    // ==================== Наблюдение за ViewModel ====================

    private fun observeViewModels() {
        // ConnectionViewModel
        connectionVM.state.observe(this) {
            // Можно обновить UI подключения
        }
        connectionVM.log.observe(this) { log -> tvLog.text = log }
        connectionVM.events.observe(this) { event ->
            when (event) {
                is ConnectionViewModel.ConnectionEvent.Connected -> {
                    switchToTab(1)
                    val (server, user, pass) = connectionVM.loadSettings()
                    browserVM.browseServer(server, event.serverPath, user, pass)
                }
                is ConnectionViewModel.ConnectionEvent.ConnectionFailed -> {
                    Toast.makeText(this, "Ошибка: ${event.error}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // BrowserViewModel
        browserVM.state.observe(this) { state ->
            updateFileList(state)
            updateSelectionUI(state.selectionMode, state.selectedIndices.size)
            tvCurrentPath.text = "Путь: ${state.currentPath}"
        }
        browserVM.log.observe(this) { log -> tvLog.text = log }
        browserVM.events.observe(this) { event ->
            when (event) {
                is BrowserViewModel.BrowserEvent.ShowToast -> {
                    Toast.makeText(this, event.message, Toast.LENGTH_SHORT).show()
                    if (event.message.startsWith("Загрузка:")) picked = emptyList()
                }
                is BrowserViewModel.BrowserEvent.OpenImagePager -> {
                    showImagePager(event.startIndex)
                }
                is BrowserViewModel.BrowserEvent.UploadCompleted -> {
                    historyVM.refreshHistory()
                    switchToTab(3)
                }
            }
        }

// TasksViewModel
        lifecycleScope.launch {
            tasksVM.tasks.collect { tasks ->
                if (tasks != null) {
                    refreshTasks(tasks)
                }
            }
        }

// HistoryViewModel
        historyVM.records.observe(this) { records -> refreshHistory(records) }
    }

    // ==================== UI Updates ====================

    private fun updateFileList(state: BrowserViewModel.BrowserState) {
        // Skeleton: показываем при загрузке
        if (state.isLoading) {
            skeletonFiles.visibility = View.VISIBLE
            rvFiles.visibility = View.GONE
        } else {
            // Плавное появление контента
            if (skeletonFiles.visibility == View.VISIBLE) {
                rvFiles.alpha = 0f
                rvFiles.visibility = View.VISIBLE
                rvFiles.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .withEndAction { skeletonFiles.visibility = View.GONE }
                    .start()
            } else {
                rvFiles.visibility = View.VISIBLE
            }
        }   

        fileAdapter.selectionMode = state.selectionMode
        fileAdapter.selectedIndices = state.selectedIndices.toMutableSet()
        fileAdapter.submitList(state.files)
    }

    private fun updateSelectionUI(selectionMode: Boolean, count: Int) {
        llSelection.visibility = if (selectionMode) View.VISIBLE else View.GONE
        tvSelectionCount.text = "Выбрано: $count"
        fileAdapter.selectionMode = selectionMode
        fileAdapter.selectedIndices = browserVM.state.value?.selectedIndices?.toMutableSet() ?: mutableSetOf()
        fileAdapter.notifyItemRangeChanged(
            0, fileAdapter.itemCount, FileRecyclerViewAdapter.PAYLOAD_SELECTION_UPDATE
        )
    }

    private fun refreshTasks(tasks: List<SyncTask>) {
        tasksComposeView.setContent {
            RezervTheme {
                TasksTab(
                    onTaskClick = { taskId: String ->
                        startActivity(Intent(this@MainActivity, TaskDetailsActivity::class.java)
                            .putExtra("taskId", taskId))
                    },
                    onCreateTask = {
                        startActivity(Intent(this@MainActivity, TaskWizardActivity::class.java))
                    }
                )
            }
        }
    }

    private fun refreshHistory(records: List<HistoryRecord>) {
        if (records.isEmpty() && isHistoryFirstLoad) {
            skeletonHistory.visibility = View.VISIBLE
            historyContainer.visibility = View.GONE
            tvHistoryEmpty.visibility = View.GONE
            isHistoryFirstLoad = false
            return
        }
        
        isHistoryFirstLoad = false
        
        // Плавное появление контента
        if (skeletonHistory.visibility == View.VISIBLE) {
            historyContainer.alpha = 0f
            historyContainer.visibility = View.VISIBLE
            historyContainer.animate()
                .alpha(1f)
                .setDuration(300)
                .withEndAction { skeletonHistory.visibility = View.GONE }
                .start()
        } else {
            historyContainer.visibility = View.VISIBLE
        }

        historyContainer.removeAllViews()
        historyChart.setRecords(records)
        if (records.isEmpty()) {
            tvHistoryEmpty.visibility = View.VISIBLE
            return
        }
        tvHistoryEmpty.visibility = View.GONE

        val historyBuilder = HistoryCardBuilder(this)
        val listener = object : HistoryCardBuilder.Listener {
            override fun onHistoryClick(record: HistoryRecord) {
                startActivity(Intent(this@MainActivity, HistoryDetailsActivity::class.java)
                    .putExtra("time", record.time))
            }
        }

        for (r in records) historyContainer.addView(historyBuilder.build(r, listener))
    }   

    // ==================== Init Views ====================

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
        btnBack = findViewById(R.id.btnBack)
        btnNewFolder = findViewById(R.id.btnNewFolder)
        llSelection = findViewById(R.id.llSelection)
        tvSelectionCount = findViewById(R.id.tvSelectionCount)
        historyChart = findViewById(R.id.historyChart)
        historyContainer = findViewById(R.id.historyContainer)
        tvHistoryEmpty = findViewById(R.id.tvHistoryEmpty)
        btnClearHistory = findViewById(R.id.btnClearHistory)
        skeletonFiles = findViewById(R.id.skeletonFiles)
        skeletonHistory = findViewById(R.id.skeletonHistory)
        tasksComposeView = findViewById(R.id.tasksComposeView)

        rvFiles.layoutManager = LinearLayoutManager(this)
        rvFiles.itemAnimator = null
        fileAdapter = FileRecyclerViewAdapter(
            this,
            serverUrl = { connectionVM.loadSettings().first },
            user = { connectionVM.loadSettings().second },
            pass = { connectionVM.loadSettings().third },
            onItemClick = { item, position -> handleFileClick(item, position) }
        )
        rvFiles.adapter = fileAdapter
        rvFiles.addOnItemTouchListener(
            DragSelectionListener(
                recyclerView = rvFiles,
                isSelectionActive = { browserVM.state.value?.selectionMode ?: false },
                onStartSelection = { pos -> browserVM.startSelectionMode(pos) },
                onDragStart = { anchor, forceAdd -> browserVM.beginRangeSelection(anchor, forceAdd) },
                onRangeSelect = { anchor, current -> browserVM.selectRange(anchor, current) }
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

        btnClearHistory.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Очистить историю?")
                .setPositiveButton("Очистить") { _, _ -> historyVM.clearHistory() }
                .setNegativeButton("Отмена", null).show()
        }

        btnConnect.setOnClickListener {
            val server = etServer.text.toString()
            val user = etUser.text.toString()
            val pass = etPass.text.toString()
            val authType = spAuth.selectedItemPosition

            // ✅ Валидация URL
            val serverError = Validators.validateServerUrl(server)
            if (serverError != null) {
                Toast.makeText(this, serverError, Toast.LENGTH_SHORT).show()
                etServer.error = serverError
                etServer.requestFocus()
                return@setOnClickListener
            }

            // ✅ Валидация логина
            val userError = Validators.validateUsername(user)
            if (userError != null) {
                Toast.makeText(this, userError, Toast.LENGTH_SHORT).show()
                etUser.error = userError
                etUser.requestFocus()
                return@setOnClickListener
            }

            // ✅ Валидация пароля
            val passError = Validators.validatePassword(pass)
            if (passError != null) {
                Toast.makeText(this, passError, Toast.LENGTH_SHORT).show()
                etPass.error = passError
                etPass.requestFocus()
             return@setOnClickListener
            }

            // Все проверки пройдены
            etServer.error = null
            etUser.error = null
            etPass.error = null
    
            connectionVM.saveSettings(server, user, pass, authType)
            connectionVM.connect(server, user, pass)
        }   

        btnBack.setOnClickListener {
            val (server, user, pass) = connectionVM.loadSettings()
            browserVM.navigateBack(server, user, pass)
        }


        btnNewFolder.setOnClickListener {
            val input = EditText(this).apply {
                hint = "Имя папки"
                setPadding(48, 24, 48, 24)
                setTextColor(0xFFFFFFFF.toInt())
                setHintTextColor(0xFF888888.toInt())
                setBackgroundResource(R.drawable.bg_input_dark)
                filters = arrayOf(android.text.InputFilter.LengthFilter(255))  // ✅ Ограничение длины
            }
            AlertDialog.Builder(this)
                .setTitle("Создать папку")
                .setView(input)
                .setPositiveButton("Создать") { _, _ ->
                    val name = input.text.toString().trim()
                    // ✅ Валидация
                    val error = Validators.validateFolderName(name)
                    if (error != null) {
                        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    val (server, user, pass) = connectionVM.loadSettings()
                    browserVM.createFolder(server, name, user, pass)
                }
                .setNegativeButton("Отмена", null).show()
        }

        findViewById<Button>(R.id.btnPick).setOnClickListener { picker.launch(arrayOf("*/*")) }

        findViewById<Button>(R.id.btnUpload).setOnClickListener {
            val (server, user, pass) = connectionVM.loadSettings()
            if (server.isBlank()) { browserVM.log("Ошибка: сервер не подключён"); return@setOnClickListener }
            if (picked.isEmpty()) { browserVM.log("Нет выбранных файлов"); return@setOnClickListener }
            showServerFolderPicker(server, user, pass) { targetPath ->
                browserVM.uploadFilesToPath(server, user, pass, picked, targetPath)
            }
        }

        findViewById<Button>(R.id.btnSelDownload).setOnClickListener {
            val (server, user, pass) = connectionVM.loadSettings()
            browserVM.downloadSelected(server, user, pass)
        }

        findViewById<Button>(R.id.btnSelDelete).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Удалить элементы?")
                .setMessage("Выбрано: ${browserVM.getSelectedCount()}")
                .setPositiveButton("Удалить") { _, _ ->
                    val (server, user, pass) = connectionVM.loadSettings()
                    browserVM.deleteSelected(server, user, pass)
                }
                .setNegativeButton("Отмена", null).show()
        }

        findViewById<Button>(R.id.btnSelCancel).setOnClickListener { browserVM.exitSelectionMode() }
    }

    // ==================== File Clicks ====================

    private fun handleFileClick(item: WebDavRepository.FileInfo, position: Int) {
        val state = browserVM.state.value ?: return

        if (state.selectionMode) {
            browserVM.toggleSelection(position)
        } else if (item.isDirectory) {
            val (server, user, pass) = connectionVM.loadSettings()
            if (server.isNotBlank()) {
                browserVM.browseServer(server, item.path, user, pass)
            } else {
                browserVM.log("Ошибка: сервер не подключён")
            }
        } else if (FileUtils.isImageFile(item.name)) {
            browserVM.openImagePager(item)
        } else if (FileUtils.isVideoFile(item.name)) {
            browserVM.viewVideo(this, item)
        } else {
            val (server, user, pass) = connectionVM.loadSettings()
            browserVM.downloadFile(server, item.path, item.name, user, pass)
        }
    }

    // ==================== Folder Picker ====================

    private fun showServerFolderPicker(server: String, user: String, pass: String, onFolderSelected: (String) -> Unit) {
        val base = WebDavRepository.normalizeBaseUrl(server)
        if (base == null) { browserVM.log("Ошибка: неверный адрес сервера"); return }
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
                browserVM.log("Ошибка загрузки списка папок: ${e.message}")
            }
        }
    }

    // ==================== Tabs ====================

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
                val (server, user, pass) = connectionVM.loadSettings()
                val state = browserVM.state.value
                if (server.isNotBlank() && state?.files.isNullOrEmpty() && state?.isLoading != true) {
                    browserVM.browseServer(server, WebDavRepository.getServerPath(server), user, pass)
                }
            }
            2 -> {
                tabTasks.visibility = View.VISIBLE
                btnTabTasks.setBackgroundResource(R.drawable.bg_button_primary)
                btnTabTasks.setTextColor(0xFF000000.toInt())
            }
            3 -> {
                tabHistory.visibility = View.VISIBLE
                btnTabHistory.setBackgroundResource(R.drawable.bg_button_primary)
                btnTabHistory.setTextColor(0xFF000000.toInt())
                historyVM.refreshHistory()
            }
        }
    }

    // ==================== Image Pager ====================

    private fun showImagePager(startIndex: Int) {
        val (server, user, pass) = connectionVM.loadSettings()
        val fragment = ImagePagerFragment.newInstance(startIndex, server, user, pass)
        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, fragment)
            .addToBackStack(null)
            .commit()
    }

    // ==================== Settings ====================

    private fun loadSettings() {
        val (server, user, pass) = connectionVM.loadSettings()
        val authType = SecurePrefs.loadAuthType(this)
        etServer.setText(server)
        etUser.setText(user)
        etPass.setText(pass)
        spAuth.setSelection(authType)
        if (server.isNotEmpty()) connectionVM.log("Настройки загружены")
    }

    // ==================== Permissions ====================

    private fun requestMediaPermissions() {
        val permissionsNeeded = mutableListOf<String>()
        when {
            Build.VERSION.SDK_INT >= 34 -> {
                if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED)
                    permissionsNeeded.add(Manifest.permission.READ_MEDIA_IMAGES)
                if (checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED)
                    permissionsNeeded.add(Manifest.permission.READ_MEDIA_VIDEO)
                if (checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) != PackageManager.PERMISSION_GRANTED)
                    permissionsNeeded.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            }
            Build.VERSION.SDK_INT >= 33 -> {
                if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED)
                    permissionsNeeded.add(Manifest.permission.READ_MEDIA_IMAGES)
                if (checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED)
                    permissionsNeeded.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
            Build.VERSION.SDK_INT >= 23 -> {
                if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                    permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                    permissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        if (permissionsNeeded.isNotEmpty()) mediaPermissionLauncher.launch(permissionsNeeded.toTypedArray())
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            when {
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED -> {}
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    AlertDialog.Builder(this)
                        .setTitle("Уведомления")
                        .setMessage("Приложение отправляет уведомления о результатах синхронизации. Разрешить?")
                        .setPositiveButton("Разрешить") { _, _ ->
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        .setNegativeButton("Не сейчас", null)
                        .show()
                }
                else -> notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun promptExactAlarms() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(AlarmManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= 34 && am.canScheduleExactAlarms()) return
            if (!am.canScheduleExactAlarms()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    try {
                        exactAlarmSettingsLauncher.launch(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
                    } catch (_: Exception) {
                        try {
                            exactAlarmSettingsLauncher.launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    private fun checkExactAlarmStatus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(AlarmManager::class.java) ?: return
            if (!am.canScheduleExactAlarms()) {
                connectionVM.log("⚠ Точные будильники отозваны")
            }
        }
    }

    private fun promptBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(android.os.PowerManager::class.java)
            if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    startActivity(Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")))
                } catch (e: Exception) {
                    try {
                        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    } catch (_: Exception) {}
                }
            }
        }
    }
}