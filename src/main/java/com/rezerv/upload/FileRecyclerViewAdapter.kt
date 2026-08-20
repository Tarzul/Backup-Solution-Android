package com.rezerv.upload

import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FileRecyclerViewAdapter(
    private val context: Context,
    private val serverUrl: () -> String,
    private val user: () -> String,
    private val pass: () -> String,
    private val onItemClick: (WebDavRepository.FileInfo, Int) -> Unit
) : RecyclerView.Adapter<FileRecyclerViewAdapter.ViewHolder>() {

    private var items: List<WebDavRepository.FileInfo> = emptyList()
    var selectedIndices: MutableSet<Int> = mutableSetOf()
    var selectionMode: Boolean = false
    private val scope = CoroutineScope(Dispatchers.Main)

    fun submitList(newItems: List<WebDavRepository.FileInfo>) {
        if (newItems === items) return
        items = newItems
        notifyDataSetChanged()
    }

    // ИСПРАВЛЕНО: метод для остановки scope адаптера
    fun cancel() {
        scope.cancel()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val isSel = selectedIndices.contains(position)

        holder.itemView.setBackgroundColor(
            if (isSel) ContextCompat.getColor(context, R.color.selection_highlight)
            else 0x00000000
        )

        holder.nameView.text = item.name
        holder.nameView.setTextColor(FileUtils.getNameColor(context, item))
        holder.nameView.setTypeface(null, if (item.isDirectory) Typeface.BOLD else Typeface.NORMAL)

        holder.infoView.text = FileUtils.getInfoText(item)
        holder.infoView.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))

        holder.icon.tag = item.path
        if (!item.isDirectory && FileUtils.isImageFile(item.name)) {
            holder.icon.setImageResource(android.R.drawable.ic_menu_gallery)
            loadThumb(holder, item)
        } else {
            holder.icon.setImageBitmap(null)
            holder.icon.setImageResource(FileUtils.getIconResource(item))
        }

        if (selectionMode) {
            holder.checkMark.visibility = View.VISIBLE
            holder.checkMark.isChecked = isSel
            holder.checkMark.jumpDrawablesToCurrentState()
        } else {
            holder.checkMark.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onItemClick(item, position) }
    }

    private fun loadThumb(holder: ViewHolder, item: WebDavRepository.FileInfo) {
        scope.launch {
            val bmp = RemoteImageLoader.loadThumbnail(serverUrl(), user(), pass(), item.path)
            if (holder.icon.tag == item.path && bmp != null) {
                holder.icon.setImageBitmap(bmp)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.ivIcon)
        val nameView: TextView = itemView.findViewById(R.id.tvName)
        val infoView: TextView = itemView.findViewById(R.id.tvSize)
        val checkMark: CheckBox = itemView.findViewById(R.id.cbSelect)
    }
}