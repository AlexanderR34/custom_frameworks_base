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

package com.android.systemui.statusbar.chips.timer.ui.viewmodel

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.res.R
import com.android.systemui.statusbar.phone.island.ChipIslandPopup
import com.android.systemui.statusbar.chips.timer.domain.interactor.TimerChipInteractor
import com.android.systemui.statusbar.chips.timer.domain.model.TimerChipModel
import com.android.systemui.statusbar.chips.ui.model.Chronometer
import com.android.systemui.statusbar.chips.ui.model.ColorsModel
import com.android.systemui.statusbar.chips.ui.model.EventTime
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel
import com.android.systemui.statusbar.chips.ui.viewmodel.OngoingActivityChipViewModel
import com.android.systemui.statusbar.chips.uievents.StatusBarChipsUiEventLogger
import com.android.systemui.util.time.SystemClock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** View model for the timer/countdown chip shown in the status bar. */
@SysUISingleton
class TimerChipViewModel
@Inject
constructor(
    @Main private val context: Context,
    @Application private val scope: CoroutineScope,
    private val interactor: TimerChipInteractor,
    private val systemClock: SystemClock,
    private val activityStarter: ActivityStarter,
    private val uiEventLogger: StatusBarChipsUiEventLogger,
) : OngoingActivityChipViewModel {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var timerPopup: ChipIslandPopup? = null

    override val chip: StateFlow<OngoingActivityChipModel> =
        interactor.timerState
            .map { state ->
                when (state) {
                    is TimerChipModel.Inactive -> {
                        mainHandler.post {
                            timerPopup?.dismissWithAnimation()
                        }
                        OngoingActivityChipModel.Inactive()
                    }
                    is TimerChipModel.Active -> {
                        mainHandler.post {
                            timerPopup?.let { popup ->
                                if (popup.isShowing) {
                                    val centerIcon = if (state.isPaused) {
                                        R.drawable.ic_music_island_play
                                    } else {
                                        R.drawable.ic_music_island_pause
                                    }
                                    popup.setCenterButton(centerIcon, "Play/Pause") {
                                        try {
                                            state.pauseResumeIntent?.send()
                                        } catch (ignored: Exception) {}
                                    }
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

    private fun prepareChip(state: TimerChipModel.Active): OngoingActivityChipModel.Active {
        val key = "$KEY_PREFIX${state.packageName}"
        val contentDesc = ContentDescription.Resource(R.string.accessibility_quick_settings_alarm)

        val icon: OngoingActivityChipModel.ChipIcon =
            if (state.appIcon != null) {
                OngoingActivityChipModel.ChipIcon.FullColorIcon(
                    Icon.Loaded(
                        drawable = state.appIcon,
                        contentDescription = contentDesc,
                    )
                )
            } else {
                OngoingActivityChipModel.ChipIcon.SingleColorIcon(
                    Icon.Resource(
                        resId = R.drawable.ic_alarm,
                        contentDescription = contentDesc,
                    )
                )
            }

        val content =
            if (state.isPaused) {
                OngoingActivityChipModel.Content.Timer(
                    value = Chronometer.Paused(java.time.Duration.ofMillis(state.pausedDurationMs.coerceAtLeast(0L))),
                    timeSource = systemClock,
                )
            } else {
                OngoingActivityChipModel.Content.Timer(
                    value =
                        Chronometer.Running(
                            EventTime.ElapsedRealtime(state.eventTimeRealtimeMs),
                            isCountdown = state.isCountdown,
                        ),
                    timeSource = systemClock,
                )
            }

        val colors = ColorsModel.DynamicThemed()

        val clickBehavior =
            OngoingActivityChipModel.ClickBehavior.ExpandAction { _ ->
                mainHandler.post {
                    showOrTogglePopup(state)
                }
            }

        return OngoingActivityChipModel.Active(
            key = key,
            notificationKey = state.notificationKey,
            managingPackageName = state.packageName,
            icon = icon,
            content = content,
            colors = colors,
            clickBehavior = clickBehavior,
        )
    }

    private fun showOrTogglePopup(state: TimerChipModel.Active) {
        val popup = timerPopup ?: ChipIslandPopup(context, "TimerIslandPopup").also { timerPopup = it }

        if (popup.isShowing) {
            popup.dismissWithAnimation()
            return
        }

        // Left button: +1 Min quick action
        popup.setLeftButton(
            iconRes = R.drawable.ic_timer_island_plus1,
            contentDescription = "+1 Min",
        ) {
            try {
                if (state.plusOneIntent != null) {
                    state.plusOneIntent.send()
                } else if (state.intent != null) {
                    activityStarter.postStartActivityDismissingKeyguard(state.intent)
                }
            } catch (ignored: Exception) {}
        }

        // Center button: Pause/Resume toggle
        val centerIcon = if (state.isPaused) {
            R.drawable.ic_music_island_play
        } else {
            R.drawable.ic_music_island_pause
        }
        popup.setCenterButton(
            iconRes = centerIcon,
            contentDescription = if (state.isPaused) "Resume" else "Pause",
        ) {
            try {
                if (state.pauseResumeIntent != null) {
                    state.pauseResumeIntent.send()
                } else if (state.intent != null) {
                    activityStarter.postStartActivityDismissingKeyguard(state.intent)
                }
            } catch (ignored: Exception) {}
        }

        // Right button: Stop / Cancel
        popup.setRightButton(
            iconRes = R.drawable.ic_timer_island_stop,
            contentDescription = "Stop",
        ) {
            try {
                if (state.stopIntent != null) {
                    state.stopIntent.send()
                } else if (state.intent != null) {
                    activityStarter.postStartActivityDismissingKeyguard(state.intent)
                }
            } catch (ignored: Exception) {}
        }

        // Apply dynamic Monet styling with warm amber/orange accent
        popup.applyMonetTheme(Color.parseColor("#F57C00"))
        popup.show()
    }

    companion object {
        const val KEY_PREFIX = "timerChip-"
    }
}
