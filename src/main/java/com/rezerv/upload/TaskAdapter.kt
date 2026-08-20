package com.rezerv.upload
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class TaskAdapter(
    private val context: Context,
    private val onRunClick: (SyncTask) -> Unit,
    private val onDeleteClick: (SyncTask) -> Unit
) : RecyclerView.Adapter<TaskAdapter.ViewHolder>() {

    private var tasks: List<SyncTask> = emptyList()

    fun submitList(newTasks: List<SyncTask>) {
        tasks = newTasks
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_task_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(tasks[position])
    override fun getItemCount() = tasks.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardRoot: LinearLayout = itemView.findViewById(R.id.cardRoot)
        val tvName: TextView = itemView.findViewById(R.id.tvTaskName)
        val tvType: TextView = itemView.findViewById(R.id.tvTaskType)
        val tvLastRun: TextView = itemView.findViewById(R.id.tvTaskLastRun)
        val tvSchedule: TextView = itemView.findViewById(R.id.tvTaskSchedule)
        val btnRun: Button = itemView.findViewById(R.id.btnRun)
        val btnEdit: Button = itemView.findViewById(R.id.btnEdit)
        val btnDelete: Button = itemView.findViewById(R.id.btnDelete)

        fun bind(task: SyncTask) {
            tvName.text = task.name
            tvType.text = when (task.syncType) {
                "two_way" -> "⇄ Двусторонняя"
                "to_left" -> "← В левую папку"
                else -> "→ В правую папку"
            }
            tvLastRun.text = if (task.lastRun > 0) "📅 ${formatDateTime(task.lastRun)}" else "Ещё не запускалось"
            tvSchedule.text = if (task.scheduleEnabled) "⏰ ${scheduleLabel(task)}" else "⏰ Расписание выключено"

            cardRoot.setOnClickListener {
                context.startActivity(Intent(context, TaskDetailsActivity::class.java).putExtra("taskId", task.id))
            }
            btnRun.setOnClickListener { onRunClick(task) }
            btnEdit.setOnClickListener { cardRoot.performClick() }
            btnDelete.setOnClickListener { onDeleteClick(task) }
        }
        
        private fun scheduleLabel(t: SyncTask): String = when (t.scheduleMode) {
            "minutes" -> "каждые ${t.intervalValue} мин"
            "hourly" -> "каждые ${t.intervalValue} ч"
            "daily" -> "ежедневно ${String.format("%02d:%02d", t.hour, t.minute)}"
            "weekly" -> "еженедельно ${String.format("%02d:%02d", t.hour, t.minute)}"
            "monthly" -> "ежемесячно ${String.format("%02d:%02d", t.hour, t.minute)}"
            else -> t.scheduleMode
        }
        private fun formatDateTime(time: Long): String = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(time))
    }
}