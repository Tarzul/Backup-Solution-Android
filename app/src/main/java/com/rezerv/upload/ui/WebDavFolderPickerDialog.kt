package com.rezerv.upload.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rezerv.upload.R
import com.rezerv.upload.WebDavRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class WebDavFolderPickerDialog : AppCompatDialogFragment() {

    interface FolderPickerListener {
        fun onFolderSelected(path: String)
        fun onFolderPickerCancelled() {}
    }

    companion object {
        private const val ARG_BASE = "arg_base"
        private const val ARG_ROOT = "arg_root"
        private const val ARG_USER = "arg_user"
        private const val ARG_PASS = "arg_pass"

        fun newInstance(
            base: String,
            root: String,
            user: String,
            pass: String
        ): WebDavFolderPickerDialog {
            return WebDavFolderPickerDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_BASE, base)
                    putString(ARG_ROOT, root)
                    putString(ARG_USER, user)
                    putString(ARG_PASS, pass)
                }
            }
        }
    }

    private var listener: FolderPickerListener? = null
    private var currentPath: String = ""
    private lateinit var base: String
    private lateinit var root: String
    private lateinit var user: String
    private lateinit var pass: String

    // State
    private var folders: List<String> = emptyList()
    private var isLoading = false
    private var dialogTitle: TextView? = null
    private var progressBar: ProgressBar? = null
    private var recyclerView: RecyclerView? = null
    private var dialog: AlertDialog? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loadJob: Job? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = when {
            context is FolderPickerListener -> context
            parentFragment is FolderPickerListener -> parentFragment as FolderPickerListener
            else -> null
        }
        if (listener == null) {
            Timber.w("No FolderPickerListener attached")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            base = it.getString(ARG_BASE, "")
            root = it.getString(ARG_ROOT, "/")
            user = it.getString(ARG_USER, "")
            pass = it.getString(ARG_PASS, "")
        }
        currentPath = savedInstanceState?.getString("currentPath", root) ?: root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()

        // Создаем layout programmatically
        val layout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }

        dialogTitle = TextView(context).apply {
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
        }
        layout.addView(dialogTitle)

        progressBar = ProgressBar(context).apply {
            visibility = View.GONE
        }
        layout.addView(progressBar)

        recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 16
            }
        }
        layout.addView(recyclerView)

        val builder = AlertDialog.Builder(context)
            .setView(layout)
            .setPositiveButton("Выбрать эту папку") { _, _ ->
                listener?.onFolderSelected(currentPath)
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                listener?.onFolderPickerCancelled()
            }

        dialog = builder.create()
        loadFolders()
        return dialog!!
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("currentPath", currentPath)
    }

    private fun loadFolders() {
        loadJob?.cancel()
        isLoading = true
        progressBar?.visibility = View.VISIBLE
        recyclerView?.visibility = View.GONE
        dialogTitle?.text = "Папка: $currentPath"

        loadJob = scope.launch {
            try {
                val files = withContext(Dispatchers.IO) {
                    WebDavRepository.listFiles(base, currentPath, user, pass)
                }

                folders = files
                    .filter { it.isDirectory }
                    .map { it.name }
                    .sorted()

                updateUI()
            } catch (e: Exception) {
                Timber.e(e, "Failed to load folders at $currentPath")
                val message = when (e) {
                    is java.net.UnknownHostException -> "Нет подключения к серверу"
                    is java.net.SocketTimeoutException -> "Превышено время ожидания"
                    is java.io.IOException -> "Ошибка сети: ${e.message}"
                    else -> "Ошибка: ${e.message}"
                }
                dialogTitle?.text = "Ошибка: $message"
                folders = emptyList()
                updateUI()
            } finally {
                isLoading = false
                progressBar?.visibility = View.GONE
                recyclerView?.visibility = View.VISIBLE
            }
        }
    }

    private fun updateUI() {
        val displayItems = mutableListOf<String>()

        if (currentPath != root) {
            displayItems.add(".. (вверх)")
        }
        displayItems.addAll(folders)

        recyclerView?.adapter = FolderAdapter(displayItems) { selected ->
            handleFolderSelection(selected)
        }
    }

    private fun handleFolderSelection(selected: String) {
        if (selected == ".. (вверх)") {
            // Переходим вверх по дереву
            val trimmed = currentPath.trimEnd('/')
            val lastSlash = trimmed.lastIndexOf('/')
            currentPath = if (lastSlash <= 0) root else trimmed.substring(0, lastSlash + 1)
        } else {
            // Переходим в подпапку
            currentPath = currentPath.trimEnd('/') + "/" + selected + "/"
        }
        loadFolders()
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        loadJob?.cancel()
        scope.cancel()
        dialogTitle = null
        progressBar = null
        recyclerView = null
        dialog = null
    }

    private class FolderAdapter(
        private val items: List<String>,
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<FolderAdapter.ViewHolder>() {

        class ViewHolder(view: TextView) : RecyclerView.ViewHolder(view)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val textView = TextView(parent.context).apply {
                setPadding(32, 24, 32, 24)
                textSize = 16f
                setTextColor(0xFFFFFFFF.toInt())
                val bg = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList(
                        arrayOf(intArrayOf()),
                        intArrayOf(0x33FFFFFF.toInt())
                    ),
                    null,
                    null
                )
                background = bg
                isClickable = true
                isFocusable = true
            }
            return ViewHolder(textView)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            (holder.itemView as TextView).text = item
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }
}