package com.rezerv.upload

import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/**
 * DragSelectionListener — выделение диапазонов файлов «проведением пальца».
 *
 * Семантика:
 * - Долгое нажатие — включить режим, выделить элемент, forceAdd=true (выделение НЕ спадает).
 * - Зажатие + вести палец — добавить диапазон (forceAdd=true).
 * - Drag с ВЫДЕЛЕННОГО элемента (в любом направлении) — снять выделение с диапазона.
 * - Drag с НЕВЫДЕЛЕННОГО элемента — добавить диапазон.
 * - Тап — переключить один элемент (жест не перехватывается).
 * - Касание пустой области под списком — обычная прокрутка.
 * - У верхнего/нижнего края — автоскролл.
 */
class DragSelectionListener(
    private val recyclerView: RecyclerView,
    private val isSelectionActive: () -> Boolean,
    private val onStartSelection: (Int) -> Unit,          // long-press → старт режима
    private val onDragStart: (Int, Boolean) -> Unit,      // (якорь, forceAdd)
    private val onRangeSelect: (Int, Int) -> Unit         // (якорь, текущая позиция)
) : RecyclerView.OnItemTouchListener {

    private val handler = Handler(Looper.getMainLooper())
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val touchSlop = ViewConfiguration.get(recyclerView.context).scaledTouchSlop

    private var downX = 0f
    private var downY = 0f
    private var downPos = -1
    private var lastPos = -1
    private var moved = false
    private var dragging = false
    private var longPressFired = false

    private val longPressRunnable = Runnable {
        if (!moved && downPos != -1 && !isSelectionActive()) {
            longPressFired = true
            dragging = true
            lastPos = downPos
            onStartSelection(downPos)
            // ИСПРАВЛЕНО: long-press принудительно включает режим добавления,
            // иначе якорь уже выделен и авто-режим сразу снял бы выделение
            onDragStart(downPos, true)
            recyclerView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                downY = e.y
                downPos = positionAt(e.x, e.y)
                lastPos = -1
                moved = false
                dragging = false
                longPressFired = false
                handler.removeCallbacks(longPressRunnable)
                if (downPos != -1) handler.postDelayed(longPressRunnable, longPressTimeout)
            }

            MotionEvent.ACTION_MOVE -> {
                // Реальное смещение пальца (не микродвижение тапа)
                if (!moved && (abs(e.x - downX) > touchSlop * 2 || abs(e.y - downY) > touchSlop * 2)) {
                    moved = true
                    handler.removeCallbacks(longPressRunnable)
                    // Drag в уже включённом режиме: режим определяется по якорю
                    if (!dragging && isSelectionActive() && downPos != -1) {
                        dragging = true
                        onDragStart(downPos, false) // false = авто-режим по состоянию якоря
                    }
                }
                // Перехват ТОЛЬКО после реального смещения — тапы работают как обычно
                if ((dragging || longPressFired) && moved) return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                // Если сработало долгое нажатие (палец не двинулся) — глотаем UP,
                // чтобы по элементу не прошёл клик
                if (longPressFired && !moved) {
                    dragging = false
                    longPressFired = false
                    return true
                }
                dragging = false
                longPressFired = false
            }
        }
        return false
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && !longPressFired) return
                // Если палец в "дырке"/padding — берём последнюю валидную позицию,
                // чтобы последний файл не терялся
                val pos = positionAt(e.x, e.y).takeIf { it != -1 } ?: lastPos
                if (pos != -1 && downPos != -1) {
                    lastPos = pos
                    onRangeSelect(downPos, pos)
                }
                autoScroll(e.y)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                longPressFired = false
            }
        }
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        if (disallowIntercept) handler.removeCallbacks(longPressRunnable)
    }

    private fun positionAt(x: Float, y: Float): Int {
        val child = recyclerView.findChildViewUnder(x, y) ?: return -1
        return recyclerView.getChildAdapterPosition(child)
    }

    private fun autoScroll(y: Float) {
        val h = recyclerView.height
        val edge = h * 0.15f
        when {
            y < edge -> recyclerView.scrollBy(0, -30)
            y > h - edge -> recyclerView.scrollBy(0, 30)
        }
    }
}