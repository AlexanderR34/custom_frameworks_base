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
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Native floating draggable in-game Tactical HUD widget.
 * Renders real-time FPS, CPU, GPU, RAM, Battery Temp, and Ping.
 */
class GamingHudOverlay(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var hudView: LinearLayout? = null
    private var isShowing = false

    private var tvFps: TextView? = null
    private var tvCpu: TextView? = null
    private var tvGpu: TextView? = null
    private var tvTemp: TextView? = null
    private var tvRam: TextView? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 60
        y = 120
    }

    fun show() {
        if (isShowing) return
        isShowing = true

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(10), dp(5), dp(10), dp(5))
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#E60C0F14"))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.parseColor("#4D00E5FF"))
            }
            background = bg
            elevation = dp(8).toFloat()
        }

        tvFps = createHudItem(root, "60 FPS", "#00E5FF", isBold = true)
        createDivider(root)
        tvCpu = createHudItem(root, "CPU 0%", "#FFB74D")
        createDivider(root)
        tvGpu = createHudItem(root, "GPU 0%", "#C084FC")
        createDivider(root)
        tvTemp = createHudItem(root, "35°C", "#00E676")
        createDivider(root)
        tvRam = createHudItem(root, "RAM 0%", "#FFFFFF")

        root.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    try {
                        windowManager.updateViewLayout(hudView, layoutParams)
                    } catch (_: Exception) {}
                    true
                }
                else -> false
            }
        }

        hudView = root
        try {
            windowManager.addView(root, layoutParams)
        } catch (_: Exception) {}
    }

    fun hide() {
        if (!isShowing) return
        isShowing = false
        hudView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }
        hudView = null
    }

    fun isVisible(): Boolean = isShowing

    fun updateMetrics(fps: Int, cpu: Int, gpu: Int, ram: Int, temp: Int, ping: Int) {
        if (!isShowing) return
        tvFps?.text = "$fps FPS"
        tvFps?.setTextColor(if (fps >= 55) Color.parseColor("#00E676") else if (fps >= 30) Color.parseColor("#FFD600") else Color.parseColor("#FF5252"))
        tvCpu?.text = "CPU $cpu%"
        tvGpu?.text = "GPU $gpu%"
        tvTemp?.text = "$temp°C"
        tvTemp?.setTextColor(if (temp >= 46) Color.parseColor("#FF5252") else if (temp >= 41) Color.parseColor("#FFD600") else Color.parseColor("#00E676"))
        tvRam?.text = "RAM $ram%"
    }

    private fun createHudItem(parent: LinearLayout, text: String, colorHex: String, isBold: Boolean = false): TextView {
        val tv = TextView(context).apply {
            this.text = text
            textSize = 10f
            setTextColor(Color.parseColor(colorHex))
            typeface = if (isBold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }
        parent.addView(tv)
        return tv
    }

    private fun createDivider(parent: LinearLayout) {
        val div = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(1), dp(12)).apply {
                gravity = Gravity.CENTER_VERTICAL
                setMargins(dp(2), 0, dp(2), 0)
            }
            setBackgroundColor(Color.parseColor("#33FFFFFF"))
        }
        parent.addView(div)
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
