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

package com.android.systemui.statusbar.lyrics

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.UserHandle
import android.util.Log
import android.view.View
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.media.controls.domain.pipeline.MediaDataManager
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.statusbar.StatusBarState
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val TAG = "LockscreenLyricsCtrl"
private const val TICK_INTERVAL_MS = 60L

@SysUISingleton
class LockscreenLyricsController @Inject constructor(
    @Application private val context: Context,
    @Main private val mainHandler: Handler,
    @Application private val applicationScope: CoroutineScope,
    private val mediaDataManager: MediaDataManager,
    private val lyricsRepository: LyricsRepository,
    private val statusBarStateController: StatusBarStateController
) : MediaDataManager.Listener, StatusBarStateController.StateListener {

    private var lyricsView: LockscreenLyricsView? = null
    private var activeController: MediaController? = null
    private var currentTrackName: String? = null
    private var currentArtistName: String? = null
    private var currentLyrics: LyricsData? = null
    private var isMediaPlaying: Boolean = false

    private var fetchJob: Job? = null
    private var isTicking = false
    private var isKeyguardShowing = false
    private var isDozing = false

    private val tickerRunnable = object : Runnable {
        override fun run() {
            if (!isTicking) return
            updateProgress()
            mainHandler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

    private val mediaControllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            mainHandler.post {
                val playing = state?.state == PlaybackState.STATE_PLAYING
                if (isMediaPlaying != playing) {
                    isMediaPlaying = playing
                    evaluateTickerState()
                    updateLyricsDisplay()
                } else {
                    updateProgress()
                }
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            mainHandler.post {
                val song = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim()
                val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim() ?: ""
                val durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
                if (!song.isNullOrEmpty() && (song != currentTrackName || artist != currentArtistName)) {
                    loadLyrics(song, artist, durationMs)
                }
            }
        }

        override fun onSessionDestroyed() {
            mainHandler.post {
                clearMedia()
            }
        }
    }

    private var isEnabled = true
    private var displayTarget = 2 // 0: Lockscreen, 1: AOD, 2: Both
    private var linesLockscreen = 5
    private var linesAod = 1

    private val settingsObserver = object : android.database.ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
            updateSettings()
        }
    }

    init {
        mediaDataManager.addListener(this)
        statusBarStateController.addCallback(this)
        isKeyguardShowing = statusBarStateController.state == StatusBarState.KEYGUARD
        isDozing = statusBarStateController.isDozing

        val resolver = context.contentResolver
        try {
            resolver.registerContentObserver(
                android.provider.Settings.System.getUriFor("lockscreen_lyrics_enabled"),
                false,
                settingsObserver,
                UserHandle.USER_ALL
            )
            resolver.registerContentObserver(
                android.provider.Settings.System.getUriFor("lockscreen_lyrics_display_target"),
                false,
                settingsObserver,
                UserHandle.USER_ALL
            )
            resolver.registerContentObserver(
                android.provider.Settings.System.getUriFor("lockscreen_lyrics_lines_lockscreen"),
                false,
                settingsObserver,
                UserHandle.USER_ALL
            )
            resolver.registerContentObserver(
                android.provider.Settings.System.getUriFor("lockscreen_lyrics_lines_aod"),
                false,
                settingsObserver,
                UserHandle.USER_ALL
            )
        } catch (e: Exception) {
            resolver.registerContentObserver(
                android.provider.Settings.System.getUriFor("lockscreen_lyrics_enabled"),
                false,
                settingsObserver
            )
            resolver.registerContentObserver(
                android.provider.Settings.System.getUriFor("lockscreen_lyrics_display_target"),
                false,
                settingsObserver
            )
            resolver.registerContentObserver(
                android.provider.Settings.System.getUriFor("lockscreen_lyrics_lines_lockscreen"),
                false,
                settingsObserver
            )
            resolver.registerContentObserver(
                android.provider.Settings.System.getUriFor("lockscreen_lyrics_lines_aod"),
                false,
                settingsObserver
            )
        }
        updateSettings()
    }

    private fun updateSettings() {
        val resolver = context.contentResolver
        isEnabled = android.provider.Settings.System.getIntForUser(
            resolver, "lockscreen_lyrics_enabled", 1, UserHandle.USER_CURRENT
        ) != 0
        displayTarget = android.provider.Settings.System.getIntForUser(
            resolver, "lockscreen_lyrics_display_target", 2, UserHandle.USER_CURRENT
        )
        linesLockscreen = android.provider.Settings.System.getIntForUser(
            resolver, "lockscreen_lyrics_lines_lockscreen", 5, UserHandle.USER_CURRENT
        )
        linesAod = android.provider.Settings.System.getIntForUser(
            resolver, "lockscreen_lyrics_lines_aod", 1, UserHandle.USER_CURRENT
        )
        lyricsView?.setLineCounts(linesLockscreen, linesAod)
        updateLyricsDisplay()
        evaluateTickerState()
    }

    private fun shouldShowForCurrentState(): Boolean {
        if (!isEnabled) return false
        if (isDozing) {
            return displayTarget == 1 || displayTarget == 2
        } else if (isKeyguardShowing) {
            return displayTarget == 0 || displayTarget == 2
        }
        return false
    }

    fun attachView(view: LockscreenLyricsView) {
        lyricsView = view
        lyricsView?.setLineCounts(linesLockscreen, linesAod)
        lyricsView?.setDozing(isDozing)
        updateLyricsDisplay()
        evaluateTickerState()
    }

    fun detachView() {
        lyricsView = null
        evaluateTickerState()
    }

    override fun onMediaDataLoaded(
        key: String,
        oldKey: String?,
        data: MediaData,
        immediately: Boolean
    ) {
        handleMediaData(data)
    }

    override fun onCurrentActiveMediaChanged(key: String?, data: MediaData?) {
        if (data != null) {
            handleMediaData(data)
        } else {
            clearMedia()
        }
    }

    override fun onMediaDataRemoved(key: String, userInitiated: Boolean) {
        if (!mediaDataManager.hasActiveMedia()) {
            clearMedia()
        }
    }

    override fun onStateChanged(newState: Int) {
        isKeyguardShowing = (newState == StatusBarState.KEYGUARD)
        evaluateTickerState()
        updateLyricsDisplay()
    }

    override fun onDozingChanged(dozing: Boolean) {
        isDozing = dozing
        lyricsView?.setDozing(dozing)
        evaluateTickerState()
        updateLyricsDisplay()
    }

    private fun handleMediaData(data: MediaData) {
        val song = data.song?.toString()?.trim()
        val artist = data.artist?.toString()?.trim() ?: ""

        if (song.isNullOrEmpty()) {
            clearMedia()
            return
        }

        val token = data.token
        if (token != null && (activeController == null || activeController?.sessionToken != token)) {
            try {
                activeController?.unregisterCallback(mediaControllerCallback)
            } catch (_: Exception) {}
            activeController = MediaController(context, token).apply {
                registerCallback(mediaControllerCallback, mainHandler)
            }
        }

        isMediaPlaying = data.isPlaying || (activeController?.playbackState?.state == PlaybackState.STATE_PLAYING)

        if (song == currentTrackName && artist == currentArtistName) {
            evaluateTickerState()
            updateLyricsDisplay()
            return
        }

        val durationMs = activeController?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        loadLyrics(song, artist, durationMs)
    }

    private fun loadLyrics(song: String, artist: String, durationMs: Long) {
        currentTrackName = song
        currentArtistName = artist
        currentLyrics = null
        updateLyricsDisplay()

        val durationSec = if (durationMs > 0) durationMs / 1000L else 0L

        fetchJob?.cancel()
        fetchJob = applicationScope.launch {
            val lyrics = lyricsRepository.fetchLyrics(song, artist, durationSec)
            mainHandler.post {
                if (currentTrackName == song && currentArtistName == artist) {
                    currentLyrics = lyrics
                    updateLyricsDisplay()
                    evaluateTickerState()
                }
            }
        }
    }

    private fun clearMedia() {
        fetchJob?.cancel()
        try {
            activeController?.unregisterCallback(mediaControllerCallback)
        } catch (_: Exception) {}
        currentTrackName = null
        currentArtistName = null
        currentLyrics = null
        activeController = null
        isMediaPlaying = false
        updateLyricsDisplay()
        evaluateTickerState()
    }

    private fun evaluateTickerState() {
        val isPlaying = isMediaPlaying || (activeController?.playbackState?.state == PlaybackState.STATE_PLAYING)
        val shouldTick = shouldShowForCurrentState() && isPlaying && (currentLyrics != null) && (lyricsView != null)

        if (shouldTick && !isTicking) {
            isTicking = true
            mainHandler.post(tickerRunnable)
        } else if (!shouldTick && isTicking) {
            isTicking = false
            mainHandler.removeCallbacks(tickerRunnable)
        }

        if (!isPlaying || currentLyrics == null || !shouldShowForCurrentState()) {
            lyricsView?.visibility = View.GONE
        }
    }

    private fun updateProgress() {
        val controller = activeController ?: return
        val lyrics = currentLyrics ?: return
        val playbackState = controller.playbackState ?: return

        var position = playbackState.position
        if (playbackState.state == PlaybackState.STATE_PLAYING) {
            val timeDelta = SystemClock.elapsedRealtime() - playbackState.lastPositionUpdateTime
            position += (timeDelta * playbackState.playbackSpeed).toLong()
        }

        val syncPosition = maxOf(0L, position)
        val index = lyrics.findCurrentIndex(syncPosition)
        lyricsView?.updateLyrics(lyrics, index, syncPosition)
    }

    private fun updateLyricsDisplay() {
        val lyrics = currentLyrics
        val isPlaying = isMediaPlaying || (activeController?.playbackState?.state == PlaybackState.STATE_PLAYING)
        if (lyrics != null && shouldShowForCurrentState() && isPlaying) {
            lyricsView?.setDozing(isDozing)
            updateProgress()
        } else {
            lyricsView?.visibility = View.GONE
        }
    }
}
