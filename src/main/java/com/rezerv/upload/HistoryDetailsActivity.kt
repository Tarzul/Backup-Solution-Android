package com.rezerv.upload

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.DecimalFormat

class HistoryDetailsActivity : AppCompatActivity() {

    private val dpToPx: (Int) -> Int = { dp ->
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()
    }
    private val sizeFormatter = DecimalFormat("#.##")

    // Адаптеры из отдельного файла HistoryDetailsAdapter.kt
    private lateinit var fileAdapter: HistoryDetailsAdapter.FileAdapter
    private lateinit var folderAdapter: HistoryDetailsAdapter.FolderAdapter
    private lateinit var rvFiles: RecyclerView
    private lateinit var rvFolders: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history_details)

        findViewById<ImageButton>(R.id.btnHistBack).setOnClickListener { finish() }

        rvFiles = findViewById(R.id.rvFilesList)
        rvFolders = findViewById(R.id.rvFoldersList)
        rvFiles.layoutManager = LinearLayoutManager(this)
        rvFolders.layoutManager = LinearLayoutManager(this)
        rvFiles.setNestedScrollingEnabled(false)
        rvFolders.setNestedScrollingEnabled(false)

        fileAdapter = HistoryDetailsAdapter.FileAdapter(emptyList())
        folderAdapter = HistoryDetailsAdapter.FolderAdapter(emptyList())
        rvFiles.adapter = fileAdapter
        rvFolders.adapter = folderAdapter

        setupExpandableHeaders()

        val time = intent.getLongExtra("time", 0L)
        val r = HistoryManager.getRecords(this).firstOrNull { it.time == time }
        if (r == null) { finish(); return }
        fillData(r)
    }

    private fun setupExpandableHeaders() {
        val filesArrow = findViewById<TextView>(R.id.tvHistFilesArrow)
        val foldersArrow = findViewById<TextView>(R.id.tvHistFoldersArrow)

        findViewById<View>(R.id.llFilesHeader).setOnClickListener {
            val isVisible = rvFiles.visibility == View.VISIBLE
            rvFiles.visibility = if (isVisible) View.GONE else View.VISIBLE
            filesArrow.text = if (isVisible) "›" else "⌄"
        }

        findViewById<View>(R.id.llFoldersHeader).setOnClickListener {
            val isVisible = rvFolders.visibility == View.VISIBLE
            rvFolders.visibility = if (isVisible) View.GONE else View.VISIBLE
            foldersArrow.text = if (isVisible) "›" else "⌄"
        }
    }

    private fun fillData(r: HistoryRecord) {
        findViewById<TextView>(R.id.tvHistTitle).text = "SD CARD > WebDAV"
        val banner = findViewById<TextView>(R.id.tvHistBanner)
        val isOk = r.status == "ok"
        banner.text = if (isOk) "Успешно" else "Ошибка"
        banner.background = GradientDrawable().apply {
            setColor(if (isOk) Color.parseColor("#FF3E6B2F") else Color.parseColor("#FF8B3A3A"))
            cornerRadius = dpToPx(12).toFloat()
        }

        val s1 = findViewById<LinearLayout>(R.id.llSection1)
        s1.removeAllViews()
        addRow(s1, "Запуск", formatDateTime(r.time))
        addRow(s1, "Длительность", formatFullDuration(r.durationMs))
        addRowBadge(s1, "Запущено", triggerLabel(r.trigger))

        val s2 = findViewById<LinearLayout>(R.id.llSection2)
        s2.removeAllViews()
        addRow(s2, "Файлов проверено", "${r.checked}")
        addRow(s2, "Файлов синхронизировано", "${r.uploaded + r.downloaded}")
        addRow(s2, "Файлов удалено", "${r.deleted}")
        addRow(s2, "Данных передано", formatSize(r.bytesTransferred))
        addRow(s2, "Длительность передачи", formatFullDuration(r.transferMs))
        addRow(s2, "Скорость", speedLabel(r))

        val files = HistoryManager.parseFiles(r.filesJson)
        findViewById<TextView>(R.id.tvHistFilesTitle).text = "Файлов передано (${files.size})"
        fileAdapter.updateData(files.take(300))

        val folders = HistoryManager.parseFolders(r.foldersJson)
        findViewById<TextView>(R.id.tvHistFoldersTitle).text = "Папок создано (${folders.size})"
        folderAdapter.updateData(folders)
    }

    private fun addRow(container: LinearLayout, label: String, value: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(4) }
        }
        row.addView(TextView(this).apply {
            text = label
            setTextColor(Color.parseColor("#FFCCCCCC"))
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(this).apply {
            text = value
            setTextColor(Color.WHITE)
            textSize = 14f
        })
        container.addView(row)
    }

    private fun addRowBadge(container: LinearLayout, label: String, value: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(4) }
        }
        row.addView(TextView(this).apply {
            text = label
            setTextColor(Color.parseColor("#FFCCCCCC"))
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(badge(value))
        container.addView(row)
    }

    private fun badge(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 12f
        setBackgroundResource(R.drawable.bg_badge)
        val pad = dpToPx(8)
        setPadding(pad, dpToPx(4), pad, dpToPx(4))
    }

    private fun formatDateTime(time: Long): String =
        java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(time))

    private fun formatFullDuration(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "${h}ч ${m}мин ${s}с" else "${m}мин ${s}с"
    }

    // ИСПРАВЛЕНО: b.toDouble() для устранения перегрузки DecimalFormat.format
    private fun formatSize(b: Long): String {
        if (b < 0) return "—"
        return when {
            b < 1024 -> "$b Б"
            b < 1024 * 1024 -> "${sizeFormatter.format(b.toDouble() / 1024.0)} КБ"
            b < 1024L * 1024 * 1024 -> "${sizeFormatter.format(b.toDouble() / (1024.0 * 1024))} МБ"
            else -> "${sizeFormatter.format(b.toDouble() / (1024.0 * 1024 * 1024))} ГБ"
        }
    }

    private fun speedLabel(r: HistoryRecord): String {
        if (r.transferMs <= 0 || r.bytesTransferred <= 0) return "0 Б/с"
        val mb = r.bytesTransferred / 1048576.0
        val sec = r.transferMs / 1000.0
        val v = mb / sec
        return if (v >= 1) "${sizeFormatter.format(v)} МБ/с"
        else "${formatSize((r.bytesTransferred / (r.transferMs / 1000.0)).toLong())}/с"
    }

    private fun triggerLabel(t: String): String = when (t) {
        "user" -> "Пользователь"
        "test" -> "Тест"
        "schedule" -> "Расписание"
        else -> t
    }
}