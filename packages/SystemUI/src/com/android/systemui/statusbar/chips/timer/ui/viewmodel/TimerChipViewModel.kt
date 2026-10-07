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
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.res.R
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

    override val chip: StateFlow<OngoingActivityChipModel> =
        interactor.timerState
            .map { state ->
                when (state) {
                    is TimerChipModel.Inactive -> OngoingActivityChipModel.Inactive()
                    is TimerChipModel.Active -> prepareChip(state)
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
            OngoingActivityChipModel.Content.Timer(
                value =
                    Chronometer.Running(
                        EventTime.ElapsedRealtime(state.eventTimeRealtimeMs),
                        isCountdown = state.isCountdown,
                    ),
                timeSource = systemClock,
            )

        val colors = ColorsModel.DynamicThemed()

        val clickBehavior =
            if (state.intent != null) {
                OngoingActivityChipModel.ClickBehavior.ExpandAction {
                    try {
                        activityStarter.postStartActivityDismissingKeyguard(state.intent)
                    } catch (ignored: Exception) {}
                }
            } else {
                OngoingActivityChipModel.ClickBehavior.None
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

    companion object {
        const val KEY_PREFIX = "timerChip-"
    }
}
