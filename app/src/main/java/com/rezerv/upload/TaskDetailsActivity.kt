package com.rezerv.upload

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TaskDetailsActivity : AppCompatActivity() {

    private var currentTask: SyncTask? = null

    private lateinit var tvDetName: TextView
    private lateinit var tvDetTitle: TextView
    private lateinit var tvDetStatusIcon: TextView
    private lateinit var tvDetType: TextView
    private lateinit var tvDetLastRun: TextView
    private lateinit var tvDetSchedule: TextView
    private lateinit var tvDetSyncType: TextView
    private lateinit var tvDetSyncTypeDesc: TextView
    private lateinit var tvDetLeftLabel: TextView
    private lateinit var tvDetLeftPath: TextView
    private lateinit var tvDetRightLabel: TextView
    private lateinit var tvDetRightPath: TextView
    private lateinit var tvPlanSchedule: TextView
    private lateinit var tvPlanDays: TextView
    private lateinit var tvSyncNetwork: TextView
    private lateinit var tvSyncNotify: TextView
    private lateinit var panelMain: LinearLayout
    private lateinit var panelPlan: LinearLayout
    private lateinit var panelSync: LinearLayout
    private lateinit var chipMain: Button
    private lateinit var chipPlan: Button
    private lateinit var chipSync: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_details)
        val taskId = intent.getStringExtra("taskId")
        if (taskId.isNullOrEmpty()) {
            Toast.makeText(this, "Ошибка: ID задания не найден", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        initViews()
        setupClickListeners()
        loadTask()
    }

    override fun onResume() {
        super.onResume()
        loadTask()
    }

    private fun loadTask() {
        val taskId = intent.getStringExtra("taskId") ?: return
        lifecycleScope.launch {
            currentTask = withContext(Dispatchers.IO) { TaskManager.getById(this@TaskDetailsActivity, taskId) }
            if (currentTask == null) {
                Toast.makeText(this@TaskDetailsActivity, "Задание не найдено", Toast.LENGTH_SHORT).show()
                finish()
            } else updateUI()
        }
    }

    private fun initViews() {
        tvDetName = findViewById(R.id.tvDetName); tvDetTitle = findViewById(R.id.tvDetTitle)
        tvDetStatusIcon = findViewById(R.id.tvDetStatusIcon); tvDetType = findViewById(R.id.tvDetType)
        tvDetLastRun = findViewById(R.id.tvDetLastRun); tvDetSchedule = findViewById(R.id.tvDetSchedule)
        tvDetSyncType = findViewById(R.id.tvDetSyncType); tvDetSyncTypeDesc = findViewById(R.id.tvDetSyncTypeDesc)
        tvDetLeftLabel = findViewById(R.id.tvDetLeftLabel); tvDetLeftPath = findViewById(R.id.tvDetLeftPath)
        tvDetRightLabel = findViewById(R.id.tvDetRightLabel); tvDetRightPath = findViewById(R.id.tvDetRightPath)
        tvPlanSchedule = findViewById(R.id.tvPlanSchedule); tvPlanDays = findViewById(R.id.tvPlanDays)
        tvSyncNetwork = findViewById(R.id.tvSyncNetwork); tvSyncNotify = findViewById(R.id.tvSyncNotify)
        panelMain = findViewById(R.id.panelMain); panelPlan = findViewById(R.id.panelPlan); panelSync = findViewById(R.id.panelSync)
        chipMain = findViewById(R.id.chipMain); chipPlan = findViewById(R.id.chipPlan); chipSync = findViewById(R.id.chipSync)
    }

    private fun setupClickListeners() {
        findViewById<ImageButton>(R.id.btnDetBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnDetRun).setOnClickListener { runTaskNow() }
        findViewById<ImageButton>(R.id.btnDetCopy).setOnClickListener { copyTask() }
        findViewById<ImageButton>(R.id.btnDetDelete).setOnClickListener { confirmDelete() }

        // Карандаш в шапке — полный мастер с начала
        findViewById<ImageButton>(R.id.btnDetEdit).setOnClickListener { openWizard(0, singleStep = false) }

        // ВОССТАНОВЛЕНО: переключение панелей «Основные / Планирование / Дополнительно»
        chipMain.setOnClickListener { switchTab(0) }
        chipPlan.setOnClickListener { switchTab(1) }
        chipSync.setOnClickListener { switchTab(2) }

        // Кнопки у пунктов — сразу нужный шаг + кнопка «Сохранить»
        findViewById<Button>(R.id.btnEditType).setOnClickListener { openWizard(0, singleStep = true) }
        findViewById<Button>(R.id.btnEditLeft).setOnClickListener { openWizard(1, singleStep = true) }
        findViewById<Button>(R.id.btnEditRight).setOnClickListener { openWizard(2, singleStep = true) }
        findViewById<Button>(R.id.btnEditPlan).setOnClickListener { openWizard(3, singleStep = true, section = "plan") }
        findViewById<Button>(R.id.btnEditSync).setOnClickListener { openWizard(3, singleStep = true, section = "sync") }
    }

    private fun openWizard(step: Int, singleStep: Boolean, section: String = "") {
        currentTask?.let {
            startActivity(Intent(this, TaskWizardActivity::class.java)
                .putExtra("taskId", it.id)
                .putExtra("startStep", step)
                .putExtra("singleStep", singleStep)
                .putExtra("section", section))
        }
    }

    private fun updateUI() {
        val t = currentTask ?: return
        val name = t.name.ifEmpty { "Без имени" }
        tvDetName.text = name
        tvDetTitle.text = name
        tvDetStatusIcon.text = when (t.lastStatus) { "ok" -> "✔"; "error" -> "✖"; else -> "•" }
        tvDetStatusIcon.setTextColor(ContextCompat.getColor(this, when (t.lastStatus) {
            "ok" -> R.color.success_green; "error" -> R.color.error_red; else -> R.color.hint_gray }))
        val typeText = when (t.syncType) {
            "two_way" -> "⇄ Двусторонняя"; "to_left" -> "← В левую папку"; else -> "→ В правую папку" }
        tvDetType.text = typeText
        tvDetSyncType.text = typeText
        tvDetSyncTypeDesc.text = when (t.syncType) {
            "two_way" -> "Синхронизирует изменения в обе стороны."
            "to_left" -> "Скачивает новые/изменённые файлы с WebDAV на устройство."
            else -> "Загружает новые/изменённые файлы с устройства на WebDAV." }
        tvDetLastRun.text = if (t.lastRun > 0) "📅 Последний запуск: ${formatDateTime(t.lastRun)}" else "📅 Ещё не запускалось"
        tvDetSchedule.text = if (t.scheduleEnabled) "⏰ ${t.scheduleLabel()}" else "⏰ Отключено"
        tvPlanSchedule.text = if (t.scheduleEnabled) t.scheduleLabel() else "Отключено"
        tvDetLeftLabel.text = if (t.leftIsWebdav) "WebDAV" else "Память устройства"
        tvDetLeftPath.text = if (t.leftIsWebdav) t.leftWebdavPath.ifEmpty { "/" } else t.leftLocalUri.ifEmpty { "Не выбрана" }
        tvDetRightLabel.text = if (t.rightIsWebdav) "WebDAV" else "Память устройства"
        tvDetRightPath.text = if (t.rightIsWebdav) t.rightWebdavPath.ifEmpty { "/" } else t.rightLocalUri.ifEmpty { "Не выбрана" }
        tvPlanDays.text = when (t.scheduleMode) {
            "weekly" -> "Дни недели: " + t.weekDays.split(",").mapNotNull { it.toIntOrNull() }
                .mapNotNull { listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").getOrNull(it - 1) }
                .joinToString(", ").ifEmpty { "не выбраны" }
            "monthly" -> "Числа: " + t.monthDays.ifEmpty { "не выбраны" }
            else -> "Дни: каждый день" }
        tvSyncNetwork.text = "Wi-Fi: ${if (t.useWifi) "вкл" else "выкл"}\n" +
                "Мобильная сеть: ${if (t.useMobile) "вкл" else "выкл"}\n" +
                "Только при зарядке: ${if (t.onlyCharging) "да" else "нет"}"
        tvSyncNotify.text = "Уведомления: " + listOfNotNull(
            if (t.notifyOnSuccess) "при успехе" else null,
            if (t.notifyOnError) "при ошибке" else null).joinToString(", ").ifEmpty { "выкл" }
        switchTab(0)
    }

    private fun runTaskNow() {
        val t = currentTask ?: return
        lifecycleScope.launch {
            val startTime = System.currentTimeMillis()
            
            // НОВОЕ: создаём live-запись СРАЗУ при нажатии ▶ ЗАПУСК
            HistoryManager.createLiveRecord(this@TaskDetailsActivity, startTime, t.name, "user")
            
            Toast.makeText(this@TaskDetailsActivity, "Запуск синхронизации...", Toast.LENGTH_SHORT).show()
            
            val result = withContext(Dispatchers.IO) {
                SyncEngine.runTask(
                    this@TaskDetailsActivity, 
                    t, 
                    trigger = "user",
                    startTime = startTime,   // НОВОЕ
                    onProgress = { },
                    onLiveUpdate = { fileName, fileIndex, totalFiles ->
                        HistoryManager.updateLiveRecord(
                            this@TaskDetailsActivity, startTime, fileName, fileIndex, totalFiles)
                    }
                )
            }
            
            val updated = t.copy(
                lastRun = System.currentTimeMillis(),
                lastStatus = if (result.errors == 0) "ok" else "error")
            withContext(Dispatchers.IO) { TaskManager.upsert(this@TaskDetailsActivity, updated) }
            currentTask = updated
            AlarmScheduler.scheduleNext(this@TaskDetailsActivity)
            
            // Показываем результат и возвращаемся в MainActivity (где видна история с live-прогрессом)
            Toast.makeText(this@TaskDetailsActivity,
                if (result.errors == 0) "✓ Синхронизация завершена" else "✗ Завершено с ошибками: ${result.errors}",
                Toast.LENGTH_SHORT).show()
            
            // НОВОЕ: закрываем активность, чтобы пользователь увидел обновлённую историю
            finish()
        }
    }

    private fun copyTask() {
        val t = currentTask ?: return
        lifecycleScope.launch {
            val newTask = t.copy(id = java.util.UUID.randomUUID().toString(),
                name = "${t.name} (копия)", lastRun = 0, lastStatus = "")
            withContext(Dispatchers.IO) { TaskManager.upsert(this@TaskDetailsActivity, newTask) }
            Toast.makeText(this@TaskDetailsActivity, "Задание скопировано", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun confirmDelete() {
        val t = currentTask ?: return
        AlertDialog.Builder(this)
            .setTitle("🗑 Удалить задание?")
            .setMessage("Задание «${t.name}» будет удалено безвозвратно.")
            .setPositiveButton("Удалить") { _, _ ->
                lifecycleScope.launch {
                    // ИСПРАВЛЕНО: сначала отменяем будильник (пока задание ещё есть),
                    // потом удаляем из хранилища, потом пересчитываем остальные
                    AlarmScheduler.cancelForTask(this@TaskDetailsActivity, t)
                    withContext(Dispatchers.IO) { TaskManager.delete(this@TaskDetailsActivity, t.id) }
                    AlarmScheduler.scheduleNext(this@TaskDetailsActivity)
                    Toast.makeText(this@TaskDetailsActivity, "Задание удалено", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .setNegativeButton("Отмена", null).show()
    }

    private fun switchTab(tabIndex: Int) {
        panelMain.visibility = if (tabIndex == 0) View.VISIBLE else View.GONE
        panelPlan.visibility = if (tabIndex == 1) View.VISIBLE else View.GONE
        panelSync.visibility = if (tabIndex == 2) View.VISIBLE else View.GONE
        chipMain.setBackgroundResource(if (tabIndex == 0) R.drawable.bg_button_primary else R.drawable.bg_button_secondary)
        chipMain.setTextColor(ContextCompat.getColor(this, if (tabIndex == 0) R.color.black else R.color.text_secondary))
        chipPlan.setBackgroundResource(if (tabIndex == 1) R.drawable.bg_button_primary else R.drawable.bg_button_secondary)
        chipPlan.setTextColor(ContextCompat.getColor(this, if (tabIndex == 1) R.color.black else R.color.text_secondary))
        chipSync.setBackgroundResource(if (tabIndex == 2) R.drawable.bg_button_primary else R.drawable.bg_button_secondary)
        chipSync.setTextColor(ContextCompat.getColor(this, if (tabIndex == 2) R.color.black else R.color.text_secondary))
    }

    private fun formatDateTime(time: Long): String =
        java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(time))
}