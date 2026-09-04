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

    init {
        mediaDataManager.addListener(this)
        statusBarStateController.addCallback(this)
        isKeyguardShowing = statusBarStateController.state == StatusBarState.KEYGUARD
        isDozing = statusBarStateController.isDozing
    }

    fun attachView(view: LockscreenLyricsView) {
        lyricsView = view
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
            activeController = MediaController(context, token)
        }

        if (song == currentTrackName && artist == currentArtistName) {
            evaluateTickerState()
            return
        }

        currentTrackName = song
        currentArtistName = artist
        currentLyrics = null
        updateLyricsDisplay()

        val durationMs = activeController?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
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
        currentTrackName = null
        currentArtistName = null
        currentLyrics = null
        activeController = null
        updateLyricsDisplay()
        evaluateTickerState()
    }

    private fun evaluateTickerState() {
        val isPlaying = activeController?.playbackState?.state == PlaybackState.STATE_PLAYING
        val shouldTick = (isKeyguardShowing || isDozing) && isPlaying && (currentLyrics != null) && (lyricsView != null)

        if (shouldTick && !isTicking) {
            isTicking = true
            mainHandler.post(tickerRunnable)
        } else if (!shouldTick && isTicking) {
            isTicking = false
            mainHandler.removeCallbacks(tickerRunnable)
        }

        if (!isPlaying || currentLyrics == null) {
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

        // Apply audio lead compensation (+200ms) to counteract Bluetooth/media player buffer latency
        val syncPosition = position + 200L

        val index = lyrics.findCurrentIndex(syncPosition)
        lyricsView?.updateLyrics(lyrics, index, syncPosition)
    }

    private fun updateLyricsDisplay() {
        val lyrics = currentLyrics
        if (lyrics != null && (isKeyguardShowing || isDozing)) {
            lyricsView?.setDozing(isDozing)
            updateProgress()
        } else {
            lyricsView?.visibility = View.GONE
        }
    }
}
