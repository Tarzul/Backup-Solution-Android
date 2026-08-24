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
import coil.dispose
import coil.load

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

    fun submitList(newItems: List<WebDavRepository.FileInfo>) {
        if (newItems === items) return
        items = newItems
        notifyDataSetChanged()
    }

    fun cancel() { /* Coil сам отменяет запросы при detach view */ }

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

        if (!item.isDirectory && FileUtils.isImageFile(item.name)) {
            holder.icon.load(WebDavImages.url(serverUrl(), item.path)) {
                addHeader("Authorization", WebDavImages.basicHeader(user(), pass()))
                memoryCacheKey(WebDavImages.cacheKey("thumb", item.path, item.size))
                diskCacheKey(WebDavImages.cacheKey("thumb", item.path, item.size))
                placeholder(android.R.drawable.ic_menu_gallery)
                error(android.R.drawable.ic_menu_report_image)
                crossfade(true)
                size(96, 96)
            }
        } else {
            holder.icon.dispose()
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

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.ivIcon)
        val nameView: TextView = itemView.findViewById(R.id.tvName)
        val infoView: TextView = itemView.findViewById(R.id.tvSize)
        val checkMark: CheckBox = itemView.findViewById(R.id.cbSelect)
    }
}