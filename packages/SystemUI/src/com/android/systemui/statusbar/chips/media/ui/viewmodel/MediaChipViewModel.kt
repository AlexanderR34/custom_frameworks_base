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

package com.android.systemui.statusbar.chips.media.ui.viewmodel

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import com.android.systemui.animation.Expandable
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.res.R
import com.android.systemui.statusbar.chips.media.domain.interactor.MediaChipInteractor
import com.android.systemui.statusbar.chips.media.domain.model.MediaChipModel
import com.android.systemui.statusbar.chips.ui.model.Chronometer
import com.android.systemui.statusbar.chips.ui.model.ColorsModel
import com.android.systemui.statusbar.chips.ui.model.EventTime
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel
import com.android.systemui.statusbar.chips.ui.viewmodel.OngoingActivityChipViewModel
import com.android.systemui.statusbar.chips.uievents.StatusBarChipsUiEventLogger
import com.android.systemui.statusbar.phone.island.MusicIslandPopup
import com.android.systemui.util.time.SystemClock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** View model for the media playback chip shown in the status bar. */
@SysUISingleton
class MediaChipViewModel
@Inject
constructor(
    @Main private val context: Context,
    @Application private val scope: CoroutineScope,
    private val interactor: MediaChipInteractor,
    private val systemClock: SystemClock,
    private val activityStarter: ActivityStarter,
    private val uiEventLogger: StatusBarChipsUiEventLogger,
) : OngoingActivityChipViewModel {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var musicPopup: MusicIslandPopup? = null

    override val chip: StateFlow<OngoingActivityChipModel> =
        interactor.mediaState
            .map { state ->
                when (state) {
                    is MediaChipModel.Inactive -> {
                        mainHandler.post {
                            musicPopup?.dismissWithAnimation()
                        }
                        OngoingActivityChipModel.Inactive()
                    }
                    is MediaChipModel.Active -> {
                        mainHandler.post {
                            musicPopup?.let { popup ->
                                if (popup.isShowing) {
                                    popup.setPlayingState(state.isPlaying)
                                }
                            }
                        }
                        prepareChip(state)
                    }
                }
            }
            .stateIn(
                scope,
                SharingStarted.WhileSubscribed(),
                OngoingActivityChipModel.Inactive(),
            )

    private fun prepareChip(state: MediaChipModel.Active): OngoingActivityChipModel.Active {
        val key = "$KEY_PREFIX${state.packageName}"
        val contentDesc = ContentDescription.Loaded(
            state.title?.toString() ?: state.appName?.toString() ?: "Media Playback"
        )

        val artStyle = try {
            android.provider.Settings.System.getIntForUser(
                context.contentResolver,
                "status_bar_chips_media_art_style",
                0,
                android.os.UserHandle.USER_CURRENT,
            )
        } catch (e: Exception) {
            0
        }

        val icon: OngoingActivityChipModel.ChipIcon =
            when {
                artStyle != 4 && state.artwork != null -> {
                    OngoingActivityChipModel.ChipIcon.FullColorIcon(
                        impl = Icon.Loaded(
                            drawable = BitmapDrawable(context.resources, state.artwork),
                            contentDescription = contentDesc,
                        ),
                        isMediaArtwork = true,
                        artStyle = artStyle,
                    )
                }
                artStyle != 4 && state.appIcon != null -> {
                    OngoingActivityChipModel.ChipIcon.FullColorIcon(
                        impl = Icon.Loaded(
                            drawable = state.appIcon,
                            contentDescription = contentDesc,
                        ),
                        isMediaArtwork = true,
                        artStyle = artStyle,
                    )
                }
                state.appIcon != null -> {
                    OngoingActivityChipModel.ChipIcon.FullColorIcon(
                        impl = Icon.Loaded(
                            drawable = state.appIcon,
                            contentDescription = contentDesc,
                        ),
                        isMediaArtwork = false,
                        artStyle = 4,
                    )
                }
                else -> {
                    OngoingActivityChipModel.ChipIcon.SingleColorIcon(
                        Icon.Resource(
                            resId = R.drawable.ic_music_island_play,
                            contentDescription = contentDesc,
                        )
                    )
                }
            }

        // Show elapsed playback time chronometer
        val cleanTitle = state.title?.toString()?.takeUnless { isWebDomain(it) }
        val content =
            if (state.isPlaying) {
                val pos = if (state.playbackPositionMs >= 0) state.playbackPositionMs else 0L
                val startTimeInElapsedRealtime =
                    systemClock.elapsedRealtime() - pos
                OngoingActivityChipModel.Content.Timer(
                    value =
                        Chronometer.Running(
                            EventTime.ElapsedRealtime(startTimeInElapsedRealtime)
                        ),
                    timeSource = systemClock,
                )
            } else if (state.playbackPositionMs > 0) {
                OngoingActivityChipModel.Content.Timer(
                    value = Chronometer.Paused(java.time.Duration.ofMillis(state.playbackPositionMs)),
                    timeSource = systemClock,
                )
            } else if (!cleanTitle.isNullOrBlank()) {
                OngoingActivityChipModel.Content.Text(cleanTitle)
            } else {
                OngoingActivityChipModel.Content.Text("Pausa")
            }

        val colors = ColorsModel.DynamicThemed(state.dominantColor)

        val clickBehavior =
            OngoingActivityChipModel.ClickBehavior.ExpandAction { _ ->
                mainHandler.post {
                    showOrTogglePopup(state)
                }
            }

        return OngoingActivityChipModel.Active(
            key = key,
            managingPackageName = state.packageName,
            icon = icon,
            content = content,
            colors = colors,
            clickBehavior = clickBehavior,
        )
    }

    private fun showOrTogglePopup(state: MediaChipModel.Active) {
        val popup = musicPopup ?: MusicIslandPopup(context) { action ->
            interactor.handlePopupAction(action)
        }.also { musicPopup = it }

        if (popup.isShowing) {
            popup.dismissWithAnimation()
            return
        }

        popup.setPlayingState(state.isPlaying)
        val isMonet = try {
            android.provider.Settings.System.getIntForUser(
                context.contentResolver,
                "status_bar_chips_monet_colors",
                0,
                android.os.UserHandle.USER_CURRENT,
            ) == 1
        } catch (e: Exception) {
            false
        }
        if (isMonet || state.dominantColor == null) {
            popup.applyMonetTheme()
        } else {
            popup.applyMonetTheme(state.dominantColor)
        }

        popup.show()
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

    companion object {
        const val KEY_PREFIX = "mediaChip-"
    }
}
