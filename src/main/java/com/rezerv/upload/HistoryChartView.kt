package com.rezerv.upload

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HistoryChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var records: List<HistoryRecord> = emptyList()
    
    // Подготовленные данные для быстрой отрисовки
    private var counts: IntArray = IntArray(0)
    private var maxCount: Int = 1
    private var dates: List<String> = emptyList()

    private val axisPaint = Paint().apply { 
        color = 0xFF4A4A4A.toInt(); strokeWidth = dpToPx(1f); style = Paint.Style.STROKE; isAntiAlias = true 
    }
    private val barPaint = Paint().apply { 
        color = 0xFF64B5F6.toInt(); style = Paint.Style.FILL; isAntiAlias = true 
    }
    private val textPaint = Paint().apply { 
        color = 0xFFCCCCCC.toInt(); textSize = spToPx(12f); isAntiAlias = true 
    }
    
    private val sdf = SimpleDateFormat("dd.MM", Locale.getDefault())
    private val tempCalendar = Calendar.getInstance() // Переиспользуемый объект

    init {
        updateDates() // Генерируем подписи дат один раз
    }

    private fun dpToPx(dp: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics
    )

    private fun spToPx(sp: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, sp, context.resources.displayMetrics
    )

    fun setRecords(r: List<HistoryRecord>) {
        records = r
        processData() // Считаем статистику ОДИН раз при получении новых данных
        invalidate()  // Запрашиваем перерисовку
    }

    private fun updateDates() {
        val days = 7
        val dayMillis = 86400000L
        tempCalendar.apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val startToday = tempCalendar.timeInMillis
        
        dates = (0 until days).map { i ->
            val dayStart = startToday - (days - 1 - i) * dayMillis
            sdf.format(Date(dayStart))
        }
    }

    private fun processData() {
        val days = 7
        val dayMillis = 86400000L
        counts = IntArray(days)
        
        tempCalendar.apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val startToday = tempCalendar.timeInMillis

        for (r in records) {
            // Находим начало дня для конкретной записи
            tempCalendar.timeInMillis = r.time
            tempCalendar.set(Calendar.HOUR_OF_DAY, 0)
            tempCalendar.set(Calendar.MINUTE, 0)
            tempCalendar.set(Calendar.SECOND, 0)
            tempCalendar.set(Calendar.MILLISECOND, 0)
            val recordDayStart = tempCalendar.timeInMillis
            
            // Теперь разница в днях считается корректно
            val diffDays = ((startToday - recordDayStart) / dayMillis).toInt()
            if (diffDays in 0 until days) {
                counts[days - 1 - diffDays]++
            }
        }
        maxCount = maxOf(1, counts.maxOrNull() ?: 1)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f || counts.isEmpty()) return

        // Отступы в dp
        val left = dpToPx(16f)
        val right = w - dpToPx(8f)
        val top = dpToPx(16f)
        val bottom = h - dpToPx(32f)
        val textBottom = h - dpToPx(8f)

        // Ось X
        canvas.drawLine(left, bottom, right, bottom, axisPaint)

        val days = 7
        val barArea = (right - left) / days

        for (i in 0 until days) {
            val x = left + barArea * i + barArea / 2
            
            // 1. Текст даты (центрируем по X)
            val dateText = dates.getOrNull(i) ?: ""
            val textWidth = textPaint.measureText(dateText)
            canvas.drawText(dateText, x - textWidth / 2, textBottom, textPaint)
            
            // 2. Столбец
            val count = counts[i]
            if (count > 0) {
                val barH = (bottom - top) * count / maxCount
                val barW = barArea * 0.6f
                
                // RectF обеспечивает совместимость с API < 21
                val rect = RectF(x - barW / 2, bottom - barH, x + barW / 2, bottom)
                canvas.drawRoundRect(rect, dpToPx(3f), dpToPx(3f), barPaint)
                
                // 3. Текст количества над столбцом (центрируем)
                val countText = count.toString()
                val countWidth = textPaint.measureText(countText)
                canvas.drawText(countText, x - countWidth / 2, bottom - barH - dpToPx(4f), textPaint)
            }
        }
    }
}