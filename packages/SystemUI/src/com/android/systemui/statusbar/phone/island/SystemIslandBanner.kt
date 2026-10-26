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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.android.systemui.res.R

/**
 * Transient Dynamic Island System Banner .
 * Smoothly expands from the punch-hole camera for system events
 * (HyperCharge 90W Ultra, LDAC Hi-Res Audio, Ringer/DND, etc.).
 */
class SystemIslandBanner(
    private val context: Context,
) {
    companion object {
        private const val DEFAULT_DURATION_MS = 3600L
        private const val TOUCH_OUTSIDE_GUARD_TIME_MS = 300L
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val bannerView: View = LayoutInflater.from(context).inflate(R.layout.system_island_banner, null)
    private val iconView: ImageView = bannerView.findViewById(R.id.system_island_icon)
    private val titleView: TextView = bannerView.findViewById(R.id.system_island_title)
    private val subtitleView: TextView = bannerView.findViewById(R.id.system_island_subtitle)
    private val badgeView: TextView = bannerView.findViewById(R.id.system_island_badge)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isAttached = false
    private var lastShowTime = 0L

    private val autoDismissRunnable = Runnable {
        dismissWithAnimation()
    }

    init {
        bannerView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                if (SystemClock.elapsedRealtime() - lastShowTime > TOUCH_OUTSIDE_GUARD_TIME_MS) {
                    dismissWithAnimation()
                }
                true
            } else if (event.action == MotionEvent.ACTION_DOWN) {
                dismissWithAnimation()
                true
            } else {
                false
            }
        }
    }

    fun show(
        @DrawableRes iconRes: Int,
        title: String,
        subtitle: String? = null,
        badge: String? = null,
        tintColor: Int = Color.parseColor("#38BDF8"),
        durationMs: Long = DEFAULT_DURATION_MS,
    ) {
        mainHandler.removeCallbacks(autoDismissRunnable)

        // Set contents
        iconView.setImageResource(iconRes)
        iconView.setColorFilter(tintColor)

        titleView.text = title

        if (!subtitle.isNullOrBlank()) {
            subtitleView.text = subtitle
            subtitleView.visibility = View.VISIBLE
        } else {
            subtitleView.visibility = View.GONE
        }

        if (!badge.isNullOrBlank()) {
            badgeView.text = badge
            badgeView.setTextColor(tintColor)
            badgeView.visibility = View.VISIBLE
        } else {
            badgeView.visibility = View.GONE
        }

        // Apply dark squircle styling with subtle glow stroke
        val root = bannerView.findViewById<View>(R.id.system_island_banner_root)
        val res = context.resources
        val strokeColor = Color.argb(60, Color.red(tintColor), Color.green(tintColor), Color.blue(tintColor))
        val shape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 22f, res.displayMetrics)
            setColor(Color.parseColor("#121217")) // Deep OLED surface
            setStroke(
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.2f, res.displayMetrics).toInt(),
                strokeColor
            )
        }
        root.background = shape

        // Compute positioning directly below status bar / punch hole
        val statusBarHeight = try {
            res.getDimensionPixelSize(com.android.internal.R.dimen.status_bar_height)
        } catch (e: Exception) {
            (28 * res.displayMetrics.density).toInt()
        }
        val topMargin = (statusBarHeight * 0.25f).toInt()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_STATUS_BAR_SUB_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            setTitle("SystemIslandBanner")
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = topMargin
        }

        lastShowTime = SystemClock.elapsedRealtime()

        if (!isAttached) {
            bannerView.alpha = 0f
            bannerView.scaleX = 0.7f
            bannerView.scaleY = 0.7f
            bannerView.translationY = -25f

            try {
                windowManager.addView(bannerView, params)
                isAttached = true
            } catch (e: Exception) {
                return
            }
        } else {
            try {
                windowManager.updateViewLayout(bannerView, params)
            } catch (ignored: Exception) {}
        }

        bannerView.animate().cancel()
        bannerView.animate()
            .setListener(null)
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(280)
            .setInterpolator(DecelerateInterpolator())
            .start()

        mainHandler.postDelayed(autoDismissRunnable, durationMs)
    }

    fun dismissWithAnimation() {
        mainHandler.removeCallbacks(autoDismissRunnable)
        if (!isAttached) return

        bannerView.animate().cancel()
        bannerView.animate()
            .alpha(0f)
            .scaleX(0.72f)
            .scaleY(0.72f)
            .translationY(-20f)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    bannerView.animate().setListener(null)
                    if (isAttached) {
                        try {
                            windowManager.removeViewImmediate(bannerView)
                        } catch (ignored: Exception) {}
                        isAttached = false
                    }
                }
            })
            .start()
    }

    val isShowing: Boolean
        get() = isAttached
}
