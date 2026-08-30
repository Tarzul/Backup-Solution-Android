package com.rezerv.upload.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.rezerv.upload.R
import com.rezerv.upload.SyncTask
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builder для карточек заданий.
 * Отделяет UI-логику от ViewModel.
 */
class TaskCardBuilder(private val context: Context) {

    /**
     * Listener для действий с карточкой задания.
     */
    interface Listener {
        fun onRunTask(task: SyncTask)
        fun onEditTask(task: SyncTask)
        fun onDeleteTask(task: SyncTask)
    }

    fun build(task: SyncTask, listener: Listener): View {
        val card = createCardContainer(task)
        card.addView(createTitle(task.name))
        card.addView(createSubtitle(getSyncTypeLabel(task.syncType)))
        card.addView(createInfo("📅 ${formatLastRun(task.lastRun)}"))
        card.addView(createInfo("⏰ ${getScheduleLabel(task)}"))
        card.addView(createActionRow(task, listener))
        return card
    }

    // ==================== Приватные методы ====================

    private fun createCardContainer(task: SyncTask): LinearLayout {
        val cardColor: Int
        val strokeColor: Int
        when {
            !task.scheduleEnabled -> {
                cardColor = 0xFF6D6D6D.toInt()
                strokeColor = 0xFFBDBDBD.toInt()
            }
            task.lastStatus == "error" -> {
                cardColor = 0xFF4A2D2D.toInt()
                strokeColor = 0xFFE57373.toInt()
            }
            else -> {
                cardColor = 0xFF2D4A2D.toInt()
                strokeColor = 0xFF81C784.toInt()
            }
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(cardColor)
                cornerRadius = 24f
                setStroke(2, strokeColor)
            }
            setPadding(24, 20, 24, 20)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 8, 0, 8) }
        }
    }

    private fun createTitle(text: String): TextView = TextView(context).apply {
        this.text = text
        setTextColor(0xFFFFFFFF.toInt())
        textSize = 16f
        setTypeface(null, Typeface.BOLD)
    }

    private fun createSubtitle(text: String): TextView = TextView(context).apply {
        this.text = text
        setTextColor(0xFFAAAAAA.toInt())
        textSize = 12f
    }

    private fun createInfo(text: String): TextView = TextView(context).apply {
        this.text = text
        setTextColor(0xFFCCCCCC.toInt())
        textSize = 12f
    }

    private fun createActionRow(task: SyncTask, listener: Listener): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 14 }

            // Кнопка "ЗАПУСК"
            addView(Button(context).apply {
                text = "▶ ЗАПУСК"
                setBackgroundResource(R.drawable.bg_button_primary)
                setTextColor(0xFF000000.toInt())
                setOnClickListener { listener.onRunTask(task) }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            // Кнопка "✏️"
            addView(Button(context).apply {
                text = "✏️"
                setBackgroundResource(R.drawable.bg_button_secondary)
                setTextColor(0xFFE0E0E0.toInt())
                setOnClickListener { listener.onEditTask(task) }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { leftMargin = 12 }
            })

            // Кнопка "🗑"
            addView(Button(context).apply {
                text = "🗑"
                setBackgroundResource(R.drawable.bg_button_danger)
                setTextColor(0xFFFFFFFF.toInt())
                setOnClickListener {
                    AlertDialog.Builder(context)
                        .setTitle("Удалить задание?")
                        .setMessage("Задание \"${task.name}\" будет удалено безвозвратно.")
                        .setPositiveButton("Удалить") { _, _ -> listener.onDeleteTask(task) }
                        .setNegativeButton("Отмена", null)
                        .show()
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { leftMargin = 12 }
            })
        }
    }

    // ==================== Утилиты форматирования ====================

    private fun getSyncTypeLabel(syncType: String): String = when (syncType) {
        "two_way" -> "⇄ Двусторонняя"
        "to_left" -> "← В левую папку"
        else -> "→ В правую папку"
    }

    private fun getScheduleLabel(task: SyncTask): String {
        if (!task.scheduleEnabled) return "Расписание выключено"
        return "Расписание: ${when (task.scheduleMode) {
            "minutes" -> "каждые ${task.intervalValue} мин"
            "hourly" -> "каждые ${task.intervalValue} ч"
            "daily" -> "ежедневно ${String.format("%02d:%02d", task.hour, task.minute)}"
            "weekly" -> "еженедельно ${String.format("%02d:%02d", task.hour, task.minute)}"
            "monthly" -> "ежемесячно ${String.format("%02d:%02d", task.hour, task.minute)}"
            else -> task.scheduleMode
        }}"
    }

    private fun formatLastRun(lastRun: Long): String {
        if (lastRun <= 0) return "Ещё не запускалось"
        return SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(lastRun))
    }
}