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

package com.android.systemui.statusbar.phone.afk

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.res.R
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject

/**
 * Controller for AFK Mode / Background Stream (Screen off while app stays resumed in foreground).
 */
@SysUISingleton
class AfkController @Inject constructor(
    @Application private val context: Context,
    @Main private val mainHandler: Handler
) {

    companion object {
        private const val TAG = "AfkController"
        private const val WAKELOCK_TAG = "SystemUI:AfkModeWakeLock"
    }

    interface Callback {
        fun onAfkStateChanged(isActive: Boolean)
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val callbacks = CopyOnWriteArrayList<Callback>()

    private var wakeLock: PowerManager.WakeLock? = null
    private var overlayView: AfkOverlayView? = null
    var isAfkActive: Boolean = false
        private set

    init {
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
            setReferenceCounted(false)
        }
    }

    fun addCallback(callback: Callback) {
        callbacks.add(callback)
        callback.onAfkStateChanged(isAfkActive)
    }

    fun removeCallback(callback: Callback) {
        callbacks.remove(callback)
    }

    fun toggleAfkMode() {
        if (isAfkActive) {
            stopAfkMode()
        } else {
            startAfkMode()
        }
    }

    fun startAfkMode() {
        mainHandler.post {
            if (isAfkActive) return@post
            isAfkActive = true

            try {
                Settings.System.putInt(context.contentResolver, "afk_mode_active", 1)
                wakeLock?.acquire()
                showOverlay()
                Log.i(TAG, "AFK Mode started: 30 FPS mode active, Partial wake lock acquired & overlay mounted")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting AFK Mode", e)
            }

            notifyCallbacks()
        }
    }

    fun stopAfkMode() {
        mainHandler.post {
            if (!isAfkActive) return@post
            isAfkActive = false

            try {
                Settings.System.putInt(context.contentResolver, "afk_mode_active", 0)
                hideOverlay()
                if (wakeLock?.isHeld == true) {
                    wakeLock?.release()
                }
                Log.i(TAG, "AFK Mode stopped: Restored original FPS, Partial wake lock released & overlay unmounted")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping AFK Mode", e)
            }

            notifyCallbacks()
        }
    }

    private fun showOverlay() {
        if (overlayView != null) return

        val view = AfkOverlayView(context) {
            stopAfkMode()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    or WindowManager.LayoutParams.FLAG_FULLSCREEN
                    or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                    or WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
            PixelFormat.TRANSLUCENT
        ).apply {
            screenBrightness = 0.0f
            preferredRefreshRate = 30.0f
            preferredMinDisplayRefreshRate = 30.0f
            preferredMaxDisplayRefreshRate = 30.0f
            gravity = Gravity.FILL
            systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        }

        try {
            windowManager.addView(view, params)
            overlayView = view
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add AFK overlay view", e)
        }
    }

    private fun hideOverlay() {
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove AFK overlay view", e)
            }
            overlayView = null
        }
    }

    private fun notifyCallbacks() {
        for (cb in callbacks) {
            cb.onAfkStateChanged(isAfkActive)
        }
    }

    /**
     * Pitch-black overlay consuming all inputs, detecting double-tap/long-press to exit.
     */
    private class AfkOverlayView(
        context: Context,
        private val onExitRequested: () -> Unit
    ) : FrameLayout(context) {

        private val hintContainer: LinearLayout
        private val gestureDetector: GestureDetector
        private val hideHintHandler = Handler(Looper.getMainLooper())

        private val hideHintRunnable = Runnable {
            hintContainer.animate()
                .alpha(0f)
                .setDuration(400)
                .start()
        }

        init {
            setBackgroundColor(Color.BLACK)
            isClickable = true
            isFocusable = true

            hintContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                alpha = 0f
            }

            val titleView = TextView(context).apply {
                text = context.getString(R.string.afk_mode_overlay_title)
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setShadowLayer(8f, 0f, 0f, Color.argb(180, 255, 255, 255))
            }

            val descView = TextView(context).apply {
                text = context.getString(R.string.afk_mode_overlay_desc)
                setTextColor(Color.argb(180, 255, 255, 255))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(8f), 0, 0)
            }

            hintContainer.addView(titleView)
            hintContainer.addView(descView)

            val layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            addView(hintContainer, layoutParams)

            gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean {
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    triggerExit()
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    triggerExit()
                }

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    pulseHint()
                    return true
                }
            })

            // Initial hint reveal and fadeout
            hintContainer.animate()
                .alpha(0.85f)
                .setDuration(300)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        hideHintHandler.postDelayed(hideHintRunnable, 2200)
                    }
                })
                .start()
        }

        private fun triggerExit() {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onExitRequested()
        }

        private fun pulseHint() {
            hideHintHandler.removeCallbacks(hideHintRunnable)
            hintContainer.animate()
                .alpha(0.40f)
                .setDuration(200)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        hideHintHandler.postDelayed(hideHintRunnable, 1400)
                    }
                })
                .start()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            gestureDetector.onTouchEvent(event)
            return true // Consume all touches to protect game from accidental taps
        }

        private fun dpToPx(dp: Float): Int {
            return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                resources.displayMetrics
            ).toInt()
        }
    }
}
