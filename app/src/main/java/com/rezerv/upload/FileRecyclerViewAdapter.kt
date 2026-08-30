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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.dispose
import coil3.load

class FileRecyclerViewAdapter(
    private val context: Context,
    private val serverUrl: () -> String,
    private val user: () -> String,
    private val pass: () -> String,
    private val onItemClick: (WebDavRepository.FileInfo, Int) -> Unit
) : ListAdapter<WebDavRepository.FileInfo, FileRecyclerViewAdapter.ViewHolder>(FileInfoDiffCallback()) {

    var selectedIndices: MutableSet<Int> = mutableSetOf()
    var selectionMode: Boolean = false

    companion object {
        const val PAYLOAD_SELECTION_UPDATE = "selection_update"
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        onBindViewHolder(holder, position, mutableListOf())
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        val item = getItem(position)

        if (payloads.contains(PAYLOAD_SELECTION_UPDATE)) {
            val isSel = selectedIndices.contains(position)
            holder.itemView.setBackgroundColor(
                if (isSel) ContextCompat.getColor(context, R.color.selection_highlight)
                else 0x00000000
            )
            holder.checkMark.isChecked = isSel
            holder.checkMark.visibility = if (selectionMode) View.VISIBLE else View.GONE
            return
        }

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
            holder.icon.dispose()

            holder.icon.load(WebDavImages.url(serverUrl(), item.path)) {
                // ✅ addHeader() работает напрямую в Coil 3
                addHeader("Authorization", WebDavImages.basicHeader(user(), pass()))
                memoryCacheKey(WebDavImages.cacheKey("thumb", item.path, item.size))
                diskCacheKey(WebDavImages.cacheKey("thumb", item.path, item.size))
                // ✅ placeholder принимает Drawable, используем ContextCompat
                placeholder(ContextCompat.getDrawable(context, android.R.drawable.ic_menu_gallery))
                error(ContextCompat.getDrawable(context, android.R.drawable.ic_menu_report_image))
                // ✅ crossfade убран - настроен глобально в BackupApplication
                size(96, 96)

                listener(
                    onError = { _, _ ->
                        holder.icon.setImageResource(android.R.drawable.ic_menu_report_image)
                    }
                )
            }
        } else {
            holder.icon.dispose()
            holder.icon.setImageBitmap(null)
            holder.icon.setImageResource(FileUtils.getIconResource(item))
        }

        if (selectionMode) {
            holder.checkMark.visibility = View.VISIBLE
            holder.checkMark.isChecked = isSel
        } else {
            holder.checkMark.visibility = View.GONE
            holder.checkMark.isChecked = false
        }

        holder.itemView.setOnClickListener { onItemClick(item, position) }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.icon.dispose()
        holder.icon.setImageBitmap(null)
        holder.checkMark.isChecked = false
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.ivIcon)
        val nameView: TextView = itemView.findViewById(R.id.tvName)
        val infoView: TextView = itemView.findViewById(R.id.tvSize)
        val checkMark: CheckBox = itemView.findViewById(R.id.cbSelect)
    }
}

class FileInfoDiffCallback : DiffUtil.ItemCallback<WebDavRepository.FileInfo>() {
    override fun areItemsTheSame(oldItem: WebDavRepository.FileInfo, newItem: WebDavRepository.FileInfo): Boolean {
        return oldItem.path == newItem.path
    }

    override fun areContentsTheSame(oldItem: WebDavRepository.FileInfo, newItem: WebDavRepository.FileInfo): Boolean {
        return oldItem == newItem
    }

    override fun getChangePayload(oldItem: WebDavRepository.FileInfo, newItem: WebDavRepository.FileInfo): Any? {
        if (oldItem.name == newItem.name && oldItem.isDirectory == newItem.isDirectory) {
            return "content_update"
        }
        return null
    }
}