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

package com.android.systemui.statusbar.phone.gaming

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * Native floating aiming crosshair overlay for SystemUI.
 */
class GamingCrosshairOverlay(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var crosshairView: CrosshairCanvasView? = null
    private var isShowing = false

    var style: Int = 0 // 0: Classic Cross, 1: Dot, 2: Circle Dot, 3: T-Cross
    var color: Int = Color.parseColor("#00E5FF")
    var sizeDp: Int = 24

    private val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.CENTER
    }

    fun show() {
        if (isShowing) return
        isShowing = true
        val view = CrosshairCanvasView(context)
        crosshairView = view
        try {
            windowManager.addView(view, layoutParams)
        } catch (_: Exception) {}
    }

    fun hide() {
        if (!isShowing) return
        isShowing = false
        crosshairView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }
        crosshairView = null
    }

    fun toggle(): Boolean {
        if (isShowing) hide() else show()
        return isShowing
    }

    fun isVisible(): Boolean = isShowing

    inner class CrosshairCanvasView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(2).toFloat()
            color = this@GamingCrosshairOverlay.color
        }

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = this@GamingCrosshairOverlay.color
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val d = dp(sizeDp + 12)
            setMeasuredDimension(d, d)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val radius = dp(sizeDp / 2).toFloat()

            when (style) {
                0 -> {
                    // Classic Cross
                    canvas.drawLine(cx - radius, cy, cx - dp(3), cy, paint)
                    canvas.drawLine(cx + dp(3), cy, cx + radius, cy, paint)
                    canvas.drawLine(cx, cy - radius, cx, cy - dp(3), paint)
                    canvas.drawLine(cx, cy + dp(3), cy, cx, cy + radius, paint)
                    canvas.drawCircle(cx, cy, dp(2).toFloat(), fillPaint)
                }
                1 -> {
                    // Dot
                    canvas.drawCircle(cx, cy, dp(4).toFloat(), fillPaint)
                }
                2 -> {
                    // Circle Dot
                    canvas.drawCircle(cx, cy, radius, paint)
                    canvas.drawCircle(cx, cy, dp(3).toFloat(), fillPaint)
                }
                3 -> {
                    // T-Cross
                    canvas.drawLine(cx - radius, cy, cx - dp(2), cy, paint)
                    canvas.drawLine(cx + dp(2), cy, cx + radius, cy, paint)
                    canvas.drawLine(cx, cy, cx, cy + radius, paint)
                }
            }
        }

        private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    }
}
