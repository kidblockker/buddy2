package com.buddy.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.*

class WaveformView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class State { IDLE, LISTENING, THINKING, SPEAKING }

    private var state = State.IDLE
    private var phase = 0f
    private var amp   = 0f
    private var targetAmp = 0f

    private val animator = ValueAnimator.ofFloat(0f, (2 * PI).toFloat()).apply {
        duration = 1800
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            amp = amp + (targetAmp - amp) * 0.12f
            invalidate()
        }
    }

    private val IDLE_COLOR     = Color.parseColor("#1A3A5C")
    private val LISTEN_COLOR   = Color.parseColor("#00D4FF")
    private val THINK_COLOR    = Color.parseColor("#7C3AED")
    private val SPEAK_COLOR    = Color.parseColor("#00FF88")

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = 3f
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = 8f
        maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
    }
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat(); val cy = h / 2f
        val color = when (state) {
            State.IDLE      -> IDLE_COLOR
            State.LISTENING -> LISTEN_COLOR
            State.THINKING  -> THINK_COLOR
            State.SPEAKING  -> SPEAK_COLOR
        }

        for (i in 0 until 3) {
            val wAmp  = amp * (1f - i * 0.22f) * (h * 0.38f)
            val freq  = 1f + i * 0.5f
            val wPhase= phase + i * (PI.toFloat() / 3)
            val alpha = 1f - i * 0.28f

            path.reset()
            for (x in 0..w.toInt() step 3) {
                val y = cy + wAmp * sin((x / w * 2f * PI.toFloat() * freq + wPhase).toDouble()).toFloat()
                if (x == 0) path.moveTo(x.toFloat(), y) else path.lineTo(x.toFloat(), y)
            }

            glowPaint.color = color; glowPaint.alpha = (70 * alpha).toInt()
            canvas.drawPath(path, glowPaint)

            linePaint.color = color; linePaint.alpha = (240 * alpha).toInt()
            linePaint.strokeWidth = 3.5f - i * 0.8f
            canvas.drawPath(path, linePaint)
        }

        // Center pulse dot
        val dotR = 5f + amp * 6f
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; style = Paint.Style.FILL
            maskFilter = BlurMaskFilter(dotR * 2.5f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle(w / 2f, cy, dotR, dotPaint)
    }

    fun setState(s: State) {
        state = s
        targetAmp = when (s) {
            State.IDLE      -> 0.06f
            State.LISTENING -> 0.45f
            State.THINKING  -> 0.28f
            State.SPEAKING  -> 0.65f
        }
        if (!animator.isRunning) animator.start()
    }

    override fun onAttachedToWindow()  { super.onAttachedToWindow(); animator.start() }
    override fun onDetachedFromWindow() { animator.cancel(); super.onDetachedFromWindow() }
}
