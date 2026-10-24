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
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.content.res.ColorStateList
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.android.systemui.res.R

/**
 * Independent Dynamic Island View .
 * Manages both the collapsed punch-hole pill and the expanded multi-activity card.
 */
class DynamicIslandView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    // Subviews
    private val pillView: View
    private val cardView: View

    // Dynamic Geometry & Notch clearance configuration
    private var basePillWidthDp: Float = 110f
    private var pillHeightDp: Float = 34f
    private var notchMarginDp: Float = 5f

    // Pill elements
    private val pillAlbumArt: ImageView
    private val pillIcon: ImageView
    private val pillWaveform: WaveformView
    private val pillTrailingIcon: ImageView
    private val pillText: TextView
    private val pillNotchCutoutSpace: View

    // Card Media elements
    private val cardMediaSection: LinearLayout
    private val cardMediaArt: ImageView
    private val cardMediaTitle: TextView
    private val cardMediaArtist: TextView
    private val cardMediaWaveform: WaveformView
    private val cardMediaTimeCurrent: TextView
    private val cardMediaProgress: ProgressBar
    private val cardMediaTimeTotal: TextView
    private val cardMediaBtnPrev: ImageView
    private val cardMediaBtnPlayPause: ImageView
    private val cardMediaBtnNext: ImageView

    // Card Call elements
    private val cardCallSection: LinearLayout
    private val cardCallName: TextView
    private val cardCallTimer: TextView
    private val cardCallBtnMute: ImageView
    private val cardCallBtnHangup: ImageView

    // Card Timer elements
    private val cardTimerSection: LinearLayout
    private val cardTimerLabel: TextView
    private val cardTimerDigits: TextView
    private val cardTimerBtnReset: ImageView
    private val cardTimerBtnPause: ImageView

    // Card Charging elements
    private val cardChargingSection: LinearLayout
    private val cardChargingTitle: TextView
    private val cardChargingBadge: TextView
    private val cardChargingPct: TextView
    private val cardChargingProgress: ProgressBar

    // Card Record elements
    private val cardRecordSection: LinearLayout
    private val cardRecordTimer: TextView
    private val cardRecordBtnStop: ImageView

    // Card Banner elements
    private val cardBannerSection: LinearLayout
    private val cardBannerIcon: ImageView
    private val cardBannerTitle: TextView
    private val cardBannerSubtitle: TextView
    private val cardBannerBadge: TextView

    // Card Navigation elements
    private val cardNavSection: LinearLayout
    private val cardNavIcon: ImageView
    private val cardNavDistance: TextView
    private val cardNavInstruction: TextView
    private val cardNavEta: TextView

    // Card Download elements
    private val cardDownloadSection: LinearLayout
    private val cardDownloadTitle: TextView
    private val cardDownloadSpeed: TextView
    private val cardDownloadProgress: ProgressBar
    private val cardDownloadTotal: TextView
    private val cardDownloadBtnCancel: ImageView

    // Card Torch elements
    private val cardTorchSection: LinearLayout
    private val cardTorchBtnToggle: ImageView

    // Card Hotspot elements
    private val cardHotspotSection: LinearLayout
    private val cardHotspotDevices: TextView
    private val cardHotspotBtnToggle: ImageView

    // Card Cast elements
    private val cardCastSection: LinearLayout
    private val cardCastDevice: TextView
    private val cardCastBtnDisconnect: ImageView

    // Card Privacy elements
    private val cardPrivacySection: LinearLayout
    private val cardPrivacyIconCam: ImageView
    private val cardPrivacyIconMic: ImageView
    private val cardPrivacyIconLoc: ImageView
    private val cardPrivacyApp: TextView

    // Card Clipboard elements
    private val cardClipboardSection: LinearLayout
    private val cardClipboardText: TextView

    // State
    private var isExpanded = false
    private var currentActivity = IslandActivityType.NONE

    // Callbacks
    var onToggleExpand: (() -> Unit)? = null
    var onPlayPauseClicked: (() -> Unit)? = null
    var onPrevClicked: (() -> Unit)? = null
    var onNextClicked: (() -> Unit)? = null
    var onEndCallClicked: (() -> Unit)? = null
    var onMuteToggled: (() -> Unit)? = null
    var onTimerPauseClicked: (() -> Unit)? = null
    var onTimerResetClicked: (() -> Unit)? = null
    var onRecordStopClicked: (() -> Unit)? = null
    var onOpenMediaAppClicked: (() -> Unit)? = null
    var onToggleTorchClicked: (() -> Unit)? = null
    var onToggleHotspotClicked: (() -> Unit)? = null
    var onDisconnectCastClicked: (() -> Unit)? = null
    var onCancelDownloadClicked: (() -> Unit)? = null
    var onOpenNavigationClicked: (() -> Unit)? = null

    init {
        val inflater = LayoutInflater.from(context)
        pillView = inflater.inflate(R.layout.dynamic_island_pill, this, false)
        cardView = inflater.inflate(R.layout.dynamic_island_card, this, false)

        addView(pillView)
        addView(cardView)

        // Find Pill views
        pillAlbumArt = pillView.findViewById(R.id.pill_album_art)
        pillIcon = pillView.findViewById(R.id.pill_icon)
        pillWaveform = pillView.findViewById(R.id.pill_waveform)
        pillTrailingIcon = pillView.findViewById(R.id.pill_trailing_icon)
        pillText = pillView.findViewById(R.id.pill_text)
        pillNotchCutoutSpace = pillView.findViewById(R.id.pill_notch_cutout_space)

        // Find Card Media views
        cardMediaSection = cardView.findViewById(R.id.card_media_section)
        cardMediaArt = cardView.findViewById(R.id.card_media_art)
        cardMediaTitle = cardView.findViewById(R.id.card_media_title)
        cardMediaArtist = cardView.findViewById(R.id.card_media_artist)
        cardMediaWaveform = cardView.findViewById(R.id.card_media_waveform)
        cardMediaTimeCurrent = cardView.findViewById(R.id.card_media_time_current)
        cardMediaProgress = cardView.findViewById(R.id.card_media_progress)
        cardMediaTimeTotal = cardView.findViewById(R.id.card_media_time_total)
        cardMediaBtnPrev = cardView.findViewById(R.id.card_media_btn_prev)
        cardMediaBtnPlayPause = cardView.findViewById(R.id.card_media_btn_play_pause)
        cardMediaBtnNext = cardView.findViewById(R.id.card_media_btn_next)

        // Find Card Call views
        cardCallSection = cardView.findViewById(R.id.card_call_section)
        cardCallName = cardView.findViewById(R.id.card_call_name)
        cardCallTimer = cardView.findViewById(R.id.card_call_timer)
        cardCallBtnMute = cardView.findViewById(R.id.card_call_btn_mute)
        cardCallBtnHangup = cardView.findViewById(R.id.card_call_btn_hangup)

        // Find Card Timer views
        cardTimerSection = cardView.findViewById(R.id.card_timer_section)
        cardTimerLabel = cardView.findViewById(R.id.card_timer_label)
        cardTimerDigits = cardView.findViewById(R.id.card_timer_digits)
        cardTimerBtnReset = cardView.findViewById(R.id.card_timer_btn_reset)
        cardTimerBtnPause = cardView.findViewById(R.id.card_timer_btn_pause)

        // Find Card Charging views
        cardChargingSection = cardView.findViewById(R.id.card_charging_section)
        cardChargingTitle = cardView.findViewById(R.id.card_charging_title)
        cardChargingBadge = cardView.findViewById(R.id.card_charging_badge)
        cardChargingPct = cardView.findViewById(R.id.card_charging_pct)
        cardChargingProgress = cardView.findViewById(R.id.card_charging_progress)

        // Find Card Record views
        cardRecordSection = cardView.findViewById(R.id.card_record_section)
        cardRecordTimer = cardView.findViewById(R.id.card_record_timer)
        cardRecordBtnStop = cardView.findViewById(R.id.card_record_btn_stop)

        // Find Card Banner views
        cardBannerSection = cardView.findViewById(R.id.card_banner_section)
        cardBannerIcon = cardView.findViewById(R.id.card_banner_icon)
        cardBannerTitle = cardView.findViewById(R.id.card_banner_title)
        cardBannerSubtitle = cardView.findViewById(R.id.card_banner_subtitle)
        cardBannerBadge = cardView.findViewById(R.id.card_banner_badge)

        // Find Card Navigation views
        cardNavSection = cardView.findViewById(R.id.card_nav_section)
        cardNavIcon = cardView.findViewById(R.id.card_nav_icon)
        cardNavDistance = cardView.findViewById(R.id.card_nav_distance)
        cardNavInstruction = cardView.findViewById(R.id.card_nav_instruction)
        cardNavEta = cardView.findViewById(R.id.card_nav_eta)

        // Find Card Download views
        cardDownloadSection = cardView.findViewById(R.id.card_download_section)
        cardDownloadTitle = cardView.findViewById(R.id.card_download_title)
        cardDownloadSpeed = cardView.findViewById(R.id.card_download_speed)
        cardDownloadProgress = cardView.findViewById(R.id.card_download_progress)
        cardDownloadTotal = cardView.findViewById(R.id.card_download_total)
        cardDownloadBtnCancel = cardView.findViewById(R.id.card_download_btn_cancel)

        // Find Card Torch views
        cardTorchSection = cardView.findViewById(R.id.card_torch_section)
        cardTorchBtnToggle = cardView.findViewById(R.id.card_torch_btn_toggle)

        // Find Card Hotspot views
        cardHotspotSection = cardView.findViewById(R.id.card_hotspot_section)
        cardHotspotDevices = cardView.findViewById(R.id.card_hotspot_devices)
        cardHotspotBtnToggle = cardView.findViewById(R.id.card_hotspot_btn_toggle)

        // Find Card Cast views
        cardCastSection = cardView.findViewById(R.id.card_cast_section)
        cardCastDevice = cardView.findViewById(R.id.card_cast_device)
        cardCastBtnDisconnect = cardView.findViewById(R.id.card_cast_btn_disconnect)

        // Find Card Privacy views
        cardPrivacySection = cardView.findViewById(R.id.card_privacy_section)
        cardPrivacyIconCam = cardView.findViewById(R.id.card_privacy_icon_cam)
        cardPrivacyIconMic = cardView.findViewById(R.id.card_privacy_icon_mic)
        cardPrivacyIconLoc = cardView.findViewById(R.id.card_privacy_icon_location)
        cardPrivacyApp = cardView.findViewById(R.id.card_privacy_app)

        // Find Card Clipboard views
        cardClipboardSection = cardView.findViewById(R.id.card_clipboard_section)
        cardClipboardText = cardView.findViewById(R.id.card_clipboard_text)

        applyBackgroundStyles()
        setupListeners()

        // Initial state: Pill visible, Card hidden
        cardView.visibility = View.GONE
        cardView.alpha = 0f
    }

    private fun applyBackgroundStyles(accentColor: Int? = null) {
        val strokeColor = if (accentColor != null) {
            Color.argb(70, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        } else {
            Color.argb(45, 255, 255, 255)
        }

        // Pill background
        val pillShape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = (pillHeightDp * density) / 2f
            setColor(Color.parseColor("#0D0D11"))
            setStroke((1.2f * density).toInt(), strokeColor)
        }
        pillView.background = pillShape

        // Card background
        val cardShape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 28f * density
            setColor(Color.parseColor("#121217"))
            setStroke((1.2f * density).toInt(), strokeColor)
        }
        cardView.background = cardShape
    }

    private fun setupListeners() {
        pillView.setOnClickListener {
            onToggleExpand?.invoke()
        }

        cardMediaArt.setOnClickListener { onOpenMediaAppClicked?.invoke() }
        cardMediaTitle.setOnClickListener { onOpenMediaAppClicked?.invoke() }
        cardMediaBtnPlayPause.setOnClickListener { onPlayPauseClicked?.invoke() }
        cardMediaBtnPrev.setOnClickListener { onPrevClicked?.invoke() }
        cardMediaBtnNext.setOnClickListener { onNextClicked?.invoke() }

        cardCallBtnHangup.setOnClickListener { onEndCallClicked?.invoke() }
        cardCallBtnMute.setOnClickListener { onMuteToggled?.invoke() }

        cardTimerBtnPause.setOnClickListener { onTimerPauseClicked?.invoke() }
        cardTimerBtnReset.setOnClickListener { onTimerResetClicked?.invoke() }

        cardRecordBtnStop.setOnClickListener { onRecordStopClicked?.invoke() }
        cardTorchBtnToggle.setOnClickListener { onToggleTorchClicked?.invoke() }
        cardHotspotBtnToggle.setOnClickListener { onToggleHotspotClicked?.invoke() }
        cardCastBtnDisconnect.setOnClickListener { onDisconnectCastClicked?.invoke() }
        cardDownloadBtnCancel.setOnClickListener { onCancelDownloadClicked?.invoke() }
        cardNavSection.setOnClickListener { onOpenNavigationClicked?.invoke() }
    }

    private fun hideAllCardSections() {
        cardMediaSection.visibility = View.GONE
        cardCallSection.visibility = View.GONE
        cardTimerSection.visibility = View.GONE
        cardChargingSection.visibility = View.GONE
        cardRecordSection.visibility = View.GONE
        cardBannerSection.visibility = View.GONE
        cardNavSection.visibility = View.GONE
        cardDownloadSection.visibility = View.GONE
        cardTorchSection.visibility = View.GONE
        cardHotspotSection.visibility = View.GONE
        cardCastSection.visibility = View.GONE
        cardPrivacySection.visibility = View.GONE
        cardClipboardSection.visibility = View.GONE
    }

    fun getExpandedHeightDp(type: IslandActivityType): Float {
        return when (type) {
            IslandActivityType.MEDIA -> 175f
            IslandActivityType.CALL -> 148f
            IslandActivityType.TIMER -> 144f
            IslandActivityType.CHARGING -> 140f
            IslandActivityType.SCREEN_RECORD -> 134f
            IslandActivityType.NAVIGATION -> 138f
            IslandActivityType.DOWNLOAD -> 155f
            IslandActivityType.TORCH -> 134f
            IslandActivityType.HOTSPOT -> 138f
            IslandActivityType.CAST -> 134f
            IslandActivityType.PRIVACY -> 140f
            IslandActivityType.CLIPBOARD -> 220f
            IslandActivityType.TRANSIENT_BANNER -> 146f
            else -> 140f
        }
    }

    fun setExpanded(expanded: Boolean, animate: Boolean = true) {
        if (isExpanded == expanded) return
        isExpanded = expanded

        if (!animate) {
            if (expanded) {
                pillView.visibility = View.GONE
                cardView.visibility = View.VISIBLE
                cardView.alpha = 1f
                cardView.scaleX = 1f
                cardView.scaleY = 1f
            } else {
                cardView.visibility = View.GONE
                pillView.visibility = View.VISIBLE
                pillView.alpha = 1f
            }
            return
        }

        if (expanded) {
            val fadeOutDuration = (expandDurationMs * 0.4f).toLong().coerceAtLeast(80L)
            pillView.animate().cancel()
            pillView.animate().alpha(0f).setDuration(fadeOutDuration).withEndAction {
                pillView.visibility = View.GONE
            }.start()

            cardView.visibility = View.VISIBLE
            cardView.alpha = 0f
            cardView.scaleX = 0.72f
            cardView.scaleY = 0.72f
            cardView.translationY = -14f * density

            val interpolator = if (expandBounceFactor > 0.05f) {
                OvershootInterpolator(expandBounceFactor)
            } else {
                DecelerateInterpolator(1.5f)
            }

            cardView.animate().cancel()
            cardView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(expandDurationMs)
                .setInterpolator(interpolator)
                .start()
        } else {
            val collapseDuration = (expandDurationMs * 0.75f).toLong().coerceAtLeast(140L)
            cardView.animate().cancel()
            cardView.animate()
                .alpha(0f)
                .scaleX(0.78f)
                .scaleY(0.78f)
                .translationY(-10f * density)
                .setDuration(collapseDuration)
                .withEndAction {
                    cardView.visibility = View.GONE
                    pillView.visibility = View.VISIBLE
                    pillView.alpha = 0f
                    pillView.scaleX = 0.85f
                    pillView.scaleY = 0.85f
                    val pillInterpolator = if (expandBounceFactor > 0.05f) {
                        OvershootInterpolator(expandBounceFactor * 0.9f)
                    } else {
                        DecelerateInterpolator(1.5f)
                    }
                    pillView.animate().cancel()
                    pillView.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(collapseDuration)
                        .setInterpolator(pillInterpolator)
                        .start()
                }.start()
        }
    }

    fun showMedia(media: IslandMediaState) {
        currentActivity = IslandActivityType.MEDIA
        applyBackgroundStyles(media.dominantColor)

        // Update Pill
        pillTrailingIcon.visibility = View.GONE
        if (media.albumArt != null) {
            pillAlbumArt.setImageBitmap(media.albumArt)
            pillAlbumArt.visibility = View.VISIBLE
            pillIcon.visibility = View.GONE
        } else {
            pillAlbumArt.visibility = View.GONE
            pillIcon.setImageResource(if (iconStyle == 0) R.drawable.ic_hyper_music else com.android.internal.R.drawable.ic_audio_media)
            pillIcon.visibility = View.VISIBLE
        }

        pillWaveform.visibility = View.VISIBLE
        pillWaveform.setPlaying(media.isPlaying)
        if (media.dominantColor != null) {
            pillWaveform.setBarColor(media.dominantColor)
        }
        pillText.visibility = View.GONE

        // Update Card Media
        cardMediaSection.visibility = View.VISIBLE
        cardCallSection.visibility = View.GONE
        cardTimerSection.visibility = View.GONE
        cardChargingSection.visibility = View.GONE
        cardRecordSection.visibility = View.GONE
        cardBannerSection.visibility = View.GONE

        if (media.albumArt != null) {
            cardMediaArt.setImageBitmap(media.albumArt)
            cardMediaArt.visibility = View.VISIBLE
        } else {
            cardMediaArt.setImageResource(R.drawable.ic_hyper_music)
        }

        cardMediaTitle.text = if (media.title.isNotBlank()) media.title else context.getString(R.string.island_now_playing)
        cardMediaTitle.isSelected = true
        cardMediaArtist.text = if (media.artist.isNotBlank()) media.artist else context.getString(R.string.island_unknown_artist)

        cardMediaWaveform.setPlaying(media.isPlaying)
        if (media.dominantColor != null) {
            cardMediaWaveform.setBarColor(media.dominantColor)
        }

        val posSec = media.positionMs / 1000
        val durSec = media.durationMs / 1000
        cardMediaTimeCurrent.text = String.format("%d:%02d", posSec / 60, posSec % 60)
        cardMediaTimeTotal.text = if (durSec > 0) String.format("%d:%02d", durSec / 60, durSec % 60) else "--:--"

        if (media.durationMs > 0) {
            cardMediaProgress.progress = ((media.positionMs.toFloat() / media.durationMs) * 1000).toInt()
        }

        cardMediaBtnPlayPause.setImageResource(
            if (media.isPlaying) R.drawable.ic_music_island_pause else R.drawable.ic_music_island_play
        )
    }

    fun showCall(call: IslandCallState) {
        currentActivity = IslandActivityType.CALL
        applyBackgroundStyles(Color.parseColor("#2E7D32"))

        // Update Pill
        pillAlbumArt.visibility = View.GONE
        pillTrailingIcon.visibility = View.GONE
        pillIcon.setImageResource(R.drawable.ic_hyper_call)
        pillIcon.setColorFilter(Color.parseColor("#81C784"))
        pillIcon.visibility = View.VISIBLE
        pillWaveform.visibility = View.GONE

        val elapsed = (SystemClock.elapsedRealtime() - call.startTimeMs) / 1000
        val timerStr = String.format("%02d:%02d", elapsed / 60, elapsed % 60)
        pillText.text = timerStr
        pillText.setTextColor(Color.parseColor("#81C784"))
        pillText.visibility = View.VISIBLE

        // Update Card Call
        cardMediaSection.visibility = View.GONE
        cardCallSection.visibility = View.VISIBLE
        cardTimerSection.visibility = View.GONE
        cardChargingSection.visibility = View.GONE
        cardRecordSection.visibility = View.GONE
        cardBannerSection.visibility = View.GONE

        cardCallName.text = if (call.contactName.isNotBlank()) call.contactName else context.getString(R.string.island_mode_sound)
        cardCallTimer.text = timerStr

        cardCallBtnMute.setColorFilter(if (call.isMuted) Color.parseColor("#FFD600") else Color.WHITE)
    }

    fun showTimer(timer: IslandTimerState) {
        currentActivity = IslandActivityType.TIMER
        applyBackgroundStyles(Color.parseColor("#F57C00"))

        // Update Pill
        pillAlbumArt.visibility = View.GONE
        pillTrailingIcon.visibility = View.GONE
        pillIcon.setImageResource(if (iconStyle == 0) R.drawable.ic_hyper_timer else R.drawable.ic_alarm)
        pillIcon.setColorFilter(Color.parseColor("#FFA726"))
        pillIcon.visibility = View.VISIBLE
        pillWaveform.visibility = View.GONE

        val remStr = String.format("%02d:%02d", timer.remainingSeconds / 60, timer.remainingSeconds % 60)
        pillText.text = remStr
        pillText.setTextColor(Color.parseColor("#FFA726"))
        pillText.visibility = View.VISIBLE

        // Update Card Timer
        cardMediaSection.visibility = View.GONE
        cardCallSection.visibility = View.GONE
        cardTimerSection.visibility = View.VISIBLE
        cardChargingSection.visibility = View.GONE
        cardRecordSection.visibility = View.GONE
        cardBannerSection.visibility = View.GONE

        cardTimerLabel.text = if (timer.label.isNotBlank()) timer.label else context.getString(R.string.island_timer)
        cardTimerDigits.text = remStr
        cardTimerBtnPause.setImageResource(
            if (timer.isPaused) R.drawable.ic_music_island_play else R.drawable.ic_music_island_pause
        )
    }

    fun showCharging(charging: IslandChargingState) {
        currentActivity = IslandActivityType.CHARGING
        val tint = if (charging.watts >= 65.0) Color.parseColor("#38BDF8") else Color.parseColor("#81C995")
        applyBackgroundStyles(tint)

        // Update Pill
        pillAlbumArt.visibility = View.GONE
        pillTrailingIcon.visibility = View.GONE
        pillIcon.setImageResource(
            if (charging.watts >= 65.0) R.drawable.ic_hyper_charging_super
            else if (charging.watts >= 30.0) R.drawable.ic_hyper_charging_turbo
            else R.drawable.ic_hyper_charging
        )
        pillIcon.setColorFilter(tint)
        pillIcon.visibility = View.VISIBLE
        pillWaveform.visibility = View.GONE

        pillText.text = "${charging.level}%"
        pillText.setTextColor(tint)
        pillText.visibility = View.VISIBLE

        // Update Card Charging
        cardMediaSection.visibility = View.GONE
        cardCallSection.visibility = View.GONE
        cardTimerSection.visibility = View.GONE
        cardChargingSection.visibility = View.VISIBLE
        cardRecordSection.visibility = View.GONE
        cardBannerSection.visibility = View.GONE

        cardChargingTitle.text = charging.title
        cardChargingBadge.text = if (charging.watts > 1.0) String.format("%.1fW Turbo", charging.watts) else context.getString(R.string.island_charging_battery)
        cardChargingBadge.setTextColor(tint)
        cardChargingPct.text = "${charging.level}%"
        cardChargingProgress.progress = charging.level
    }

    fun showRecording(record: IslandRecordState) {
        currentActivity = IslandActivityType.SCREEN_RECORD
        applyBackgroundStyles(Color.parseColor("#D32F2F"))

        // Update Pill - Indicator circle must be RED (#FF3B30), timer text in RED (#FF3B30)
        pillAlbumArt.visibility = View.GONE
        pillTrailingIcon.visibility = View.GONE
        pillIcon.setImageResource(R.drawable.ic_screenrecord)
        pillIcon.setColorFilter(Color.parseColor("#FF3B30"))
        pillIcon.visibility = View.VISIBLE
        pillWaveform.visibility = View.GONE

        val elapsed = (SystemClock.elapsedRealtime() - record.startTimeMs) / 1000
        val str = String.format("%02d:%02d", elapsed / 60, elapsed % 60)
        pillText.text = str
        pillText.setTextColor(Color.parseColor("#FF3B30"))
        pillText.visibility = View.VISIBLE

        // Update Card Recording
        cardMediaSection.visibility = View.GONE
        cardCallSection.visibility = View.GONE
        cardTimerSection.visibility = View.GONE
        cardChargingSection.visibility = View.GONE
        cardRecordSection.visibility = View.VISIBLE
        cardBannerSection.visibility = View.GONE

        cardRecordTimer.text = str
        cardRecordTimer.setTextColor(Color.parseColor("#FF3B30"))
        cardRecordBtnStop.setColorFilter(Color.parseColor("#FF3B30"))
    }

    fun showNavigation(nav: IslandNavigationState) {
        currentActivity = IslandActivityType.NAVIGATION
        applyBackgroundStyles(Color.parseColor("#0288D1"))

        // Update Pill
        pillAlbumArt.visibility = View.GONE
        pillTrailingIcon.visibility = View.GONE
        pillIcon.setImageResource(R.drawable.ic_hyper_navigation)
        pillIcon.setColorFilter(Color.parseColor("#38BDF8"))
        pillIcon.visibility = View.VISIBLE
        pillWaveform.visibility = View.GONE

        pillText.text = if (nav.distanceText.isNotBlank()) nav.distanceText else context.getString(R.string.island_nav_route)
        pillText.setTextColor(Color.parseColor("#38BDF8"))
        pillText.visibility = View.VISIBLE

        // Update Card
        hideAllCardSections()
        cardNavSection.visibility = View.VISIBLE

        cardNavDistance.text = if (nav.distanceText.isNotBlank()) nav.distanceText else context.getString(R.string.island_nav_route)
        cardNavInstruction.text = if (nav.instructionText.isNotBlank()) nav.instructionText else context.getString(R.string.island_nav_on_route)
        cardNavEta.text = if (nav.etaText.isNotBlank()) nav.etaText else "Google Maps"
    }

    fun showDownload(download: IslandDownloadState) {
        currentActivity = IslandActivityType.DOWNLOAD
        val accentColor = when {
            download.isCancelled || download.isFailed -> Color.parseColor("#D32F2F")
            download.isCompleted -> Color.parseColor("#1B5E20")
            else -> Color.parseColor("#0288D1")
        }
        applyBackgroundStyles(accentColor)

        pillAlbumArt.visibility = View.GONE
        pillWaveform.visibility = View.GONE
        pillTrailingIcon.visibility = View.GONE

        when {
            download.isCancelled -> {
                pillIcon.setImageResource(R.drawable.ic_close)
                pillIcon.setColorFilter(Color.parseColor("#FF3B30"))
                pillIcon.visibility = View.VISIBLE
                pillText.text = context.getString(R.string.island_cancelled)
                pillText.setTextColor(Color.parseColor("#FF3B30"))
                pillText.visibility = View.VISIBLE
            }
            download.isFailed -> {
                pillIcon.setImageResource(R.drawable.ic_close)
                pillIcon.setColorFilter(Color.parseColor("#FF3B30"))
                pillIcon.visibility = View.VISIBLE
                pillText.text = context.getString(R.string.island_failed)
                pillText.setTextColor(Color.parseColor("#FF3B30"))
                pillText.visibility = View.VISIBLE
            }
            download.isCompleted -> {
                pillIcon.setImageResource(R.drawable.ic_hyper_download)
                pillIcon.setColorFilter(Color.parseColor("#00E676"))
                pillIcon.visibility = View.VISIBLE
                pillText.text = "100%"
                pillText.setTextColor(Color.parseColor("#00E676"))
                pillText.visibility = View.VISIBLE
            }
            else -> {
                pillIcon.setImageResource(R.drawable.ic_hyper_download)
                pillIcon.setColorFilter(Color.parseColor("#38BDF8"))
                pillIcon.visibility = View.VISIBLE
                pillText.text = "${download.progress}%"
                pillText.setTextColor(Color.parseColor("#38BDF8"))
                pillText.visibility = View.VISIBLE
            }
        }

        // Update Card
        hideAllCardSections()
        cardDownloadSection.visibility = View.VISIBLE

        cardDownloadTitle.text = if (download.fileName.isNotBlank()) download.fileName else context.getString(R.string.island_downloading)

        when {
            download.isCancelled -> {
                cardDownloadSpeed.text = context.getString(R.string.island_download_cancelled)
                cardDownloadSpeed.setTextColor(Color.parseColor("#FF3B30"))
            }
            download.isFailed -> {
                cardDownloadSpeed.text = context.getString(R.string.island_download_failed_stopped)
                cardDownloadSpeed.setTextColor(Color.parseColor("#FF3B30"))
            }
            download.isCompleted -> {
                cardDownloadSpeed.text = "${context.getString(R.string.island_download_complete)} • 100%"
                cardDownloadSpeed.setTextColor(Color.parseColor("#00E676"))
            }
            else -> {
                val displaySpeed = if (download.speedText.isNotBlank()) {
                    download.speedText
                } else if (download.totalSizeText.isNotBlank()) {
                    download.totalSizeText
                } else {
                    ""
                }
                val statusText = if (displaySpeed.isNotBlank()) {
                    "${context.getString(R.string.island_downloading)} • $displaySpeed (${download.progress}%)"
                } else {
                    "${context.getString(R.string.island_downloading)} • ${download.progress}%"
                }
                cardDownloadSpeed.text = statusText
                cardDownloadSpeed.setTextColor(Color.parseColor("#38BDF8"))
            }
        }

        val progressColor = when {
            download.isCancelled || download.isFailed -> Color.parseColor("#FF3B30")
            download.isCompleted -> Color.parseColor("#00E676")
            else -> Color.parseColor("#38BDF8")
        }
        cardDownloadProgress.progress = if (download.isCompleted) 100 else download.progress
        cardDownloadProgress.progressTintList = ColorStateList.valueOf(progressColor)

        cardDownloadTotal.text = when {
            download.isCancelled -> context.getString(R.string.island_cancelled)
            download.isFailed -> context.getString(R.string.island_failed)
            download.isCompleted -> context.getString(R.string.island_completed)
            else -> if (download.totalSizeText.isNotBlank()) download.totalSizeText else context.getString(R.string.island_download)
        }

        if (download.isCancelled || download.isFailed) {
            cardDownloadBtnCancel.setImageResource(R.drawable.ic_close)
            cardDownloadBtnCancel.setColorFilter(Color.parseColor("#FF3B30"))
        } else if (download.isCompleted) {
            cardDownloadBtnCancel.setImageResource(R.drawable.ic_hyper_download)
            cardDownloadBtnCancel.setColorFilter(Color.parseColor("#00E676"))
        } else {
            cardDownloadBtnCancel.setImageResource(R.drawable.ic_close)
            cardDownloadBtnCancel.setColorFilter(Color.WHITE)
        }
    }

    fun showTorch(torch: IslandTorchState) {
        currentActivity = IslandActivityType.TORCH
        applyBackgroundStyles(Color.parseColor("#E65100"))

        // Update Pill
        pillAlbumArt.visibility = View.GONE
        pillTrailingIcon.visibility = View.GONE
        pillIcon.setImageResource(if (iconStyle == 0) R.drawable.ic_hyper_torch else R.drawable.ic_qs_flashlight)
        pillIcon.setColorFilter(Color.parseColor("#FFD600"))
        pillIcon.visibility = View.VISIBLE
        pillWaveform.visibility = View.GONE

        pillText.text = context.getString(R.string.island_torch_pill)
        pillText.setTextColor(Color.parseColor("#FFD600"))
        pillText.visibility = View.VISIBLE

        // Update Card
        hideAllCardSections()
        cardTorchSection.visibility = View.VISIBLE
    }

    fun showHotspot(hotspot: IslandHotspotState) {
        currentActivity = IslandActivityType.HOTSPOT
        applyBackgroundStyles(Color.parseColor("#BF360C"))

        // Update Pill
        pillAlbumArt.visibility = View.GONE
        pillWaveform.visibility = View.GONE
        pillIcon.setImageResource(if (iconStyle == 0) R.drawable.ic_hyper_hotspot else R.drawable.ic_hotspot)
        pillIcon.setColorFilter(Color.parseColor("#FF9100"))
        pillIcon.visibility = View.VISIBLE

        if (hotspot.showIntroBanner) {
            pillTrailingIcon.visibility = View.GONE
            pillText.text = context.getString(R.string.island_active)
            pillText.setTextColor(Color.parseColor("#FF9100"))
            pillText.visibility = View.VISIBLE
        } else {
            pillTrailingIcon.setImageResource(R.drawable.ic_island_link)
            pillTrailingIcon.setColorFilter(Color.parseColor("#FF9100"))
            pillTrailingIcon.visibility = View.VISIBLE
            // Strictly NO "dev." or "disp."! Only client count number!
            pillText.text = "${hotspot.clientCount}"
            pillText.setTextColor(Color.parseColor("#FF9100"))
            pillText.visibility = View.VISIBLE
        }

        // Update Card
        hideAllCardSections()
        cardHotspotSection.visibility = View.VISIBLE
        val devText = when (hotspot.clientCount) {
            0 -> context.getString(R.string.island_hotspot_devices_none)
            1 -> context.getString(R.string.island_hotspot_devices_one)
            else -> context.getString(R.string.island_hotspot_devices_many, hotspot.clientCount)
        }
        cardHotspotDevices.text = devText
    }

    fun showCast(cast: IslandCastState) {
        currentActivity = IslandActivityType.CAST
        applyBackgroundStyles(Color.parseColor("#01579B"))

        // Update Pill
        pillAlbumArt.visibility = View.GONE
        pillTrailingIcon.visibility = View.GONE
        pillIcon.setImageResource(if (iconStyle == 0) R.drawable.ic_hyper_cast else R.drawable.ic_cast)
        pillIcon.setColorFilter(Color.parseColor("#38BDF8"))
        pillIcon.visibility = View.VISIBLE
        pillWaveform.visibility = View.GONE

        pillText.text = context.getString(R.string.island_cast)
        pillText.setTextColor(Color.parseColor("#38BDF8"))
        pillText.visibility = View.VISIBLE

        // Update Card
        hideAllCardSections()
        cardCastSection.visibility = View.VISIBLE
        cardCastDevice.text = if (cast.deviceName.isNotBlank()) cast.deviceName else context.getString(R.string.island_wireless_display)
    }

    fun showPrivacy(privacy: IslandPrivacyState) {
        currentActivity = IslandActivityType.PRIVACY
        val sensorColor = when {
            privacy.isCameraInUse -> Color.parseColor("#00E676")
            privacy.isMicInUse -> Color.parseColor("#FF9100")
            else -> Color.parseColor("#0091EA")
        }
        val sensorName = when {
            privacy.isCameraInUse -> context.getString(R.string.island_camera)
            privacy.isMicInUse -> context.getString(R.string.island_microphone)
            else -> context.getString(R.string.island_location)
        }
        applyBackgroundStyles(sensorColor)

        // Update Pill
        pillAlbumArt.visibility = View.GONE
        pillWaveform.visibility = View.GONE
        pillTrailingIcon.visibility = View.GONE

        pillIcon.setImageResource(when {
            privacy.isCameraInUse -> R.drawable.ic_camera
            privacy.isMicInUse -> R.drawable.vd_mic
            else -> R.drawable.ic_location
        })
        pillIcon.setColorFilter(sensorColor)
        pillIcon.visibility = View.VISIBLE

        val displayLabel = privacy.appName.ifBlank { sensorName }
        pillText.text = displayLabel
        pillText.setTextColor(sensorColor)
        pillText.visibility = View.VISIBLE

        // Update Card
        hideAllCardSections()
        cardPrivacySection.visibility = View.VISIBLE

        cardPrivacyIconCam.visibility = if (privacy.isCameraInUse) View.VISIBLE else View.GONE
        cardPrivacyIconMic.visibility = if (privacy.isMicInUse) View.VISIBLE else View.GONE
        cardPrivacyIconLoc.visibility = if (privacy.isLocationInUse) View.VISIBLE else View.GONE
        cardPrivacyApp.text = context.getString(R.string.island_in_use_by, displayLabel)
        cardPrivacyApp.setTextColor(sensorColor)
    }

    fun showClipboard(clipboard: IslandClipboardState) {
        currentActivity = IslandActivityType.CLIPBOARD
        applyBackgroundStyles(Color.parseColor("#006064"))

        // Update Pill
        pillAlbumArt.visibility = View.GONE
        pillTrailingIcon.visibility = View.GONE
        pillIcon.setImageResource(R.drawable.ic_hyper_clipboard)
        pillIcon.setColorFilter(Color.parseColor("#00E5FF"))
        pillIcon.visibility = View.VISIBLE
        pillWaveform.visibility = View.GONE

        pillText.text = context.getString(R.string.island_clipboard_copied)
        pillText.setTextColor(Color.parseColor("#00E5FF"))
        pillText.visibility = View.VISIBLE

        // Update Card
        hideAllCardSections()
        cardClipboardSection.visibility = View.VISIBLE
        cardClipboardText.text = clipboard.copiedText
    }

    fun showBanner(banner: IslandBannerState) {
        currentActivity = IslandActivityType.TRANSIENT_BANNER
        applyBackgroundStyles(banner.tintColor)

        // Update Pill
        pillAlbumArt.visibility = View.GONE
        pillTrailingIcon.visibility = View.GONE
        pillIcon.setImageResource(banner.iconRes)
        pillIcon.setColorFilter(banner.tintColor)
        pillIcon.visibility = View.VISIBLE
        pillWaveform.visibility = View.GONE

        if (!banner.badge.isNullOrBlank()) {
            pillText.text = banner.badge
            pillText.setTextColor(banner.tintColor)
            pillText.visibility = View.VISIBLE
        } else {
            pillText.visibility = View.GONE
        }

        // Update Card Banner
        cardMediaSection.visibility = View.GONE
        cardCallSection.visibility = View.GONE
        cardTimerSection.visibility = View.GONE
        cardChargingSection.visibility = View.GONE
        cardRecordSection.visibility = View.GONE
        cardBannerSection.visibility = View.VISIBLE

        cardBannerIcon.setImageResource(banner.iconRes)
        cardBannerIcon.setColorFilter(banner.tintColor)
        cardBannerTitle.text = banner.title
        cardBannerSubtitle.text = banner.subtitle

        if (!banner.badge.isNullOrBlank()) {
            cardBannerBadge.text = banner.badge
            cardBannerBadge.setTextColor(banner.tintColor)
            cardBannerBadge.visibility = View.VISIBLE
        } else {
            cardBannerBadge.visibility = View.GONE
        }
    }

    fun setBasePillWidth(widthDp: Float) {
        basePillWidthDp = widthDp
        requestLayout()
    }

    fun setPillHeight(heightDp: Float) {
        pillHeightDp = heightDp
        val heightPx = (heightDp * density).toInt()
        val lp = pillView.layoutParams
        if (lp != null) {
            lp.height = heightPx
            pillView.layoutParams = lp
        }
        applyBackgroundStyles()
        requestLayout()
    }

    fun setNotchMargin(marginDp: Float) {
        notchMarginDp = marginDp
        // Punch-hole cutout base diameter is ~22dp. Clearance space expands with marginDp.
        val spacePx = ((22f + 2f * marginDp) * density).toInt()
        val lp = pillNotchCutoutSpace.layoutParams
        if (lp != null) {
            lp.width = spacePx
            pillNotchCutoutSpace.layoutParams = lp
        }
        requestLayout()
    }

    fun getEstimatedPillWidthDp(type: IslandActivityType): Float {
        val extra = when (type) {
            IslandActivityType.MEDIA -> 14f
            IslandActivityType.CALL -> 18f
            IslandActivityType.TIMER -> 20f
            IslandActivityType.CHARGING -> 16f
            IslandActivityType.SCREEN_RECORD -> 20f
            IslandActivityType.NAVIGATION -> 24f
            IslandActivityType.DOWNLOAD -> 26f
            IslandActivityType.TORCH -> 14f
            IslandActivityType.HOTSPOT -> 22f
            IslandActivityType.CAST -> 16f
            IslandActivityType.PRIVACY -> 36f
            IslandActivityType.CLIPBOARD -> 22f
            IslandActivityType.TRANSIENT_BANNER -> 28f
            else -> 0f
        }
        val calculatedCutoutSpace = 22f + 2f * notchMarginDp
        val contentPaddingAndIcons = 56f + extra
        return maxOf(basePillWidthDp, calculatedCutoutSpace + contentPaddingAndIcons)
    }
}
