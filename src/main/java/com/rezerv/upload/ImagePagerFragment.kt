package com.rezerv.upload

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.launch

class ImagePagerFragment : Fragment(R.layout.fragment_image_pager) {

    private val viewModel: MainViewModel by activityViewModels()

    private var images: List<WebDavRepository.FileInfo> = emptyList()
    private var server = ""
    private var user = ""
    private var pass = ""

    private lateinit var pager: ViewPager2
    private lateinit var pagerAdapter: PagerAdapter
    private lateinit var thumbsAdapter: ThumbAdapter
    private lateinit var tvCounter: TextView
    private lateinit var tvFileName: TextView
    private lateinit var badgeSelected: TextView
    private lateinit var thumbsRecycler: RecyclerView

    private var userScrolledThumbs = false
    private var programmaticThumbsScroll = false

    companion object {
        fun newInstance(startIndex: Int, server: String, user: String, pass: String): ImagePagerFragment {
            return ImagePagerFragment().apply {
                arguments = Bundle().apply {
                    putInt("start", startIndex)
                    putString("server", server)
                    putString("user", user)
                    putString("pass", pass)
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        images = viewModel.pagerImages
        if (images.isEmpty()) {
            parentFragmentManager.popBackStack()
            return
        }
        server = arguments?.getString("server") ?: ""
        user = arguments?.getString("user") ?: ""
        pass = arguments?.getString("pass") ?: ""

        tvCounter = view.findViewById(R.id.tvCounter)
        tvFileName = view.findViewById(R.id.tvFileName)
        badgeSelected = view.findViewById(R.id.badgeSelected)
        pager = view.findViewById(R.id.viewPager)
        thumbsRecycler = view.findViewById(R.id.recyclerThumbnails)

        view.findViewById<Button>(R.id.btnClose).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        pagerAdapter = PagerAdapter()
        pager.adapter = pagerAdapter

        thumbsRecycler.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        thumbsAdapter = ThumbAdapter()
        thumbsRecycler.adapter = thumbsAdapter

        // Динамический padding: крайние миниатюры могут вставать ровно по центру
        thumbsRecycler.post {
            setupCarouselPadding()
            centerThumbImmediate(pager.currentItem)
        }

        // ==================== Карусель → пейджер ====================
        thumbsRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                when (newState) {
                    RecyclerView.SCROLL_STATE_DRAGGING -> userScrolledThumbs = true

                    RecyclerView.SCROLL_STATE_SETTLING -> {
                        if (!programmaticThumbsScroll) userScrolledThumbs = true
                    }

                    RecyclerView.SCROLL_STATE_IDLE -> {
                        if (userScrolledThumbs) {
                            userScrolledThumbs = false
                            // палец отпущен — плавно доцентровываем ближайшую миниатюру
                            val pos = centerThumbPosition()
                            if (pos != -1) {
                                if (pos != pager.currentItem) pager.setCurrentItem(pos, false)
                                centerThumbSmooth(pos)
                            }
                        }
                        programmaticThumbsScroll = false
                    }
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (!userScrolledThumbs) return
                val pos = centerThumbPosition()
                // во время drag меняем ТОЛЬКО пейджер, карусель не трогаем
                if (pos != -1 && pos != pager.currentItem) {
                    pager.setCurrentItem(pos, false)
                }
            }
        })

        // ==================== Пейджер → карусель ====================
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = updatePageUI(position)
        })

        viewModel.uiState.observe(viewLifecycleOwner) { updateSelectionIndicators() }

        val start = arguments?.getInt("start") ?: 0
        if (images.isNotEmpty()) {
            pager.setCurrentItem(start.coerceIn(0, images.lastIndex), false)
            updatePageUI(pager.currentItem)
        }
    }

    // ==================== Центрирование ====================

    private fun setupCarouselPadding() {
        if (thumbsRecycler.width == 0) return
        val density = resources.displayMetrics.density
        val itemWidthPx = ((80 + 8) * density).toInt()
        val pad = maxOf(0, (thumbsRecycler.width - itemWidthPx) / 2)
        thumbsRecycler.setPadding(pad, thumbsRecycler.paddingTop, pad, thumbsRecycler.paddingBottom)
        thumbsRecycler.clipToPadding = false
    }

    /** ПЛАВНО ставит миниатюру ровно по центру (без рывков). */
    private fun centerThumbSmooth(position: Int) {
        val lm = thumbsRecycler.layoutManager as? LinearLayoutManager ?: return
        programmaticThumbsScroll = true
        val view = lm.findViewByPosition(position)
        if (view == null) {
            // элемент далеко — мгновенно подводим и центруем на следующем layout
            centerThumbImmediate(position)
            thumbsRecycler.post { centerThumbSmooth(position) }
            return
        }
        val viewCenter = (lm.getDecoratedLeft(view) + lm.getDecoratedRight(view)) / 2f
        val rvCenter = thumbsRecycler.width / 2f
        val dx = (viewCenter - rvCenter).toInt()
        if (dx != 0) thumbsRecycler.smoothScrollBy(dx, 0)
    }

    /** Мгновенное центрирование (только при первом открытии). */
    private fun centerThumbImmediate(position: Int) {
        programmaticThumbsScroll = true
        val lm = thumbsRecycler.layoutManager as? LinearLayoutManager ?: return
        lm.scrollToPositionWithOffset(position, thumbsRecycler.paddingStart)
    }

    // ==================== Выделение ====================
    private fun isImageSelected(f: WebDavRepository.FileInfo): Boolean {
        val state = viewModel.uiState.value ?: return false
        val idx = state.files.indexOfFirst { it.path == f.path }
        return idx >= 0 && state.selectedIndices.contains(idx)
    }

    private fun toggleSelectionFor(f: WebDavRepository.FileInfo) {
        val state = viewModel.uiState.value ?: return
        val idx = state.files.indexOfFirst { it.path == f.path }
        if (idx >= 0) viewModel.toggleSelection(idx)
    }

    // ==================== UI ====================
    private fun centerThumbPosition(): Int {
        val centerX = thumbsRecycler.width / 2f
        var bestDist = Float.MAX_VALUE
        var bestPos = -1
        for (i in 0 until thumbsRecycler.childCount) {
            val child = thumbsRecycler.getChildAt(i)
            val dist = kotlin.math.abs((child.left + child.right) / 2f - centerX)
            if (dist < bestDist) {
                bestDist = dist
                bestPos = thumbsRecycler.getChildAdapterPosition(child)
            }
        }
        return bestPos
    }

    private fun updatePageUI(position: Int) {
        val f = images.getOrNull(position) ?: return
        tvCounter.text = "${position + 1} / ${images.size}"
        tvFileName.text = f.name
        // ИСПРАВЛЕНО: точечное обновление рамки вместо notifyDataSetChanged (убирает дёргание)
        thumbsAdapter.setCurrent(position)
        // ИСПРАВЛЕНО: плавное центрирование, только если карусель не тянет пользователь
        if (!userScrolledThumbs) centerThumbSmooth(position)
        updateSelectionIndicators()
    }

    private fun updateSelectionIndicators() {
        val f = images.getOrNull(pager.currentItem) ?: return
        badgeSelected.visibility = if (isImageSelected(f)) View.VISIBLE else View.GONE
        // ИСПРАВЛЕНО: точечно обновляем только текущую миниатюру (галочка ✓)
        thumbsAdapter.notifyItemChanged(pager.currentItem)
    }

    // ==================== Адаптер пейджера ====================
    private inner class PagerAdapter : RecyclerView.Adapter<PagerAdapter.VH>() {
        inner class VH(root: FrameLayout) : RecyclerView.ViewHolder(root) {
            val iv: ImageView = root.findViewById(R.id.ivFull)
            val pb: ProgressBar = root.findViewById(R.id.pbLoad)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val root = layoutInflater.inflate(R.layout.item_pager_image, parent, false) as FrameLayout
            return VH(root)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val f = images[position]
            holder.pb.visibility = View.VISIBLE
            holder.iv.setImageBitmap(null)
            holder.iv.tag = f.path
            holder.iv.setOnClickListener { toggleSelectionFor(f) }
            lifecycleScope.launch {
                val bmp = RemoteImageLoader.loadFull(server, user, pass, f.path)
                if (holder.iv.tag == f.path && bmp != null) {
                    holder.iv.setImageBitmap(bmp)
                    holder.pb.visibility = View.GONE
                }
            }
        }

        override fun getItemCount(): Int = images.size
    }

    // ==================== Адаптер карусели ====================
    private inner class ThumbAdapter : RecyclerView.Adapter<ThumbAdapter.VH>() {
        private var current = -1

        // ИСПРАВЛЕНО: перерисовываем только старую и новую миниатюры
        fun setCurrent(pos: Int) {
            val old = current
            current = pos
            if (old >= 0 && old != pos) notifyItemChanged(old)
            if (pos >= 0) notifyItemChanged(pos)
        }

        inner class VH(val root: FrameLayout) : RecyclerView.ViewHolder(root) {
            val iv: ImageView = root.findViewById(R.id.ivThumb)
            val check: TextView = root.findViewById(R.id.tvCheck)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val root = layoutInflater.inflate(R.layout.item_thumbnail, parent, false) as FrameLayout
            return VH(root)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val f = images[position]
            holder.iv.tag = f.path
            holder.iv.setImageBitmap(null)
            lifecycleScope.launch {
                val bmp = RemoteImageLoader.loadThumbnail(server, user, pass, f.path)
                if (holder.iv.tag == f.path && bmp != null) holder.iv.setImageBitmap(bmp)
            }

            holder.check.visibility = if (isImageSelected(f)) View.VISIBLE else View.GONE

            val stroke = if (position == current) Color.parseColor("#64B5F6") else Color.TRANSPARENT
            holder.root.foreground = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(6, stroke)
            }

            holder.root.setOnClickListener { pager.setCurrentItem(position, true) }
        }

        override fun getItemCount(): Int = images.size
    }
}