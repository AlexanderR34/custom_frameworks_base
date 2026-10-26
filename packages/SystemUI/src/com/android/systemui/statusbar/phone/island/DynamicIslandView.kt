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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import com.android.systemui.compose.ComposeInitializer
import com.android.systemui.statusbar.phone.island.compose.ConfigManager
import com.android.systemui.statusbar.phone.island.compose.DynamicIsland
import com.android.systemui.statusbar.phone.island.compose.DynamicIslandLabTheme
import com.android.systemui.statusbar.phone.island.compose.DynamicIslandManager
import com.android.systemui.statusbar.phone.island.compose.IconPackStyle
import com.android.systemui.statusbar.phone.island.compose.IslandActivityType as ComposeActivityType
import com.android.systemui.statusbar.phone.island.compose.TransientBannerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Native Jetpack Compose Dynamic Island View for SystemUI.
 * Hosts the DynamicIsland composable with full spring physics, custom canvas morphing,
 * and direct system state updates.
 */
class DynamicIslandView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    // Interaction callbacks wired from DynamicIslandController
    var onToggleExpand: (() -> Unit)? = null
    var onPlayPauseClicked: (() -> Unit)? = null
    var onPrevClicked: (() -> Unit)? = null
    var onNextClicked: (() -> Unit)? = null
    var onEndCallClicked: (() -> Unit)? = null
    var onMuteToggled: (() -> Unit)? = null
    var onOpenMediaAppClicked: (() -> Unit)? = null
    var onSeekTo: ((Long) -> Unit)? = null
    var onRecordStopClicked: (() -> Unit)? = null
    var onToggleTorchClicked: (() -> Unit)? = null
    var onToggleHotspotClicked: (() -> Unit)? = null
    var onDisconnectCastClicked: (() -> Unit)? = null
    var onCancelDownloadClicked: (() -> Unit)? = null
    var onOpenNavigationClicked: (() -> Unit)? = null
    var onTimerPauseClicked: (() -> Unit)? = null
    var onTimerResetClicked: (() -> Unit)? = null
    var onCollapseCompleted: (() -> Unit)? = null
    var onPillWidthChanged: (() -> Unit)? = null

    private var wasExpanded = false
    private var collapseDelayRunnable: Runnable? = null

    private val tempRect = Rect()
    private val insetsListener = ViewTreeObserver.OnComputeInternalInsetsListener { inoutInfo ->
        inoutInfo.setTouchableInsets(ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_REGION)
        tempRect.setEmpty()
        computeTouchableRegion(tempRect)
        inoutInfo.touchableRegion.set(tempRect)
    }

    private fun computeTouchableRegion(outRect: Rect) {
        if (visibility != View.VISIBLE || alpha <= 0f) {
            outRect.setEmpty()
            return
        }
        val density = context.resources.displayMetrics.density
        val totalWidth = if (width > 0) width else context.resources.displayMetrics.widthPixels
        val centerX = totalWidth / 2
        val config = ConfigManager.config.value

        if (DynamicIslandManager.isExpanded.value) {
            val cardWidthPx = (362f * density).toInt()
            val activeActivity = resolveActiveActivity()
            val cardHeightDp = DynamicIslandManager.getExpandedCardHeightDp(
                activeActivity,
                DynamicIslandManager.transientBanner.value.type,
                DynamicIslandManager.isClipboardCardActive.value
            )
            val notchCenterYDp = 22.5f + config.yOffsetDp
            val closedTopYDp = (notchCenterYDp - (config.pillHeightDp / 2f)).coerceAtLeast(0f)
            val expandedTopYDp = maxOf(closedTopYDp, notchCenterYDp + (config.pillHeightDp / 2f) + 6f)
            val cardHeightPx = ((expandedTopYDp + cardHeightDp + 60f) * density).toInt()
            val left = (centerX - cardWidthPx / 2).coerceAtLeast(0)
            val right = (centerX + cardWidthPx / 2).coerceAtMost(totalWidth)
            outRect.set(left, 0, right, cardHeightPx)
        } else {
            val pillWidthDp = getEstimatedPillWidthDp(IslandActivityType.NONE)
            val pillWidthPx = ((pillWidthDp + 24f) * density).toInt()
            val pillHeightPx = ((config.pillHeightDp + maxOf(0f, config.yOffsetDp) + 24f) * density).toInt()
            val centerX = (totalWidth / 2) + ((config.xOffsetDp) * density).toInt()
            val left = (centerX - pillWidthPx / 2).coerceAtLeast(0)
            val right = (centerX + pillWidthPx / 2).coerceAtMost(totalWidth)
            outRect.set(left, 0, right, pillHeightPx)
        }
    }

    fun resolveActiveActivity(): ComposeActivityType {
        val banner = DynamicIslandManager.transientBanner.value
        val hotspot = DynamicIslandManager.hotspotState.value
        val download = DynamicIslandManager.downloadState.value
        val privacy = DynamicIslandManager.privacyState.value
        val call = DynamicIslandManager.callState.value
        val nav = DynamicIslandManager.navigationState.value
        val timer = DynamicIslandManager.timerState.value
        val cast = DynamicIslandManager.castState.value
        val torch = DynamicIslandManager.torchState.value
        val screenRecord = DynamicIslandManager.screenRecordState.value
        val media = DynamicIslandManager.mediaState.value

        return when {
            banner.type != TransientBannerType.NONE -> ComposeActivityType.TRANSIENT_BANNER
            call.isActive -> ComposeActivityType.CALL
            nav.isActive -> ComposeActivityType.NAVIGATION
            timer.isActive -> ComposeActivityType.TIMER
            cast.isCasting -> ComposeActivityType.CAST
            privacy.hasAnyPrivacyActive -> ComposeActivityType.PRIVACY
            torch.isOn -> ComposeActivityType.TORCH
            screenRecord.isRecording -> ComposeActivityType.SCREEN_RECORD
            download.isDownloading || download.isCompleted || download.isFailed || download.isCancelled -> ComposeActivityType.DOWNLOAD
            hotspot.isEnabled -> ComposeActivityType.HOTSPOT
            media.hasActiveMedia -> ComposeActivityType.MEDIA
            else -> ComposeActivityType.NONE
        }
    }

    init {
        ConfigManager.init(context)
        DynamicIslandManager.init(context)

        val composeView = ComposeView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setContent {
                DynamicIslandLabTheme {
                    val mediaState by DynamicIslandManager.mediaState.collectAsState()
                    val batteryState by DynamicIslandManager.batteryState.collectAsState()
                    val isExpanded by DynamicIslandManager.isExpanded.collectAsState()
                    val liveConfig by ConfigManager.config.collectAsState()

                    DynamicIsland(
                        mediaState = mediaState,
                        batteryState = batteryState,
                        config = liveConfig,
                        isExpanded = isExpanded,
                        onToggleExpand = {
                            val next = !DynamicIslandManager.isExpanded.value
                            DynamicIslandManager.isExpanded.value = next
                            onToggleExpand?.invoke()
                        },
                        onPlayPause = { onPlayPauseClicked?.invoke() },
                        onNext = { onNextClicked?.invoke() },
                        onPrevious = { onPrevClicked?.invoke() },
                        onOpenMusicApp = { onOpenMediaAppClicked?.invoke() },
                        onSeek = { posMs -> onSeekTo?.invoke(posMs) },
                        onToggleTorch = { onToggleTorchClicked?.invoke() },
                        onStopRecording = { onRecordStopClicked?.invoke() },
                        onEndCall = { onEndCallClicked?.invoke() },
                        onToggleHotspot = { onToggleHotspotClicked?.invoke() },
                        onDisconnectCast = { onDisconnectCastClicked?.invoke() },
                        onCancelDownload = {
                            val ds = DynamicIslandManager.downloadState.value
                            if (ds.isCompleted || ds.isFailed || ds.isCancelled) {
                                DynamicIslandManager.dismissDownload()
                            } else {
                                onCancelDownloadClicked?.invoke()
                            }
                        },
                        onCollapseCompleted = {
                            collapseDelayRunnable?.let { mainHandler.removeCallbacks(it) }
                            onCollapseCompleted?.invoke()
                        }
                    )
                }
            }
        }

        addView(composeView)

        // Observe expansion state to notify controller when spring collapse completes
        scope.launch {
            DynamicIslandManager.isExpanded.collect { expanded ->
                collapseDelayRunnable?.let { mainHandler.removeCallbacks(it) }
                if (!expanded && wasExpanded) {
                    collapseDelayRunnable = Runnable {
                        if (!DynamicIslandManager.isExpanded.value) {
                            onCollapseCompleted?.invoke()
                        }
                    }
                    mainHandler.postDelayed(collapseDelayRunnable!!, 600L)
                }
                wasExpanded = expanded
                post {
                    requestLayout()
                }
                onPillWidthChanged?.invoke()
            }
        }

        scope.launch {
            DynamicIslandManager.transientBanner.collect {
                onPillWidthChanged?.invoke()
            }
        }

        scope.launch {
            DynamicIslandManager.downloadState.collect {
                onPillWidthChanged?.invoke()
            }
        }

        scope.launch {
            DynamicIslandManager.mediaState.collect {
                onPillWidthChanged?.invoke()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnComputeInternalInsetsListener(insetsListener)
        ComposeInitializer.onAttachedToWindow(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewTreeObserver.removeOnComputeInternalInsetsListener(insetsListener)
        collapseDelayRunnable?.let { mainHandler.removeCallbacks(it) }
        ComposeInitializer.onDetachedFromWindow(this)
    }

    fun setAnimationConfig(bounceFactor: Float, durationMs: Long) {
        val current = ConfigManager.config.value
        ConfigManager.update(current.copy(springDamping = bounceFactor))
    }

    fun setIconStyle(style: Int) {
        val current = ConfigManager.config.value
        val newStyle = if (style == 1) IconPackStyle.HYPEROS else IconPackStyle.SYSTEM
        ConfigManager.update(current.copy(iconPackStyle = newStyle))
    }

    fun setXOffset(xOffsetDp: Float) {
        val current = ConfigManager.config.value
        ConfigManager.update(current.copy(xOffsetDp = xOffsetDp))
    }

    fun setYOffset(yOffsetDp: Float) {
        val current = ConfigManager.config.value
        ConfigManager.update(current.copy(yOffsetDp = yOffsetDp))
    }

    fun setBasePillWidth(widthDp: Float) {
        val current = ConfigManager.config.value
        ConfigManager.update(current.copy(pillWidthDp = widthDp))
    }

    fun setPillHeight(heightDp: Float) {
        val current = ConfigManager.config.value
        ConfigManager.update(current.copy(pillHeightDp = heightDp))
    }

    fun setNotchMargin(marginDp: Float) {
        val current = ConfigManager.config.value
        ConfigManager.update(current.copy(notchMarginDp = marginDp))
    }

    fun setExpanded(expanded: Boolean, animate: Boolean = true) {
        DynamicIslandManager.isExpanded.value = expanded
    }

    fun showPillAfterCollapse() {
        DynamicIslandManager.isExpanded.value = false
    }

    fun getExpandedHeightDp(type: IslandActivityType): Float {
        val composeType = when (type) {
            IslandActivityType.CALL -> ComposeActivityType.CALL
            IslandActivityType.MEDIA -> ComposeActivityType.MEDIA
            IslandActivityType.TIMER -> ComposeActivityType.TIMER
            IslandActivityType.NAVIGATION -> ComposeActivityType.NAVIGATION
            IslandActivityType.DOWNLOAD -> ComposeActivityType.DOWNLOAD
            IslandActivityType.CAST -> ComposeActivityType.CAST
            IslandActivityType.HOTSPOT -> ComposeActivityType.HOTSPOT
            IslandActivityType.SCREEN_RECORD -> ComposeActivityType.SCREEN_RECORD
            IslandActivityType.TORCH -> ComposeActivityType.TORCH
            IslandActivityType.PRIVACY -> ComposeActivityType.PRIVACY
            IslandActivityType.CLIPBOARD,
            IslandActivityType.TRANSIENT_BANNER -> ComposeActivityType.TRANSIENT_BANNER
            else -> ComposeActivityType.NONE
        }
        return DynamicIslandManager.getExpandedCardHeightDp(
            composeType,
            DynamicIslandManager.transientBanner.value.type,
            DynamicIslandManager.isClipboardCardActive.value
        )
    }

    fun getEstimatedPillWidthDp(type: IslandActivityType): Float {
        val banner = DynamicIslandManager.transientBanner.value
        val hotspot = DynamicIslandManager.hotspotState.value
        val download = DynamicIslandManager.downloadState.value
        val config = ConfigManager.config.value

        return when {
            banner.type != TransientBannerType.NONE -> banner.targetWidthDp
            download.isDownloading || download.isCompleted || download.isCancelled || download.isFailed -> 185f
            hotspot.isEnabled -> if (hotspot.showIntroBanner) 310f else 180f
            DynamicIslandManager.privacyState.value.hasAnyPrivacyActive -> 210f
            DynamicIslandManager.callState.value.isActive -> 185f
            DynamicIslandManager.navigationState.value.isActive -> 220f
            DynamicIslandManager.timerState.value.isActive -> 205f
            DynamicIslandManager.castState.value.isCasting -> 215f
            DynamicIslandManager.torchState.value.isOn -> 180f
            DynamicIslandManager.screenRecordState.value.isRecording -> 180f
            else -> config.pillWidthDp
        }
    }

    fun showMedia(media: IslandMediaState) {
        val pkg = media.packageName
        var art = media.albumArt
        if (art == null && pkg.isNotBlank()) {
            try {
                val d = context.packageManager.getApplicationIcon(pkg)
                if (d is android.graphics.drawable.BitmapDrawable && d.bitmap != null) {
                    art = d.bitmap
                } else {
                    val w = d.intrinsicWidth.takeIf { it > 0 } ?: 128
                    val h = d.intrinsicHeight.takeIf { it > 0 } ?: 128
                    val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(b)
                    d.setBounds(0, 0, w, h)
                    d.draw(canvas)
                    art = b
                }
            } catch (ignored: Exception) {}
        }
        DynamicIslandManager.updateMedia(
            title = media.title,
            artist = media.artist,
            albumArt = art,
            isPlaying = media.isPlaying,
            positionMs = media.positionMs,
            durationMs = media.durationMs,
            lastUpdateTimeMs = media.lastUpdateTimeMs,
            packageName = pkg
        )
    }

    fun showCall(call: IslandCallState) {
        DynamicIslandManager.updateCall(
            isActive = call.isActive,
            contactName = call.contactName,
            startTimeMs = call.startTimeMs,
            hangupIntent = call.intent
        )
    }

    fun showTimer(timer: IslandTimerState) {
        DynamicIslandManager.updateTimer(
            isActive = timer.isActive,
            label = timer.label,
            remainingTimeSeconds = timer.remainingSeconds,
            isPaused = timer.isPaused,
            cancelIntent = timer.resetIntent,
            pauseResumeIntent = timer.pauseResumeIntent
        )
    }

    fun showCharging(charging: IslandChargingState) {
        DynamicIslandManager.updateBattery(
            isCharging = charging.isCharging,
            level = charging.level,
            watts = charging.watts,
            isNewConnection = charging.isNewConnection
        )
    }

    fun showRecording(record: IslandRecordState) {
        DynamicIslandManager.updateScreenRecording(
            isRecording = record.isRecording,
            startTimeMs = record.startTimeMs,
            stopIntent = record.stopIntent
        )
    }

    fun showNavigation(nav: IslandNavigationState) {
        DynamicIslandManager.updateNavigation(
            isActive = nav.isActive,
            maneuver = nav.maneuver,
            instruction = nav.instructionText,
            distanceText = nav.distanceText,
            etaText = nav.etaText,
            openIntent = nav.openIntent
        )
    }

    fun showDownload(download: IslandDownloadState) {
        DynamicIslandManager.updateDownload(
            isDownloading = download.isDownloading,
            fileName = download.fileName,
            progress = download.progress,
            speedText = download.speedText,
            totalSizeText = download.totalSizeText,
            isCompleted = download.isCompleted,
            isFailed = download.isFailed,
            isCancelled = download.isCancelled,
            cancelIntent = download.cancelIntent
        )
    }

    fun showTorch(torch: IslandTorchState) {
        DynamicIslandManager.updateTorch(
            isOn = torch.isOn
        )
    }

    fun showHotspot(hotspot: IslandHotspotState) {
        DynamicIslandManager.updateHotspot(
            isEnabled = hotspot.isEnabled,
            connectedDevices = hotspot.clientCount
        )
    }

    fun showCast(cast: IslandCastState) {
        DynamicIslandManager.updateCast(
            isCasting = cast.isCasting,
            deviceName = cast.deviceName,
            castType = cast.castType
        )
    }

    fun showPrivacy(privacy: IslandPrivacyState) {
        DynamicIslandManager.updatePrivacy(
            isCameraInUse = privacy.isCameraInUse,
            isMicInUse = privacy.isMicInUse,
            isLocationInUse = privacy.isLocationInUse,
            appName = privacy.appName
        )
    }

    fun showClipboard(clipboard: IslandClipboardState) {
        DynamicIslandManager.onClipboardCopied(clipboard.copiedText)
    }

    fun dismissClipboard() {
        DynamicIslandManager.dismissTransientBanner()
    }

    fun showBanner(banner: IslandBannerState) {
        if (!banner.isActive) return
        val bannerType = when {
            banner.title.contains("zona", ignoreCase = true) || banner.title.contains("hotspot", ignoreCase = true) -> TransientBannerType.HOTSPOT
            banner.title.contains("linterna", ignoreCase = true) || banner.title.contains("torch", ignoreCase = true) -> TransientBannerType.TORCH
            banner.title.contains("cargador", ignoreCase = true) || banner.title.contains("charger", ignoreCase = true) -> TransientBannerType.CHARGING
            banner.title.contains("batería", ignoreCase = true) || banner.title.contains("battery", ignoreCase = true) -> TransientBannerType.BATTERY_LOW
            else -> TransientBannerType.NONE
        }
        val targetWidth = when (bannerType) {
            TransientBannerType.HOTSPOT -> 255f
            TransientBannerType.CHARGING -> 250f
            TransientBannerType.BATTERY_LOW -> 250f
            TransientBannerType.TORCH -> 230f
            else -> 235f
        }
        DynamicIslandManager.showTransientBanner(
            type = bannerType,
            title = banner.title,
            subtitle = banner.subtitle,
            tintColor = banner.tintColor.toLong() and 0xFFFFFFFFL,
            targetWidthDp = targetWidth,
            durationMs = 3500L
        )
    }

    fun hidePill() {
        DynamicIslandManager.collapseIsland()
    }
}
