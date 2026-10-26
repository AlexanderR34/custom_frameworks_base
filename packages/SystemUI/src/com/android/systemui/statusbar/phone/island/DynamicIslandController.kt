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
import android.app.AppOpsManager
import android.app.DownloadManager
import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.StatusBarManager
import android.app.StatusBarManager.WindowVisibleState
import android.os.Process
import com.android.systemui.statusbar.CommandQueue
import com.android.systemui.Dependency
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.statusbar.StatusBarState
import com.android.systemui.statusbar.phone.island.compose.DynamicIslandManager
import com.android.systemui.statusbar.policy.KeyguardStateController
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.PixelFormat
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.net.Uri
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
import android.view.animation.OvershootInterpolator
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.android.systemui.res.R
import com.android.systemui.statusbar.NotificationListener
import kotlin.math.abs

/**
 * Master Controller for the Independent Dynamic Island .
 * Manages full multi-activity states: Media, Calls, 90W Ultra HyperCharge, Screen Recording,
 * Timers, GPS Navigation, Downloads, Flashlight, Wi-Fi Hotspot, Cast, Hardware Privacy,
 * Clipboard preview, and System Banners.
 */
class DynamicIslandController(
    private val context: Context,
    var notificationListener: NotificationListener? = null,
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

    private var isKeyguardOrAod = false
    private var keyguardStateController: KeyguardStateController? = null
    private var statusBarStateController: StatusBarStateController? = null

    private val keyguardCallback = object : KeyguardStateController.Callback {
        override fun onKeyguardShowingChanged() {
            mainHandler.post { updateKeyguardState() }
        }

        override fun onKeyguardFadingAwayChanged() {
            mainHandler.post { updateKeyguardState() }
        }

        override fun onUnlockedChanged() {
            mainHandler.post { updateKeyguardState() }
        }
    }

    private val statusBarStateListener = object : StatusBarStateController.StateListener {
        override fun onStateChanged(newState: Int) {
            mainHandler.post { updateKeyguardState() }
        }

        override fun onDozingChanged(isDozing: Boolean) {
            mainHandler.post { updateKeyguardState() }
        }
    }

    private fun checkKeyguardOrAod(): Boolean {
        val keyguardShowing = keyguardStateController?.isShowing == true
        val dozing = statusBarStateController?.isDozing == true
        val state = statusBarStateController?.state
        val isKeyguardState = (state == StatusBarState.KEYGUARD || state == StatusBarState.SHADE_LOCKED)
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isLocked = km?.isKeyguardLocked == true

        return keyguardShowing || dozing || isKeyguardState || isLocked
    }

    private fun updateKeyguardState() {
        val locked = checkKeyguardOrAod()
        if (isKeyguardOrAod != locked) {
            isKeyguardOrAod = locked
            Log.d(TAG, "updateKeyguardState: isKeyguardOrAod = $isKeyguardOrAod")
            if (isKeyguardOrAod) {
                if (isExpanded) {
                    collapse(force = true)
                }
                islandView?.visibility = View.GONE
                hideIslandWindow()
            } else {
                islandView?.visibility = View.VISIBLE
                refreshCurrentActivity()
            }
        }
    }
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
    private var isTorchIntroActive = false
    private var pillWidthAnimator: ValueAnimator? = null
    private var hotspotState = IslandHotspotState()
    private var castState = IslandCastState()
    private var castController: com.android.systemui.statusbar.policy.CastController? = null
    private val castCallback = com.android.systemui.statusbar.policy.CastController.Callback {
        mainHandler.post { checkCastState() }
    }
    private var privacyState = IslandPrivacyState()
    private var clipboardState = IslandClipboardState()
    private var isStatusBarHidden: Boolean = false
    private var commandQueue: CommandQueue? = null
    private var configurationController: com.android.systemui.statusbar.policy.ConfigurationController? = null
    private val activeCameraUids = mutableSetOf<Int>()
    private val activeMicUids = mutableSetOf<Int>()
    private val activeLocUids = mutableSetOf<Int>()

    private var wasCharging = false
    private var isChargingBannerActive = false
    private var lastChargingWatts = 0.0
    private var lastRingerMode: Int? = null
    private var lastInterruptionFilter: Int? = null
    private var lastExpandTimestamp = 0L

    private val powerDisconnectRunnable = Runnable {
        val hadCharging = wasCharging || chargingState.isCharging
        wasCharging = false
        isChargingBannerActive = false
        lastChargingWatts = 0.0
        mainHandler.removeCallbacks(chargingDismissRunnable)
        val lastLevel = chargingState.level
        chargingState = chargingState.copy(isCharging = false)
        if (isExpanded && resolveActiveActivity() != IslandActivityType.MEDIA) {
            collapse()
        }
        if (hadCharging && isChargingEnabled) {
            showTurnOffBanner(
                title = context.getString(R.string.island_charger_disconnected),
                subtitle = "$lastLevel%",
                iconRes = R.drawable.ic_hyper_charging,
                tintColor = Color.parseColor("#9E9E9E")
            )
        } else {
            refreshCurrentActivity()
        }
    }

    private val chargingDismissRunnable: Runnable = Runnable {
        if (isExpanded && resolveActiveActivity() == IslandActivityType.CHARGING) {
            mainHandler.postDelayed(chargingDismissRunnable, 3000L)
            return@Runnable
        }
        isChargingBannerActive = false
        if (isExpanded && resolveActiveActivity() != IslandActivityType.MEDIA) {
            collapse()
        }
        refreshCurrentActivity()
    }

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

    private val clipboardDismissRunnable: Runnable = Runnable {
        if (isExpanded && resolveActiveActivity() == IslandActivityType.CLIPBOARD) {
            mainHandler.postDelayed(clipboardDismissRunnable, 1500L)
            return@Runnable
        }
        clipboardState = clipboardState.copy(isActive = false)
        islandView?.dismissClipboard()
        DynamicIslandManager.dismissTransientBanner()
        refreshCurrentActivity()
    }

    private val downloadDismissRunnable = Runnable {
        downloadState = IslandDownloadState()
        islandView?.showDownload(downloadState)
        refreshCurrentActivity()
    }

    private val downloadObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            checkDownloads()
        }
    }

    private val notificationHandler = object : NotificationListener.NotificationHandler {
        override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: NotificationListenerService.RankingMap?) {
            if (sbn == null) return
            mainHandler.post {
                handleNotificationPosted(sbn)
            }
        }

        override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: NotificationListenerService.RankingMap?) {
            if (sbn == null) return
            mainHandler.post {
                handleNotificationRemoved(sbn)
            }
        }

        override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: NotificationListenerService.RankingMap?, reason: Int) {
            if (sbn == null) return
            mainHandler.post {
                handleNotificationRemoved(sbn)
            }
        }

        override fun onNotificationRankingUpdate(rankingMap: NotificationListenerService.RankingMap?) {}
        override fun onNotificationsInitialized() {}
    }
    private val hotspotBannerDismissRunnable = Runnable {
        if (hotspotState.isEnabled && hotspotState.showIntroBanner) {
            hotspotState = hotspotState.copy(showIntroBanner = false)
            refreshCurrentActivity()
        }
    }

    private val torchDismissRunnable = Runnable {
        isTorchIntroActive = false
        refreshCurrentActivity()
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
                val stateChanged = torchState.isOn != enabled
                torchState = IslandTorchState(isOn = enabled)
                if (stateChanged) {
                    isTorchIntroActive = true
                    mainHandler.removeCallbacks(torchDismissRunnable)
                    mainHandler.postDelayed(torchDismissRunnable, 2500L)
                }
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


    private val opActiveCallback = AppOpsManager.OnOpActiveChangedListener { op, uid, pkgName, active ->
        if (uid < Process.FIRST_APPLICATION_UID || pkgName == "android" || pkgName == "com.android.systemui") {
            return@OnOpActiveChangedListener
        }
        mainHandler.post { handlePrivacyOp(op, uid, active, pkgName) }
    }

    private val commandQueueCallback = object : CommandQueue.Callbacks {
        override fun setWindowState(
            displayId: Int,
            @StatusBarManager.WindowType window: Int,
            @WindowVisibleState state: Int,
        ) {
            if (window == StatusBarManager.WINDOW_STATUS_BAR) {
                val isHidden = (state == StatusBarManager.WINDOW_STATE_HIDDEN || state == StatusBarManager.WINDOW_STATE_HIDING)
                mainHandler.post {
                    handleStatusBarWindowStateChanged(isHidden)
                }
            }
        }
    }

    private val configurationListener = object : com.android.systemui.statusbar.policy.ConfigurationController.ConfigurationListener {
        override fun onConfigChanged(newConfig: android.content.res.Configuration?) {
            mainHandler.post {
                updateWindowDimensions(isExpanded)
                notifyIslandWidthChanged()
            }
        }
    }


    private val broadcastReceiver = object : BroadcastReceiver() {

        override fun onReceive(ctx: Context?, intent: Intent?) {

            if (ctx == null || intent == null) return

            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    val batteryIntent = try {
                        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    } catch (e: Exception) {
                        null
                    }
                    handleBatteryEvent(batteryIntent ?: intent, forceConnected = true)
                }
                Intent.ACTION_BATTERY_CHANGED -> handleBatteryEvent(intent)
                Intent.ACTION_BATTERY_LOW -> handleBatteryLowEvent(intent)
                Intent.ACTION_POWER_DISCONNECTED -> {
                    mainHandler.removeCallbacks(powerDisconnectRunnable)
                    mainHandler.postDelayed(powerDisconnectRunnable, 600L)
                }

                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> handleBluetoothEvent(intent)

                AudioManager.RINGER_MODE_CHANGED_ACTION -> handleRingerModeEvent(intent)

                NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> handleDndEvent()

                "com.android.systemui.screenrecord.START" -> {
                    recordState = IslandRecordState(isRecording = true, startTimeMs = SystemClock.elapsedRealtime())
                    DynamicIslandManager.updateScreenRecording(true, recordState.startTimeMs)
                    refreshCurrentActivity()
                }

                "com.android.systemui.screenrecord.STOP",
                "com.android.systemui.screenrecord.ScreenRecordingService.ACTION_STOP",
                "com.android.systemui.screenrecord.ACTION_STOP" -> {
                    recordState = IslandRecordState(isRecording = false)
                    DynamicIslandManager.updateScreenRecording(false)
                    refreshCurrentActivity()
                }

                "com.android.systemui.screenrecord.UPDATE_STATE" -> {
                    val isRecording = intent.getBooleanExtra("extra_state", false)
                    recordState = if (isRecording) {
                        IslandRecordState(isRecording = true, startTimeMs = SystemClock.elapsedRealtime())
                    } else {
                        IslandRecordState(isRecording = false)
                    }
                    DynamicIslandManager.updateScreenRecording(recordState.isRecording, recordState.startTimeMs)
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

                DownloadManager.ACTION_DOWNLOAD_COMPLETE -> checkDownloads()

                ACTION_UPDATE_CAST -> handleCastIntent(intent)

                ACTION_UPDATE_TIMER -> handleTimerIntent(intent)

                Intent.ACTION_SCREEN_OFF,
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT -> {
                    mainHandler.post { updateKeyguardState() }
                }

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
            addAction(Intent.ACTION_BATTERY_LOW)

            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)

            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)

            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)

            addAction("com.android.systemui.screenrecord.START")
            addAction("com.android.systemui.screenrecord.STOP")
            addAction("com.android.systemui.screenrecord.UPDATE_STATE")
            addAction("com.android.systemui.screenrecord.ScreenRecordingService.ACTION_STOP")
            addAction("com.android.systemui.screenrecord.ACTION_STOP")

            addAction("android.net.wifi.WIFI_AP_STATE_CHANGED")

            addAction("android.net.conn.TETHER_STATE_CHANGED")

            addAction(ACTION_UPDATE_NAVIGATION)

            addAction(ACTION_STOP_NAVIGATION)

            addAction(ACTION_UPDATE_DOWNLOAD)

            addAction(DownloadManager.ACTION_DOWNLOAD_COMPLETE)

            addAction(ACTION_UPDATE_CAST)

            addAction(ACTION_UPDATE_TIMER)

            addAction(Intent.ACTION_SCREEN_OFF)

            addAction(Intent.ACTION_SCREEN_ON)

            addAction(Intent.ACTION_USER_PRESENT)

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

        // Register HotspotController listener
        try {
            val hotspotController = com.android.systemui.Dependency.get(com.android.systemui.statusbar.policy.HotspotController::class.java)
            hotspotController?.addCallback(object : com.android.systemui.statusbar.policy.HotspotController.Callback {
                override fun onHotspotChanged(enabled: Boolean, numDevices: Int) {
                    mainHandler.post {
                        val wasEnabled = hotspotState.isEnabled
                        if (enabled) {
                            clipboardState = IslandClipboardState(isActive = false)
                            DynamicIslandManager.isClipboardCardActive.value = false
                            mainHandler.removeCallbacks(clipboardDismissRunnable)
                        }
                        hotspotState = IslandHotspotState(isEnabled = enabled, clientCount = numDevices)
                        DynamicIslandManager.updateHotspot(enabled, numDevices)
                        if (!enabled && wasEnabled) {
                            if (isExpanded) collapse()
                            showTurnOffBanner(
                                title = context.getString(R.string.island_hotspot_off),
                                iconRes = R.drawable.ic_hyper_hotspot,
                                tintColor = Color.parseColor("#9E9E9E")
                            )
                        } else {
                            refreshCurrentActivity()
                        }
                    }
                }
            })
        } catch (ignored: Exception) {}

        // Register FlashlightController listener
        try {
            val fc = com.android.systemui.Dependency.get(com.android.systemui.statusbar.policy.FlashlightController::class.java)
            fc?.addCallback(object : com.android.systemui.statusbar.policy.FlashlightController.FlashlightListener {
                override fun onFlashlightChanged(enabled: Boolean) {
                    mainHandler.post {
                        val wasOn = torchState.isOn
                        if (enabled) {
                            clipboardState = IslandClipboardState(isActive = false)
                            DynamicIslandManager.isClipboardCardActive.value = false
                            mainHandler.removeCallbacks(clipboardDismissRunnable)
                        }
                        torchState = IslandTorchState(isOn = enabled)
                        DynamicIslandManager.updateTorch(enabled)
                        if (!enabled && wasOn) {
                            if (isExpanded) collapse()
                            showTurnOffBanner(
                                title = context.getString(R.string.island_torch_off),
                                iconRes = R.drawable.ic_hyper_torch,
                                tintColor = Color.parseColor("#9E9E9E")
                            )
                        } else {
                            refreshCurrentActivity()
                        }
                    }
                }
                override fun onFlashlightError() {}
                override fun onFlashlightAvailabilityChanged(available: Boolean) {}
                override fun onFlashlightStrengthChanged(level: Int) {}
            })
        } catch (ignored: Exception) {}

        // Register CastController listener
        try {
            val cc = com.android.systemui.Dependency.get(com.android.systemui.statusbar.policy.CastController::class.java)
            castController = cc
            cc?.addCallback(castCallback)
            mainHandler.post { checkCastState() }
        } catch (ignored: Exception) {}


        try {
            context.contentResolver.registerContentObserver(
                Uri.parse("content://downloads/all_downloads"),
                true,
                downloadObserver
            )
            context.contentResolver.registerContentObserver(
                Uri.parse("content://downloads/my_downloads"),
                true,
                downloadObserver
            )
        } catch (ignored: Exception) {}

        try {
            notificationListener?.addNotificationHandler(notificationHandler)
        } catch (ignored: Exception) {}

        try {
            keyguardStateController = Dependency.get(KeyguardStateController::class.java)
            keyguardStateController?.addCallback(keyguardCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register KeyguardStateController callback", e)
        }

        try {
            statusBarStateController = Dependency.get(StatusBarStateController::class.java)
            statusBarStateController?.addCallback(statusBarStateListener)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register StatusBarStateController callback", e)
        }

        try {
            commandQueue = Dependency.get(CommandQueue::class.java)
            commandQueue?.addCallback(commandQueueCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register CommandQueue callback", e)
        }

        try {
            configurationController = Dependency.get(com.android.systemui.statusbar.policy.ConfigurationController::class.java)
            configurationController?.addCallback(configurationListener)
        } catch (ignored: Exception) {}

        DynamicIslandManager.onOpenMediaOutput = {
            val pkg = mediaState.packageName.ifBlank { activeMediaController?.packageName ?: "" }
            val token = activeMediaController?.sessionToken
            mainHandler.post {
                collapse()
                try {
                    val manager = Dependency.get(com.android.systemui.media.dialog.MediaOutputDialogManager::class.java)
                    if (manager != null) {
                        if (pkg.isNotBlank()) {
                            manager.createAndShow(
                                packageName = pkg,
                                aboveStatusBar = true,
                                view = null,
                                userHandle = android.os.Process.myUserHandle(),
                                token = token,
                                useSystemColors = false
                            )
                        } else {
                            manager.createAndShowForSystemRouting()
                        }
                        return@post
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Direct MediaOutputDialogManager call failed, trying fallback", e)
                }

                // Fallback broadcast
                try {
                    val intent = Intent("com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG").apply {
                        setPackage(context.packageName)
                        if (pkg.isNotBlank()) {
                            putExtra("package_name", pkg)
                            putExtra("key_media_session_token", token)
                        }
                    }
                    context.sendBroadcast(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending LAUNCH_MEDIA_OUTPUT_DIALOG broadcast", e)
                }
            }
        }

        isKeyguardOrAod = checkKeyguardOrAod()

        setupOverlayWindow()

        checkDownloads()
        refreshCurrentActivity()
    }

    fun detach() {

        try { context.contentResolver.unregisterContentObserver(settingsObserver) } catch (ignored: Exception) {}

        try { context.contentResolver.unregisterContentObserver(downloadObserver) } catch (ignored: Exception) {}

        try { context.unregisterReceiver(broadcastReceiver) } catch (ignored: Exception) {}

        try { mediaSessionManager.removeOnActiveSessionsChangedListener(sessionsListener) } catch (ignored: Exception) {}

        try { cameraManager?.unregisterTorchCallback(torchCallback) } catch (ignored: Exception) {}

        try { clipboardManager?.removePrimaryClipChangedListener(clipListener) } catch (ignored: Exception) {}

        try { appOpsManager?.stopWatchingActive(opActiveCallback) } catch (ignored: Exception) {}

        try { keyguardStateController?.removeCallback(keyguardCallback) } catch (ignored: Exception) {}
        try { statusBarStateController?.removeCallback(statusBarStateListener) } catch (ignored: Exception) {}
        try { commandQueue?.removeCallback(commandQueueCallback) } catch (ignored: Exception) {}
        try { configurationController?.removeCallback(configurationListener) } catch (ignored: Exception) {}
        try { castController?.removeCallback(castCallback) } catch (ignored: Exception) {}
        castController = null

        activeMediaController?.unregisterCallback(mediaCallback)
        activeMediaController = null

        pillWidthAnimator?.cancel()
        pillWidthAnimator = null
        mainHandler.removeCallbacks(torchDismissRunnable)
        mainHandler.removeCallbacks(powerDisconnectRunnable)
        mainHandler.removeCallbacks(chargingDismissRunnable)

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

        isFeatureEnabled = Settings.System.getIntForUser(cr, SETTING_DYNAMIC_ISLAND, 0, UserHandle.USER_CURRENT) == 1

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


        islandView?.setXOffset(xOffsetDp.toFloat())
        islandView?.setYOffset(yOffsetDp.toFloat())
        islandView?.setBasePillWidth(pillWidthDp)
        islandView?.setPillHeight(pillHeightDp)
        islandView?.setNotchMargin(notchMarginDp)

        val bounceProgress = Settings.System.getIntForUser(cr, SETTING_ISLAND_BOUNCE, 135, UserHandle.USER_CURRENT).coerceAtLeast(35)
        val bounceFactor = (bounceProgress / 100f).coerceIn(0.35f, 2.0f)
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

        view.onSeekTo = { posMs ->
            activeMediaController?.transportControls?.seekTo(posMs)
            mediaState = mediaState.copy(
                positionMs = posMs,
                lastUpdateTimeMs = SystemClock.elapsedRealtime()
            )
            DynamicIslandManager.updateMedia(
                title = mediaState.title,
                artist = mediaState.artist,
                albumArt = mediaState.albumArt,
                isPlaying = mediaState.isPlaying,
                positionMs = posMs,
                durationMs = mediaState.durationMs,
                lastUpdateTimeMs = SystemClock.elapsedRealtime(),
                packageName = mediaState.packageName
            )
        }

        view.onRecordStopClicked = { stopScreenRecord() }

        view.onToggleTorchClicked = { toggleTorch() }

        view.onToggleHotspotClicked = { toggleHotspot() }

        view.onDisconnectCastClicked = { disconnectCast() }

        view.onCancelDownloadClicked = { cancelDownload() }

        view.onOpenNavigationClicked = { openNavigationApp() }

        view.onTimerPauseClicked = { toggleTimer() }

        view.onTimerResetClicked = { resetTimer() }

        view.onPillWidthChanged = { notifyIslandWidthChanged() }

        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                if (isExpanded && SystemClock.elapsedRealtime() - lastExpandTimestamp > 500L) {
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

        val pillH = (pillHeightDp * density).toInt()
        val notchYOffset = maxOf(0, (statusBarHeight - pillH) / 2)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            ((pillHeightDp + maxOf(0, yOffsetDp) + 24) * density).toInt(),
            WindowManager.LayoutParams.TYPE_STATUS_BAR_SUB_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            title = "DynamicIslandOverlay"
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }

        windowLayoutParams = params
        islandView = view
        if (isKeyguardOrAod) {
            view.visibility = View.GONE
        }
        islandView?.onCollapseCompleted = {
            if (!isExpanded) {
                updateWindowDimensions(expanded = false)
                islandView?.showPillAfterCollapse()
                if (isStatusBarHidden) {
                    animateHidePill()
                }
            }
        }
        updateIslandDimensionsFromSettings()
    }


    private fun toggleExpand() {
        if (isKeyguardOrAod) return
        if (isExpanded) collapse() else expand()
    }


    private fun expand() {
        if (isExpanded || isKeyguardOrAod) return
        lastExpandTimestamp = SystemClock.elapsedRealtime()
        isExpanded = true
        val view = islandView
        if (view != null) {
            view.animate().cancel()
            view.alpha = 1f
            view.translationY = 0f
            view.scaleX = 1f
            view.scaleY = 1f
        }
        updateWindowDimensions(expanded = true)
        islandView?.setExpanded(true, animate = true)
    }

    private fun collapse(force: Boolean = false) {
        if (!isExpanded) return
        if (!force && SystemClock.elapsedRealtime() - lastExpandTimestamp < 500L) {
            return
        }
        isExpanded = false
        islandView?.setExpanded(false, animate = true)
    }

    private fun updateWindowDimensions(expanded: Boolean) {
        val params = windowLayoutParams ?: return
        val density = context.resources.displayMetrics.density

        params.x = 0
        params.y = 0
        params.width = WindowManager.LayoutParams.MATCH_PARENT

        if (expanded) {
            pillWidthAnimator?.cancel()
            val activeType = resolveActiveActivity()
            val dynamicHeightDp = islandView?.getExpandedHeightDp(activeType) ?: 175f
            val notchCenterYDp = 22.5f + yOffsetDp
            val closedTopYDp = (notchCenterYDp - (pillHeightDp / 2f)).coerceAtLeast(0f)
            val expandedTopYDp = maxOf(closedTopYDp, notchCenterYDp + (pillHeightDp / 2f) + 6f)
            params.height = ((expandedTopYDp + dynamicHeightDp + 60f) * density).toInt()
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            islandView?.let {
                if (isWindowAttached) {
                    try { windowManager.updateViewLayout(it, params) } catch (ignored: Exception) {}
                    it.post { it.requestLayout() }
                    notifyIslandWidthChanged()
                }
            }
        } else {
            val targetHeight = ((pillHeightDp + maxOf(0, yOffsetDp) + 24) * density).toInt()
            params.height = targetHeight
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

            val view = islandView
            if (view != null && isWindowAttached) {
                try { windowManager.updateViewLayout(view, params) } catch (ignored: Exception) {}
                notifyIslandWidthChanged()
            }
        }
    }

    private fun animateHidePill() {
        val view = islandView ?: return
        onIslandWidthChanged?.invoke(0, false, false)
        val density = context.resources.displayMetrics.density
        val hideTransY = if (view.height > 0) -view.height.toFloat() else -((pillHeightDp + maxOf(0, yOffsetDp) + 24) * density)
        view.animate().cancel()
        view.animate()
            .alpha(0f)
            .translationY(hideTransY)
            .scaleX(0.7f)
            .scaleY(0.7f)
            .setDuration(240L)
            .start()
    }

    private fun animateShowPill() {
        val view = islandView ?: return
        val activeType = resolveActiveActivity()
        if (activeType != IslandActivityType.NONE) {
            view.animate().cancel()
            view.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(240L)
                .withEndAction {
                    notifyIslandWidthChanged()
                }
                .start()
            notifyIslandWidthChanged()
        }
    }

    private fun handleStatusBarWindowStateChanged(isHidden: Boolean) {
        if (isStatusBarHidden == isHidden) return
        isStatusBarHidden = isHidden

        if (isHidden) {
            // En modo pantalla completa (juegos, videos), si la isla está colapsada, se oculta acompañando a la barra de estado
            if (!isExpanded) {
                animateHidePill()
            }
        } else {
            // La barra de estado vuelve a ser visible: restaurar la isla si hay actividad activa
            if (!isExpanded) {
                animateShowPill()
            }
        }
    }

    private fun notifyIslandWidthChanged() {
        val activeType = resolveActiveActivity()
        if (!isWindowAttached || !isFeatureEnabled || isKeyguardOrAod || (isStatusBarHidden && !isExpanded) || activeType == IslandActivityType.NONE || islandView?.alpha == 0f) {
            onIslandWidthChanged?.invoke(0, false, false)
            return
        }
        val density = context.resources.displayMetrics.density
        val currentWidthPx = if (isExpanded) {
            ((362 + 24) * density).toInt()
        } else {
            val dynamicWidthDp = islandView?.getEstimatedPillWidthDp(activeType) ?: pillWidthDp
            ((dynamicWidthDp + 24) * density).toInt()
        }
        onIslandWidthChanged?.invoke(currentWidthPx, isExpanded, true)
    }

    private fun showIslandWindow() {
        if (!isFeatureEnabled || isKeyguardOrAod || (isStatusBarHidden && !isExpanded)) return
        if (islandView == null) {
            setupOverlayWindow()
        }
        val view = islandView ?: return
        val params = windowLayoutParams ?: return

        view.animate().cancel()

        if (!isWindowAttached) {
            view.alpha = 0f
            view.scaleX = 0.7f
            view.scaleY = 0.7f
            view.translationY = 0f
            try {
                windowManager.addView(view, params)
                isWindowAttached = true
            } catch (e: Exception) {
                return
            }

            view.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(240)
                .start()
            notifyIslandWidthChanged()
        } else {
            view.alpha = 1f
            view.scaleX = 1f
            view.scaleY = 1f
            view.translationY = 0f
            notifyIslandWidthChanged()
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
     * CALL > SCREEN_RECORD > CHARGING > TRANSIENT_BANNER > NAVIGATION > TIMER >
     * TORCH > HOTSPOT > DOWNLOAD > CAST > PRIVACY > CLIPBOARD > MEDIA > NONE
     */
    private fun resolveActiveActivity(): IslandActivityType {
        return when {
            isCallEnabled && callState.isActive -> IslandActivityType.CALL
            isRecordEnabled && recordState.isRecording -> IslandActivityType.SCREEN_RECORD
            isChargingEnabled && chargingState.isCharging && isChargingBannerActive -> IslandActivityType.CHARGING
            bannerState.isActive -> IslandActivityType.TRANSIENT_BANNER
            isNavigationEnabled && navState.isActive -> IslandActivityType.NAVIGATION
            isTimerEnabled && timerState.isActive -> IslandActivityType.TIMER
            isTorchEnabled && (torchState.isOn || isTorchIntroActive) -> IslandActivityType.TORCH
            isHotspotEnabled && hotspotState.isEnabled -> IslandActivityType.HOTSPOT
            isDownloadEnabled && (downloadState.isDownloading || downloadState.isCompleted || downloadState.isFailed || downloadState.isCancelled) -> IslandActivityType.DOWNLOAD
            isCastEnabled && castState.isCasting -> IslandActivityType.CAST
            isPrivacyEnabled && privacyState.hasAnyPrivacyActive -> IslandActivityType.PRIVACY
            isClipboardEnabled && clipboardState.isActive -> IslandActivityType.CLIPBOARD
            isMusicEnabled && mediaState.hasActiveMedia -> IslandActivityType.MEDIA
            else -> IslandActivityType.NONE
        }
    }

    private fun refreshCurrentActivity() {
        if (!isFeatureEnabled || isKeyguardOrAod) {
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

        when (activeType) {
            IslandActivityType.PRIVACY -> view.showPrivacy(privacyState)
            IslandActivityType.CALL -> view.showCall(callState)
            IslandActivityType.SCREEN_RECORD -> view.showRecording(recordState)
            IslandActivityType.CHARGING -> view.showCharging(chargingState)
            IslandActivityType.NAVIGATION -> view.showNavigation(navState)
            IslandActivityType.TIMER -> view.showTimer(timerState)
            IslandActivityType.TORCH -> view.showTorch(torchState.copy(isIntro = isTorchIntroActive))
            IslandActivityType.CAST -> view.showCast(castState)
            IslandActivityType.DOWNLOAD -> view.showDownload(downloadState)
            IslandActivityType.HOTSPOT -> view.showHotspot(hotspotState)
            IslandActivityType.TRANSIENT_BANNER -> view.showBanner(bannerState)
            IslandActivityType.MEDIA -> view.showMedia(mediaState)
            IslandActivityType.CLIPBOARD -> view.showClipboard(clipboardState)
            else -> {}
        }

        updateWindowDimensions(isExpanded)

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

        if (activeType == IslandActivityType.MEDIA) {
            // Compose updates the progress bar smoothly and continuously; no need to re-emit showMedia every second
        } else if (activeType == IslandActivityType.DOWNLOAD && downloadState.isDownloading) {
            checkDownloads()
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
            if (mediaState.hasActiveMedia) {
                val wasPlaying = mediaState.isPlaying
                mediaState = mediaState.copy(isPlaying = false)
                if (wasPlaying || !mainHandler.hasCallbacks(mediaPauseHideRunnable)) {
                    mainHandler.removeCallbacks(mediaPauseHideRunnable)
                    val timeoutMs = getMediaPauseTimeoutMs()
                    if (timeoutMs > 0L) {
                        mainHandler.postDelayed(mediaPauseHideRunnable, timeoutMs)
                    }
                }
                refreshCurrentActivity()
            } else {
                mediaState = IslandMediaState(hasActiveMedia = false)
                refreshCurrentActivity()
            }
        }
    }

    private fun handlePlaybackStateChanged(state: PlaybackState?) {
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING
        val pos = state?.position ?: 0L

        val wasPlaying = mediaState.isPlaying
        val hasMedia = isPlaying || (mediaState.hasActiveMedia && (state != null || mediaState.title.isNotBlank()))

        val pkgPlayback = activeMediaController?.packageName ?: mediaState.packageName
        mediaState = mediaState.copy(
            isPlaying = isPlaying,
            positionMs = pos,
            lastUpdateTimeMs = SystemClock.elapsedRealtime(),
            hasActiveMedia = hasMedia,
            packageName = pkgPlayback
        )

        if (!isPlaying) {
            if (wasPlaying || !mainHandler.hasCallbacks(mediaPauseHideRunnable)) {
                mainHandler.removeCallbacks(mediaPauseHideRunnable)
                val timeoutMs = getMediaPauseTimeoutMs()
                if (timeoutMs > 0L) {
                    mainHandler.postDelayed(mediaPauseHideRunnable, timeoutMs)
                }
            }
        } else {
            mainHandler.removeCallbacks(mediaPauseHideRunnable)
        }

        refreshCurrentActivity()
    }

    private fun handleMetadataChanged(metadata: MediaMetadata?) {
        if (metadata == null) return

        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0L)
        var art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata.description?.iconBitmap

        if (art == null) {
            val uri = metadata.description?.iconUri
                ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)?.let { Uri.parse(it) }
                ?: metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)?.let { Uri.parse(it) }
            if (uri != null) {
                try {
                    art = ImageDecoder.decodeBitmap(
                        ImageDecoder.createSource(context.contentResolver, uri)
                    )
                } catch (ignored: Exception) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            art = BitmapFactory.decodeStream(stream)
                        }
                    } catch (ignored2: Exception) {}
                }
            }
        }

        val pkg = activeMediaController?.packageName ?: mediaState.packageName
        if (art == null && pkg.isNotBlank()) {
            try {
                val d = context.packageManager.getApplicationIcon(pkg)
                if (d is android.graphics.drawable.BitmapDrawable && d.bitmap != null) {
                    art = d.bitmap
                } else {
                    val w = d.intrinsicWidth.takeIf { it > 0 } ?: 128
                    val h = d.intrinsicHeight.takeIf { it > 0 } ?: 128
                    val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(b)
                    d.setBounds(0, 0, w, h)
                    d.draw(canvas)
                    art = b
                }
            } catch (ignored: Exception) {}
        }

        val dominantColor = art?.let { extractColor(it) }

        mediaState = mediaState.copy(
            title = title,
            artist = artist,
            durationMs = duration,
            albumArt = art,
            dominantColor = dominantColor,
            hasActiveMedia = true,
            packageName = pkg
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
        val pendingIntent = activeMediaController?.sessionActivity
        if (pendingIntent != null) {
            try {
                pendingIntent.send()
                collapse()
                return
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send sessionActivity PendingIntent, falling back to launch intent", e)
            }
        }
        val pkg = mediaState.packageName.ifBlank { activeMediaController?.packageName ?: "" }
        if (pkg.isNotBlank()) {
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    context.startActivity(launchIntent)
                    collapse()
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start activity for package $pkg", e)
            }
        }
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
            recordState.stopIntent?.send()
        } catch (e: Exception) {
            Log.e(TAG, "Error sending stopIntent", e)
        }
        try {
            android.app.PendingIntent.getService(
                context,
                com.android.systemui.screenrecord.RecordingService.REQUEST_CODE,
                com.android.systemui.screenrecord.RecordingService.getStopIntent(context),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            ).send()
        } catch (_: Exception) {
            try {
                context.startService(com.android.systemui.screenrecord.RecordingService.getStopIntent(context))
            } catch (_: Exception) {}
        }
        try {
            val modernIntent = Intent(context, com.android.systemui.screenrecord.service.ScreenRecordingService::class.java).apply {
                action = com.android.systemui.screenrecord.service.ScreenRecordingService.ACTION_STOP
                putExtra(Intent.EXTRA_USER_HANDLE, context.userId)
            }
            context.startService(modernIntent)
        } catch (_: Exception) {}
        try {
            context.sendBroadcast(Intent("com.android.systemui.screenrecord.ACTION_STOP"))
        } catch (_: Exception) {}
        recordState = IslandRecordState(isRecording = false)
        DynamicIslandManager.updateScreenRecording(false)
        collapse()
        refreshCurrentActivity()
    }

    // --- Torch / Flashlight ---
    fun toggleTorch() {
        try {
            val fc = try {
                com.android.systemui.Dependency.get(com.android.systemui.statusbar.policy.FlashlightController::class.java)
            } catch (e: Exception) {
                null
            }
            if (fc != null && fc.isAvailable) {
                val newState = !fc.isEnabled
                fc.setFlashlight(newState)
                torchState = IslandTorchState(isOn = newState)
                DynamicIslandManager.updateTorch(newState)
                if (!newState) {
                    if (isExpanded) collapse()
                    showTurnOffBanner(
                        title = context.getString(R.string.island_torch_off),
                        iconRes = R.drawable.ic_hyper_torch,
                        tintColor = Color.parseColor("#9E9E9E")
                    )
                } else {
                    refreshCurrentActivity()
                }
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling torch via FlashlightController", e)
        }
        try {
            val cm = cameraManager ?: return
            val newState = !torchState.isOn
            cm.setTorchMode("0", newState)
            torchState = IslandTorchState(isOn = newState)
            DynamicIslandManager.updateTorch(newState)
            if (!newState) {
                if (isExpanded) collapse()
                showTurnOffBanner(
                    title = context.getString(R.string.island_torch_off),
                    iconRes = R.drawable.ic_hyper_torch,
                    tintColor = Color.parseColor("#9E9E9E")
                )
            } else {
                refreshCurrentActivity()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling torch", e)
        }
    }

    // --- Wi-Fi Hotspot ---
    fun toggleHotspot() {
        try {
            val hotspotController = try {
                com.android.systemui.Dependency.get(com.android.systemui.statusbar.policy.HotspotController::class.java)
            } catch (e: Exception) {
                null
            }
            val tm = context.getSystemService(TetheringManager::class.java)
            val isCurrentlyEnabled = hotspotController?.isHotspotEnabled == true || hotspotState.isEnabled
            if (isCurrentlyEnabled) {
                hotspotController?.setHotspotEnabled(false)
                tm?.stopTethering(TetheringManager.TETHERING_WIFI)
                hotspotState = IslandHotspotState(isEnabled = false, clientCount = 0)
                if (isExpanded) collapse()
                DynamicIslandManager.updateHotspot(false, 0)
                showTurnOffBanner(
                    title = context.getString(R.string.island_hotspot_off),
                    iconRes = R.drawable.ic_hyper_hotspot,
                    tintColor = Color.parseColor("#9E9E9E")
                )
            } else {
                hotspotController?.setHotspotEnabled(true)
                tm?.startTethering(
                    TetheringManager.TETHERING_WIFI,
                    context.mainExecutor,
                    object : TetheringManager.StartTetheringCallback {}
                )
                hotspotState = IslandHotspotState(isEnabled = true, clientCount = 0)
                DynamicIslandManager.updateHotspot(true, 0)
                refreshCurrentActivity()
            }
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
                val wasEnabled = hotspotState.isEnabled
                mainHandler.removeCallbacks(hotspotBannerDismissRunnable)
                hotspotState = IslandHotspotState(isEnabled = false, clientCount = 0, showIntroBanner = false)
                DynamicIslandManager.updateHotspot(false, 0)
                if (wasEnabled) {
                    if (isExpanded) collapse()
                    showTurnOffBanner(
                        title = context.getString(R.string.island_hotspot_off),
                        iconRes = R.drawable.ic_hyper_hotspot,
                        tintColor = Color.parseColor("#9E9E9E")
                    )
                } else {
                    refreshCurrentActivity()
                }
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
    private fun checkCastState() {
        if (!isCastEnabled) return
        val controller = castController ?: return
        val devices = try { controller.castDevices } catch (_: Exception) { emptyList() }
        var activeDevice: com.android.systemui.statusbar.policy.CastDevice? = null
        for (device in devices) {
            if (device.isCasting) {
                activeDevice = device
                break
            }
        }
        val isCasting = activeDevice != null
        val rawName = activeDevice?.name?.trim() ?: ""
        val deviceName = if (rawName.isNotBlank()) rawName else context.getString(R.string.island_wireless_display)
        val castType = activeDevice?.description?.ifBlank { context.getString(R.string.island_casting_screen) }
            ?: context.getString(R.string.island_casting_screen)

        if (castState.isCasting != isCasting || castState.deviceName != deviceName) {
            castState = IslandCastState(
                isCasting = isCasting,
                deviceName = deviceName,
                castType = castType
            )
            islandView?.showCast(castState)
            DynamicIslandManager.updateCast(
                isCasting = isCasting,
                deviceName = deviceName,
                castType = castType
            )
            refreshCurrentActivity()
        }
    }

    fun disconnectCast() {
        val controller = castController
        if (controller != null) {
            try {
                for (device in controller.castDevices) {
                    if (device.isCasting) {
                        controller.stopCasting(device, android.media.projection.StopReason.STOP_QS_TILE)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping cast", e)
            }
        }
        try {
            context.sendBroadcast(Intent("android.intent.action.DISCONNECT_CAST"))
        } catch (ignored: Exception) {}
        castState = IslandCastState(isCasting = false)
        islandView?.showCast(castState)
        DynamicIslandManager.updateCast(isCasting = false)
        collapse()
        refreshCurrentActivity()
    }

    private fun handleCastIntent(intent: Intent) {
        val isCasting = intent.getBooleanExtra("is_casting", false)
        val deviceName = intent.getStringExtra("device_name") ?: context.getString(R.string.island_wireless_display)
        val castType = intent.getStringExtra("cast_type") ?: context.getString(R.string.island_wireless_display)
        castState = IslandCastState(isCasting = isCasting, deviceName = deviceName, castType = castType)
        islandView?.showCast(castState)
        DynamicIslandManager.updateCast(isCasting = isCasting, deviceName = deviceName, castType = castType)
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

    private fun checkDownloads() {
        if (!isDownloadEnabled) return
        // If an active download is tracked via notification (Play Store, Chrome, etc.), do not overwrite with DownloadManager
        if (downloadState.isDownloading && downloadState.sourcePackage.isNotBlank() && downloadState.sourcePackage != "download_manager") {
            return
        }
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (dm != null) {
                try {
                    dm.setAccessAllDownloads(true)
                } catch (ignored: Exception) {}

                val query = DownloadManager.Query().setFilterByStatus(
                    DownloadManager.STATUS_RUNNING
                )
                val cursor = dm.query(query)
                var foundActive = false
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        val titleIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
                        val bytesIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val totalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)

                        val fileName = if (titleIndex >= 0) cursor.getString(titleIndex) ?: "" else ""
                        if (fileName.contains("metadata", ignoreCase = true) ||
                            fileName.contains(".model.", ignoreCase = true) ||
                            fileName.contains("speech", ignoreCase = true) ||
                            fileName.contains("es.model", ignoreCase = true)) {
                            continue
                        }
                        val downloadedBytes = if (bytesIndex >= 0) cursor.getLong(bytesIndex) else 0L
                        val totalBytes = if (totalIndex >= 0) cursor.getLong(totalIndex) else 0L
                        val status = if (statusIndex >= 0) cursor.getInt(statusIndex) else 0

                        // Must be actively running AND transferring bytes or have known total size
                        if (status == DownloadManager.STATUS_RUNNING && (downloadedBytes > 0L || totalBytes > 0L)) {
                            val progress = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
                            val totalSizeText = if (totalBytes > 0) android.text.format.Formatter.formatFileSize(context, totalBytes) else ""
                            val downloadedText = if (downloadedBytes > 0) android.text.format.Formatter.formatFileSize(context, downloadedBytes) else ""

                            val speedDisplay = if (downloadedText.isNotBlank() && totalSizeText.isNotBlank()) {
                                "$downloadedText / $totalSizeText"
                            } else if (downloadedText.isNotBlank()) {
                                downloadedText
                            } else {
                                ""
                            }

                            downloadState = IslandDownloadState(
                                isDownloading = true,
                                fileName = fileName.ifBlank { context.getString(R.string.island_download) },
                                progress = progress,
                                speedText = speedDisplay,
                                totalSizeText = totalSizeText,
                                isCompleted = false,
                                isFailed = false,
                                isCancelled = false,
                                sourcePackage = "download_manager"
                            )
                            islandView?.showDownload(downloadState)
                            refreshCurrentActivity()
                            foundActive = true
                            break
                        }
                    }
                    cursor.close()
                }
                if (foundActive) return
            }

            // Fallback: Check active notifications for downloads (Chrome, Firefox, Telegram, etc.)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            val activeNotifs = nm?.activeNotifications
            if (activeNotifs != null) {
                for (sbn in activeNotifs) {
                    val notif = sbn.notification ?: continue
                    val extras = notif.extras ?: continue
                    val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()
                        ?: context.getString(R.string.island_download)

                    if (title.contains("metadata", ignoreCase = true) ||
                        title.contains(".model.", ignoreCase = true) ||
                        title.contains("es.model", ignoreCase = true)) {
                        continue
                    }

                    val maxProgress = extras.getInt(android.app.Notification.EXTRA_PROGRESS_MAX, 0)
                    val currentProgress = extras.getInt(android.app.Notification.EXTRA_PROGRESS, -1)
                    val isIndeterminate = extras.getBoolean(android.app.Notification.EXTRA_PROGRESS_INDETERMINATE, false)

                    val isDownloadCategory = notif.category == android.app.Notification.CATEGORY_PROGRESS ||
                        notif.category == android.app.Notification.CATEGORY_STATUS ||
                        sbn.packageName.contains("download", ignoreCase = true) ||
                        sbn.packageName.contains("chrome", ignoreCase = true) ||
                        sbn.packageName.contains("brave", ignoreCase = true) ||
                        sbn.packageName.contains("firefox", ignoreCase = true) ||
                        sbn.packageName.contains("browser", ignoreCase = true) ||
                        sbn.packageName == "com.android.vending" ||
                        sbn.packageName.contains("vending")

                    if ((maxProgress > 0 && currentProgress >= 0) || (isIndeterminate && isDownloadCategory)) {
                        val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
                        val progressPct = if (maxProgress > 0) ((currentProgress * 100) / maxProgress).coerceIn(0, 100) else 0

                        val speedStr = if (progressPct > 0) "$progressPct%" else if (text != "0 B") text else ""
                        downloadState = IslandDownloadState(
                            isDownloading = true,
                            fileName = title,
                            progress = progressPct,
                            speedText = speedStr,
                            totalSizeText = if (text != "0 B") text else "",
                            isCompleted = false,
                            isFailed = false,
                            isCancelled = false,
                            sourcePackage = sbn.packageName
                        )
                        islandView?.showDownload(downloadState)
                        refreshCurrentActivity()
                        return
                    }
                }
            }

            if (downloadState.isDownloading && (downloadState.sourcePackage == "download_manager" || downloadState.sourcePackage.isBlank())) {
                var actuallyCompleted = false
                if (downloadState.progress >= 90) {
                    try {
                        val finishedQuery = DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_SUCCESSFUL)
                        val finishedCursor = dm?.query(finishedQuery)
                        if (finishedCursor != null) {
                            if (finishedCursor.moveToFirst()) {
                                actuallyCompleted = true
                            }
                            finishedCursor.close()
                        }
                    } catch (_: Exception) {}
                }

                if (actuallyCompleted || downloadState.progress >= 98) {
                    downloadState = downloadState.copy(
                        isDownloading = false,
                        isCompleted = true,
                        progress = 100
                    )
                    islandView?.showDownload(downloadState)
                    mainHandler.removeCallbacks(downloadDismissRunnable)
                    mainHandler.postDelayed(downloadDismissRunnable, 3500L)
                } else {
                    downloadState = IslandDownloadState(isDownloading = false)
                    islandView?.showDownload(downloadState)
                }
                refreshCurrentActivity()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking downloads", e)
        }
    }

    private fun handleDownloadIntent(intent: Intent) {
        if (!isDownloadEnabled) return
        val isDownloading = intent.getBooleanExtra("is_downloading", false)
        val fileName = intent.getStringExtra("file_name") ?: context.getString(R.string.island_download)
        val progress = intent.getIntExtra("progress", 0).coerceIn(0, 100)
        val speedText = intent.getStringExtra("speed_text") ?: ""
        val totalSizeText = intent.getStringExtra("total_size_text") ?: ""
        val isCompleted = intent.getBooleanExtra("is_completed", false)
        val isFailed = intent.getBooleanExtra("is_failed", false)
        val isCancelled = intent.getBooleanExtra("is_cancelled", false)
        val cancelIntent: PendingIntent? = intent.getParcelableExtra("cancel_intent", PendingIntent::class.java)

        downloadState = IslandDownloadState(
            isDownloading = isDownloading,
            fileName = fileName,
            progress = progress,
            speedText = speedText,
            totalSizeText = totalSizeText,
            isCompleted = isCompleted,
            isFailed = isFailed,
            isCancelled = isCancelled,
            cancelIntent = cancelIntent
        )
        if (isCompleted || isFailed || isCancelled) {
            mainHandler.removeCallbacks(downloadDismissRunnable)
            mainHandler.postDelayed(downloadDismissRunnable, 4000L)
        }
        refreshCurrentActivity()
    }

    private fun handleNotificationPosted(sbn: StatusBarNotification) {
        if (!isFeatureEnabled) return
        val notification = sbn.notification ?: return
        val pkg = sbn.packageName.lowercase()
        val channelId = notification.channelId?.lowercase() ?: ""
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""

        // Blacklist internal system/voice/model metadata and silent background updates
        val isInternalSystemMetadata = title.contains("metadata", ignoreCase = true) ||
                title.contains(".model.", ignoreCase = true) ||
                title.contains("speech", ignoreCase = true) ||
                title.contains("synthesizer", ignoreCase = true) ||
                title.contains("split_config", ignoreCase = true) ||
                title.endsWith(".tmp", ignoreCase = true) ||
                title.contains("es.model", ignoreCase = true) ||
                pkg == "com.google.android.tts" ||
                pkg == "com.google.android.as" ||
                pkg == "com.google.android.gms"

        if (isInternalSystemMetadata) {
            return
        }

        // Screen recording active notification detection
        val isScreenRecordNotif = (pkg == "com.android.systemui" || pkg.contains("screenrecord")) && (
            channelId.contains("record") ||
            channelId == "screen_record" ||
            title.contains("grab", ignoreCase = true) ||
            title.contains("record", ignoreCase = true)
        )
        if (isScreenRecordNotif && isRecordEnabled) {
            val isOngoing = (notification.flags and android.app.Notification.FLAG_ONGOING_EVENT) != 0
            val isSavedOrProcessing = title.contains("guardad", ignoreCase = true) ||
                title.contains("saved", ignoreCase = true) ||
                title.contains("finaliz", ignoreCase = true) ||
                title.contains("complet", ignoreCase = true) ||
                title.contains("proces", ignoreCase = true) ||
                notification.group == "screenrecord_saved"

            val stopAction = notification.actions?.firstOrNull { action ->
                val at = action.title?.toString()?.lowercase() ?: ""
                at.contains("stop") || at.contains("detener") || at.contains("parar") || at.contains("cancel")
            }

            if (!isOngoing || isSavedOrProcessing || stopAction == null) {
                if (recordState.isRecording) {
                    recordState = IslandRecordState(isRecording = false)
                    DynamicIslandManager.updateScreenRecording(false)
                    islandView?.showRecording(recordState)
                    refreshCurrentActivity()
                }
                return
            }

            val startMs = if (notification.`when` > 0) notification.`when` else SystemClock.elapsedRealtime()
            recordState = IslandRecordState(
                isRecording = true,
                startTimeMs = startMs,
                stopIntent = stopAction.actionIntent
            )
            DynamicIslandManager.updateScreenRecording(true, recordState.startTimeMs)
            islandView?.showRecording(recordState)
            refreshCurrentActivity()
            return
        }

        val maxProgress = extras.getInt(android.app.Notification.EXTRA_PROGRESS_MAX, 0)
        val curProgress = extras.getInt(android.app.Notification.EXTRA_PROGRESS, 0)
        val isProgressIndeterminate = extras.getBoolean(android.app.Notification.EXTRA_PROGRESS_INDETERMINATE, false)

        val allTexts = listOfNotNull(
            extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString(),
            extras.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT)?.toString(),
            extras.getCharSequence(android.app.Notification.EXTRA_INFO_TEXT)?.toString(),
            extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString(),
            title
        ).joinToString(" ")

        val percentRegex = Regex("""(\d{1,3})\s*%""")
        val percentMatch = percentRegex.find(allTexts)
        val parsedPercent = percentMatch?.groupValues?.get(1)?.toIntOrNull()

        val isDownloadPkgOrChannel = pkg == "com.android.providers.downloads" ||
                pkg == "com.android.vending" ||
                pkg.contains("chrome") ||
                pkg.contains("browser") ||
                pkg.contains("brave") ||
                pkg.contains("firefox") ||
                pkg.contains("opera") ||
                pkg.contains("edge") ||
                pkg.contains("download") ||
                channelId.contains("download") ||
                channelId.contains("install") ||
                channelId.contains("transfer") ||
                notification.category == android.app.Notification.CATEGORY_PROGRESS

        if (!isDownloadPkgOrChannel) return

        val percent = if (maxProgress > 0) {
            ((curProgress.toLong() * 100L) / maxProgress.toLong()).toInt().coerceIn(0, 100)
        } else {
            (parsedPercent ?: 0).coerceIn(0, 100)
        }

        val hasProgressBar = maxProgress > 0 || parsedPercent != null
        val isActivelyDownloading = (maxProgress > 0 && curProgress < maxProgress) ||
                (parsedPercent != null && parsedPercent < 100) ||
                (isProgressIndeterminate && isDownloadPkgOrChannel)

        // 1. ACTIVE RUNNING DOWNLOAD
        if (isActivelyDownloading && isDownloadEnabled) {
            val bigTitle = extras.getCharSequence(android.app.Notification.EXTRA_TITLE_BIG)?.toString()?.trim() ?: ""
            val titleText = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
            val textContent = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
            val bigTextContent = extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString()?.trim() ?: ""

            var downloadTitle = when {
                bigTitle.isNotBlank() && !bigTitle.equals("Google Play Store", ignoreCase = true) && !bigTitle.equals("Google Play", ignoreCase = true) -> bigTitle
                titleText.isNotBlank() && !titleText.equals("Google Play Store", ignoreCase = true) && !titleText.equals("Google Play", ignoreCase = true) -> titleText
                else -> ""
            }

            if (downloadTitle.isBlank() && (pkg == "com.android.vending" || pkg.contains("vending"))) {
                val candidate = textContent.ifBlank { bigTextContent }
                val match = Regex("""(?:Descargando|Instalando|Actualizando|Downloading|Installing|Updating)\s+([^(:\n]+)""", RegexOption.IGNORE_CASE).find(candidate)
                if (match != null) {
                    downloadTitle = match.groupValues[1].trim()
                }
            }
            if (downloadTitle.isBlank()) {
                downloadTitle = if (titleText.isNotBlank()) titleText else context.getString(R.string.island_download)
            }

            val speedRegex = Regex("""(\d+(?:\.\d+)?\s*(?:MB/s|KB/s|GB/s|kB/s|mb/s|kb/s|mB/s))""", RegexOption.IGNORE_CASE)
            val speedMatch = speedRegex.find(allTexts)
            val sizeRegex = Regex("""(\d+(?:\.\d+)?\s*(?:MB|GB|KB)\s*(?:/|de)\s*\d+(?:\.\d+)?\s*(?:MB|GB|KB))""", RegexOption.IGNORE_CASE)
            val sizeMatch = sizeRegex.find(allTexts)
            val singleSizeRegex = Regex("""(\d+(?:\.\d+)?\s*(?:MB|GB|KB))""", RegexOption.IGNORE_CASE)
            val singleSizeMatch = singleSizeRegex.find(allTexts)

            val parsedSpeed = when {
                speedMatch != null -> speedMatch.value.replace(" ", "")
                sizeMatch != null -> sizeMatch.value.trim()
                singleSizeMatch != null -> singleSizeMatch.value.replace(" ", "")
                else -> ""
            }
            val cleanSpeed = if (parsedSpeed == "0 B" || parsedSpeed.startsWith("0 B")) "" else parsedSpeed

            val cancelAction = notification.actions?.firstOrNull { action ->
                val at = action.title?.toString()?.lowercase() ?: ""
                at.contains("cancel") || at.contains("cancelar") || at.contains("pausar") || at.contains("pause")
            }

            downloadState = IslandDownloadState(
                isDownloading = true,
                fileName = downloadTitle,
                progress = percent,
                cancelIntent = cancelAction?.actionIntent,
                speedText = cleanSpeed,
                totalSizeText = if (sizeMatch != null) sizeMatch.value.trim() else "",
                isCompleted = false,
                isFailed = false,
                isCancelled = false,
                sourcePackage = pkg
            )
            islandView?.showDownload(downloadState)
            mainHandler.removeCallbacks(downloadDismissRunnable)
            refreshCurrentActivity()
            return
        }

        // 2. FAILED DOWNLOAD (Only if was previously downloading)
        val isFailedText = allTexts.contains("error al descargar", ignoreCase = true) ||
                allTexts.contains("descarga cancelada", ignoreCase = true) ||
                allTexts.contains("descarga fallida", ignoreCase = true) ||
                allTexts.contains("download failed", ignoreCase = true) ||
                allTexts.contains("download cancelled", ignoreCase = true)

        if (isFailedText && downloadState.isDownloading) {
            if (isDownloadEnabled) {
                downloadState = downloadState.copy(
                    isDownloading = false,
                    isCompleted = false,
                    isFailed = true,
                    fileName = title.ifBlank { downloadState.fileName.ifBlank { "Descarga fallida" } }
                )
                islandView?.showDownload(downloadState)
                refreshCurrentActivity()
                mainHandler.removeCallbacks(downloadDismissRunnable)
                mainHandler.postDelayed(downloadDismissRunnable, 3500L)
            }
            return
        }

        // 3. COMPLETED DOWNLOAD (Must have been downloading or explicit finished match)
        val isFinishedText = allTexts.contains("descarga completa", ignoreCase = true) ||
                allTexts.contains("descarga finalizada", ignoreCase = true) ||
                allTexts.contains("descarga correcta", ignoreCase = true) ||
                allTexts.contains("download complete", ignoreCase = true) ||
                allTexts.contains("download finished", ignoreCase = true) ||
                (percent >= 100 && hasProgressBar)

        if (isFinishedText && downloadState.isDownloading) {
            if (isDownloadEnabled) {
                downloadState = downloadState.copy(
                    isDownloading = false,
                    isCompleted = true,
                    isFailed = false,
                    progress = 100,
                    fileName = title.ifBlank { downloadState.fileName.ifBlank { "Descarga completa" } }
                )
                islandView?.showDownload(downloadState)
                refreshCurrentActivity()
                mainHandler.removeCallbacks(downloadDismissRunnable)
                mainHandler.postDelayed(downloadDismissRunnable, 3500L)
            }
            return
        }
    }

    private fun handleNotificationRemoved(sbn: StatusBarNotification) {
        val notification = sbn.notification ?: return
        val pkg = sbn.packageName.lowercase()
        val channelId = notification.channelId?.lowercase() ?: ""

        val isScreenRecordRemoval = (pkg == "com.android.systemui" || pkg.contains("screenrecord")) && (
            channelId.contains("record") || channelId == "screen_record"
        )
        if (isScreenRecordRemoval && recordState.isRecording) {
            recordState = IslandRecordState(isRecording = false)
            DynamicIslandManager.updateScreenRecording(false)
            islandView?.showRecording(recordState)
            refreshCurrentActivity()
            return
        }

        val isDownloadRemoval = pkg == "com.android.providers.downloads" ||
            pkg == "com.android.vending" ||
            pkg.contains("chrome") ||
            pkg.contains("browser") ||
            pkg.contains("brave") ||
            pkg.contains("firefox") ||
            pkg.contains("opera") ||
            pkg.contains("edge") ||
            pkg.contains("download") ||
            channelId.contains("download") ||
            channelId.contains("install") ||
            channelId.contains("transfer") ||
            notification.category == android.app.Notification.CATEGORY_PROGRESS

        if (isDownloadRemoval && isDownloadEnabled) {
            if (downloadState.isDownloading && (downloadState.sourcePackage == pkg || downloadState.sourcePackage.isBlank())) {
                if (downloadState.progress >= 85) {
                    downloadState = downloadState.copy(
                        isDownloading = false,
                        isCompleted = true,
                        progress = 100
                    )
                    islandView?.showDownload(downloadState)
                    mainHandler.removeCallbacks(downloadDismissRunnable)
                    mainHandler.postDelayed(downloadDismissRunnable, 3500L)
                } else {
                    downloadState = IslandDownloadState(isDownloading = false)
                    islandView?.showDownload(downloadState)
                }
                refreshCurrentActivity()
            }
        }
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
    private fun handlePrivacyOp(op: String, uid: Int, active: Boolean, pkgName: String?) {
        when (op) {
            AppOpsManager.OPSTR_CAMERA -> {
                if (active) activeCameraUids.add(uid) else activeCameraUids.remove(uid)
            }
            AppOpsManager.OPSTR_RECORD_AUDIO -> {
                if (active) activeMicUids.add(uid) else activeMicUids.remove(uid)
            }
            AppOpsManager.OPSTR_FINE_LOCATION, AppOpsManager.OPSTR_COARSE_LOCATION -> {
                if (active) activeLocUids.add(uid) else activeLocUids.remove(uid)
            }
        }

        val curCam = activeCameraUids.isNotEmpty()
        val curMic = activeMicUids.isNotEmpty()
        val curLoc = activeLocUids.isNotEmpty()

        val appName = if (curCam || curMic || curLoc) {
            if (active && !pkgName.isNullOrBlank()) {
                try {
                    val appInfo = context.packageManager.getApplicationInfo(pkgName, 0)
                    context.packageManager.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    pkgName
                }
            } else privacyState.appName
        } else ""

        privacyState = IslandPrivacyState(
            isCameraInUse = curCam,
            isMicInUse = curMic,
            isLocationInUse = curLoc,
            appName = appName
        )
        DynamicIslandManager.updatePrivacy(
            isCameraInUse = curCam,
            isMicInUse = curMic,
            isLocationInUse = curLoc,
            appName = appName
        )
        refreshCurrentActivity()
    }

    // --- Clipboard Preview Banner ---
    private fun handleClipboardText(text: String) {
        if (!isClipboardEnabled) return
        val preview = if (text.length > 36) text.take(36) + "..." else text
        clipboardState = IslandClipboardState(isActive = true, copiedText = preview)
        mainHandler.removeCallbacks(clipboardDismissRunnable)
        mainHandler.postDelayed(clipboardDismissRunnable, 3000L)
        refreshCurrentActivity()
    }

    // --- Battery & Charging ---
    private fun handleBatteryEvent(intent: Intent, forceConnected: Boolean = false) {
        val batteryIntent = if (intent.action == Intent.ACTION_BATTERY_CHANGED && intent.hasExtra(BatteryManager.EXTRA_STATUS)) {
            intent
        } else {
            try {
                context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: intent
            } catch (e: Exception) {
                intent
            }
        }

        val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = forceConnected ||
                status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        if (isCharging) {
            mainHandler.removeCallbacks(powerDisconnectRunnable)
        }

        val isNewConnection = forceConnected || (intent.action == Intent.ACTION_POWER_CONNECTED) || (!wasCharging && isCharging)
        wasCharging = isCharging

        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val batteryPct = if (scale > 0) (level * 100 / scale) else level

        if (!isCharging) {
            mainHandler.removeCallbacks(powerDisconnectRunnable)
            mainHandler.postDelayed(powerDisconnectRunnable, 600L)
            DynamicIslandManager.updateBattery(isCharging = false, level = batteryPct, watts = 0.0, isNewConnection = false)
            if (batteryPct <= 20) {
                checkAndTriggerLowBattery(batteryPct)
            } else if (batteryPct > 25) {
                hasShownLowBatteryAlert = false
            }
            return
        } else {
            hasShownLowBatteryAlert = false
        }

        val watts = calculateWatts(batteryIntent)
        val wattsChanged = abs(watts - lastChargingWatts) >= 2.0 && watts > 1.0
        val shouldShowBanner = isNewConnection || (wattsChanged && !isChargingBannerActive)

        val wattFormatted = if (watts >= 10.0) String.format(java.util.Locale.US, "%.0fW", watts) else String.format(java.util.Locale.US, "%.1fW", watts)
        val title = when {
            watts >= 65.0 -> "${context.getString(R.string.island_charging_hypercharge)} · $wattFormatted"
            watts >= 30.0 -> "${context.getString(R.string.island_charging_turbo)} · $wattFormatted"
            watts >= 15.0 -> "${context.getString(R.string.island_charging_fast)} · $wattFormatted"
            watts > 0.5 -> "${context.getString(R.string.island_charging_fast)} · $wattFormatted"
            else -> context.getString(R.string.island_charging_fast)
        }

        chargingState = IslandChargingState(
            isCharging = true,
            level = batteryPct,
            watts = watts,
            isNewConnection = isNewConnection,
            title = title
        )

        if (shouldShowBanner) {
            lastChargingWatts = watts
            isChargingBannerActive = true
            mainHandler.removeCallbacks(chargingDismissRunnable)
            mainHandler.postDelayed(chargingDismissRunnable, 4000L)
            refreshCurrentActivity()
        } else if (isChargingBannerActive) {
            lastChargingWatts = watts
            refreshCurrentActivity()
        }
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
    private var hasShownLowBatteryAlert = false

    private fun handleBatteryLowEvent(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 20)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val batteryPct = if (scale > 0) (level * 100 / scale) else level
        checkAndTriggerLowBattery(batteryPct)
    }

    private fun checkAndTriggerLowBattery(batteryPct: Int) {
        if (hasShownLowBatteryAlert || isKeyguardOrAod || !isFeatureEnabled) return
        hasShownLowBatteryAlert = true

        bannerState = IslandBannerState(
            isActive = true,
            title = context.getString(R.string.island_battery_low),
            subtitle = String.format(java.util.Locale.getDefault(), context.getString(R.string.island_battery_low_sub), batteryPct),
            badge = "$batteryPct%",
            iconRes = R.drawable.battery_meter_progress_low,
            tintColor = Color.parseColor("#FF453A")
        )

        mainHandler.removeCallbacks(bannerDismissRunnable)
        mainHandler.postDelayed(bannerDismissRunnable, 4500L)
        refreshCurrentActivity()
    }

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

    private fun showTurnOffBanner(title: String, subtitle: String = "", iconRes: Int, tintColor: Int) {
        bannerState = IslandBannerState(
            isActive = true,
            title = title,
            subtitle = subtitle,
            iconRes = iconRes,
            tintColor = tintColor
        )
        mainHandler.removeCallbacks(bannerDismissRunnable)
        mainHandler.postDelayed(bannerDismissRunnable, 3500L)
        refreshCurrentActivity()
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
