/*
 * Copyright (C) 2024-2026 The Android Open Source Project
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

package com.android.systemui.statusbar.chips.media.domain.interactor

import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.UserHandle
import android.provider.Settings
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.Logger
import com.android.systemui.statusbar.StatusBarState
import com.android.systemui.statusbar.chips.StatusBarChipLogTags.pad
import com.android.systemui.statusbar.chips.StatusBarChipsLog
import com.android.systemui.statusbar.chips.media.domain.model.MediaChipModel
import com.android.systemui.statusbar.policy.KeyguardStateController
import com.android.systemui.plugins.statusbar.StatusBarStateController
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/** Interactor for tracking the active media playback session for the status bar chip. */
@SysUISingleton
class MediaChipInteractor
@Inject
constructor(
    @Application private val scope: CoroutineScope,
    private val context: Context,
    private val mediaSessionManager: MediaSessionManager,
    private val keyguardStateController: KeyguardStateController,
    private val statusBarStateController: StatusBarStateController,
    private val mediaDataManagerLazy: dagger.Lazy<com.android.systemui.media.controls.domain.pipeline.MediaDataManager>,
    @StatusBarChipsLog private val logBuffer: LogBuffer,
) {
    private val logger = Logger(logBuffer, "MediaChip".pad())
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _mediaState = MutableStateFlow<MediaChipModel>(MediaChipModel.Inactive)

    private var activeController: MediaController? = null
    private var isFeatureEnabled = true
    private var isKeyguardShowing = false
    private var currentDuration: Long = 0L
    private var localPlaybackStartTime: Long = 0L
    private var cachedArtworkBitmap: Bitmap? = null
    private var cachedSongTitle: String? = null
    private var cachedArtistName: String? = null

    private val hideRunnable = Runnable {
        _mediaState.value = MediaChipModel.Inactive
    }

    private val progressTickRunnable = object : Runnable {
        override fun run() {
            updateMediaState()
            val state = _mediaState.value
            if (state is MediaChipModel.Active && state.isPlaying && isFeatureEnabled && !isKeyguardShowing) {
                mainHandler.postDelayed(this, PROGRESS_TICK_INTERVAL_MS)
            }
        }
    }

    private val keyguardCallback = object : KeyguardStateController.Callback {
        override fun onKeyguardShowingChanged() {
            mainHandler.post { updateKeyguardAndState() }
        }

        override fun onKeyguardFadingAwayChanged() {
            mainHandler.post { updateKeyguardAndState() }
        }

        override fun onUnlockedChanged() {
            mainHandler.post { updateKeyguardAndState() }
        }
    }

    private val statusBarStateListener = object : StatusBarStateController.StateListener {
        override fun onStateChanged(newState: Int) {
            mainHandler.post { updateKeyguardAndState() }
        }

        override fun onDozingChanged(isDozing: Boolean) {
            mainHandler.post { updateKeyguardAndState() }
        }
    }

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

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            mainHandler.post { updateActiveController(controllers) }
        }

    private val settingsObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            updateFeatureEnabledState()
        }
    }

    init {
        try {
            context.contentResolver.registerContentObserver(
                Settings.System.getUriFor(SETTING_ENABLE_MEDIA),
                false,
                settingsObserver,
                UserHandle.USER_ALL,
            )
            context.contentResolver.registerContentObserver(
                Settings.System.getUriFor(SETTING_MUSIC_ISLAND),
                false,
                settingsObserver,
                UserHandle.USER_ALL,
            )
        } catch (ignored: Exception) {}

        keyguardStateController.addCallback(keyguardCallback)
        statusBarStateController.addCallback(statusBarStateListener)

        try {
            mediaSessionManager.addOnActiveSessionsChangedListener(sessionsListener, null, mainHandler)
        } catch (e: Exception) {
            logger.e({ "Failed to register active media sessions listener: $str1" }) { str1 = e.message }
        }

        try {
            mediaDataManagerLazy.get().addListener(object : com.android.systemui.media.controls.domain.pipeline.MediaDataManager.Listener {
                override fun onMediaDataLoaded(
                    key: String,
                    oldKey: String?,
                    data: com.android.systemui.media.controls.shared.model.MediaData,
                    immediately: Boolean
                ) {
                    val artIcon = data.artwork
                    if (artIcon != null) {
                        try {
                            val drawable = artIcon.loadDrawable(context)
                            if (drawable is android.graphics.drawable.BitmapDrawable) {
                                cachedArtworkBitmap = drawable.bitmap
                            } else if (drawable != null) {
                                val bmp = Bitmap.createBitmap(
                                    drawable.intrinsicWidth.coerceAtLeast(1),
                                    drawable.intrinsicHeight.coerceAtLeast(1),
                                    Bitmap.Config.ARGB_8888
                                )
                                val canvas = android.graphics.Canvas(bmp)
                                drawable.setBounds(0, 0, canvas.width, canvas.height)
                                drawable.draw(canvas)
                                cachedArtworkBitmap = bmp
                            }
                        } catch (e: Exception) {}
                    }
                    val songStr = data.song?.toString()?.trim()
                    if (!songStr.isNullOrEmpty() && !isWebDomain(songStr)) {
                        cachedSongTitle = songStr
                    }
                    val artistStr = data.artist?.toString()?.trim()
                    if (!artistStr.isNullOrEmpty() && !isWebDomain(artistStr)) {
                        cachedArtistName = artistStr
                    }
                    if (data.token != null) {
                        try {
                            if (activeController == null || activeController?.sessionToken != data.token) {
                                val ctrl = MediaController(context, data.token)
                                if (data.isPlaying == true || ctrl.playbackState?.state == PlaybackState.STATE_PLAYING || activeController == null) {
                                    activeController?.unregisterCallback(mediaCallback)
                                    activeController = ctrl
                                    cachedSongTitle = null
                                    cachedArtistName = null
                                    cachedArtworkBitmap = null
                                    activeController?.registerCallback(mediaCallback, mainHandler)
                                    handleMetadataChanged(ctrl.metadata)
                                    handlePlaybackStateChanged(ctrl.playbackState)
                                }
                            }
                        } catch (e: Exception) {}
                    }
                    mainHandler.post { updateMediaState() }
                }

                override fun onMediaDataRemoved(key: String, userInitiated: Boolean) {
                    if (!mediaDataManagerLazy.get().hasActiveMedia()) {
                        cachedArtworkBitmap = null
                        cachedSongTitle = null
                        cachedArtistName = null
                    }
                }
            })
        } catch (e: Exception) {
            logger.e({ "Failed to register mediaDataManager listener: $str1" }) { str1 = e.message }
        }

        updateKeyguardAndState()
        updateFeatureEnabledState()
    }

    val mediaState: StateFlow<MediaChipModel> =
        _mediaState
            .asStateFlow()
            .onEach {
                logger.d({ "Media chip state updated: newState=$str1" }) { str1 = it.logString() }
            }
            .stateIn(scope, SharingStarted.Lazily, MediaChipModel.Inactive)

    private fun updateKeyguardAndState() {
        val isLocked = keyguardStateController.isShowing ||
            statusBarStateController.state == StatusBarState.KEYGUARD ||
            statusBarStateController.state == StatusBarState.SHADE_LOCKED ||
            statusBarStateController.isDozing

        if (isKeyguardShowing != isLocked) {
            isKeyguardShowing = isLocked
            if (isKeyguardShowing) {
                _mediaState.value = MediaChipModel.Inactive
            } else {
                updateMediaState()
            }
        }
    }

    private fun updateFeatureEnabledState() {
        val mediaChipEnabled = Settings.System.getIntForUser(
            context.contentResolver, SETTING_ENABLE_MEDIA, 1, UserHandle.USER_CURRENT
        ) == 1
        val musicIslandEnabled = Settings.System.getIntForUser(
            context.contentResolver, SETTING_MUSIC_ISLAND, 1, UserHandle.USER_CURRENT
        ) == 1
        isFeatureEnabled = mediaChipEnabled && musicIslandEnabled

        if (!isFeatureEnabled) {
            mainHandler.removeCallbacks(hideRunnable)
            mainHandler.removeCallbacks(progressTickRunnable)
            activeController?.unregisterCallback(mediaCallback)
            activeController = null
            _mediaState.value = MediaChipModel.Inactive
        } else {
            findActiveMediaSession()
        }
    }

    private fun findActiveMediaSession() {
        if (!isFeatureEnabled) return
        try {
            val sessions = mediaSessionManager.getActiveSessions(null)
            updateActiveController(sessions)
        } catch (e: Exception) {
            logger.e({ "Error querying active media sessions: $str1" }) { str1 = e.message }
        }
    }

    private fun updateActiveController(controllers: List<MediaController>?) {
        if (!isFeatureEnabled) return

        var playingController: MediaController? = null
        if (controllers != null) {
            // 1. Look for a controller that is actively playing
            for (c in controllers) {
                val state = c.playbackState
                if (state != null && state.state == PlaybackState.STATE_PLAYING) {
                    playingController = c
                    break
                }
            }
            // 2. If no controller is actively playing, keep the current active controller
            // ONLY if it is still present in the list and in PAUSED or BUFFERING state
            if (playingController == null && activeController != null) {
                val currentToken = activeController?.sessionToken
                val stillPresent = controllers.firstOrNull { it.sessionToken == currentToken }
                val state = stillPresent?.playbackState?.state
                if (stillPresent != null && (state == PlaybackState.STATE_PAUSED || state == PlaybackState.STATE_BUFFERING)) {
                    playingController = stillPresent
                }
            }
        }

        if (playingController == null) {
            // No media is currently playing or actively tracked
            mainHandler.removeCallbacks(hideRunnable)
            mainHandler.removeCallbacks(progressTickRunnable)
            activeController?.unregisterCallback(mediaCallback)
            activeController = null
            _mediaState.value = MediaChipModel.Inactive
            return
        }

        if (activeController?.sessionToken != playingController.sessionToken) {
            activeController?.unregisterCallback(mediaCallback)
            activeController = playingController
            cachedSongTitle = null
            cachedArtistName = null
            cachedArtworkBitmap = null
            activeController?.registerCallback(mediaCallback, mainHandler)

            handleMetadataChanged(activeController?.metadata)
            handlePlaybackStateChanged(activeController?.playbackState)
        }
    }

    private fun getMediaTimeoutSeconds(): Int {
        return try {
            Settings.System.getIntForUser(
                context.contentResolver,
                SETTING_MEDIA_TIMEOUT,
                DEFAULT_PAUSE_HIDE_DELAY_SECONDS,
                UserHandle.USER_CURRENT,
            )
        } catch (e: Exception) {
            DEFAULT_PAUSE_HIDE_DELAY_SECONDS
        }
    }

    private fun handlePlaybackStateChanged(state: PlaybackState?) {
        if (!isFeatureEnabled || state == null || isKeyguardShowing) {
            _mediaState.value = MediaChipModel.Inactive
            return
        }

        val playbackStateVal = state.state
        if (playbackStateVal == PlaybackState.STATE_STOPPED ||
            playbackStateVal == PlaybackState.STATE_NONE ||
            playbackStateVal == PlaybackState.STATE_ERROR
        ) {
            mainHandler.removeCallbacks(progressTickRunnable)
            mainHandler.removeCallbacks(hideRunnable)
            activeController?.unregisterCallback(mediaCallback)
            activeController = null
            _mediaState.value = MediaChipModel.Inactive
            return
        }

        val isPlaying = playbackStateVal == PlaybackState.STATE_PLAYING
        if (isPlaying) {
            mainHandler.removeCallbacks(hideRunnable)
            updateMediaState()
            mainHandler.removeCallbacks(progressTickRunnable)
            mainHandler.post(progressTickRunnable)
        } else {
            localPlaybackStartTime = 0L
            mainHandler.removeCallbacks(progressTickRunnable)
            updateMediaState()
            mainHandler.removeCallbacks(hideRunnable)
            val timeoutSecs = getMediaTimeoutSeconds()
            if (timeoutSecs > 0) {
                mainHandler.postDelayed(hideRunnable, timeoutSecs * 1000L)
            } else {
                _mediaState.value = MediaChipModel.Inactive
            }
        }
    }

    private fun handleMetadataChanged(metadata: MediaMetadata?) {
        if (!isFeatureEnabled || isKeyguardShowing) return
        currentDuration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val metaTitle = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim()
        if (!metaTitle.isNullOrBlank() && !isWebDomain(metaTitle)) {
            cachedSongTitle = metaTitle
        }
        val metaArtist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim()
        if (!metaArtist.isNullOrBlank() && !isWebDomain(metaArtist)) {
            cachedArtistName = metaArtist
        }
        updateMediaState()
    }

    private fun updateMediaState() {
        val controller = activeController
        val playbackState = controller?.playbackState
        if (controller == null || playbackState == null || isKeyguardShowing || !isFeatureEnabled) {
            _mediaState.value = MediaChipModel.Inactive
            return
        }

        val stateVal = playbackState.state
        if (stateVal != PlaybackState.STATE_PLAYING &&
            stateVal != PlaybackState.STATE_PAUSED &&
            stateVal != PlaybackState.STATE_BUFFERING
        ) {
            _mediaState.value = MediaChipModel.Inactive
            return
        }

        val metadata = controller.metadata
        val isPlaying = stateVal == PlaybackState.STATE_PLAYING && (playbackState.playbackSpeed > 0f || playbackState.position >= 0)

        var position = playbackState.position
        if (position >= 0) {
            if (isPlaying && playbackState.lastPositionUpdateTime > 0L) {
                val elapsed = SystemClock.elapsedRealtime() - playbackState.lastPositionUpdateTime
                position += (elapsed * playbackState.playbackSpeed).toLong()
            }
        } else {
            // For web media sessions (Brave, Chrome, web players) that report -1 / unknown position
            if (isPlaying) {
                if (localPlaybackStartTime == 0L) {
                    localPlaybackStartTime = SystemClock.elapsedRealtime()
                }
                position = maxOf(0L, SystemClock.elapsedRealtime() - localPlaybackStartTime)
            } else {
                localPlaybackStartTime = 0L
                position = 0L
            }
        }

        val packageName = controller.packageName
        val appName = try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(appInfo)
        } catch (e: Exception) {
            null
        }

        val appIcon: Drawable? = try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            null
        }

        val rawTitle = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim()
        val rawArtist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim()

        val title = if (!rawTitle.isNullOrBlank() && !isWebDomain(rawTitle)) {
            rawTitle
        } else {
            cachedSongTitle ?: if (!isWebDomain(appName?.toString() ?: "")) appName?.toString() else null
        }

        val artist = if (!rawArtist.isNullOrBlank() && !isWebDomain(rawArtist)) {
            rawArtist
        } else {
            cachedArtistName
        }

        var artwork: Bitmap? = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: cachedArtworkBitmap

        val dominantColor = artwork?.let { extractDominantColor(it) }

        _mediaState.value =
            MediaChipModel.Active(
                packageName = packageName,
                appName = appName,
                title = title,
                artist = artist,
                artwork = artwork,
                appIcon = appIcon,
                isPlaying = isPlaying,
                durationMs = currentDuration,
                playbackPositionMs = position,
                dominantColor = dominantColor,
                intent = controller.sessionActivity,
            )
    }

    private fun isWebDomain(str: String): Boolean {
        val lower = str.lowercase().trim()
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("www.")) return true
        if (Regex("(?i)^(https?://)?([a-z0-9-]+\\.)+[a-z]{2,}(/.*)?$").matches(lower)) return true
        return lower.contains(".com") ||
                lower.contains(".net") ||
                lower.contains(".org") ||
                lower.contains(".io") ||
                lower.contains(".edu") ||
                lower.contains(".gov") ||
                lower.contains(".site") ||
                lower.contains(".online") ||
                lower.contains(".app") ||
                lower.contains(".xyz") ||
                lower.contains(".me") ||
                lower.contains(".tv") ||
                lower.contains("khinsider") ||
                lower.contains("music.youtube") ||
                lower.contains("youtube.com") ||
                lower.contains(".youtube") ||
                lower.contains("youtube")
    }

    private fun extractDominantColor(bitmap: Bitmap): Int {
        return try {
            val scaled = Bitmap.createScaledBitmap(bitmap, 16, 16, false)
            var redBucket = 0
            var greenBucket = 0
            var blueBucket = 0
            var pixelCount = 0

            for (y in 0 until scaled.height) {
                for (x in 0 until scaled.width) {
                    val p = scaled.getPixel(x, y)
                    if (Color.alpha(p) < 50) continue
                    redBucket += Color.red(p)
                    greenBucket += Color.green(p)
                    blueBucket += Color.blue(p)
                    pixelCount++
                }
            }
            if (pixelCount > 0) {
                Color.rgb(redBucket / pixelCount, greenBucket / pixelCount, blueBucket / pixelCount)
            } else {
                context.getColor(com.android.internal.R.color.system_accent1_500)
            }
        } catch (e: Exception) {
            context.getColor(com.android.internal.R.color.system_accent1_500)
        }
    }

    fun handlePopupAction(action: com.android.systemui.statusbar.phone.island.MusicIslandPopup.Action) {
        val controller = activeController ?: return
        when (action) {
            com.android.systemui.statusbar.phone.island.MusicIslandPopup.Action.PREVIOUS -> {
                try {
                    controller.transportControls.skipToPrevious()
                } catch (e: Exception) {
                    sendMediaKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                }
            }
            com.android.systemui.statusbar.phone.island.MusicIslandPopup.Action.TOGGLE_PLAY_PAUSE -> {
                try {
                    val isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING
                    if (isPlaying) {
                        controller.transportControls.pause()
                    } else {
                        controller.transportControls.play()
                    }
                } catch (e: Exception) {
                    sendMediaKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                }
            }
            com.android.systemui.statusbar.phone.island.MusicIslandPopup.Action.NEXT -> {
                try {
                    controller.transportControls.skipToNext()
                } catch (e: Exception) {
                    sendMediaKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
                }
            }
        }
    }

    private fun sendMediaKeyEvent(keyCode: Int) {
        val controller = activeController ?: return
        try {
            val eventDown = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode)
            val eventUp = android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode)
            controller.dispatchMediaButtonEvent(eventDown)
            controller.dispatchMediaButtonEvent(eventUp)
        } catch (e: Exception) {
            logger.e({ "Failed to send media key event: $str1" }) { str1 = e.message }
        }
    }

    companion object {
        const val SETTING_ENABLE_MEDIA = "status_bar_chip_enable_media"
        const val SETTING_MUSIC_ISLAND = "status_bar_music_island"
        const val SETTING_MEDIA_TIMEOUT = "status_bar_chips_media_timeout"
        private const val DEFAULT_PAUSE_HIDE_DELAY_SECONDS = 30
        private const val PROGRESS_TICK_INTERVAL_MS = 1000L
    }
}
