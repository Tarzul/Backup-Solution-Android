package com.rezerv.upload

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * HistoryAdapter — RecyclerView-адаптер для карточек истории синхронизаций.
 * Используется во вкладке "История" в MainActivity.
 */
class HistoryAdapter(private val context: Context) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private var records: List<HistoryRecord> = emptyList()

    fun submitList(newRecords: List<HistoryRecord>) {
        records = newRecords
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.item_history_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(records[position])
    }

    override fun getItemCount(): Int = records.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardRoot: LinearLayout = itemView.findViewById(R.id.cardRoot)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvHistTitle)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvHistStatus)
        private val tvTime: TextView = itemView.findViewById(R.id.tvHistTime)
        private val tvStats: TextView = itemView.findViewById(R.id.tvHistStats)

        fun bind(r: HistoryRecord) {
            val isOk = r.status == "ok"

            tvTitle.text = "Синхронизация"
            tvStatus.text = if (isOk) "✔" else "✖"
            tvStatus.setTextColor(
                if (isOk) ContextCompat.getColor(context, R.color.success_green)
                else ContextCompat.getColor(context, R.color.error_red)
            )

            tvTime.text = "📅 ${formatDateTime(r.time)} | ⏱ ${formatDuration(r.durationMs)}"
            tvStats.text = "🔍 Проверено: ${r.checked} | " +
                "⬆ Передано: ${r.uploaded + r.downloaded} | " +
                "✗ Ошибок: ${r.errors}"

            cardRoot.setOnClickListener {
                val intent = Intent(context, HistoryDetailsActivity::class.java)
                intent.putExtra("time", r.time)
                context.startActivity(intent)
            }
        }

        private fun formatDateTime(time: Long): String =
            SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(time))

        private fun formatDuration(ms: Long): String {
            val totalSec = ms / 1000
            val h = totalSec / 3600
            val m = (totalSec % 3600) / 60
            val s = totalSec % 60
            return when {
                h > 0 -> "${h}ч ${m}м ${s}с"
                m > 0 -> "${m}м ${s}с"
                else -> "${s}с"
            }
        }
    }
}