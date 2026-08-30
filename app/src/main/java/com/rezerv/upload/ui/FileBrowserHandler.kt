package com.rezerv.upload.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rezerv.upload.DragSelectionListener
import com.rezerv.upload.FileRecyclerViewAdapter
import com.rezerv.upload.R
import com.rezerv.upload.WebDavRepository
import com.rezerv.upload.viewmodel.BrowserViewModel

class FileBrowserHandler(
    private val context: Context,
    private val browserVM: BrowserViewModel,
    private val rvFiles: RecyclerView,
    private val skeletonFiles: View,
    private val tvCurrentPath: TextView,
    private val llSelection: LinearLayout,
    private val tvSelectionCount: TextView
) {
    private lateinit var adapter: FileRecyclerViewAdapter
    private val animationDurationMs = 300L

    fun setupAdapter(
        serverUrl: () -> String,
        user: () -> String,
        pass: () -> String,
        onItemClick: (WebDavRepository.FileInfo, Int) -> Unit
    ) {
        rvFiles.layoutManager = LinearLayoutManager(context)
        rvFiles.itemAnimator = null

        adapter = FileRecyclerViewAdapter(
            context,
            serverUrl = serverUrl,
            user = user,
            pass = pass,
            onItemClick = onItemClick
        )
        rvFiles.adapter = adapter

        // ✅ DragSelectionListener теперь правильно подключен к BrowserViewModel
        rvFiles.addOnItemTouchListener(
            DragSelectionListener(
                recyclerView = rvFiles,
                isSelectionActive = { browserVM.state.value?.selectionMode ?: false },
                onStartSelection = { pos -> browserVM.startSelectionMode(pos) },
                onDragStart = { anchor, forceAdd ->
                    browserVM.beginRangeSelection(anchor, forceAdd)
                },
                onRangeSelect = { anchor, current ->
                    browserVM.selectRange(anchor, current)
                }
            )
        )
    }

    fun updateFileList(state: BrowserViewModel.BrowserState) {
        if (state.isLoading) {
            skeletonFiles.visibility = View.VISIBLE
            rvFiles.visibility = View.GONE
        } else {
            if (skeletonFiles.visibility == View.VISIBLE) {
                // Плавное появление контента
                rvFiles.alpha = 0f
                rvFiles.visibility = View.VISIBLE
                rvFiles.animate()
                    .alpha(1f)
                    .setDuration(animationDurationMs)
                    .withEndAction { skeletonFiles.visibility = View.GONE }
                    .start()
            } else {
                rvFiles.visibility = View.VISIBLE
            }
        }

        adapter.selectionMode = state.selectionMode
        adapter.selectedIndices = state.selectedIndices.toMutableSet()
        adapter.submitList(state.files)
    }

    fun updateSelectionUI(selectionMode: Boolean, count: Int) {
        llSelection.visibility = if (selectionMode) View.VISIBLE else View.GONE
        tvSelectionCount.text = context.getString(R.string.selection_count, count)
        adapter.selectionMode = selectionMode
    }

    fun updateCurrentPath(path: String) {
        tvCurrentPath.text = context.getString(R.string.current_path, path)
    }
}