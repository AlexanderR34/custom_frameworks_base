package com.android.systemui.statusbar.phone.island

import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.View

/**
 * Controller for Status Bar Music Island (Dynamic Island / Media Notification Icon)
 */
class MusicIslandController(
    private val context: Context,
    private val mainHandler: Handler = Handler(Looper.getMainLooper())
) {

    companion object {
        private const val TAG = "MusicIslandController"
        const val SETTING_MUSIC_ISLAND = "status_bar_music_island"
        private const val PAUSE_HIDE_DELAY_MS = 30000L // 30 seconds inactivity timeout
        private const val PROGRESS_TICK_INTERVAL_MS = 1000L
    }

    private val mMediaSessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

    private var mIslandView: MusicIslandView? = null
    private var mPopup: MusicIslandPopup? = null

    private var mActiveController: MediaController? = null
    private var mIsFeatureEnabled = false
    private var mIsPlaying = false
    private var mLastPlaybackState: PlaybackState? = null
    private var mCurrentDuration: Long = 0L

    private val mHideRunnable = Runnable {
        Log.d(TAG, "Inactivity timeout reached, hiding island")
        mIslandView?.hideIsland()
        mPopup?.dismissWithAnimation()
    }

    private val mProgressTickRunnable = object : Runnable {
        override fun run() {
            updatePlaybackProgress()
            if (mIsPlaying && mIsFeatureEnabled) {
                mainHandler.postDelayed(this, PROGRESS_TICK_INTERVAL_MS)
            }
        }
    }

    private val mMediaCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            mainHandler.post {
                handlePlaybackStateChanged(state)
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            mainHandler.post {
                handleMetadataChanged(metadata)
            }
        }

        override fun onSessionDestroyed() {
            mainHandler.post {
                findActiveMediaSession()
            }
        }
    }

    private val mSessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            mainHandler.post {
                updateActiveController(controllers)
            }
        }

    private val mSettingsObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            updateFeatureEnabledState()
        }
    }

    fun attachView(islandView: MusicIslandView) {
        mIslandView = islandView

        mPopup = MusicIslandPopup(context) { action ->
            handlePopupAction(action)
        }

        islandView.onIslandClicked = { anchor ->
            if (mPopup?.isShowing == true) {
                mPopup?.dismissWithAnimation()
            } else {
                mPopup?.showBelow(anchor)
            }
        }

        // Register settings observer
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor(SETTING_MUSIC_ISLAND),
            false,
            mSettingsObserver
        )

        try {
            mMediaSessionManager.addOnActiveSessionsChangedListener(mSessionsListener, null, mainHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register active sessions listener", e)
        }

        updateFeatureEnabledState()
        findActiveMediaSession()
    }

    fun detach() {
        mainHandler.removeCallbacks(mHideRunnable)
        mainHandler.removeCallbacks(mProgressTickRunnable)
        try {
            context.contentResolver.unregisterContentObserver(mSettingsObserver)
            mMediaSessionManager.removeOnActiveSessionsChangedListener(mSessionsListener)
            mActiveController?.unregisterCallback(mMediaCallback)
        } catch (ignored: Exception) {}
        mPopup?.dismissWithAnimation()
    }

    private fun updateFeatureEnabledState() {
        mIsFeatureEnabled = Settings.System.getInt(
            context.contentResolver, SETTING_MUSIC_ISLAND, 0
        ) == 1

        Log.d(TAG, "Music Island feature enabled: $mIsFeatureEnabled")
        if (!mIsFeatureEnabled) {
            mainHandler.removeCallbacks(mHideRunnable)
            mainHandler.removeCallbacks(mProgressTickRunnable)
            mIslandView?.hideIsland()
            mPopup?.dismissWithAnimation()
        } else {
            findActiveMediaSession()
        }
    }

    private fun findActiveMediaSession() {
        if (!mIsFeatureEnabled) return
        try {
            val sessions = mMediaSessionManager.getActiveSessions(null)
            updateActiveController(sessions)
        } catch (e: Exception) {
            Log.e(TAG, "Error querying active media sessions", e)
        }
    }

    private fun updateActiveController(controllers: List<MediaController>?) {
        if (!mIsFeatureEnabled) return

        var playingController: MediaController? = null
        if (controllers != null) {
            for (c in controllers) {
                val state = c.playbackState
                if (state != null && state.state == PlaybackState.STATE_PLAYING) {
                    playingController = c
                    break
                }
            }
            if (playingController == null && controllers.isNotEmpty()) {
                playingController = controllers[0]
            }
        }

        if (mActiveController?.sessionToken != playingController?.sessionToken) {
            mActiveController?.unregisterCallback(mMediaCallback)
            mActiveController = playingController
            mActiveController?.registerCallback(mMediaCallback, mainHandler)

            handleMetadataChanged(mActiveController?.metadata)
            handlePlaybackStateChanged(mActiveController?.playbackState)
        }
    }

    private fun handlePlaybackStateChanged(state: PlaybackState?) {
        mLastPlaybackState = state
        if (!mIsFeatureEnabled || state == null) {
            mIsPlaying = false
            mIslandView?.hideIsland()
            return
        }

        mIsPlaying = state.state == PlaybackState.STATE_PLAYING
        mPopup?.setPlayingState(mIsPlaying)

        if (mIsPlaying) {
            mainHandler.removeCallbacks(mHideRunnable)
            mIslandView?.showIsland()

            mainHandler.removeCallbacks(mProgressTickRunnable)
            mainHandler.post(mProgressTickRunnable)
        } else {
            mainHandler.removeCallbacks(mProgressTickRunnable)
            updatePlaybackProgress()

            // Start 30 seconds timer before hiding
            mainHandler.removeCallbacks(mHideRunnable)
            mainHandler.postDelayed(mHideRunnable, PAUSE_HIDE_DELAY_MS)
        }
    }

    private fun handleMetadataChanged(metadata: MediaMetadata?) {
        if (!mIsFeatureEnabled) return

        mCurrentDuration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

        var bitmap: Bitmap? = null
        if (metadata != null) {
            bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        }

        // Fallback: si la cancion no tiene portada de album, usar el icono de la app reproductora
        if (bitmap == null) {
            bitmap = getAppIconBitmap(mActiveController?.packageName)
        }

        mIslandView?.setArtwork(bitmap)

        if (bitmap != null) {
            val dominantColor = extractDominantColor(bitmap)
            mIslandView?.setRingColor(dominantColor)
        } else {
            val defaultColor = context.getColor(android.R.color.system_accent1_500)
            mIslandView?.setRingColor(defaultColor)
        }

        updatePlaybackProgress()
    }

    private fun getAppIconBitmap(packageName: String?): Bitmap? {
        if (packageName.isNullOrEmpty()) return null
        return try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            drawableToBitmap(drawable)
        } catch (e: Exception) {
            null
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 64
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 64
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun updatePlaybackProgress() {
        val state = mLastPlaybackState ?: return
        if (mCurrentDuration <= 0L) {
            mIslandView?.setProgress(0f)
            return
        }

        var currentPosition = state.position
        if (mIsPlaying && state.lastPositionUpdateTime > 0L) {
            val elapsed = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
            currentPosition += (elapsed * state.playbackSpeed).toLong()
        }

        val progressRatio = (currentPosition.toFloat() / mCurrentDuration.toFloat()).coerceIn(0f, 1f)
        mIslandView?.setProgress(progressRatio)
    }

    private fun handlePopupAction(action: MusicIslandPopup.Action) {
        val controller = mActiveController ?: return
        when (action) {
            MusicIslandPopup.Action.PREVIOUS -> {
                try {
                    controller.transportControls.skipToPrevious()
                } catch (e: Exception) {
                    sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                }
            }
            MusicIslandPopup.Action.TOGGLE_PLAY_PAUSE -> {
                try {
                    if (mIsPlaying) {
                        controller.transportControls.pause()
                    } else {
                        controller.transportControls.play()
                    }
                } catch (e: Exception) {
                    sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                }
            }
            MusicIslandPopup.Action.NEXT -> {
                try {
                    controller.transportControls.skipToNext()
                } catch (e: Exception) {
                    sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT)
                }
            }
        }
    }

    private fun sendMediaKeyEvent(keyCode: Int) {
        val controller = mActiveController ?: return
        try {
            val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            val eventUp = KeyEvent(KeyEvent.ACTION_UP, keyCode)
            controller.dispatchMediaButtonEvent(eventDown)
            controller.dispatchMediaButtonEvent(eventUp)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send media key event: $keyCode", e)
        }
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
                    // Skip transparent pixels
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
                context.getColor(android.R.color.system_accent1_500)
            }
        } catch (e: Exception) {
            context.getColor(android.R.color.system_accent1_500)
        }
    }
}
