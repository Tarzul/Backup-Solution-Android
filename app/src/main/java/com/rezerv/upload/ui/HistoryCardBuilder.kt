package com.rezerv.upload.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.rezerv.upload.HistoryRecord
import com.rezerv.upload.utils.FormatUtils  // ✅ ДОБАВИТЬ

class HistoryCardBuilder(private val context: Context) {

    interface Listener {
        fun onHistoryClick(record: HistoryRecord)
    }

    fun build(record: HistoryRecord, listener: Listener?): View {
        val isRunning = record.status == "running"
        val isOk = record.status == "ok"

        val card = createCardContainer(record, isRunning, isOk, listener)
        card.addView(createHeader(record, isRunning, isOk))

        if (isRunning) {
            addRunningContent(card, record)
        } else {
            addFinishedContent(card, record)
        }

        return card
    }

    private fun createCardContainer(
        record: HistoryRecord,
        isRunning: Boolean,
        isOk: Boolean,
        listener: Listener?
    ): LinearLayout {
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

            if (!isRunning && listener != null) {
                setOnClickListener { listener.onHistoryClick(record) }
            }
        }
    }

    private fun createHeader(record: HistoryRecord, isRunning: Boolean, isOk: Boolean): LinearLayout {
        val statusIcon = when {
            isRunning -> "⏳"
            isOk -> "✔"
            else -> "✖"
        }
        val statusColor = when {
            isRunning -> 0xFF64B5F6.toInt()
            isOk -> 0xFF81C784.toInt()
            else -> 0xFFE57373.toInt()
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            addView(TextView(context).apply {
                text = record.taskName.ifBlank { "Синхронизация" }
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
            })

            addView(TextView(context).apply {
                text = statusIcon
                setTextColor(statusColor)
                textSize = 22f
            })
        }
    }

    private fun addRunningContent(card: LinearLayout, record: HistoryRecord) {
        val elapsed = System.currentTimeMillis() - record.liveStartedAt
        // ✅ ИСПОЛЬЗУЕМ FormatUtils
        card.addView(createRow("🔄 Выполняется...", "⏱ ${FormatUtils.formatDuration(elapsed)}"))

        if (record.totalFiles > 0) {
            card.addView(ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = record.totalFiles
                progress = record.currentFileIndex
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 12 }
            })

            card.addView(TextView(context).apply {
                text = "📄 ${record.currentFileIndex} из ${record.totalFiles}"
                setTextColor(0xFFCCCCCC.toInt())
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 4 }
            })
        }

        if (record.currentFileName.isNotEmpty()) {
            card.addView(TextView(context).apply {
                text = "▸ ${record.currentFileName}"
                setTextColor(0xFF64B5F6.toInt())
                textSize = 12f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 4 }
            })
        }

        if (record.currentFileIndex > 0 && record.totalFiles > record.currentFileIndex) {
            val avgPerFile = elapsed.toFloat() / record.currentFileIndex
            val etaMs = (avgPerFile * (record.totalFiles - record.currentFileIndex)).toLong()
            // ✅ ИСПОЛЬЗУЕМ FormatUtils
            card.addView(TextView(context).apply {
                text = "⏱ Осталось примерно: ${FormatUtils.formatDuration(etaMs)}"
                setTextColor(0xFFAAAAAA.toInt())
                textSize = 11f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 4 }
            })
        }
    }

    private fun addFinishedContent(card: LinearLayout, record: HistoryRecord) {
        // ✅ ИСПОЛЬЗУЕМ FormatUtils
        card.addView(createRow(
            "📅 ${FormatUtils.formatDateTime(record.time)}",
            "⏱ ${FormatUtils.formatDuration(record.durationMs)}"
        ))
        card.addView(createRow(
            "🔍 Проверено: ${record.checked}",
            "⬆ Передано: ${record.uploaded + record.downloaded}"
        ))
        if (record.uploaded > 0 || record.downloaded > 0) {
            card.addView(createRow(
                "⬆ Загружено: ${record.uploaded} ф.",
                "⬇ Скачано: ${record.downloaded} ф."
            ))
        }
        if (record.bytesTransferred > 0) {
            val speed = if (record.transferMs > 0) {
                val v = (record.bytesTransferred / 1048576.0) / (record.transferMs / 1000.0)
                if (v >= 1) String.format("%.1f МБ/с", v)
                // ✅ ИСПОЛЬЗУЕМ FormatUtils
                else "${FormatUtils.formatSize((record.bytesTransferred / (record.transferMs / 1000.0)).toLong())}/с"
            } else "—"
            card.addView(createRow(
                // ✅ ИСПОЛЬЗУЕМ FormatUtils
                "💾 ${FormatUtils.formatSize(record.bytesTransferred)}",
                "⚡ $speed"
            ))
        }
        card.addView(createRow("✗ Ошибок: ${record.errors}", getTriggerLabel(record.trigger)))
    }

    private fun createRow(left: String, right: String): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8 }

            addView(TextView(context).apply {
                text = left
                setTextColor(0xFFCCCCCC.toInt())
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
            })

            addView(TextView(context).apply {
                text = right
                setTextColor(0xFFCCCCCC.toInt())
                textSize = 13f
            })
        }
    }

    private fun getTriggerLabel(trigger: String): String = when (trigger) {
        "user" -> "👤 Пользователь"
        "test" -> "🧪 Тест"
        "schedule" -> "⏰ Расписание"
        else -> "•"
    }
}