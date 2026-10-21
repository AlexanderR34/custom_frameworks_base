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

package com.android.systemui.statusbar.policy

import android.content.Context
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.WakefulnessLifecycle
import java.io.PrintWriter
import javax.inject.Inject

@SysUISingleton
class PocketModeManager @Inject constructor(
    private val context: Context,
    private val wakefulnessLifecycle: WakefulnessLifecycle,
) : CoreStartable, SensorEventListener {

    companion object {
        private const val TAG = "PocketModeManager"
        const val SETTING_POCKET_MODE = "pocket_mode_enabled"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager by lazy { context.getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    private val sensorManager by lazy { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    private val proximitySensor: Sensor? by lazy { sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY) }

    private var isFeatureEnabled = false
    private var isSensorRegistered = false
    private var overlayView: View? = null

    private val settingsObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            updateFeatureEnabled()
        }
    }

    private val wakeObserver = object : WakefulnessLifecycle.Observer {
        override fun onStartedWakingUp() {
            if (isFeatureEnabled) {
                registerProximitySensor()
            }
        }

        override fun onStartedGoingToSleep() {
            unregisterProximitySensor()
            hideOverlay()
        }
    }

    override fun start() {
        try {
            context.contentResolver.registerContentObserver(
                Settings.System.getUriFor(SETTING_POCKET_MODE),
                false,
                settingsObserver,
                UserHandle.USER_ALL
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register settings observer", e)
        }

        wakefulnessLifecycle.addObserver(wakeObserver)
        updateFeatureEnabled()
    }

    private fun updateFeatureEnabled() {
        isFeatureEnabled = try {
            Settings.System.getIntForUser(
                context.contentResolver,
                SETTING_POCKET_MODE,
                0,
                UserHandle.USER_CURRENT
            ) != 0
        } catch (e: Exception) {
            false
        }

        if (!isFeatureEnabled) {
            unregisterProximitySensor()
            hideOverlay()
        }
    }

    private fun registerProximitySensor() {
        val sensor = proximitySensor ?: return
        if (!isSensorRegistered) {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI, mainHandler)
            isSensorRegistered = true
        }
    }

    private fun unregisterProximitySensor() {
        if (isSensorRegistered) {
            sensorManager.unregisterListener(this)
            isSensorRegistered = false
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        val sensor = proximitySensor ?: return

        val distance = event.values[0]
        val isNear = distance < sensor.maximumRange

        if (isNear) {
            showOverlay()
        } else {
            hideOverlay()
            // Unregister sensor once the device is safely uncovered to preserve battery
            unregisterProximitySensor()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun showOverlay() {
        if (overlayView != null) return

        val density = context.resources.displayMetrics.density

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#F20A0A0A"))
            setPadding((32 * density).toInt(), (32 * density).toInt(), (32 * density).toInt(), (32 * density).toInt())

            setOnTouchListener { _, _ -> true } // Consume all touch events
        }

        val icon = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_lock_lock)
            setColorFilter(Color.parseColor("#388E3C"))
            val iconSize = (72 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                bottomMargin = (24 * density).toInt()
            }
        }

        val isSpanish = context.resources.configuration.locales[0].language.startsWith("es")

        val title = TextView(context).apply {
            text = if (isSpanish) "Modo bolsillo activado" else "Pocket mode active"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (12 * density).toInt()
            }
        }

        val subtitle = TextView(context).apply {
            text = if (isSpanish) "No tapes la parte superior de la pantalla" else "Do not cover the top portion of the screen"
            textSize = 14f
            setTextColor(Color.parseColor("#B0B0B0"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        root.addView(icon)
        root.addView(title)
        root.addView(subtitle)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_STATUS_BAR_SUB_PANEL,
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            setTitle("PocketModeOverlay")
        }

        try {
            windowManager.addView(root, params)
            overlayView = root
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add pocket mode overlay", e)
        }
    }

    private fun hideOverlay() {
        val view = overlayView ?: return
        try {
            windowManager.removeView(view)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove pocket mode overlay", e)
        }
        overlayView = null
    }

    override fun dump(pw: PrintWriter, args: Array<out String>) {
        pw.println("PocketModeManager:")
        pw.println("  isFeatureEnabled=$isFeatureEnabled")
        pw.println("  isSensorRegistered=$isSensorRegistered")
        pw.println("  isOverlayShowing=${overlayView != null}")
    }
}
