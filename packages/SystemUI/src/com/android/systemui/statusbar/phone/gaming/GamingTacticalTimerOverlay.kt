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
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Native floating tactical timer countdown widget for cooldown tracking in-game.
 */
class GamingTacticalTimerOverlay(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    private var timerView: LinearLayout? = null
    private var countDownTimer: CountDownTimer? = null
    private var isShowing = false

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
        gravity = Gravity.TOP or Gravity.END
        x = 60
        y = 200
    }

    fun startTimer(durationSeconds: Int, label: String = "Cooldown") {
        hide()
        isShowing = true

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#E613171F"))
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.parseColor("#80FF5C8D"))
            }
            background = bg
            elevation = dp(8).toFloat()
        }

        val tvTime = TextView(context).apply {
            text = "${durationSeconds}s"
            textSize = 14f
            setTextColor(Color.parseColor("#FF5C8D"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val tvLabel = TextView(context).apply {
            text = label
            textSize = 9f
            setTextColor(Color.parseColor("#B3FFFFFF"))
            gravity = Gravity.CENTER
        }

        root.addView(tvTime)
        root.addView(tvLabel)

        root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX - (event.rawX - initialTouchX).toInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    try {
                        windowManager.updateViewLayout(timerView, layoutParams)
                    } catch (_: Exception) {}
                    true
                }
                else -> false
            }
        }

        root.setOnClickListener {
            hide()
        }

        timerView = root
        try {
            windowManager.addView(root, layoutParams)
        } catch (_: Exception) {}

        countDownTimer = object : CountDownTimer(durationSeconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val secs = (millisUntilFinished / 1000L).toInt()
                tvTime.text = "${secs}s"
            }

            override fun onFinish() {
                tvTime.text = "READY!"
                tvTime.setTextColor(Color.parseColor("#00E676"))
                try {
                    vibrator?.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                } catch (_: Exception) {}
                root.postDelayed({ hide() }, 3000L)
            }
        }.start()
    }

    fun hide() {
        if (!isShowing) return
        isShowing = false
        countDownTimer?.cancel()
        countDownTimer = null
        timerView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }
        timerView = null
    }

    fun isShowing(): Boolean = isShowing

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()
}
