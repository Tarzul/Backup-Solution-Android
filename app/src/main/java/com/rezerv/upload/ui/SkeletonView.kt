package com.rezerv.upload.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.rezerv.upload.R

class SkeletonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val baseColor: Int = ContextCompat.getColor(context, R.color.skeleton_base)
    private val shimmerColor: Int = ContextCompat.getColor(context, R.color.skeleton_shimmer)
    private val highlightColor: Int = ContextCompat.getColor(context, R.color.skeleton_highlight)

    private val paint = Paint()
    private var gradient: LinearGradient? = null
    private var translateAnimator: ValueAnimator? = null
    private var gradientTranslation = 0f

    init {
        paint.color = baseColor
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return

        gradient = LinearGradient(
            0f, 0f, w * 2f, 0f,
            intArrayOf(baseColor, shimmerColor, highlightColor, shimmerColor, baseColor),
            floatArrayOf(0f, 0.3f, 0.5f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = gradient
        startAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        gradient?.let {
            canvas.save()
            canvas.translate(gradientTranslation, 0f)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            canvas.restore()
        }
    }

    private fun startAnimation() {
        translateAnimator?.cancel()
        translateAnimator = ValueAnimator.ofFloat(-width.toFloat(), width.toFloat()).apply {
            duration = 1500L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                gradientTranslation = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stopAnimation() {
        translateAnimator?.cancel()
        translateAnimator = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (width > 0) startAnimation()
    }
}