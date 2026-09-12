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

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.app.ActivityTaskManager
import android.app.GameManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.res.R
import com.android.systemui.shared.system.TaskStackChangeListener
import com.android.systemui.shared.system.TaskStackChangeListeners
import com.android.systemui.statusbar.phone.afk.AfkController
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject

/**
 * Native in-OS Gaming Space & Gaming Overlay controller for SystemUI.
 * Manages in-game floating trigger handle, Material You Expressive 3 Game Booster Sidebar,
 * Touch Shield, Tactical HUD, Aiming Crosshairs, and Governor tuning.
 */
@SysUISingleton
class GamingOverlayController @Inject constructor(
    @Application private val context: Context,
    @Main private val mainHandler: Handler,
    private val afkController: AfkController
) : CoreStartable, GamingPerformanceMonitor.Listener {

    companion object {
        private const val TAG = "GamingOverlayController"
        const val SETTING_GAMING_MODE = "gaming_mode_enabled"
        const val SETTING_GAMING_PERF_MODE = "gaming_performance_mode"
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val packageManager = context.packageManager
    private val taskManager by lazy { ActivityTaskManager.getService() }

    val performanceMonitor = GamingPerformanceMonitor(context, mainHandler)
    val hudOverlay = GamingHudOverlay(context)
    val crosshairOverlay = GamingCrosshairOverlay(context)
    val tacticalTimerOverlay = GamingTacticalTimerOverlay(context)

    private var triggerView: View? = null
    private var sidebarView: View? = null
    private var touchShieldView: View? = null
    private var isSidebarExpanded = false
    private var isTouchShieldActive = false
    private var currentActiveGame: String? = null
    var isGamingModeEnabled = true
        private set

    private var currentFps = 60
    private var currentCpu = 0
    private var currentGpu = 0
    private var currentRamMb = 0
    private var currentRamPercent = 0
    private var currentBatteryTemp = 30
    private var currentCpuTemp = 35
    private var currentPing = 25

    // Cached references for live UI updates
    private var tvSidebarFps: TextView? = null
    private var tvSidebarCpu: TextView? = null
    private var tvSidebarGpu: TextView? = null
    private var tvSidebarRam: TextView? = null
    private var tvSidebarTemp: TextView? = null
    private var tvSidebarPing: TextView? = null

    private val triggerLayoutParams = WindowManager.LayoutParams(
        dp(6),
        dp(72),
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        x = 0
    }

    private val sidebarLayoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.CENTER
    }

    interface Callback {
        fun onGamingStateChanged(isEnabled: Boolean)
    }

    private val callbacks = CopyOnWriteArrayList<Callback>()

    fun addCallback(cb: Callback) {
        if (!callbacks.contains(cb)) {
            callbacks.add(cb)
        }
    }

    fun removeCallback(cb: Callback) {
        callbacks.remove(cb)
    }

    private fun notifyStateChanged() {
        callbacks.forEach { it.onGamingStateChanged(isGamingModeEnabled) }
    }

    private val taskStackChangeListener = object : TaskStackChangeListener {
        override fun onTaskStackChanged() {
            mainHandler.post { checkForegroundApp() }
        }
    }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                mainHandler.post {
                    collapseSidebar()
                    dismissTouchShield()
                }
            }
        }
    }

    private val settingsObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            updateSettingsState()
            checkForegroundApp()
        }
    }

    override fun start() {
        // Native SystemUI overlay disabled in favor of dedicated Miku GamingSidebar
        updateSettingsState()
    }

    private fun updateSettingsState() {
        isGamingModeEnabled = Settings.System.getInt(
            context.contentResolver,
            SETTING_GAMING_MODE,
            1
        ) == 1
    }

    private fun isAppGame(packageName: String): Boolean {
        // 1. Check GameSpace list in settings
        val gameList = Settings.System.getString(context.contentResolver, "gamespace_game_list")
        if (!gameList.isNullOrEmpty()) {
            val matches = gameList.split(";").any { it.split("=").firstOrNull() == packageName }
            if (matches) return true
        }

        // 2. Check ApplicationInfo category / flags
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            if ((appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0 ||
                appInfo.category == ApplicationInfo.CATEGORY_GAME) {
                return true
            }
        } catch (_: Exception) {}

        // 3. Check GameManager API
        try {
            val gm = context.getSystemService(GameManager::class.java)
            if (gm != null) {
                val mode = gm.getGameMode(packageName)
                if (mode != GameManager.GAME_MODE_UNSUPPORTED) {
                    return true
                }
            }
        } catch (_: Exception) {}

        return false
    }

    private fun checkForegroundApp() {
        if (!isGamingModeEnabled) {
            if (triggerView != null) hideTriggerHandle()
            if (isSidebarExpanded) collapseSidebar()
            currentActiveGame = null
            return
        }

        try {
            val focusedTask = taskManager.focusedRootTaskInfo
            val topPkg = focusedTask?.topActivity?.packageName
            if (topPkg.isNullOrEmpty() || topPkg == context.packageName) {
                return
            }

            val isGame = isAppGame(topPkg)
            if (isGame) {
                currentActiveGame = topPkg
                if (triggerView == null) {
                    showTriggerHandle()
                }
            } else {
                currentActiveGame = null
                if (triggerView != null) {
                    hideTriggerHandle()
                }
                if (isSidebarExpanded) {
                    collapseSidebar()
                }
                if (hudOverlay.isVisible()) {
                    hudOverlay.hide()
                }
                if (crosshairOverlay.isVisible()) {
                    crosshairOverlay.hide()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check foreground app", e)
        }
    }

    fun toggleGamingMode(): Boolean {
        isGamingModeEnabled = !isGamingModeEnabled
        Settings.System.putInt(
            context.contentResolver,
            SETTING_GAMING_MODE,
            if (isGamingModeEnabled) 1 else 0
        )
        if (isGamingModeEnabled) {
            checkForegroundApp()
        } else {
            hideTriggerHandle()
            collapseSidebar()
            hudOverlay.hide()
            crosshairOverlay.hide()
            tacticalTimerOverlay.hide()
            dismissTouchShield()
        }
        notifyStateChanged()
        return isGamingModeEnabled
    }

    fun showTriggerHandle() {
        // Disabled: Blue trigger handle replaced by dedicated Miku GamingSidebar
    }

    fun hideTriggerHandle() {
        triggerView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }
        triggerView = null
    }

    override fun dump(pw: PrintWriter, args: Array<out String>) {
        pw.println("GamingOverlayController:")
        pw.println("  isGamingModeEnabled: $isGamingModeEnabled")
        pw.println("  currentActiveGame: $currentActiveGame")
        pw.println("  isSidebarExpanded: $isSidebarExpanded")
        pw.println("  isTriggerShowing: ${triggerView != null}")
    }

    fun expandSidebar() {
        if (isSidebarExpanded || sidebarView != null) return
        isSidebarExpanded = true

        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#4D000000")) // Translucent Scrim
            setOnClickListener { collapseSidebar() }
        }

        // Material You Expressive 3 Modal Container
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val lp = FrameLayout.LayoutParams(dp(360), FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                setMargins(dp(12), dp(12), dp(12), dp(12))
            }
            layoutParams = lp
            setPadding(dp(16), dp(16), dp(16), dp(16))
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#E60C0F14")) // SurfaceContainerLowest
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), Color.parseColor("#4D00E5FF")) // OutlineVariant Monet
            }
            background = bg
            elevation = dp(24).toFloat()
            isClickable = true
        }

        buildSidebarContent(container)
        root.addView(container)

        sidebarView = root
        try {
            windowManager.addView(root, sidebarLayoutParams)
        } catch (_: Exception) {}
    }

    fun collapseSidebar() {
        if (!isSidebarExpanded) return
        isSidebarExpanded = false
        sidebarView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }
        sidebarView = null
    }

    private fun buildSidebarContent(container: LinearLayout) {
        // 1. Header (Title, Admin Badge, Close)
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val tvTitle = TextView(context).apply {
            text = "MIKU GAMING SPACE"
            textSize = 14f
            setTextColor(Color.parseColor("#00E5FF"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(tvTitle)

        val btnClose = TextView(context).apply {
            text = "✕"
            textSize = 16f
            setTextColor(Color.parseColor("#B3FFFFFF"))
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener { collapseSidebar() }
        }
        header.addView(btnClose)
        container.addView(header)

        // Divider
        container.addView(createHorizontalDivider())

        // 2. Metrics Cards Row (FPS, CPU%, GPU%, Temp, RAM)
        val metricsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dp(8), 0, dp(8))
            }
            weightSum = 4f
        }

        tvSidebarFps = createMetricPill(metricsRow, "FPS", "$currentFps", "#00E5FF")
        tvSidebarCpu = createMetricPill(metricsRow, "CPU", "$currentCpu%", "#FFB74D")
        tvSidebarGpu = createMetricPill(metricsRow, "GPU", "$currentGpu%", "#C084FC")
        tvSidebarTemp = createMetricPill(metricsRow, "TEMP", "$currentBatteryTemp°C", "#00E676")
        container.addView(metricsRow)

        // 3. Performance Governor Pills (Power Save, Balanced, Turbo)
        val govRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(32)).apply {
                setMargins(0, dp(4), 0, dp(8))
            }
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#33191F2B"))
                cornerRadius = dp(16).toFloat()
            }
            background = bg
        }
        createGovPill(govRow, "Power Save", 0)
        createGovPill(govRow, "Balanced", 1)
        createGovPill(govRow, "Turbo", 2, isDefault = true)
        container.addView(govRow)

        // 4. Quick Actions Scrollable Row
        val actionsScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dp(4), 0, dp(8))
            }
        }
        val actionsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        createActionChip(actionsRow, "HUD", hudOverlay.isVisible()) {
            if (hudOverlay.isVisible()) hudOverlay.hide() else hudOverlay.show()
            collapseSidebar()
        }
        createActionChip(actionsRow, "Crosshair", crosshairOverlay.isVisible()) {
            crosshairOverlay.toggle()
        }
        createActionChip(actionsRow, "Timer 30s", tacticalTimerOverlay.isShowing()) {
            tacticalTimerOverlay.startTimer(30, "Respawn")
            collapseSidebar()
        }
        createActionChip(actionsRow, "Touch Shield", false) {
            showTouchShield()
            collapseSidebar()
        }
        createActionChip(actionsRow, "AFK Mode", false) {
            afkController.toggleAfkMode()
            collapseSidebar()
        }
        createActionChip(actionsRow, "Boost RAM", false) {
            cleanRam()
        }
        createActionChip(actionsRow, "Screenshot", false) {
            takeScreenshot()
            collapseSidebar()
        }
        createActionChip(actionsRow, "DND", isDndEnabled()) {
            toggleDnd()
        }
        createActionChip(actionsRow, "Wi-Fi", wifiManager?.isWifiEnabled == true) {
            toggleWifi()
        }

        actionsScroll.addView(actionsRow)
        container.addView(actionsScroll)

        // 5. Audio & Brightness Sliders
        container.addView(createSliderRow("Game Volume", getGameVolume(), 100) { setGameVolume(it) })
        container.addView(createSliderRow("Brightness", getBrightness(), 255) { setBrightness(it) })
    }

    private fun createMetricPill(parent: LinearLayout, label: String, value: String, colorHex: String): TextView {
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#26191F2B"))
                cornerRadius = dp(12).toFloat()
            }
            background = bg
        }
        val tvVal = TextView(context).apply {
            text = value
            textSize = 12f
            setTextColor(Color.parseColor(colorHex))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val tvLbl = TextView(context).apply {
            text = label
            textSize = 8.5f
            setTextColor(Color.parseColor("#80FFFFFF"))
            gravity = Gravity.CENTER
        }
        col.addView(tvVal)
        col.addView(tvLbl)
        parent.addView(col)
        return tvVal
    }

    private fun createGovPill(parent: LinearLayout, title: String, mode: Int, isDefault: Boolean = false) {
        val btn = TextView(context).apply {
            text = title
            textSize = 10f
            setTextColor(if (isDefault) Color.parseColor("#00E5FF") else Color.parseColor("#B3FFFFFF"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            val bg = GradientDrawable().apply {
                setColor(if (isDefault) Color.parseColor("#4D00E5FF") else Color.TRANSPARENT)
                cornerRadius = dp(14).toFloat()
            }
            background = bg
            setOnClickListener {
                setPerformanceGovernor(mode)
            }
        }
        parent.addView(btn)
    }

    private fun createActionChip(parent: LinearLayout, title: String, isActive: Boolean, onClick: () -> Unit) {
        val chip = TextView(context).apply {
            text = title
            textSize = 10.5f
            setTextColor(if (isActive) Color.parseColor("#00E5FF") else Color.parseColor("#E6FFFFFF"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(6), dp(12), dp(6))
            val bg = GradientDrawable().apply {
                setColor(if (isActive) Color.parseColor("#3300E5FF") else Color.parseColor("#26191F2B"))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), if (isActive) Color.parseColor("#00E5FF") else Color.parseColor("#33FFFFFF"))
            }
            background = bg
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, dp(8), 0)
            }
            layoutParams = lp
            setOnClickListener { onClick() }
        }
        parent.addView(chip)
    }

    private fun createSliderRow(title: String, initialProgress: Int, max: Int, onProgress: (Int) -> Unit): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(28))
        }
        val tv = TextView(context).apply {
            text = title
            textSize = 10f
            setTextColor(Color.parseColor("#B3FFFFFF"))
            layoutParams = LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val sb = SeekBar(context).apply {
            this.max = max
            progress = initialProgress
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) onProgress(progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        row.addView(tv)
        row.addView(sb)
        return row
    }

    private fun createHorizontalDivider(): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
                setMargins(0, dp(8), 0, dp(4))
            }
            setBackgroundColor(Color.parseColor("#1FFFFFFF"))
        }
    }

    private fun showTouchShield() {
        if (isTouchShieldActive || touchShieldView != null) return
        isTouchShieldActive = true

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#99000000"))
        }

        val tvHint = TextView(context).apply {
            text = "🛡️ Touch Shield Active\nDouble tap anywhere to unlock"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
            layoutParams = lp
        }
        root.addView(tvHint)

        var lastTapTime = 0L
        root.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val now = System.currentTimeMillis()
                if (now - lastTapTime < 400L) {
                    dismissTouchShield()
                } else {
                    lastTapTime = now
                }
                true
            } else {
                true
            }
        }

        touchShieldView = root
        try {
            windowManager.addView(root, params)
        } catch (_: Exception) {}
    }

    private fun dismissTouchShield() {
        if (!isTouchShieldActive) return
        isTouchShieldActive = false
        touchShieldView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }
        touchShieldView = null
    }

    private fun takeScreenshot() {
        try {
            val intent = Intent("com.android.systemui.SCREENSHOT")
            context.sendBroadcast(intent)
        } catch (_: Exception) {}
    }

    private fun setPerformanceGovernor(mode: Int) {
        Settings.System.putInt(context.contentResolver, SETTING_GAMING_PERF_MODE, mode)
        try {
            val gov = when (mode) {
                0 -> "powersave"
                1 -> "schedutil"
                else -> "performance"
            }
            for (i in 0..7) {
                val f = File("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_governor")
                if (f.exists() && f.canWrite()) {
                    FileOutputStream(f).use { it.write(gov.toByteArray()) }
                }
            }
        } catch (_: Exception) {}
    }

    private fun cleanRam() {
        try {
            val procs = activityManager.runningAppProcesses ?: return
            for (p in procs) {
                if (p.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND) {
                    p.pkgList?.forEach { activityManager.killBackgroundProcesses(it) }
                }
            }
            Toast.makeText(context, "RAM Boosted: Background tasks cleaned", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }

    private fun isDndEnabled(): Boolean {
        return try {
            notificationManager?.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        } catch (_: Exception) {
            false
        }
    }

    private fun toggleDnd() {
        try {
            if (isDndEnabled()) {
                notificationManager?.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            } else {
                notificationManager?.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            }
        } catch (_: Exception) {}
    }

    private fun toggleWifi() {
        try {
            wifiManager?.isWifiEnabled = !(wifiManager?.isWifiEnabled ?: true)
        } catch (_: Exception) {}
    }

    private fun getGameVolume(): Int {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return if (max > 0) (cur * 100) / max else 70
    }

    private fun setGameVolume(percent: Int) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (percent * max) / 100
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
    }

    private fun getBrightness(): Int {
        return try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (_: Exception) {
            128
        }
    }

    private fun setBrightness(value: Int) {
        try {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value.coerceIn(10, 255))
        } catch (_: Exception) {}
    }

    override fun onMetricsUpdated(
        fps: Int,
        cpuPercent: Int,
        gpuPercent: Int,
        ramUsedMb: Int,
        ramPercent: Int,
        batteryTemp: Int,
        cpuTemp: Int,
        pingMs: Int
    ) {
        currentFps = fps
        currentCpu = cpuPercent
        currentGpu = gpuPercent
        currentRamMb = ramUsedMb
        currentRamPercent = ramPercent
        currentBatteryTemp = batteryTemp
        currentCpuTemp = cpuTemp
        currentPing = pingMs

        hudOverlay.updateMetrics(fps, cpuPercent, gpuPercent, ramPercent, batteryTemp, pingMs)

        if (isSidebarExpanded) {
            tvSidebarFps?.text = "$fps"
            tvSidebarCpu?.text = "$cpuPercent%"
            tvSidebarGpu?.text = "$gpuPercent%"
            tvSidebarTemp?.text = "$batteryTemp°C"
        }
    }

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()
}
