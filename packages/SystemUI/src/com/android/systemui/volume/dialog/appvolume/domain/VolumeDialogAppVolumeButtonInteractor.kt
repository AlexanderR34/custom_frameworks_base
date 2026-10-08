package com.android.systemui.volume.dialog.appvolume.domain

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.volume.dialog.dagger.scope.VolumeDialog
import com.android.systemui.volume.dialog.dagger.scope.VolumeDialogScope
import com.android.systemui.volume.domain.interactor.VolumePanelNavigationInteractor
import com.android.systemui.volume.ui.navigation.VolumeNavigator
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn

/** Exposes [VolumeDialogAppVolumeButtonViewModel]. */
@VolumeDialogScope
class VolumeDialogAppVolumeButtonInteractor
@Inject
constructor(
    @Application private val context: Context,
    @VolumeDialog private val coroutineScope: CoroutineScope,
    private val volumeNavigator: VolumeNavigator,
    private val volumePanelNavigationInteractor: VolumePanelNavigationInteractor,
) {
    private fun shouldShowAppVolume(): Boolean {
        val showAppVolume = Settings.System.getIntForUser(
            context.contentResolver,
            Settings.System.SHOW_APP_VOLUME,
            1,
            UserHandle.USER_CURRENT
        )
        if (showAppVolume == 1) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
            val list = try { audioManager.listAppVolumes() } catch (_: Exception) { null }
            if (list != null && list.any { it.packageName != "android" && it.packageName != "com.android.systemui" }) {
                return true
            }
            if (audioManager.isMusicActive) {
                return true
            }
            try {
                if (audioManager.activePlaybackConfigurations.isNotEmpty()) {
                    return true
                }
            } catch (_: Exception) {}
            try {
                val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? android.media.session.MediaSessionManager
                if (mediaSessionManager != null && mediaSessionManager.getActiveSessions(null).isNotEmpty()) {
                    return true
                }
            } catch (_: Exception) {}
            try {
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                val runningTasks = activityManager?.getRunningTasks(1)
                if (runningTasks != null && runningTasks.isNotEmpty()) {
                    val topPkg = runningTasks[0].topActivity?.packageName
                    if (topPkg != null && topPkg != "android" && topPkg != "com.android.systemui" && !topPkg.contains("launcher")) {
                        return true
                    }
                }
            } catch (_: Exception) {}
        }
        return false
    }

    val isVisible: StateFlow<Boolean> =
        callbackFlow {
            val handler = Handler(Looper.getMainLooper())
            val observer = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    trySend(shouldShowAppVolume())
                }
            }
            context.contentResolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.SHOW_APP_VOLUME),
                false,
                observer,
                UserHandle.USER_CURRENT
            )

            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
                override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
                    trySend(shouldShowAppVolume())
                }
            }
            try {
                audioManager.registerAudioPlaybackCallback(playbackCallback, handler)
            } catch (e: Exception) {
                // Ignore if audio playback callback not available in current process
            }

            trySend(shouldShowAppVolume())
            awaitClose {
                context.contentResolver.unregisterContentObserver(observer)
                try {
                    audioManager.unregisterAudioPlaybackCallback(playbackCallback)
                } catch (e: Exception) {
                }
            }
        }
            .stateIn(coroutineScope, SharingStarted.Eagerly, shouldShowAppVolume())

    fun onButtonClicked() {
        volumeNavigator.openVolumePanel(
            volumePanelNavigationInteractor.getAppVolumePanelRoute()
        )
    }
}
