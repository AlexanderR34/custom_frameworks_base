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

package com.android.systemui.statusbar.chips.download.ui.viewmodel

import android.content.Context
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.statusbar.chips.download.domain.interactor.DownloadChipInteractor
import com.android.systemui.statusbar.chips.download.domain.model.DownloadChipModel
import com.android.systemui.statusbar.chips.notification.domain.interactor.StatusBarNotificationChipsInteractor
import com.android.systemui.statusbar.chips.ui.model.ColorsModel
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel
import com.android.systemui.statusbar.chips.ui.viewmodel.OngoingActivityChipViewModel
import com.android.systemui.statusbar.chips.ui.viewmodel.OngoingActivityChipViewModel.Companion.createNotificationToggleClickBehavior
import com.android.systemui.statusbar.chips.ui.viewmodel.OngoingActivityChipViewModel.Companion.isShowingHeadsUpFromChipTap
import com.android.systemui.statusbar.chips.uievents.StatusBarChipsUiEventLogger
import com.android.systemui.statusbar.notification.domain.interactor.HeadsUpNotificationInteractor
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.Logger
import com.android.systemui.statusbar.chips.StatusBarChipLogTags.pad
import com.android.systemui.statusbar.chips.StatusBarChipsLog
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** View model for the download progress chip shown in the status bar. */
@SysUISingleton
class DownloadChipViewModel
@Inject
constructor(
    @Main private val context: Context,
    @Application private val scope: CoroutineScope,
    private val interactor: DownloadChipInteractor,
    private val notifChipsInteractor: StatusBarNotificationChipsInteractor,
    private val headsUpNotificationInteractor: HeadsUpNotificationInteractor,
    private val activityStarter: ActivityStarter,
    @StatusBarChipsLog private val logBuffer: LogBuffer,
    private val uiEventLogger: StatusBarChipsUiEventLogger,
) : OngoingActivityChipViewModel {
    private val logger = Logger(logBuffer, "DownloadChipVM".pad())

    override val chip: StateFlow<OngoingActivityChipModel> =
        combine(
            interactor.downloadState,
            headsUpNotificationInteractor.statusBarHeadsUpState,
        ) { state, headsUpState ->
            when (state) {
                is DownloadChipModel.Inactive -> OngoingActivityChipModel.Inactive()
                is DownloadChipModel.Active -> {
                    val isShowingHeadsUp =
                        headsUpState.isShowingHeadsUpFromChipTap(state.notificationKey)
                    prepareChip(state, isShowingHeadsUp)
                }
            }
        }
        .stateIn(
            scope,
            SharingStarted.WhileSubscribed(),
            OngoingActivityChipModel.Inactive(),
        )

    private fun prepareChip(
        state: DownloadChipModel.Active,
        isShowingHeadsUp: Boolean,
    ): OngoingActivityChipModel.Active {
        val key = "$KEY_PREFIX${state.notificationKey}"
        val contentDesc = ContentDescription.Loaded(
            state.title?.toString() ?: state.appName?.toString() ?: "Download Progress"
        )

        val icon: OngoingActivityChipModel.ChipIcon =
            if (state.appIcon != null) {
                OngoingActivityChipModel.ChipIcon.FullColorIcon(
                    Icon.Loaded(
                        drawable = state.appIcon,
                        contentDescription = contentDesc,
                    )
                )
            } else {
                OngoingActivityChipModel.ChipIcon.StatusBarNotificationIcon(
                    notificationKey = state.notificationKey,
                    contentDescription = contentDesc,
                )
            }

        val text = state.progressText
            ?: when {
                state.max > 0 -> "${((state.progress.toFloat() / state.max.toFloat()) * 100).toInt().coerceIn(0, 100)}%"
                else -> "Descarga"
            }

        val content =
            if (isShowingHeadsUp) {
                OngoingActivityChipModel.Content.IconOnly
            } else {
                OngoingActivityChipModel.Content.Text(text.toString())
            }

        val colors = ColorsModel.DynamicThemed(state.dominantColor)

        val clickBehavior =
            createNotificationToggleClickBehavior(
                applicationScope = scope,
                notifChipsInteractor = notifChipsInteractor,
                logger = logger,
                notificationKey = state.notificationKey,
                isShowingHeadsUpFromChipTap = isShowingHeadsUp,
            )

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
        const val KEY_PREFIX = "downloadChip-"
    }
}
