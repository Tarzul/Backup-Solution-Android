package com.rezerv.upload

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dagger.hilt.android.AndroidEntryPoint
import com.rezerv.upload.utils.Validators
import com.rezerv.upload.data.TaskRepository
import com.rezerv.upload.data.SyncScheduler
import javax.inject.Inject

@AndroidEntryPoint
class TaskWizardActivity : AppCompatActivity() {
    @Inject lateinit var taskRepository: TaskRepository   // ✅
    @Inject lateinit var syncScheduler: SyncScheduler     // ✅
    private var editTaskId: String? = null
    private var editingTask: SyncTask? = null
    private var currentStep = 0
    private var syncType = "two_way"
    private var leftIsWebdav = false
    private var leftLocalUri = ""
    private var leftWebdavPath = ""
    private var rightIsWebdav = true
    private var rightLocalUri = ""
    private var rightWebdavPath = "/"
    private var scheduleEnabled = true
    private var scheduleMode = "daily"
    private var intervalValue = 15
    private var hour = 3
    private var minute = 0
    private val selectedWeekDays = mutableSetOf<Int>()
    private val selectedMonthDays = mutableSetOf<Int>()
    private var useWifi = true
    private var useMobile = false
    private var onlyCharging = false
    private var notifyOnSuccess = false
    private var notifyOnError = true
    private var taskName = ""
    private var intervalOptions = arrayOf("5", "10", "15", "30", "60")
    private var startStep = 0
    private var editSingleStep = false
    private var editSection = ""   // НОВОЕ: "plan" | "sync" | ""
    private lateinit var flipper: ViewFlipper
    private lateinit var tvTitle: TextView
    private lateinit var btnBack: Button
    private lateinit var btnNext: Button

    private val localFolderPicker = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            } catch (_: Exception) {}
            if (currentStep == 1) {
                leftLocalUri = uri.toString()
                findViewById<TextView>(R.id.tvLeftSelected).text = "📱 ${shortUri(uri.toString())}"
            } else if (currentStep == 2) {
                rightLocalUri = uri.toString()
                findViewById<TextView>(R.id.tvRightSelected).text = "📱 ${shortUri(uri.toString())}"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_wizard)
        editTaskId = intent.getStringExtra("taskId")
        startStep = intent.getIntExtra("startStep", 0)
        editSingleStep = intent.getBooleanExtra("singleStep", false)
        editSection = intent.getStringExtra("section") ?: ""

        initViews(); setupStep1(); setupStep2(); setupStep3(); setupStep4(); setupStep5()
        updateNavigation()

        // ✅ Загружаем задание из Room асинхронно
        if (!editTaskId.isNullOrBlank()) {
            lifecycleScope.launch {
                editingTask = withContext(Dispatchers.IO) { taskRepository.getTaskById(editTaskId!!) }
                editingTask?.let { restoreFromTask(it) }
                editSingleStep = editSingleStep && editingTask != null

                tvTitle.text = if (editingTask != null) "Редактировать задание" else "Создать задание"
                setupStep1(); setupStep2(); setupStep3(); setupStep4(); setupStep5()

                if (editSingleStep) {
                    currentStep = startStep.coerceIn(0, 4)
                    flipper.displayedChild = currentStep
                    if (currentStep == 4) updateSummary()
                    if (currentStep == 3) {
                        findViewById<LinearLayout>(R.id.llPlanSection).visibility =
                            if (editSection == "sync") View.GONE else View.VISIBLE
                        findViewById<LinearLayout>(R.id.llSyncSection).visibility =
                            if (editSection == "plan") View.GONE else View.VISIBLE
                        tvTitle.text = when (editSection) {
                            "plan" -> "Планирование"
                            "sync" -> "Дополнительно"
                            else -> "Редактировать задание"
                        }
                    }
                }
                updateNavigation()
            }
        }
    }

    private fun restoreFromTask(t: SyncTask) {
        syncType = t.syncType
        leftIsWebdav = t.leftIsWebdav; leftLocalUri = t.leftLocalUri; leftWebdavPath = t.leftWebdavPath
        rightIsWebdav = t.rightIsWebdav; rightLocalUri = t.rightLocalUri; rightWebdavPath = t.rightWebdavPath
        scheduleEnabled = t.scheduleEnabled; scheduleMode = t.scheduleMode
        intervalValue = t.intervalValue; hour = t.hour; minute = t.minute
        selectedWeekDays.clear(); selectedWeekDays.addAll(t.weekDays.split(",").mapNotNull { it.toIntOrNull() })
        selectedMonthDays.clear(); selectedMonthDays.addAll(t.monthDays.split(",").mapNotNull { it.toIntOrNull() })
        useWifi = t.useWifi; useMobile = t.useMobile; onlyCharging = t.onlyCharging
        notifyOnSuccess = t.notifyOnSuccess; notifyOnError = t.notifyOnError
        taskName = t.name
    }

    private fun initViews() {
        flipper = findViewById(R.id.flipper); tvTitle = findViewById(R.id.tvWizardTitle)
        btnBack = findViewById(R.id.btnWizardBack); btnNext = findViewById(R.id.btnWizardNext)
        tvTitle.text = if (editingTask != null) "Редактировать задание" else "Создать задание"
        btnBack.setOnClickListener {
            if (currentStep == 0 || editSingleStep) finish()
            else { flipper.showPrevious(); currentStep--; updateNavigation() }
        }
        btnNext.setOnClickListener {
            if (!validateCurrentStep()) return@setOnClickListener
            if (editSingleStep || currentStep == 4) saveAndFinish()
            else {
                flipper.showNext(); currentStep++; updateNavigation()
                if (currentStep == 4) updateSummary()
            }
        }
    }

    private fun updateNavigation() {
        btnBack.text = if (currentStep == 0 || editSingleStep) "Отмена" else "Назад"
        btnNext.text = if (currentStep == 4 || editSingleStep) "Сохранить" else "Следующий"
    }

    private fun setupStep1() {
        val cardTwoWay = findViewById<LinearLayout>(R.id.cardTwoWay)
        val cardToRight = findViewById<LinearLayout>(R.id.cardToRight)
        val cardToLeft = findViewById<LinearLayout>(R.id.cardToLeft)
        fun highlight(selected: View) {
            listOf(cardTwoWay, cardToRight, cardToLeft).forEach { it.setBackgroundResource(R.drawable.bg_rounded_dark) }
            selected.setBackgroundResource(R.drawable.bg_button_secondary)
        }
        cardTwoWay.setOnClickListener { syncType = "two_way"; highlight(cardTwoWay) }
        cardToRight.setOnClickListener { syncType = "to_right"; highlight(cardToRight) }
        cardToLeft.setOnClickListener { syncType = "to_left"; highlight(cardToLeft) }
        when (syncType) {
            "two_way" -> highlight(cardTwoWay); "to_right" -> highlight(cardToRight); else -> highlight(cardToLeft)
        }
    }

    private fun setupStep2() {
        findViewById<Button>(R.id.btnLeftLocal).setOnClickListener { leftIsWebdav = false; localFolderPicker.launch(null) }
        findViewById<Button>(R.id.btnLeftWebdav).setOnClickListener {
            leftIsWebdav = true
            pickWebdavFolder { path -> leftWebdavPath = path; findViewById<TextView>(R.id.tvLeftSelected).text = "☁ WebDAV: $path" }
        }
        if (leftIsWebdav && leftWebdavPath.isNotEmpty()) findViewById<TextView>(R.id.tvLeftSelected).text = "☁ WebDAV: $leftWebdavPath"
        else if (!leftIsWebdav && leftLocalUri.isNotEmpty()) findViewById<TextView>(R.id.tvLeftSelected).text = "📱 ${shortUri(leftLocalUri)}"
    }

    private fun setupStep3() {
        findViewById<Button>(R.id.btnRightLocal).setOnClickListener { rightIsWebdav = false; localFolderPicker.launch(null) }
        findViewById<Button>(R.id.btnRightWebdav).setOnClickListener {
            rightIsWebdav = true
            pickWebdavFolder { path -> rightWebdavPath = path; findViewById<TextView>(R.id.tvRightSelected).text = "☁ WebDAV: $path" }
        }
        if (rightIsWebdav && rightWebdavPath.isNotEmpty()) findViewById<TextView>(R.id.tvRightSelected).text = "☁ WebDAV: $rightWebdavPath"
        else if (!rightIsWebdav && rightLocalUri.isNotEmpty()) findViewById<TextView>(R.id.tvRightSelected).text = "📱 ${shortUri(rightLocalUri)}"
    }

    private fun setupStep4() {
        val cbSchedule = findViewById<CheckBox>(R.id.cbSchedule)
        val spScheduleMode = findViewById<Spinner>(R.id.spScheduleMode)
        val btnTime = findViewById<Button>(R.id.btnTime)
        val llWeek = findViewById<LinearLayout>(R.id.llWeek)
        val btnMonthDays = findViewById<Button>(R.id.btnMonthDays)
        cbSchedule.isChecked = scheduleEnabled
        findViewById<CheckBox>(R.id.cbWifi).isChecked = useWifi
        findViewById<CheckBox>(R.id.cbMobile).isChecked = useMobile
        findViewById<CheckBox>(R.id.cbCharging).isChecked = onlyCharging
        findViewById<CheckBox>(R.id.cbNotifyOk).isChecked = notifyOnSuccess
        findViewById<CheckBox>(R.id.cbNotifyErr).isChecked = notifyOnError
        spScheduleMode.adapter = ArrayAdapter(this, R.layout.spinner_item, resources.getStringArray(R.array.schedule_modes)).apply {
            setDropDownViewResource(R.layout.spinner_dropdown_item)
        }
        spScheduleMode.setSelection(when (scheduleMode) {
            "minutes" -> 0; "hourly" -> 1; "daily" -> 2; "weekly" -> 3; "monthly" -> 4; else -> 2 })
        spScheduleMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                scheduleMode = when (pos) { 0 -> "minutes"; 1 -> "hourly"; 2 -> "daily"; 3 -> "weekly"; 4 -> "monthly"; else -> "daily" }
                updateScheduleUI(); refreshIntervals()   // ИСПРАВЛЕНО: интервалы зависят от режима
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        refreshIntervals()
        btnTime.text = "⏰ Время: ${String.format("%02d:%02d", hour, minute)}"
        btnTime.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                hour = h; minute = m
                btnTime.text = "⏰ Время: ${String.format("%02d:%02d", hour, minute)}"
            }, hour, minute, true).show()
        }
        llWeek.removeAllViews()
        val dayNames = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
        for (i in 1..7) {
            llWeek.addView(CheckBox(this).apply {
                text = dayNames[i - 1]; setTextColor(getColor(R.color.text_secondary))
                isChecked = selectedWeekDays.contains(i)
                setOnCheckedChangeListener { _, checked -> if (checked) selectedWeekDays.add(i) else selectedWeekDays.remove(i) }
            })
        }
        btnMonthDays.text = if (selectedMonthDays.isEmpty()) "📅 Числа месяца: не выбраны"
        else "📅 Числа: " + selectedMonthDays.sorted().joinToString(",")
        btnMonthDays.setOnClickListener {
            showMonthDaysPicker {
                btnMonthDays.text = if (selectedMonthDays.isEmpty()) "📅 Числа месяца: не выбраны"
                else "📅 Числа: " + selectedMonthDays.sorted().joinToString(",")
            }
        }
        cbSchedule.setOnCheckedChangeListener { _, checked -> scheduleEnabled = checked }
        findViewById<CheckBox>(R.id.cbWifi).setOnCheckedChangeListener { _, c -> useWifi = c }
        findViewById<CheckBox>(R.id.cbMobile).setOnCheckedChangeListener { _, c -> useMobile = c }
        findViewById<CheckBox>(R.id.cbCharging).setOnCheckedChangeListener { _, c -> onlyCharging = c }
        findViewById<CheckBox>(R.id.cbNotifyOk).setOnCheckedChangeListener { _, c -> notifyOnSuccess = c }
        findViewById<CheckBox>(R.id.cbNotifyErr).setOnCheckedChangeListener { _, c -> notifyOnError = c }
        updateScheduleUI()
    }

    // ИСПРАВЛЕНО: для «ежечасно» — часы (1..24), а не «60 ч»
    private fun refreshIntervals() {
        val spInterval = findViewById<Spinner>(R.id.spInterval)
        intervalOptions = if (scheduleMode == "hourly") arrayOf("1", "2", "3", "6", "12", "24")
        else arrayOf("5", "10", "15", "30", "60")
        spInterval.adapter = ArrayAdapter(this, R.layout.spinner_item, intervalOptions).apply {
            setDropDownViewResource(R.layout.spinner_dropdown_item)
        }
        spInterval.setSelection(intervalOptions.indexOf(intervalValue.toString()).coerceAtLeast(0))
        spInterval.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { intervalValue = intervalOptions[pos].toInt() }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun updateScheduleUI() {
        findViewById<LinearLayout>(R.id.llInterval).visibility =
            if (scheduleMode == "minutes" || scheduleMode == "hourly") View.VISIBLE else View.GONE
        findViewById<Button>(R.id.btnTime).visibility =
            if (scheduleMode in listOf("daily", "weekly", "monthly")) View.VISIBLE else View.GONE
        findViewById<LinearLayout>(R.id.llWeek).visibility = if (scheduleMode == "weekly") View.VISIBLE else View.GONE
        findViewById<Button>(R.id.btnMonthDays).visibility = if (scheduleMode == "monthly") View.VISIBLE else View.GONE
    }

    private fun showMonthDaysPicker(onDone: () -> Unit) {
        val selected = BooleanArray(31) { (it + 1) in selectedMonthDays }
        AlertDialog.Builder(this)
            .setTitle("Выберите числа месяца")
            .setMultiChoiceItems((1..31).map { "$it" }.toTypedArray(), selected) { _, which, checked -> selected[which] = checked }
            .setPositiveButton("OK") { _, _ ->
                selectedMonthDays.clear()
                selected.forEachIndexed { i, v -> if (v) selectedMonthDays.add(i + 1) }
                onDone()
            }
            .setNegativeButton("Отмена", null).show()
    }

    private fun setupStep5() {
        findViewById<EditText>(R.id.etTaskName).setText(taskName.ifBlank {
            val left = if (leftIsWebdav) "WebDAV" else "SD CARD"
            val right = if (rightIsWebdav) "WebDAV" else "SD CARD"
            "$left ⇄ $right"
        })
    }

    private fun updateSummary() {
        taskName = findViewById<EditText>(R.id.etTaskName).text.toString().trim()
        findViewById<TextView>(R.id.tvSummary).text = buildString {
            appendLine("Имя: ${taskName.ifBlank { "—" }}")
            appendLine("Тип: ${syncTypeLabel(syncType)}")
            appendLine("Левая папка: ${folderDesc(leftIsWebdav, leftLocalUri, leftWebdavPath)}")
            appendLine("Правая папка: ${folderDesc(rightIsWebdav, rightLocalUri, rightWebdavPath)}")
            append("Расписание: ")
            if (scheduleEnabled) appendLine(scheduleLabel()) else appendLine("выключено")
        }
    }

    private fun validateCurrentStep(): Boolean {
        when (currentStep) {
            1 -> {
                // Валидация левой папки
                if (leftIsWebdav) {
                    if (leftWebdavPath.isBlank()) {
                        toast("Выберите WebDAV папку слева")
                        return false
                    }
                    val error = Validators.validateWebDavPath(leftWebdavPath)
                    if (error != null) {
                        toast("Левая папка: $error")
                        return false
                    }
                } else {
                    if (leftLocalUri.isBlank()) {
                        toast("Выберите локальную папку слева")
                        return false
                    }
                }
            }
            2 -> {
                // Валидация правой папки
                if (rightIsWebdav) {
                    if (rightWebdavPath.isBlank()) {
                        toast("Выберите WebDAV папку справа")
                        return false
                    }
                    val error = Validators.validateWebDavPath(rightWebdavPath)
                    if (error != null) {
                        toast("Правая папка: $error")
                        return false
                    }
                } else {
                    if (rightLocalUri.isBlank()) {
                        toast("Выберите локальную папку справа")
                        return false
                    }
                }    
            
                // Проверка: ровно одна WebDAV папка
                if (leftIsWebdav == rightIsWebdav) {
                    toast("Одна папка должна быть локальной, другая — WebDAV")
                    return false
                }
            }
            3 -> {
                // Валидация расписания
                if (scheduleEnabled) {
                    // Время (hour и minute уже в переменных)
                    val timeError = Validators.validateTime(hour, minute)
                    if (timeError != null) {
                        toast(timeError)
                        return false
                    }

                    when (scheduleMode) {
                        "minutes" -> {
                            val error = Validators.validateIntervalMinutes(intervalValue)
                            if (error != null) {
                                toast("Интервал: $error")
                                return false
                            }
                        }
                        "hourly" -> {
                            val error = Validators.validateIntervalHours(intervalValue)
                            if (error != null) {
                                toast("Интервал: $error")
                                return false
                            }
                        }
                        "weekly" -> {
                            if (selectedWeekDays.isEmpty()) {
                                toast("Выберите хотя бы один день недели")
                                return false
                            }
                        }   
                        "monthly" -> {
                            if (selectedMonthDays.isEmpty()) {
                                toast("Выберите хотя бы одно число месяца")
                                return false
                            }
                        }
                    }
                }
            }
            4 -> {
                // Валидация имени задания
                val name = findViewById<EditText>(R.id.etTaskName).text.toString().trim()
                val error = Validators.validateTaskName(name)
                if (error != null) {
                    val nameInput = findViewById<EditText>(R.id.etTaskName)
                    nameInput.error = error
                    nameInput.requestFocus()
                    toast(error)
                    return false
                }
            }
        }
        return true
    }

    private fun collectUiState() {
        scheduleEnabled = findViewById<CheckBox>(R.id.cbSchedule).isChecked
        useWifi = findViewById<CheckBox>(R.id.cbWifi).isChecked
        useMobile = findViewById<CheckBox>(R.id.cbMobile).isChecked
        onlyCharging = findViewById<CheckBox>(R.id.cbCharging).isChecked
        notifyOnSuccess = findViewById<CheckBox>(R.id.cbNotifyOk).isChecked
        notifyOnError = findViewById<CheckBox>(R.id.cbNotifyErr).isChecked
        val name = findViewById<EditText>(R.id.etTaskName).text.toString().trim()
        if (name.isNotBlank()) taskName = name
    }

    private fun saveAndFinish() {
        collectUiState()
        val task = SyncTask(
            id = editingTask?.id ?: java.util.UUID.randomUUID().toString(),  // ✅ UUID! Иначе все новые задачи будут с id="" и перезаписывать друг друга в Room
            name = taskName, syncType = syncType,
            leftIsWebdav = leftIsWebdav, leftLocalUri = leftLocalUri, leftWebdavPath = leftWebdavPath,
            rightIsWebdav = rightIsWebdav, rightLocalUri = rightLocalUri, rightWebdavPath = rightWebdavPath,
            scheduleEnabled = scheduleEnabled, scheduleMode = scheduleMode, intervalValue = intervalValue,
            hour = hour, minute = minute,
            weekDays = selectedWeekDays.sorted().joinToString(","),
            monthDays = selectedMonthDays.sorted().joinToString(","),
            useWifi = useWifi, useMobile = useMobile, onlyCharging = onlyCharging,
            notifyOnSuccess = notifyOnSuccess, notifyOnError = notifyOnError,
            lastRun = editingTask?.lastRun ?: 0L, lastStatus = editingTask?.lastStatus ?: ""
        )
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { taskRepository.saveTask(task) }   // ✅ Room
            syncScheduler.scheduleNext(this@TaskWizardActivity)             // ✅ будильники из Room
            toast("Задание сохранено")
            finish()
        }
    }

    // ИСПРАВЛЕНО: навигация по папкам WebDAV с «.. (вверх)» и «Выбрать эту папку» (зам. 19)
    private fun pickWebdavFolder(onPicked: (String) -> Unit) {
        val (server, user, pass) = SecurePrefs.loadCredentials(this)
        if (server.isBlank()) { toast("Сначала подключитесь к серверу"); return }
        val base = WebDavRepository.normalizeBaseUrl(server) ?: return
        val root = WebDavRepository.getServerPath(server)
        showWebdavPicker(base, root, user, pass, root, onPicked)
    }

    private fun showWebdavPicker(base: String, root: String, user: String, pass: String, current: String, onPicked: (String) -> Unit) {
        lifecycleScope.launch {
            try {
                val files = withContext(Dispatchers.IO) { WebDavRepository.listFiles(base, current, user, pass) }
                val folders = files.filter { it.isDirectory }.map { it.name }.sorted()
                val items = mutableListOf<String>()
                if (current != root) items.add(".. (вверх)")
                items.addAll(folders)
                withContext(Dispatchers.Main) {
                    if (isFinishing || isDestroyed) return@withContext
                    AlertDialog.Builder(this@TaskWizardActivity)
                        .setTitle("Папка: $current")
                        .setItems(items.toTypedArray()) { _, which ->
                            val sel = items[which]
                            if (sel == ".. (вверх)") {
                                val trimmed = current.trimEnd('/')
                                val ls = trimmed.lastIndexOf('/')
                                showWebdavPicker(base, root, user, pass, if (ls <= 0) root else trimmed.substring(0, ls + 1), onPicked)
                            } else showWebdavPicker(base, root, user, pass, current.trimEnd('/') + "/" + sel + "/", onPicked)
                        }
                        .setPositiveButton("Выбрать эту папку") { _, _ -> onPicked(current) }
                        .setNegativeButton("Отмена", null).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { toast("Ошибка: ${e.message}") }
            }
        }
    }

    private fun shortUri(uri: String): String {
        val decoded = try { java.net.URLDecoder.decode(uri, "UTF-8") } catch (e: Exception) { uri }
        val last = decoded.substringAfterLast('/').substringAfterLast(':')
        return if (last.length > 30) last.takeLast(27) + "..." else last
    }

    private fun syncTypeLabel(t: String): String = when (t) {
        "two_way" -> "⇄ Двусторонняя"; "to_left" -> "← В левую папку"; "to_right" -> "→ В правую папку"; else -> t
    }

    private fun folderDesc(isWebdav: Boolean, localUri: String, webPath: String): String =
        if (isWebdav) "☁ WebDAV: $webPath" else "📱 ${shortUri(localUri)}"

    private fun scheduleLabel(): String = when (scheduleMode) {
        "minutes" -> "каждые $intervalValue мин"
        "hourly" -> "каждые $intervalValue ч"
        "daily" -> String.format("ежедневно %02d:%02d", hour, minute)
        "weekly" -> String.format("еженедельно %02d:%02d", hour, minute)
        "monthly" -> String.format("ежемесячно %02d:%02d", hour, minute)
        else -> scheduleMode
    }

    private fun toast(msg: String) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
}