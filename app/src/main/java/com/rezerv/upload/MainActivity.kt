package com.rezerv.upload

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.rezerv.upload.navigation.NavigationManager
import com.rezerv.upload.permissions.PermissionHandler
import com.rezerv.upload.ui.ConnectionFormHandler
import com.rezerv.upload.ui.FileBrowserHandler
import com.rezerv.upload.ui.HistoryCardBuilder
import com.rezerv.upload.ui.TabManager
import com.rezerv.upload.ui.WebDavFolderPickerDialog
import com.rezerv.upload.ui.compose.TasksTab
import com.rezerv.upload.ui.theme.RezervTheme
import com.rezerv.upload.viewmodel.BrowserViewModel
import com.rezerv.upload.viewmodel.ConnectionViewModel
import com.rezerv.upload.viewmodel.HistoryViewModel
import com.rezerv.upload.viewmodel.TasksViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), WebDavFolderPickerDialog.FolderPickerListener {

    companion object {
        private const val ANIMATION_DURATION_MS = 300L
        private const val PADDING_DP = 48
        private const val MAX_FOLDER_NAME_LENGTH = 255
    }

    @Inject lateinit var navigationManager: NavigationManager
    @Inject lateinit var permissionHandler: PermissionHandler

    // ViewModels
    private val connectionVM: ConnectionViewModel by viewModels()
    private val browserVM: BrowserViewModel by viewModels()
    private val tasksVM: TasksViewModel by viewModels()
    private val historyVM: HistoryViewModel by viewModels()

    // Handlers
    private lateinit var tabManager: TabManager
    private lateinit var connectionForm: ConnectionFormHandler
    private lateinit var fileBrowser: FileBrowserHandler

    // UI Components
    private lateinit var tvLog: TextView
    private lateinit var tabTasks: androidx.compose.ui.platform.ComposeView
    private lateinit var historyContainer: LinearLayout
    private lateinit var tvHistoryEmpty: TextView
    private lateinit var historyChart: HistoryChartView
    private lateinit var skeletonHistory: View

    // Состояние
    private var picked: List<android.net.Uri> = emptyList()
    private var isHistoryFirstLoad = true
    private var pendingUploadCallback: ((String) -> Unit)? = null

    // Permission Launchers
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) connectionVM.log(getString(R.string.notifications_allowed))
    }

    private val mediaPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    private val exactAlarmSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { connectionVM.ensureScheduler() }

    private val picker = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        picked = uris
        picked.forEach { uri ->
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                Timber.w(e, "Permission error for $uri")
            }
        }
        browserVM.log(getString(R.string.files_selected, picked.size))
    }

    // ==================== Lifecycle ====================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        loadSettings()
        observeViewModels()
        requestPermissions()
        connectionVM.ensureScheduler()
    }

    override fun onResume() {
        super.onResume()
        if (tabManager.isTabVisible(3)) historyVM.refreshHistory()
        checkExactAlarmStatus()
    }

    // ==================== Initialization ====================

    private fun initViews() {
        // Tabs
        tabManager = TabManager(
            tabs = mapOf(
                0 to findViewById<ScrollView>(R.id.tabConnection),
                1 to findViewById<View>(R.id.tabBrowser),
                2 to findViewById<androidx.compose.ui.platform.ComposeView>(R.id.tabTasks)
                    .also { tabTasks = it },
                3 to findViewById<ScrollView>(R.id.tabHistory)
            ),
            buttons = mapOf(
                0 to findViewById(R.id.btnTabConnection),
                1 to findViewById(R.id.btnTabBrowser),
                2 to findViewById(R.id.btnTabTasks),
                3 to findViewById(R.id.btnTabHistory)
            )
        )

        // Connection form (Spinner настраивается внутри)
        connectionForm = ConnectionFormHandler(
            context = this,
            etServer = findViewById(R.id.etServer),
            etUser = findViewById(R.id.etUser),
            etPass = findViewById(R.id.etPass),
            spAuth = findViewById(R.id.spAuth)
        )

        // File browser (с передачей browserVM для DragSelectionListener)
        fileBrowser = FileBrowserHandler(
            context = this,
            browserVM = browserVM,
            rvFiles = findViewById(R.id.rvFiles),
            skeletonFiles = findViewById(R.id.skeletonFiles),
            tvCurrentPath = findViewById(R.id.tvCurrentPath),
            llSelection = findViewById(R.id.llSelection),
            tvSelectionCount = findViewById(R.id.tvSelectionCount)
        )

        fileBrowser.setupAdapter(
            serverUrl = { connectionVM.loadSettings().first },
            user = { connectionVM.loadSettings().second },
            pass = { connectionVM.loadSettings().third },
            onItemClick = { item, position -> handleFileClick(item, position) }
        )

        // Остальные компоненты
        tvLog = findViewById(R.id.tvLog)
        historyChart = findViewById(R.id.historyChart)
        historyContainer = findViewById(R.id.historyContainer)
        tvHistoryEmpty = findViewById(R.id.tvHistoryEmpty)
        skeletonHistory = findViewById(R.id.skeletonHistory)
    }

    private fun setupListeners() {
        // Tabs
        findViewById<Button>(R.id.btnTabConnection).setOnClickListener { switchToTab(0) }
        findViewById<Button>(R.id.btnTabBrowser).setOnClickListener { switchToTab(1) }
        findViewById<Button>(R.id.btnTabTasks).setOnClickListener { switchToTab(2) }
        findViewById<Button>(R.id.btnTabHistory).setOnClickListener { switchToTab(3) }

        // Connection
        findViewById<Button>(R.id.btnConnect).setOnClickListener { handleConnect() }

        // Browser
        findViewById<Button>(R.id.btnBack).setOnClickListener {
            val (server, user, pass) = connectionVM.loadSettings()
            browserVM.navigateBack(server, user, pass)
        }
        findViewById<Button>(R.id.btnNewFolder).setOnClickListener { showNewFolderDialog() }
        findViewById<Button>(R.id.btnPick).setOnClickListener { picker.launch(arrayOf("*/*")) }
        findViewById<Button>(R.id.btnUpload).setOnClickListener { handleUpload() }
        findViewById<Button>(R.id.btnSelDownload).setOnClickListener {
            val (server, user, pass) = connectionVM.loadSettings()
            browserVM.downloadSelected(server, user, pass)
        }
        findViewById<Button>(R.id.btnSelDelete).setOnClickListener { handleDelete() }
        findViewById<Button>(R.id.btnSelCancel).setOnClickListener { browserVM.exitSelectionMode() }

        // History
        findViewById<Button>(R.id.btnClearHistory).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.clear_history)
                .setPositiveButton(R.string.clear) { _, _ -> historyVM.clearHistory() }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    // ==================== Permissions ====================

    private fun requestPermissions() {
        permissionHandler.getRequiredMediaPermissions(this).takeIf { it.isNotEmpty() }?.let {
            mediaPermissionLauncher.launch(it.toTypedArray())
        }

        if (permissionHandler.shouldRequestNotifications(this)) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissionHandler.needsExactAlarmPermission(this)) {
            exactAlarmSettingsLauncher.launch(permissionHandler.createExactAlarmIntent(this))
        }

        if (permissionHandler.needsBatteryOptimizationExemption(this)) {
            try {
                startActivity(permissionHandler.createBatteryOptimizationIntent(this))
            } catch (e: Exception) {
                Timber.w(e, "Cannot open battery settings")
                try {
                    startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } catch (e2: Exception) {
                    Timber.w(e2, "Cannot open battery settings fallback")
                }
            }
        }
    }

    private fun checkExactAlarmStatus() {
        if (permissionHandler.needsExactAlarmPermission(this)) {
            connectionVM.log(getString(R.string.exact_alarms_revoked))
        }
    }

    // ==================== ViewModel Observers ====================

    private fun observeViewModels() {
        observeConnectionVM()
        observeBrowserVM()
        observeTasksVM()
        observeHistoryVM()
    }

    private fun observeConnectionVM() {
        connectionVM.log.observe(this) { tvLog.text = it }
        connectionVM.events.observe(this) { handleConnectionEvent(it) }
    }

    private fun observeBrowserVM() {
        browserVM.state.observe(this) { state ->
            fileBrowser.updateFileList(state)
            fileBrowser.updateSelectionUI(state.selectionMode, state.selectedIndices.size)
            fileBrowser.updateCurrentPath(state.currentPath)
        }
        browserVM.log.observe(this) { tvLog.text = it }
        browserVM.events.observe(this) { handleBrowserEvent(it) }
    }

    private fun observeTasksVM() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                tasksVM.tasks
                    .filterNotNull()
                    .collect { tasks -> refreshTasks(tasks) }
            }
        }
    }

    private fun observeHistoryVM() {
        historyVM.records.observe(this) { refreshHistory(it) }
    }

    private fun handleConnectionEvent(event: ConnectionViewModel.ConnectionEvent) {
        when (event) {
            is ConnectionViewModel.ConnectionEvent.Connected -> {
                switchToTab(1)
                val (server, user, pass) = connectionVM.loadSettings()
                browserVM.browseServer(server, event.serverPath, user, pass)
            }
            is ConnectionViewModel.ConnectionEvent.ConnectionFailed -> {
                Toast.makeText(
                    this,
                    "${getString(R.string.connection_failed)}: ${event.error}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun handleBrowserEvent(event: BrowserViewModel.BrowserEvent) {
        when (event) {
            is BrowserViewModel.BrowserEvent.ShowToast -> {
                Toast.makeText(this, event.message, Toast.LENGTH_SHORT).show()
                if (event.message.startsWith("Загрузка:")) picked = emptyList()
            }
            is BrowserViewModel.BrowserEvent.OpenImagePager -> showImagePager(event.startIndex)
            is BrowserViewModel.BrowserEvent.UploadCompleted -> {
                historyVM.refreshHistory()
                switchToTab(3)
            }
        }
    }

    // ==================== UI Updates ====================

    private fun refreshTasks(tasks: List<SyncTask>) {
        tabTasks.setContent {
            RezervTheme {
                TasksTab(
                    onTaskClick = { taskId ->
                        navigationManager.openTaskDetails(this, taskId)
                    },
                    onCreateTask = {
                        navigationManager.openCreateTask(this)
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

        if (skeletonHistory.visibility == View.VISIBLE) {
            historyContainer.alpha = 0f
            historyContainer.visibility = View.VISIBLE
            historyContainer.animate()
                .alpha(1f)
                .setDuration(ANIMATION_DURATION_MS)
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
                navigationManager.openHistoryDetails(this@MainActivity, record.time)
            }
        }
        records.forEach { historyContainer.addView(historyBuilder.build(it, listener)) }
    }

    // ==================== Actions ====================

    private fun handleConnect() {
        val (server, user, pass) = connectionForm.getCredentials()
        val authType = connectionForm.getAuthType()

        if (!connectionForm.validate()) return
        connectionForm.clearErrors()

        connectionVM.saveSettings(server, user, pass, authType)
        connectionVM.connect(server, user, pass)
    }

    private fun showNewFolderDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.folder_name)
            val paddingPx = (PADDING_DP * resources.displayMetrics.density).toInt()
            setPadding(paddingPx, paddingPx / 2, paddingPx, paddingPx / 2)
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF888888.toInt())
            setBackgroundResource(R.drawable.bg_input_dark)
            filters = arrayOf(android.text.InputFilter.LengthFilter(MAX_FOLDER_NAME_LENGTH))
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.create_folder)
            .setView(input)
            .setPositiveButton(R.string.create) { _, _ ->
                val name = input.text.toString().trim()
                com.rezerv.upload.utils.Validators.validateFolderName(name)?.let { error ->
                    Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val (server, user, pass) = connectionVM.loadSettings()
                browserVM.createFolder(server, name, user, pass)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun handleUpload() {
        val (server, user, pass) = connectionVM.loadSettings()

        when {
            server.isBlank() -> browserVM.log(getString(R.string.server_not_connected))
            picked.isEmpty() -> browserVM.log(getString(R.string.no_files_selected))
            else -> {
                pendingUploadCallback = { targetPath ->
                    browserVM.uploadFilesToPath(server, user, pass, picked, targetPath)
                }
                showServerFolderPicker(server, user, pass)
            }
        }
    }

    private fun handleDelete() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_items)
            .setMessage(getString(R.string.selected_count, browserVM.getSelectedCount()))
            .setPositiveButton(R.string.delete) { _, _ ->
                val (server, user, pass) = connectionVM.loadSettings()
                browserVM.deleteSelected(server, user, pass)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun handleFileClick(item: WebDavRepository.FileInfo, position: Int) {
        val currentState = browserVM.state.value
        if (currentState == null) {
            Timber.w("Browser state is null during file click")
            return
        }

        when {
            currentState.selectionMode -> browserVM.toggleSelection(position)
            item.isDirectory -> {
                val (server, user, pass) = connectionVM.loadSettings()
                if (server.isNotBlank()) {
                    browserVM.browseServer(server, item.path, user, pass)
                } else {
                    browserVM.log(getString(R.string.server_not_connected))
                }
            }
            FileUtils.isImageFile(item.name) -> browserVM.openImagePager(item)
            FileUtils.isVideoFile(item.name) -> browserVM.viewVideo(this, item)
            else -> {
                val (server, user, pass) = connectionVM.loadSettings()
                browserVM.downloadFile(server, item.path, item.name, user, pass)
            }
        }
    }

    // ==================== Folder Picker (без рекурсии) ====================

    private fun showServerFolderPicker(server: String, user: String, pass: String) {
        val base = WebDavRepository.normalizeBaseUrl(server)
        if (base == null) {
            browserVM.log("Ошибка: неверный адрес сервера")
            return
        }

        val root = WebDavRepository.getServerPath(server)
        val dialog = WebDavFolderPickerDialog.newInstance(base, root, user, pass)
        dialog.show(supportFragmentManager, "folder_picker")
    }

    override fun onFolderSelected(path: String) {
        pendingUploadCallback?.invoke(path)
        pendingUploadCallback = null
    }

    override fun onFolderPickerCancelled() {
        pendingUploadCallback = null
    }

    // ==================== Tabs ====================

    private fun switchToTab(tab: Int) {
        tabManager.switchTo(tab) { tabNum ->
            when (tabNum) {
                1 -> loadBrowserIfNeeded()
                3 -> historyVM.refreshHistory()
            }
        }
    }

    private fun loadBrowserIfNeeded() {
        val (server, user, pass) = connectionVM.loadSettings()
        val state = browserVM.state.value

        if (server.isNotBlank() && state?.files.isNullOrEmpty() && state?.isLoading != true) {
            browserVM.browseServer(server, WebDavRepository.getServerPath(server), user, pass)
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
        connectionForm.setCredentials(server, user, pass, authType)

        if (server.isNotEmpty()) {
            connectionVM.log(getString(R.string.settings_loaded))
        }
    }
}