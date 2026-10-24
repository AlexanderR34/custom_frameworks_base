/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.phone.island

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

/**
 * Animated 4-bar audio equalizer waveform for Dynamic Island .
 */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#38BDF8") // HyperOS Electric Cyan
    }

    private val barRect = RectF()
    private val density = resources.displayMetrics.density
    private val barWidth = 3f * density
    private val barSpacing = 2.5f * density
    private val cornerRadius = 1.5f * density

    private var isPlaying = false
    private var animator: ValueAnimator? = null
    private var animTimeMs = 0L
    private var lastTimeMs = 0L

    init {
        setWillNotDraw(false)
    }

    fun setPlaying(playing: Boolean) {
        if (isPlaying == playing) return
        isPlaying = playing
        if (playing) {
            startAnimation()
        } else {
            stopAnimation()
        }
    }

    fun setBarColor(color: Int) {
        barPaint.color = color
        invalidate()
    }

    private fun startAnimation() {
        animator?.cancel()
        lastTimeMs = SystemClock.elapsedRealtime()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val now = SystemClock.elapsedRealtime()
                animTimeMs += (now - lastTimeMs)
                lastTimeMs = now
                invalidate()
            }
            start()
        }
    }

    private fun stopAnimation() {
        animator?.cancel()
        animator = null
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isPlaying) startAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val totalWidth = (4 * barWidth + 3 * barSpacing).toInt()
        val totalHeight = (18f * density).toInt()
        setMeasuredDimension(totalWidth, totalHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val maxHeight = height.toFloat().coerceAtLeast(10f * density)
        val centerY = height / 2f
        val t = animTimeMs / 1000f

        val heights = if (isPlaying) {
            floatArrayOf(
                (0.35f + 0.65f * (0.5f + 0.5f * sin(t * 8.0f))),
                (0.20f + 0.80f * (0.5f + 0.5f * sin(t * 12.0f + 1.2f))),
                (0.40f + 0.60f * (0.5f + 0.5f * sin(t * 7.0f + 2.4f))),
                (0.25f + 0.75f * (0.5f + 0.5f * sin(t * 10.5f + 3.8f)))
            )
        } else {
            floatArrayOf(0.40f, 0.60f, 0.45f, 0.55f)
        }

        var startX = 0f
        for (i in 0 until 4) {
            val barH = (maxHeight * heights[i]).coerceAtLeast(4f * density)
            barRect.set(
                startX,
                centerY - barH / 2f,
                startX + barWidth,
                centerY + barH / 2f
            )
            canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, barPaint)
            startX += barWidth + barSpacing
        }
    }
}
