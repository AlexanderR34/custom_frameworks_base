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

import android.app.AppOpsManager
import android.app.NotificationManager
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.TetheringManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.UserHandle
import android.provider.Settings
import android.telecom.TelecomManager
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.android.systemui.res.R
import kotlin.math.abs

/**
 * Master Controller for the Independent Dynamic Island .
 * Manages full multi-activity states: Media, Calls, 90W Ultra HyperCharge, Screen Recording,
 * Timers, GPS Navigation, Downloads, Flashlight, Wi-Fi Hotspot, Cast, Hardware Privacy,
 * Clipboard preview, and System Banners.
 */
class DynamicIslandController(
    private val context: Context,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
) {
    companion object {
        private const val TAG = "DynamicIslandController"
        @JvmField
        @Volatile
        var isRightSideOccluded: Boolean = false
        // Setting keys
        const val SETTING_DYNAMIC_ISLAND = "status_bar_dynamic_island_enabled"
        const val SETTING_ISLAND_HIDE_CHIPS = "status_bar_island_hide_chips"
        const val SETTING_ISLAND_MUSIC = "status_bar_island_music"
        const val SETTING_ISLAND_CHARGING = "status_bar_island_charging"
        const val SETTING_ISLAND_CALL = "status_bar_island_call"
        const val SETTING_ISLAND_TIMER = "status_bar_island_timer"
        const val SETTING_ISLAND_SCREENRECORD = "status_bar_island_screenrecord"
        const val SETTING_ISLAND_BLUETOOTH = "status_bar_island_bluetooth"
        const val SETTING_ISLAND_NAVIGATION = "status_bar_island_navigation"
        const val SETTING_ISLAND_DOWNLOAD = "status_bar_island_download"
        const val SETTING_ISLAND_TORCH = "status_bar_island_torch"
        const val SETTING_ISLAND_HOTSPOT = "status_bar_island_hotspot"
        const val SETTING_ISLAND_CAST = "status_bar_island_cast"
        const val SETTING_ISLAND_PRIVACY = "status_bar_island_privacy"
        const val SETTING_ISLAND_CLIPBOARD = "status_bar_island_clipboard"

        // Geometry settings (punch-hole)
        const val SETTING_ISLAND_X_OFFSET = "status_bar_island_x_offset"
        const val SETTING_ISLAND_Y_OFFSET = "status_bar_island_y_offset"
        const val SETTING_ISLAND_PILL_WIDTH = "status_bar_island_pill_width"
        const val SETTING_ISLAND_PILL_HEIGHT = "status_bar_island_pill_height"
        const val SETTING_ISLAND_NOTCH_MARGIN = "status_bar_island_notch_margin"
        const val SETTING_ISLAND_MEDIA_TIMEOUT = "status_bar_island_media_timeout"
        const val SETTING_ISLAND_AVOID_OVERLAP = "status_bar_island_avoid_overlap"
        const val SETTING_ISLAND_BOUNCE = "status_bar_island_bounce"
        const val SETTING_ISLAND_DURATION = "status_bar_island_duration"
        const val SETTING_ISLAND_ICON_STYLE = "status_bar_island_icon_style"
        const val DEFAULT_MEDIA_TIMEOUT_SECONDS = 30

        // External broadcast actions
        const val ACTION_UPDATE_NAVIGATION = "com.android.systemui.island.UPDATE_NAVIGATION"
        const val ACTION_STOP_NAVIGATION = "com.android.systemui.island.STOP_NAVIGATION"
        const val ACTION_UPDATE_DOWNLOAD = "com.android.systemui.island.UPDATE_DOWNLOAD"
        const val ACTION_UPDATE_CAST = "com.android.systemui.island.UPDATE_CAST"
        const val ACTION_UPDATE_TIMER = "com.android.systemui.island.UPDATE_TIMER"

        private const val TICK_INTERVAL_MS = 1000L
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    private val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager

    private var islandView: DynamicIslandView? = null
    private var windowLayoutParams: WindowManager.LayoutParams? = null
    private var isWindowAttached = false

    private var activeMediaController: MediaController? = null
    private var isFeatureEnabled = true
    private var isExpanded = false
    var onIslandWidthChanged: ((widthPx: Int, isExpanded: Boolean, isVisible: Boolean) -> Unit)? = null
    // Feature toggles
    private var isMusicEnabled = true
    private var isChargingEnabled = true
    private var isCallEnabled = true
    private var isTimerEnabled = true
    private var isRecordEnabled = true
    private var isBluetoothEnabled = true
    private var isNavigationEnabled = true
    private var isDownloadEnabled = true
    private var isTorchEnabled = true
    private var isHotspotEnabled = true
    private var isCastEnabled = true
    private var isPrivacyEnabled = true
    private var isClipboardEnabled = true

    // Geometry and notch clearance ()
    private var xOffsetDp: Int = 0
    private var yOffsetDp: Int = 0
    private var pillWidthDp: Float = 110f
    private var pillHeightDp: Float = 34f
    private var notchMarginDp: Float = 5f

    // State holders
    private var mediaState = IslandMediaState()
    private var callState = IslandCallState()
    private var timerState = IslandTimerState()
    private var chargingState = IslandChargingState()
    private var recordState = IslandRecordState()
    private var bannerState = IslandBannerState()
    private var navState = IslandNavigationState()
    private var downloadState = IslandDownloadState()
    private var torchState = IslandTorchState()
    private var hotspotState = IslandHotspotState()
    private var castState = IslandCastState()
    private var privacyState = IslandPrivacyState()
    private var clipboardState = IslandClipboardState()

    private var wasCharging = false
    private var lastRingerMode: Int? = null
    private var lastInterruptionFilter: Int? = null

    // Runnables
    private val progressTickRunnable = object : Runnable {
        override fun run() {
            updateTick()
            if (shouldKeepTicking()) {
                mainHandler.postDelayed(this, TICK_INTERVAL_MS)
            }
        }
    }

    private val mediaPauseHideRunnable = Runnable {
        if (!mediaState.isPlaying) {
            mediaState = mediaState.copy(hasActiveMedia = false)
            refreshCurrentActivity()
        }
    }

    private val bannerDismissRunnable = Runnable {
        bannerState = bannerState.copy(isActive = false)
        refreshCurrentActivity()
    }

    private val clipboardDismissRunnable = Runnable {
        if (isExpanded && resolveActiveActivity() == IslandActivityType.CLIPBOARD) {
            mainHandler.postDelayed(clipboardDismissRunnable, 2000L)
            return@Runnable
        }
        clipboardState = clipboardState.copy(isActive = false)
        refreshCurrentActivity()
    }

    private val downloadDismissRunnable = Runnable {
        downloadState = downloadState.copy(isDownloading = false, isCompleted = false, isFailed = false, isCancelled = false)
        refreshCurrentActivity()
    }
    private val hotspotBannerDismissRunnable = Runnable {
        if (hotspotState.isEnabled && hotspotState.showIntroBanner) {
            hotspotState = hotspotState.copy(showIntroBanner = false)
            refreshCurrentActivity()
        }
    }


    // Callbacks & Observers

    private val mediaCallback = object : MediaController.Callback() {

        override fun onPlaybackStateChanged(state: PlaybackState?) {

            mainHandler.post { handlePlaybackStateChanged(state) }

        }


        override fun onMetadataChanged(metadata: MediaMetadata?) {

            mainHandler.post { handleMetadataChanged(metadata) }

        }


        override fun onSessionDestroyed() {

            mainHandler.post { findActiveMediaSession() }

        }

    }


    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->

        mainHandler.post { updateActiveController(controllers) }

    }


    private val torchCallback = object : CameraManager.TorchCallback() {

        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {

            mainHandler.post {

                torchState = IslandTorchState(isOn = enabled)

                refreshCurrentActivity()

            }

        }

    }


    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {

        try {

            val clip = clipboardManager?.primaryClip

            if (clip != null && clip.itemCount > 0) {

                val text = clip.getItemAt(0)?.coerceToText(context)?.toString() ?: ""

                if (text.isNotBlank()) {

                    mainHandler.post { handleClipboardText(text) }

                }

            }

        } catch (e: Exception) {

            Log.e(TAG, "Error in clipListener", e)

        }

    }


    private val opActiveCallback = AppOpsManager.OnOpActiveChangedListener { op, _, pkgName, active ->

        mainHandler.post { handlePrivacyOp(op, active, pkgName) }

    }


    private val broadcastReceiver = object : BroadcastReceiver() {

        override fun onReceive(ctx: Context?, intent: Intent?) {

            if (ctx == null || intent == null) return

            when (intent.action) {

                Intent.ACTION_POWER_CONNECTED,

                Intent.ACTION_BATTERY_CHANGED -> handleBatteryEvent(intent)

                Intent.ACTION_POWER_DISCONNECTED -> {

                    wasCharging = false

                    chargingState = chargingState.copy(isCharging = false)

                    refreshCurrentActivity()

                }

                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> handleBluetoothEvent(intent)

                AudioManager.RINGER_MODE_CHANGED_ACTION -> handleRingerModeEvent(intent)

                NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> handleDndEvent()

                "com.android.systemui.screenrecord.START" -> {

                    recordState = IslandRecordState(isRecording = true, startTimeMs = SystemClock.elapsedRealtime())

                    refreshCurrentActivity()

                }

                "com.android.systemui.screenrecord.STOP" -> {

                    recordState = IslandRecordState(isRecording = false)

                    refreshCurrentActivity()

                }

                "android.net.wifi.WIFI_AP_STATE_CHANGED" -> handleHotspotEvent(intent)

                "android.net.conn.TETHER_STATE_CHANGED" -> checkHotspotState()

                ACTION_UPDATE_NAVIGATION -> handleNavigationIntent(intent)

                ACTION_STOP_NAVIGATION -> {

                    navState = IslandNavigationState(isActive = false)

                    refreshCurrentActivity()

                }

                ACTION_UPDATE_DOWNLOAD -> handleDownloadIntent(intent)

                ACTION_UPDATE_CAST -> handleCastIntent(intent)

                ACTION_UPDATE_TIMER -> handleTimerIntent(intent)

            }

        }

    }


    private val settingsObserver = object : ContentObserver(mainHandler) {

        override fun onChange(selfChange: Boolean) {

            checkEnabledSettings()

        }

    }


    fun attach() {

        checkEnabledSettings()


        // Register settings observer for master switch, sub-features and notch geometry

        val settingsToObserve = listOf(

            SETTING_DYNAMIC_ISLAND,

            SETTING_ISLAND_HIDE_CHIPS,

            SETTING_ISLAND_MUSIC,

            SETTING_ISLAND_CHARGING,

            SETTING_ISLAND_CALL,

            SETTING_ISLAND_TIMER,

            SETTING_ISLAND_SCREENRECORD,

            SETTING_ISLAND_BLUETOOTH,

            SETTING_ISLAND_NAVIGATION,

            SETTING_ISLAND_DOWNLOAD,

            SETTING_ISLAND_TORCH,

            SETTING_ISLAND_HOTSPOT,

            SETTING_ISLAND_CAST,

            SETTING_ISLAND_PRIVACY,

            SETTING_ISLAND_CLIPBOARD,

            SETTING_ISLAND_X_OFFSET,

            SETTING_ISLAND_Y_OFFSET,

            SETTING_ISLAND_PILL_WIDTH,

            SETTING_ISLAND_PILL_HEIGHT,

            SETTING_ISLAND_NOTCH_MARGIN,

            SETTING_ISLAND_MEDIA_TIMEOUT,

            SETTING_ISLAND_AVOID_OVERLAP,

            SETTING_ISLAND_BOUNCE,

            SETTING_ISLAND_DURATION,

            SETTING_ISLAND_ICON_STYLE

        )

        for (setting in settingsToObserve) {

            try {

                context.contentResolver.registerContentObserver(

                    Settings.System.getUriFor(setting),

                    false,

                    settingsObserver,

                    UserHandle.USER_ALL

                )

            } catch (ignored: Exception) {}

        }


        // Register broadcast receiver

        val filter = IntentFilter().apply {

            addAction(Intent.ACTION_POWER_CONNECTED)

            addAction(Intent.ACTION_POWER_DISCONNECTED)

            addAction(Intent.ACTION_BATTERY_CHANGED)

            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)

            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)

            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)

            addAction("com.android.systemui.screenrecord.START")

            addAction("com.android.systemui.screenrecord.STOP")

            addAction("android.net.wifi.WIFI_AP_STATE_CHANGED")

            addAction("android.net.conn.TETHER_STATE_CHANGED")

            addAction(ACTION_UPDATE_NAVIGATION)

            addAction(ACTION_STOP_NAVIGATION)

            addAction(ACTION_UPDATE_DOWNLOAD)

            addAction(ACTION_UPDATE_CAST)

            addAction(ACTION_UPDATE_TIMER)

        }

        try {

            context.registerReceiver(broadcastReceiver, filter, Context.RECEIVER_EXPORTED)

        } catch (e: Exception) {

            try { context.registerReceiver(broadcastReceiver, filter) } catch (ignored: Exception) {}

        }


        // Register media session listener

        try {

            mediaSessionManager.addOnActiveSessionsChangedListener(sessionsListener, null, mainHandler)

            findActiveMediaSession()

        } catch (ignored: Exception) {}


        // Register camera torch callback

        try {

            cameraManager?.registerTorchCallback(torchCallback, mainHandler)

        } catch (ignored: Exception) {}


        // Register clipboard listener

        try {

            clipboardManager?.addPrimaryClipChangedListener(clipListener)

        } catch (ignored: Exception) {}


        // Register app ops privacy watcher

        try {

            val ops = arrayOf(

                AppOpsManager.OPSTR_CAMERA,

                AppOpsManager.OPSTR_RECORD_AUDIO,

                AppOpsManager.OPSTR_FINE_LOCATION,

                AppOpsManager.OPSTR_COARSE_LOCATION

            )

            appOpsManager?.startWatchingActive(ops, context.mainExecutor, opActiveCallback)

        } catch (ignored: Exception) {}


        setupOverlayWindow()

        refreshCurrentActivity()

    }


    fun detach() {

        try { context.contentResolver.unregisterContentObserver(settingsObserver) } catch (ignored: Exception) {}

        try { context.unregisterReceiver(broadcastReceiver) } catch (ignored: Exception) {}

        try { mediaSessionManager.removeOnActiveSessionsChangedListener(sessionsListener) } catch (ignored: Exception) {}

        try { cameraManager?.unregisterTorchCallback(torchCallback) } catch (ignored: Exception) {}

        try { clipboardManager?.removePrimaryClipChangedListener(clipListener) } catch (ignored: Exception) {}

        try { appOpsManager?.stopWatchingActive(opActiveCallback) } catch (ignored: Exception) {}


        activeMediaController?.unregisterCallback(mediaCallback)

        activeMediaController = null


        removeOverlayWindow()

    }


    private fun getMediaPauseTimeoutMs(): Long {

        val seconds = try {

            Settings.System.getIntForUser(

                context.contentResolver,

                SETTING_ISLAND_MEDIA_TIMEOUT,

                DEFAULT_MEDIA_TIMEOUT_SECONDS,

                UserHandle.USER_CURRENT

            )

        } catch (e: Exception) {

            DEFAULT_MEDIA_TIMEOUT_SECONDS

        }

        return if (seconds <= 0) 0L else seconds * 1000L

    }


    private fun checkEnabledSettings() {

        val cr = context.contentResolver

        isFeatureEnabled = Settings.System.getIntForUser(cr, SETTING_DYNAMIC_ISLAND, 1, UserHandle.USER_CURRENT) == 1

        isMusicEnabled = Settings.System.getIntForUser(cr, SETTING_ISLAND_MUSIC, 1, UserHandle.USER_CURRENT) == 1

        isChargingEnabled = Settings.System.getIntForUser(cr, SETTING_ISLAND_CHARGING, 1, UserHandle.USER_CURRENT) == 1

        isCallEnabled = Settings.System.getIntForUser(cr, SETTING_ISLAND_CALL, 1, UserHandle.USER_CURRENT) == 1

        isTimerEnabled = Settings.System.getIntForUser(cr, SETTING_ISLAND_TIMER, 1, UserHandle.USER_CURRENT) == 1

        isRecordEnabled = Settings.System.getIntForUser(cr, SETTING_ISLAND_SCREENRECORD, 1, UserHandle.USER_CURRENT) == 1

        isBluetoothEnabled = Settings.System.getIntForUser(cr, SETTING_ISLAND_BLUETOOTH, 1, UserHandle.USER_CURRENT) == 1

        isNavigationEnabled = Settings.System.getIntForUser(cr, SETTING_ISLAND_NAVIGATION, 1, UserHandle.USER_CURRENT) == 1

        isDownloadEnabled = Settings.System.getIntForUser(cr, SETTING_ISLAND_DOWNLOAD, 1, UserHandle.USER_CURRENT) == 1

        isTorchEnabled = Settings.System.getIntForUser(cr, SETTING_ISLAND_TORCH, 1, UserHandle.USER_CURRENT) == 1

        isHotspotEnabled = Settings.System.getIntForUser(cr, SETTING_ISLAND_HOTSPOT, 1, UserHandle.USER_CURRENT) == 1

        isCastEnabled = Settings.System.getIntForUser(cr, SETTING_ISLAND_CAST, 1, UserHandle.USER_CURRENT) == 1

        isPrivacyEnabled = Settings.System.getIntForUser(cr, SETTING_ISLAND_PRIVACY, 1, UserHandle.USER_CURRENT) == 1

        isClipboardEnabled = Settings.System.getIntForUser(cr, SETTING_ISLAND_CLIPBOARD, 1, UserHandle.USER_CURRENT) == 1


        updateIslandDimensionsFromSettings()


        if (!mediaState.isPlaying && mediaState.hasActiveMedia) {

            mainHandler.removeCallbacks(mediaPauseHideRunnable)

            val timeoutMs = getMediaPauseTimeoutMs()

            if (timeoutMs > 0L) {

                mainHandler.postDelayed(mediaPauseHideRunnable, timeoutMs)

            }

        }


        if (!isFeatureEnabled) {

            hideIslandWindow()

        } else {

            refreshCurrentActivity()

        }

    }


    private fun updateIslandDimensionsFromSettings() {

        val cr = context.contentResolver

        xOffsetDp = Settings.System.getIntForUser(cr, SETTING_ISLAND_X_OFFSET, 0, UserHandle.USER_CURRENT)

        yOffsetDp = Settings.System.getIntForUser(cr, SETTING_ISLAND_Y_OFFSET, 0, UserHandle.USER_CURRENT)

        pillWidthDp = Settings.System.getIntForUser(cr, SETTING_ISLAND_PILL_WIDTH, 110, UserHandle.USER_CURRENT).toFloat()

        pillHeightDp = Settings.System.getIntForUser(cr, SETTING_ISLAND_PILL_HEIGHT, 34, UserHandle.USER_CURRENT).toFloat()

        notchMarginDp = Settings.System.getIntForUser(cr, SETTING_ISLAND_NOTCH_MARGIN, 5, UserHandle.USER_CURRENT).toFloat()


        islandView?.setBasePillWidth(pillWidthDp)

        islandView?.setPillHeight(pillHeightDp)

        islandView?.setNotchMargin(notchMarginDp)

        val bounceProgress = Settings.System.getIntForUser(cr, SETTING_ISLAND_BOUNCE, 135, UserHandle.USER_CURRENT)
        val bounceFactor = bounceProgress / 100f
        val durationMs = Settings.System.getIntForUser(cr, SETTING_ISLAND_DURATION, 300, UserHandle.USER_CURRENT).toLong()
        val iconStyle = Settings.System.getIntForUser(cr, SETTING_ISLAND_ICON_STYLE, 0, UserHandle.USER_CURRENT)
        islandView?.setAnimationConfig(bounceFactor, durationMs)
        islandView?.setIconStyle(iconStyle)

        updateWindowDimensions(isExpanded)

    }


    private fun setupOverlayWindow() {

        if (islandView != null) return


        val density = context.resources.displayMetrics.density

        val view = DynamicIslandView(context)


        // Setup interaction callbacks

        view.onToggleExpand = { toggleExpand() }

        view.onPlayPauseClicked = { togglePlayPause() }

        view.onPrevClicked = { skipToPrevious() }

        view.onNextClicked = { skipToNext() }

        view.onEndCallClicked = { endCall() }

        view.onMuteToggled = { toggleMute() }

        view.onOpenMediaAppClicked = { openMediaApp() }

        view.onRecordStopClicked = { stopScreenRecord() }

        view.onToggleTorchClicked = { toggleTorch() }

        view.onToggleHotspotClicked = { toggleHotspot() }

        view.onDisconnectCastClicked = { disconnectCast() }

        view.onCancelDownloadClicked = { cancelDownload() }

        view.onOpenNavigationClicked = { openNavigationApp() }

        view.onTimerPauseClicked = { toggleTimer() }

        view.onTimerResetClicked = { resetTimer() }


        view.setOnTouchListener { _, event ->

            if (event.action == MotionEvent.ACTION_OUTSIDE) {

                if (isExpanded) {

                    collapse()

                }

                true

            } else {

                false

            }

        }


        val statusBarHeight = try {

            context.resources.getDimensionPixelSize(com.android.internal.R.dimen.status_bar_height)

        } catch (e: Exception) {

            (28 * density).toInt()

        }

        val notchYOffset = (statusBarHeight * 0.15f).toInt()


        val params = WindowManager.LayoutParams(

            (180 * density).toInt(),

            (36 * density).toInt(),

            WindowManager.LayoutParams.TYPE_STATUS_BAR_SUB_PANEL,

            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or

                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or

                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,

            PixelFormat.TRANSLUCENT

        ).apply {

            title = "DynamicIslandOverlay"

            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL

            y = notchYOffset

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {

                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS

            }

        }


        windowLayoutParams = params

        islandView = view

        updateIslandDimensionsFromSettings()

    }


    private fun toggleExpand() {

        if (isExpanded) collapse() else expand()

    }


    private fun expand() {

        if (isExpanded) return

        isExpanded = true

        updateWindowDimensions(expanded = true)

        islandView?.setExpanded(true, animate = true)

    }


    private fun collapse() {

        if (!isExpanded) return

        isExpanded = false

        updateWindowDimensions(expanded = false)

        islandView?.setExpanded(false, animate = true)

    }


    private fun updateWindowDimensions(expanded: Boolean) {

        val params = windowLayoutParams ?: return

        val density = context.resources.displayMetrics.density


        val statusBarHeight = try {

            context.resources.getDimensionPixelSize(com.android.internal.R.dimen.status_bar_height)

        } catch (e: Exception) {

            (28 * density).toInt()

        }

        val notchYOffset = (statusBarHeight * 0.15f).toInt()


        params.x = (xOffsetDp * density).toInt()

        params.y = notchYOffset + (yOffsetDp * density).toInt()


        if (expanded) {
            val activeType = resolveActiveActivity()
            val dynamicHeightDp = islandView?.getExpandedHeightDp(activeType) ?: 145f
            params.width = (360 * density).toInt()
            params.height = (dynamicHeightDp * density).toInt()
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        } else {
            val dynamicWidthDp = islandView?.getEstimatedPillWidthDp(resolveActiveActivity()) ?: pillWidthDp
            params.width = (dynamicWidthDp * density).toInt()
            params.height = (pillHeightDp * density).toInt()
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        }

        islandView?.let {
            if (isWindowAttached) {
                try { windowManager.updateViewLayout(it, params) } catch (ignored: Exception) {}
                onIslandWidthChanged?.invoke(params.width, expanded, true)
            }
        }
    }

    private fun showIslandWindow() {
        if (!isFeatureEnabled) return
        val view = islandView ?: return
        val params = windowLayoutParams ?: return

        if (!isWindowAttached) {
            view.alpha = 0f
            view.scaleX = 0.7f
            view.scaleY = 0.7f
            try {
                windowManager.addView(view, params)
                isWindowAttached = true
            } catch (e: Exception) {
                return
            }

            view.animate().cancel()
            view.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(240)
                .start()
            onIslandWidthChanged?.invoke(params.width, isExpanded, true)
        }
    }

    private fun hideIslandWindow() {
        val view = islandView ?: return
        if (isWindowAttached) {
            onIslandWidthChanged?.invoke(0, false, false)
            view.animate().cancel()
            view.animate()
                .alpha(0f)
                .scaleX(0.7f)
                .scaleY(0.7f)
                .setDuration(200)
                .withEndAction {
                    if (isWindowAttached) {
                        try { windowManager.removeViewImmediate(view) } catch (ignored: Exception) {}
                        isWindowAttached = false
                    }
                }.start()
        }
    }

    private fun removeOverlayWindow() {
        val view = islandView ?: return
        if (isWindowAttached) {
            onIslandWidthChanged?.invoke(0, false, false)
            try { windowManager.removeViewImmediate(view) } catch (ignored: Exception) {}
            isWindowAttached = false
        }
        islandView = null
    }

    /**
     * Resolves the active activity according to island priority hierarchy:
     * PRIVACY > CALL > CHARGING > SCREEN_RECORD > NAVIGATION > TIMER > TORCH >
     * CAST > DOWNLOAD > HOTSPOT > TRANSIENT_BANNER > MEDIA > CHARGING (trickle) > CLIPBOARD > NONE
     */
    private fun resolveActiveActivity(): IslandActivityType {
        return when {
            isPrivacyEnabled && privacyState.hasAnyPrivacyActive -> IslandActivityType.PRIVACY
            isCallEnabled && callState.isActive -> IslandActivityType.CALL
            isChargingEnabled && chargingState.isCharging && chargingState.isNewConnection -> IslandActivityType.CHARGING
            isRecordEnabled && recordState.isRecording -> IslandActivityType.SCREEN_RECORD
            isNavigationEnabled && navState.isActive -> IslandActivityType.NAVIGATION
            isTimerEnabled && timerState.isActive -> IslandActivityType.TIMER
            isTorchEnabled && torchState.isOn -> IslandActivityType.TORCH
            isCastEnabled && castState.isCasting -> IslandActivityType.CAST
            isDownloadEnabled && (downloadState.isDownloading || downloadState.isCompleted || downloadState.isFailed || downloadState.isCancelled) -> IslandActivityType.DOWNLOAD
            isHotspotEnabled && hotspotState.isEnabled -> IslandActivityType.HOTSPOT
            bannerState.isActive -> IslandActivityType.TRANSIENT_BANNER
            isMusicEnabled && mediaState.hasActiveMedia -> IslandActivityType.MEDIA
            isChargingEnabled && chargingState.isCharging -> IslandActivityType.CHARGING
            isClipboardEnabled && clipboardState.isActive -> IslandActivityType.CLIPBOARD
            else -> IslandActivityType.NONE
        }
    }

    private fun refreshCurrentActivity() {
        if (!isFeatureEnabled) {
            hideIslandWindow()
            return
        }

        val activeType = resolveActiveActivity()
        val view = islandView ?: return

        if (activeType == IslandActivityType.NONE) {
            hideIslandWindow()
            return
        }

        showIslandWindow()
        updateWindowDimensions(isExpanded)

        when (activeType) {
            IslandActivityType.PRIVACY -> view.showPrivacy(privacyState)
            IslandActivityType.CALL -> view.showCall(callState)
            IslandActivityType.SCREEN_RECORD -> view.showRecording(recordState)
            IslandActivityType.CHARGING -> view.showCharging(chargingState)
            IslandActivityType.NAVIGATION -> view.showNavigation(navState)
            IslandActivityType.TIMER -> view.showTimer(timerState)
            IslandActivityType.TORCH -> view.showTorch(torchState)
            IslandActivityType.CAST -> view.showCast(castState)
            IslandActivityType.DOWNLOAD -> view.showDownload(downloadState)
            IslandActivityType.HOTSPOT -> view.showHotspot(hotspotState)
            IslandActivityType.TRANSIENT_BANNER -> view.showBanner(bannerState)
            IslandActivityType.MEDIA -> view.showMedia(mediaState)
            IslandActivityType.CLIPBOARD -> view.showClipboard(clipboardState)
            else -> {}
        }

        if (shouldKeepTicking()) {
            mainHandler.removeCallbacks(progressTickRunnable)
            mainHandler.post(progressTickRunnable)
        }
    }

    private fun shouldKeepTicking(): Boolean {
        return (mediaState.isPlaying && mediaState.hasActiveMedia) ||
            callState.isActive ||
            recordState.isRecording ||
            (timerState.isActive && !timerState.isPaused) ||
            downloadState.isDownloading
    }

    private fun updateTick() {
        val view = islandView ?: return
        val activeType = resolveActiveActivity()

        if (activeType == IslandActivityType.MEDIA && mediaState.isPlaying && mediaState.durationMs > 0) {
            val elapsed = SystemClock.elapsedRealtime() - mediaState.lastUpdateTimeMs
            val currentPos = (mediaState.positionMs + elapsed).coerceIn(0L, mediaState.durationMs)
            view.showMedia(mediaState.copy(positionMs = currentPos))
        } else if (activeType == IslandActivityType.CALL) {
            view.showCall(callState)
        } else if (activeType == IslandActivityType.SCREEN_RECORD) {
            view.showRecording(recordState)
        } else if (activeType == IslandActivityType.TIMER && !timerState.isPaused) {
            val rem = (timerState.remainingSeconds - 1).coerceAtLeast(0)
            timerState = timerState.copy(remainingSeconds = rem)
            view.showTimer(timerState)
        }
    }

    // --- Media Controls ---
    private fun findActiveMediaSession() {
        try {
            val controllers = mediaSessionManager.getActiveSessions(null)
            updateActiveController(controllers)
        } catch (ignored: Exception) {}
    }

    private fun updateActiveController(controllers: List<MediaController>?) {
        val playingController = controllers?.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: controllers?.firstOrNull()

        if (activeMediaController?.sessionToken != playingController?.sessionToken) {
            activeMediaController?.unregisterCallback(mediaCallback)
            activeMediaController = playingController
            activeMediaController?.registerCallback(mediaCallback, mainHandler)
        }

        activeMediaController?.let {
            handleMetadataChanged(it.metadata)
            handlePlaybackStateChanged(it.playbackState)
        } ?: run {
            mediaState = IslandMediaState(hasActiveMedia = false)
            refreshCurrentActivity()
        }
    }

    private fun handlePlaybackStateChanged(state: PlaybackState?) {
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING
        val pos = state?.position ?: 0L

        mediaState = mediaState.copy(
            isPlaying = isPlaying,
            positionMs = pos,
            lastUpdateTimeMs = SystemClock.elapsedRealtime(),
            hasActiveMedia = isPlaying || (mediaState.hasActiveMedia && state != null)
        )

        mainHandler.removeCallbacks(mediaPauseHideRunnable)
        if (!isPlaying) {
            val timeoutMs = getMediaPauseTimeoutMs()
            if (timeoutMs > 0L) {
                mainHandler.postDelayed(mediaPauseHideRunnable, timeoutMs)
            }
        }

        refreshCurrentActivity()
    }

    private fun handleMetadataChanged(metadata: MediaMetadata?) {
        if (metadata == null) return

        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0L)
        val art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)

        val dominantColor = art?.let { extractColor(it) }

        mediaState = mediaState.copy(
            title = title,
            artist = artist,
            durationMs = duration,
            albumArt = art,
            dominantColor = dominantColor,
            hasActiveMedia = true
        )

        refreshCurrentActivity()
    }

    private fun extractColor(bitmap: Bitmap): Int {
        return try {
            val sample = Bitmap.createScaledBitmap(bitmap, 1, 1, false)
            val color = sample.getPixel(0, 0)
            sample.recycle()
            color
        } catch (e: Exception) {
            Color.parseColor("#38BDF8")
        }
    }

    fun togglePlayPause() {
        val controller = activeMediaController ?: return
        val state = controller.playbackState?.state
        if (state == PlaybackState.STATE_PLAYING) {
            controller.transportControls?.pause()
        } else {
            controller.transportControls?.play()
        }
    }

    fun skipToNext() {
        activeMediaController?.transportControls?.skipToNext()
    }

    fun skipToPrevious() {
        activeMediaController?.transportControls?.skipToPrevious()
    }

    fun openMediaApp() {
        try {
            activeMediaController?.sessionActivity?.send()
            collapse()
        } catch (ignored: Exception) {}
    }

    // --- Phone Call Controls ---
    fun endCall() {
        try {
            telecomManager?.endCall()
        } catch (ignored: Exception) {}
        callState.intent?.let {
            try { it.send() } catch (ignored: Exception) {}
        }
        callState = IslandCallState(isActive = false)
        collapse()
        refreshCurrentActivity()
    }

    fun toggleMute() {
        audioManager?.let {
            val newMute = !it.isMicrophoneMute
            it.isMicrophoneMute = newMute
            callState = callState.copy(isMuted = newMute)
            islandView?.showCall(callState)
        }
    }

    // --- Screen Record ---
    fun stopScreenRecord() {
        try {
            context.sendBroadcast(Intent("com.android.systemui.screenrecord.ACTION_STOP"))
        } catch (ignored: Exception) {}
        recordState = IslandRecordState(isRecording = false)
        collapse()
        refreshCurrentActivity()
    }

    // --- Torch / Flashlight ---
    fun toggleTorch() {
        try {
            val cm = cameraManager ?: return
            val newState = !torchState.isOn
            cm.setTorchMode("0", newState)
            torchState = IslandTorchState(isOn = newState)
            refreshCurrentActivity()
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling torch", e)
        }
    }

    // --- Wi-Fi Hotspot ---
    fun toggleHotspot() {
        try {
            val tm = context.getSystemService(TetheringManager::class.java)
            if (hotspotState.isEnabled) {
                tm?.stopTethering(TetheringManager.TETHERING_WIFI)
                hotspotState = IslandHotspotState(isEnabled = false, clientCount = 0)
            } else {
                tm?.startTethering(
                    TetheringManager.TETHERING_WIFI,
                    context.mainExecutor,
                    object : TetheringManager.StartTetheringCallback {}
                )
                hotspotState = IslandHotspotState(isEnabled = true, clientCount = 0)
            }
            refreshCurrentActivity()
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling hotspot", e)
        }
    }

    private fun handleHotspotEvent(intent: Intent) {
        val state = intent.getIntExtra("wifi_state", 14)
        when (state) {
            13 -> { // WIFI_AP_STATE_ENABLED
                val curCount = hotspotState.clientCount
                hotspotState = IslandHotspotState(isEnabled = true, clientCount = curCount, showIntroBanner = true)
                mainHandler.removeCallbacks(hotspotBannerDismissRunnable)
                mainHandler.postDelayed(hotspotBannerDismissRunnable, 3500L)
                refreshCurrentActivity()
            }
            10, 11 -> { // WIFI_AP_STATE_DISABLING or DISABLED
                mainHandler.removeCallbacks(hotspotBannerDismissRunnable)
                hotspotState = IslandHotspotState(isEnabled = false, clientCount = 0, showIntroBanner = false)
                refreshCurrentActivity()
            }
        }
    }
    private fun checkHotspotState() {
        val wm = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
        val isApEnabled = try {
            val method = wm.javaClass.getMethod("isWifiApEnabled")
            method.invoke(wm) as? Boolean ?: false
        } catch (e: Exception) {
            false
        }
        if (isApEnabled != hotspotState.isEnabled) {
            val showBanner = isApEnabled && !hotspotState.isEnabled
            hotspotState = IslandHotspotState(isEnabled = isApEnabled, clientCount = hotspotState.clientCount, showIntroBanner = showBanner)
            if (showBanner) {
                mainHandler.removeCallbacks(hotspotBannerDismissRunnable)
                mainHandler.postDelayed(hotspotBannerDismissRunnable, 3500L)
            } else {
                mainHandler.removeCallbacks(hotspotBannerDismissRunnable)
            }
            refreshCurrentActivity()
        }
    }


    // --- Wireless Display & Cast ---

    fun disconnectCast() {

        try {

            context.sendBroadcast(Intent("android.intent.action.DISCONNECT_CAST"))

        } catch (ignored: Exception) {}

        castState = IslandCastState(isCasting = false)

        collapse()

        refreshCurrentActivity()

    }


    private fun handleCastIntent(intent: Intent) {

        val isCasting = intent.getBooleanExtra("is_casting", false)

        val deviceName = intent.getStringExtra("device_name") ?: context.getString(R.string.island_wireless_display)

        val castType = intent.getStringExtra("cast_type") ?: context.getString(R.string.island_wireless_display)

        castState = IslandCastState(isCasting = isCasting, deviceName = deviceName, castType = castType)

        refreshCurrentActivity()

    }


    // --- File Downloads ---

    fun cancelDownload() {
        if (downloadState.isCancelled || downloadState.isFailed || downloadState.isCompleted) {
            mainHandler.removeCallbacks(downloadDismissRunnable)
            downloadState = IslandDownloadState(isDownloading = false)
            collapse()
            refreshCurrentActivity()
            return
        }
        downloadState.cancelIntent?.let {
            try { it.send() } catch (ignored: Exception) {}
        }
        downloadState = downloadState.copy(
            isDownloading = false,
            isCancelled = true,
            isFailed = false,
            isCompleted = false
        )
        mainHandler.removeCallbacks(downloadDismissRunnable)
        mainHandler.postDelayed(downloadDismissRunnable, 3500L)
        refreshCurrentActivity()
    }

    private fun handleDownloadIntent(intent: Intent) {
        val isDownloading = intent.getBooleanExtra("is_downloading", false)
        val fileName = intent.getStringExtra("file_name") ?: context.getString(R.string.island_download)
        val progress = intent.getIntExtra("progress", 0)
        val speed = intent.getStringExtra("speed") ?: ""
        val total = intent.getStringExtra("total_size") ?: ""
        val isCompleted = intent.getBooleanExtra("is_completed", false)
        val isFailed = intent.getBooleanExtra("is_failed", false)
        val isCancelled = intent.getBooleanExtra("is_cancelled", false)

        downloadState = IslandDownloadState(
            isDownloading = isDownloading,
            fileName = fileName,
            progress = progress,
            speedText = speed,
            totalSizeText = total,
            isCompleted = isCompleted,
            isFailed = isFailed,
            isCancelled = isCancelled
        )

        mainHandler.removeCallbacks(downloadDismissRunnable)
        if (isCompleted || isFailed) {
            mainHandler.postDelayed(downloadDismissRunnable, 3500L)
        }
        refreshCurrentActivity()
    }

    // --- GPS Navigation ---
    fun openNavigationApp() {
        navState.openIntent?.let {
            try {
                it.send()
                collapse()
            } catch (ignored: Exception) {}
        }
    }

    private fun handleNavigationIntent(intent: Intent) {
        val isActive = intent.getBooleanExtra("is_active", false)
        val maneuver = intent.getStringExtra("maneuver") ?: "TURN_RIGHT"
        val dist = intent.getStringExtra("distance") ?: ""
        val inst = intent.getStringExtra("instruction") ?: ""
        val eta = intent.getStringExtra("eta") ?: ""

        navState = IslandNavigationState(
            isActive = isActive,
            maneuver = maneuver,
            distanceText = dist,
            instructionText = inst,
            etaText = eta
        )
        refreshCurrentActivity()
    }

    // --- Timers ---
    fun toggleTimer() {
        timerState.pauseResumeIntent?.let {
            try { it.send() } catch (ignored: Exception) {}
        }
        timerState = timerState.copy(isPaused = !timerState.isPaused)
        islandView?.showTimer(timerState)
    }

    fun resetTimer() {
        timerState.resetIntent?.let {
            try { it.send() } catch (ignored: Exception) {}
        }
        timerState = IslandTimerState(isActive = false)
        collapse()
        refreshCurrentActivity()
    }

    private fun handleTimerIntent(intent: Intent) {
        val isActive = intent.getBooleanExtra("is_active", false)
        val label = intent.getStringExtra("label") ?: "Temporizador"
        val seconds = intent.getIntExtra("remaining_seconds", 0)
        val isPaused = intent.getBooleanExtra("is_paused", false)

        timerState = IslandTimerState(
            isActive = isActive,
            label = label,
            remainingSeconds = seconds,
            isPaused = isPaused
        )
        refreshCurrentActivity()
    }

    // --- Privacy Indicators (Camera, Microphone, Location) ---
    private fun handlePrivacyOp(op: String, active: Boolean, pkgName: String?) {
        val curCam = if (op == AppOpsManager.OPSTR_CAMERA) active else privacyState.isCameraInUse
        val curMic = if (op == AppOpsManager.OPSTR_RECORD_AUDIO) active else privacyState.isMicInUse
        val curLoc = if (op == AppOpsManager.OPSTR_FINE_LOCATION || op == AppOpsManager.OPSTR_COARSE_LOCATION) active else privacyState.isLocationInUse

        val appName = if (active && !pkgName.isNullOrBlank()) {
            try {
                val appInfo = context.packageManager.getApplicationInfo(pkgName, 0)
                context.packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                pkgName
            }
        } else privacyState.appName

        privacyState = IslandPrivacyState(
            isCameraInUse = curCam,
            isMicInUse = curMic,
            isLocationInUse = curLoc,
            appName = if (curCam || curMic || curLoc) appName else ""
        )
        refreshCurrentActivity()
    }

    // --- Clipboard Preview Banner ---
    private fun handleClipboardText(text: String) {
        if (!isClipboardEnabled) return
        val preview = if (text.length > 36) text.take(36) + "..." else text
        clipboardState = IslandClipboardState(isActive = true, copiedText = preview)
        mainHandler.removeCallbacks(clipboardDismissRunnable)
        mainHandler.postDelayed(clipboardDismissRunnable, 4000L)
        refreshCurrentActivity()
    }

    // --- Battery & Charging ---
    private fun handleBatteryEvent(intent: Intent) {
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val isNewConnection = (intent.action == Intent.ACTION_POWER_CONNECTED) || (!wasCharging && isCharging)
        wasCharging = isCharging

        if (!isCharging) {
            chargingState = chargingState.copy(isCharging = false)
            refreshCurrentActivity()
            return
        }

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val batteryPct = if (scale > 0) (level * 100 / scale) else level

        val watts = calculateWatts(intent)
        val title = when {
            watts >= 65.0 -> context.getString(R.string.island_charging_hypercharge)
            watts >= 30.0 -> context.getString(R.string.island_charging_turbo)
            else -> context.getString(R.string.island_charging_fast)
        }

        chargingState = IslandChargingState(
            isCharging = true,
            level = batteryPct,
            watts = watts,
            isNewConnection = isNewConnection,
            title = title
        )

        refreshCurrentActivity()
    }

    private fun calculateWatts(intent: Intent): Double {
        val maxCurrentUa = intent.getIntExtra("max_charging_current", -1)
        val maxVoltageUv = intent.getIntExtra("max_charging_voltage", -1)
        if (maxCurrentUa > 0 && maxVoltageUv > 0) {
            val w = (maxCurrentUa.toDouble() / 1_000_000.0) * (maxVoltageUv.toDouble() / 1_000_000.0)
            if (w > 0.5) return w
        }

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        if (bm != null) {
            val voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1).takeIf { it > 0 } ?: 4100
            val currentNowUa = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            if (currentNowUa != 0 && currentNowUa != Int.MIN_VALUE) {
                val currentAmps = abs(currentNowUa).toDouble() / 1_000_000.0
                val voltageVolts = voltageMv.toDouble() / 1000.0
                val w = currentAmps * voltageVolts
                if (w > 0.5) return w
            }
        }
        return 0.0
    }

    // --- Bluetooth & Audio Banners ---
    private fun handleBluetoothEvent(intent: Intent) {
        val state = intent.getIntExtra(BluetoothA2dp.EXTRA_STATE, -1)
        if (state == BluetoothA2dp.STATE_CONNECTED) {
            val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                ?: return

            val deviceName = try { device.alias ?: device.name ?: context.getString(R.string.island_headphones_connected) } catch (e: Exception) { context.getString(R.string.island_headphones_connected) }
            val battery = try { device.batteryLevel } catch (e: Exception) { -1 }

            bannerState = IslandBannerState(
                isActive = true,
                title = deviceName,
                subtitle = context.getString(R.string.island_headphones_hires),
                badge = if (battery in 0..100) "$battery%" else null,
                iconRes = R.drawable.ic_hyper_headphones,
                tintColor = Color.parseColor("#78D9EC")
            )

            mainHandler.removeCallbacks(bannerDismissRunnable)
            mainHandler.postDelayed(bannerDismissRunnable, 4000L)
            refreshCurrentActivity()
        }
    }

    private fun handleRingerModeEvent(intent: Intent) {
        val ringerMode = intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1)
        if (ringerMode == -1) return
        if (lastRingerMode == null) {
            lastRingerMode = ringerMode
            return
        }
        if (lastRingerMode == ringerMode) return
        lastRingerMode = ringerMode

        val (title, sub, icon, tint) = when (ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> Quad(
                context.getString(R.string.island_mode_silent),
                context.getString(R.string.island_silent_desc),
                R.drawable.ic_hyper_silent,
                Color.parseColor("#FF5252")
            )
            AudioManager.RINGER_MODE_VIBRATE -> Quad(
                context.getString(R.string.island_mode_vibrate),
                context.getString(R.string.island_vibrate_desc),
                R.drawable.ic_hyper_vibrate,
                Color.parseColor("#FFB74D")
            )
            else -> Quad(
                context.getString(R.string.island_mode_sound),
                context.getString(R.string.island_sound_desc),
                R.drawable.ic_hyper_sound,
                Color.parseColor("#81C784")
            )
        }

        bannerState = IslandBannerState(
            isActive = true,
            title = title,
            subtitle = sub,
            iconRes = icon,
            tintColor = tint
        )

        mainHandler.removeCallbacks(bannerDismissRunnable)
        mainHandler.postDelayed(bannerDismissRunnable, 3000L)
        refreshCurrentActivity()
    }
    private fun handleDndEvent() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val filter = nm.currentInterruptionFilter
        if (lastInterruptionFilter == null) {
            lastInterruptionFilter = filter
            return
        }

        if (lastInterruptionFilter == filter) return
        lastInterruptionFilter = filter
        val isDnd = (filter != NotificationManager.INTERRUPTION_FILTER_ALL && filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN)
        bannerState = IslandBannerState(
            isActive = true,
            title = context.getString(R.string.island_mode_dnd),
            subtitle = if (isDnd) context.getString(R.string.island_dnd_active_desc) else context.getString(R.string.island_dnd_inactive_desc),
            iconRes = R.drawable.ic_hyper_dnd,
            tintColor = if (isDnd) Color.parseColor("#B388FF") else Color.parseColor("#9E9E9E")
        )

        mainHandler.removeCallbacks(bannerDismissRunnable)
        mainHandler.postDelayed(bannerDismissRunnable, 3000L)
        refreshCurrentActivity()
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
